# Translate Novel

基于 [SPEC.md](D:/Claude%20Code/Code/Translate%20novel/SPEC.md) 创建的第一版全栈项目，目标是把日文小说阅读和语言学习整合到同一个流程中。

## 当前实现

- 后端：`Spring Boot 2.7 + MyBatis-Plus + Spring Security + JWT + MySQL`
- 前端：`Vue 3 + Vite + Pinia + Vue Router + Element Plus`
- 已完成的 MVP 能力：
  - 用户注册 / 登录 / 当前用户信息
  - Access Token + Refresh Token 登录态续期
  - TXT / EPUB 小说上传与解析
  - 图书列表、图书详情、章节列表
  - 阅读页按段落加载
  - 阅读页按滚动位置和当前段落自动保存进度
  - OpenAI 驱动的段落翻译，未配置密钥时自动降级为占位翻译
  - 段落注音（Ruby HTML）
  - 启发式语法分析
  - 单词查询
  - 书签与生词本

## 目录结构

- [backend](D:/Claude%20Code/Code/Translate%20novel/backend)：后端服务
- [frontend](D:/Claude%20Code/Code/Translate%20novel/frontend)：前端应用
- [SPEC.md](D:/Claude%20Code/Code/Translate%20novel/SPEC.md)：项目规格文档

## 本地启动

### 后端

```powershell
cd "D:\Claude Code\Code\Translate novel\backend"
.\mvnw.cmd spring-boot:run
```

默认使用本地 MySQL，业务数据会落在 `translate_novel` 数据库。

默认开发配置已切到本地 MySQL：

```text
host: 127.0.0.1
port: 3306
database: translate_novel
username: root
password: 123456
```

应用启动时会自动创建数据库和表。若你的本地 MySQL 账号或端口不同，也可以通过环境变量覆盖：

```powershell
$env:MYSQL_HOST="127.0.0.1"
$env:MYSQL_PORT="3306"
$env:MYSQL_DATABASE="translate_novel"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="123456"
```

数据库结构现在由 Flyway 管理，迁移脚本放在 [db/migration](D:/Claude%20Code/Code/Translate%20novel/backend/src/main/resources/db/migration)。已有旧表的数据库首次启动会自动 baseline 后再执行迁移。

如需启用真实翻译服务，先设置环境变量：

```powershell
$env:OPENAI_API_KEY="your_api_key"
```

后端默认会使用 OpenAI Responses API；如果没有配置密钥，会自动回退到占位翻译，不影响其余流程。

### 前端

```powershell
cd "D:\Claude Code\Code\Translate novel\frontend"
npm install
npm run dev
```

开发环境下 Vite 会把 `/api` 代理到 `http://localhost:8080`。

## 校验命令

后端测试：

```powershell
cd "D:\Claude Code\Code\Translate novel\backend"
.\mvnw.cmd test
```

前端构建：

```powershell
cd "D:\Claude Code\Code\Translate novel\frontend"
npm run build
```

## 当前取舍

- 自动化测试仍然固定使用 `H2` 内存库，避免依赖本机 MySQL 状态。
- 数据库结构迁移改成了 `Flyway` 版本化管理，后续新增字段和索引建议继续追加 `V2/V3/...` 迁移脚本。
- 当前 refresh token 为无状态 JWT，适合本地单体开发；如果后续要做多端登录管理、强制下线或撤销会话，建议再补服务端持久化与轮换策略。
