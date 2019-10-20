package org.builder.session.jackson.workflow.utilize;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    private static final int CONSUMER_WORKER_COUNT =
            Integer.parseInt(System.getenv("CONSUMER_WORKER_COUNT"));
    private static final int WORKER_SLEEP_TIME_IN_NANOS =
            Integer.parseInt(System.getenv("WORKER_SLEEP_TIME_IN_NANOS"));
    private static final long CPU_TIME_PER_LOAD_IN_NANOS =
            Long.parseLong(System.getenv("CPU_TIME_PER_LOAD_IN_NANOS"));

    @Getter
    private final String name = "CpuConsumer";
    @NonNull
    private final SystemUtil system;
    @NonNull
    private final ExecutorService executorService;
    @NonNull
    private final List<Future> backgroundThreads;
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
        this.executorService = Executors.newCachedThreadPool();
        this.backgroundThreads = Collections.unmodifiableList(
                    IntStream.range(0, CONSUMER_WORKER_COUNT)
                         .mapToObj(i -> executorService.submit(() -> {
                             long sleepMillis = TimeUnit.NANOSECONDS.toMillis(WORKER_SLEEP_TIME_IN_NANOS);
                             int sleepNanos = (int)(WORKER_SLEEP_TIME_IN_NANOS % TimeUnit.MILLISECONDS.toNanos(1));
                             while(true) {
                                 try {
                                     // Do some work...
                                     Instant start = Instant.now();
                                     long workTimeInNanos = this.getLoadSize() * CPU_TIME_PER_LOAD_IN_NANOS;
                                     while (Duration.between(start, Instant.now()).toNanos() < workTimeInNanos) {
                                         //Do nothing. As loop time approaches sleep time, we get 50% System CPU.
                                     }
                                     Thread.sleep(sleepMillis, sleepNanos);
                                 } catch (Throwable t) {
                                     log.warn("Ran into exception in CPU consumption thread.", t);
                                 }
                             }
                 }))
        .collect(Collectors.toList()));
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
        return () -> {};
    }

    @Override
    public void close() {
        executorService.shutdown();
        super.close();
    }
}
