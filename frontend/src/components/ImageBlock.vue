<script setup>
import { ref } from 'vue'

const props = defineProps({
  block: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['bookmark'])

const failed = ref(false)

function handleError() {
  failed.value = true
}
</script>

<template>
  <article class="image-flow">
    <div class="image-flow__tools">
      <button type="button" class="image-flow__bookmark" @click="emit('bookmark', block)">
        加入书签
      </button>
    </div>

    <div class="image-frame">
      <img
        v-if="block.imageUrl && !failed"
        class="image-frame__asset"
        :src="block.imageUrl"
        :alt="block.originalText || '小说插画'"
        loading="lazy"
        @error="handleError"
      />
      <div v-else class="image-frame__fallback">
        插画加载失败
      </div>
    </div>

    <p v-if="block.originalText" class="image-frame__caption">
      {{ block.originalText }}
    </p>
  </article>
</template>

<style scoped>
.image-flow {
  padding: 26px 0 34px;
}

.image-flow__tools {
  margin-bottom: 14px;
}

.image-flow__bookmark {
  border: 0;
  background: transparent;
  cursor: pointer;
  color: var(--reader-bookmark-color, var(--accent-warning));
  font: inherit;
  font-weight: 600;
  padding: 6px 4px;
}

.image-frame {
  border-radius: 24px;
  overflow: hidden;
  background: color-mix(in srgb, var(--reader-panel-bg, rgba(142, 116, 89, 0.06)) 88%, white);
  border: 1px solid color-mix(in srgb, var(--reader-border-soft, rgba(142, 116, 89, 0.24)) 72%, transparent);
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-frame__asset {
  display: block;
  width: 100%;
  max-height: min(78vh, 980px);
  object-fit: contain;
  background: color-mix(in srgb, var(--reader-paper-bg, rgba(255, 251, 244, 0.96)) 86%, white);
}

.image-frame__fallback {
  padding: 40px 24px;
  color: var(--reader-subtle-text, var(--text-secondary));
  text-align: center;
}

.image-frame__caption {
  margin: 14px 2px 0;
  color: var(--reader-subtle-text, var(--text-secondary));
  line-height: 1.8;
  font-size: 15px;
}
</style>
