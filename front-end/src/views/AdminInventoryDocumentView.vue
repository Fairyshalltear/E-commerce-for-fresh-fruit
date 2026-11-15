<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import PageStatus from '../components/PageStatus.vue'
import PagePagination from '../components/PagePagination.vue'
import { errorMessage } from '../api/catalog'
import { deleteDocument, getDocument, inventoryDate, movementLabels, notifyInventoryChanged, postDocument, refreshSnapshot, saveDocument, skuOptions, type Stock, type StockDocument, type DocumentType, type DocumentItem, type Page } from '../api/inventory'
const route = useRoute(), router = useRouter(), document = ref<StockDocument | null>(null)
const isNew = computed(() => route.params.documentId === 'new')
const type = ref<DocumentType>('PURCHASE_IN')
const isCount = computed(() => type.value === 'STOCKTAKE'), posted = computed(() => document.value?.status === 'POSTED')
const form = reactive({ supplier: '', referenceNo: '', reason: '' })
const rows = ref<DocumentItem[]>([]), loading = ref(false), error = ref(''), actionError = ref(''), busy = ref(false), notice = ref(''), conflict = ref(false)
const searchText = ref(''), options = ref<Page<Stock> | null>(null), optionPage = ref(1), optionLoading = ref(false), optionError = ref('')
let baseline = '', sequence = 0
const signature = () => JSON.stringify({ ...form, type: type.value, items: rows.value.map(r => ({ skuId: r.skuId, quantity: r.quantity, countedQuantity: r.countedQuantity })) })
const dirty = computed(() => signature() !== baseline)
const back = computed(() => type.value === 'STOCKTAKE' ? '/admin/inventory/stocktakes' : type.value === 'SUPPLIER_RETURN_OUT' ? '/admin/inventory/outbound' : '/admin/inventory/inbound')
function adopt(d: StockDocument) { document.value = d; type.value = d.type; Object.assign(form, { supplier: d.supplier, referenceNo: d.referenceNo, reason: d.reason }); rows.value = d.items; baseline = signature() }
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''; actionError.value = ''; notice.value = ''; conflict.value = false
  try {
    if (isNew.value) { const requested = String(route.query.type || 'PURCHASE_IN'); type.value = ['PURCHASE_IN', 'SUPPLIER_RETURN_OUT', 'STOCKTAKE'].includes(requested) ? requested as DocumentType : 'PURCHASE_IN'; document.value = null; Object.assign(form, { supplier: '', referenceNo: '', reason: '' }); rows.value = []; baseline = signature() }
    else { const id = Number(route.params.documentId); if (!Number.isSafeInteger(id) || id <= 0) throw new Error('单据编号无效'); const d = await getDocument(id); if (current === sequence) adopt(d) }
    if (current === sequence && !posted.value) { options.value = null; optionPage.value = 1; await searchOptions() }
  } catch (e) { if (current === sequence) error.value = errorMessage(e) } finally { if (current === sequence) loading.value = false }
}
let optionSequence = 0
async function searchOptions(p = 1) { const current = ++optionSequence; optionPage.value = p; optionLoading.value = true; optionError.value = ''; try { const result = await skuOptions({ keyword: searchText.value || undefined, type: type.value, page: p, size: 10 }); if (current === optionSequence) options.value = result } catch (e) { if (current === optionSequence) optionError.value = errorMessage(e) } finally { if (current === optionSequence) optionLoading.value = false } }
function add(s: Stock) { if (rows.value.some(r => r.skuId === s.skuId) || rows.value.length >= 100) return; rows.value.push({ id: 0, skuId: s.skuId, productName: s.productName, specification: s.specification, quantity: isCount.value ? null : 1, countedQuantity: isCount.value ? s.onHand : null, beforeOnHand: null, beforeReserved: null, beforeAvailable: null, inventoryVersion: null, afterOnHand: null, quantityDelta: null, currentStock: s }) }
function delta(r: DocumentItem) { return isCount.value ? Number(r.countedQuantity) - (r.beforeOnHand ?? r.currentStock.onHand) : (type.value === 'PURCHASE_IN' ? 1 : -1) * Number(r.quantity) }
function validate() { if (!rows.value.length) return '请至少添加一个规格'; if (!isCount.value && !form.supplier.trim()) return '请填写供应商名称'; if (type.value !== 'PURCHASE_IN' && !form.reason.trim()) return '请填写退供或盘点原因'; for (const r of rows.value) { const n = isCount.value ? r.countedQuantity : r.quantity; if (n === null || !Number.isInteger(n) || n < (isCount.value ? 0 : 1) || n > 2147483647) return '数量必须为支持范围内的' + (isCount.value ? '非负' : '正') + '整数' } return '' }
async function save() {
  actionError.value = validate(); if (actionError.value) return; busy.value = true; notice.value = ''
  try { const d = await saveDocument(isNew.value ? null : document.value!.id, { ...form, type: type.value, version: document.value?.version, items: rows.value.map(r => isCount.value ? { skuId: r.skuId, countedQuantity: r.countedQuantity! } : { skuId: r.skuId, quantity: r.quantity! }) }); adopt(d); notice.value = '草稿已保存，库存尚未变更'; if (isNew.value) await router.replace('/admin/inventory/documents/' + d.id) } catch (e) { actionError.value = errorMessage(e) } finally { busy.value = false }
}
async function confirmPost() {
  if (!document.value || dirty.value || busy.value) return
  const description = rows.value.map(r => `${r.productName} / ${r.specification}：${delta(r) >= 0 ? '+' : ''}${delta(r)}`).join('\n')
  try { await ElMessageBox.confirm(`${isCount.value ? '' : '供应商：' + form.supplier + '\n参考采购号：' + (form.referenceNo || '未填写') + '\n'}${description}\n确认后整张单据立即生效，已生效记录不可直接修改。`, '确认' + movementLabels[type.value], { confirmButtonText: '确认生效', cancelButtonText: '继续复核', customClass: 'inventory-confirm' }) } catch { return }
  busy.value = true; actionError.value = ''; notice.value = ''
  try { adopt(await postDocument(document.value.id, document.value.version)); conflict.value = false; notice.value = '单据已生效，库存与流水已更新'; notifyInventoryChanged() } catch (e) { actionError.value = errorMessage(e); if (isCount.value) { conflict.value = true; try { const latest = await getDocument(document.value.id); for (const row of rows.value) { const now = latest.items.find(i => i.skuId === row.skuId); if (now) row.currentStock = now.currentStock } } catch { /* Keep saved inputs when the latest stock cannot be read. */ } } } finally { busy.value = false }
}
async function refresh() {
  if (!document.value || dirty.value || busy.value) return
  try { await ElMessageBox.confirm('保留已保存的实盘数量，读取最新账面库存。请重新核对差异，再确认生效。', '刷新账面数据并复核', { confirmButtonText: '刷新并复核', cancelButtonText: '保留原快照' }) } catch { return }
  busy.value = true; actionError.value = ''; try { adopt(await refreshSnapshot(document.value.id, document.value.version)); conflict.value = false; notice.value = '账面数据已刷新，实盘数量保留，请重新核对差异后确认' } catch (e) { actionError.value = errorMessage(e) } finally { busy.value = false }
}
async function removeDraft() {
  if (!document.value || busy.value) return
  try { await ElMessageBox.confirm('删除草稿不会影响库存，确认删除？', '删除草稿', { confirmButtonText: '删除', cancelButtonText: '保留' }) } catch { return }
  busy.value = true; try { await deleteDocument(document.value.id, document.value.version); baseline = signature(); await router.push(back.value) } catch (e) { actionError.value = errorMessage(e) } finally { busy.value = false }
}
onBeforeRouteLeave(async () => { if (!dirty.value || posted.value) return true; try { await ElMessageBox.confirm('离开将丢弃未保存的输入。', '保留当前草稿？', { confirmButtonText: '离开', cancelButtonText: '继续编辑' }); return true } catch { return false } })
watch(() => route.params.documentId, load, { immediate: true })
</script>
<template>
  <div class="inventory-page"><nav class="breadcrumbs"><RouterLink :to="back">{{ movementLabels[type] }}</RouterLink><span>/</span><span>{{ isNew ? '新建单据' : posted ? '单据详情' : '编辑草稿' }}</span></nav>
    <header class="catalog-heading"><div><p class="eyebrow">仓储作业</p><h1>{{ movementLabels[type] }}</h1><p class="inventory-number field-hint">{{ document?.documentNo || '添加货物明细，保存草稿后再确认生效' }}</p></div><span v-if="document" class="inventory-badge" :class="{ posted }">{{ posted ? '已生效' : '草稿' }}</span></header>
    <PageStatus :loading="loading" :error="error" @retry="load" />
    <template v-if="!loading && !error"><p class="inventory-explanation">{{ isCount ? '实盘与账面差异在确认后调整；实物不得低于订单预留量。' : type === 'PURCHASE_IN' ? '货物到达并验收后再确认入库。保存草稿不增加库存。' : '退给供应商的货物记为退供出库；不能占用客户订单预留。' }}</p>
      <form @submit.prevent="save"><fieldset :disabled="posted || busy" class="inventory-fieldset"><div class="inventory-filters"><label v-if="!isCount">供应商名称<input v-model.trim="form.supplier" required maxlength="100" placeholder="例如：果园合作社"></label><label v-if="!isCount">参考采购单号（选填）<input v-model.trim="form.referenceNo" maxlength="100" placeholder="仅用于人工追溯"></label><label class="inventory-reason">{{ type === 'PURCHASE_IN' ? '原因 / 备注（选填）' : '原因' }}<input v-model.trim="form.reason" :required="type !== 'PURCHASE_IN'" maxlength="200" placeholder="例如：到货验收、质量问题、日常核对"></label></div></fieldset>
        <section v-if="!posted" class="inventory-picker"><h2>添加货物规格 <small>已选 {{ rows.length }} / 100</small></h2><div class="inventory-search"><input v-model.trim="searchText" aria-label="搜索货物规格" maxlength="100" placeholder="搜索商品名称或规格" @keydown.enter.prevent="searchOptions()"><button class="secondary-button" type="button" :disabled="optionLoading || busy" @click="searchOptions()">搜索规格</button></div><PageStatus :loading="optionLoading" :error="optionError" :empty="!optionLoading && !optionError && options?.total === 0" empty-text="暂无可选规格" @retry="searchOptions()" /><div v-if="!optionLoading && !optionError" class="inventory-options"><article v-for="s in options?.items" :key="s.skuId"><div><strong>{{ s.productName }}</strong><small>{{ s.specification }} · 实物 {{ s.onHand }} / 预留 {{ s.reserved }} / 可售 {{ s.availableStock }}</small></div><button class="secondary-button" type="button" :disabled="busy || rows.some(r => r.skuId === s.skuId) || rows.length >= 100" @click="add(s)">{{ rows.some(r => r.skuId === s.skuId) ? '已添加' : '添加' }}</button></article></div><PagePagination v-if="options && !optionError" :total="options.total" :page="optionPage" :size="10" :busy="optionLoading" @change="searchOptions" /></section>
        <section class="inventory-lines"><h2>货物明细</h2><p v-if="!rows.length" class="field-hint">从上方搜索结果添加规格。</p><div v-else class="table-scroll"><table class="admin-table"><thead><tr><th>商品 / 规格</th><th>账面实物 / 预留</th><th>{{ isCount ? '实盘数量' : '作业数量' }}</th><th>{{ isCount ? '盘点差异' : '实物变动' }}</th><th v-if="isCount && !posted">最新实物 / 预留</th><th v-if="posted">实物调整后</th><th v-if="!posted">操作</th></tr></thead><tbody><tr v-for="(r, index) in rows" :key="r.skuId"><td><strong>{{ r.productName }}</strong><small>{{ r.specification }}</small></td><td>{{ r.beforeOnHand ?? r.currentStock.onHand }} / {{ r.beforeReserved ?? r.currentStock.reserved }}</td><td><span v-if="posted">{{ isCount ? r.countedQuantity : r.quantity }}</span><input v-else-if="isCount" v-model.number="r.countedQuantity" :disabled="busy" type="number" min="0" max="2147483647" step="1" required :aria-label="r.productName + ' 实盘数量'"><input v-else v-model.number="r.quantity" :disabled="busy" type="number" min="1" max="2147483647" step="1" required :aria-label="r.productName + ' 作业数量'"></td><td><strong :class="delta(r) >= 0 ? 'inventory-positive' : 'danger-text'">{{ delta(r) > 0 ? '+' : '' }}{{ delta(r) }}</strong></td><td v-if="isCount && !posted">{{ r.currentStock.onHand }} / {{ r.currentStock.reserved }}<small v-if="r.inventoryVersion !== null && r.inventoryVersion !== r.currentStock.inventoryVersion" class="danger-text">账面已变化</small><small v-if="Number(r.countedQuantity) < r.currentStock.reserved" class="danger-text">实盘低于预留</small></td><td v-if="posted">{{ r.afterOnHand }}</td><td v-if="!posted"><button type="button" class="text-action danger-text" :disabled="busy" @click="rows.splice(index, 1)">移除</button></td></tr></tbody></table></div></section>
        <p v-if="actionError" class="form-message-error" role="alert">{{ actionError }}</p><p v-if="notice" class="form-message-success" role="status">{{ notice }}</p><p v-if="dirty" class="field-hint">存在未保存输入，请先保存草稿再确认。</p>
        <div v-if="!posted" class="inventory-actions"><button class="primary-button" :disabled="busy || !rows.length">{{ busy ? '正在处理…' : '保存草稿' }}</button><button v-if="document" class="secondary-button" type="button" :disabled="busy || dirty" @click="confirmPost">确认生效</button><button v-if="isCount && document" class="secondary-button" type="button" :disabled="busy || dirty" @click="refresh">刷新账面数据并复核</button><button v-if="document" class="text-action danger-text" type="button" :disabled="busy" @click="removeDraft">删除草稿</button><RouterLink class="text-action" :to="back">返回列表</RouterLink></div>
        <p v-if="conflict" class="field-hint">原快照和实盘输入已保留。先保存输入，再显式刷新账面数据，复核后重新确认。</p>
      </form>
      <section v-if="document" class="inventory-audit"><p>创建：{{ document.creatorName }} · {{ inventoryDate(document.createdAt) }}</p><p v-if="posted">确认：{{ document.posterName }} · {{ inventoryDate(document.postedAt) }}</p><p v-if="posted" class="field-hint">本单据已生效，记录保持只读。账面差异通过新的盘点调整纠正。</p></section>
    </template>
  </div>
</template>
