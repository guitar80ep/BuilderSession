package org.builder.session.jackson.health;

import static org.builder.session.jackson.utils.CommandLineArguments.parseArg;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Function;

import org.build.session.jackson.proto.Candidate;
import org.build.session.jackson.proto.ConsumeRequest;
import org.build.session.jackson.proto.ConsumeResponse;
import org.builder.session.jackson.client.consumer.ConsumerBackendClient;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistry;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistryImpl;

public class Healthcheck {

    public static void main (String[] args) throws UnknownHostException {

        int port = parseArg(args, true, "--port", s -> Integer.parseInt(s)).get();
        String serviceDiscoveryId = parseArg(args, true, "--serviceDiscoveryId", Function.identity()).get();

        try {
            String address = InetAddress.getLocalHost().getHostAddress();
            try (ConsumerBackendClient client = new ConsumerBackendClient(address, port)) {
                ServiceRegistry registry = new ServiceRegistryImpl(serviceDiscoveryId);
                if (runShallowHealthcheck(client) && runDeepHealthcheck(client, registry)) {
                    System.exit(0);
                } else {
                    System.exit(1);
                }
            }
        } catch (Throwable t) {
            System.err.println("Failed healthcheck due to: " + t);
            System.exit(1);
        }

    }

    private static boolean runShallowHealthcheck (ConsumerBackendClient client) {
        ConsumeResponse response = client.call(ConsumeRequest.newBuilder().setCandidate(Candidate.SELF).build());
        if (response.getErrorCount() <= 0 && response.getInstancesCount() == 1) {
            System.out.println("Succeeded shallow healthcheck with response: " + response);
            return true;
        } else {
            System.err.println("Failed shallow healthcheck with response: " + response);
            return false;
        }
    }

    private static boolean runDeepHealthcheck (ConsumerBackendClient client, ServiceRegistry registry) {
        ConsumeResponse response = client.call(ConsumeRequest.newBuilder().setCandidate(Candidate.ALL).build());
        int healthyHosts = registry.resolveHosts().size();
        if (response.getErrorCount() <= 0 && response.getInstancesCount() >= healthyHosts) {
            System.out.println("Succeeded deep healthcheck with response: " + response);
            return true;
        } else {
            System.err.println("Failed deep healthcheck with response: " + response);
            return false;
        }
    }
}
