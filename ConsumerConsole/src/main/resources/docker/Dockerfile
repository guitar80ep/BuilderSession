# Built from this directory: docker image build -t john_jackson/consumer_console ./
# Run from this directory: docker container run -it --env-file ./EnvironmentVariables.list --publish 8081:8080 john_jackson/consumer_console:latest
# Execute a command (like bash): docker exec -it container-id bash
FROM tomcat:latest
RUN rm -rf $CATALINA_HOME/webapps/ROOT/
MAINTAINER john_jackson
COPY ROOT.war /usr/local/tomcat/webapps/