FROM gradle:7.2.0-jdk17 AS build

WORKDIR /app

COPY ./ /app

RUN ./gradlew shadowJar

FROM openjdk:17-jdk-slim

COPY --from=build /app/build/libs/ /app
ENV OTEL_AGENT_VERSION=1.28.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
#COPY ./opentelemetry-javaagent-2-9-0.jar /app/opentelemetry-javaagent.jar

USER root
RUN chmod 777 /app/opentelemetry-javaagent.jar

USER 1000
WORKDIR /app

CMD ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "clickhouse-playground-1.0-SNAPSHOT.jar"]