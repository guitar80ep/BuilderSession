package org.builder.session.jackson.workflow.utilize;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.utils.PIDConfig;
import org.builder.session.jackson.utils.SystemUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CpuConsumer extends AbstractPidConsumer {

    private static final ImmutableSet<Unit> COMPUTE_UNITS = ImmutableSet.of(Unit.PERCENTAGE,
                                                                            Unit.VCPU);

    public static final int SLEEP_TIME_IN_MILLIS = 100;
    public static final int COMPUTE_LOOP_COUNT = 200;

    @Getter
    private final String name = "CpuConsumer";
    @NonNull
    private final SystemUtil system;
    @NonNull
    private final ExecutorService executorService;
    @NonNull
    private final ImmutableMap<Unit, Double> max;
    @Getter
    private double targetPercentage;

    public CpuConsumer(double targetPercentage, @NonNull SystemUtil system, @NonNull PIDConfig pidConfig) {
        super(pidConfig);
        Preconditions.checkArgument(0.0 <= targetPercentage && targetPercentage <= 1.0, "Must be between 0.0 and 1.0.");
        this.targetPercentage = targetPercentage;
        this.system = system;
        this.max = ImmutableMap.<Unit, Double>builder().put(Unit.PERCENTAGE, 1.0)
                                                       .put(Unit.VCPU, (double)this.system.getCpuUnitsTotal() / (double)this.system.getUnitsPerProcessor())
                                                       .build();
        this.executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void setTargetPercentage (double value, @NonNull Unit unit) {
        Preconditions.checkArgument(COMPUTE_UNITS.contains(unit), "Must specify a compute unit, but got " + unit);
        Preconditions.checkArgument(Range.open(0.0, max.get(unit)).contains(value),
                                    "Must specify a value in max (0.0, " + max.get(unit) + "), but got " + unit);
        log.info("Setting CPU consumption from " + this.targetPercentage + " to " + value + " at " + unit.name());
        this.targetPercentage = value / max.get(unit);
    }

    @Override
    public double getActualPercentage() {
        return (double) this.system.getCpuUnitsUtilized()
                / (double) this.system.getCpuUnitsTotal();
    }

    @Override
    protected long getGoal () {
        return (long)(this.system.getCpuUnitsTotal() * this.getTargetPercentage());
    }

    @Override
    protected long getConsumed () {
        return this.system.getCpuUnitsUtilized();
    }

    @Override
    protected Load generateLoad () {
            Future future = executorService.submit(() -> {
                double value = 1.0;
                Random random = new Random(Instant.now().toEpochMilli());
                while(true) {
                    for(int k = 0; k < COMPUTE_LOOP_COUNT; k++) {
                        value = (value * random.nextDouble()) + random.nextDouble() - random.nextDouble();
                    }
                    Thread.sleep(SLEEP_TIME_IN_MILLIS);
                    value = 1.0;
                }
            });
            return () -> future.cancel(true);
    }
}
