# Built from this directory: docker image build -t john_jackson/consumer_backend ./
# Run from this directory: docker container run -it --env-file ./EnvironmentVariables.list --publish 2581:2581 john_jackson/consumer_backend:latest
# Execute a command (like bash): docker exec -it container-id bash
FROM openjdk:8u212-jre-alpine3.9
MAINTAINER john_jackson
RUN mkdir -p /usr/local/ConsumerBackend
COPY ConsumerBackend.jar /usr/local/ConsumerBackend
ENTRYPOINT ["java", "-jar", "/usr/local/ConsumerBackend/ConsumerBackend.jar", "--port", "2581", "--serviceDiscoveryId", "someSpecialId123", "--pid", "[0.075,0.100,0.035]"]