FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src

RUN mvn -B -ntp clean package 

FROM gcr.io/distroless/java21-debian13:nonroot AS runtime
COPY --from=build \
    /workspace/target/user-service-1.0.0.jar \
     /app/app.jar



EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
