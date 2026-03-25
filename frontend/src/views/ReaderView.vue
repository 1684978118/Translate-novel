<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import ParagraphBlock from '@/components/ParagraphBlock.vue'
import WordDrawer from '@/components/WordDrawer.vue'
import { fetchBookDetail, fetchProgress, saveProgress } from '@/api/book'
import { createBookmark, createWord } from '@/api/collection'
import { analyzeGrammar, fetchFurigana, fetchParagraphs, lookupWord, translateParagraph } from '@/api/read'
import { useReaderStore } from '@/stores/reader'

const READER_THEMES = [
  {
    id: 'paper',
    name: '羊皮纸',
    page: 'linear-gradient(180deg, #f8f1e5 0%, #f3ead9 100%)',
    paper: 'rgba(255, 251, 244, 0.96)',
    text: '#2f261c',
    subtle: '#6b5a49',
    muted: '#907f6e',
    border: 'rgba(142, 116, 89, 0.22)',
    accent: '#2f6fb0',
    accentSoft: 'rgba(47, 111, 176, 0.28)',
    buttonBg: 'rgba(255, 255, 255, 0.76)',
    buttonActiveBg: 'rgba(47, 111, 176, 0.12)',
    hoverBg: 'rgba(47, 111, 176, 0.12)',
    hoverColor: '#2f6fb0',
    hoverBorder: 'rgba(47, 111, 176, 0.28)',
    ruby: '#2f6fb0',
    bookmark: '#b7791f',
    panel: 'rgba(142, 116, 89, 0.06)',
    translation: 'rgba(47, 111, 176, 0.08)',
    grammar: 'rgba(47, 139, 87, 0.08)',
  },
  {
    id: 'mint',
    name: '青瓷绿',
    page: 'linear-gradient(180deg, #edf4ee 0%, #e5efe8 100%)',
    paper: 'rgba(248, 252, 249, 0.97)',
    text: '#243328',
    subtle: '#516555',
    muted: '#6f8474',
    border: 'rgba(98, 128, 107, 0.24)',
    accent: '#3f7d63',
    accentSoft: 'rgba(63, 125, 99, 0.34)',
    buttonBg: 'rgba(255, 255, 255, 0.8)',
    buttonActiveBg: 'rgba(63, 125, 99, 0.12)',
    hoverBg: 'rgba(63, 125, 99, 0.12)',
    hoverColor: '#2f6b52',
    hoverBorder: 'rgba(63, 125, 99, 0.28)',
    ruby: '#3f7d63',
    bookmark: '#9c6f24',
    panel: 'rgba(63, 125, 99, 0.06)',
    translation: 'rgba(63, 125, 99, 0.08)',
    grammar: 'rgba(73, 109, 135, 0.08)',
  },
  {
    id: 'night',
    name: '深夜黑',
    page: 'linear-gradient(180deg, #1b1f26 0%, #13161c 100%)',
    paper: 'rgba(26, 30, 38, 0.95)',
    text: '#ebe5d9',
    subtle: '#c8bfad',
    muted: '#9f988a',
    border: 'rgba(135, 149, 173, 0.18)',
    accent: '#7fb2ff',
    accentSoft: 'rgba(127, 178, 255, 0.3)',
    buttonBg: 'rgba(40, 46, 57, 0.9)',
    buttonActiveBg: 'rgba(127, 178, 255, 0.16)',
    hoverBg: 'rgba(127, 178, 255, 0.16)',
    hoverColor: '#9dc4ff',
    hoverBorder: 'rgba(127, 178, 255, 0.28)',
    ruby: '#9dc4ff',
    bookmark: '#f3c36d',
    panel: 'rgba(127, 178, 255, 0.08)',
    translation: 'rgba(127, 178, 255, 0.1)',
    grammar: 'rgba(112, 205, 172, 0.12)',
  },
]

