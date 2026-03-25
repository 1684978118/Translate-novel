<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  word: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['update:modelValue', 'add-word'])

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="单词详情"
    size="360px"
    @close="close"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="word" class="word-drawer">
      <h3>{{ word.word }}</h3>
      <p><strong>读音：</strong>{{ word.reading || '暂无' }}</p>
      <p><strong>词性：</strong>{{ word.partOfSpeech || '暂无' }}</p>
      <p><strong>释义：</strong>{{ word.meaning || '暂无' }}</p>
      <el-button type="primary" round @click="emit('add-word', word)">加入生词本</el-button>
    </div>
  </el-drawer>
</template>
