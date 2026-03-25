<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useBookStore } from '@/stores/book'

const bookStore = useBookStore()
const loading = ref(false)
const books = computed(() => bookStore.books)

async function load() {
  loading.value = true
  try {
    await bookStore.loadBooks()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function removeBook(book) {
  try {
    await bookStore.removeBook(book.id)
    ElMessage.success('小说已删除')
  } catch (error) {
    ElMessage.error(error.message)
  }
}
</script>

<template>
  <section class="page">
    <div class="hero-card" style="padding: 28px 30px;">
      <div class="split-grid" style="align-items: center;">
        <div>
          <div class="pill">小说库</div>
          <h1 class="page-title" style="margin-top: 18px;">把 TXT / EPUB 日文小说整理成可读、可查、可学习的书库。</h1>
          <p class="page-subtitle">
            当前第一版支持 TXT / EPUB 上传、章节解析、逐段阅读、占位翻译、注音和单词查询。
          </p>
        </div>
        <div class="surface-card" style="padding: 24px;">
          <div class="metric-row">
            <div class="surface-card metric-card">
              <div class="muted">书籍数</div>
              <strong style="font-size: 28px;">{{ books.length }}</strong>
            </div>
            <div class="surface-card metric-card">
              <div class="muted">状态</div>
              <strong style="font-size: 28px;">MVP</strong>
            </div>
            <div class="surface-card metric-card">
              <div class="muted">入口</div>
              <RouterLink to="/library/upload" style="font-size: 18px; color: var(--accent-primary);">去上传</RouterLink>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div style="display: flex; justify-content: space-between; align-items: center; margin: 28px 0 18px;">
      <h2 style="margin: 0;">我的书架</h2>
      <RouterLink to="/library/upload">
        <el-button type="primary" round>上传新小说</el-button>
      </RouterLink>
    </div>

    <el-skeleton v-if="loading" animated :rows="6" />

    <div v-else-if="books.length" class="library-grid">
      <article v-for="book in books" :key="book.id" class="surface-card library-card">
        <div class="pill">{{ book.sourceType }}</div>
        <h3>{{ book.title }}</h3>
        <p class="muted">{{ book.author || 'Unknown' }}</p>
        <p class="page-subtitle">
          {{ book.chapterCount }} 章 · {{ book.wordCount || 0 }} 词元 · 状态 {{ book.status }}
        </p>
        <div class="library-card__actions">
          <RouterLink :to="`/read/${book.id}`">
            <el-button type="primary" plain round>继续阅读</el-button>
          </RouterLink>
          <el-popconfirm title="确定删除这本小说吗？" @confirm="removeBook(book)">
            <template #reference>
              <el-button text type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </article>
    </div>

    <el-empty v-else description="还没有上传任何小说">
      <RouterLink to="/library/upload">
        <el-button type="primary">上传第一本小说</el-button>
      </RouterLink>
    </el-empty>
  </section>
</template>

<style scoped>
.library-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 18px;
}

.library-card {
  padding: 22px;
}

.library-card h3 {
  margin: 18px 0 8px;
  font-family: "Source Han Serif SC", "Songti SC", serif;
  font-size: 24px;
}

.library-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>
