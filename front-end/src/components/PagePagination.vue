<template>
  <nav v-if="total > 0" class="pagination" aria-label="分页">
    <span>共 {{ total }} 条</span><div><button class="secondary-button" :disabled="page <= 1 || busy" @click="$emit('change', page - 1)">上一页</button>
    <span>{{ page }} / {{ Math.max(1, Math.ceil(total / size)) }}</span><button class="secondary-button" :disabled="page >= Math.ceil(total / size) || busy" @click="$emit('change', page + 1)">下一页</button></div>
  </nav>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ total: number; page: number; size?: number; busy?: boolean }>(), { size: 12 })
defineEmits<{ change: [page: number] }>()
</script>
