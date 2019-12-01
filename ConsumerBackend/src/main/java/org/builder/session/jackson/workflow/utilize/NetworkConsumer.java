package org.builder.session.jackson.workflow.utilize;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistry;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.system.DigitalUnit;
import org.builder.session.jackson.system.SystemUtil;
import org.builder.session.jackson.utils.DynamicByteArray;

import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NetworkConsumer extends AbstractPidConsumer {

    private static final long DEFAULT_INITIAL_TARGET = 1024;
    private static final Duration TRANSMIT_PACE = Duration.ofMillis(500);
    private static final Duration SEEK_CONNECTION_PACE = Duration.ofMinutes(1);
    private static final int LISTENER_PORT = 32316;

    @Getter
    private final String name = "NetworkConsumer";
    @NonNull
    private final SystemUtil system;
    @NonNull
    private final ServerSocket server;
    @NonNull
    private final ExecutorService executor;
    @NonNull
    private final ServiceRegistry registry;
    @NonNull
    private final AtomicInteger scaleAdjustment = new AtomicInteger(0);

    public NetworkConsumer (@NonNull final SystemUtil system,
                            @NonNull final PIDConfig pidConfig,
                            @NonNull final  ServiceRegistry registry) {
        this(DigitalUnit.BYTES_PER_SECOND
                     .from(DEFAULT_INITIAL_TARGET,
                           DigitalUnit.KILOBYTES_PER_SECOND),
             system,
             pidConfig,
             registry);
    }

    public NetworkConsumer (final long targetRateInBytes,
                            @NonNull final SystemUtil system,
                            @NonNull final PIDConfig pidConfig,
                            @NonNull final  ServiceRegistry registry) {
        super(pidConfig);
        this.system = system;
        this.registry = registry;
        this.setTarget(targetRateInBytes, Unit.BYTES_PER_SECOND);


        try {
            this.executor = Executors.newCachedThreadPool();

            // Setup connection handling to respond to others.
            this.server = new ServerSocket(LISTENER_PORT);
            this.executor.submit(() -> {
                while (true) {
                    try {
                        Socket newConnection = server.accept();
                        this.executor.submit(() -> runWriter(newConnection));
                    } catch (Throwable t) {
                        log.error("Caught error in NetworkConsumer connection handler thread.", t);
                    }
                }
            });

            // Setup connections to push data to others.
            this.executor.submit(() -> {
                Map<ServiceRegistry.Instance, Future> connectionsMap = new HashMap<>();
                while (true) {
                    try {
                        // Constantly try to find new hosts to talk to...
                        for(ServiceRegistry.Instance i : this.registry.resolveHosts()) {
                            Optional<Future> future = Optional.ofNullable(connectionsMap.get(i));
                            if(future.isPresent()) {
                                Future f = future.get();
                                if(f.isDone() || f.isCancelled()) {
                                    connectionsMap.remove(i);
                                    future = Optional.empty();
                                }
                            }

                            if(!future.isPresent()) {
                                Socket newConnection = new Socket(i.getAddress(), i.getPort());
                                Future newFuture = this.executor.submit(() -> runReader(newConnection));
                                connectionsMap.put(i, newFuture);
                            }
                        }
                        Thread.sleep(SEEK_CONNECTION_PACE.toMillis());
                    } catch (Throwable t) {
                        log.error("Caught error in NetworkConsumer connection handler thread.", t);
                    }
                }
            });
        } catch (Throwable t) {
            throw new ConsumerInternalException("Failed to start NetworkConsumer on port: " + LISTENER_PORT, t);
        }
    }

    /**
     * Runs a writer that constantly writes data out to a listener.
     */
    private void runWriter(Socket socket) {
        log.info("Connected new writer at socket: " + socket);
        try {
            DynamicByteArray writeData = new DynamicByteArray();
            while (true) {
                Thread.sleep(TRANSMIT_PACE.toMillis());
                int dataSize = scaleAdjustment.get();
                dataSize = dataSize > 0 ? dataSize : 1;
                writeData.setSize(dataSize);
                writeData.write(socket.getOutputStream());
                log.debug("Writing {} bytes to socket.", dataSize);
            }
        } catch (Throwable t) {
            log.error("Encountered error in NetworkConsumer Writer. Closing connection.", t);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("Failed to close socket on NetworkConsumer Writer.", e);
            }
        }
    }

    /**
     * Runs a reader that constantly reads data out from a series of listeners or skips
     * the data if necessary to maintain consumption goals.
     */
    private void runReader(Socket socket) {
        log.info("Connected new reader at socket: " + socket);
        try {
            DynamicByteArray readData = new DynamicByteArray();
            while(true) {
                InputStream stream = socket.getInputStream();
                int readByteCount = readData.read(stream);
                log.debug("Read {} bytes from socket.", readByteCount);
                int available = stream.available();
                int newSize = available > 1 ? available : readData.getSize();
                readData.setSize(newSize);
                Thread.sleep(TRANSMIT_PACE.toMillis());
            }
        } catch (Throwable t) {
            log.error("Encountered error in NetworkConsumer Reader.", t);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("Failed to close socket on NetworkConsumer Reader.", e);
            }
        }
    }

    @Override
    public boolean isUnitAllowed (Unit unit) {
        return DigitalUnit.from(getStoredUnit()).canConvertTo(unit);
    }

    @Override
    protected double convertFromStoredUnitTo (double storedValue, Unit unit) {
        return DigitalUnit.from(unit).from(storedValue, getStoredUnit());
    }

    @Override
    protected double convertToStoredUnitFrom (double value, Unit unit) {
        return DigitalUnit.from(getStoredUnit()).from(value, unit);
    }

    @Override
    public double getActual () {
        return this.system.getNetworkUsage(DigitalUnit.from(getStoredUnit()));
    }

    @Override
    protected Unit getStoredUnit () {
        return Unit.KILOBYTES_PER_SECOND;
    }

    @Override
    public Unit getDefaultUnit () {
        return Unit.KILOBYTES_PER_SECOND;
    }

    @Override
    protected long getGoal () {
        return (long) getTarget(getStoredUnit());
    }

    @Override
    protected long getConsumed () {
        return (long) getActual(getStoredUnit());
    }

    @Override
    protected void generateLoad (long scale) {
        Preconditions.checkArgument(scale >= 0, "Scale should be greater than or equal to zero.");
        Preconditions.checkArgument(Math.abs(scale) <= (long)Integer.MAX_VALUE, "Scale should be integer size.");
        scaleAdjustment.addAndGet((int)scale);
    }

    @Override
    protected void destroyLoad (long scale) {
        Preconditions.checkArgument(scale >= 0, "Scale should be greater than or equal to zero.");
        Preconditions.checkArgument(Math.abs(scale) <= (long)Integer.MAX_VALUE, "Scale should be integer size.");
        scaleAdjustment.addAndGet((int)-scale);
    }

    @Override
    public void close () {
        try {
            executor.shutdown();
            server.close();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to close NetworkConsumer.", t);
        }
        super.close();
    }
}
