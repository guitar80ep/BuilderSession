package org.builder.session.jackson.workflow.utilize;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
public class CpuConsumer extends AbstractPidConsumer {

    private static final ImmutableSet<Unit> COMPUTE_UNITS = ImmutableSet.of(Unit.PERCENTAGE,
                                                                            Unit.VCPU);

    private static final int LOAD_FRACTION =
            Integer.parseInt(System.getenv("CONSUMER_LOAD_FRACTION"));

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
                                                       .put(Unit.VCPU, (double)this.system.getCpuUnitsTotal())
                                                       .build();
        //For CPU, our load is a series of threads run across all cores of our CPU.
        this.executorService = new ThreadPoolExecutor(LOAD_FRACTION * 32, Integer.MAX_VALUE,
                                                      5, TimeUnit.MINUTES, new SynchronousQueue<>());
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
        return system.getCpuPercentage();
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
        Future loadRunner = executorService.submit(() -> {
            while(true) {
                try {
                    Instant start = Instant.now();
                    long sleepTimeInMillis = LOAD_FRACTION;
                    // Work for 1 ms, sleep for the rest of the time. This will lead to
                    // significant context switching, but will allow a relatively slow ramp-up
                    // to a value if tuned correctly. The idea is that each load would consume
                    // like 0.1% of the CPU and allow for adjustments.
                    while (Duration.between(start, Instant.now()).toMillis() < 1) { }
                    Thread.sleep(sleepTimeInMillis);
                } catch (Throwable t) {
                    log.warn("Ran into exception in CPU consumption thread.", t);
                }
            }
        });

        // Close the thread when the load is deleted.
        return () -> loadRunner.cancel(true);
    }

    @Override
    public void close() {
        executorService.shutdown();
        super.close();
    }
}
