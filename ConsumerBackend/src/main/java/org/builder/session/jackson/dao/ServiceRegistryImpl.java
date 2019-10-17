package org.builder.session.jackson.dao;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.InstanceSummary;
import software.amazon.awssdk.services.servicediscovery.model.ListInstancesRequest;
import software.amazon.awssdk.services.servicediscovery.model.ListInstancesResponse;

@Slf4j
public class ServiceRegistryImpl implements ServiceRegistry {
    @NonNull
    private final String serviceId;
    @NonNull
    private final ServiceDiscoveryClient client;

    public ServiceRegistryImpl(@NonNull final String serviceId) {
        this.serviceId = serviceId;
        client = createClient();
    }

    protected ServiceDiscoveryClient createClient() {
        return client;
    }

    @Override
    public List<Instance> resolveHosts () {
        return findInstances().stream()
                              .map(i -> convert(i))
                              .collect(Collectors.toList());
    }

    protected static Instance convert(InstanceSummary instance) {
        return new Instance(instance.attributes().get("AWS_INSTANCE_IPV4"),
                            Integer.parseInt(instance.attributes().get("AWS_INSTANCE_PORT")));
    }

    protected List<InstanceSummary> findInstances() {
        UUID uuid = UUID.randomUUID();
        try {
            ListInstancesRequest request = ListInstancesRequest.builder()
                                                               .serviceId(this.serviceId)
                                                               .build();
            log.debug("Call {} Request={}", uuid.toString(), request);
            ListInstancesResponse response = client.listInstances(request);
            log.debug("Call {} Response={}", uuid.toString(), response);
            return response.instances();
        } catch (Throwable t) {
            log.error("Call {} Failed={}", uuid.toString(), t);
            throw t;
        }
    }
}
