package org.builder.session.jackson.client.loadbalancing;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import lombok.Data;
import lombok.NonNull;
import lombok.ToString;

public interface ServiceRegistry {

    public List<Instance> resolveHosts();

    public default Instance resolveHost() {
        List<Instance> host = resolveHosts();
        int index = new Random(Instant.now().toEpochMilli()).nextInt(host.size());
        return host.get(index);
    }

    @Data
    @ToString
    public static class Instance {
        @NonNull
        private final String address;
        @NonNull
        private final int port;
    }
}
