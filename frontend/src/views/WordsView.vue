<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createWord, deleteWord, fetchWords } from '@/api/collection'

const loading = ref(false)
const words = ref([])
const form = reactive({
  word: '',
  reading: '',
  meaning: '',
  exampleSentence: '',
})

async function load() {
  loading.value = true
  try {
    const result = await fetchWords()
    words.value = result.data
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function submit() {
  try {
    await createWord(form)
    ElMessage.success('已加入生词本')
    form.word = ''
    form.reading = ''
    form.meaning = ''
    form.exampleSentence = ''
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function removeWord(id) {
  try {
    await deleteWord(id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <div class="split-grid">
      <div class="hero-card" style="padding: 28px;">
        <div class="pill">Word Book</div>
        <h1 class="page-title" style="margin-top: 16px;">生词本</h1>
        <p class="page-subtitle">
          点击阅读页中的词元可直接加入，这里也支持手动补充词语、读音和释义。
        </p>
      </div>

      <div class="surface-card" style="padding: 24px;">
        <h3 style="margin-top: 0;">手动添加</h3>
        <el-form label-position="top">
          <el-form-item label="单词">
            <el-input v-model="form.word" placeholder="例如：勉強" />
          </el-form-item>
          <el-form-item label="读音">
            <el-input v-model="form.reading" placeholder="例如：べんきょう" />
          </el-form-item>
          <el-form-item label="释义">
            <el-input v-model="form.meaning" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="例句">
            <el-input v-model="form.exampleSentence" type="textarea" :rows="2" />
          </el-form-item>
          <el-button type="primary" round @click="submit">保存</el-button>
        </el-form>
      </div>
    </div>

    <div v-loading="loading" class="word-grid">
      <article v-for="item in words" :key="item.id" class="surface-card word-card">
        <div class="word-card__top">
          <div>
            <h3>{{ item.word }}</h3>
            <p class="muted">{{ item.reading || '暂无读音' }}</p>
          </div>
          <el-button text type="danger" @click="removeWord(item.id)">删除</el-button>
        </div>
        <p class="page-subtitle">{{ item.meaning }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.word-grid {
  margin-top: 24px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 18px;
}

.word-card {
  padding: 20px;
}

.word-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.word-card h3 {
  margin: 0 0 6px;
  font-family: "Source Han Serif SC", "Songti SC", serif;
  font-size: 24px;
}
</style>
