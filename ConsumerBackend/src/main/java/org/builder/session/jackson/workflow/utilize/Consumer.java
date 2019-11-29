package org.builder.session.jackson.workflow.utilize;

import org.build.session.jackson.proto.Unit;

public interface Consumer extends AutoCloseable {
    public String getName();
    public void setTarget (double value, Unit unit);
    public double getTarget(Unit unit);
    public double getActual(Unit unit);
    public Unit getDefaultUnit();

    public default double getTarget() {
        return getTarget(getDefaultUnit());
    }

    public default double getActual() {
        return getActual(getDefaultUnit());
    }

    public void consume();
    public void close();
}
