#!/bin/bash
java -javaagent:$(dirname "$0")/skywalking-agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=short-link-gateway \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar "$(dirname "$0")/gateway/target/shortlink-gateway.jar"
