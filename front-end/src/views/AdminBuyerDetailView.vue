<template>
  <section>
    <div class="catalog-heading"><div><p class="eyebrow">账户运营</p><h1>买家详情</h1></div><RouterLink class="text-action" to="/admin/buyers">返回列表</RouterLink></div>
    <PageStatus :loading="loading" :error="error" :empty="!buyer" empty-text="买家账户不存在。" @retry="load" />
    <template v-if="buyer && !loading && !error"><div class="buyer-detail-layout"><dl class="buyer-detail-list"><div><dt>用户名</dt><dd>{{ buyer.username }}</dd></div><div><dt>昵称</dt><dd>{{ buyer.nickname || '未设置' }}</dd></div><div><dt>手机号</dt><dd>{{ buyer.phone }}</dd></div><div><dt>注册时间</dt><dd>{{ formatDate(buyer.createdAt) }}</dd></div><div><dt>账户状态</dt><dd><span class="status-pill" :class="{ 'status-off': buyer.status === 0 }">{{ buyer.status ? '启用' : '禁用' }}</span></dd></div></dl><div class="buyer-detail-actions"><button class="primary-button" :disabled="busy" @click="toggleStatus">{{ buyer.status ? '禁用账户' : '启用账户' }}</button><RouterLink class="secondary-button" to="/admin/buyers">返回买家列表</RouterLink></div></div><p v-if="message" class="form-message-success" role="status">{{ message }}</p><p v-if="actionError" class="form-message-error" role="alert">{{ actionError }}</p></template>
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageStatus from '../components/PageStatus.vue'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { adminBuyer, setBuyerStatus, type BuyerAccount } from '../api/admin-buyers'
import { errorMessage } from '../api/catalog'
const route = useRoute(), buyer = ref<BuyerAccount | null>(null), loading = ref(false), error = ref(''), actionError = ref(''), message = ref(''), busy = ref(false)
async function load() { loading.value = true; error.value = ''; try { buyer.value = await adminBuyer(Number(route.params.buyerId)) } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } }
async function toggleStatus() { if (!buyer.value) return; const target = buyer.value.status ? 0 : 1; try { await ElMessageBox.confirm(target === 0 ? '禁用后，该买家将无法登录，现有令牌立即失效。' : '确认重新启用该买家账户？', target === 0 ? '禁用买家账户' : '启用买家账户', { confirmButtonText: target === 0 ? '禁用' : '启用', cancelButtonText: '取消', type: target === 0 ? 'warning' : 'info' }) } catch { return } busy.value = true; actionError.value = ''; message.value = ''; try { buyer.value = await setBuyerStatus(buyer.value.id, target); message.value = buyer.value.status ? '买家账户已启用。' : '买家账户已禁用，现有登录令牌已失效。' } catch (e) { actionError.value = errorMessage(e) } finally { busy.value = false } }
function formatDate(value: string) { return value?.replace('T', ' ').slice(0, 16) ?? '—' }
onMounted(load)
</script>
