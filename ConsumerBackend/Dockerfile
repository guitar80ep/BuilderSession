# Built from this directory: docker image build -t consumer-backend ./
# Run from this directory: docker container run -it --env-file ./docker/EnvironmentVariables.list --publish 2581:2581 consumer-backend:latest
# Execute a command (like bash): docker exec -it container-id bash
FROM openjdk:8u212-jre-alpine3.9
MAINTAINER john_jackson
RUN mkdir -p /usr/local/ConsumerBackend
COPY ./target/ConsumerBackend.jar /usr/local/ConsumerBackend
ENTRYPOINT ["java", "-jar", "/usr/local/ConsumerBackend/ConsumerBackend.jar", "--runProfiling", "30", "--port", "2581", "--serviceDiscoveryId", "someSpecialId123", "--pid", "[250,0.075,0.100,0.035,0.95]"]