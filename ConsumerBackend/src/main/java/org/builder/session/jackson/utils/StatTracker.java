package org.builder.session.jackson.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class StatTracker {

    private double total;
    private Optional<Double> max;
    private Optional<Double> min;
    private long count;
    private Datapoint latest;
    private Optional<Datapoint> previous;

    public StatTracker(double initialValue) {
        this.reset(initialValue);
    }

    public double getAverage() {
        return count == 0 ? 0.0 : (total / (double) count);
    }

    public Optional<Double> getLatestChange() {
        return previous.map(prev -> latest.getValue() - prev.getValue());
    }

    public Optional<Double> getLatestTimeBetweenStats(TimeUnit unit) {
        return previous.map(prev -> Duration.between(prev.getTimestamp(), latest.getTimestamp()))
                       .map(d -> (double)unit.convert(d.toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * This times the latest two stats (if there have been two)
     * and calculates the rate between them.
     */
    public Optional<Double> getLatestRate(TimeUnit unit) {
        Optional<Double> change = getLatestChange();
        Optional<Double> time = getLatestTimeBetweenStats(unit);
        return time.map(t -> change.map(v -> v / t).orElse(null));
    }

    public void addStat(double value) {
        total += value;
        max = !max.isPresent() || max.get() < value ? Optional.of(value) : max;
        min = !min.isPresent() || min.get() > value ? Optional.of(value) : min;
        count++;

        previous = Optional.of(latest);
        latest = new Datapoint(value, Instant.now());
    }

    public void reset(double initialValue) {
        total = 0.0;
        max = Optional.empty();
        min = Optional.empty();
        count = 0;

        latest = new Datapoint(initialValue, Instant.now());
        previous = Optional.empty();
    }

    public String toString() {
        return "{ Avg[" + getAverage() +
                "], Min[" + getMin().orElse(null) +
                "], Max[" + getMax().orElse(null) +
                "], Count[" + getCount() +
                "], Latest[" + getLatest() +
                "], Previous[" + getPrevious().orElse(null) +
                "], RatePerSec[" + getLatestRate(TimeUnit.SECONDS).orElse(null) +
                "] }";
    }

    @Data
    public class Datapoint {
        private final double value;
        @NonNull
        private final Instant timestamp;
    }
}
