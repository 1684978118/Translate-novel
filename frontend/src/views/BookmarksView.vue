<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteBookmark, fetchBookmarks } from '@/api/collection'

const loading = ref(false)
const bookmarks = ref([])

async function load() {
  loading.value = true
  try {
    const result = await fetchBookmarks()
    bookmarks.value = result.data
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function removeBookmark(id) {
  try {
    await deleteBookmark(id)
    ElMessage.success('书签已删除')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <div class="hero-card" style="padding: 28px;">
      <div class="pill">Bookmarks</div>
      <h1 class="page-title" style="margin-top: 16px;">书签回看</h1>
      <p class="page-subtitle">把值得回看的段落、表达和语法痕迹集中在一个页面里。</p>
    </div>

    <div v-loading="loading" class="bookmark-list">
      <article v-for="item in bookmarks" :key="item.id" class="surface-card bookmark-item">
        <div>
          <div class="muted">{{ item.bookTitle }}</div>
          <h3>{{ item.paragraphPreview }}</h3>
          <p class="page-subtitle">{{ item.note || '未添加备注' }}</p>
        </div>
        <div style="display: flex; gap: 12px; align-items: center;">
          <RouterLink :to="`/read/${item.bookId}`">
            <el-button round>打开原文</el-button>
          </RouterLink>
          <el-button text type="danger" @click="removeBookmark(item.id)">删除</el-button>
        </div>
      </article>
    </div>

    <el-empty v-if="!loading && bookmarks.length === 0" description="还没有书签" />
  </section>
</template>

<style scoped>
.bookmark-list {
  margin-top: 24px;
  display: grid;
  gap: 16px;
}

.bookmark-item {
  padding: 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.bookmark-item h3 {
  margin: 8px 0;
  font-family: "Source Han Serif SC", "Songti SC", serif;
}

@media (max-width: 900px) {
  .bookmark-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
