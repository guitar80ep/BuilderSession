# Built from this directory: docker image build -t consumer-console ./
# Run from this directory: docker container run -it --env-file ./docker/EnvironmentVariables.list --publish 8080:8080 consumer-console:latest
# Execute a command (like bash): docker exec -it container-id bash
FROM tomcat:latest
RUN rm -rf $CATALINA_HOME/webapps/ROOT/
MAINTAINER john_jackson
COPY ./target/ROOT.war /usr/local/tomcat/webapps/