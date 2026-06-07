#!/bin/bash

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE=$(find "$PROJECT_ROOT/server/target" -maxdepth 1 -name "kkFileView-*.jar" -type f 2>/dev/null | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: kkFileView jar not found in server/target/"
    echo "Please run: mvn -q -DskipTests package"
    exit 1
fi

OFFICE_HOME="${PROJECT_ROOT}/server/LibreOfficePortable/App/libreoffice/program"
CONFIG_FILE="${PROJECT_ROOT}/server/src/main/config/application.properties"
LOG_DIR="${PROJECT_ROOT}/log"

mkdir -p "$LOG_DIR"

echo "============================================"
echo "  kkFileView Startup"
echo "============================================"
echo "Project Root: $PROJECT_ROOT"
echo "Jar File: $JAR_FILE"
echo "Office Home: $OFFICE_HOME"
echo "Config: $CONFIG_FILE"
echo "Log Dir: $LOG_DIR"
echo "============================================"

export KK_OFFICE_HOME="$OFFICE_HOME"
export KK_LOG_DIR="$LOG_DIR"

java -Dfile.encoding=UTF-8 \
     -Dspring.config.location="$CONFIG_FILE" \
     -jar "$JAR_FILE"
