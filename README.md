# Translate Novel（日文小说阅读与学习平台）

把「读日文小说」和「学日语」合并到同一个流程里的全栈项目：上传 TXT / EPUB 小说后，可以在阅读页按段落做翻译、注音、语法分析和单词查询，并保存阅读进度、书签与生词。

## 功能

- 用户注册 / 登录 / 刷新登录态（Access Token + Refresh Token）
- TXT / EPUB 小说上传与解析（分词与章节切分在后端完成）
- 书架：图书列表、图书详情、章节列表
- 阅读页：按段落加载、按滚动位置自动保存进度、上一章 / 下一章
- 段落翻译（OpenAI 或 Google 网页翻译；未配置密钥时自动降级为占位翻译）
- 段落注音（Ruby）、启发式语法分析、单词查询
- 书签与生词本

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Spring Boot 2.7、MyBatis-Plus、Spring Security + JWT、Flyway、MySQL 8 |
| 前端 | Vue 3 + Vite + Pinia + Vue Router + Element Plus + axios |
| 日语分词 | 后端 kuromoji-ipadic |

## 目录结构

- `backend/`：Spring Boot 服务
- `frontend/`：Vue 3 应用
- `SPEC.md`：最初的设计文档；实际实现以代码为准

## 环境要求

- JDK：源码级别为 Java 8；已适配较高版本 JDK（实测 JDK 25 可编译运行）
  - Spring Boot 2.7 默认带入的 Lombok 版本不支持较新的 JDK，本项目已在 `backend/pom.xml` 中把 Lombok 固定到 `1.18.44`，因此可直接用新版 JDK 构建
- Maven：仓库自带 `mvnw` 包装器，无需单独安装（首次运行会自动下载 Maven）
- Node.js 18+（前端构建）
- MySQL 8（后端默认连接本地 MySQL）

## 本地启动

### 后端

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

默认连接本地 MySQL，业务数据落在 `translate_novel` 数据库，启动时自动建库建表（表结构由 Flyway 管理，迁移脚本位于 `backend/src/main/resources/db/migration`）。

默认开发配置：

```text
host: 127.0.0.1
port: 3306
database: translate_novel
username: root
password: 123456
```

可用环境变量覆盖：

```powershell
$env:MYSQL_HOST="127.0.0.1"
$env:MYSQL_PORT="3306"
$env:MYSQL_DATABASE="translate_novel"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="123456"
```

如需启用真实翻译服务，先设置环境变量：

```powershell
$env:OPENAI_API_KEY="your_api_key"
```

未配置密钥时会自动回退到 Google 网页翻译或占位翻译，不影响其余流程。

生产部署请务必覆盖 JWT 签名密钥（默认值仅用于本地开发）：

```powershell
$env:JWT_SECRET="至少32字节的随机字符串"
```

### 前端

```powershell
cd frontend
npm install
npm run dev
```

开发环境下 Vite 会把 `/api` 代理到 `http://localhost:8080`。

## 校验命令

后端测试（使用 H2 内存库，无需本地 MySQL）：

```powershell
cd backend
./mvnw.cmd test
```

前端构建：

```powershell
cd frontend
npm run build
```

## 一些取舍

- 自动化测试固定使用 H2 内存库，避免依赖本机 MySQL 状态。
- 数据库结构由 Flyway 版本化管理，后续新增字段/索引建议继续追加 `V2/V3/...` 迁移脚本。
- 当前 refresh token 为无状态 JWT，适合本地单体开发；若后续要做多端登录管理、强制下线或会话撤销，建议补服务端持久化与轮换策略。
