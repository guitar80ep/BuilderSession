package org.builder.session.jackson.workflow.utilize;

import java.util.LinkedList;
import java.util.Queue;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.system.DigitalUnit;
import org.builder.session.jackson.system.SystemUtil;

import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoryConsumer extends AbstractPidConsumer {

    private static final double DEFAULT_INITIAL_TARGET = 0.33;
    public static final int MEMORY_PER_LOAD_IN_BYTES =
            Integer.parseInt(System.getenv("CONSUMER_MEMORY_PER_LOAD_IN_BYTES"));

    @Getter
    private final String name = "MemoryConsumer";
    @NonNull
    private final SystemUtil system;
    @NonNull
    private Queue<byte[]> load = new LinkedList<>();

    public MemoryConsumer(@NonNull final SystemUtil system,
                          @NonNull final PIDConfig pidConfig) {
        this(DEFAULT_INITIAL_TARGET, system, pidConfig);
    }

    public MemoryConsumer(final double targetPercentage,
                          @NonNull final SystemUtil system,
                          @NonNull final PIDConfig pidConfig) {
        super(pidConfig);
        this.system = system;
        setTarget(targetPercentage, Unit.PERCENTAGE);
    }

    @Override
    public boolean isUnitAllowed (Unit unit) {
        return DigitalUnit.isPercentage(unit) || DigitalUnit.BYTES.canConvertTo(unit);
    }

    @Override
    protected double convertFromStoredUnitTo (double storedValue, Unit unit) {
        if(DigitalUnit.isPercentage(unit)) {
            return storedValue;
        } else {
            return storedValue * this.system.getTotalMemory(DigitalUnit.from(unit));
        }
    }

    @Override
    protected double convertToStoredUnitFrom (double value, Unit unit) {
        if(DigitalUnit.isPercentage(unit)) {
            return value;
        } else {
            return value / this.system.getTotalMemory(DigitalUnit.from(unit));
        }
    }

    @Override
    public double getActual () {
        return this.system.getMemoryPercentage();
    }

    @Override
    protected Unit getStoredUnit () {
        return Unit.PERCENTAGE;
    }

    @Override
    public Unit getDefaultUnit () {
        return Unit.PERCENTAGE;
    }

    @Override
    protected long getGoal () {
        return (long)getTarget(Unit.MEGABYTES);
    }

    @Override
    protected long getConsumed () {
        return (long)getActual(Unit.MEGABYTES);
    }

    @Override
    protected void generateLoad (long scale) {
        Preconditions.checkArgument(scale >= 0, "Scale should be greater than or equal to zero.");
        for(int i = 0; i < scale; i++) {
            load.add(new byte[MEMORY_PER_LOAD_IN_BYTES]);
        }
    }

    @Override
    protected void destroyLoad (long scale) {
        Preconditions.checkArgument(scale >= 0, "Scale should be greater than or equal to zero.");
        for(int i = 0; i < scale && !load.isEmpty(); i++) {
            //TODO: This consumer would function much more accurately if it created memory outside of the heap.
            //      this would reduce the delay between destroyLoad() and GC cycles.
            load.remove();
        }
    }
}
