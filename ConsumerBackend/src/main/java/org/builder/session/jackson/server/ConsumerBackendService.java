package org.builder.session.jackson.server;

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
import org.builder.session.jackson.utils.PIDConfig;
import org.builder.session.jackson.workflow.Workflow;
import org.builder.session.jackson.workflow.utilize.Consumer;
import org.builder.session.jackson.workflow.utilize.CpuConsumer;
import org.builder.session.jackson.workflow.utilize.MemoryConsumer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import io.grpc.stub.StreamObserver;
import lombok.NonNull;

public final class ConsumerBackendService extends ConsumerBackendServiceGrpc.ConsumerBackendServiceImplBase {

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
                                  @NonNull final PIDConfig pidConfig,
                                  @NonNull String serviceDiscoveryId) {
        this.host = host;
        this.port = port;
        this.registry = new ServiceRegistryImpl(serviceDiscoveryId);

        // Setup consumers...
        consumers = ImmutableMap.<Resource, Consumer>builder()
                .put(Resource.CPU, new CpuConsumer(0.0, pidConfig))
                .put(Resource.MEMORY, new MemoryConsumer(0.0, pidConfig))
                .build();
        consumers.forEach((r, c) -> workflow.consume(c));
    }

    @Override
    public void consume (ConsumeRequest request, StreamObserver<ConsumeResponse> responseObserver) {
        try {
            switch (request.getCandidate()) {
                case SELF:
                    for(UsageSpec usage : request.getUsageList()) {
                        Preconditions.checkArgument(Double.compare(0.0, usage.getActual()) == 0,
                                                    "Cannot specify field [actual] in calls to consume().");
                        Optional<Consumer> consumer = Optional.of(consumers.get(usage.getResource()));
                        consumer.orElseThrow(() -> new IllegalStateException("Could not find consumer for " + usage))
                                .setTargetPercentage(usage.getTarget(), usage.getUnit());
                    }
                    responseObserver.onNext(onSingleSuccess());
                    break;
                case ALL:
                    List<String> hosts = registry.resolveHosts();
                    List<ConsumeResponse> responses = hosts.parallelStream()
                                                           .map(h -> {
                                                               int indexOfPortSeparator = h.lastIndexOf(":");
                                                               String hostIp = h.substring(0, indexOfPortSeparator);
                                                               int port = Integer.parseInt(h.substring(indexOfPortSeparator + 1));
                                                               ConsumerBackendClient client = new ConsumerBackendClient(hostIp, port);
                                                               return client.call(ConsumeRequest.newBuilder(request)
                                                                                                //Propagate a single call to all known hosts.
                                                                                                .setCandidate(Candidate.SELF)
                                                                                                .build());
                                                           })
                                                           .collect(Collectors.toList());
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
