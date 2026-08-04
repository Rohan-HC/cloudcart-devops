FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN for attempt in 1 2 3; do \
      mvn -B -ntp \
        -Dmaven.wagon.http.retryHandler.count=5 \
        dependency:go-offline && break; \
      echo "Maven download attempt ${attempt} failed; retrying..."; \
      rm -rf /root/.m2/repository/com/github/luben/zstd-jni; \
      if [ "${attempt}" = "3" ]; then exit 1; fi; \
      sleep 10; \
    done
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
