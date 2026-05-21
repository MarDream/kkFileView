# kkFileView 5.0.0 Docker 镜像 - 完整独立构建
# 不依赖任何外部镜像，所有依赖内置
#
# 完全离线所需文件:
#   - ubuntu-24.04.tar  (Ubuntu 基础镜像，需预先导入)
#   - server/target/kkFileView-5.0.0.tar.gz  (应用包)
#   - jdk-25.tar.gz     (Java 25 预下载)

# 导入 Ubuntu 基础镜像：docker load -i ubuntu-24.04.tar
FROM ubuntu:24.04

# 下载并安装 Java 25 (Eclipse Temurin)
COPY jdk-25.tar.gz /tmp/
RUN mkdir -p /opt/java && \
    tar -xzf /tmp/jdk-25.tar.gz -C /opt/java --strip-components=1 && \
    rm /tmp/jdk-25.tar.gz && \
    update-alternatives --install /usr/bin/java java /opt/java/bin/java 100 && \
    update-alternatives --install /usr/bin/javac javac /opt/java/bin/javac 100

ENV JAVA_HOME=/opt/java
ENV PATH=/opt/java/bin:$PATH

# 安装运行时环境（包含 LibreOffice / 中文字体）
RUN sed -i 's@//.*archive.ubuntu.com@//mirrors.aliyun.com@g' /etc/apt/sources.list.d/ubuntu.sources && \
    sed -i 's@//security.ubuntu.com@//mirrors.aliyun.com@g' /etc/apt/sources.list.d/ubuntu.sources && \
    sed -i 's@//ports.ubuntu.com@//mirrors.aliyun.com@g' /etc/apt/sources.list.d/ubuntu.sources && \
    apt-get update && \
    export DEBIAN_FRONTEND=noninteractive && \
    apt-get install -y --no-install-recommends tzdata locales xfonts-utils fontconfig libreoffice-nogui && \
    echo 'Asia/Shanghai' > /etc/timezone && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    localedef -i zh_CN -c -f UTF-8 -A /usr/share/locale/locale.alias zh_CN.UTF-8 && \
    locale-gen zh_CN.UTF-8 && \
    apt-get install -y --no-install-recommends ttf-mscorefonts-installer && \
    apt-get install -y --no-install-recommends ttf-wqy-microhei ttf-wqy-zenhei xfonts-wqy && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 复制中文字体
COPY kkfileview-base/fonts/* /usr/share/fonts/chinese/
RUN cd /usr/share/fonts/chinese && \
    mkfontscale && \
    mkfontdir && \
    fc-cache -fv

ENV LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8

# 解压并部署应用
COPY server/target/kkFileView-5.0.0.tar.gz /opt/
RUN tar -xzf /opt/kkFileView-5.0.0.tar.gz -C /opt/ && \
    rm /opt/kkFileView-5.0.0.tar.gz

ENV KKFILEVIEW_BIN_FOLDER=/opt/kkFileView-5.0.0/bin
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dspring.config.location=/opt/kkFileView-5.0.0/config/application.properties", "-jar", "/opt/kkFileView-5.0.0/bin/kkFileView-5.0.0.jar"]