const FONT_FAMILIES = {
  serif: '"Source Han Serif JP", "Noto Serif JP", "Yu Mincho", serif',
  sans: '"Hiragino Sans GB", "PingFang SC", "Microsoft YaHei", sans-serif',
}

const route = useRoute()
const router = useRouter()
const readerStore = useReaderStore()

const loading = ref(false)
const book = ref(null)
const chapters = ref([])
const paragraphs = ref([])
const bookProgress = ref(null)
const selectedWord = ref(null)
const drawerOpen = ref(false)
const paragraphRefs = ref({})

let saveTimer = null
let restoreTimer = null
let lastProgressErrorAt = 0

const currentChapterId = computed(() => Number(route.params.chapterId || 0))
const currentChapter = computed(
  () => chapters.value.find((chapter) => chapter.id === currentChapterId.value) || null,
)
const currentChapterIndex = computed(() =>
  chapters.value.findIndex((chapter) => chapter.id === currentChapterId.value),
)
const previousChapter = computed(() =>
  currentChapterIndex.value > 0 ? chapters.value[currentChapterIndex.value - 1] : null,
)
const nextChapter = computed(() =>
  currentChapterIndex.value >= 0 && currentChapterIndex.value < chapters.value.length - 1
    ? chapters.value[currentChapterIndex.value + 1]
    : null,
)
const currentTheme = computed(
  () => READER_THEMES.find((theme) => theme.id === readerStore.themeId) || READER_THEMES[0],
)
const readerSurfaceStyle = computed(() => ({
  '--reader-page-bg': currentTheme.value.page,
  '--reader-paper-bg': currentTheme.value.paper,
  '--reader-text-color': currentTheme.value.text,
  '--reader-subtle-text': currentTheme.value.subtle,
  '--reader-muted-text': currentTheme.value.muted,
  '--reader-border-soft': currentTheme.value.border,
  '--reader-accent-strong': currentTheme.value.accent,
  '--reader-accent-soft': currentTheme.value.accentSoft,
  '--reader-button-bg': currentTheme.value.buttonBg,
  '--reader-button-active-bg': currentTheme.value.buttonActiveBg,
  '--reader-token-hover-bg': currentTheme.value.hoverBg,
  '--reader-token-hover-color': currentTheme.value.hoverColor,
  '--reader-token-hover-border': currentTheme.value.hoverBorder,
  '--reader-ruby-color': currentTheme.value.ruby,
  '--reader-bookmark-color': currentTheme.value.bookmark,
  '--reader-panel-bg': currentTheme.value.panel,
  '--reader-translation-bg': currentTheme.value.translation,
  '--reader-grammar-bg': currentTheme.value.grammar,
  '--reader-font-size': `${readerStore.fontSize}px`,
  '--reader-mobile-font-size': `${Math.max(readerStore.fontSize - 6, 20)}px`,
  '--reader-line-height': String(readerStore.lineHeight),
  '--reader-font-family': FONT_FAMILIES[readerStore.fontFamily] || FONT_FAMILIES.serif,
  '--reader-content-width': `${readerStore.contentWidth}px`,
}))

function createParagraphState(item) {
  return {
    ...item,
    grammar: item.grammar || null,
    analysisOpen: false,
    analysisError: '',
    translationVisible: false,
    furiganaVisible: false,
    grammarVisible: false,
    translationLoading: false,
    furiganaLoading: false,
    grammarLoading: false,
  }
}

