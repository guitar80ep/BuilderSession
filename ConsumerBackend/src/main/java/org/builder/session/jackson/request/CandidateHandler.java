package org.builder.session.jackson.request;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.build.session.jackson.proto.Candidate;
import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistry;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import lombok.NonNull;

public class CandidateHandler {

    public static List<ServiceRegistry.Instance> resolve(@NonNull final Candidate candidate,
                                                         @NonNull final ServiceRegistry.Instance self,
                                                         @NonNull final Optional<ServiceRegistry.Instance> selected,
                                                         @NonNull final SimpleClient<List<ServiceRegistry.Instance>> registry) {
        switch (candidate) {
            case SELF:
                return Lists.newArrayList(self);
            case RANDOM:
                List<ServiceRegistry.Instance> hostsForRandom = registry.call();
                int hostIndex = ThreadLocalRandom.current().nextInt(hostsForRandom.size());
                ServiceRegistry.Instance randomInstance = hostsForRandom.get(hostIndex);
                return Lists.newArrayList(randomInstance);
            case SPECIFIC:
                ServiceRegistry.Instance selectedInstance = selected.orElseThrow(() -> new IllegalArgumentException(
                        "Expected a selected instance, but found none for candidate " + candidate));
                List<ServiceRegistry.Instance> hostsForValidation = registry.call();
                Preconditions.checkArgument(hostsForValidation.stream().anyMatch(i -> i.equals(selectedInstance)),
                                            "Expected at least one host to match " + selectedInstance
                                                    + ", but none did " + hostsForValidation);
                return Lists.newArrayList(selectedInstance);
            case ALL:
                return registry.call();
            default:
                throw new IllegalArgumentException("An unexpected candidate type " + candidate + " was specified.");
        }
    }

    public static <T> T merge(@NonNull final T identity,
                              @NonNull final Collection<T> value,
                              @NonNull final BinaryOperator<T> merge) {
        return value.stream()
                    .collect(Collectors.reducing(identity, merge));
    }
}
