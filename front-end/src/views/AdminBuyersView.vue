<template>
  <section>
    <div class="catalog-heading"><div><p class="eyebrow">账户运营</p><h1>买家管理</h1></div></div>
    <form class="admin-filters" @submit.prevent="search"><label class="form-field">搜索买家<input v-model.trim="keyword" maxlength="100" placeholder="用户名、手机号或昵称"></label><label class="form-field">账户状态<select v-model="statusFilter"><option value="">全部状态</option><option value="1">启用</option><option value="0">禁用</option></select></label><button class="primary-button">查询</button><button type="button" class="text-action" @click="reset">重置</button></form>
    <p v-if="message" class="form-message-success" role="status">{{ message }}</p><p v-if="actionError" class="form-message-error" role="alert">{{ actionError }}</p>
    <PageStatus :loading="loading" :error="error" :empty="!items.length" empty-text="没有符合条件的买家。" @retry="load" />
    <div v-if="!loading && !error && items.length" class="table-scroll"><table class="admin-table buyer-table"><thead><tr><th>买家</th><th>手机号</th><th>注册时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="buyer in items" :key="buyer.id"><td><RouterLink class="buyer-name-link" :to="`/admin/buyers/${buyer.id}`">{{ buyer.nickname || buyer.username }}<small>{{ buyer.username }}</small></RouterLink></td><td>{{ buyer.phone }}</td><td class="nowrap muted">{{ formatDate(buyer.createdAt) }}</td><td><span class="status-pill" :class="{ 'status-off': buyer.status === 0 }">{{ buyer.status ? '启用' : '禁用' }}</span></td><td><div class="table-actions"><RouterLink class="text-action" :to="`/admin/buyers/${buyer.id}`">详情</RouterLink><button class="text-action" :disabled="busyId === buyer.id" @click="toggleStatus(buyer)">{{ buyer.status ? '禁用' : '启用' }}</button><button class="text-action" :disabled="busyId === buyer.id" @click="openReset(buyer)">重置密码</button></div></td></tr></tbody></table></div>
    <PagePagination :total="total" :page="page" :busy="loading" @change="changePage" />
    <div v-if="resetBuyer" class="dialog-backdrop" @click.self="closeReset"><section class="buyer-dialog" role="dialog" aria-modal="true" aria-labelledby="reset-title"><div class="catalog-heading"><h2 id="reset-title">重置买家密码</h2><button class="icon-button" aria-label="关闭" :disabled="resetBusy" @click="closeReset">×</button></div><p class="muted">为 {{ resetBuyer.nickname || resetBuyer.username }} 设置临时密码。</p><form class="auth-form" @submit.prevent="submitReset"><label class="form-field">临时密码<input v-model="temporaryPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required></label><label class="form-field">确认临时密码<input v-model="confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required></label><p v-if="resetError" class="form-message-error" role="alert">{{ resetError }}</p><div class="form-actions"><button class="primary-button" :disabled="resetBusy">{{ resetBusy ? '提交中…' : '确认重置' }}</button><button type="button" class="secondary-button" :disabled="resetBusy" @click="closeReset">取消</button></div></form></section></div>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageStatus from '../components/PageStatus.vue'
import PagePagination from '../components/PagePagination.vue'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { adminBuyers, resetBuyerPassword, setBuyerStatus, type BuyerAccount } from '../api/admin-buyers'
import { errorMessage } from '../api/catalog'
const route = useRoute(), router = useRouter()
const items = ref<BuyerAccount[]>([]), keyword = ref(''), statusFilter = ref(''), page = ref(1), total = ref(0)
const loading = ref(false), error = ref(''), actionError = ref(''), message = ref(''), busyId = ref<number | null>(null)
const resetBuyer = ref<BuyerAccount | null>(null), temporaryPassword = ref(''), confirmPassword = ref(''), resetBusy = ref(false), resetError = ref('')
let generation = 0
async function load() { const own = ++generation; loading.value = true; error.value = ''; keyword.value = String(route.query.keyword || ''); statusFilter.value = String(route.query.status ?? ''); page.value = Number(route.query.page || 1); try { const result = await adminBuyers({ keyword: keyword.value || undefined, status: statusFilter.value === '' ? undefined : Number(statusFilter.value), page: page.value }); if (own !== generation) return; items.value = result.items; total.value = result.total } catch (e) { if (own === generation) error.value = errorMessage(e) } finally { if (own === generation) loading.value = false } }
function search() { message.value = ''; actionError.value = ''; void router.push({ query: { ...(keyword.value ? { keyword: keyword.value } : {}), ...(statusFilter.value !== '' ? { status: statusFilter.value } : {}) } }) }
function reset() { keyword.value = ''; statusFilter.value = ''; search() }
function changePage(next: number) { void router.push({ query: { ...route.query, page: next } }) }
async function toggleStatus(buyer: BuyerAccount) { try { await ElMessageBox.confirm(buyer.status ? `禁用“${buyer.nickname || buyer.username}”后，该买家将无法登录，现有令牌立即失效。` : `确认启用“${buyer.nickname || buyer.username}”的账户？`, buyer.status ? '禁用买家账户' : '启用买家账户', { confirmButtonText: buyer.status ? '禁用' : '启用', cancelButtonText: '取消', type: buyer.status ? 'warning' : 'info' }) } catch { return } busyId.value = buyer.id; actionError.value = ''; message.value = ''; try { await setBuyerStatus(buyer.id, buyer.status ? 0 : 1); message.value = buyer.status ? '买家账户已禁用，现有登录令牌已失效。' : '买家账户已启用。'; await load() } catch (e) { actionError.value = errorMessage(e) } finally { busyId.value = null } }
function openReset(buyer: BuyerAccount) { resetBuyer.value = buyer; temporaryPassword.value = ''; confirmPassword.value = ''; resetError.value = '' }
function closeReset() { if (resetBusy.value) return; resetBuyer.value = null; temporaryPassword.value = ''; confirmPassword.value = '' }
async function submitReset() { if (!resetBuyer.value) return; if (temporaryPassword.value !== confirmPassword.value) { resetError.value = '两次输入的密码不一致'; return } if (new TextEncoder().encode(temporaryPassword.value).length > 72) { resetError.value = '密码 UTF-8 编码不能超过 72 字节'; return } resetBusy.value = true; resetError.value = ''; try { await resetBuyerPassword(resetBuyer.value.id, temporaryPassword.value); message.value = '临时密码已重置，该买家的现有登录令牌已失效。'; resetBuyer.value = null; temporaryPassword.value = ''; confirmPassword.value = '' } catch (e) { resetError.value = errorMessage(e) } finally { resetBusy.value = false } }
function formatDate(value: string) { return value?.replace('T', ' ').slice(0, 16) ?? '—' }
watch(() => route.fullPath, load, { immediate: true })
</script>
