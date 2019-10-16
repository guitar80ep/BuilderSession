package org.builder.session.jackson.dao;

import java.time.Instant;
import java.util.List;
import java.util.Random;

public interface ServiceRegistry {

    public List<String> resolveHosts();

    public default String resolveHost() {
        List<String> host = resolveHosts();
        int index = new Random(Instant.now().toEpochMilli()).nextInt(host.size());
        return host.get(index);
    }
}
