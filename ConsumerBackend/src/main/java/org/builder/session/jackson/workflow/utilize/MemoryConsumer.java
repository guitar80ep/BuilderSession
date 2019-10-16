package org.builder.session.jackson.workflow.utilize;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.utils.PIDConfig;
import org.builder.session.jackson.utils.SystemUtil;
import org.builder.session.jackson.utils.SystemUtilImpl;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import lombok.Getter;
import lombok.NonNull;

public class MemoryConsumer extends AbstractPidConsumer {

    private static  SystemUtil SYSTEM = new SystemUtilImpl();
    private static final ImmutableSet<Unit> MEMORY_UNITS = ImmutableSet.of(Unit.PERCENTAGE,
                                                                           Unit.BYTES,
                                                                           Unit.KILOBYTES,
                                                                           Unit.MEGABYTES,
                                                                           Unit.GIGABYTES);
    private static final ImmutableMap<Unit, Range<Double>> UNIT_RANGE = ImmutableMap.<Unit, Range<Double>>builder()
            .put(Unit.PERCENTAGE, Range.open(0.0, 1.0))
            .put(Unit.BYTES, Range.open(0.0, (double)SYSTEM.getTotalMemory(SystemUtil.MemoryUnit.BYTES)))
            .put(Unit.KILOBYTES, Range.open(0.0, (double)SYSTEM.getTotalMemory(SystemUtil.MemoryUnit.KILOBYTES)))
            .put(Unit.MEGABYTES, Range.open(0.0, (double)SYSTEM.getTotalMemory(SystemUtil.MemoryUnit.MEGABYTES)))
            .put(Unit.GIGABYTES, Range.open(0.0, (double)SYSTEM.getTotalMemory(SystemUtil.MemoryUnit.GIGABYTES)))
            .build();

    @Getter
    private final String name = "MemoryConsumer";
    @NonNull
    private final SystemUtil system;
    @Getter
    private double targetPercentage;

    public MemoryConsumer(double targetPercentage, @NonNull PIDConfig pidConfig) {
        super(pidConfig);
        Preconditions.checkArgument(0.0 <= targetPercentage && targetPercentage <= 1.0, "Must be between 0.0 and 1.0.");
        this.targetPercentage = targetPercentage;
        this.system = new SystemUtilImpl();
    }

    public MemoryConsumer(double targetPercentage) {
        this(targetPercentage, PIDConfig.builder()
                                        .proportionFactor(0.10)
                                        .derivativeFactor(0.15)
                                        .integralFactor(0.05)
                                        .build());
    }

    @Override
    public void setTargetPercentage (double value, @NonNull Unit unit) {
        Preconditions.checkArgument(MEMORY_UNITS.contains(unit), "Must specify a memory unit, but got " + unit);
        Preconditions.checkArgument(UNIT_RANGE.get(unit).contains(value),
                                    "Must specify a value in range " + UNIT_RANGE.get(unit) + ", but got " + unit);
        switch (unit) {
            case PERCENTAGE:
                this.targetPercentage = value;
                break;
            case BYTES:
                this.targetPercentage = value / (double) system.getTotalMemory(SystemUtil.MemoryUnit.BYTES);
                break;
            case KILOBYTES:
                this.targetPercentage = value / (double) system.getTotalMemory(SystemUtil.MemoryUnit.KILOBYTES);
                break;
            case MEGABYTES:
                this.targetPercentage = value / (double) system.getTotalMemory(SystemUtil.MemoryUnit.MEGABYTES);
                break;
            case GIGABYTES:
                this.targetPercentage = value / (double) system.getTotalMemory(SystemUtil.MemoryUnit.GIGABYTES);
                break;
            default:
                throw new IllegalArgumentException("Unexpected case.");
        }
    }

    @Override
    public double getActualPercentage() {
        return (double) this.system.getUsedMemory(SystemUtil.MemoryUnit.BYTES)
                / (double) this.system.getTotalMemory(SystemUtil.MemoryUnit.BYTES);
    }

    @Override
    protected long getGoal () {
        return (long)(this.system.getTotalMemory(SystemUtil.MemoryUnit.MEGABYTES) * this.getTargetPercentage());
    }

    @Override
    protected long getConsumed () {
        return this.system.getUsedMemory(SystemUtil.MemoryUnit.MEGABYTES);
    }

    @Override
    protected Load generateLoad () {
        return new Load() {
            private byte[] array = new byte[1024 * 1024];

            @Override
            public void close () {
                //Do nothing... GC will pick it up.
                array = null;
            }
        };
    }
}
