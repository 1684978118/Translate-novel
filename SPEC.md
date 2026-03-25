# 日文小说阅读与学习平台 - 实现文档

## 1. 项目概述

### 1.1 项目目标
让用户上传日文小说（txt/epub），在阅读过程中进行语言学习：翻译、注音、语法解析、单词查询。

### 1.2 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                      前端 (Vue 3)                        │
│  - Vite + Pinia + Vue Router + Element Plus             │
│  - 日语分词库: kuromoji.js                               │
│  - epub.js: epub阅读渲染                                 │
└─────────────────────┬───────────────────────────────────┘
                      │ HTTP/REST + JWT
┌─────────────────────▼───────────────────────────────────┐
│                    后端 (Spring Boot)                    │
│  - MyBatis-Plus + Spring Security + JWT                 │
│  - MySQL 8.0                                             │
│  - 外部API: Google Translate / DeepL (翻译)             │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 数据库设计

### 2.1 ER 图

```
users ───┐
         │ 1:N           ┌──────────────┐    N:1 ┌──────────┐
         └───────────────►│    books     │────────►│ categories│
         │               └──────────────┘         └──────────┘
         │                     │ 1:N
         │               ┌─────┴──────────┐
         │               │   chapters      │
         │               └─────┬──────────┘
         │                     │ 1:N
         │               ┌─────┴──────────┐
         │               │   paragraphs    │
         │               └─────────────────┘
         │                     │
         ├─────────────────────┼──────────────────┐
         │                     │                   │
         ▼                     ▼                   ▼
┌──────────────┐    ┌──────────────────┐  ┌──────────────────┐
│ reading_prog │    │    bookmarks      │  │ word_dictionary  │
└──────────────┘    └──────────────────┘  └──────────────────┘
         │
         ├─────────────────────┐
         │                     │
         ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│ reading_records  │  │ reading_history   │
│ (阅读记录)       │  │ (阅读历史)        │
└──────────────────┘  └──────────────────┘
```

### 2.2 表结构

```sql
-- 用户表
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    avatar VARCHAR(500) DEFAULT '/default-avatar.png',  -- 头像URL
    role VARCHAR(20) DEFAULT 'USER',  -- USER, ADMIN
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 图书表
CREATE TABLE books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255),
    cover_url VARCHAR(500),
    source_type VARCHAR(20) NOT NULL,  -- TXT, EPUB
    file_path VARCHAR(500) NOT NULL,
    category_id BIGINT,
    upload_user_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PROCESSING',  -- PROCESSING, READY, ERROR
    word_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (upload_user_id) REFERENCES users(id)
);

-- 分类表
CREATE TABLE categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    sort_order INT DEFAULT 0
);

-- 章节表
CREATE TABLE chapters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    chapter_order INT NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    UNIQUE KEY uk_book_order (book_id, chapter_order)
);

-- 段落表
CREATE TABLE paragraphs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chapter_id BIGINT NOT NULL,
    paragraph_order INT NOT NULL,
    original_text TEXT NOT NULL,        -- 原始日文
    segmented_text TEXT,                -- 分词结果JSON
    translation TEXT,                   -- 翻译结果
    furigana_text TEXT,                 -- 注音结果JSON
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
    UNIQUE KEY uk_chapter_para (chapter_id, paragraph_order)
);

-- 阅读进度表
CREATE TABLE reading_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    paragraph_id BIGINT NOT NULL,
    scroll_position INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id),
    FOREIGN KEY (paragraph_id) REFERENCES paragraphs(id),
    UNIQUE KEY uk_user_book (user_id, book_id)
);

-- 书签表
CREATE TABLE bookmarks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    paragraph_id BIGINT NOT NULL,
    note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    FOREIGN KEY (paragraph_id) REFERENCES paragraphs(id)
);

-- 生词本表
CREATE TABLE word_dictionary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    word VARCHAR(100) NOT NULL,
    reading VARCHAR(200),              -- 读音
    meaning TEXT,                       -- 释义
    example_sentence TEXT,             -- 例句
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_word (user_id, word)
);

-- 语法笔记表
CREATE TABLE grammar_notes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    paragraph_id BIGINT NOT NULL,
    grammar_pattern VARCHAR(255),      -- 语法句型
    explanation TEXT,                   -- 解释
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    FOREIGN KEY (paragraph_id) REFERENCES paragraphs(id)
);

-- 阅读记录表
CREATE TABLE reading_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    chapter_id BIGINT,
    read_date DATE NOT NULL,           -- 阅读日期
    read_seconds INT DEFAULT 0,        -- 当日阅读秒数
    paragraphs_studied INT DEFAULT 0,   -- 学习段落数
    translations_viewed INT DEFAULT 0,  -- 查看翻译次数
    furigana_viewed INT DEFAULT 0,     -- 查看注音次数
    grammar_viewed INT DEFAULT 0,       -- 查看语法次数
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id),
    UNIQUE KEY uk_user_date (user_id, read_date)
);

-- 阅读历史表（最近阅读的书籍）
CREATE TABLE reading_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    last_chapter_id BIGINT,
    last_read_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_book (user_id, book_id)
);
```

---

## 3. 后端 API 设计

### 3.1 认证模块 `/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register` | 用户注册 |
| POST | `/auth/login` | 登录，返回JWT |
| POST | `/auth/refresh` | 刷新Token |
| GET | `/auth/me` | 获取当前用户信息 |

### 3.2 用户信息模块 `/api/users`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/users/me` | 获取个人信息 |
| PUT | `/users/me` | 更新个人信息 |
| PUT | `/users/password` | 修改密码 |
| POST | `/users/avatar` | 上传头像 |
| DELETE | `/users/avatar` | 删除头像 |

### 3.3 图书模块 `/api/books`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/books/upload` | 上传小说文件 |
| GET | `/books` | 图书列表（分页） |
| GET | `/books/{id}` | 图书详情 |
| DELETE | `/books/{id}` | 删除图书 |
| GET | `/books/{id}/chapters` | 获取章节列表 |
| GET | `/books/{id}/progress` | 获取阅读进度 |
| PUT | `/books/{id}/progress` | 更新阅读进度 |

### 3.4 阅读模块 `/api/read`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/read/{chapterId}/paragraphs` | 获取章节段落 |
| POST | `/read/paragraph/{id}/translate` | 翻译段落 |
| POST | `/read/paragraph/{id}/furigana` | 获取注音 |
| POST | `/read/paragraph/{id}/grammar` | 语法解析 |
| GET | `/read/word/{word}/definition` | 单词查询 |

### 3.5 书签模块 `/api/bookmarks`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/bookmarks` | 我的书签 |
| POST | `/bookmarks` | 添加书签 |
| DELETE | `/bookmarks/{id}` | 删除书签 |

### 3.6 生词本模块 `/api/words`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/words` | 我的生词本 |
| POST | `/words` | 添加生词 |
| DELETE | `/words/{id}` | 删除生词 |

### 3.7 阅读统计模块 `/api/read-stats`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/read-stats/today` | 今日阅读时长 |
| GET | `/read-stats/history` | 阅读历史（最近阅读的书籍） |
| GET | `/read-stats/records` | 阅读记录列表（分页） |
| POST | `/read-stats/records` | 记录阅读时长 |

---

## 4. 前端页面设计

### 4.1 页面路由

```javascript
const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login },
  { path: '/register', component: Register },
  { path: '/shelf', component: Shelf },           // 书架（首页）
  { path: '/shelf/upload', component: Upload },  // 上传小说
  { path: '/read/:bookId', component: Reader,  // 阅读器
    children: [
      { path: '', redirect: 'chapter/1' },
      { path: 'chapter/:chapterId', component: ChapterView }
    ]
  },
  { path: '/bookmarks', component: Bookmarks },    // 书签页
  { path: '/words', component: WordBook },         // 生词本
  { path: '/profile', component: Profile }         // 个人设置
]
```

### 4.2 书架页面（番茄小说风格）

书架是用户上传小说后的主页面，参考番茄小说设计：

```
┌─────────────────────────────────────────────────────────────────────┐
│  我的书架                                    [搜索]  [👤] [⚙]   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [全部] [文学] [轻小说] [同人] [原创] [+ 分类]   ← 分类标签       │
│                                                                       │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐               │
│  │         │  │         │  │         │  │         │               │
│  │  封面   │  │  封面   │  │  封面   │  │  + 添加  │               │
│  │         │  │         │  │         │  │  新书    │               │
│  │ 书名    │  │ 书名    │  │ 书名    │  │         │               │
│  │ 作者名   │  │ 作者名   │  │ 作者名   │  │         │               │
│  │ ████░░ │  │ ██████░ │  │ ██░░░░ │  │         │               │
│  │ 40%    │  │ 100%    │  │ 20%    │  │         │               │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘               │
│                                                                       │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐               │
│  │  封面   │  │  封面   │  │  封面   │  │  封面   │               │
│  │         │  │         │  │         │  │         │               │
│  │ 书名    │  │ 书名    │  │ 书名    │  │ 书名    │               │
│  │ 作者名   │  │ 作者名   │  │ 作者名   │  │ 作者名   │               │
│  │ ████░░ │  │ ██████░ │  │ ██░░░░ │  │ ██░░░░ │               │
│  │ 40%    │  │ 100%    │  │ 20%    │  │ 15%    │               │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘               │
│                                                                       │
│  ┌─────────┐                                                       │
│  │  封面   │                                                       │
│  │         │                                                       │
│  │ 书名    │                                                       │
│  │ 作者名   │                                                       │
│  │ ██░░░░ │                                                       │
│  │ 10%    │                                                       │
│  └─────────┘                                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

**书架交互：**

| 交互 | 行为 |
|------|------|
| 点击图书封面 | 跳转到阅读器上次阅读位置 |
| 右键/长按图书 | 显示菜单（删除、查看信息、移到分类） |
| 点击"添加新书" | 弹出上传页面 |
| 点击分类标签 | 筛选该分类下的图书 |
| 点击搜索 | 搜索书名/作者 |

**书架卡片组件：**

```vue
<template>
  <div class="book-card" @click="openBook(book)">
    <div class="book-cover">
      <img :src="book.coverUrl || '/default-cover.png'" :alt="book.title">
      <!-- 阅读进度条 -->
      <div class="progress-bar" v-if="book.readProgress > 0">
        <div class="progress-fill" :style="{ width: book.readProgress + '%' }"></div>
      </div>
    </div>
    <div class="book-info">
      <div class="book-title">{{ book.title }}</div>
      <div class="book-author">{{ book.author }}</div>
      <div class="book-progress" v-if="book.readProgress > 0">
        {{ book.readProgress }}%
      </div>
    </div>

    <!-- 右键菜单 -->
    <div class="book-menu" v-if="showMenu" @click.stop>
      <div @click="showBookInfo">查看信息</div>
      <div @click="moveToCategory">移动分类</div>
      <div @click="deleteBook" class="danger">删除</div>
    </div>
  </div>
