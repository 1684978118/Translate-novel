<script setup>
const props = defineProps({
  paragraph: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['analyze', 'translate', 'furigana', 'grammar', 'bookmark', 'word'])

function handleWordClick(token) {
  if (token?.surface?.trim()) {
    emit('word', {
      word: token.surface,
      tokenIndex: token.index,
      contextText: props.paragraph.originalText,
    })
  }
}

function isKanji(char) {
  return /\p{Script=Han}/u.test(char)
}

function countLeadingNonKanji(surface) {
  let index = 0
  while (index < surface.length && !isKanji(surface[index])) {
    index += 1
  }
  return index
}

function findTrailingNonKanjiStart(surface) {
  let index = surface.length
  while (index > 0 && !isKanji(surface[index - 1])) {
    index -= 1
  }
  return index
}

function normalizeKana(value) {
  let result = ''
  for (const char of value) {
    const code = char.charCodeAt(0)
    if (code >= 0x3041 && code <= 0x3096) {
      result += char
      continue
    }
    if (code >= 0x30a1 && code <= 0x30f6) {
      result += String.fromCharCode(code - 0x60)
      continue
    }
    if (/\s/u.test(char) || '。、・「」『』（）()！？!?,，．.-ー'.includes(char)) {
      continue
    }
    return ''
  }
  return result
}

function splitRubySegments(surface, reading) {
  if (!surface) {
    return []
  }

  if (![...surface].some((char) => isKanji(char)) || !reading) {
    return [{ text: surface, reading: '' }]
  }

  if ([...surface].every((char) => isKanji(char))) {
    return [{ text: surface, reading }]
  }

  const leadingPlainLength = countLeadingNonKanji(surface)
  if (leadingPlainLength > 0) {
    const leading = surface.slice(0, leadingPlainLength)
    const leadingKana = normalizeKana(leading)
    return [
      { text: leading, reading: '' },
      ...splitRubySegments(
        surface.slice(leadingPlainLength),
        leadingKana && reading.startsWith(leadingKana) ? reading.slice(leadingKana.length) : reading,
      ),
    ]
  }

  const trailingPlainStart = findTrailingNonKanjiStart(surface)
  if (trailingPlainStart < surface.length) {
    const trailing = surface.slice(trailingPlainStart)
    const trailingKana = normalizeKana(trailing)
    const remainingReading =
      trailingKana && reading.endsWith(trailingKana)
        ? reading.slice(0, reading.length - trailingKana.length)
        : reading

    return [
      ...splitRubySegments(surface.slice(0, trailingPlainStart), remainingReading),
      { text: trailing, reading: '' },
    ]
  }

  const matcher = /[^\p{Script=Han}]+/gu
  let match = matcher.exec(surface)
  while (match) {
    if (match.index > 0 && match.index + match[0].length < surface.length) {
      const separator = match[0]
      const separatorKana = normalizeKana(separator)
      if (separatorKana) {
        const readingIndex = reading.indexOf(separatorKana)
        if (readingIndex > 0 && readingIndex < reading.length - separatorKana.length) {
          return [
            ...splitRubySegments(surface.slice(0, match.index), reading.slice(0, readingIndex)),
            { text: separator, reading: '' },
            ...splitRubySegments(
              surface.slice(match.index + separator.length),
              reading.slice(readingIndex + separatorKana.length),
            ),
          ]
        }
      }
    }
    match = matcher.exec(surface)
  }

  return [{ text: surface, reading }]
}

function buildRubySegments(token) {
  return splitRubySegments(token.surface || '', token.kana || '')
}
</script>

<template>
  <article class="paragraph-flow">
    <div class="paragraph-flow__tools">
      <button
        type="button"
        class="analysis-entry"
        :class="{ 'is-active': paragraph.analysisOpen }"
        @click="emit('analyze', paragraph)"
      >
        {{ paragraph.analysisOpen ? '收起分析' : '分析' }}
      </button>
    </div>

    <div v-if="paragraph.furiganaVisible && paragraph.tokens?.length" class="reader-text reader-text--ruby">
      <template v-for="token in paragraph.tokens" :key="`${paragraph.id}-${token.index}-${token.surface}`">
        <template
          v-for="(segment, segmentIndex) in buildRubySegments(token)"
          :key="`${paragraph.id}-${token.index}-${segmentIndex}-${segment.text}`"
        >
          <ruby v-if="segment.reading" class="reader-ruby-token" @click="handleWordClick(token)">
            <span class="reader-token reader-token--ruby">{{ segment.text }}</span>
            <rt>{{ segment.reading }}</rt>
          </ruby>
          <span v-else class="reader-token" @click="handleWordClick(token)">
            {{ segment.text }}
          </span>
        </template>
      </template>
    </div>

    <div v-else-if="paragraph.tokens?.length" class="reader-text">
      <span
        v-for="token in paragraph.tokens"
        :key="`${paragraph.id}-${token.index}-${token.surface}`"
        class="reader-token"
        @click="handleWordClick(token)"
      >
        {{ token.surface }}
      </span>
    </div>

    <div v-else class="reader-text">
      {{ paragraph.originalText }}
    </div>

    <div v-if="paragraph.analysisOpen" class="analysis-toolbar">
      <button
        type="button"
        class="analysis-action"
        :class="{ 'is-active': paragraph.translationVisible }"
        @click="emit('translate', paragraph)"
      >
        {{ paragraph.translationLoading ? '翻译中...' : '翻译' }}
      </button>
      <button
        type="button"
        class="analysis-action"
        :class="{ 'is-active': paragraph.furiganaVisible }"
        @click="emit('furigana', paragraph)"
      >
        {{ paragraph.furiganaLoading ? '注音中...' : '注音' }}
      </button>
      <button
        type="button"
        class="analysis-action"
        :class="{ 'is-active': paragraph.grammarVisible }"
        @click="emit('grammar', paragraph)"
      >
        {{ paragraph.grammarLoading ? '分析中...' : '语法' }}
      </button>
      <button type="button" class="analysis-bookmark" @click="emit('bookmark', paragraph)">
        加入书签
      </button>
    </div>

    <div v-if="paragraph.analysisError" class="analysis-error">
      {{ paragraph.analysisError }}
    </div>

    <div v-if="paragraph.translationVisible" class="analysis-panel analysis-panel--translation">
      <div class="analysis-panel__label">译文</div>
      <div v-if="paragraph.translationLoading" class="analysis-loading">正在生成译文...</div>
      <div v-else class="analysis-copy">{{ paragraph.translation }}</div>
    </div>

    <div v-if="paragraph.grammarVisible" class="analysis-panel analysis-panel--grammar">
      <div class="analysis-panel__label">语法</div>
      <div v-if="paragraph.grammarLoading" class="analysis-loading">正在分析语法...</div>
      <template v-else-if="paragraph.grammar">
        <div v-if="paragraph.grammar.patterns?.length" class="grammar-list">
          <div
            v-for="pattern in paragraph.grammar.patterns"
            :key="`${paragraph.id}-${pattern.name}`"
            class="grammar-item"
          >
            <strong>{{ pattern.name }}</strong>
            <span>{{ pattern.explanation }}</span>
          </div>
        </div>
      </template>
    </div>
  </article>
</template>

<style scoped>
.paragraph-flow {
  padding: 22px 0 34px;
}

.paragraph-flow__tools {
  margin-bottom: 14px;
}

.analysis-entry,
.analysis-action,
.analysis-bookmark {
  border: 0;
  background: transparent;
  cursor: pointer;
  font: inherit;
}

.analysis-entry {
  padding: 7px 18px;
  border-radius: 999px;
  border: 1px solid var(--reader-accent-soft, rgba(47, 139, 87, 0.4));
  background: var(--reader-button-bg, rgba(255, 255, 255, 0.76));
  color: var(--reader-accent-strong, #1f6a43);
  font-weight: 700;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.analysis-entry.is-active,
.analysis-entry:hover {
  background: var(--reader-button-active-bg, rgba(47, 139, 87, 0.12));
  border-color: var(--reader-accent-strong, rgba(47, 139, 87, 0.56));
}

.reader-text {
  font-family: var(--reader-font-family, "Source Han Serif JP", "Noto Serif JP", "Yu Mincho", serif);
  font-size: var(--reader-font-size, 32px);
  line-height: var(--reader-line-height, 2);
  letter-spacing: 0.04em;
  color: var(--reader-text-color, var(--text-primary));
  word-break: break-word;
}

.reader-token {
  cursor: pointer;
  padding: 1px 3px;
  border-radius: 8px;
  transition: background 0.18s ease, color 0.18s ease;
}

.reader-token:hover {
  background: var(--reader-token-hover-bg, rgba(47, 111, 176, 0.12));
  color: var(--reader-token-hover-color, var(--accent-primary));
}

.reader-ruby-token {
  cursor: pointer;
}

.reader-text--ruby :deep(rt) {
  color: var(--reader-ruby-color, var(--accent-primary));
  font-size: 11px;
}

.analysis-toolbar {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.analysis-action {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid var(--reader-border-soft, rgba(142, 116, 89, 0.24));
  background: var(--reader-button-bg, rgba(255, 255, 255, 0.72));
  color: var(--reader-subtle-text, var(--text-secondary));
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.analysis-action.is-active,
.analysis-action:hover {
  background: var(--reader-token-hover-bg, rgba(47, 111, 176, 0.1));
  color: var(--reader-token-hover-color, var(--accent-primary));
  border-color: var(--reader-token-hover-border, rgba(47, 111, 176, 0.28));
}

.analysis-bookmark {
  color: var(--reader-bookmark-color, var(--accent-warning));
  font-weight: 600;
  padding: 6px 4px;
}

.analysis-error,
.analysis-panel {
  margin-top: 14px;
  border-radius: 18px;
  padding: 16px 18px;
}

.analysis-error {
  background: rgba(201, 106, 58, 0.1);
  color: #9b4a24;
}

.analysis-panel {
  background: var(--reader-panel-bg, rgba(142, 116, 89, 0.06));
}

.analysis-panel--translation {
  background: var(--reader-translation-bg, rgba(47, 111, 176, 0.08));
}

.analysis-panel--grammar {
  background: var(--reader-grammar-bg, rgba(47, 139, 87, 0.08));
}

.analysis-panel__label {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--reader-muted-text, var(--text-muted));
}

.analysis-loading,
.analysis-copy {
  line-height: 1.9;
  color: var(--reader-subtle-text, var(--text-secondary));
}

.analysis-copy {
  color: var(--reader-text-color, var(--text-primary));
  font-size: 17px;
}

.grammar-list {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.grammar-item {
  display: grid;
  gap: 4px;
  line-height: 1.75;
  color: var(--reader-text-color, var(--text-primary));
}

@media (max-width: 760px) {
  .reader-text {
    font-size: var(--reader-mobile-font-size, 24px);
  }
}
</style>
