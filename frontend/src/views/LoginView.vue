<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

async function submit() {
  loading.value = true
  try {
    await authStore.loginWithCredentials(form)
    ElMessage.success('登录成功')
    router.push('/library')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-card hero-card">
      <div class="auth-intro">
        <div class="pill" style="background: rgba(255,255,255,0.16); color: white;">Phase 1 / MVP</div>
        <h1 class="page-title" style="margin-top: 18px; color: white;">把日文小说变成可学习的阅读流。</h1>
        <p class="page-subtitle" style="color: rgba(255,255,255,0.82);">
          上传 TXT 小说，按章节阅读，点词查询，逐段翻译，把阅读过程直接变成语言学习材料。
        </p>
      </div>

      <div class="auth-form">
        <h2 style="margin-top: 0;">登录</h2>
        <p class="page-subtitle">使用账号进入你的小说书库。</p>

        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="输入密码" />
          </el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%;" size="large" @click="submit">
            登录
          </el-button>
        </el-form>

        <p class="page-subtitle" style="margin-top: 20px;">
          还没有账号？
          <RouterLink to="/register" style="color: var(--accent-primary);">去注册</RouterLink>
        </p>
      </div>
    </div>
  </section>
</template>
