FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src

RUN mvn -B -ntp clean package 

FROM eclipse-temurin:21-jre-alpine AS runtime

USER root
RUN apk upgrade --no-cache
COPY --from=build \
    /workspace/target/user-service-1.0.0.jar \
     /app/app.jar



USER 10001:10001

USER 10001:10001

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
