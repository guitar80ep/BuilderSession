package org.builder.session.jackson.dao;

import java.util.List;
import java.util.stream.Collectors;

import lombok.NonNull;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.InstanceSummary;
import software.amazon.awssdk.services.servicediscovery.model.ListInstancesRequest;
import software.amazon.awssdk.services.servicediscovery.model.ListInstancesResponse;

public class ServiceRegistryImpl implements ServiceRegistry {
    @NonNull
    private final String serviceId;
    private final ServiceDiscoveryClient client;

    public ServiceRegistryImpl(@NonNull final String serviceId) {
        this.serviceId = serviceId;
        this.client = ServiceDiscoveryClient.create();
    }

    @Override
    public List<String> resolveHosts () {
        return findInstances().stream()
                              .map(i -> getInstanceURI(i))
                              .collect(Collectors.toList());
    }

    protected String getInstanceURI(InstanceSummary instance) {
        return instance.attributes().get("AWS_INSTANCE_IPV4")
                + ":"
                + instance.attributes().get("AWS_INSTANCE_PORT");
    }

    protected List<InstanceSummary> findInstances() {
        ListInstancesResponse response = client.listInstances(ListInstancesRequest.builder()
                                                                                          .serviceId(this.serviceId)
                                                                                          .build());
        return response.instances();
    }
}
