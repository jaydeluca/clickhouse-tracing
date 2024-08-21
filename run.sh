#!/bin/bash

./gradlew shadowJar

java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.resource.attributes=service.name=test-service \
     -Dotel.traces.exporter=otlp \
     -Dotel.metrics.exporter=none \
     -Dotel.logs.exporter=none \
     -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
      -jar build/libs/clickhouse-playground-1.0-SNAPSHOT.jar