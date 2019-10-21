package org.builder.session.jackson.workflow.utilize;

import java.util.LinkedList;
import java.util.Queue;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.system.SystemUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoryConsumer extends AbstractPidConsumer {

    private static final ImmutableSet<Unit> MEMORY_UNITS = ImmutableSet.of(Unit.PERCENTAGE,
                                                                           Unit.BYTES,
                                                                           Unit.KILOBYTES,
                                                                           Unit.MEGABYTES,
                                                                           Unit.GIGABYTES);
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
    private final ImmutableMap<Unit, Double> max;

    public MemoryConsumer(double targetPercentage, SystemUtil system, @NonNull PIDConfig pidConfig) {
        super(pidConfig);
        Preconditions.checkArgument(0.0 <= targetPercentage && targetPercentage <= 1.0, "Must be between 0.0 and 1.0.");
        this.targetPercentage = targetPercentage;
        this.system = system;
        this.max = ImmutableMap.<Unit, Double>builder()
                .put(Unit.PERCENTAGE, 1.0)
                .put(Unit.BYTES, (double)this.system.getTotalMemory(SystemUtil.MemoryUnit.BYTES))
                .put(Unit.KILOBYTES,  (double)this.system.getTotalMemory(SystemUtil.MemoryUnit.KILOBYTES))
                .put(Unit.MEGABYTES, (double)this.system.getTotalMemory(SystemUtil.MemoryUnit.MEGABYTES))
                .put(Unit.GIGABYTES, (double) this.system.getTotalMemory(SystemUtil.MemoryUnit.GIGABYTES))
                .build();
    }

    @Override
    public void setTargetPercentage (double value, @NonNull Unit unit) {
        Preconditions.checkArgument(MEMORY_UNITS.contains(unit), "Must specify a memory unit, but got " + unit);
        Preconditions.checkArgument(Range.open(0.0, max.get(unit)).contains(value),
                                    "Must specify a value in max (0.0, " + max.get(unit) + "), but got " + unit);
        log.info("Setting Memory consumption from " + this.targetPercentage + " to " + value + " at " + unit.name());
        this.targetPercentage = value / max.get(unit);
    }

    @Override
    public double getActualPercentage() {
        return this.system.getMemoryPercentage();
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
    protected void generateLoad (long scale) {
        for(int i = 0; i < scale; i++) {
            load.add(new byte[MEMORY_PER_LOAD_IN_BYTES]);
        }
    }

    @Override
    protected void destroyLoad (long scale) {
        for(int i = 0; i < scale && !load.isEmpty(); i++) {
            load.remove();
        }
    }
}
