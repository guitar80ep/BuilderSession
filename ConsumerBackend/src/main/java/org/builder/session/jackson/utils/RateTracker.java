package org.builder.session.jackson.utils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RateTracker {

    @NonNull
    private final Supplier<Double> functionToReadTotal;
    @Getter
    @NonNull
    private final Duration pollingPeriod;
    @NonNull
    private final ExecutorService executorService;
    @NonNull
    private StatTracker stats;

    public RateTracker (@NonNull final Supplier<Double> functionToReadTotal,
                        @NonNull final Duration pollingPeriod) {
        this.functionToReadTotal = functionToReadTotal;
        this.pollingPeriod = pollingPeriod;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.stats = new StatTracker(this.functionToReadTotal.get());
        this.executorService.submit(() -> {
            while (true) {
                try {
                    //This ensures that the pacing is accurate.
                    synchronized (stats) {
                        stats.addStat(functionToReadTotal.get());
                    }
                    Thread.sleep(pollingPeriod.toMillis());
                } catch (Throwable t) {
                    log.error("Caught error in background polling thread for RateTracker.", t);
                }
            }
        });
    }

    /**
     * Gathers the latest per second rate of change for the value
     * over the last polling period.
     */
    public Optional<Double> getLatestRate(TimeUnit unit) {
        synchronized (stats) {
            return stats.getLatestRate(unit);
        }
    }
}
