package org.builder.session.jackson.workflow.utilize;

import org.build.session.jackson.proto.Unit;

public interface Consumer extends AutoCloseable {
    public String getName();
    public double getTargetPercentage();
    public double getActualPercentage();
    public void setTargetPercentage(double value, Unit unit);
    public void consume();
    public void close();
}
