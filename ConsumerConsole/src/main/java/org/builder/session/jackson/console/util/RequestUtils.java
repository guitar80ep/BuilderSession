package org.builder.session.jackson.console.util;

import java.net.InetAddress;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.build.session.jackson.proto.Candidate;
import org.build.session.jackson.proto.ConsumeRequest;
import org.build.session.jackson.proto.ConsumeResponse;
import org.build.session.jackson.proto.InstanceSummary;
import org.build.session.jackson.proto.Resource;
import org.build.session.jackson.proto.Unit;
import org.build.session.jackson.proto.UsageSpec;
import org.builder.session.jackson.client.consumer.ConsumerBackendClient;
import org.builder.session.jackson.console.tags.HostViewTag;
import org.builder.session.jackson.exception.ConsumerDependencyException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.NONE)
public final class RequestUtils {

    public static Optional<ConsumeResponse> request(HttpServletRequest request, ConsumerBackendClient client) {

        if(request.getParameter(HostViewTag.Input.Candidate.name()) != null) {
            ConsumeRequest.Builder msg = ConsumeRequest.newBuilder();

            Candidate candidate = Candidate.valueOf(request.getParameter(HostViewTag.Input.Candidate.name()));
            msg.setCandidate(candidate);

            if(Candidate.SPECIFIC.equals(candidate)) {
                String hostAddress = request.getParameter(HostViewTag.Input.HostAddress.name());
                String hostPort = request.getParameter(HostViewTag.Input.HostPort.name());
                msg.setHost(hostAddress);
                msg.setPort(Integer.parseInt(hostPort));
            }

            for (Resource resource : Resource.values()) {
                HostViewTag.Input saveEnum = HostViewTag.Input.find(resource, "Save");
                HostViewTag.Input valueEnum = HostViewTag.Input.find(resource, "Value");
                HostViewTag.Input unitEnum = HostViewTag.Input.find(resource, "Unit");

                if(request.getParameter(saveEnum.name()) != null) {
                    Unit unit = Unit.valueOf(request.getParameter(unitEnum.name()));
                    double value = Double.parseDouble(request.getParameter(valueEnum.name()));
                    msg.addUsage(UsageSpec.newBuilder()
                                          .setResource(resource)
                                          .setUnit(unit)
                                          .setTarget(value)
                                          .build());
                }
            }

            return Optional.of(client.call(msg.build()));
        } else {
            return Optional.empty();
        }
    }

    public static List<InstanceSummary> describe(ConsumerBackendClient client) {
        ConsumeRequest request = ConsumeRequest.newBuilder()
                                               .setCandidate(Candidate.ALL)
                                               .build();
        ConsumeResponse response = client.call(request);
        if(!response.getErrorList().isEmpty()) {
            throw new ConsumerDependencyException("Failed to describe: " + response);
        } else {
            return response.getInstancesList();
        }
    }

    public static List<InstanceSummary> sort(List<InstanceSummary> instances) {
        instances.sort(Comparator.comparing(i -> i.getHost() + ":" + i.getPort()));
        return instances;
    }

    public static String getHostname() {
        String localHost = "Unknown";
        try {
            localHost = InetAddress.getLocalHost().toString();
        } catch (Throwable t) {
            log.error("Encountered error resolving hostname.", t);
            localHost += " (Error: " + t + ")";
        }
        return localHost;
    }
}
