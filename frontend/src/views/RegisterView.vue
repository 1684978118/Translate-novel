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
  email: '',
  password: '',
})

async function submit() {
  loading.value = true
  try {
    await authStore.registerWithCredentials(form)
    ElMessage.success('注册成功')
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
        <div class="pill" style="background: rgba(255,255,255,0.16); color: white;">Reader Setup</div>
        <h1 class="page-title" style="margin-top: 18px; color: white;">创建你的日文阅读工作台。</h1>
        <p class="page-subtitle" style="color: rgba(255,255,255,0.82);">
          新账号会直接进入个人书库，后续可继续扩展书签、生词本和阅读进度同步。
        </p>
      </div>

      <div class="auth-form">
        <h2 style="margin-top: 0;">注册</h2>
        <p class="page-subtitle">创建一个用于管理个人书库和学习记录的账号。</p>

        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="至少 3 个字符" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="you@example.com" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="至少 6 个字符" />
          </el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%;" size="large" @click="submit">
            注册并进入
          </el-button>
        </el-form>

        <p class="page-subtitle" style="margin-top: 20px;">
          已经有账号？
          <RouterLink to="/login" style="color: var(--accent-primary);">去登录</RouterLink>
        </p>
      </div>
    </div>
  </section>
</template>
