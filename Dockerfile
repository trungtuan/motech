FROM nhong/motech-maven
ADD . /opt/motech/
RUN cd /opt/motech && mvn clean install
RUN cp /opt/motech/platform/server/target/motech-platform-server.war /tomcat/webapps
ENV TOMCAT_PASS secr3t
