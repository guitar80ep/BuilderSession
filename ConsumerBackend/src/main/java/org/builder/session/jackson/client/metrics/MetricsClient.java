package org.builder.session.jackson.client.metrics;

import org.builder.session.jackson.client.Client;
import org.builder.session.jackson.exception.ConsumerClientException;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;

public class MetricsClient implements Client<String, Void> {

    private final CloudWatchClient cw = CloudWatchClient.create();

    public class MetricRequest {

    }

    @Override
    public Void call (String s) throws ConsumerInternalException, ConsumerDependencyException, ConsumerClientException {
        cw.putMetricData(PutMetricDataRequest.builder().build());
        return null;
    }
}
