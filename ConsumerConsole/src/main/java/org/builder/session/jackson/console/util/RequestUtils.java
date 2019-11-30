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
import org.builder.session.jackson.utils.JsonHelper;

import com.google.common.collect.Lists;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.utils.StringUtils;

@Slf4j
@NoArgsConstructor(access = AccessLevel.NONE)
public final class RequestUtils {

    public static Optional<ConsumeResponse> request(HttpServletRequest request, ConsumerBackendClient client) {

        String candidateRawValue = request.getParameter(HostViewTag.Input.Candidate.name());
        if(candidateRawValue != null) {
            ConsumeRequest.Builder msg = ConsumeRequest.newBuilder();

            Candidate candidate = Candidate.valueOf(candidateRawValue);
            msg.setCandidate(candidate);

            if(Candidate.SPECIFIC.equals(candidate)) {
                String hostAddress = request.getParameter(HostViewTag.Input.HostAddress.name());
                String hostPort = request.getParameter(HostViewTag.Input.HostPort.name());
                log.info("Sending consume request to specific host {}:{}.", hostAddress, hostPort);
                msg.setHost(hostAddress);
                msg.setPort(Integer.parseInt(hostPort));
            }

            List<Resource> resources = Lists.newArrayList(Resource.values());
            resources.remove(Resource.UNRECOGNIZED);
            for (Resource resource : resources) {
                HostViewTag.Input saveEnum = HostViewTag.Input.find(resource, "Save");
                HostViewTag.Input valueEnum = HostViewTag.Input.find(resource, "Value");
                HostViewTag.Input unitEnum = HostViewTag.Input.find(resource, "Unit");

                String rawValue = request.getParameter(valueEnum.name());
                String rawUnit = request.getParameter(unitEnum.name());

                if(request.getParameter(saveEnum.name()) != null
                        && !StringUtils.isBlank(rawValue)
                        && !StringUtils.isBlank(rawUnit) ) {
                    Unit unit = Unit.valueOf(rawUnit);
                    double value = Double.parseDouble(rawValue);
                    msg.addUsage(UsageSpec.newBuilder()
                                          .setResource(resource)
                                          .setUnit(unit)
                                          .setTarget(value)
                                          .build());
                }
            }

            ConsumeRequest consumeRequest = msg.build();
            log.info("Change request: {}", JsonHelper.toSingleLine(consumeRequest));
            ConsumeResponse response = client.call(consumeRequest);
            log.info("Change request {} returned {}",
                     JsonHelper.toSingleLine(consumeRequest),
                     JsonHelper.toSingleLine(response));

            return Optional.of(response);
        } else {
            return Optional.empty();
        }
    }

    public static List<InstanceSummary> describe(ConsumerBackendClient client) {
        ConsumeRequest request = ConsumeRequest.newBuilder()
                                               .setCandidate(Candidate.ALL)
                                               .build();
        log.info("Describe request: {}", JsonHelper.toSingleLine(request));
        ConsumeResponse response = client.call(request);
        log.info("Describe request {} returned {}",
                 JsonHelper.toSingleLine(request),
                 JsonHelper.toSingleLine(response));
        if(!response.getErrorList().isEmpty()) {
            throw new ConsumerDependencyException("Failed to describe: " + response);
        } else {
            return response.getInstancesList();
        }
    }

    public static List<InstanceSummary> sort(List<InstanceSummary> instances) {
        Lists.newArrayList(instances).sort(
                Comparator.comparing(i -> i.getHost() + ":" + i.getPort()));
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
