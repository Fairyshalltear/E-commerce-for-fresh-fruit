<template>
  <div><div class="catalog-heading"><div><p class="eyebrow">商品运营</p><h1>商品管理</h1><p class="muted">维护水果信息、规格和库存。</p></div><RouterLink class="primary-button" to="/admin/products/new">＋ 新增商品</RouterLink></div>
    <form class="admin-filters" @submit.prevent="submit"><label class="form-field">商品名称<input v-model="keyword" placeholder="搜索商品" maxlength="100"></label><label class="form-field">分类<select v-model="categoryId"><option value="">全部分类</option><optgroup v-for="root in tree" :key="root.id" :label="root.name"><option :value="String(root.id)">{{ root.name }}（全部）</option><option v-for="child in root.children" :key="child.id" :value="String(child.id)">{{ child.name }}</option></optgroup></select></label><label class="form-field">上架状态<select v-model="status"><option value="">全部状态</option><option value="1">已上架</option><option value="0">已下架</option></select></label><label class="form-field">推荐<select v-model="recommended"><option value="">全部商品</option><option value="true">已推荐</option><option value="false">未推荐</option></select></label><button class="primary-button">查询</button><button type="button" class="text-action" @click="reset">重置</button></form>
    <p v-if="actionSuccess" class="product-status-success" role="status">{{ actionSuccess }}</p>
    <p v-if="actionError" class="form-message-error" role="alert">{{ actionError }}</p><PageStatus :loading="loading" :error="error" :empty="!items.length" empty-text="还没有商品，点击“新增商品”开始创建。" @retry="load" />
    <div v-if="!loading && !error && items.length" class="table-scroll"><table class="admin-table"><thead><tr><th>商品</th><th>分类</th><th>价格范围</th><th>可售库存</th><th>状态 / 推荐</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="row in items" :key="row.product.id"><td><div class="table-product"><img v-if="row.product.mainImage" :src="imageUrl(row.product.mainImage)" :alt="row.product.name"><span>{{ row.product.name }}</span></div></td><td>{{ row.product.categoryName }}</td><td class="nowrap">¥{{ price(row.product.minPrice) }}<template v-if="row.product.minPrice !== row.product.maxPrice"> ～ {{ price(row.product.maxPrice) }}</template></td><td>{{ row.product.availableStock }}<small v-if="row.product.soldOut" class="muted"> · 售罄</small></td><td><span class="status-pill" :class="{ 'status-off': row.status === 0 }">{{ row.status ? '已上架' : '已下架' }}</span><span v-if="row.recommended" class="recommended-label">推荐</span></td><td class="muted nowrap">{{ row.updatedAt?.replace('T', ' ').slice(0,16) }}</td><td><div class="table-actions"><RouterLink class="text-action" :to="'/admin/products/' + row.product.id + '/edit'">编辑</RouterLink><button class="text-action" :disabled="actionBusy || statusOpen" @click="toggle(row, $event)">{{ row.status ? '下架' : '上架' }}</button><button class="text-action danger-text" :disabled="actionBusy || statusOpen" @click="remove(row)">删除</button></div></td></tr></tbody></table></div>
    <ProductStatusDialog :open="statusOpen" :product="statusProduct" :target-status="targetStatus" :busy="actionBusy || statusReading" :reading="statusReading" :error="statusError" :reload-required="reloadRequired" :confirm-disabled="confirmDisabled" @cancel="closeStatus" @confirm="confirmStatus" @reload="reloadStatus" @closed="restoreFocus" />
    <PagePagination :total="total" :page="page" :busy="loading" @change="changePage" />
  </div>
