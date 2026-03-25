<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useBookStore } from '@/stores/book'

const router = useRouter()
const bookStore = useBookStore()
const file = ref(null)
const loading = ref(false)

function onFileChange(event) {
  const nextFile = event.target.files?.[0]
  file.value = nextFile || null
}

async function submit() {
  if (!file.value) {
    ElMessage.warning('请先选择 TXT 或 EPUB 文件')
    return
  }

  loading.value = true
  try {
    const book = await bookStore.uploadNovel(file.value)
    ElMessage.success('上传完成')
    const firstChapterId = book.chapters?.[0]?.id
    router.push(firstChapterId ? `/read/${book.id}/chapter/${firstChapterId}` : `/read/${book.id}`)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page">
    <div class="hero-card upload-layout">
      <div>
        <div class="pill">TXT / EPUB Upload</div>
        <h1 class="page-title" style="margin-top: 18px;">把原始日文文本转成可阅读的章节结构。</h1>
        <p class="page-subtitle">
          当前版本支持 UTF-8 编码的 TXT 小说，以及标准 EPUB 电子书。系统会自动抽取章节与段落结构。
        </p>
      </div>

      <div class="surface-card upload-panel">
        <div class="upload-dropzone">
          <input accept=".txt,.epub,application/epub+zip" type="file" @change="onFileChange" />
          <p>{{ file ? file.name : '选择一个 .txt 或 .epub 日文小说文件' }}</p>
        </div>
        <el-button type="primary" :loading="loading" size="large" @click="submit">
          上传并解析
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.upload-layout {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 24px;
  padding: 32px;
}

.upload-panel {
  padding: 24px;
  display: grid;
  gap: 18px;
}

.upload-dropzone {
  border: 1px dashed rgba(47, 111, 176, 0.35);
  background: rgba(47, 111, 176, 0.06);
  padding: 26px;
  border-radius: 18px;
}

.upload-dropzone input {
  width: 100%;
}

@media (max-width: 960px) {
  .upload-layout {
    grid-template-columns: 1fr;
  }
}
</style>
