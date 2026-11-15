<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import PageStatus from '../components/PageStatus.vue'
import PagePagination from '../components/PagePagination.vue'
import { adminCategories } from '../api/admin-catalog'
import { errorMessage, type Category } from '../api/catalog'
import { inventorySummary, listStocks, listDocuments, listMovements, setThreshold, notifyInventoryChanged, movementLabels, inventoryDate, sourcePath, type Stock, type Summary, type Page, type StockDocument, type Movement, type DocumentType, type StockQuery } from '../api/inventory'
const route = useRoute()
const section = computed(() => String(route.params.section || 'overview'))
const tabs = [{ id: 'overview', title: '库存总览' }, { id: 'inbound', title: '入库管理' }, { id: 'outbound', title: '出库管理' }, { id: 'alerts', title: '库存预警' }, { id: 'stocktakes', title: '盘点记录' }, { id: 'movements', title: '库存流水' }]
const title = computed(() => tabs.find(t => t.id === section.value)?.title || '库存总览')
const stockMode = computed(() => ['overview', 'alerts'].includes(section.value))
const subtype = ref('PURCHASE_IN')
const documentMode = computed(() => section.value === 'stocktakes' || (section.value === 'inbound' && subtype.value === 'PURCHASE_IN') || (section.value === 'outbound' && subtype.value === 'SUPPLIER_RETURN_OUT'))
const newType = computed<DocumentType>(() => section.value === 'stocktakes' ? 'STOCKTAKE' : section.value === 'outbound' ? 'SUPPLIER_RETURN_OUT' : 'PURCHASE_IN')
const query = reactive({ keyword: '', categoryId: '', status: '', skuStatus: '', deleted: '0', stockState: '', documentStatus: '', direction: '', type: '', sourceNo: '', from: '', to: '' })
const page = ref(1), loading = ref(false), error = ref(''), actionError = ref(''), busy = ref(false), categories = ref<Category[]>([]), categoryError = ref('')
const stocks = ref<Page<Stock> | null>(null), documents = ref<Page<StockDocument> | null>(null), movements = ref<Page<Movement> | null>(null), summary = ref<Summary | null>(null)
const total = computed(() => stockMode.value ? stocks.value?.total ?? 0 : documentMode.value ? documents.value?.total ?? 0 : movements.value?.total ?? 0)
let sequence = 0, disposed = false, timer: ReturnType<typeof setInterval> | undefined
function params(): StockQuery { return { keyword: query.keyword || undefined, categoryId: query.categoryId ? Number(query.categoryId) : undefined, status: query.status ? Number(query.status) : undefined, skuStatus: query.skuStatus ? Number(query.skuStatus) : undefined, deleted: Number(query.deleted), stockState: section.value === 'alerts' ? 'LOW' : query.stockState || undefined, page: page.value, size: 20 } }
async function loadCategories() { try { categories.value = await adminCategories(); categoryError.value = '' } catch (e) { categoryError.value = errorMessage(e) } }
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  try {
    if (stockMode.value) { const [rows, sums] = await Promise.all([listStocks(params()), inventorySummary(params())]); if (current === sequence && !disposed) { stocks.value = rows; summary.value = sums } }
    else if (documentMode.value) { const rows = await listDocuments({ type: newType.value, status: query.documentStatus || undefined, keyword: query.keyword || undefined, from: query.from || undefined, to: query.to || undefined, page: page.value, size: 20 }); if (current === sequence && !disposed) documents.value = rows }
    else { const rows = await listMovements({ keyword: query.keyword || undefined, skuId: route.query.skuId ? Number(route.query.skuId) : undefined, type: ['inbound', 'outbound'].includes(section.value) ? subtype.value : query.type || undefined, direction: query.direction || undefined, sourceNo: query.sourceNo || undefined, from: query.from || undefined, to: query.to || undefined, page: page.value, size: 20 }); if (current === sequence && !disposed) movements.value = rows }
  } catch (e) { if (current === sequence && !disposed) error.value = errorMessage(e) } finally { if (current === sequence && !disposed) loading.value = false }
}
function search() { page.value = 1; void load() }
function paginate(p: number) { page.value = p; void load() }
async function threshold(s: Stock) {
  actionError.value = ''
  let value: string
  try { const result = await ElMessageBox.prompt('可售库存低于阈值时提醒；0 表示关闭提醒。', '设置低库存阈值', { inputValue: String(s.lowStockThreshold), inputPattern: /^\d+$/, inputErrorMessage: '请输入非负整数', confirmButtonText: '保存', cancelButtonText: '取消' }); value = result.value } catch { return }
  const n = Number(value); if (!Number.isInteger(n) || n > 2147483647) { actionError.value = '阈值超出支持范围'; return }
  busy.value = true; try { await setThreshold(s.skuId, n, s.inventoryVersion); notifyInventoryChanged(); await load() } catch (e) { actionError.value = errorMessage(e) } finally { busy.value = false }
}
function background() { if (!document.hidden && section.value === 'alerts' && !loading.value) void load() }
watch(() => [section.value, route.query.skuId], () => { page.value = 1; query.keyword = ''; query.stockState = ''; query.type = ''; query.sourceNo = ''; query.from = ''; query.to = ''; query.documentStatus = ''; subtype.value = section.value === 'outbound' ? 'SUPPLIER_RETURN_OUT' : 'PURCHASE_IN'; actionError.value = ''; stocks.value = null; documents.value = null; movements.value = null; summary.value = null; void load() }, { immediate: true })
onMounted(() => { void loadCategories(); timer = setInterval(background, 60000); document.addEventListener('visibilitychange', background); window.addEventListener('fresh-fruit-inventory-changed', background) })
onUnmounted(() => { disposed = true; sequence++; clearInterval(timer); document.removeEventListener('visibilitychange', background); window.removeEventListener('fresh-fruit-inventory-changed', background) })
</script>
<template>
  <div class="inventory-page">
    <header class="catalog-heading"><div><p class="eyebrow">仓储管理</p><h1>{{ title }}</h1><p class="field-hint">按商品规格管理货物，可售库存 = 实物库存 − 订单预留。</p></div><RouterLink v-if="documentMode" class="primary-button" :to="'/admin/inventory/documents/new?type=' + newType">＋ {{ movementLabels[newType] }}</RouterLink></header>
    <nav class="inventory-tabs" aria-label="库存功能"><RouterLink v-for="tab in tabs" :key="tab.id" :class="{ active: section === tab.id }" :to="tab.id === 'overview' ? '/admin/inventory' : '/admin/inventory/' + tab.id">{{ tab.title }}</RouterLink></nav>
    <div v-if="stockMode && summary && !error" class="inventory-summary"><article><span>规格数</span><strong>{{ summary.skuCount }}</strong></article><article><span>实物件数</span><strong>{{ summary.onHand }}</strong></article><article><span>订单预留</span><strong>{{ summary.reserved }}</strong></article><article><span>可售件数</span><strong>{{ summary.availableStock }}</strong></article><article><span>低库存规格</span><strong class="danger-text">{{ summary.lowStockCount }}</strong></article></div>
    <div v-if="['inbound', 'outbound'].includes(section)" class="inventory-subtypes"><button v-for="type in (section === 'inbound' ? ['PURCHASE_IN', 'RETURN_RESTOCK'] : ['SUPPLIER_RETURN_OUT', 'ORDER_DISPATCH'])" :key="type" class="secondary-button" :class="{ selected: subtype === type }" @click="subtype = type; search()">{{ movementLabels[type] }}</button><p class="field-hint">{{ section === 'inbound' ? '客户退货由售后验收产生，仅可重新销售部分入库。' : '销售出库由订单发货自动产生；退供表示退给供应商。' }}</p></div>
    <p v-if="section === 'alerts'" class="inventory-explanation">可售库存低于正阈值时提醒，恢复到阈值即解除。阈值 0 关闭提醒；页面可见时每 60 秒刷新。</p>
    <p v-if="section === 'stocktakes'" class="inventory-explanation">先保存实盘记录，复核后确认调整。盘点期间库存发生变化时，需要刷新账面数据再复核。</p>
    <form class="inventory-filters" @submit.prevent="search">
      <label>搜索<input v-model.trim="query.keyword" maxlength="100" :placeholder="documentMode ? '单号 / 供应商 / 参考采购号' : '商品名称 / 规格'"></label>
      <template v-if="stockMode"><label>分类<select v-model="query.categoryId"><option value="">全部分类</option><template v-for="c in categories" :key="c.id"><option :value="String(c.id)">{{ c.name }}</option><option v-for="child in c.children" :key="child.id" :value="String(child.id)">　{{ child.name }}</option></template></select></label><label>商品状态<select v-model="query.status"><option value="">全部</option><option value="1">上架</option><option value="0">下架</option></select></label><label>规格状态<select v-model="query.skuStatus"><option value="">全部</option><option value="1">启用</option><option value="0">停用</option></select></label><label v-if="section !== 'alerts'">库存状态<select v-model="query.stockState"><option value="">全部</option><option value="NORMAL">正常</option><option value="LOW">低库存</option><option value="ZERO">可售为零</option></select></label><label v-if="section !== 'alerts'">数据范围<select v-model="query.deleted"><option value="0">未删除商品</option><option value="1">已删除商品库存</option></select></label></template>
      <template v-else><label v-if="documentMode">单据状态<select v-model="query.documentStatus"><option value="">全部</option><option value="DRAFT">草稿</option><option value="POSTED">已生效</option></select></label><template v-if="!documentMode"><label>来源单号<input v-model.trim="query.sourceNo" maxlength="100" placeholder="订单 / 售后 / 库存单号"></label><label v-if="section === 'movements'">流水类型<select v-model="query.type"><option value="">全部</option><option v-for="[type, label] in Object.entries(movementLabels).filter(([type]) => type !== 'STOCKTAKE')" :key="type" :value="type">{{ label }}</option></select></label><label v-if="section === 'movements'">方向<select v-model="query.direction"><option value="">全部</option><option value="IN">增加</option><option value="OUT">减少</option></select></label></template><label>开始时间<input v-model="query.from" type="datetime-local"></label><label>结束时间<input v-model="query.to" type="datetime-local"></label></template>
      <button class="primary-button" :disabled="loading">查询</button>
    </form>
    <p v-if="categoryError" class="form-message-error">分类加载失败：{{ categoryError }} <button class="text-action" @click="loadCategories">重试</button></p>
    <p v-if="actionError" class="form-message-error" role="alert">{{ actionError }}</p>
    <PageStatus :loading="loading" :error="error" :empty="!loading && !error && total === 0" empty-text="暂无匹配记录" @retry="load" />
    <div v-if="!loading && !error && total" class="table-scroll inventory-table">
      <table v-if="stockMode" class="admin-table"><thead><tr><th>商品 / 规格</th><th>分类 / 状态</th><th>实物</th><th>预留</th><th>可售</th><th>预警阈值</th><th>操作</th></tr></thead><tbody><tr v-for="s in stocks?.items" :key="s.skuId"><td><strong>{{ s.productName }}</strong><small>{{ s.specification }}</small></td><td>{{ s.categoryName }}<small>{{ s.deleted ? '商品已删除' : s.productStatus ? '上架' : '下架' }} · {{ s.skuStatus ? '规格启用' : '规格停用' }}</small></td><td>{{ s.onHand }}</td><td>{{ s.reserved }}</td><td><strong :class="{ 'danger-text': s.lowStock || s.availableStock === 0 }">{{ s.availableStock }}</strong><small v-if="s.lowStock" class="danger-text">低库存</small></td><td>{{ s.lowStockThreshold || '未开启' }}</td><td><div class="table-actions"><button class="text-action" :disabled="busy" @click="threshold(s)">设置阈值</button><RouterLink class="text-action" :to="'/admin/inventory/movements?skuId=' + s.skuId">查看流水</RouterLink></div></td></tr></tbody></table>
      <table v-else-if="documentMode" class="admin-table"><thead><tr><th>单号</th><th>供应商 / 原因</th><th>规格数</th><th>状态</th><th>创建人 / 时间</th><th>操作</th></tr></thead><tbody><tr v-for="d in documents?.items" :key="d.id"><td class="inventory-number">{{ d.documentNo }}<small>{{ d.referenceNo }}</small></td><td>{{ d.supplier || '—' }}<small>{{ d.reason }}</small></td><td>{{ d.items.length }}</td><td><span class="inventory-badge" :class="{ posted: d.status === 'POSTED' }">{{ d.status === 'DRAFT' ? '草稿' : '已生效' }}</span></td><td>{{ d.creatorName }}<small>{{ inventoryDate(d.createdAt) }}</small></td><td><RouterLink class="text-action" :to="'/admin/inventory/documents/' + d.id">{{ d.status === 'DRAFT' ? '编辑 / 确认' : '查看详情' }}</RouterLink></td></tr></tbody></table>
      <table v-else class="admin-table"><thead><tr><th>商品 / 规格</th><th>类型</th><th>变动</th><th>实物前 → 后</th><th>来源 / 原因</th><th>操作人 / 时间</th></tr></thead><tbody><tr v-for="m in movements?.items" :key="m.id"><td>{{ m.productName }}<small>{{ m.specification }}</small></td><td>{{ movementLabels[m.type] || m.type }}</td><td><strong :class="m.quantityDelta > 0 ? 'inventory-positive' : 'danger-text'">{{ m.quantityDelta > 0 ? '+' : '' }}{{ m.quantityDelta }}</strong></td><td>{{ m.beforeOnHand }} → {{ m.afterOnHand }}</td><td class="inventory-number"><RouterLink v-if="sourcePath(m)" class="text-action" :to="sourcePath(m)">{{ m.sourceNo || '查看来源' }}</RouterLink><span v-else>{{ m.sourceNo || '历史记录' }}</span><small>{{ m.reason }}</small></td><td>{{ m.adminName }}<small>{{ inventoryDate(m.createdAt) }}</small></td></tr></tbody></table>
    </div>
    <PagePagination v-if="!error" :total="total" :page="page" :size="20" :busy="loading" @change="paginate" />
  </div>
</template>
