package org.builder.session.jackson.utils;

import lombok.Getter;

@Getter
public class StatTracker {

    private double total = 0.0;
    private double max = -Double.MAX_VALUE;
    private double min = Double.MAX_VALUE;
    private long count = 0;
    private double previous;

    public StatTracker(double initialValue) {
        this.previous = initialValue;
    }

    public double getAverage() {
        return count == 0 ? 0.0 : (total / (double) count);
    }

    public void addStat(double value) {
        previous = value;
        total += value;
        max = max < value ? value : max;
        min = min > value ? value : min;
        count++;
    }

    public String toString() {
        return "{ Avg[" + getAverage() +
                "], Min[" + getMin() +
                "], Max[" + getMax() +
                "], Count[" + getCount() +
                "] }";
    }
}
