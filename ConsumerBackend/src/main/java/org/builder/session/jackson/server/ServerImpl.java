package org.builder.session.jackson.server;

import java.io.IOException;

import org.builder.session.jackson.utils.HostnameUtils;
import org.builder.session.jackson.utils.PIDConfig;
import org.builder.session.jackson.utils.SystemUtil;

import com.google.common.base.Preconditions;

import io.grpc.ServerBuilder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServerImpl implements Server {

    @Getter
    private final int port;
    @NonNull
    @Getter
    private final String host;
    @NonNull
    private final io.grpc.Server server;

    public ServerImpl (@NonNull final int port,
                       @NonNull final SystemUtil systemUtil,
                       @NonNull final PIDConfig pidConfig,
                       @NonNull final String serviceDiscoveryId) {
        Preconditions.checkArgument(port >= 0 && port < (Short.MAX_VALUE * 2),
                                    "Port must be within the range [0, 65535], but was " + port);
        this.host = HostnameUtils.resolveIpAddress();
        this.port = port;
        ConsumerBackendService consumerService = new ConsumerBackendService(this.host,
                                                                            this.port,
                                                                            systemUtil,
                                                                            pidConfig,
                                                                            serviceDiscoveryId);
        server = ServerBuilder.forPort(port)
                              .addService(consumerService).build();
    }

    public void start() throws IOException {
        log.info("Trying to start server on " + port + ".");
        server.start();
        log.info("Server has started. Listening on " + port + ".");
    }

    @Override
    public void close () throws IOException {
        if(!server.isShutdown()) {
            log.info("Trying to stop server on " + port + ".");
            server.shutdown();
            log.info("Server stopped. Closed listener on " + port + ".");
        } else {
            log.info("Skipping server shutdown. Server already terminated on " + port + ".");
        }
    }
}
