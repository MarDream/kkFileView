#!/bin/bash
# kkFileView Docker 构建脚本 - 完全本地化版本
# 用法: ./build-docker.sh [版本号]
# 版本号默认为 5.0.0
#
# 完全离线构建所需文件:
#   - ubuntu-24.04.tar  (Ubuntu 基础镜像)
#   - jdk-25.tar.gz     (Java 25)
#   - server/target/kkFileView-*.tar.gz  (应用包)

VERSION=${1:-5.0.0}
IMAGE_NAME="kkfileview:${VERSION}"
DOCKERFILE="Dockerfile"

set -e

echo "========================================="
echo "  kkFileView ${VERSION} Docker 构建"
echo "========================================="

# 检查文件是否存在
echo "[检查] 验证本地依赖文件..."

if [ ! -f "ubuntu-24.04.tar" ]; then
    echo "错误: 找不到 Ubuntu 基础镜像 ubuntu-24.04.tar"
    echo "请下载:"
    echo "  docker pull ubuntu:24.04"
    echo "  docker save -o ubuntu-24.04.tar ubuntu:24.04"
    exit 1
fi

if [ ! -f "jdk-25.tar.gz" ]; then
    echo "错误: 找不到 Java 25 jdk-25.tar.gz"
    echo "请下载 Amazon Corretto 25:"
    echo "  curl -L -o jdk-25.tar.gz https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz"
    exit 1
fi

if [ ! -f "server/target/kkFileView-${VERSION}.tar.gz" ]; then
    echo "错误: 找不到应用包 server/target/kkFileView-${VERSION}.tar.gz"
    echo "请先构建应用包:"
    echo "  mvn -q -pl server -DskipTests package"
    exit 1
fi

# 导入 Ubuntu 基础镜像
echo "[1/4] 导入 Ubuntu 基础镜像..."
if docker image inspect ubuntu:24.04 > /dev/null 2>&1; then
    echo "      Ubuntu 24.04 已存在，跳过"
else
    docker load -i ubuntu-24.04.tar
fi

# 构建镜像
echo "[2/4] 构建镜像..."
docker build -t "$IMAGE_NAME" -f "$DOCKERFILE" .

# 导出离线包
if docker image inspect "$IMAGE_NAME" > /dev/null 2>&1; then
    echo "[3/4] 导出离线镜像包..."
    docker save -o "kkfileview-${VERSION}-offline.tar" "$IMAGE_NAME"
    echo "      离线包: kkfileview-${VERSION}-offline.tar ($(du -h kkfileview-${VERSION}-offline.tar | cut -f1))"
fi

# 启动测试
echo "[4/4] 启动容器测试..."
docker rm -f kkfileview > /dev/null 2>&1 || true
docker run -d --name kkfileview -p 8080:8080 "$IMAGE_NAME"
sleep 5

if docker exec kkfileview sh -c "wget -qO- http://localhost:8012/" | grep -q "kkFileView"; then
    echo ""
    echo "========================================="
    echo "  构建成功！"
    echo "========================================="
    echo "镜像名称: $IMAGE_NAME"
    echo "镜像大小: $(docker image inspect $IMAGE_NAME --format='{{.Size}}' | numfmt --to=iec-i --suffix=B)"
    echo ""
    echo "访问地址: http://localhost:8080/"
    echo ""
    echo "完全离线部署:"
    echo "  1. 拷贝 kkfileview-${VERSION}-offline.tar 到目标机器"
    echo "  2. docker load -i kkfileview-${VERSION}-offline.tar"
    echo "  3. docker run -d -p 8080:8080 $IMAGE_NAME"
else
    echo "警告: 容器启动测试未通过，请检查日志: docker logs kkfileview"
    exit 1
fi
