#!/bin/bash
cd /workspace/chungyak-advisor/leader/android && export GRADLE_USER_HOME=/workspace/chungyak-advisor/leader/.gradle-home && ./gradlew --no-daemon -q "$@"
