#!/bin/sh
set -e

JAVA_ROOT="${KK_JAVA_HOME:-${JAVA_HOME:-/opt/jdk}}"

if [ -n "${KK_JAVA_BIN:-}" ]; then
    JAVA_CMD="$KK_JAVA_BIN"
elif [ -x "${JAVA_ROOT}/bin/java" ]; then
    JAVA_CMD="${JAVA_ROOT}/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD="$(command -v java)"
else
    echo "Error: Java runtime not found." >&2
    echo "Mount a Linux JDK into the container and set KK_JAVA_HOME, for example:" >&2
    echo "  -v /host/jdk:/opt/jdk:ro -e KK_JAVA_HOME=/opt/jdk" >&2
    echo "Or set KK_JAVA_BIN to the java executable path inside the container." >&2
    exit 1
fi

APP_HOME="${KKFILEVIEW_HOME:-/opt/kkFileView-${KKFILEVIEW_VERSION:-5.0.0}}"
CONFIG_FILE="${KK_CONFIG_FILE:-${APP_HOME}/config/application.properties}"
JAR_FILE="${KK_JAR_FILE:-}"

if [ -z "$JAR_FILE" ]; then
    JAR_FILE="$(find "${APP_HOME}/bin" -maxdepth 1 -name 'kkFileView-*.jar' -type f | sort | tail -n 1)"
fi

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "Error: kkFileView jar not found under ${APP_HOME}/bin." >&2
    exit 1
fi

exec "$JAVA_CMD" ${JAVA_OPTS:-} \
    -Dfile.encoding=UTF-8 \
    -Dspring.config.location="$CONFIG_FILE" \
    -jar "$JAR_FILE" \
    "$@"
