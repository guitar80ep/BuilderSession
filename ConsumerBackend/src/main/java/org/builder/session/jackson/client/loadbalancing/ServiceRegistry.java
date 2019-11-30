package org.builder.session.jackson.client.loadbalancing;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.utils.NoArgs;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

public interface ServiceRegistry extends SimpleClient<List<ServiceRegistry.Instance>> {

    public default List<Instance> call(NoArgs noArgs) {
        return this.resolveHosts();
    }

    public List<Instance> resolveHosts();

    public default Instance resolveHost() {
        List<Instance> host = resolveHosts();
        int index = new Random(Instant.now().toEpochMilli()).nextInt(host.size());
        return host.get(index);
    }

    @Data
    @ToString
    @EqualsAndHashCode
    public static class Instance {
        @NonNull
        private final String address;
        private final int port;
    }
}