</template>
```

**书架卡片样式：**

```css
/* 书架网格布局 */
.shelf-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 20px;
  padding: 20px;
}

/* 图书卡片 */
.book-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s;
}

.book-card:hover {
  transform: translateY(-4px);
}

.book-card .book-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 3/4;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.book-card .book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.book-card .progress-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: rgba(0,0,0,0.3);
}

.book-card .progress-fill {
  height: 100%;
  background: #1a73e8;
}

.book-card .book-info {
  padding: 8px 4px 0;
}

.book-card .book-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.book-card .book-author {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.book-card .book-progress {
  font-size: 11px;
  color: #1a73e8;
  margin-top: 2px;
}

/* 添加新书卡片 */
.book-card.add-new {
  border: 2px dashed #ddd;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  color: #999;
  transition: all 0.2s;
}

.book-card.add-new:hover {
  border-color: #1a73e8;
  color: #1a73e8;
}

.book-card.add-new .add-icon {
  font-size: 48px;
  margin-bottom: 8px;
}
```

**分类标签栏：**

```css
/* 分类标签 */
.category-tabs {
  display: flex;
  gap: 8px;
  padding: 16px 20px;
  overflow-x: auto;
  border-bottom: 1px solid #eee;
}

.category-tab {
  padding: 8px 16px;
  font-size: 14px;
  color: #666;
  background: #f5f5f5;
  border-radius: 20px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s;
}

.category-tab:hover {
  background: #e8f0fe;
  color: #1a73e8;
}

.category-tab.active {
  background: #1a73e8;
  color: white;
}

.category-tab.add-category {
  border: 1px dashed #ccc;
  background: transparent;
}
```

### 4.3 阅读界面视觉规范

#### 设计风格
- **风格参考**: 简约现代、类似 GitHub Readme / Notion / 微信读书
- **背景色**: 米白/护眼模式 (模仿纸书质感)
- **字体**: 采用衬线体用于正文，增强阅读体验

#### 配色方案
```css
:root {
  /* 明亮模式 */
  --bg-primary: #faf8f5;        /* 米白背景 */
  --bg-secondary: #f0ede8;     /* 卡片背景 */
  --bg-hover: #e8e4dd;          /* 悬停背景 */
  --text-primary: #2c2c2c;      /* 主文字 */
  --text-secondary: #666666;    /* 次要文字 */
  --text-muted: #999999;        /* 弱化文字 */
  --accent-primary: #4a90d9;   /* 主色调-蓝 */
  --accent-success: #52c41a;   /* 成功-绿 */
  --accent-warning: #fa8c16;   /* 警告-橙 */
  --accent-error: #f5222d;      /* 错误-红 */
  --furigana-color: #4a90d9;    /* 注音颜色 */
  --grammar-color: #722ed1;     /* 语法高亮-紫 */
  --word-highlight: #fff3bf;    /* 单词选中背景 */
  --border-color: #e0dbd4;      /* 边框色 */
  --shadow: rgba(0,0,0,0.08);   /* 阴影 */

  /* 夜间模式 */
  --dark-bg-primary: #1a1a1a;
  --dark-bg-secondary: #252525;
  --dark-text-primary: #e0e0e0;
  --dark-text-secondary: #a0a0a0;
}
```

#### 字体规范
```css
/* 日文正文 - 衬线体增强阅读感 */
.reader-text {
  font-family: "Source Han Serif JP", "Noto Serif JP", "Hiragino Mincho Pro", serif;
  font-size: 18px;
  line-height: 1.9;           /* 日文阅读行高略大 */
  letter-spacing: 0.05em;
}

/* 中文/翻译 - 无衬线体 */
.translation-text {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 15px;
  line-height: 1.7;
  color: var(--text-secondary);
}

/* 注音/假名 */
.ruby-text rt {
  font-family: "Hiragino Sans", sans-serif;
  font-size: 10px;
  color: var(--furigana-color);
}

/* 语法标注 */
.grammar-tag {
  font-family: monospace;
  font-size: 13px;
  background: linear-gradient(135deg, #722ed1 0%, #eb7fd4 100%);
  color: white;
  padding: 2px 8px;
  border-radius: 4px;
}
```

---

### 4.4 阅读器核心布局

详细布局和交互见 **4.12 阅读器整体布局（浏览器端优化版）**

---

### 4.5 段落交互设计（点击展开式）

#### 设计原则
- **纯文本阅读**：默认仅显示日文原文，无任何UI干扰
- **自带注音优先**：如果小说原文已有注音（振り仮名），优先显示原文注音
- **点击选中**：点击段落任意位置，选中该段落
- **按需展开**：点击操作按钮后才显示翻译/注音/语法

#### 自带注音的处理

**来源**：EPUB/TXT中的注音可能来自：
1. EPUB的`<ruby>`标签（rt元素）
2. TXT中的旁注标记（如：「漢字【かんじ】」）

**显示规则**：
```
原文自带注音（如《日本的四季》这类精校电子书）:
┌─────────────────────────────────────────────┐
│  抵【いた】り着【つ】いた。                 │
│  勉学【べんがく】の日々が続【つづ】く。     │
└─────────────────────────────────────────────┘
      ↓ (解析后渲染为)
      抵(いた)り着(つ)いた。
      勉学(べんがく)の日々が続(つず)く。
```

**数据存储格式**：
```json
{
  "originalText": "抵り着いた。勉学の日々が続く。",
  "furiganaSegments": [
    { "text": "抵", "reading": "いた", "isExternal": true },
    { "text": "り", "reading": null, "isExternal": false },
    { "text": "着", "reading": "つ", "isExternal": true },
    { "text": "い", "reading": null, "isExternal": false },
    { "text": "た", "reading": null, "isExternal": false },
    { "text": "勉学", "reading": "べんがく", "isExternal": true },
    ...
  ]
}
```

**渲染逻辑**：
```javascript
// 渲染段落文本，优先使用自带注音
function renderParagraph(paragraph) {
  if (paragraph.furiganaSegments && showFurigana) {
    // 使用预存的注音分段
    return paragraph.furiganaSegments.map(seg => {
      if (seg.reading) {
        return `<ruby>${seg.text}<rt>${seg.reading}</rt></ruby>`
      } else {
        return seg.text
      }
    }).join('')
  } else if (showFurigana && !paragraph.furiganaSegments) {
    // 没有自带注音，使用分词器的注音
    return tokenizeWithReading(paragraph.text)
  } else {
    // 无注音模式，直接返回原文
    return paragraph.text
  }
}
```

#### 交互流程

```
状态1: 默认阅读（纯文本，无分隔线）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ここにようやく到着した。勉学の日々が続く。
それでも、明日への希望を捨てなかった。
それは小さな灯りのように胸に灯り続けた。


状态2: 点击段落后（显示操作条）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ここにようやく到着した。勉学の日々が続く。
それでも、明日への希望を捨てなかった。
那是小さな灯りのように胸に灯り続けた。
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[📖翻译]  [あ注音]  [📚语法]


状态3: 点击「翻译」后（展开翻译）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ここにようやく到着した。勉学の日々が続く。
それでも、明日への希望を捨てなかった。
那是小さな灯りのように胸に灯り続けた。
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[📖翻译 ✓]  [あ注音]  [📚语法]

┌─────────────────────────────────────────┐
│  终于到达这里了。继续着勤奋学习的日子。   │
│  [复制]  [加入生词本]                    │
└─────────────────────────────────────────┘


状态4: 点击「注音」后（显示注音）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ここにようやく到着した。勉学の日々が続く。
这里(ここ) に ようやく(ようや) 到着(とうちゃく) した。
勉学(べんがく) の 日々(ひび) が 続く(つずく)。

[📖翻译 ✓]  [あ注音 ✓]  [📚语法]


状态5: 原文自带注音 + 点击注音按钮
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
抵り着いた。勉学の日々が続いている。

(原文自带注音，勾选注音后直接显示)
抵(いた)り着(つ)いた。
勉学(べんがく) の 日々(ひび) が 続(つず)いている。

[📖翻译]  [あ注音 ✓]  [📚语法]
```

#### Vue 组件实现

```vue
<template>
  <div class="reader-content">
    <div
      v-for="paragraph in paragraphs"
      :key="paragraph.id"
      class="paragraph"
      :class="{ active: activeParagraph === paragraph.id }"
      @click="selectParagraph(paragraph)"
    >
      <!-- 日文原文 -->
      <div class="para-text">{{ paragraph.text }}</div>

      <!-- 操作条 (仅当前段落显示) -->
      <div class="action-bar" v-if="activeParagraph === paragraph.id">
        <button
          class="action-btn"
          :class="{ active: paragraph.showTranslation }"
          @click.stop="toggleTranslation(paragraph)"
        >
          📖 翻译
        </button>
        <button
          class="action-btn"
          :class="{ active: paragraph.showFurigana }"
          @click.stop="toggleFurigana(paragraph)"
        >
          あ 注音
        </button>
        <button
          class="action-btn"
          :class="{ active: paragraph.showGrammar }"
          @click.stop="toggleGrammar(paragraph)"
        >
          📚 语法
        </button>
      </div>

      <!-- 展开内容 -->
      <div class="expand-content" v-if="hasExpandContent(paragraph)">
        <div v-if="paragraph.showTranslation" class="translation-box">
          {{ paragraph.translation }}
        </div>
        <div v-if="paragraph.showFurigana" class="furigana-box">
          <span
            v-for="(token, idx) in paragraph.tokens"
            :key="idx"
            class="token"
          >
            <ruby>
              {{ token.surface }}
              <rt v-if="token.reading">{{ token.reading }}</rt>
            </ruby>
          </span>
        </div>
        <div v-if="paragraph.showGrammar" class="grammar-box">
          <div
            v-for="g in paragraph.grammarPatterns"
            :key="g.name"
            class="grammar-item"
          >
            <span class="grammar-tag">{{ g.name }}</span>
            <span class="grammar-desc">{{ g.explanation }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const activeParagraph = ref(null)

function selectParagraph(p) {
  activeParagraph.value = activeParagraph.value === p.id ? null : p.id
}

function toggleTranslation(p) {
  if (!p.translation) {
    fetchTranslation(p)
  }
  p.showTranslation = !p.showTranslation
}

// ... 类似方法处理注音和语法
</script>
```

#### CSS 样式

```css
/* 段落容器 - 默认纯文本，无边框无背景 */
.paragraph {
  position: relative;
  padding: 16px 0;
  cursor: pointer;
  transition: background 0.15s;
}

/* 默认阅读：无任何装饰，仅文本 */
.paragraph .para-text {
  font-family: "Noto Serif JP", "Hiragino Mincho Pro", serif;
  font-size: 17px;
  line-height: 2;
  color: #333;
  text-align: justify;
}

/* 选中状态：轻微背景 + 底部细线 + 操作条 */
.paragraph.active {
  background: rgba(0, 0, 0, 0.02);
}

.paragraph.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: #e0e0e0;
}

/* 操作条 */
.paragraph .action-bar {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.paragraph .action-btn {
  padding: 6px 14px;
  font-size: 13px;
  border: 1px solid #ddd;
  border-radius: 16px;
  background: white;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.paragraph .action-btn:hover {
  border-color: #1a73e8;
  color: #1a73e8;
}

.paragraph .action-btn.active {
  background: #1a73e8;
  border-color: #1a73e8;
  color: white;
}

/* 展开区域 */
.paragraph .expand-content {
  margin-top: 14px;
  animation: slideDown 0.25s ease;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 翻译框 */
.paragraph .translation-box {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 14px 16px;
  font-family: "PingFang SC", sans-serif;
  font-size: 15px;
  line-height: 1.8;
  color: #333;
}

/* 注音样式 */
.paragraph .furigana-box ruby {
  ruby-position: over;
}

.paragraph .furigana-box rt {
  font-family: "Hiragino Sans", sans-serif;
  font-size: 9px;
  color: #666;
}

/* 语法框 */
.paragraph .grammar-box {
  background: #fafafa;
  border-radius: 8px;
  padding: 14px 16px;
  border-left: 3px solid #9334e6;
}

.paragraph .grammar-item {
  margin-bottom: 8px;
  font-size: 14px;
}

.paragraph .grammar-item:last-child {
  margin-bottom: 0;
}

.paragraph .grammar-tag {
  display: inline-block;
  font-size: 12px;
  padding: 2px 6px;
  background: linear-gradient(135deg, #9334e6, #b65fc6);
  color: white;
  border-radius: 4px;
  margin-right: 6px;
}
```

---

### 4.6 展开内容样式

点击操作按钮后，翻译/注音/语法在段落下方直接展开（inline），而非独立面板。

```
┌──────────────────────────────────────────────────────────────┐
│  📖 翻译                                         [× 关闭]   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                                                        │ │
│  │  终于到达这里了。                                       │ │
│  │  继续着勤奋学习的日子。                                  │ │
│  │                                                        │ │
│  │  ───────────────────────────────────────────────────   │ │
│  │                                                        │ │
│  │  📝 机器翻译 · Google                                  │ │
│  │                                                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │ 复制全文  │  │ 查看原文  │  │ 语法分析  │                 │
│  └──────────┘  └──────────┘  └──────────┘                 │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**样式代码：**
```css
.translation-panel {
  background: linear-gradient(145deg, #f7f9fc 0%, #edf2f7 100%);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  margin-top: 16px;
  overflow: hidden;
}

.translation-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: white;
  border-bottom: 1px solid var(--border-color);
  font-weight: 500;
}

.translation-panel .panel-body {
  padding: 20px;
}

.translation-panel .translated-text {
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 1.8;
  color: var(--text-primary);
}

.translation-panel .meta-info {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-color);
  font-size: 12px;
  color: var(--text-muted);
}
```

---

### 4.7 注音样式

点击「注音」按钮后，段落内联显示假名注音：

**显示效果：**
```
ここにようやく到着した。勉学の日々が続く。
ここに(に) ようやく(ようや) 到着(とうちゃく) した。勉学(べんがく)の 日々(ひび) が 続(つず)く。
```

**Ruby 标签渲染：**
```css
/* 注音模式 */
.para-text.with-furigana ruby {
  ruby-position: over;     /* 注音显示在文字上方 */
}

.para-text.with-furigana rt {
  font-family: "Hiragino Sans", sans-serif;
  font-size: 9px;
  color: #666;
  font-weight: normal;
}

/* 自带注音与分词注音样式一致 */
.para-text ruby.external {
  /* 原文自带注音，可选特殊样式区分 */
}

.para-text ruby.generated {
  /* 分词器生成的注音 */
}
```

**HTML 输出示例：**
```html
<!-- 自带注音 -->
<ruby class="external">勉学<rt>べんがく</rt></ruby>

<!-- 分词器生成 -->
<ruby class="generated">到着<rt>とうちゃく</rt></ruby>した
```

---

### 4.8 语法样式

点击「学习」后，inline展开语法分析：

```
┌──────────────────────────────────────────────────────────────┐
│  📚 语法分析                                     [× 关闭]   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─ 检测到的语法句型 ──────────────────────────────────────┐  │
│  │                                                        │  │
│  │  ┌────────────────────────────────────────────────┐   │  │
│  │  │ ★ に (格助词)                                   │   │  │
│  │  │    表示到达地点、动作的归着点                     │   │  │
│  │  │    → ここに / 到着した                           │   │  │
│  │  └────────────────────────────────────────────────┘   │  │
│  │                                                        │  │
│  │  ┌────────────────────────────────────────────────┐   │  │
│  │  │ ★ ～た (动词た形)                               │   │  │
│  │  │    表示动作的过去、完了                          │   │  │
│  │  │    → 到着した (到达了)                          │   │  │
│  │  └────────────────────────────────────────────────┘   │  │
│  │                                                        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ 单词分析 ─────────────────────────────────────────────┐  │
│  │                                                        │  │
│  │  ここ    [代词]    这里、此处                          │  │
│  │  に      [助词]    到达地点格助词                       │  │
│  │  ようやく[副词]    终于、总算                          │  │
│  │  到着    [名/動]  到达、抵达                          │  │
│  │  した    [助動]   过去、完了                          │  │
│  │                                                        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**样式代码：**
```css
.grammar-panel {
  background: linear-gradient(145deg, #faf8ff 0%, #f3f0f7 100%);
  border: 1px solid #e0d8eb;
  border-radius: 12px;
  margin-top: 16px;
}

.grammar-panel .section {
  padding: 16px;
  border-bottom: 1px solid #e0d8eb;
}

.grammar-panel .section:last-child {
  border-bottom: none;
}

.grammar-panel .grammar-item {
  background: white;
  border-radius: 8px;
  padding: 14px 16px;
  margin: 10px 0;
  border-left: 4px solid var(--grammar-color);
}

.grammar-panel .grammar-name {
  font-weight: 600;
  color: var(--grammar-color);
  margin-bottom: 6px;
}

.grammar-panel .grammar-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 4px 0;
}

.grammar-panel .grammar-example {
  font-size: 13px;
  color: var(--text-muted);
  padding-left: 12px;
  border-left: 2px solid var(--border-color);
  margin-top: 8px;
}

.grammar-panel .word-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}

.grammar-panel .word-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: white;
  border-radius: 6px;
  font-size: 14px;
}

.grammar-panel .word-surface {
  font-weight: 600;
  color: var(--text-primary);
  min-width: 60px;
}

.grammar-panel .word-pos {
  font-size: 11px;
  padding: 2px 6px;
  background: #f0f0f0;
  border-radius: 4px;
  color: var(--text-muted);
}

.grammar-panel .word-meaning {
  color: var(--text-secondary);
}
```

---

### 4.9 单词悬浮卡片 (Word Popup)

当用户点击日文单词时，显示悬浮卡片：

```
    ┌─────────────────────────────────────────┐
    │                                    [×]  │
    │                                         │
    │  勉学 (べんがく)                        │
    │  ════════════════                       │
    │                                         │
    │  📖 释义                                │
    │  学业，用功学习；勤学                    │
    │                                         │
    │  📝 词性                                │
    │  名词 · サ変名詞                         │
    │                                         │
    │  🔗 相关语法                            │
    │  勉学を积む (积累学业)                  │
    │                                         │
    │  ┌────────────┐  ┌────────────┐        │
    │  │  ⭐ 加入生词 │  │  📝 笔记   │        │
    │  └────────────┘  └────────────┘        │
    │                                         │
    └─────────────────────────────────────────┘
         │
         ▼ 点击位置
    勉学，这可是很重要的事情だ。
```

**样式代码：**
```css
.word-popup {
  position: absolute;
  z-index: 1000;
  width: 280px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.15);
  overflow: hidden;
  animation: popupFadeIn 0.2s ease-out;
}

@keyframes popupFadeIn {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.word-popup .popup-header {
  padding: 14px 16px;
  background: linear-gradient(135deg, #4a90d9 0%, #357abd 100%);
  color: white;
}

.word-popup .popup-word {
  font-family: "Noto Serif JP", serif;
  font-size: 22px;
  font-weight: 600;
}

.word-popup .popup-reading {
  font-size: 14px;
  opacity: 0.9;
  margin-top: 4px;
}

.word-popup .popup-body {
  padding: 14px 16px;
}

.word-popup .popup-section {
  margin-bottom: 12px;
}

.word-popup .popup-section:last-child {
  margin-bottom: 0;
}

.word-popup .section-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.word-popup .section-content {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.5;
}

.word-popup .popup-actions {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  background: #fafafa;
  border-top: 1px solid var(--border-color);
}

.word-popup .popup-btn {
  flex: 1;
  padding: 8px 12px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.word-popup .btn-primary {
  background: var(--accent-primary);
  color: white;
}

.word-popup .btn-primary:hover {
  background: #357abd;
}
```

---

### 4.10 单词点击交互逻辑

```javascript
// ParagraphBlock.vue - 处理单词点击

function handleTextClick(event) {
  const selection = window.getSelection()
  const clickedText = selection.toString().trim()

  // 如果用户有选中文本
  if (clickedText && clickedText.length > 0) {
    // 查询选中文本的意思
    showWordPopup(clickedText, event)
    return
  }

  // 否则检测点击位置是否是单词
  const target = event.target

  // 获取点击坐标
  const rect = target.getBoundingClientRect()
  const clickX = event.clientX - rect.left
  const clickY = event.clientY - rect.top

  // 获取文本内容
  const textNode = findTextNode(target, clickX, clickY)
  if (!textNode) return

  const fullText = textNode.textContent
  const offset = getOffsetInTextNode(textNode, clickX)

  // 使用分词器找到点击的单词
  const wordInfo = findWordAtPosition(fullText, offset, tokenizer)
  if (wordInfo) {
    showWordPopup(wordInfo.word, event, wordInfo)
  }
}

// 使用 kuromoji 分词找到对应单词
function findWordAtPosition(text, clickPos, tokenizer) {
  const tokens = tokenizer.tokenize(text)

  let charCount = 0
  for (const token of tokens) {
    const tokenLen = token.surface.length
    if (clickPos >= charCount && clickPos < charCount + tokenLen) {
      return {
        word: token.surface,
        reading: token.reading,
        pos: token.pos,
        posDetail: token.posDetail1
      }
    }
    charCount += tokenLen
  }
  return null
}
```

---

### 4.11 目录与设置面板

#### 左侧章节目录（可折叠抽屉）

```
┌─────────────────────────────────┐
│  书名                         │
│  ─────────────────────────────  │
│                                 │
│  ☰ 第一章  序章               │
│  ☰ 第二章  出会い             │
│  ☰ 第三章  成長               │
│  ☰ 第四章  試練               │
│  ☰ 第五章  突破               │
│  ...                           │
│                                 │
│  ☰ 第十二章 終章              │
│                                 │
└─────────────────────────────────┘
(点击☰图标或滑动手势打开)
```

**目录样式：**
```css
.chapter-drawer {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 280px;
  background: white;
  z-index: 200;
  transform: translateX(-100%);
  transition: transform 0.3s ease;
  box-shadow: 4px 0 20px rgba(0,0,0,0.1);
}

.chapter-drawer.open {
  transform: translateX(0);
}

.chapter-item {
  padding: 14px 20px;
  font-size: 15px;
  color: #333;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: all 0.15s;
}

.chapter-item:hover {
  background: #f5f5f5;
}

.chapter-item.active {
  background: #e8f0fe;
  border-left-color: #1a73e8;
  color: #1a73e8;
}
```

#### 顶部 Header

```
[☰ 目录]  书名 - 第一章              [🌙 夜间] [Aa 设置] [👤 用户]
```

#### 目录按钮交互（点击弹出下拉）

点击「☰ 目录」按钮，弹出章节目录列表：

```
┌─────────────────────────────────────────────────────────────┐
│  书名                                                  [×] │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  ☰ 第1章 序章                                    [100%]  │
│  ☰ 第2章 出会い                                  [0%]   │
│  ☰ 第3章 成長                                     [0%]   │
│  ☰ 第4章 試練                                     [0%]   │
│  ☰ 第5章 突破                                     [0%]   │
│  ...                                                    │
│  ☰ 第12章 終章                                    [0%]   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**交互特点：**
- 点击「☰ 目录」按钮 → 弹出目录浮层
- 点击章节 → 跳转并关闭浮层
- 点击空白处或「×」→ 关闭浮层
- 显示阅读进度百分比

#### 阅读设置面板（番茄小说风格）

点击「Aa」按钮，弹出阅读设置面板：

```
┌─────────────────────────────────────────────────────────────┐
│  阅读设置                                               [×] │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  ┌─ 字体大小 ───────────────────────────────────────────┐  │
│  │                                                         │  │
│  │  Aa-                    ●━━━━━━━━━━━                  Aa+  │
│  │         (拖动滑块或点击调整，范围 14px ~ 24px)          │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─ 字体样式 ───────────────────────────────────────────┐  │
│  │                                                         │  │
│  │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐    │  │
│  │  │默认字体│  │黑体   │  │楷体   │  │明朝体  │    │  │
│  │  │ PingFang│  │Heiti │  │Kaiti │  │Mincho │    │  │
│  │  └────────┘  └────────┘  └────────┘  └────────┘    │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─ 背景颜色 ───────────────────────────────────────────┐  │
│  │                                                         │  │
│  │    ○      ○      ○      ○      ○                    │  │
│  │  羊皮纸  明亮白  护眼绿  夜间黑  粉红               │  │
│  │  #f5e6d3 #faf8f5 #f0f4e8 #1a1a1a #fce4ec           │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─ 行距 ───────────────────────────────────────────────┐  │
│  │                                                         │  │
│  │  ┌─ 紧凑 ─┐  ┌─ 标准 ─┐  ┌─ 宽松 ─┐               │  │
│  │  │ 1.5   │  │ 1.8   │  │ 2.2   │               │  │
│  │  └───────┘  └───────┘  └───────┘                │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─ 页面宽度 ───────────────────────────────────────────┐  │
│  │                                                         │  │
│  │  ┌─ 窄 ─┐  ┌─ 中 ─┐  ┌─ 宽 ─┐  ┌─ 全宽 ─┐       │  │
│  │  │ 680px│  │ 820px│  │ 960px│  │ 100%  │       │  │
│  │  └─────┘  └─────┘  └─────┘  └───────┘            │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**设置项详细说明：**

```css
/* 阅读设置面板 */
.setting-panel {
  position: fixed;
  top: 48px;        /* header 下方 */
  right: 60px;      /* Aa按钮右侧 */
  width: 360px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.15);
  z-index: 200;
  animation: slideDown 0.2s ease;
  max-height: 80vh;
  overflow-y: auto;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 字体大小滑块 */
.font-size-slider {
  display: flex;
  align-items: center;
  gap: 12px;
}

.font-size-slider input[type="range"] {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: #e0e0e0;
  -webkit-appearance: none;
}

.font-size-slider input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #1a73e8;
  cursor: pointer;
}

/* 字体样式选择 */
.font-style-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.font-style-item {
  padding: 10px 8px;
  text-align: center;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13px;
}

.font-style-item:hover {
  border-color: #1a73e8;
}

.font-style-item.active {
  border-color: #1a73e8;
  background: #e8f0fe;
}

.font-style-item .font-preview {
  font-size: 18px;
  margin-bottom: 4px;
}

/* 背景颜色选择 */
.bg-color-grid {
  display: flex;
  gap: 12px;
}

.bg-color-item {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  cursor: pointer;
  border: 3px solid transparent;
  transition: all 0.2s;
}

.bg-color-item:hover {
  transform: scale(1.1);
}

.bg-color-item.active {
  border-color: #1a73e8;
  box-shadow: 0 0 0 2px white, 0 0 0 4px #1a73e8;
}

/* 行距/宽度选项 */
.option-group {
  display: flex;
  gap: 8px;
}

.option-item {
  flex: 1;
  padding: 10px;
  text-align: center;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.option-item:hover {
  border-color: #1a73e8;
}

.option-item.active {
  border-color: #1a73e8;
  background: #e8f0fe;
  color: #1a73e8;
}
```

**阅读设置保存：**
```javascript
// 设置持久化到 localStorage
const readerSettings = {
  fontSize: 17,        // 14-24
  fontFamily: 'PingFang SC',
  background: '#faf8f5',
  lineHeight: 1.8,
  pageWidth: 820,     // 680, 820, 960, '100%'
  nightMode: false
}

// 加载设置
function loadSettings() {
  const saved = localStorage.getItem('readerSettings')
  if (saved) {
    return JSON.parse(saved)
  }
  return readerSettings
}

// 保存设置
function saveSettings(settings) {
  localStorage.setItem('readerSettings', JSON.stringify(settings))
}
```

**夜间模式样式：**
```css
body.night-mode {
  /* 背景 */
  --bg-page: #1a1a1a;
  --bg-content: #252525;

  /* 文字 */
  --text-primary: #e0e0e0;
  --text-secondary: #a0a0a0;

  /* 边框 */
  --border-color: #333;

  /* 阅读器背景 */
  background: #1a1a1a;
  color: #e0e0e0;
}

body.night-mode .reader-content {
  background: #1a1a1a;
}

body.night-mode .paragraph.active {
  background: rgba(255, 255, 255, 0.05);
}
```

---

### 4.12 阅读器整体布局（浏览器端优化版）

#### 核心设计理念

浏览器端与App端不同，应采用更适合PC/Mac的交互方式：

| 特性 | App端 | 浏览器端 |
|------|-------|----------|
| 导航 | 可隐藏 | **保持可见** |
| 翻页 | 左右滑动 | **滚轮/jk键/点击** |
| 操作 | 触屏手势 | **鼠标悬停** |
| 屏幕 | 竖屏手机 | **横屏宽屏** |
| 键盘 | 不支持 | **完整快捷键** |

#### 浏览器端布局

```
┌─────────────────────────────────────────────────────────────────────┐
│  [☰ 目录]  书名 - 第一章 序章              [夜间] [Aa] [🎧 听书]   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                                                                       │
│      ┌───────────────────────────────────────────────────────────┐   │
│      │                                                            │   │
│      │   ここにようやく到着した。勉学の日々が続く。                 │   │
│      │   それでも、明日への希望を捨てなかった。                     │   │
│      │                                                            │   │
│      │   那是小さな灯りのように胸に灯り続けた。                    │   │
│      │                                                            │   │
│      │   ┌─────────────────────────────────────────────────┐     │   │
│      │   │  [📖翻译]  [あ注音]  [📚语法]                  │     │ ← hover显示  │
│      │   └─────────────────────────────────────────────────┘     │   │
│      │                                                            │   │
│      │   ┌─────────────────────────────────────────────────┐     │   │
│      │   │  终于到达这里了。继续着勤奋学习的日子。            │     │   │
│      │   │  [复制] [加入生词本]                             │     │   │
│      │   └─────────────────────────────────────────────────┘     │   │
│      │                                                            │   │
│      │   それでも、明日への希望を捨てなかった。                     │   │
│      │   那是小さな灯りのように胸に灯り続けた。                   │   │
│      │                                                            │   │
│      └───────────────────────────────────────────────────────────┘   │
│                                                                       │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│  [◀ 上一章]    第 1 章 / 共 12 章    [下一章 ▶]                     │
│  ════════════════════●═══════════════════  8%                        │
└─────────────────────────────────────────────────────────────────────┘
```

#### 关键交互方式

**1. 滚轮翻页（主要）**
- 鼠标滚轮向上滚动 → 下一段
- 鼠标滚轮向下滚动 → 上一段
- 或使用 PageUp/PageDown 键

**2. 键盘快捷键（完整支持）**
```javascript
'j': 'nextParagraph',     // 下一段
'k': 'prevParagraph',     // 上一段
'Enter': 'nextParagraph', // 回车下一页
'Space': 'nextParagraph', // 空格下一页
'[': 'prevChapter',       // 上一章
']': 'nextChapter',       // 下一章
't': 'toggleTranslation', // 翻译
'f': 'toggleFurigana',   // 注音
'g': 'toggleGrammar',    // 语法
'm': 'toggleMenu',       // 目录
'o': 'toggleNightMode',  // 夜间
'space': 'toggleTTS',    // 听书
'p': 'toggleAutoScroll', // 自动滚动
'Escape': 'closeAll'     // 关闭所有
```

**3. 鼠标悬停显示操作**
- 鼠标悬停在段落上 → 显示操作按钮
- 鼠标移开 → 隐藏操作按钮

**4. 选中单词 → 弹出释义**

#### 浏览器端 CSS 实现

```css
/* 整体布局 - 固定导航，不隐藏 */
.reader-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}

/* 顶部导航 - 始终显示 */
.reader-header {
  position: sticky;     /* 改为 sticky 不隐藏 */
  top: 0;
  height: 52px;
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  z-index: 100;
}

/* 主阅读区域 - 舒适宽度 */
.reader-content {
  flex: 1;
  max-width: 860px;        /* 浏览器端可以用更宽 */
  margin: 0 auto;
  padding: 40px 24px;
  width: 100%;
}

/* 段落 - 悬停显示操作 */
.paragraph {
  position: relative;
  padding: 20px 0;
  margin: 0;
  cursor: default;
  transition: background 0.2s;
  border-radius: 4px;
}

.paragraph:hover {
  background: rgba(0, 0, 0, 0.02);
}

/* 操作条 - 悬停显示 */
.paragraph .action-bar {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}

.paragraph:hover .action-bar {
  opacity: 1;
}

.action-btn {
  padding: 6px 14px;
  font-size: 13px;
  border: 1px solid #ddd;
  border-radius: 16px;
  background: white;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  border-color: #1a73e8;
  color: #1a73e8;
}

.action-btn.active {
  background: #1a73e8;
  border-color: #1a73e8;
  color: white;
}

/* 底部导航 - 始终显示 */
.reader-footer {
  position: sticky;     /* 改为 sticky */
  bottom: 0;
  height: 60px;
  background: var(--bg-footer);
  border-top: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 0 20px;
  z-index: 100;
}

/* 进度条 */
.chapter-progress {
  width: 200px;
  height: 4px;
  background: #e0e0e0;
  border-radius: 2px;
}

.chapter-progress .fill {
  height: 100%;
  background: #1a73e8;
  border-radius: 2px;
  transition: width 0.3s;
}

/* 章节按钮 */
.nav-btn {
  padding: 8px 20px;
  font-size: 14px;
  border: 1px solid #ddd;
  border-radius: 20px;
  background: white;
  color: #333;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-btn:hover:not(:disabled) {
  border-color: #1a73e8;
  color: #1a73e8;
}

.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
```

#### 滚轮翻页实现

```javascript
// 滚轮翻页
class ScrollPagination {
  constructor(options = {}) {
    this.container = document.querySelector('.reader-content')
    this.isScrolling = false
    this.scrollThreshold = 100  // 滚动超过100px触发翻页
    this.lastScrollTop = 0
    this.init()
  }

  init() {
    // 监听滚轮事件
    this.container.addEventListener('wheel', this.onWheel.bind(this), {
      passive: false
    })

    // 监听键盘事件
    document.addEventListener('keydown', this.onKeyDown.bind(this))
  }

  onWheel(event) {
    if (this.isScrolling) return

    const delta = event.deltaY
    if (Math.abs(delta) < 10) return  // 忽略小滚动

    this.isScrolling = true

    if (delta > 0) {
      // 向下滚动 → 下一页
      this.nextPage()
    } else {
      // 向上滚动 → 上一页
      this.prevPage()
    }

    // 防抖
    setTimeout(() => {
      this.isScrolling = false
    }, 300)
  }

  onKeyDown(event) {
    // 避免在输入框中触发
    if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA') {
      return
    }

    switch(event.key) {
      case 'j':
      case 'Enter':
      case ' ':
        event.preventDefault()
        this.nextPage()
        break
      case 'k':
      case 'Backspace':
        event.preventDefault()
        this.prevPage()
        break
      case '[':
        this.prevChapter()
        break
      case ']':
        this.nextChapter()
        break
    }
  }

  nextPage() {
    window.scrollBy({ top: window.innerHeight * 0.7, behavior: 'smooth' })
  }

  prevPage() {
    window.scrollBy({ top: -window.innerHeight * 0.7, behavior: 'smooth' })
  }
}
```

#### 目录抽屉（浏览器端优化）

```css
/* 目录抽屉 - 从左侧滑入 */
.chapter-drawer {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 320px;
  background: white;
  z-index: 200;
  transform: translateX(-100%);
  transition: transform 0.3s ease;
  box-shadow: 4px 0 20px rgba(0,0,0,0.1);
  overflow-y: auto;
}

.chapter-drawer.open {
  transform: translateX(0);
}

/* 遮罩层 */
.drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  z-index: 199;
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s;
}

.drawer-overlay.visible {
  opacity: 1;
  visibility: visible;
}

/* 章节列表 */
.chapter-list {
  padding: 16px 0;
}

.chapter-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  font-size: 15px;
  color: #333;
  cursor: pointer;
  transition: background 0.15s;
}

.chapter-item:hover {
  background: #f5f5f5;
}

.chapter-item.active {
  background: #e8f0fe;
  color: #1a73e8;
}

.chapter-item .progress {
  font-size: 12px;
  color: #999;
}
```

#### 响应式设计

```css
/* 大屏幕 - 更宽内容区 */
@media (min-width: 1200px) {
  .reader-content {
    max-width: 900px;
    padding: 60px 40px;
  }
}

/* 中等屏幕 */
@media (max-width: 900px) {
  .reader-content {
    max-width: 100%;
    padding: 30px 20px;
  }

  .reader-header {
    padding: 0 12px;
  }

  .reader-footer {
    height: 54px;
    gap: 16px;
  }

  .chapter-progress {
    width: 120px;
  }
}

/* 小屏幕/平板 */
@media (max-width: 600px) {
  /* 移动端布局 - 类似App */
  .reader-header {
    height: 48px;
  }

  .reader-content {
    padding: 20px 16px 70px;
    font-size: 16px;
  }

  /* 隐藏底部进度条文字 */
  .footer-center .chapter-title {
    display: none;
  }

  .chapter-progress {
    width: 80px;
  }
}
```

#### 浏览器端 vs App端对比

| 功能 | 浏览器端 | App端 |
|------|----------|-------|
| 顶部导航 | **始终显示** | 可隐藏 |
| 底部导航 | **始终显示** | 可隐藏 |
| 翻页方式 | **滚轮/键盘/jk键** | 左右滑动 |
| 操作显示 | **悬停显示** | 点击显示 |
| 内容宽度 | **860px(可更宽)** | 100% |
| 快捷键 | **完整支持** | 不支持 |
| 进度条 | **始终显示** | 可隐藏 |

---

### 4.13 组件状态

```css
/* 段落状态 */
.paragraph {
  /* 默认：纯阅读，无任何标记 */
  cursor: pointer;
}

.paragraph.active {
  /* 选中状态：轻微背景 */
  background: rgba(0, 0, 0, 0.02);
}

.paragraph.has-translation .action-btn.translate {
  /* 已有翻译 */
  background: #e8f5e9;
  color: #2e7d32;
  border-color: #2e7d32;
}

.paragraph.has-furigana .action-btn.furigana {
  background: #e3f2fd;
  color: #1565c0;
  border-color: #1565c0;
}

.paragraph.has-grammar .action-btn.grammar {
  background: #f3e5f5;
  color: #7b1fa2;
  border-color: #7b1fa2;
}

/* 夜间模式 */
body.night-mode {
  --bg-page: #1a1a1a;
  --bg-content: #252525;
  --text-primary: #e0e0e0;
  --text-secondary: #a0a0a0;
  --border-light: #333;
}

body.night-mode .paragraph.active {
  background: rgba(255, 255, 255, 0.05);
}

/* 护眼模式 (豆沙绿) */
body.eye-care-mode {
  --bg-page: #f0f4e8;
  --bg-content: #f5f8f0;
  --text-primary: #3d3d3d;
}
```

### 4.14 底部章节导航

```
┌─────────────────────────────────────────────────────────────────────┐
│  Footer                                                                │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  [◀ 上一章]    第 1 章 / 共 12 章    [下一章 ▶]              │  │
│  │  ════════════════●══════════════════  8%                      │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

```css
.reader-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: white;
  border-top: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  z-index: 100;
}

.footer-center {
  text-align: center;
}

.footer-center .chapter-title {
  font-size: 14px;
  color: #333;
  margin-bottom: 4px;
}

.progress-bar {
  width: 200px;
  height: 3px;
  background: #e0e0e0;
  border-radius: 2px;
  margin: 0 auto;
}

.progress-bar .fill {
  height: 100%;
  background: #1a73e8;
  border-radius: 2px;
  transition: width 0.3s;
}

.nav-btn {
  padding: 8px 20px;
  font-size: 14px;
  border: 1px solid #ddd;
  border-radius: 20px;
  background: white;
  color: #333;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-btn:hover:not(:disabled) {
  border-color: #1a73e8;
  color: #1a73e8;
}

.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
```

### 4.15 响应式断点

```css
/* 桌面 - 居中阅读 */
@media (min-width: 768px) {
  .reader-content {
    max-width: 820px;
    margin: 0 auto;
    padding: 70px 40px 80px;
  }
}

/* 手机 - 全宽阅读，底部导航简化 */
@media (max-width: 768px) {
  .reader-content {
    padding: 60px 16px 70px;
  }

  .paragraph .para-text {
    font-size: 16px;
    line-height: 1.9;
  }

  /* 段落操作条改为悬浮在底部 */
  .paragraph .action-bar {
    position: fixed;
    bottom: 70px;
    left: 50%;
    transform: translateX(-50%);
    background: white;
    padding: 8px 16px;
    border-radius: 24px;
    box-shadow: 0 4px 16px rgba(0,0,0,0.12);
    z-index: 100;
  }

  /* 单词查询气泡改为底部弹窗 */
  .word-bubble {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    width: 100%;
    border-radius: 16px 16px 0 0;
    max-height: 60vh;
    overflow-y: auto;
  }

  /* 底部导航简化 */
  .reader-footer {
    height: 50px;
    padding: 0 12px;
  }

  .footer-center .chapter-title {
    font-size: 13px;
  }

  .progress-bar {
    width: 120px;
  }

  .nav-btn {
    padding: 6px 14px;
    font-size: 13px;
  }
}
```

### 4.16 快捷键

```javascript
// 阅读器快捷键
const shortcuts = {
  'j': 'nextParagraph',        // 下一段
  'k': 'prevParagraph',        // 上一段
  't': 'toggleTranslation',   // 翻译
  'f': 'toggleFurigana',      // 注音
  'g': 'toggleGrammar',       // 语法
  'Escape': 'closeActive',    // 关闭当前展开
  '[': 'prevChapter',         // 上一章
  ']': 'nextChapter',          // 下一章
  'm': 'toggleMenu',           // 目录菜单
  'o': 'toggleNightMode',      // 夜间模式
}
```

---

### 4.17 听书功能（朗读）

番茄小说的核心功能之一，让用户闭眼也能"阅读"。

#### 朗读控制条

点击「听书」按钮或耳机图标，弹出朗读控制条：

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│                 🎧 正在朗读 · 第一章                                 │
│                                                                      │
│     ここにようやく到着した。勉学の日々が続く。                          │
│     ▼ 当前进度: ここに────────────────────────────────────          │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ◀◀   ▶️/⏸   ▶▶   │  🔈 ━━━━●━━━━  │  1.0x  │  音色 ▼  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  [停止朗读]                                          [最小化]        │
└─────────────────────────────────────────────────────────────────────┘
```

#### 朗读迷你条（最小化后）

```
┌─────────────────────────────────────────────────────────────────────┐
│  🎧 朗读中 · 第一章  ████████░░░░░░░ 50%    ▶️  1.0x  [展开]  [×] │
└─────────────────────────────────────────────────────────────────────┘
```

#### 功能说明

| 功能 | 说明 |
|------|------|
| 播放/暂停 | 控制朗读暂停 |
| 上一句/下一句 | 手动跳段 |
| 音量 | 调节音量 |
| 语速 | 0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 2.0x |
| 音色 | 女声A / 女声B / 男声A / 男声B |
| 最小化 | 收起为迷你条，后台播放 |
| 停止 | 结束朗读 |

#### 朗读实现

```javascript
// 使用 Web Speech API 或 外部TTS服务
class TextToSpeechService {
  constructor() {
    this.synth = window.speechSynthesis
    this.voices = []
    this.currentUtterance = null
  }

  // 初始化音色列表
  loadVoices() {
    this.voices = this.synth.getVoices()
    // 筛选日语音色
    return this.voices.filter(v => v.lang.includes('ja'))
  }

  // 朗读段落
  speak(text, options = {}) {
    const {
      rate = 1.0,    // 语速
      pitch = 1.0,   // 音调
      voice = null,  // 音色
      volume = 1.0   // 音量
    } = options

    const utterance = new SpeechSynthesisUtterance(text)
    utterance.rate = rate
    utterance.pitch = pitch
    utterance.volume = volume
    if (voice) utterance.voice = voice

    this.synth.speak(utterance)
    this.currentUtterance = utterance
    return utterance
  }

  // 暂停
  pause() {
    this.synth.pause()
  }

  // 继续
  resume() {
    this.synth.resume()
  }

  // 停止
  stop() {
    this.synth.cancel()
  }
}
```

#### 朗读UI组件

```vue
<template>
  <!-- 朗读悬浮条 -->
  <div class="tts-bar" v-if="isTTSActive" :class="{ minimized: isMinimized }">
    <!-- 展开状态 -->
    <div v-if="!isMinimized" class="tts-expanded">
      <div class="tts-info">
        <span class="tts-icon">🎧</span>
        <span class="tts-status">正在朗读 · {{ currentChapter }}</span>
      </div>
      <div class="tts-progress">
        <span class="current-sentence">{{ currentSentence }}</span>
      </div>
      <div class="tts-controls">
        <button @click="skipPrevious">◀◀</button>
        <button @click="togglePlay" class="play-btn">
          {{ isPlaying ? '⏸' : '▶' }}
        </button>
        <button @click="skipNext">▶▶</button>
        <div class="speed-control">
          <span>语速</span>
          <select v-model="playbackRate" @change="updateSpeed">
            <option value="0.5">0.5x</option>
            <option value="0.75">0.75x</option>
            <option value="1">1.0x</option>
            <option value="1.25">1.25x</option>
            <option value="1.5">1.5x</option>
            <option value="2">2.0x</option>
          </select>
        </div>
        <div class="voice-control">
          <span>音色</span>
          <select v-model="selectedVoice">
            <option v-for="v in voices" :key="v.name" :value="v">
              {{ v.name }}
            </option>
          </select>
        </div>
      </div>
      <div class="tts-actions">
        <button @click="stopTTS">停止</button>
        <button @click="minimize">最小化</button>
      </div>
    </div>

    <!-- 最小化状态 -->
    <div v-else class="tts-mini">
      <span>🎧</span>
      <span>朗读中 · {{ currentChapter }}</span>
      <div class="mini-progress">
        <div class="mini-progress-bar" :style="{ width: progress + '%' }"></div>
      </div>
      <button @click="togglePlay">{{ isPlaying ? '⏸' : '▶' }}</button>
      <span>{{ playbackRate }}</span>
      <button @click="expand">展开</button>
      <button @click="stopTTS">×</button>
    </div>
  </div>
</template>
```

#### 朗读样式

```css
/* 朗读悬浮条 */
.tts-bar {
  position: fixed;
  bottom: 70px;  /* 在底部导航上方 */
  left: 50%;
  transform: translateX(-50%);
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.15);
  z-index: 150;
  min-width: 500px;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateX(-50%) translateY(20px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

.tts-bar.minimized {
  min-width: auto;
  padding: 8px 16px;
}

/* 迷你条 */
.tts-mini {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.mini-progress {
  width: 100px;
  height: 4px;
  background: #e0e0e0;
  border-radius: 2px;
}

.mini-progress-bar {
  height: 100%;
  background: #1a73e8;
  border-radius: 2px;
  transition: width 0.3s;
}
```

---

### 4.18 自动滚动阅读（懒人模式）

类似起点读书的自动阅读功能，适合不想手动翻页的用户。

#### 自动滚动控制

点击「自动」按钮开启，屏幕底部显示滚动进度：

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│     ここにようやく到着した。勉学の日々が続く。                          │
│     それでも、明日への希望を捨てなかった。                              │
│     それは小さな灯りのように胸に灯り続けた。                            │
│                                                                      │
│     ようやくの休息に安堵する間もなく、                                 │
│     次の瞬間、何かが起こった。                                         │
│                                                                      │
├─────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────┐    │
│  │  ▶ 自动滚动中                                              │    │
│  │  ━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━━  50%   3分钟/页      │    │
│  │  ◀ 慢  ━━━━━━━━━━━━●━━━━━━━━━  快  │  [暂停]  [退出]     │    │
│  └────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

#### 功能说明

| 功能 | 说明 |
|------|------|
| 滚动开关 | 开始/暂停自动滚动 |
| 滚动速度 | 慢(20秒/屏) / 中(15秒/屏) / 快(10秒/屏) / 极快(5秒/屏) |
| 暂停 | 临时停止滚动，看完后继续 |
| 退出 | 关闭自动滚动模式 |
| 智能调速 | 可选：阅读到段落末尾自动加速 |

#### 自动滚动实现

```javascript
class AutoScrollService {
  constructor() {
    this.isScrolling = false
    this.speed = 15  // 秒/屏
    this.intervalId = null
    this.scrollAmount = 0
  }

  start(container, options = {}) {
    const { speed = 15, onProgress } = options
    this.speed = speed
    this.isScrolling = true

    // 计算每次滚动的像素量
    const viewportHeight = window.innerHeight
    const scrollStep = viewportHeight / (this.speed * 10)  // 每100ms滚动量

    this.intervalId = setInterval(() => {
      if (!this.isScrolling) return

      const currentScroll = window.scrollY
      const maxScroll = document.documentElement.scrollHeight - viewportHeight

      // 执行滚动
      window.scrollBy(0, scrollStep)

      // 计算进度
      const progress = (currentScroll / maxScroll) * 100
      if (onProgress) onProgress(progress)

      // 到达底部自动停止
      if (currentScroll >= maxScroll - 10) {
        this.stop()
      }
    }, 100)  // 每100ms更新一次
  }

  pause() {
    this.isScrolling = false
  }

  resume() {
    this.isScrolling = true
  }

  stop() {
    this.isScrolling = false
    if (this.intervalId) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
  }

  setSpeed(speed) {
    this.speed = speed
  }
}
```

#### 自动滚动UI组件

```vue
<template>
  <!-- 自动滚动悬浮条 -->
  <div class="auto-scroll-bar" v-if="isAutoScrollActive">
    <div class="scroll-status">
      <span class="scroll-icon">▶</span>
      <span>自动滚动中</span>
    </div>
    <div class="scroll-progress">
      <span class="progress-percent">{{ Math.round(scrollProgress) }}%</span>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: scrollProgress + '%' }"></div>
      </div>
    </div>
    <div class="speed-control">
      <button
        v-for="s in speeds"
        :key="s.value"
        :class="{ active: currentSpeed === s.value }"
        @click="setSpeed(s.value)"
      >
        {{ s.label }}
      </button>
    </div>
    <div class="scroll-actions">
      <button @click="togglePause">
        {{ isPaused ? '▶ 继续' : '⏸ 暂停' }}
      </button>
      <button @click="exitAutoScroll">退出</button>
    </div>
  </div>
</template>

<script setup>
const speeds = [
  { label: '慢', value: 20 },   // 20秒/屏
  { label: '中', value: 15 },  // 15秒/屏
  { label: '快', value: 10 },  // 10秒/屏
  { label: '极快', value: 5 }   // 5秒/屏
]
</script>
```

#### 自动滚动样式

```css
/* 自动滚动悬浮条 */
.auto-scroll-bar {
  position: fixed;
  bottom: 70px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.85);
  color: white;
  border-radius: 24px;
  padding: 12px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  z-index: 150;
  backdrop-filter: blur(10px);
}

.scroll-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.scroll-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-percent {
  font-size: 12px;
  min-width: 35px;
}

.progress-bar {
  width: 120px;
  height: 4px;
  background: rgba(255,255,255,0.3);
  border-radius: 2px;
}

.progress-fill {
  height: 100%;
  background: #1a73e8;
  border-radius: 2px;
  transition: width 0.1s;
}

.speed-control {
  display: flex;
  gap: 4px;
}

.speed-control button {
  padding: 4px 8px;
  font-size: 12px;
  border: none;
  background: transparent;
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  border-radius: 4px;
}

.speed-control button:hover {
  background: rgba(255,255,255,0.1);
}

.speed-control button.active {
  background: #1a73e8;
  color: white;
}

.scroll-actions {
  display: flex;
  gap: 8px;
}

.scroll-actions button {
  padding: 6px 12px;
  font-size: 13px;
  border: none;
  border-radius: 16px;
  cursor: pointer;
}

.scroll-actions button:first-child {
  background: rgba(255,255,255,0.2);
  color: white;
}

.scroll-actions button:last-child {
  background: rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.7);
}
```

---

### 4.19 阅读时长统计

记录用户阅读习惯，激励持续学习。

#### 统计显示（可在设置或个人中心查看）

```
┌─────────────────────────────────────────────────────────────┐
│  📊 今日阅读                                               │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│     ⏱ 今日时长         📅 连续阅读                          │
│     ┌────────┐         ┌────────┐                           │
│     │ 2小时  │         │  7 天  │                           │
│     │ 35分钟 │         │        │                           │
│     └────────┘         └────────┘                           │
│                                                              │
│     📖 本章阅读进度                                          │
│     第一章  ████████░░░░░░░░░░░  40%                       │
│                                                              │
│     📈 阅读趋势 (近7天)                                      │
│     █                                                       │
│     █ █                                                     │
│     █ █ █                                                   │
│     █ █ █ █                                                 │
│     月 火 水 木 金 土 日                                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 数据存储

```javascript
// 阅读记录
const readingRecord = {
  date: '2024-01-15',
  totalSeconds: 9210,  // 2小时35分 = 255*60 = 15300秒
  chaptersRead: [
    { bookId: 1, chapterId: 3, readSeconds: 3600 },
    { bookId: 1, chapterId: 4, readSeconds: 5610 }
  ],
  paragraphsStudied: {
    translation: 12,
    furigana: 8,
    grammar: 5
  }
}
```

---

### 4.20 历史阅读快速入口

快速回到最近阅读的书籍。

```
┌─────────────────────────────────────────────────────────────┐
│  📚 最近阅读                                    [查看全部]  │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                │
│  │  封面    │  │  封面    │  │  封面    │                │
│  │          │  │          │  │          │                │
│  │ 书名     │  │ 书名     │  │ 书名     │                │
│  │ 第三章   │  │ 第五章   │  │ 第一章   │                │
│  │ ████░░░ │  │ ███░░░░ │  │ ██████░ │                │
│  │ 60%     │  │ 40%     │  │ 85%     │                │
│  └──────────┘  └──────────┘  └──────────┘                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. 核心功能实现方案

### 5.1 文件上传与解析

**TXT 处理流程：**
```
上传 → 按换行符分段 → 存入 paragraphs 表
      → 异步调用分词服务 → 存入 segmented_text
      → 异步调用翻译服务(可选) → 存入 translation
```

**EPUB 处理流程：**
```
上传 → 解析 EPUB 结构(XHTML + OPF)
      → 提取章节 → 存入 chapters 表
      → 提取段落 → 存入 paragraphs 表
      → 分词/翻译(同上)
```

**依赖库：**
- Spring Boot: `spring-boot-starter-web`
- 文件上传: `commons-fileupload`
- EPUB解析: `epublib-core` 或 `jsoup` (解析XHTML)

### 5.2 日语分词 (后端)

使用 Kuromoji 分词器：

```java
// 引入依赖
// <dependency>
//     <groupId>com.atilika.kuromoji</groupId>
//     <artifactId>kuromoji-ipadic</artifactId>
//     <version>0.9.0</version>
// </dependency>

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

@Service
public class JapaneseSegmentService {

    private final Tokenizer tokenizer = new Tokenizer();

    public List<WordToken> segment(String text) {
        List<WordToken> tokens = new ArrayList<>();
        for (Token token : tokenizer.tokenize(text)) {
            tokens.add(new WordToken(
                token.getSurface(),
                token.getReading(),
                token.getPartOfSpeech(),
                token.getConjugationForm(),
                token.getConjugationType()
            ));
        }
        return tokens;
    }
}

public class WordToken {
    private String surface;      // 原形
    private String reading;      // 读音(平假名)
    private String partOfSpeech;  // 词性
    private String conjugation;   // 活用形
    // getters...
}
```

### 5.3 翻译服务

**方案A: Google Translate API (推荐)**
```java
@Service
public class TranslationService {

    public String translate(String text, String from, String to) {
        // 使用 RestTemplate 调用 Google Translate API
        // 或使用 @feignClient 调用微服务
    }
}
```

**方案B: 本地简单翻译 (无API)**
- 使用词典文件 + 简单替换
- 适用于离线场景

### 5.4 注音 (Furigana) 生成

```java
@Service
public class FuriganaService {

    public String generateFurigana(String text) {
        // 利用分词结果，生成ruby标签格式
        // 输入: "勉学"
        // 输出: "<ruby>勉<rp>(</rp><rt>べん</rt><rp>)</rp>学<rp>(</rp><rt>がく</rt><rp>)</rp></ruby>"
    }

    // 也可以返回JSON结构供前端渲染
    public List<RubySegment> generateFuriganaJson(String text) {
        List<RubySegment> result = new ArrayList<>();
        // ...
        return result;
    }
}

public class RubySegment {
    private String kanji;
    private String hiragana;
    // getters...
}
```

### 5.5 语法解析

```java
@Service
public class GrammarAnalysisService {

    public GrammarResult analyze(String paragraph) {
        // 1. 分词
        List<WordToken> tokens = segmentService.segment(paragraph);

        // 2. 识别语法句型
        //    - 助动词判断 (ない、れる、せる等)
        //    - 助词组合 (は、が、を、にも等)
        //    - 活用形识别

        // 3. 返回分析结果
        return new GrammarResult(
            tokens,
            detectedPatterns,  // 识别出的语法点
            explanation        // 解释
        );
    }
}
```

### 5.6 前端分词 (可选，减轻后端压力)

使用 `kuromoji.js`：

```javascript
import kuromoji from 'kuromoji'

// 初始化
const builder = kuromoji.builder({ dicPath: '/dict' })
builder.build((err, tokenizer) => {
  const tokens = tokenizer.tokenize('勉学')
  console.log(tokens[0].surface)   // 勉学
  console.log(tokens[0].reading)  // べんがく
})

// 点击单词时查询
function lookupWord(word) {
  const tokens = tokenizer.tokenize(word)
  return {
    surface: tokens[0].surface,
    reading: tokens[0].reading,
    pos: tokens[0].pos
  }
}
```

### 5.7 阅读界面交互

```vue
<template>
  <div class="reader">
    <!-- 段落 -->
    <div
      v-for="para in paragraphs"
      :key="para.id"
      class="paragraph"
    >
      <!-- 原文，点击单词触发查询 -->
      <div
        class="original-text"
        @click="handleWordClick($event, para)"
        v-html="renderWithFurigana(para)"
      ></div>

      <!-- 操作按钮 -->
      <div class="actions">
        <el-button size="small" @click="translate(para)">翻译</el-button>
        <el-button size="small" @click="showFurigana(para)">注音</el-button>
        <el-button size="small" @click="analyzeGrammar(para)">学习</el-button>
      </div>

      <!-- 翻译结果 -->
      <div v-if="para.showTranslation" class="translation">
        {{ para.translation }}
      </div>

      <!-- 语法分析 -->
      <div v-if="para.showGrammar" class="grammar-analysis">
        <div v-for="pattern in para.grammarPatterns" :key="pattern.name">
          <strong>{{ pattern.name }}</strong>: {{ pattern.explanation }}
        </div>
      </div>
    </div>

    <!-- 单词悬浮卡片 -->
    <WordPopup
      v-if="selectedWord"
      :word="selectedWord"
      :position="popupPosition"
      @add-to-dict="addToDictionary"
    />
  </div>
</template>

<script setup>
// 段落点击 → 判断是否点击在单词上
// 如果是 → 弹出单词卡片
// 如果否 → 忽略或选中整段
</script>
```

---

## 6. 项目结构

### 6.1 后端结构 (Spring Boot)

```
backend/
├── pom.xml
├── src/main/java/com/novelreader/
│   ├── NovelReaderApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── JwtConfig.java
│   │   └── CorsConfig.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── BookController.java
│   │   ├── ReadController.java
│   │   ├── BookmarkController.java
│   │   └── WordController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── BookService.java
│   │   ├── JapaneseSegmentService.java
│   │   ├── TranslationService.java
│   │   ├── FuriganaService.java
│   │   └── GrammarAnalysisService.java
│   ├── mapper/
│   │   ├── UserMapper.java
│   │   ├── BookMapper.java
│   │   ├── ChapterMapper.java
│   │   ├── ParagraphMapper.java
│   │   └── ...
│   ├── entity/
│   │   ├── User.java
│   │   ├── Book.java
│   │   ├── Chapter.java
│   │   └── ...
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── BookUploadResponse.java
│   │   └── ...
│   ├── security/
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── UserDetailsServiceImpl.java
│   └── exception/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.yml
│   └── mapper/
└── uploads/  (小说文件存储)
```

### 6.2 前端结构 (Vue 3)

```
frontend/
├── index.html
├── vite.config.js
├── package.json
├── public/
│   └── dict/           (kuromoji词典)
├── src/
│   ├── main.js
│   ├── App.vue
│   ├── router/
│   │   └── index.js
│   ├── stores/
│   │   ├── auth.js
│   │   ├── book.js
│   │   └── reader.js
│   ├── api/
│   │   ├── auth.js
│   │   ├── book.js
│   │   └── read.js
│   ├── views/
│   │   ├── Login.vue
│   │   ├── Register.vue
│   │   ├── Library.vue
│   │   ├── Upload.vue
│   │   ├── Reader.vue
│   │   ├── Bookmarks.vue
│   │   └── WordBook.vue
│   ├── components/
│   │   ├── WordPopup.vue
│   │   ├── ParagraphBlock.vue
│   │   ├── ChapterList.vue
│   │   └── ReadingProgress.vue
│   └── styles/
│       └── reader.css
```

---

## 7. 第三方依赖

### 7.1 后端 (pom.xml 关键依赖)

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>3.5.3</version>
    </dependency>

    <!-- MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>

    <!-- 日语分词 -->
    <dependency>
        <groupId>com.atilika.kuromoji</groupId>
        <artifactId>kuromoji-ipadic</artifactId>
        <version>0.9.0</version>
    </dependency>

    <!-- EPUB解析 -->
    <dependency>
        <groupId>nl.siegmann.epublib</groupId>
        <artifactId>epublib-core</artifactId>
        <version>3.1</version>
    </dependency>

    <!-- HTTP Client -->
    <dependency>
        <groupId>org.apache.httpcomponents.client5</groupId>
        <artifactId>httpclient5</artifactId>
    </dependency>

    <!-- 文件上传 -->
    <dependency>
        <groupId>commons-fileupload</groupId>
        <artifactId>commons-fileupload</artifactId>
        <version>1.5</version>
    </dependency>
</dependencies>
```

### 7.2 前端 (package.json)

```json
{
  "dependencies": {
    "vue": "^3.4",
    "vue-router": "^4.2",
    "pinia": "^2.1",
    "element-plus": "^2.5",
    "axios": "^1.6",
    "epubjs": "^0.3",
    "kuromoji": "^0.1.2"
  },
  "devDependencies": {
    "vite": "^5.0",
    "@vitejs/plugin-vue": "^5.0"
  }
}
```

---

## 8. 实现优先级

### Phase 1: 基础功能 (MVP)
1. 用户注册/登录 (JWT)
2. TXT文件上传与解析
3. 图书列表与阅读
4. 段落翻译 (调用外部API)
5. 单词点击查询

### Phase 2: 核心功能
1. EPUB文件上传与解析
2. 注音 (Furigana) 显示（含原文自带注音）
3. 阅读进度保存
4. 书签功能
5. 生词本
6. 番茄小说风格设置面板（字体/背景/行距）

### Phase 3: 增强功能
1. 语法分析
2. 本地分词 (减少API调用)
3. 离线缓存
4. 听书/朗读功能
5. 自动滚动阅读（懒人模式）
6. 阅读时长统计
7. 历史阅读记录

---

## 9. 部署建议

- **开发环境**: `docker-compose` 启动 MySQL
- **生产环境**: 前端 Nginx 静态托管 + 后端 Jar 部署
- **词典文件**: kuromoji 词典 (~50MB) 建议独立存储

---

## 10. 错误处理设计

### 10.1 统一错误响应格式

```json
{
  "code": 40001,
  "message": "登录失败，用户名或密码错误",
  "data": null,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 10.2 错误码规范

| 错误码范围 | 模块 | 说明 |
|-------------|------|------|
| 1xxxx | 认证模块 | 用户注册、登录、Token相关 |
| 2xxxx | 图书模块 | 上传、解析、删除图书 |
| 3xxxx | 阅读模块 | 章节、段落、翻译、注音 |
| 4xxxx | 学习模块 | 书签、生词、语法 |
| 5xxxx | 系统模块 | 服务器、数据库、外部API |

### 10.3 详细错误码

| 错误码 | 说明 |
|---------|------|
| 10001 | 用户名已存在 |
| 10002 | 用户名或密码错误 |
| 10003 | Token已过期 |
| 10004 | Token格式错误 |
| 10005 | RefreshToken已过期 |
| 20001 | 文件类型不支持 |
| 20002 | 文件大小超限（最大50MB） |
| 20003 | 文件解析失败 |
| 20004 | 图书不存在 |
| 20005 | 无权删除他人图书 |
| 30001 | 章节不存在 |
| 30002 | 段落不存在 |
| 30003 | 翻译服务调用失败 |
| 30004 | 分词服务调用失败 |
| 40001 | 书签不存在 |
| 40002 | 生词已存在 |
| 50001 | 数据库连接失败 |
| 50002 | 外部API调用失败 |

### 10.4 前端错误处理

```javascript
// 统一错误处理
axios.interceptors.response.use(
  response => response,
  error => {
    const { code, message } = error.response.data

    switch (code) {
      case 10003: // Token过期
        // 跳转登录页
        router.push('/login')
        break
      case 30003: // 翻译失败
        ElMessage.error('翻译服务暂时不可用，请稍后重试')
        break
      default:
        ElMessage.error(message || '请求失败')
    }

    return Promise.reject(error)
  }
)
```

---

## 11. 性能优化方案

### 11.1 前端性能优化

| 优化项 | 方案 |
|--------|------|
| **首屏加载** | Vue路由懒加载，组件异步import |
| **图片加载** | epub封面懒加载，placeholder |
| **词典文件** | kuromoji词典独立CDN，按需加载 |
| **长列表** | 虚拟滚动，仅渲染可见段落 |
| **缓存** | localStorage缓存用户设置 |

```javascript
// 路由懒加载
const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  { path: '/read/:bookId', component: () => import('./views/Reader.vue') },
]

