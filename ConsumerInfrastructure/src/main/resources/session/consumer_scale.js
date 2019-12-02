const AWS = require('aws-sdk');

exports.handler = function(event, context, callback) {
    console.log('Received event:', JSON.stringify(event, null, 4));

    var message = event.Records[0].Sns.Message;
    console.log('Message received from SNS:', message);

    var clusterName = process.env.ECS_CLUSTER_NAME;
    var serviceName = process.env.ECS_SERVICE_NAME;
    var containerName = process.env.ECS_CONTAINER_NAME;
    var taskDefinitionFamily = process.env.TASK_DEFINITION_FAMILY;
    var ecs = new AWS.ECS();



    //Describe current TaskDefinition...
    var request = {
        "taskDefinition": taskDefinitionFamily
    };
    var taskDefinition;
    ecs.describeTaskDefinition(request, function (error, data) {
        if (error) {
            console.log(`Error: ${error}`);
        } else {
            console.log(`Described TaskDef: ${data}`);
            taskDefinition = data;
        }
    });

    //Alter current TaskDefinition memory and Register... If Fargate, remember special sizing constraints.

    //Update Service...

    //Update alarms to higher threshhold....

    callback(null, "Success");
};