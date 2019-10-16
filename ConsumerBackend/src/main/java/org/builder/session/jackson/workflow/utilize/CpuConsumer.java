package org.builder.session.jackson.workflow.utilize;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

public class CpuConsumer extends AbstractPidConsumer {

    private static  SystemUtil SYSTEM = new SystemUtilImpl();
    private static final ImmutableSet<Unit> COMPUTE_UNITS = ImmutableSet.of(Unit.PERCENTAGE,
                                                                            Unit.CORES);
    private static final ImmutableMap<Unit, Range<Double>> UNIT_RANGE = ImmutableMap.<Unit, Range<Double>>builder()
                                                                                    .put(Unit.PERCENTAGE, Range.open(0.0, 1.0))
                                                                                    .put(Unit.CORES, Range.open(0.0, (double)SYSTEM.getTotalProcessors()))
                                                                                    .build();

    public static final int SLEEP_TIME_IN_MILLIS = 100;
    public static final int COMPUTE_LOOP_COUNT = 200;

    @Getter
    private final String name = "CpuConsumer";
    @NonNull
    private final SystemUtil system;
    @NonNull
    private final ExecutorService executorService;
    @Getter
    private double targetPercentage;

    public CpuConsumer(double targetPercentage, @NonNull PIDConfig pidConfig) {
        super(pidConfig);
        Preconditions.checkArgument(0.0 <= targetPercentage && targetPercentage <= 1.0, "Must be between 0.0 and 1.0.");
        this.targetPercentage = targetPercentage;
        this.system = SYSTEM;
        this.executorService = Executors.newCachedThreadPool();
    }

    public CpuConsumer(double targetPercentage) {
        this(targetPercentage, PIDConfig.builder()
                                        .proportionFactor(0.075)
                                        .derivativeFactor(0.100)
                                        .integralFactor(0.035)
                                        .build());
    }

    @Override
    public void setTargetPercentage (double value, @NonNull Unit unit) {
        Preconditions.checkArgument(COMPUTE_UNITS.contains(unit), "Must specify a compute unit, but got " + unit);
        Preconditions.checkArgument(UNIT_RANGE.get(unit).contains(value),
                                    "Must specify a value in range " + UNIT_RANGE.get(unit) + ", but got " + unit);
        switch (unit) {
            case PERCENTAGE:
                this.targetPercentage = value;
                break;
            case CORES:
                this.targetPercentage = value / (double) system.getTotalProcessors();
                break;
                default:
                    throw new IllegalArgumentException("Unexpected case.");
        }
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
