import { onMounted, onUnmounted, ref } from 'vue'
import { alertCount } from '../api/inventory'
import { errorMessage } from '../api/catalog'
export function useInventoryAlerts() {
  const count = ref<number | null>(null), error = ref('')
  let pending = false, active = true, timer: ReturnType<typeof setInterval> | undefined
  async function refresh() { if (pending || !active || document.hidden) return; pending = true; try { const next = await alertCount(); if (active) { count.value = next; error.value = '' } } catch (e) { if (active) error.value = errorMessage(e) } finally { pending = false } }
  function visible() { if (!document.hidden) void refresh() }
  onMounted(() => { void refresh(); timer = setInterval(refresh, 60000); document.addEventListener('visibilitychange', visible); window.addEventListener('fresh-fruit-inventory-changed', refresh) })
  onUnmounted(() => { active = false; clearInterval(timer); document.removeEventListener('visibilitychange', visible); window.removeEventListener('fresh-fruit-inventory-changed', refresh) })
  return { count, error, refresh }
}