</template>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import axios from 'axios'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import ProductStatusDialog from '../components/ProductStatusDialog.vue'
import PageStatus from '../components/PageStatus.vue'
import PagePagination from '../components/PagePagination.vue'
import { adminProducts, adminCategories, adminProduct, changeStatus, deleteProduct, type AdminCard } from '../api/admin-catalog'
import { price, imageUrl, errorMessage, type Category } from '../api/catalog'
const route = useRoute(), router = useRouter(), items = ref<AdminCard[]>([]), tree = ref<Category[]>([])
const keyword = ref(''), categoryId = ref(''), status = ref(''), recommended = ref(''), page = ref(1), total = ref(0), loading = ref(false), error = ref(''), actionError = ref(''), actionBusy = ref(false)
const text = (value: unknown) => typeof value === 'string' ? value : ''
let generation = 0
async function load() { const own = ++generation; loading.value = true; error.value = ''; keyword.value = text(route.query.keyword); categoryId.value = text(route.query.categoryId); status.value = text(route.query.status); recommended.value = text(route.query.recommended); page.value = Number(text(route.query.page) || 1); try { const [cats, data] = await Promise.all([adminCategories(), adminProducts({ keyword: keyword.value || undefined, categoryId: categoryId.value ? Number(categoryId.value) : undefined, status: status.value ? Number(status.value) : undefined, recommended: recommended.value ? recommended.value === 'true' : undefined, page: page.value })]); if (own !== generation) return; tree.value = cats; items.value = data.items; total.value = data.total; if (!items.value.length && page.value > 1 && total.value < (page.value - 1) * 12 + 1) await router.replace({ query: { ...route.query, page: Math.max(1, Math.ceil(total.value / 12)) } }) } catch (e) { if (own === generation) error.value = errorMessage(e) } finally { if (own === generation) loading.value = false } }
function submit() { actionSuccess.value = ''; actionError.value = ''; void router.push({ query: { ...(keyword.value ? { keyword: keyword.value } : {}), ...(categoryId.value ? { categoryId: categoryId.value } : {}), ...(status.value ? { status: status.value } : {}), ...(recommended.value ? { recommended: recommended.value } : {}) } }).then(() => { if (!loading.value) void load() }) }
function reset() { keyword.value = ''; categoryId.value = ''; status.value = ''; recommended.value = ''; submit() }
function changePage(next: number) { void router.push({ query: { ...route.query, page: next } }) }
const actionSuccess = ref('')
const statusOpen = ref(false), statusProduct = ref<AdminCard | null>(null), targetStatus = ref<0 | 1>(0)
const statusReading = ref(false), statusError = ref(''), reloadRequired = ref(false), confirmDisabled = ref(false)
let trigger: HTMLElement | null = null
let statusGeneration = 0
function toggle(row: AdminCard, event: MouseEvent) {
  if (actionBusy.value || statusOpen.value) return
  trigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  statusProduct.value = { ...row, product: { ...row.product } }
  targetStatus.value = row.status === 1 ? 0 : 1
  statusError.value = ''; actionError.value = ''; actionSuccess.value = ''
  reloadRequired.value = false; confirmDisabled.value = false
  statusGeneration++
  statusOpen.value = true
}
function closeStatus() {
  if (actionBusy.value || statusReading.value) return
  statusGeneration++
  statusOpen.value = false
}
async function restoreFocus() {
  await nextTick()
  if (statusOpen.value) return
  const destination = trigger?.isConnected ? trigger : document.querySelector<HTMLElement>('.admin-filters .primary-button')
  destination?.focus()
}
function statusFailure(error: unknown) {
  statusError.value = errorMessage(error)
  if (!axios.isAxiosError<{ code?: string }>(error)) {
    statusError.value = '请求结果未确认，请重新读取商品状态后再操作。'
    reloadRequired.value = true
  } else if (!error.response || error.response.status >= 500) {
    statusError.value = '请求结果未确认，请重新读取商品状态后再操作。'
    reloadRequired.value = true
  } else if (error.response.data?.code === 'VERSION_CONFLICT') {
    reloadRequired.value = true
  } else if (error.response.status === 404) {
    statusError.value = '商品已删除或不可用，请取消后刷新列表。'
    confirmDisabled.value = true
  }
}
async function statusSucceeded(message: string) {
  statusOpen.value = false
  actionSuccess.value = message
  await load()
  await restoreFocus()
}
async function confirmStatus() {
  const row = statusProduct.value
  if (!row || !statusOpen.value || actionBusy.value || statusReading.value || reloadRequired.value || confirmDisabled.value) return
  const own = statusGeneration
  actionBusy.value = true; statusError.value = ''
  try {
    await changeStatus(row.product.id, targetStatus.value, row.version)
    if (own !== statusGeneration) return
    await statusSucceeded(targetStatus.value === 1 ? '商品已上架' : '商品已下架')
  } catch (error) { if (own === statusGeneration) statusFailure(error) }
  finally { if (own === statusGeneration) { actionBusy.value = false; if (!statusOpen.value) void restoreFocus() } }
}
async function reloadStatus() {
  const row = statusProduct.value
  if (!row || !statusOpen.value || actionBusy.value || statusReading.value) return
  const own = statusGeneration
  statusReading.value = true; statusError.value = ''
  try {
    const [latest, categories] = await Promise.all([adminProduct(row.product.id), adminCategories()])
    if (own !== statusGeneration) return
    tree.value = categories
    const category = categories.flatMap(root => [root, ...root.children]).find(item => item.id === latest.categoryId)
    statusProduct.value = { ...row, status: latest.status, version: latest.version, product: { ...row.product, name: latest.name, categoryId: latest.categoryId, categoryName: category?.name ?? '分类不可用', mainImage: latest.images[0]?.url ?? null } }
    if (latest.status === targetStatus.value) await statusSucceeded(targetStatus.value === 1 ? '商品已是上架状态，列表已更新' : '商品已是下架状态，列表已更新')
    else { reloadRequired.value = false; confirmDisabled.value = false }
  } catch (error) {
    if (own !== statusGeneration) return
    statusError.value = axios.isAxiosError(error) && error.response?.status === 404 ? '商品已删除或不可用，请取消后刷新列表。' : '无法重新读取商品信息，请重试或取消。'
    reloadRequired.value = true; confirmDisabled.value = true
  } finally { if (own === statusGeneration) { statusReading.value = false; if (!statusOpen.value) void restoreFocus() } }
}
onBeforeUnmount(() => { statusGeneration++; generation++ })
async function remove(row: AdminCard) { if (actionBusy.value || statusOpen.value) return; actionSuccess.value = ''; try { await ElMessageBox.confirm('删除“' + row.product.name + '”后将从管理列表和商城移除，相关历史数据保留。', '删除商品', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }) } catch { return } actionBusy.value = true; actionError.value = ''; try { await deleteProduct(row.product.id, row.version); await load() } catch (e) { actionError.value = errorMessage(e) } finally { actionBusy.value = false } }
watch(() => route.fullPath, load, { immediate: true })
</script>
