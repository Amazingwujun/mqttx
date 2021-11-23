FROM openjdk:17.0.1
WORKDIR /mqttx
ARG EXECUTABLE_JAR=target/*.jar
COPY ${EXECUTABLE_JAR} app.jar
COPY target/classes/*.yml ./
COPY src/main/resources/logback-spring.xml ./

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=/mqttx/"]