package org.builder.session.jackson.workflow.utilize;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.system.DigitalUnit;
import org.builder.session.jackson.system.SystemUtil;

import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NetworkConsumer extends AbstractPidConsumer {

    private static final DigitalUnit BASE_UNIT = DigitalUnit.BYTES;
    private static final long DEFAULT_INITIAL_TARGET = 256;
    private static final Duration TRANSMIT_PACE = Duration.ofMillis(50);
    private static final int PORT = 9876;

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
    private final AtomicLong scaleAdjustment = new AtomicLong(0);
    @Getter
    private long targetRate;

    public NetworkConsumer (@NonNull final SystemUtil system, @NonNull final PIDConfig pidConfig) {
        this(DEFAULT_INITIAL_TARGET, system, pidConfig);
    }

    public NetworkConsumer (final long targetRateInBytes, @NonNull final SystemUtil system, @NonNull final PIDConfig pidConfig) {
        super(pidConfig);
        this.system = system;
        this.setTarget(targetRateInBytes, Unit.BYTES);

        //Setup Web Socket connection with loopback.
        try {
            this.executor = Executors.newScheduledThreadPool(4);
            this.server = new ServerSocket(PORT);
            Thread.sleep(TRANSMIT_PACE.toMillis());
            Future<Socket> future = executor.submit(() -> this.server.accept());
            Thread.sleep(TRANSMIT_PACE.toMillis());
            this.readerSocket = new Socket(InetAddress.getByName(null).getHostName(), PORT);
            Thread.sleep(TRANSMIT_PACE.toMillis());
            this.writerSocket = future.get();
        } catch (Throwable t) {
            throw new ConsumerInternalException("Failed to start NetworkConsumer on port: " + PORT, t);
        }

        //Start Writer
        this.executor.scheduleAtFixedRate(() -> {
            try {
                byte[] data = new byte[(int) scaleAdjustment.get()];
                ThreadLocalRandom.current().nextBytes(data);
                writerSocket.getOutputStream().write(data);
            } catch (Throwable t) {
                log.error("Encountered error in NetworkConsumer Writer.", t);
            }
        }, 0, TRANSMIT_PACE.toMillis(), TimeUnit.MILLISECONDS);

        //Start Reader
        this.executor.scheduleAtFixedRate(() -> {
            try {
                while (this.readerSocket.getInputStream().available() > 0) {
                    int remaining = this.readerSocket.getInputStream().available();
                    this.readerSocket.getInputStream().read(new byte[remaining]);
                }
            } catch (Throwable t) {
                log.error("Encountered error in NetworkConsumer Reader.", t);
            }
        }, 0, TRANSMIT_PACE.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void setTarget (double value, @NonNull Unit unit) {
        Preconditions.checkArgument(BASE_UNIT.canConvertTo(DigitalUnit.from(unit)),
                                    "Must specify a network unit, but got " + unit);
        Preconditions.checkArgument(value >= 0,
                                    "Must specify a non-negative value, but got " + unit);
        log.info("Setting Network consumption from " + this.targetRate + " to " + value + " at " + unit.name());
        this.targetRate = (long) DigitalUnit.BYTES.from(value, DigitalUnit.from(unit));
    }

    @Override
    public double getTarget (Unit unit) {
        return DigitalUnit.from(unit).from(targetRate, DigitalUnit.BYTES);
    }

    @Override
    public double getActual (Unit unit) {
        return this.system.getNetworkUsage(DigitalUnit.from(unit));
    }

    @Override
    protected long getGoal () {
        return (long) getTarget(Unit.BYTES);
    }

    @Override
    protected long getConsumed () {
        return (long) getActual(Unit.BYTES);
    }

    @Override
    protected void generateLoad (long scale) {
        Preconditions.checkArgument(scale >= 0, "Scale should be greater than or equal to zero.");
        scaleAdjustment.addAndGet(scale);
    }

    @Override
    protected void destroyLoad (long scale) {
        Preconditions.checkArgument(scale >= 0, "Scale should be greater than or equal to zero.");
        scaleAdjustment.addAndGet(-scale);
    }
}
