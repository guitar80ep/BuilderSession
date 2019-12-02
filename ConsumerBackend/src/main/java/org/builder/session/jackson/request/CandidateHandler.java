package org.builder.session.jackson.request;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.build.session.jackson.proto.Candidate;
import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistry;

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
                return Lists.newArrayList(selectedInstance);
            case ALL:
                List<ServiceRegistry.Instance> instances = registry.call();
                return instances.isEmpty() ? Lists.newArrayList(self) : instances;
            default:
                throw new IllegalArgumentException("An unexpected candidate type " + candidate + " was specified.");
        }
    }
}
