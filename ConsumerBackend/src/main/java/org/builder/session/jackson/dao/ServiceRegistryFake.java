package org.builder.session.jackson.dao;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.InstanceSummary;

@Slf4j
public class ServiceRegistryFake extends ServiceRegistryImpl {

    @NonNull
    private final int port;

    public ServiceRegistryFake(@NonNull final String serviceId,
                               @NonNull final int port) {
        super(serviceId);
        this.port = port;
    }

    @Override
    protected ServiceDiscoveryClient createClient () {
        return new ServiceDiscoveryClient() {
            @Override
            public String serviceName () {
                throw new UnsupportedOperationException("Not implemented for fake client.");
            }

            @Override
            public void close () {
                throw new UnsupportedOperationException("Not implemented for fake client.");
            }
        };
    }

    @Override
    protected List<InstanceSummary> findInstances() {
        Map<String, String> map = ImmutableMap.<String, String>builder()
                .put("AWS_INSTANCE_IPV4", "127.0.0.1")
                .put("AWS_INSTANCE_PORT", Integer.toString(port))
                .build();
        return ImmutableList.of(InstanceSummary.builder()
                                               .id("someId")
                                               .attributes(map)
                                               .build());
    }
}
