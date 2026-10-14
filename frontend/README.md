# 前端（Vue 3 + Vite）

日语小说阅读平台的前端，配合 [backend](../backend) 的 REST API 使用。

## 功能

- 书架：上传 TXT / EPUB（自动解析章节与段落）、删除书籍
- 阅读器：段落级翻译、注音（ruby）、语法要点、点词释义
- 收藏：书签、生词本、阅读进度续读

## 技术栈

- Vue 3（`<script setup>`）+ Vue Router 4 + Pinia
- Element Plus 组件库
- axios：请求拦截器自动附加 JWT，令牌临近过期或收到 401 时用 refresh token 静默续期

## 本地开发

```bash
npm install
npm run dev     # http://localhost:5173，/api 代理到 http://localhost:8080
npm run build   # 产物输出到 dist/
```

接口依赖后端，请先按[根目录 README](../README.md) 启动后端服务。
