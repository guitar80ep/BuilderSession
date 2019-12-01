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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.system.DigitalUnit;
import org.builder.session.jackson.system.SystemUtil;
import org.builder.session.jackson.utils.DynamicByteArray;
import org.builder.session.jackson.utils.FileUtilities;

import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskConsumer extends AbstractPidConsumer {

    private static final long DEFAULT_INITIAL_TARGET = 500; // KB/Second
    private static final Duration WRITE_PACE = Duration.ofMillis(50);
    private static final Duration SWAP_PACE = Duration.ofSeconds(5);
    private static final int FILE_BUFFER_SIZE = 3;
    public static final double NORMALIZER = 10.0;

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

    public DiskConsumer (@NonNull final SystemUtil system, @NonNull final PIDConfig pidConfig) {
        this(DigitalUnit.BYTES_PER_SECOND
                        .from(DEFAULT_INITIAL_TARGET,
                              DigitalUnit.KILOBYTES_PER_SECOND),
             system,
             pidConfig);
    }

    public DiskConsumer (final long targetRateInBytes, @NonNull final SystemUtil system, @NonNull final PIDConfig pidConfig) {
        super(pidConfig);
        this.system = system;
        this.setTarget(targetRateInBytes, Unit.BYTES_PER_SECOND);
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
        DynamicByteArray writeData = new DynamicByteArray();
        this.executor.scheduleAtFixedRate(() -> {
            try {
                File fileToWrite = fileBuffer.get(calculateIndex(fileRef.get(), FILE_BUFFER_SIZE, 0));
                // Only one file reads, writes or deletes at a given time.
                // This should be limited anyway due to the indexing.
                synchronized (fileToWrite) {
                    try (OutputStream output = new FileOutputStream(fileToWrite)) {
                        int dataSize = scaleAdjustment.get();
                        log.trace("Writing {} bytes to file {}.", dataSize, fileToWrite.getName());
                        writeData.setSize(dataSize > 0 ? dataSize : 1);
                        writeData.write(output);
                    }
                }
            } catch (Throwable t) {
                log.error("Encountered error in DiskConsumer Writer.", t);
            } finally {

            }
        }, 0, WRITE_PACE.toMillis(), TimeUnit.MILLISECONDS);

        //Start Reader
        DynamicByteArray readData = new DynamicByteArray();
        this.executor.scheduleAtFixedRate(() -> {
            try {
                File fileToRead = fileBuffer.get(calculateIndex(fileRef.get(), FILE_BUFFER_SIZE, 1));
                // Only one file reads, writes or deletes at a given time.
                // This should be limited anyway due to the indexing.
                synchronized (fileToRead) {
                    try (InputStream input = new FileInputStream(fileToRead)) {
                        while (input.available() > 0) {
                            int availableToRead = input.available();
                            log.trace("Reading {} bytes from file {}.", availableToRead, fileToRead.getName());
                            readData.setSize(availableToRead <= 0 ? 1 : availableToRead);
                            readData.read(input, availableToRead);
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
                    log.trace("Deleting file {}.", fileToDelete.getName());
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
        if(rawIndex < 0 || rawIndex >= size) {
            return offset > 0 ? rawIndex - size : rawIndex + size;
        } else {
            return rawIndex;
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
        return this.system.getStorageUsage(DigitalUnit.from(getStoredUnit()));
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
        return (long) (getTarget(Unit.MEGABYTES_PER_SECOND) / NORMALIZER);
    }

    @Override
    protected long getConsumed () {
        return (long) (getActual(Unit.MEGABYTES_PER_SECOND) / NORMALIZER);
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
            throw new RuntimeException("Failed to close DiskConsumer.", t);
        }
        super.close();
    }
}
