package org.builder.session.jackson.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.Preconditions;

import lombok.NonNull;

public class DynamicByteArray {

    private static final double SIZE_REDUCTION_THRESHOLD = 0.666;
    private static final Duration SIZE_REDUCTION_DELAY = Duration.ofMinutes(1);

    private byte[] data;
    private int size; // External view of the size.
    private Optional<Instant> reductionCheckpoint;

    public DynamicByteArray() {
        this(100);
    }

    public DynamicByteArray(int initialSize) {
        data = new byte[initialSize];
        size = initialSize;
        reductionCheckpoint = Optional.empty();
    }

    public void setSize(int newSize) {
        Preconditions.checkArgument(newSize > 0, "Size of byte arrays must be positive.");
        if(newSize > data.length) {
            reductionCheckpoint = Optional.empty(); // Reset wait time.
            data = new byte[newSize];
            this.randomize();
        } else if (newSize <= (int)(data.length * SIZE_REDUCTION_THRESHOLD)) {
            //If the array has been smaller for a while, we can resize to save memory.
            boolean shouldCheckpoint = !reductionCheckpoint.isPresent();
            boolean shouldReduce = reductionCheckpoint.map(start -> Duration.between(start, Instant.now()))
                                                      .map(duration -> duration.toMillis() >= SIZE_REDUCTION_DELAY.toMillis())
                                                      .orElse(false);
            if(shouldCheckpoint) {
                reductionCheckpoint = Optional.of(Instant.now()); // Set wait time.
            } else if (shouldReduce) {
                reductionCheckpoint = Optional.empty(); // Reset wait time.
                data = new byte[newSize];
                this.randomize();
            }
        }
        size = newSize;
    }

    public int getSize() {
        return size;
    }

    public void randomize() {
        ThreadLocalRandom.current().nextBytes(data);
    }

    public byte get(int index) {
        Preconditions.checkArgument(index >= 0 && index < size,
                                    "Index " + index + " is out of bounds [0 , " + size + ")");
        return data[index];
    }

    public int read(@NonNull InputStream stream) throws IOException {
        return read(stream, size);
    }

    public int read(@NonNull InputStream stream, int readBytes) throws IOException {
        Preconditions.checkArgument(readBytes <= size,
                                    "Cannot read more bytes than the size of the array.");
        return stream.read(data, 0, readBytes);
    }

    public void write(@NonNull OutputStream stream) throws IOException {
        stream.write(data, 0, size);
    }
}
