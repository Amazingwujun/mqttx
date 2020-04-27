FROM openjdk:8-jdk-alpine AS builder
WORKDIR target/dependency
ARG APPJAR=target/*.jar
COPY ${APPJAR} app.jar

ENTRYPOINT ["java","-jar","-Xms1g","-Xmx1g","app.jar"]