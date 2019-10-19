package org.builder.session.jackson.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.builder.session.jackson.client.ConsumerBackendClient;
import org.builder.session.jackson.dao.ServiceRegistry;
import org.builder.session.jackson.dao.ServiceRegistryImpl;
import org.builder.session.jackson.system.SystemUtil;
import org.builder.session.jackson.workflow.Workflow;
import org.builder.session.jackson.workflow.utilize.Consumer;
import org.builder.session.jackson.workflow.utilize.CpuConsumer;
import org.builder.session.jackson.workflow.utilize.MemoryConsumer;
import org.builder.session.jackson.workflow.utilize.PIDConfig;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConsumerBackendService extends ConsumerBackendServiceGrpc.ConsumerBackendServiceImplBase {

    private static final double INITIAL_TARGET = 0.65;

    @NonNull
    private final Workflow workflow = new Workflow();
    @NonNull
    private final Map<Resource, Consumer> consumers;
    @NonNull
    private final ServiceRegistry registry;
    @NonNull
    private final String host;
    @NonNull
    private final int port;

    public ConsumerBackendService(@NonNull final String host,
                                  final int port,
                                  @NonNull final SystemUtil systemUtil,
                                  @NonNull final PIDConfig pidConfig,
                                  @NonNull String serviceDiscoveryId) {
        this.host = host;
        this.port = port;
        this.registry = new ServiceRegistryImpl(serviceDiscoveryId);

        // Setup consumers...
        consumers = ImmutableMap.<Resource, Consumer>builder()
                .put(Resource.CPU, new CpuConsumer(INITIAL_TARGET, systemUtil, pidConfig))
                .put(Resource.MEMORY, new MemoryConsumer(INITIAL_TARGET, systemUtil, pidConfig))
                .build();
        consumers.forEach((r, c) -> workflow.consume(c));
    }

    @Override
    public void consume (ConsumeRequest request, StreamObserver<ConsumeResponse> responseObserver) {
        try {
            switch (request.getCandidate()) {
                case SELF:
                    responseObserver.onNext(consume(new ServiceRegistry.Instance(this.host, this.port),
                                                    request));
                    break;
                case ALL:
                    List<ServiceRegistry.Instance> hosts = registry.resolveHosts();
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
        boolean isThisHostBeingInvoked = host.getAddress().equals(this.host)
                && host.getPort() == this.port;
        List<UsageSpec> usages = Optional.ofNullable(request.getUsageList())
                                         .orElse(new ArrayList<>());
        if(isThisHostBeingInvoked) {
            log.debug("Handling a call to this instance {}.", new ServiceRegistry.Instance(this.host, this.port));
            for(UsageSpec usage : usages) {
                Preconditions.checkArgument(Double.compare(0.0, usage.getActual()) == 0,
                                            "Cannot specify field [actual] in calls to consume().");
                Optional<Consumer> consumer = Optional.of(consumers.get(usage.getResource()));
                consumer.orElseThrow(() -> new IllegalStateException("Could not find consumer for " + usage))
                        .setTargetPercentage(usage.getTarget(), usage.getUnit());
            }
            return onSingleSuccess();
        } else {
            log.debug("Propagating calls on to neighboring host {}", host);
            ConsumerBackendClient client = new ConsumerBackendClient(host.getAddress(), host.getPort());
            return client.call(request);
        }
    }

    protected ConsumeResponse onSingleSuccess() {
        return ConsumeResponse.newBuilder()
                              .addInstances(this.getInstanceSummary())
                              .build();
    }

    protected ConsumeResponse onSingleFailure(ErrorCode error, String message) {
        return ConsumeResponse.newBuilder()
                              .addInstances(this.getInstanceSummary())
                              .addError(Error.newBuilder()
                                             .setType(error)
                                             .setMessage(message)
                                             .build())
                              .build();
    }

    protected InstanceSummary getInstanceSummary() {
        List<UsageSpec> usage = this.consumers.entrySet()
                                              .stream()
                                              .map(e -> convert(e.getKey(), e.getValue()))
                                              .collect(Collectors.toList());
        return InstanceSummary.newBuilder()
                              .setHost(this.host)
                              .setPort(this.port)
                              .addAllUsage(usage)
                              .build();
    }

    private UsageSpec convert(Resource resource, Consumer consumer) {
        return UsageSpec.newBuilder()
                        .setResource(resource)
                        .setTarget(consumer.getTargetPercentage())
                        .setActual(consumer.getActualPercentage())
                        .setUnit(Unit.PERCENTAGE)
                        .build();
    }
}
