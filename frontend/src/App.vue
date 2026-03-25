<template>
  <div class="app-shell">
    <div class="ambient ambient-left"></div>
    <div class="ambient ambient-right"></div>
    <AppHeader />
    <RouterView />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
</script>
