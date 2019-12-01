package org.builder.session.jackson.workflow.utilize;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.build.session.jackson.proto.Unit;
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

    private static final long DEFAULT_INITIAL_TARGET = 500; // KB/Second
    private static final Duration TRANSMIT_PACE = Duration.ofMillis(50);

    @Getter
    private final String name = "NetworkConsumer";
    @NonNull
    private final SystemUtil system;
    @NonNull
    private final ServerSocket server;
    @NonNull
    private final Socket writerSocket;
    @NonNull
    private final Socket readerSocket;
    @NonNull
    private final ScheduledExecutorService executor;
    @NonNull
    private final AtomicInteger scaleAdjustment = new AtomicInteger(0);
    @Getter
    private long targetRate;

    public NetworkConsumer (@NonNull final SystemUtil system, @NonNull final PIDConfig pidConfig) {
        this(DigitalUnit.BYTES_PER_SECOND
                     .from(DEFAULT_INITIAL_TARGET,
                           DigitalUnit.KILOBYTES_PER_SECOND),
             system,
             pidConfig);
    }

    public NetworkConsumer (final long targetRateInBytes, @NonNull final SystemUtil system, @NonNull final PIDConfig pidConfig) {
        super(pidConfig);
        this.system = system;
        this.setTarget(targetRateInBytes, Unit.BYTES_PER_SECOND);

        //Setup Web Socket connection with loopback.
        int dynamicPort = 0;
        try {
            this.executor = Executors.newScheduledThreadPool(4);
            this.server = new ServerSocket(0);
            Future<Socket> future = executor.submit(() -> this.server.accept());
            Thread.sleep(TRANSMIT_PACE.toMillis());
            dynamicPort = server.getLocalPort();
            this.readerSocket = new Socket(InetAddress.getByName(null).getHostName(), dynamicPort);
            this.writerSocket = future.get();
        } catch (Throwable t) {
            throw new ConsumerInternalException("Failed to start NetworkConsumer on port: " + dynamicPort, t);
        }

        //Start Writer
        DynamicByteArray writeData = new DynamicByteArray();
        this.executor.scheduleAtFixedRate(() -> {
            try {
                int dataSize = scaleAdjustment.get();
                log.trace("Writing {} bytes to socket.", dataSize);
                writeData.setSize(dataSize > 0 ? dataSize : 1);
                writeData.write(writerSocket.getOutputStream());
            } catch (Throwable t) {
                log.error("Encountered error in NetworkConsumer Writer.", t);
            }
        }, 0, TRANSMIT_PACE.toMillis(), TimeUnit.MILLISECONDS);

        //Start Reader
        DynamicByteArray readData = new DynamicByteArray();
        this.executor.scheduleAtFixedRate(() -> {
            try {
                while (this.readerSocket.getInputStream().available() > 0) {
                    int remaining = this.readerSocket.getInputStream().available();
                    log.trace("Reading {} bytes from socket.", remaining);
                    readData.setSize(remaining <= 0 ? 1 : remaining);
                    readData.read(readerSocket.getInputStream(), remaining);
                }
            } catch (Throwable t) {
                log.error("Encountered error in NetworkConsumer Reader.", t);
            }
        }, 0, TRANSMIT_PACE.toMillis(), TimeUnit.MILLISECONDS);
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
        return Unit.BYTES_PER_SECOND;
    }

    @Override
    public Unit getDefaultUnit () {
        return Unit.BYTES_PER_SECOND;
    }

    @Override
    protected long getGoal () {
        return (long) getTarget(Unit.KILOBYTES_PER_SECOND);
    }

    @Override
    protected long getConsumed () {
        return (long) getActual(Unit.KILOBYTES_PER_SECOND);
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
            writerSocket.close();
            readerSocket.close();
            server.close();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to close NetworkConsumer.", t);
        }
        super.close();
    }
}
