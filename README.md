# BuilderSession
## Introduction
A set of containers to automatically consume resources for a builder session on analyzing your ECS workloads. The project helps to setup an initial service that can use an API to consume resources on the backing host. Requests can specify the resource consumption for either a single host (as specified by the Elastic Load Balancer) or for all of the hosts as they communicate via Service Discovery.

![Alt text](Architecture.png "High-Level Setup of ConsumerService")

## Setup
To setup the service in your ECS account, you merely perform a few simple steps. The estimated setup time is approximately 10 minutes.

1. **Sign into AWS Console**
    * Go to https://aws.amazon.com/
    * Click on "Sign-In" to Console in the top-right corner.
    * Sign in with your credentials. Choose an IAM User with **Admin** permissions.
    * Select a region to run this service in using the "Region" tab in the top-right corner. The default choice should be "US       East (N. Virginia)". From this point onward, it is **expected** that you are in this region for the following steps.           For Builder Sessions, it is **always** recommended to choose a region or account where no production work is being
      run.
2. **Provide CodeBuild with Permissions.**
    * Navigate to the **CodeBuild** console.
    * Select "Create Project" and navigate to the "Source" section.
    * Click "Connect Using OAuth" and "Connect to GitHub".
    * Type in this Repository information and provide CodeBuild access to your GitHub credentials.
3. **Create a first-time Cluster and Service in ECS.**
    * Navigate to the **ElasticContainerService (ECS)** console via the [First Run Wizard](https://console.aws.amazon.com/ecs/home#/firstRun)
    * This will create a default Cluster and Service that will setup ECS Execution Roles and Service Linked Roles in your           account.
    * You can do this by going to  and walking through the steps for an ECS Service running on EC2.
2. **Setup your Cloudformation stack.**
    * Navigate to the **Cloudformation** console. Click "CreateStack".
    * Copy the Cloudformation template from [ConsumerInfrastructure](https://github.com/guitar80ep/BuilderSession/blob/master/ConsumerInfrastructure/src/main/resources/CfnService.yaml) into the template definition.
    * Choose your parameters, but keep DesiredCount of Tasks set to 0 for the moment. Make sure to select a valid ecsTaskExecutionRole (specified by ARN from IAM Console).
    * Click Create and wait for the stack to start up and complete its update.
3. **Build your images in CodeBuild.**
    * Navigate to the CodeBuild console and to your Projects section in your region of choice.
    * Start Build for both your Console and Backend projects that were created by Cloudformation.
    * Wait for the build to complete successfully.
4. **Start your containers.**
    * Navigate to the ECS console and to your Service created by Cloudformation in your region of choice.
    * Update the Service to your specified Desired Count to start Tasks.
    * Wait for the Service to finish updating.
5. **Connect to your endpoint for requests.**
    * Navigate to the EC2 console and to the Load Balancer section in your region of choice.
    * Find the LoadBalancer referenced in your Cloudformation stack and copy the Public DNS Name into a separate browser window.
    * Hit enter. You should see a simple console for inputting an API request.
6. **Make a request.**
    * Input the data you would like to send in your request.
    * Hit save.
    
    
## Exercises
Perform these set of exercises to learn how you can use Container Insights to the fullest. Make sure you fully understand what is being done to implement each step.

1. **Enabling/Disabling Container Insights**
2. **Viewing Available "Out-Of-The-Box" Metrics**
3. **Running Queries on Container Insights Logs**
4. **Setting Up Custom Dimensionality and More Granular Metrics**
5. **Determining the Size of a Task (or Container) Using Historical Data**
6. **Automatic Resizing of a Task**
