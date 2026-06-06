# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

kkFileView 是一个基于 Spring Boot 4.0.6 + Freemarker 的文件在线预览服务，支持 20+ 种文档格式（Office、PDF、CAD、图片、音视频、压缩包、3D 模型等）。项目使用 Java 25，采用 Maven 多模块结构（单 `server` 子模块）。

## Build & Run Commands

```bash
# 构建 jar（跳过测试）
mvn -q -pl server -DskipTests package

# 开发模式（热重载，修改模板/CSS/JS 后即时生效）
./server/src/main/bin/dev.sh

# 运行指定单元测试
mvn -q -pl server -Dtest=PdfViewerCompatibilityTests test

# CI 构建命令
mvn -B package -Dmaven.test.skip=true --file pom.xml

# 生产启动（Linux）
./server/src/main/bin/startup.sh

# 生产启动（Windows）
server\src\main\bin\startup.bat
```

## Architecture

### 预览调度流程（Strategy/Factory 模式）

```
Request → OnlinePreviewController
        → FileHandlerService（解析 URL 为 FileAttribute，含 FileType 枚举）
        → FilePreviewFactory（按 FileType 查找对应 Spring Bean）
        → FilePreview 实现（渲染对应 Freemarker 模板）
```

- `FileType` 枚举将文件扩展名映射到预览策略 Bean
- `service/impl/` 下有 25+ 个文件类型专属实现类（如 `OfficeFilePreviewImpl`、`PdfFilePreviewImpl`、`CadFilePreviewImpl`）
- 所有实现类通过 Spring 注册，Factory 自动按类型分发

### 缓存层（三种实现，通过 `cache.type` 配置选择）

| 配置值 | 实现类 | 适用场景 |
|--------|--------|----------|
| `jdk` | `CacheServiceJDKImpl`（ConcurrentHashMap） | 单机默认 |
| `redis` | `CacheServiceRedisImpl`（Redisson） | 集群部署 |
| `default` | `CacheServiceRocksDBImpl` | 持久化缓存 |

### 关键入口文件

- 应用入口：`server/src/main/java/cn/keking/ServerMain.java`
- 预览控制器：`web/controller/OnlinePreviewController.java`
- 文件解析：`service/FileHandlerService.java`
- 预览工厂：`service/FilePreviewFactory.java`
- 文件类型枚举：`model/FileType.java`

### Servlet Filter 链（安全/路由管道）

请求依次经过：`TrustHostFilter` → `TrustDirFilter` → `BaseUrlFilter` → `ChinesePathFilter` → `AttributeSetFilter` → `UrlCheckFilter`

### 模板与静态资源

- Freemarker 模板：`server/src/main/resources/web/*.ftl`
- 静态资源（JS/CSS/第三方库）：`server/src/main/resources/static/`
- 不同预览页面使用不同模板，即使外观相似也不要假设共享同一 CSS/行为

## Configuration

主配置文件：`server/src/main/config/application.properties`，所有配置项支持 `KK_` 前缀环境变量覆盖。

高频修改配置项：

| 配置项 | 作用 | 默认值 |
|--------|------|--------|
| `trust.host` | SSRF 防护白名单（v4.4.0+ 默认拒绝所有外部请求） | `default`（拒绝所有） |
| `office.preview.type` | Office 预览模式（`pdf`/`image`） | `pdf` |
| `cache.type` | 缓存实现（`jdk`/`redis`/`default`） | `jdk` |
| `file.upload.disable` | 禁用首页文件上传 | `true` |
| `base.url` | 反向代理时的服务地址 | 从请求读取 |

安全相关：详见 `SECURITY_CONFIG.md`。黑名单优先级高于白名单。

## Key Conventions

- 修改预览行为前，先确认行为由配置、后端路由还是前端模板控制
- Office 文件可渲染为 `pdf` 模式或 `image` 模式，两者使用不同模板
- 压缩包预览涉及目录树生成 + 解压到磁盘 + 嵌套预览 URL 构建 + iframe 加载，调试时从磁盘文件完整性开始排查
- 修改默认值时需区分：本地开发 / 仓库默认配置 / 部署服务器配置 / query-param 覆盖
- 生产环境 `startup.bat` 中的配置文件路径可能与仓库默认不同

## E2E Tests

Playwright 测试位于 `tests/e2e/`，CI 工作流：
- `pr-e2e-mvp.yml`：PR 时运行
- `nightly-e2e.yml`：每夜回归

## CI/CD

- `maven.yml`：master push 和 PR 触发构建
- `master-auto-deploy.yml`：master push 自动部署到 Windows 生产服务器（WinRM）
- 脚本：`.github/scripts/remote_windows_deploy.ps1`
