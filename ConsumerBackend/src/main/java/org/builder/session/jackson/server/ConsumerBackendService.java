package org.builder.session.jackson.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.build.session.jackson.proto.Candidate;
import org.build.session.jackson.proto.ConsumeRequest;
import org.build.session.jackson.proto.ConsumeResponse;
import org.build.session.jackson.proto.ConsumerBackendServiceGrpc;
import org.build.session.jackson.proto.Error;
import org.build.session.jackson.proto.ErrorCode;
import org.build.session.jackson.proto.InstanceSummary;
import org.build.session.jackson.proto.Resource;
import org.build.session.jackson.proto.Unit;
import org.build.session.jackson.proto.UsageSpec;
import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.client.consumer.ConsumerBackendClient;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistry;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistryImpl;
import org.builder.session.jackson.client.wrapper.CachedClient;
import org.builder.session.jackson.system.SystemUtil;
import org.builder.session.jackson.workflow.Workflow;
import org.builder.session.jackson.workflow.utilize.Consumer;
import org.builder.session.jackson.workflow.utilize.CpuConsumer;
import org.builder.session.jackson.workflow.utilize.DiskConsumer;
import org.builder.session.jackson.workflow.utilize.MemoryConsumer;
import org.builder.session.jackson.workflow.utilize.NetworkConsumer;
import org.builder.session.jackson.workflow.utilize.PIDConfig;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConsumerBackendService extends ConsumerBackendServiceGrpc.ConsumerBackendServiceImplBase {

    private static final Duration INSTANCE_DISCOVERY_PACE = Duration.ofSeconds(15);

    @NonNull
    private final Workflow workflow = new Workflow();
    @NonNull
    private final Map<Resource, Consumer> consumers;
    @NonNull
    private final SimpleClient<List<ServiceRegistry.Instance>> registry;
    @NonNull
    private final ServiceRegistry.Instance host;

    public ConsumerBackendService(@NonNull final String host,
                                  final int port,
                                  @NonNull final SystemUtil systemUtil,
                                  @NonNull final PIDConfig pidConfig,
                                  @NonNull String serviceDiscoveryId) {
        this.host = new ServiceRegistry.Instance(host, port);
        this.registry = CachedClient.wrap(new ServiceRegistryImpl(serviceDiscoveryId), INSTANCE_DISCOVERY_PACE, true);

        // Setup consumers...
        consumers = ImmutableMap.<Resource, Consumer>builder()
                .put(Resource.CPU, new CpuConsumer(systemUtil, pidConfig))
                .put(Resource.MEMORY, new MemoryConsumer(systemUtil, pidConfig))
                .put(Resource.NETWORK, new NetworkConsumer(systemUtil, pidConfig))
                .put(Resource.DISK, new DiskConsumer(systemUtil, pidConfig))
                .build();
        consumers.forEach((r, c) -> workflow.consume(c));
    }

    @Override
    public void consume (ConsumeRequest request, StreamObserver<ConsumeResponse> responseObserver) {
        try {
            Candidate candidate = request.getCandidate();
            ServiceRegistry.Instance hostSpecified = new ServiceRegistry.Instance(Optional.ofNullable(request.getHost())
                                                                                          .orElse(this.host.getAddress()),
                                                                                  this.host.getPort());
            switch (candidate) {
                case SELF:
                    responseObserver.onNext(consume(hostSpecified, request));
                    break;
                case RANDOM:
                    List<ServiceRegistry.Instance> hostsForRandom = registry.call();
                    int hostIndex = ThreadLocalRandom.current().nextInt(hostsForRandom.size());
                    ServiceRegistry.Instance randomInstance = hostsForRandom.get(hostIndex);
                    responseObserver.onNext(consume(randomInstance, request));
                    break;
                case ALL:
                    List<ServiceRegistry.Instance> hosts = registry.call();
                    List<ConsumeResponse> responses = hosts.parallelStream()
                                                           .map(h -> consume(h, ConsumeRequest.newBuilder()
                                                                                              .addAllUsage(request.getUsageList())
                                                                                              //Convert to call a single actor instead of ALL.
                                                                                              .setCandidate(Candidate.SELF)
                                                                                              .build()))
                                                           .collect(Collectors.toList());

                    log.debug("Aggregating calls from ALL hosts: {}", responses);
                    //Aggregate the results from all callers...
                    ConsumeResponse.Builder builder = ConsumeResponse.newBuilder();
                    responses.forEach(c -> {
                        builder.addAllInstances(c.getInstancesList());
                        builder.addAllError(c.getErrorList());
                    });
                    responseObserver.onNext(builder.build());
                    break;
                    default:
                        throw new IllegalArgumentException("An unexpected candidate type "
                                                                   + request.getCandidate()
                                                                   + " was specified.");
            }
        } catch (IllegalArgumentException e) {
            responseObserver.onNext(onSingleFailure(ErrorCode.INVALID_PARAMETER, e.getClass().getSimpleName() + ": " + e.getMessage()));
        } catch (Throwable t) {
            responseObserver.onNext(onSingleFailure(ErrorCode.UNKNOWN, t.getClass().getSimpleName() + ": " + t.getMessage()));
        }

        responseObserver.onCompleted();
    }

    /**
     * Runs consume method for self if necessary or passes to other host.
     */
    protected ConsumeResponse consume(ServiceRegistry.Instance host, ConsumeRequest request) {
        boolean isThisHostBeingInvoked = this.host.equals(host);
        List<UsageSpec> usages = Optional.ofNullable(request.getUsageList())
                                         .orElse(new ArrayList<>());
        if(isThisHostBeingInvoked) {
            log.debug("Handling a call to this instance {}.", this.host);
            for(UsageSpec usage : usages) {
                Preconditions.checkArgument(Double.compare(0.0, usage.getActual()) == 0,
                                            "Cannot specify field [actual] in calls to consume().");
                Optional<Consumer> consumer = Optional.of(consumers.get(usage.getResource()));
                consumer.orElseThrow(() -> new IllegalStateException("Could not find consumer for " + usage))
                        .setTarget(usage.getTarget(), usage.getUnit());
            }
            return onSingleSuccess(usages);
        } else {
            log.debug("Propagating calls on to neighboring host {}", host);
            ConsumerBackendClient client = new ConsumerBackendClient(host.getAddress(), host.getPort());
            return client.call(request);
        }
    }

    protected ConsumeResponse onSingleSuccess(List<UsageSpec> usages) {
        return ConsumeResponse.newBuilder()
                              .addInstances(this.getInstanceSummary(usages))
                              .build();
    }

    protected ConsumeResponse onSingleFailure(ErrorCode error, String message) {
        return ConsumeResponse.newBuilder()
                              .addInstances(this.getInstanceSummary(new ArrayList<>()))
                              .addError(Error.newBuilder()
                                             .setType(error)
                                             .setMessage(message)
                                             .build())
                              .build();
    }

    protected InstanceSummary getInstanceSummary(List<UsageSpec> usages) {
        Map<Resource, Unit> resourceToUnitMap = usages.stream()
                                                      .collect(Collectors.toMap(k -> k.getResource(),
                                                                                v -> v.getUnit()));
        //Gather resource usage for all consumers by the unit specified, if available.
        List<UsageSpec> resolvedUsage = this.consumers.entrySet()
                                                      .stream()
                                                      .map(e -> {
                                                          return convert(e.getKey(),
                                                                         Optional.ofNullable(resourceToUnitMap.get(e.getKey())),
                                                                         e.getValue());
                                                      })
                                                      .collect(Collectors.toList());
        return InstanceSummary.newBuilder()
                              .setHost(this.host.getAddress())
                              .setPort(this.host.getPort())
                              .addAllUsage(resolvedUsage)
                              .build();
    }

    private UsageSpec convert(@NonNull Resource resource, @NonNull Optional<Unit> unit, @NonNull Consumer consumer) {
        Unit resolvedUnit = unit.orElse(consumer.getDefaultUnit());
        return UsageSpec.newBuilder()
                        .setResource(resource)
                        .setTarget(consumer.getTarget(resolvedUnit))
                        .setActual(consumer.getActual(resolvedUnit))
                        .setUnit(resolvedUnit)
                        .build();
    }
}