// 虚拟滚动（长段落列表）
import { useVirtualList } from '@vueuse/core'

const { list, containerProps, wrapperProps } = useVirtualList(
  paragraphs,
  { itemHeight: 120 }
)
```

### 11.2 后端性能优化

| 优化项 | 方案 |
|--------|------|
| **数据库** | MySQL索引优化，连接池 |
| **分词缓存** | Redis缓存热门段落分词结果 |
| **翻译缓存** | Redis缓存已翻译内容（24小时） |
| **异步处理** | 文件解析、翻译使用异步队列 |

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  redis:
    host: localhost
    port: 6379
```

### 11.3 大章节加载策略

```
问题：单章节超过100段时，一次性加载太慢

方案：
1. 首次只加载前20段
2. 用户滚动到底部时，懒加载后续段落
3. 预加载下一章章节列表
```

```javascript
// 懒加载段落
async function loadMoreParagraphs(chapterId, offset, limit = 20) {
  const response = await api.get(`/read/${chapterId}/paragraphs`, {
    params: { offset, limit }
  })
  return response.data
}

// 滚动监听
const observer = new IntersectionObserver((entries) => {
  if (entries[0].isIntersecting) {
    loadMoreParagraphs(chapterId, currentOffset)
  }
}, { rootMargin: '200px' })
```