async function loadBookAndChapter() {
  const bookId = Number(route.params.bookId)
  if (!bookId) {
    return
  }

  loading.value = true
  try {
    const detail = await fetchBookDetail(bookId)
    book.value = detail.data
    chapters.value = detail.data.chapters || []

    try {
      const progress = await fetchProgress(bookId)
      bookProgress.value = progress.data
    } catch {
      bookProgress.value = null
    }

    let targetChapterId = Number(route.params.chapterId || 0)
    if (!targetChapterId) {
      targetChapterId = bookProgress.value?.chapterId || chapters.value[0]?.id
      if (targetChapterId) {
        await router.replace(`/read/${bookId}/chapter/${targetChapterId}`)
        return
      }
    }

    await loadChapter(targetChapterId)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function loadChapter(chapterId) {
  if (!chapterId) {
    paragraphs.value = []
    paragraphRefs.value = {}
    return
  }

  loading.value = true
  try {
    const result = await fetchParagraphs(chapterId)
    paragraphs.value = result.data.map(createParagraphState)
    await restoreProgress(chapterId)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function handleAnalyze(paragraph) {
  if (paragraph.analysisOpen) {
    paragraph.analysisOpen = false
    paragraph.translationVisible = false
    paragraph.furiganaVisible = false
    paragraph.grammarVisible = false
    paragraph.analysisError = ''
    return
  }

  paragraph.analysisOpen = true
  paragraph.analysisError = ''
}

async function handleTranslate(paragraph) {
  if (paragraph.translationVisible) {
    paragraph.translationVisible = false
    return
  }

  paragraph.analysisError = ''
  paragraph.translationVisible = true

  if (paragraph.translation || paragraph.translationLoading) {
    return
  }

  paragraph.translationLoading = true
  try {
    const result = await translateParagraph(paragraph.id)
    paragraph.translation = result.data.translation
  } catch (error) {
    paragraph.translationVisible = false
    paragraph.analysisError = error.message || '翻译加载失败，请稍后重试。'
  } finally {
    paragraph.translationLoading = false
  }
}

async function handleFurigana(paragraph) {
  if (paragraph.furiganaVisible) {
    paragraph.furiganaVisible = false
    return
  }

  paragraph.analysisError = ''
  paragraph.furiganaVisible = true

  if (paragraph.furiganaText || paragraph.furiganaLoading) {
    return
  }

  paragraph.furiganaLoading = true
  try {
    const result = await fetchFurigana(paragraph.id)
    paragraph.furiganaText = result.data.rubyHtml
  } catch (error) {
    paragraph.furiganaVisible = false
    paragraph.analysisError = error.message || '注音加载失败，请稍后重试。'
  } finally {
    paragraph.furiganaLoading = false
  }
}

async function handleGrammar(paragraph) {
  if (paragraph.grammarVisible) {
    paragraph.grammarVisible = false
    return
  }

  paragraph.analysisError = ''
  paragraph.grammarVisible = true

  if (paragraph.grammar || paragraph.grammarLoading) {
    return
  }

  paragraph.grammarLoading = true
  try {
    const result = await analyzeGrammar(paragraph.id)
    paragraph.grammar = result.data
  } catch (error) {
    paragraph.grammarVisible = false
    paragraph.analysisError = error.message || '语法分析失败，请稍后重试。'
  } finally {
    paragraph.grammarLoading = false
  }
}

async function handleWord(payload) {
  try {
    const result = await lookupWord(payload)
    selectedWord.value = result.data
    drawerOpen.value = true
    readerStore.setActiveWord(result.data)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function handleBookmark(paragraph) {
  try {
    await createBookmark({
      bookId: Number(route.params.bookId),
      paragraphId: paragraph.id,
      note: '',
    })
    ElMessage.success('已加入书签')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function addWordToDictionary(word) {
  try {
    await createWord({
      word: word.word,
      reading: word.reading,
      meaning: word.meaning,
      exampleSentence: '',
    })
    ElMessage.success('已加入生词本')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function jumpChapter(chapterId) {
  readerStore.closeChapterNav()
  router.push(`/read/${route.params.bookId}/chapter/${chapterId}`)
}

function setParagraphRef(element, paragraphId) {
  if (element) {
    paragraphRefs.value[paragraphId] = element
    return
  }

  delete paragraphRefs.value[paragraphId]
}

async function restoreProgress(chapterId) {
  clearRestoreTimer()
  await nextTick()

  const progress = bookProgress.value
  restoreTimer = window.setTimeout(() => {
    const savedScroll = progress?.chapterId === chapterId ? Number(progress.scrollPosition || 0) : 0
    if (savedScroll > 0) {
      window.scrollTo({ top: savedScroll, behavior: 'auto' })
    } else if (progress?.chapterId === chapterId && progress?.paragraphId) {
      paragraphRefs.value[progress.paragraphId]?.scrollIntoView({ block: 'start' })
    } else {
      window.scrollTo({ top: 0, behavior: 'auto' })
    }

    scheduleProgressSave(true)
  }, 0)
}

function scheduleProgressSave(immediate = false) {
  clearSaveTimer()

  if (immediate) {
    persistProgress()
    return
  }

  saveTimer = window.setTimeout(() => {
    persistProgress()
  }, 500)
}

async function persistProgress() {
  const bookId = Number(route.params.bookId)
  const chapterId = currentChapterId.value
  if (!bookId || !chapterId || !paragraphs.value.length) {
    return
  }

  const paragraphId = getCurrentParagraphId()
  if (!paragraphId) {
    return
  }

  try {
    const result = await saveProgress(bookId, {
      chapterId,
      paragraphId,
      scrollPosition: Math.max(Math.round(window.scrollY || window.pageYOffset || 0), 0),
    })
    bookProgress.value = result.data
  } catch (error) {
    const now = Date.now()
    if (now - lastProgressErrorAt > 5000) {
      ElMessage.error(error.message)
      lastProgressErrorAt = now
    }
  }
}

function getCurrentParagraphId() {
  if (!paragraphs.value.length) {
    return null
  }

  const threshold = window.innerHeight * 0.3
  let currentParagraph = paragraphs.value[0]

  for (const paragraph of paragraphs.value) {
    const element = paragraphRefs.value[paragraph.id]
    if (!element) {
      continue
    }

    if (element.getBoundingClientRect().top <= threshold) {
      currentParagraph = paragraph
      continue
    }

    break
  }

  return currentParagraph?.id || null
}

function handleScroll() {
  scheduleProgressSave()
}

function clearSaveTimer() {
  if (saveTimer) {
    window.clearTimeout(saveTimer)
    saveTimer = null
  }
}

function clearRestoreTimer() {
  if (restoreTimer) {
    window.clearTimeout(restoreTimer)
    restoreTimer = null
  }
}

watch(
  () => route.params.chapterId,
  async (value, previous) => {
    if (value && value !== previous) {
      await loadChapter(Number(value))
    }
  },
)

watch(
  () => route.params.bookId,
  async (value, previous) => {
    if (value && value !== previous) {
      await loadBookAndChapter()
    }
  },
)

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  loadBookAndChapter()
})

onBeforeUnmount(() => {
  clearSaveTimer()
  clearRestoreTimer()
  window.removeEventListener('scroll', handleScroll)
  persistProgress()
})
</script>

<template>
  <section class="page reader-page" :style="readerSurfaceStyle">
    <div v-if="book" class="hero-card reader-hero">
      <div class="reader-hero__copy">
        <div class="pill">{{ book.sourceType }} · {{ book.status }}</div>
        <h1 class="page-title reader-hero__title">{{ book.title }}</h1>
        <p class="page-subtitle reader-hero__meta">
          {{ book.author || '未知作者' }} · {{ book.wordCount || 0 }} 词元 · {{ chapters.length }} 章
        </p>
      </div>
      <div class="reader-hero__actions">
        <el-button round @click="readerStore.toggleChapterNav()">目录</el-button>
        <el-button round @click="readerStore.toggleSettings()">阅读设置</el-button>
      </div>
    </div>

    <div v-loading="loading" class="reader-stage">
      <article class="surface-card reader-paper">
        <header v-if="currentChapter" class="reader-paper__header">
          <div class="reader-paper__eyebrow">当前章节</div>
          <h2 class="reader-paper__title">{{ currentChapter.title }}</h2>
          <p class="reader-paper__hint">
            点击“分析”后再按需查看翻译、注音和语法。阅读设置里可以调整字号、主题、页宽和字体。
          </p>
        </header>

        <div v-if="paragraphs.length" class="reader-stream">
          <div
            v-for="paragraph in paragraphs"
            :key="paragraph.id"
            :ref="(element) => setParagraphRef(element, paragraph.id)"
            :data-paragraph-id="paragraph.id"
            class="reader-stream__item"
          >
            <ParagraphBlock
              :paragraph="paragraph"
              @analyze="handleAnalyze"
              @translate="handleTranslate"
              @furigana="handleFurigana"
              @grammar="handleGrammar"
              @bookmark="handleBookmark"
              @word="handleWord"
            />
          </div>
        </div>

        <div v-else-if="!loading" class="reader-empty">
          当前章节暂时没有可阅读内容。
        </div>
      </article>
    </div>

    <div v-if="book" class="reader-footer-nav">
      <el-button :disabled="!previousChapter" round @click="previousChapter && jumpChapter(previousChapter.id)">
        上一章
      </el-button>
      <div class="reader-footer-nav__meta">
        <strong>{{ currentChapterIndex + 1 }}</strong>
        <span>/ {{ chapters.length }} 章</span>
      </div>
      <el-button :disabled="!nextChapter" type="primary" round @click="nextChapter && jumpChapter(nextChapter.id)">
        下一章
      </el-button>
    </div>

    <el-drawer
      v-model="readerStore.chapterNavOpen"
      title="章节目录"
      size="360px"
      direction="rtl"
    >
      <div class="reader-drawer">
        <button
          v-for="chapter in chapters"
          :key="chapter.id"
          class="chapter-link"
          :class="{ active: chapter.id === currentChapterId }"
          @click="jumpChapter(chapter.id)"
        >
          <span>{{ chapter.chapterOrder }}.</span>
          <span>{{ chapter.title }}</span>
        </button>
      </div>
    </el-drawer>

    <el-drawer
      v-model="readerStore.settingsOpen"
      title="阅读设置"
      size="420px"
      direction="rtl"
    >
      <div class="settings-panel">
        <section class="settings-group">
          <div class="settings-group__title">主题</div>
          <div class="theme-grid">
            <button
              v-for="theme in READER_THEMES"
              :key="theme.id"
              type="button"
              class="theme-swatch"
              :class="{ active: readerStore.themeId === theme.id }"
              @click="readerStore.setTheme(theme.id)"
            >
              <span class="theme-swatch__preview" :style="{ background: theme.page }"></span>
              <span>{{ theme.name }}</span>
            </button>
          </div>
        </section>

        <section class="settings-group">
          <div class="settings-group__title">字体</div>
          <el-radio-group :model-value="readerStore.fontFamily" @change="readerStore.setFontFamily">
            <el-radio-button label="serif">衬线体</el-radio-button>
            <el-radio-button label="sans">无衬线</el-radio-button>
          </el-radio-group>
        </section>

        <section class="settings-group">
          <div class="settings-group__title">字号</div>
          <el-slider
            :model-value="readerStore.fontSize"
            :min="24"
            :max="40"
            :step="1"
            show-input
            @update:model-value="readerStore.setFontSize"
          />
        </section>

        <section class="settings-group">
          <div class="settings-group__title">行距</div>
          <el-slider
            :model-value="readerStore.lineHeight"
            :min="1.6"
            :max="2.6"
            :step="0.1"
            show-input
            @update:model-value="readerStore.setLineHeight"
          />
        </section>

        <section class="settings-group">
          <div class="settings-group__title">版心宽度</div>
          <el-slider
            :model-value="readerStore.contentWidth"
            :min="760"
            :max="1080"
            :step="20"
            show-input
            @update:model-value="readerStore.setContentWidth"
          />
        </section>

        <el-button round @click="readerStore.resetAppearance()">恢复默认</el-button>
      </div>
    </el-drawer>

    <WordDrawer v-model="drawerOpen" :word="selectedWord" @add-word="addWordToDictionary" />
  </section>
</template>

<style scoped>
.reader-page {
  max-width: 1560px;
  background: var(--reader-page-bg);
  border-radius: 36px;
  transition: background 0.25s ease;
}

.reader-hero {
  padding: 28px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background: color-mix(in srgb, var(--reader-paper-bg) 88%, transparent);
  border-color: var(--reader-border-soft);
}

.reader-hero__copy {
  min-width: 0;
}

.reader-hero__title {
  margin-top: 16px;
  color: var(--reader-text-color);
}

.reader-hero__meta {
  color: var(--reader-subtle-text);
}

.reader-hero__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.reader-stage {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

.reader-paper {
  width: min(100%, calc(var(--reader-content-width) + 96px));
  overflow: hidden;
  background: var(--reader-paper-bg);
  border-color: var(--reader-border-soft);
  box-shadow: 0 24px 60px rgba(36, 28, 18, 0.08);
}

.reader-paper__header {
  padding: 36px 48px 18px;
}

.reader-paper__eyebrow {
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--reader-muted-text);
}

.reader-paper__title {
  margin: 12px 0 0;
  font-family: "Source Han Serif SC", "Songti SC", serif;
  font-size: clamp(24px, 3vw, 36px);
  line-height: 1.2;
  color: var(--reader-text-color);
}

.reader-paper__hint {
  margin: 12px 0 0;
  line-height: 1.8;
  color: var(--reader-subtle-text);
}

.reader-stream {
  padding: 4px 0 46px;
}

.reader-stream__item {
  padding: 0 48px;
  scroll-margin-top: calc(var(--header-height) + 28px);
}

.reader-stream__item + .reader-stream__item {
  border-top: 1px solid color-mix(in srgb, var(--reader-border-soft) 60%, transparent);
}

.reader-empty {
  padding: 40px 48px 48px;
  color: var(--reader-subtle-text);
}

.reader-footer-nav {
  margin-top: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
}

.reader-footer-nav__meta {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: var(--reader-subtle-text);
}

.reader-drawer,
.settings-panel {
  display: grid;
  gap: 16px;
}

.chapter-link {
  width: 100%;
  border: 0;
  background: transparent;
  display: flex;
  gap: 12px;
  padding: 14px 12px;
  border-radius: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
}

.chapter-link.active {
  background: rgba(47, 111, 176, 0.12);
  color: var(--accent-primary);
}

.settings-group {
  display: grid;
  gap: 12px;
}

.settings-group__title {
  font-weight: 700;
  color: var(--text-primary);
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.theme-swatch {
  border: 1px solid rgba(142, 116, 89, 0.18);
  border-radius: 16px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.7);
  display: grid;
  gap: 8px;
  text-align: left;
  cursor: pointer;
}

.theme-swatch.active {
  border-color: rgba(47, 111, 176, 0.4);
  box-shadow: 0 0 0 2px rgba(47, 111, 176, 0.08);
}

.theme-swatch__preview {
  height: 48px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.24);
}

@media (max-width: 760px) {
  .reader-hero {
    padding: 24px;
    align-items: flex-start;
    flex-direction: column;
  }

  .reader-paper {
    width: 100%;
  }

  .reader-paper__header,
  .reader-stream__item,
  .reader-empty {
    padding-left: 24px;
    padding-right: 24px;
  }

  .theme-grid {
    grid-template-columns: 1fr;
  }

  .reader-footer-nav {
    flex-wrap: wrap;
  }
}
</style>
