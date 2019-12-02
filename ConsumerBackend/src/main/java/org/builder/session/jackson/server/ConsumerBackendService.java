package org.builder.session.jackson.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.build.session.jackson.proto.Candidate;
import org.build.session.jackson.proto.ConsumeRequest;
import org.build.session.jackson.proto.ConsumeResponse;
import org.build.session.jackson.proto.ConsumerBackendServiceGrpc;
import org.build.session.jackson.proto.DescribeEndpointRequest;
import org.build.session.jackson.proto.DescribeEndpointResponse;
import org.build.session.jackson.proto.InstanceSummary;
import org.build.session.jackson.proto.Resource;
import org.build.session.jackson.proto.Unit;
import org.build.session.jackson.proto.UsageSpec;
import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.client.consumer.ConsumerBackendClient;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistry;
import org.builder.session.jackson.client.wrapper.CachedClient;
import org.builder.session.jackson.request.CandidateHandler;
import org.builder.session.jackson.request.ErrorHandler;
import org.builder.session.jackson.workflow.Workflow;
import org.builder.session.jackson.workflow.utilize.Consumer;

import com.google.common.base.Preconditions;

import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConsumerBackendService extends ConsumerBackendServiceGrpc.ConsumerBackendServiceImplBase
        implements AutoCloseable {

    private static final Duration INSTANCE_DISCOVERY_PACE = Duration.ofSeconds(15);

    @NonNull
    private final Workflow workflow = new Workflow();
    @NonNull
    private final SimpleClient<List<ServiceRegistry.Instance>> registry;
    @NonNull
    private final Map<Resource, Consumer> consumers;
    @NonNull
    private final ServiceRegistry.Instance host;

    public ConsumerBackendService(@NonNull final String host,
                                  final int port,
                                  @NonNull final Map<Resource, Consumer> consumers,
                                  @NonNull ServiceRegistry registry) {
        this.host = new ServiceRegistry.Instance(host, port);
        this.registry = CachedClient.wrap(registry, INSTANCE_DISCOVERY_PACE, true);
        this.consumers = consumers;
        consumers.forEach((r, c) -> workflow.consume(c));
    }

    @Override
    public void consume (ConsumeRequest request, StreamObserver<ConsumeResponse> responseObserver) {

        ErrorHandler.ResultOrError<ConsumeResponse> response = ErrorHandler.wrap((req, observer) -> {
            boolean hasSpecifiedInstance = req.getHost() == null;
            Optional<ServiceRegistry.Instance> selected = Optional.ofNullable(
                    hasSpecifiedInstance
                    ? new ServiceRegistry.Instance(req.getHost(), request.getPort())
                    : null);
            List<ServiceRegistry.Instance> hosts = CandidateHandler.resolve(req.getCandidate(),
                                                                            host,
                                                                            selected,
                                                                            registry);
            ConsumeRequest.Builder proxyRequest = ConsumeRequest.newBuilder()
                                                                .addAllUsage(request.getUsageList())
                                                                // Convert to call a single SELF since it
                                                                // will end up that way regardless.
                                                                .setCandidate(Candidate.SELF);

            return hosts.parallelStream()
                        .map(h -> consume(h, proxyRequest.build()))
                        .unordered()
                        .reduce(ConsumerBackendService::merge)
                        .orElseThrow(() -> new IllegalStateException("No hosts found in merge of: " + hosts));
        }, request, log);

        if(response.wasSuccessful()) {
            responseObserver.onNext(response.getResult());
        } else {
            responseObserver.onNext(ConsumeResponse.newBuilder()
                                                   .addInstances(this.getInstanceSummary(new ArrayList<>()))
                                                   .addError(response.getError())
                                                   .build());
        }




        responseObserver.onCompleted();
    }

    private static ConsumeResponse merge(@NonNull ConsumeResponse a, @NonNull ConsumeResponse b) {
        return ConsumeResponse.newBuilder()
                              .addAllInstances(a.getInstancesList())
                              .addAllInstances(b.getInstancesList())
                              .addAllError(a.getErrorList())
                              .addAllError(b.getErrorList())
                              .build();
    }



    @Override
    public void describeEndpoint (DescribeEndpointRequest request,
                                  StreamObserver<DescribeEndpointResponse> responseObserver) {

    }

    /**
     * Runs consume method for self if necessary or passes to other host.
     */
    protected ConsumeResponse consume(ServiceRegistry.Instance targetHost, ConsumeRequest request) {
        boolean isThisHostBeingInvoked = this.host.equals(targetHost);
        List<UsageSpec> usages = Optional.ofNullable(request.getUsageList())
                                         .orElse(new ArrayList<>());
        if(isThisHostBeingInvoked) {
            log.info("Handling a call to this instance {}.", this.host);
            for(UsageSpec usage : usages) {
                Preconditions.checkArgument(Double.compare(0.0, usage.getActual()) == 0,
                                            "Cannot specify field [actual] in calls to consume().");
                Optional<Consumer> consumer = Optional.of(consumers.get(usage.getResource()));
                consumer.orElseThrow(() -> new IllegalStateException("Could not find consumer for " + usage))
                        .setTarget(usage.getTarget(), usage.getUnit());
            }

            return ConsumeResponse.newBuilder()
                                  .addInstances(this.getInstanceSummary(usages))
                                  .build();
        } else {
            log.info("Propagating calls on to neighboring host {}", targetHost);
            try (ConsumerBackendClient client = new ConsumerBackendClient(targetHost.getAddress(),
                                                                          targetHost.getPort())) {
                return client.call(request);
            }
        }
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

    @Override
    public void close() {
        workflow.close();
    }
}