---

## 12. 用户设置页面

### 12.1 页面布局

```
┌─────────────────────────────────────────────────────────────┐
│  [← 返回]  个人设置                                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─ 账户信息 ─────────────────────────────────────────────┐  │
│  │                                                           │  │
│  │  头像: [👤]     用户名: john Doe                      │  │
│  │                     邮箱: john@example.com              │  │
│  │                                                           │  │
│  │  [编辑资料]  [修改密码]                                 │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌─ 阅读偏好 ─────────────────────────────────────────────┐  │
│  │                                                           │  │
│  │  默认字体:    [PingFang SC        ▼]                    │  │
│  │  默认背景:    [明亮白             ▼]                    │  │
│  │  默认注音:    [关 ○ ○ 开]                              │  │
│  │  默认翻译:    [关 ○ ○ 开]                              │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌─ 学习设置 ─────────────────────────────────────────────┐  │
│  │                                                           │  │
│  │  划词翻译:    [开 ○ ○ 关]                              │  │
│  │  自动发音:    [关 ○ ○ 开]                              │  │
│  │  生词提示:    [开 ○ ○ 关]                              │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌─ 数据管理 ─────────────────────────────────────────────┐  │
│  │                                                           │  │
│  │  [导出生词本]  [导入生词本]  [清空阅读记录]            │  │
│  │                                                           │  │
│  │  [退出登录]  [注销账户]                                │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 12.2 用户设置存储

```sql
ALTER TABLE users ADD COLUMN settings JSON;

