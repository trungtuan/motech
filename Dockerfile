FROM tutum/tomcat
RUN apt-get --yes install maven
RUN printf "export MAVEN_OPTS=\"-Xmx512m -XX:MaxPermSize=128m\"\n" >> ~/.profile
RUN env MAVEN_OPTS="-Xmx512m -XX:MaxPermSize=128m" mvn clean install
RUN cp platform/server/target/motech-platform-server.war /tomcat/webapps