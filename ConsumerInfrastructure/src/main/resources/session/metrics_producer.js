const zlib = require('zlib');
const AWS = require('aws-sdk');

// Lambda Function: ConsumerService-MetricsProducer
//
// Blueprint: cloudwatch-logs-process-data
// Metric Filter: <ClusterName> <ServiceName> <ContainerName> <RequiredMetrics>
// Example ConsumerCluster ConsumerService BackendContainer CpuUtilized
//
// This is provided as a simple example of how Container Insights can help to
// provide simple Container metrics.
//
exports.handler = (event, context) => {
    const payload = new Buffer(event.awslogs.data, 'base64');
    const message = JSON.parse(zlib.gunzipSync(payload).toString('utf8'));
    console.log('Decoded payload:', JSON.stringify(message));

    //Determine CPU and Memory.
    var cpuUtilizedAvg = 0.0;
    var memoryUtilizedAvg = 0.0;
    var ecsCluster = "Unknown";
    var ecsService = "Unknown";
    message.logEvents.forEach(logData => {
        const statsData = JSON.parse(logData.message);
    cpuUtilizedAvg += parseInt(statsData.CpuUtilized, 10);
    memoryUtilizedAvg += parseInt(statsData.MemoryUtilized, 10);
    ecsCluster = statsData.ClusterName;
    ecsService = statsData.ServiceName;
});
    cpuUtilizedAvg = cpuUtilizedAvg / message.logEvents.length;
    memoryUtilizedAvg = memoryUtilizedAvg / message.logEvents.length;
    console.log(`Calculated average cpu ${cpuUtilizedAvg} and memory ${memoryUtilizedAvg} for Cluster/Service ${ecsCluster}/${ecsService}.`);

    // Create CloudWatch client.
    AWS.config.update({region: process.env.AWS_REGION });
    var client = new AWS.CloudWatch({apiVersion: '2010-08-01'});
    console.log('Created CloudWatch client.');

    // Create parameters JSON for putMetricData
    var request = {
        MetricData: [
            {
                MetricName: 'CpuUtilized',
                Dimensions: [
                    {
                        Name: 'ClusterName',
                        Value: ecsCluster
                    },
                    {
                        Name: 'ServiceName',
                        Value: ecsService
                    },
                    {
                        Name: 'ContainerName',
                        Value: process.env.CONTAINER_NAME
                    },
                ],
                Unit: 'None',
                Value: cpuUtilizedAvg
            },
            {
                MetricName: 'MemoryUtilized',
                Dimensions: [
                    {
                        Name: 'ClusterName',
                        Value: ecsCluster
                    },
                    {
                        Name: 'ServiceName',
                        Value: ecsService
                    },
                    {
                        Name: 'ContainerName',
                        Value: process.env.CONTAINER_NAME
                    },
                ],
                Unit: 'None',
                Value: memoryUtilizedAvg
            }
        ],
        Namespace: process.env.METRIC_NAMESPACE
    };

    client.putMetricData(request, function(err, data) {
        if (err) {
            console.log("Failed to put metrics to CloudWatch.", err);
        } else {
            console.log("Successfully put metrics to CloudWatch.", JSON.stringify(data));
        }
    });

    return `Successfully processed ${message.logEvents.length} log events.`;
};