-- settings 字段示例
{
  "defaultFont": "PingFang SC",
  "defaultBackground": "light",
  "showFurigana": false,
  "showTranslation": false,
  "autoPronounce": false,
  "wordHint": true
}
```

---

## 13. 安全设计

### 13.1 JWT Token 策略

```
访问Token (Access Token):
- 有效期: 30分钟
- 存储: localStorage
- 用途: API请求认证

刷新Token (Refresh Token):
- 有效期: 7天
- 存储: httpOnly Cookie
- 用途: 续期访问Token
```

### 13.2 权限控制

```java
// 接口权限
@PreAuthorize("hasRole('USER')")
@GetMapping("/books")
public List<Book> getMyBooks() {
    Long userId = getCurrentUserId();
    return bookService.getBooksByUser(userId);
}

// 管理员接口
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/books/{id}")
public Result deleteBook(@PathVariable Long id) {
    // 管理员可删除任何图书
    bookService.deleteById(id);
    return Result.success();
}
```

### 13.3 常见攻击防护

| 攻击类型 | 防护措施 |
|----------|----------|
| XSS | 输入过滤，HTML转义 |
| CSRF | Token校验，SameSite Cookie |
| SQL注入 | MyBatis参数绑定 |
| 暴力破解 | 登录失败次数限制，5次后锁定15分钟 |
| 文件上传 | 文件类型白名单(.txt, .epub)，大小限制50MB |

---

## 14. 监控与日志

### 14.1 日志规范

```java
// 使用 SLF4J + Logback
@Slf4j
@RestController
public class BookController {

    @PostMapping("/books/upload")
    public Result upload(MultipartFile file) {
        log.info("用户上传文件: {}, 大小: {}",
            file.getOriginalFilename(),
            file.getSize());

        try {
            // 处理逻辑
            log.info("文件处理成功: bookId={}", bookId);
            return Result.success(bookId);
        } catch (Exception e) {
            log.error("文件处理失败", e);
            return Result.error(20003, "文件解析失败");
        }
    }
}
```

### 14.2 关键指标监控

| 指标 | 告警阈值 |
|------|----------|
| API响应时间 | > 2秒 |
| 错误率 | > 1% |
| CPU使用率 | > 80% |
| 内存使用率 | > 85% |
| 翻译API失败率 | > 5% |

---

如需更详细的某个模块实现说明，请告知。
