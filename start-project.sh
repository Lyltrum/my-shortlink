#!/bin/bash
java -javaagent:$(dirname "$0")/skywalking-agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=short-link-project \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar "$(dirname "$0")/project/target/shortlink-project-1.0-SNAPSHOT.jar"
