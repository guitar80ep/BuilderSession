package org.builder.session.jackson.workflow.utilize;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.system.DigitalUnit;
import org.builder.session.jackson.system.SystemUtil;
import org.builder.session.jackson.utils.FileUtilities;

import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskConsumer extends AbstractPidConsumer {

    private static final DigitalUnit BASE_UNIT = DigitalUnit.BYTES_PER_SECOND;
    private static final long DEFAULT_INITIAL_TARGET = 512000;
    private static final Duration WRITE_PACE = Duration.ofMillis(50);
    private static final Duration SWAP_PACE = Duration.ofSeconds(5);
    private static final int FILE_BUFFER_SIZE = 3;

    @Getter
    private final String name = "DiskConsumer";
    @NonNull
    private final SystemUtil system;
    @NonNull
    private final ScheduledExecutorService executor;
    @NonNull
    private final AtomicInteger scaleAdjustment = new AtomicInteger(0);
    @NonNull
    private final List<File> fileBuffer = new ArrayList<>(FILE_BUFFER_SIZE);
    @Getter
    private long targetRate;

    public DiskConsumer (@NonNull final SystemUtil system, @NonNull final PIDConfig pidConfig) {
        this(DEFAULT_INITIAL_TARGET, system, pidConfig);
    }

    public DiskConsumer (final long targetRateInBytes, @NonNull final SystemUtil system, @NonNull final PIDConfig pidConfig) {
        super(pidConfig);
        this.system = system;
        this.setTarget(targetRateInBytes, getDefaultUnit());
        this.executor = Executors.newScheduledThreadPool(4);

        //Prepare file buffers for data transfer.
        AtomicInteger fileRef = new AtomicInteger(0);
        try {
            for (int i = 0; i < 3; i++) {
                File file = FileUtilities.createTempFile(Optional.of("DiskConsumer" + i));
                fileBuffer.add(file);
                FileUtilities.reset(file, true);
            }
        } catch (Throwable t) {
            throw new ConsumerInternalException("Failed to start DiskConsumer.", t);
        }

        //Start Writer
        this.executor.scheduleAtFixedRate(() -> {
            try {
                File fileToWrite = fileBuffer.get(calculateIndex(fileRef.get(), FILE_BUFFER_SIZE, 0));
                // Only one file reads, writes or deletes at a given time.
                // This should be limited anyway due to the indexing.
                synchronized (fileToWrite) {
                    try (OutputStream output = new FileOutputStream(fileToWrite)) {
                        int dataSize = scaleAdjustment.get();
                        byte[] data = new byte[dataSize > 0 ? dataSize : 0];
                        ThreadLocalRandom.current().nextBytes(data);
                        output.write(data);
                    }
                }
            } catch (Throwable t) {
                log.error("Encountered error in DiskConsumer Writer.", t);
            } finally {

            }
        }, 0, WRITE_PACE.toMillis(), TimeUnit.MILLISECONDS);

        //Start Reader
        this.executor.scheduleAtFixedRate(() -> {
            try {
                File fileToRead = fileBuffer.get(calculateIndex(fileRef.get(), FILE_BUFFER_SIZE, 1));
                // Only one file reads, writes or deletes at a given time.
                // This should be limited anyway due to the indexing.
                synchronized (fileToRead) {
                    try (InputStream input = new FileInputStream(fileToRead)) {
                        while (input.available() > 0) {
                            input.read(new byte[input.available()]);
                        }
                    }
                }
            } catch (Throwable t) {
                log.error("Encountered error in DiskConsumer Writer.", t);
            }
        }, 0, WRITE_PACE.toMillis(), TimeUnit.MILLISECONDS);

        //Start Swapper
        this.executor.scheduleAtFixedRate(() -> {
            try {
                //Swap file pointer...
                fileRef.getAndUpdate(i -> calculateIndex(i, FILE_BUFFER_SIZE, 1));
                File fileToDelete = fileBuffer.get(calculateIndex(fileRef.get(), FILE_BUFFER_SIZE, 2));
                // Only one file reads, writes or deletes at a given time.
                // This should be limited anyway due to the indexing.
                synchronized (fileToDelete) {
                    //Erase next file...
                    FileUtilities.reset(fileToDelete, true);
                }
            } catch (Throwable t) {
                log.error("Encountered error in DiskConsumer Swapper.", t);
            }
        }, 0, SWAP_PACE.toMillis(), TimeUnit.MILLISECONDS);
    }

    private int calculateIndex(int index, int size, int offset) {
        Preconditions.checkArgument(index >= 0 && index < size,
                                    "Index " + index + " was out of bounds for size " + size);
        Preconditions.checkArgument(offset < size,
                                    "The offset (" + offset + ") must be less than the size " + size);
        int rawIndex = index + offset;
        return offset > 0 ? rawIndex - size : rawIndex + size;
    }

    @Override
    public void setTarget (double value, @NonNull Unit unit) {
        Preconditions.checkArgument(BASE_UNIT.canConvertTo(DigitalUnit.from(unit)), "Must specify a storage unit, but got " + unit);
        Preconditions.checkArgument(value >= 0, "Must specify a non-negative value, but got " + unit);
        log.info("Setting Disk consumption from " + this.targetRate + " to " + value + " at " + unit.name());
        this.targetRate = (long) DigitalUnit.from(getDefaultUnit())
                                            .from(value, DigitalUnit.from(unit));
    }

    @Override
    public double getTarget (Unit unit) {
        return DigitalUnit.from(unit).from(targetRate,
                                           DigitalUnit.from(getDefaultUnit()));
    }

    @Override
    public double getActual (Unit unit) {
        return this.system.getNetworkUsage(DigitalUnit.from(unit));
    }

    @Override
    public Unit getDefaultUnit () {
        return Unit.BYTES_PER_SECOND;
    }

    @Override
    protected long getGoal () {
        return (long) getTarget(getDefaultUnit());
    }

    @Override
    protected long getConsumed () {
        return (long) getActual(getDefaultUnit());
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
        } catch (Throwable t) {
            throw new RuntimeException("Failed to close NetworkConsumer.", t);
        }
        super.close();
    }
}
