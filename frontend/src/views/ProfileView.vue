<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchBookmarks, fetchWords } from '@/api/collection'
import { fetchBooks } from '@/api/book'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const booksCount = ref(0)
const bookmarksCount = ref(0)
const wordsCount = ref(0)

const user = computed(() => authStore.user)

async function load() {
  try {
    if (!authStore.user) {
      await authStore.refreshProfile()
    }

    const [books, bookmarks, words] = await Promise.all([
      fetchBooks(),
      fetchBookmarks(),
      fetchWords(),
    ])

    booksCount.value = books.data.length
    bookmarksCount.value = bookmarks.data.length
    wordsCount.value = words.data.length
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <div class="hero-card" style="padding: 30px;">
      <div class="pill">Profile</div>
      <h1 class="page-title" style="margin-top: 16px;">{{ user?.username || '用户' }}</h1>
      <p class="page-subtitle">{{ user?.email }} · 角色 {{ user?.role }}</p>
    </div>

    <div class="metric-row" style="margin-top: 24px;">
      <div class="surface-card metric-card">
        <div class="muted">书库</div>
        <strong style="font-size: 32px;">{{ booksCount }}</strong>
      </div>
      <div class="surface-card metric-card">
        <div class="muted">书签</div>
        <strong style="font-size: 32px;">{{ bookmarksCount }}</strong>
      </div>
      <div class="surface-card metric-card">
        <div class="muted">生词</div>
        <strong style="font-size: 32px;">{{ wordsCount }}</strong>
      </div>
    </div>
  </section>
</template>
