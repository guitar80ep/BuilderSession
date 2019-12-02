# The below commands can be run to assist with setting up Container Insights.

# List the available Clusters in your account/region.
aws ecs list-clusters

# Enable Container Insights for a Cluster in your account/region.
aws ecs update-cluster-settings --cluster <YOUR_CLUSTER> --settings name=containerInsights,value=enabled

# Enable Container Insights for all new Cluster created by a specific user.
aws ecs put-account-setting --name containerInsights --value enabled --principal-arn <YOUR_IAM_USER_ARN>
