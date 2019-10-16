package org.builder.session.jackson.server;

import java.io.IOException;

import org.builder.session.jackson.utils.HostnameUtils;
import org.builder.session.jackson.utils.PIDConfig;

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
                       @NonNull final PIDConfig pidConfig,
                       @NonNull final String serviceDiscoveryId) {
        this(ServerBuilder.forPort(port), port, pidConfig, serviceDiscoveryId);
    }

    public ServerImpl (@NonNull final ServerBuilder<?> serverBuilder,
                       @NonNull final int port,
                       @NonNull final PIDConfig pidConfig,
                       @NonNull final String serviceDiscoveryId) {
        Preconditions.checkArgument(port >= 0 && port < (Short.MAX_VALUE * 2),
                                    "Port must be within the range [0, 65535], but was " + port);
        this.host = HostnameUtils.resolveIpAddress();
        this.port = port;
        server = serverBuilder.addService(new ConsumerBackendService(this.host, this.port, pidConfig, serviceDiscoveryId)).build();
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
