FROM nhong/motech-autobuild:latest
RUN cd /opt/motech && mvn clean install -fn
RUN cp /opt/motech/platform/server/target/motech-platform-server.war /tomcat/webapps
