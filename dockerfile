FROM openjdk:17.0.1
WORKDIR /mqttx
ARG EXECUTABLE_JAR=target/*.jar
COPY ${EXECUTABLE_JAR} app.jar
COPY target/classes/*.yml ./
COPY src/main/resources/logback-spring.xml ./

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=/mqttx/application.yml", "--spring.profiles.active=prod"]

# 设置容器时间
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 1883
EXPOSE 8083
