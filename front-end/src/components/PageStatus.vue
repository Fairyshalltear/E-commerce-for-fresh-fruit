<template>
  <div v-if="loading || error || empty" class="page-status" :role="error ? 'alert' : 'status'">
    <span class="status-dot" :class="{ 'status-error': error }"></span>
    <p>{{ loading ? '正在加载…' : error || emptyText }}</p>
    <button v-if="error && !loading" class="secondary-button" @click="$emit('retry')">重新加载</button>
  </div>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ loading?: boolean; error?: string; empty?: boolean; emptyText?: string }>(), { emptyText: '暂无数据' })
defineEmits<{ retry: [] }>()
</script>
