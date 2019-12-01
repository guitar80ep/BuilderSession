package org.builder.session.jackson.workflow.utilize;

import org.build.session.jackson.proto.Unit;

import com.google.common.base.Preconditions;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractConsumer implements Consumer {

    @Getter(AccessLevel.PROTECTED)
    private double target;

    protected void throwIfUnitInvalid(Unit unit) {
        Preconditions.checkArgument(isUnitAllowed(unit),
                                    "Must specify a valid unit for " + getName()
                                            + ", but got " + unit);
    }

    protected void throwIfValueInvalid(double value) {
        Preconditions.checkArgument(value >= 0,
                                    "Must specify a non-negative value for " + getName()
                                            + ", but got " + value);
    }

    public final void setTarget (double value, Unit unit) {
        throwIfUnitInvalid(unit);
        throwIfValueInvalid(value);
        log.info("Setting {} consumption from {} to {} {}",
                 new Object[] { getName(), this.target, value, unit });
        this.target = convertToStoredUnitFrom(value, unit);
    }

    @Override
    public final double getTarget(Unit unit) {
        throwIfUnitInvalid(unit);
        return convertFromStoredUnitTo(getTarget(), unit);
    }

    @Override
    public final double getActual(Unit unit) {
        throwIfUnitInvalid(unit);
        return convertFromStoredUnitTo(getActual(), unit);
    }

    @Override
    public void close () {

    }

    protected abstract Unit getStoredUnit();

    protected abstract double getActual();

    protected abstract double convertFromStoredUnitTo(double storedValue, Unit unit);

    protected abstract double convertToStoredUnitFrom(double value, Unit unit);
}
