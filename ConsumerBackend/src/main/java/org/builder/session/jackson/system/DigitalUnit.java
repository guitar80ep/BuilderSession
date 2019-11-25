package org.builder.session.jackson.system;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.build.session.jackson.proto.Unit;

import com.google.common.base.Preconditions;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum DigitalUnit {

    BYTES(Unit.BYTES, Unit.BYTES, 1, Optional.empty()),
    KILOBYTES(Unit.BYTES, Unit.KILOBYTES, 1024, Optional.empty()),
    MEGABYTES(Unit.BYTES, Unit.MEGABYTES, 1024 * 1024, Optional.empty()),
    GIGABYTES(Unit.BYTES, Unit.GIGABYTES, 1024 * 1024 * 1024, Optional.empty()),

    BYTES_PER_SECOND(Unit.BYTES_PER_SECOND, Unit.BYTES_PER_SECOND, 1, Optional.of(TimeUnit.SECONDS)),
    KILOBYTES_PER_SECOND(Unit.BYTES_PER_SECOND, Unit.KILOBYTES_PER_SECOND, 1024, Optional.of(TimeUnit.SECONDS)),
    MEGABYTES_PER_SECOND(Unit.BYTES_PER_SECOND, Unit.MEGABYTES_PER_SECOND, 1024 * 1024, Optional.of(TimeUnit.SECONDS)),

    VCPU(Unit.VCPU, Unit.VCPU, 1, Optional.empty());

    @NonNull
    private final Unit baseMeasure;
    @NonNull
    private final Unit unit;
    private final long magnitudeToBase;
    private final Optional<TimeUnit> rateUnitOfTime;

    public boolean isRate() {
        return rateUnitOfTime.isPresent();
    }

    public TimeUnit getTimeUnit() {
        return rateUnitOfTime.orElseThrow(() -> {
            return new IllegalArgumentException("Cannot request a unit of time for a non-rate unit.");
        });
    }

    /**
     * Convert to this unit from the specified value and unit.
     */
    public double from(double value, @NonNull DigitalUnit unitOfValue) {
        Preconditions.checkArgument(this.baseMeasure == unitOfValue.baseMeasure,
                                    "Cannot convert from " + this.name() + " to " + unitOfValue.name());
        return (value * (double)unitOfValue.magnitudeToBase) / (double)this.magnitudeToBase;
    }

    /**
     * Convert to this unit from the specified value and unit. Truncated rounding.
     */
    public long from(long value, @NonNull DigitalUnit unitOfValue) {
        return (long)this.from(value, unitOfValue);
    }

    /**
     * Gets the serializable unit equivalent.
     */
    public Unit toUnit() {
        return this.unit;
    }

    /**
     * Determines if the two units can be converted between.
     */
    public boolean canConvertTo(DigitalUnit unit) {
        return this.baseMeasure == unit.baseMeasure;
    }

    /**
     * Convert this specified unit with the given values to a percentage.
     */
    public double toPercentage(long numerator, long denominator) {
        return (double)DigitalUnit.from(this.baseMeasure).from(numerator, this)
                / (double)DigitalUnit.from(this.baseMeasure).from(denominator, this);
    }

    /**
     * Determines whether or not the provided unit is a percentage.
     */
    public static boolean isPercentage(Unit unit) {
        return Unit.PERCENTAGE.equals(unit);
    }

    /**
     * Gets the DigitalUnit that matches the serializable unit.
     */
    public static DigitalUnit from(Unit unit) {
        for(DigitalUnit candidate : DigitalUnit.values()) {
            if(candidate.toUnit() == unit) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("There is no DigitalUnit that matches the specified Unit.");
    }

    public static Set<DigitalUnit> findMatchingUnits(DigitalUnit unit) {
        Set<DigitalUnit> units = new HashSet<>();
        for(DigitalUnit candidate : DigitalUnit.values()) {
            if(unit.canConvertTo(candidate)) {
                units.add(candidate);
            }
        }
        return units;
    }

    public static Set<Unit> findMatchingUnits(Unit unit) {
        Set<Unit> units = new HashSet<>();
        DigitalUnit unitToMatch = from(unit);
        for(DigitalUnit candidate : DigitalUnit.values()) {
            if(unitToMatch.canConvertTo(candidate)) {
                units.add(candidate.toUnit());
            }
        }
        return units;
    }
}
