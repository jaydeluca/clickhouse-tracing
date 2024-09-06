FROM gradle:7.2.0-jdk17 AS build

WORKDIR /app

COPY ./ /app

RUN ./gradlew shadowJar

FROM openjdk:17-jdk-slim

COPY --from=build /app/build/libs/ /app

COPY ./opentelemetry-javaagent.jar /app

WORKDIR /app

CMD ["java", "-javaagent:opentelemetry-javaagent.jar", "-jar", "clickhouse-playground-1.0-SNAPSHOT.jar"]