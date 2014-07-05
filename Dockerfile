FROM tutum/tomcat:7.0
RUN apt-get --yes install maven
ENV MAVEN_OPTS -Xmx512m -XX:MaxPermSize=128m
RUN mvn clean install
