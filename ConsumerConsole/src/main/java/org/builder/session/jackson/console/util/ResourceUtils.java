package org.builder.session.jackson.console.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.build.session.jackson.proto.Resource;
import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.system.DigitalUnit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ResourceUtils {

    private static Map<Resource, Set<Unit>> ALLOWED_UNITS = ImmutableMap.<Resource, Set<Unit>>builder()
            .put(Resource.CPU, ImmutableSet.<Unit>builder()
                    .add(Unit.PERCENTAGE)
                    .addAll(DigitalUnit.findMatchingUnits(Unit.VCPU))
                    .build())
            .put(Resource.MEMORY, ImmutableSet.<Unit>builder()
                    .add(Unit.PERCENTAGE)
                    .addAll(DigitalUnit.findMatchingUnits(Unit.BYTES))
                    .build())
            .put(Resource.DISK, ImmutableSet.<Unit>builder()
                    .addAll(DigitalUnit.findMatchingUnits(Unit.BYTES))
                    .build())
            .put(Resource.NETWORK, ImmutableSet.<Unit>builder()
                    .addAll(DigitalUnit.findMatchingUnits(Unit.BYTES))
                    .build())
            .build();

    public static Set<Unit> getMatchingUnits(Resource resource) {
        return Optional.ofNullable(ALLOWED_UNITS.get(resource)).orElseThrow(() -> {
            return new IllegalArgumentException("Resource " + resource.name() + " was not found.");
        });
    }

    public static boolean isMatching(Resource resource, Unit unit) {
        return getMatchingUnits(resource).contains(unit);
    }
}
