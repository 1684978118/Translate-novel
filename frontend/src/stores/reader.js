import { ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'translate_novel_reader_preferences'

const DEFAULT_PREFERENCES = {
  fontSize: 32,
  lineHeight: 2,
  contentWidth: 920,
  themeId: 'paper',
  fontFamily: 'serif',
}

function readPreferences() {
  if (typeof window === 'undefined') {
    return { ...DEFAULT_PREFERENCES }
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return { ...DEFAULT_PREFERENCES }
    }
    return {
      ...DEFAULT_PREFERENCES,
      ...JSON.parse(raw),
    }
  } catch {
    return { ...DEFAULT_PREFERENCES }
  }
}

export const useReaderStore = defineStore('reader', () => {
  const preferences = readPreferences()

  const chapterNavOpen = ref(false)
  const settingsOpen = ref(false)
  const activeWord = ref(null)
  const fontSize = ref(preferences.fontSize)
  const lineHeight = ref(preferences.lineHeight)
  const contentWidth = ref(preferences.contentWidth)
  const themeId = ref(preferences.themeId)
  const fontFamily = ref(preferences.fontFamily)

  function persistPreferences() {
    if (typeof window === 'undefined') {
      return
    }

    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        fontSize: fontSize.value,
        lineHeight: lineHeight.value,
        contentWidth: contentWidth.value,
        themeId: themeId.value,
        fontFamily: fontFamily.value,
      }),
    )
  }

  function toggleChapterNav() {
    chapterNavOpen.value = !chapterNavOpen.value
  }

  function closeChapterNav() {
    chapterNavOpen.value = false
  }

  function toggleSettings() {
    settingsOpen.value = !settingsOpen.value
  }

  function closeSettings() {
    settingsOpen.value = false
  }

  function setActiveWord(word) {
    activeWord.value = word
  }

  function setFontSize(value) {
    fontSize.value = value
    persistPreferences()
  }

  function setLineHeight(value) {
    lineHeight.value = value
    persistPreferences()
  }

  function setContentWidth(value) {
    contentWidth.value = value
    persistPreferences()
  }

  function setTheme(theme) {
    themeId.value = theme
    persistPreferences()
  }

  function setFontFamily(value) {
    fontFamily.value = value
    persistPreferences()
  }

  function resetAppearance() {
    fontSize.value = DEFAULT_PREFERENCES.fontSize
    lineHeight.value = DEFAULT_PREFERENCES.lineHeight
    contentWidth.value = DEFAULT_PREFERENCES.contentWidth
    themeId.value = DEFAULT_PREFERENCES.themeId
    fontFamily.value = DEFAULT_PREFERENCES.fontFamily
    persistPreferences()
  }

  return {
    chapterNavOpen,
    settingsOpen,
    activeWord,
    fontSize,
    lineHeight,
    contentWidth,
    themeId,
    fontFamily,
    toggleChapterNav,
    closeChapterNav,
    toggleSettings,
    closeSettings,
    setActiveWord,
    setFontSize,
    setLineHeight,
    setContentWidth,
    setTheme,
    setFontFamily,
    resetAppearance,
  }
})
