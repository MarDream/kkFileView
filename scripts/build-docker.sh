#!/bin/bash
set -euo pipefail

# Usage: ./scripts/build-docker.sh [version] [dockerfile]
# Default Dockerfile is the standalone image with CJK fonts and the
# browser-native PDF viewer fix (recommended for demand_system / kkfileview 5.0.0+).
# Pass 'Dockerfile' as the second argument to build the original kkFileView
# upstream image (omnidoc base, no CJK fonts, original PDF.js viewer).

VERSION=${1:-5.0.0}
DOCKERFILE=${2:-Dockerfile.standalone}
IMAGE_NAME=${IMAGE_NAME:-kkfileview:${VERSION}}
APP_PACKAGE="server/target/kkFileView-${VERSION}.tar.gz"

echo "========================================="
echo "  kkFileView ${VERSION} Docker build"
echo "========================================="

if [ ! -f "$APP_PACKAGE" ]; then
    echo "Error: application package not found: $APP_PACKAGE"
    echo "Build it first:"
    echo "  mvn -q -pl server -DskipTests package"
    exit 1
fi

docker build \
    --build-arg KKFILEVIEW_VERSION="$VERSION" \
    -t "$IMAGE_NAME" \
    -f "$DOCKERFILE" \
    .

echo ""
echo "Build success: $IMAGE_NAME"
echo ""
echo "Example startup with host-provided runtimes:"
echo "  docker run -d --name kkfileview -p 8012:8012 \\"
echo "    -v /host/jdk:/opt/jdk:ro -e KK_JAVA_HOME=/opt/jdk \\"
echo "    -v /host/libreoffice:/opt/libreoffice:ro -e KK_OFFICE_HOME=/opt/libreoffice \\"
echo "    $IMAGE_NAME"
