<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const showHeader = computed(() => !['/login', '/register'].includes(route.path))

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <header v-if="showHeader" class="app-header">
    <div class="app-header__inner">
      <RouterLink class="brand-mark" to="/library">
        <span class="brand-badge">文</span>
        <span>Translate Novel</span>
      </RouterLink>

      <nav class="app-nav">
        <RouterLink to="/library">书库</RouterLink>
        <RouterLink to="/library/upload">上传</RouterLink>
        <RouterLink to="/bookmarks">书签</RouterLink>
        <RouterLink to="/words">生词本</RouterLink>
        <RouterLink to="/profile">个人页</RouterLink>
      </nav>

      <div style="display: flex; align-items: center; gap: 12px;">
        <span class="pill">{{ authStore.user?.username || '访客' }}</span>
        <el-button text type="primary" @click="logout">退出</el-button>
      </div>
    </div>
  </header>
</template>
