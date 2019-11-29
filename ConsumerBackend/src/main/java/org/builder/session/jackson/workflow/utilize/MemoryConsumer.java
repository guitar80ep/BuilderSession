package org.builder.session.jackson.workflow.utilize;

import java.util.LinkedList;
import java.util.Queue;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.system.DigitalUnit;
import org.builder.session.jackson.system.SystemUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoryConsumer extends AbstractPidConsumer {

    private static final DigitalUnit BASE_UNIT = DigitalUnit.BYTES;
    private static final double DEFAULT_INITIAL_TARGET = 0.33;
    public static final int MEMORY_PER_LOAD_IN_BYTES =
            Integer.parseInt(System.getenv("CONSUMER_MEMORY_PER_LOAD_IN_BYTES"));

    @Getter
    private final String name = "MemoryConsumer";
    @NonNull
    private final SystemUtil system;
    @NonNull
    private Queue<byte[]> load = new LinkedList<>();
    @Getter
    private double targetPercentage;
    @NonNull
    private final ImmutableMap<org.build.session.jackson.proto.Unit, Double> max;

    public MemoryConsumer(@NonNull final SystemUtil system,
                          @NonNull final PIDConfig pidConfig) {
        this(DEFAULT_INITIAL_TARGET, system, pidConfig);
    }

    public MemoryConsumer(final double targetPercentage,
                          @NonNull final SystemUtil system,
                          @NonNull final PIDConfig pidConfig) {
        super(pidConfig);
        this.system = system;
        this.max = ImmutableMap.<org.build.session.jackson.proto.Unit, Double>builder()
                .put(org.build.session.jackson.proto.Unit.PERCENTAGE, 1.0)
                .put(org.build.session.jackson.proto.Unit.BYTES, (double)this.system.getTotalMemory(DigitalUnit.BYTES))
                .put(org.build.session.jackson.proto.Unit.KILOBYTES, (double)this.system.getTotalMemory(DigitalUnit.KILOBYTES))
                .put(org.build.session.jackson.proto.Unit.MEGABYTES, (double)this.system.getTotalMemory(DigitalUnit.MEGABYTES))
                .put(org.build.session.jackson.proto.Unit.GIGABYTES, (double) this.system.getTotalMemory(DigitalUnit.GIGABYTES))
                .build();
        setTarget(targetPercentage, Unit.PERCENTAGE);
    }

    @Override
    public void setTarget (double value, @NonNull Unit unit) {
        boolean isPercentage = DigitalUnit.isPercentage(unit);
        Preconditions.checkArgument(isPercentage || BASE_UNIT.canConvertTo(DigitalUnit.from(unit)),
                                    "Must specify a memory unit, but got " + unit);
        Preconditions.checkArgument(Range.open(0.0, max.get(unit)).contains(value),
                                    "Must specify a value in max (0.0, " + max.get(unit) + "), but got " + unit);
        log.info("Setting Memory consumption from " + this.targetPercentage + " to " + value + " at " + unit.name());
        this.targetPercentage = value / max.get(unit);
    }

    @Override
    public double getTarget(Unit unit) {
        return DigitalUnit.isPercentage(unit) ? this.getTargetPercentage()
                                              : this.system.getUsedMemory(DigitalUnit.from(unit));
    }

    @Override
    public double getActual (Unit unit) {
        return DigitalUnit.isPercentage(unit) ? this.system.getMemoryPercentage()
                                              : DigitalUnit.from(unit).from(getConsumed(), DigitalUnit.MEGABYTES);
    }

    @Override
    public Unit getDefaultUnit () {
        return Unit.PERCENTAGE;
    }

    @Override
    protected long getGoal () {
        return (long)(this.system.getTotalMemory(DigitalUnit.MEGABYTES) * this.getTargetPercentage());
    }

    @Override
    protected long getConsumed () {
        return this.system.getUsedMemory(DigitalUnit.MEGABYTES);
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
