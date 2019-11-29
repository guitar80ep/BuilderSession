package org.builder.session.jackson.console.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.build.session.jackson.proto.InstanceSummary;
import org.build.session.jackson.proto.Resource;
import org.build.session.jackson.proto.UsageSpec;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.utils.Validate;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class InstanceUtils {

    public static InstanceSummary getRandomInstance(List<InstanceSummary> instances) {
        return instances.get(ThreadLocalRandom.current()
                                              .nextInt(instances.size()));
    }

    public static InstanceSummary calculateAverage(String name, int port, List<InstanceSummary> instances) {
        return InstanceSummary.newBuilder()
                              .setHost(name)
                              .setPort(port)
                              .addAllUsage(getAverageUsages(instances))
                              .build();
    }

    protected static List<UsageSpec> getAverageUsages(List<InstanceSummary> instances) {
        Map<Resource, List<UsageSpec>> usageByResource = mapByResource(instances);
        return usageByResource.entrySet().stream().map(e -> {
            List<UsageSpec> usages = e.getValue();
            return UsageSpec.newBuilder()
                            .setResource(assertMatching(usages, UsageSpec::getResource))
                            .setUnit(assertMatching(usages, UsageSpec::getUnit))
                            .setTarget(usages.stream()
                                             .mapToDouble(UsageSpec::getTarget)
                                             .average()
                                             .orElse(0.0))
                            .setActual(usages.stream()
                                             .mapToDouble(UsageSpec::getActual)
                                             .average()
                                             .orElse(0.0))
                            .build();
        }).collect(Collectors.toList());
    }

    protected static Map<Resource, List<UsageSpec>> mapByResource(List<InstanceSummary> instances) {
        return instances.stream()
                        .flatMap(i -> i.getUsageList().stream())
                        .collect(Collectors.groupingBy(UsageSpec::getResource));
    }

    protected static <K, R> R assertMatching(Collection<K> collection,
                                             Function<K, R> conversion) {
        Validate.isTrue(!collection.isEmpty(), "Collection must be non-empty.");
        R value = conversion.apply(collection.stream().findFirst().get());
        Validate.isTrue(collection.stream().allMatch(k -> Objects.equals(value, conversion.apply(k))),
                        "Expected all values to match, but they do not for " + collection);
        return value;
    }
}
