#!/bin/bash

./gradlew shadowJar

#cp /Users/jay/code/projects/opentelemetry-java-instrumentation/javaagent/build/libs/opentelemetry-javaagent-2.5.0-SNAPSHOT.jar opentelemetry-javaagent.jar

java -javaagent:opentelemetry-javaagent-2-12-0.jar \
      -Dotel.experimental.config.file=sdk-config.yml \
      -jar build/libs/clickhouse-playground-1.0-SNAPSHOT.jar