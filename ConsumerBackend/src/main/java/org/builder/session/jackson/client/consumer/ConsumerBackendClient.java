package org.builder.session.jackson.client.consumer;

import java.util.UUID;

import org.build.session.jackson.proto.ConsumeRequest;
import org.build.session.jackson.proto.ConsumeResponse;
import org.build.session.jackson.proto.ConsumerBackendServiceGrpc;
import org.builder.session.jackson.client.Client;
import org.builder.session.jackson.utils.JsonHelper;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsumerBackendClient implements Client<ConsumeRequest, ConsumeResponse> {

    @NonNull
    private final ManagedChannel channel;
    @NonNull
    private final ConsumerBackendServiceGrpc.ConsumerBackendServiceBlockingStub blockingStub;

    public ConsumerBackendClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
    }

    public ConsumerBackendClient(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        blockingStub = ConsumerBackendServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public ConsumeResponse call (@NonNull ConsumeRequest request) {
        UUID uuid = UUID.randomUUID();
        try {
            log.debug("Call {} Request={}", uuid.toString(), JsonHelper.toSingleLine(request));
            ConsumeResponse response = blockingStub.consume(request);
            log.debug("Call {} Response={}", uuid.toString(), JsonHelper.toSingleLine(response));
            return response;
        } catch (Throwable t) {
            log.error("Call {} Failed={}", uuid.toString(), t);
            throw t;
        }
    }
}
