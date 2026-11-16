<template>
  <section class="admin-workspace">
    <div class="catalog-heading"><div><p class="eyebrow">经营分析</p><h1>经营统计</h1></div></div>
    <form class="analytics-filters" @submit.prevent="load">
      <label>统计周期<select v-model="period"><option value="TODAY">今日</option><option value="WEEK">本周</option><option value="MONTH">本月</option><option value="LAST_30_DAYS">最近 30 天</option><option value="CUSTOM">自定义</option></select></label>
      <template v-if="period === 'CUSTOM'"><label>开始日期<input v-model="from" type="date" required></label><label>结束日期<input v-model="to" type="date" required></label></template>
      <label>趋势粒度<select v-model="grain"><option value="DAY">按日</option><option value="WEEK">按周</option><option value="MONTH">按月</option></select></label>
      <button class="primary-button" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
    </form>
    <p v-if="error" class="form-error" role="alert">{{ error }} <button class="text-action" type="button" @click="load">重试</button></p>
    <p v-if="loading" class="page-status">正在读取经营数据…</p>
    <template v-else-if="dashboard">
      <div class="analytics-kpis">
        <article><span>净销售额</span><strong>¥{{ money(dashboard.salesAmount) }}</strong></article>
        <article><span>净销售量</span><strong>{{ dashboard.units }}</strong></article>
        <article><span>订单量</span><strong>{{ dashboard.orders }}</strong></article>
        <article><span>注册买家</span><strong>{{ dashboard.registrations }}</strong></article>
        <article><span>活跃买家</span><strong>{{ dashboard.activeBuyers }}</strong></article>
      </div>
      <div class="analytics-chart-grid"><section><h2>销售与订单趋势</h2><div ref="trendEl" class="analytics-chart"></div></section><section><h2>订单状态分布</h2><div ref="statusEl" class="analytics-chart"></div></section></div>
      <div class="analytics-ranks"><section><h2>热销商品排行</h2><table><thead><tr><th>商品</th><th>净销量</th><th>净销售额</th></tr></thead><tbody><tr v-for="item in dashboard.hotProducts" :key="item.id"><td>{{ item.name }}</td><td>{{ item.units }}</td><td>¥{{ money(item.salesAmount) }}</td></tr><tr v-if="!dashboard.hotProducts.length"><td colspan="3">暂无销售数据</td></tr></tbody></table></section><section><h2>可售库存排行</h2><table><thead><tr><th>商品</th><th>可售库存</th></tr></thead><tbody><tr v-for="item in dashboard.stockProducts" :key="item.id"><td>{{ item.name }}</td><td>{{ item.availableStock }}</td></tr><tr v-if="!dashboard.stockProducts.length"><td colspan="2">暂无库存数据</td></tr></tbody></table></section></div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type ECharts } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import http, { type ApiResponse } from '../api/http'

interface Dashboard {
  salesAmount: number; units: number; orders: number; registrations: number; activeBuyers: number
  orderStatuses: Record<string, number>
  trend: Array<{ date: string; salesAmount: number; units: number; orders: number; registrations: number }>
  hotProducts: Array<{ id: number; name: string; units: number; salesAmount: number }>
  stockProducts: Array<{ id: number; name: string; availableStock: number }>
}
const period = ref('MONTH'), grain = ref('DAY'), from = ref(''), to = ref(''), dashboard = ref<Dashboard | null>(null), loading = ref(false), error = ref('')
const trendEl = ref<HTMLElement>(), statusEl = ref<HTMLElement>()
use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
let trendChart: ECharts | undefined, statusChart: ECharts | undefined
async function load() {
  loading.value = true; error.value = ''
  try {
    const response = await http.get<ApiResponse<Dashboard>>('/api/v1/admin/analytics', { params: { period: period.value, grain: grain.value, ...(period.value === 'CUSTOM' ? { from: from.value, to: to.value } : {}) } })
    dashboard.value = response.data.data
    loading.value = false
    await nextTick(); renderCharts()
  } catch { error.value = '统计数据暂时无法读取，请检查筛选条件后重试。' } finally { loading.value = false }
}
function renderCharts() {
  if (!dashboard.value || !trendEl.value || !statusEl.value) return
  trendChart ??= init(trendEl.value); statusChart ??= init(statusEl.value)
  const points = dashboard.value.trend
  trendChart.setOption({ color: ['#25855a', '#e29a43'], tooltip: { trigger: 'axis' }, legend: { data: ['销售额', '订单量'] }, grid: { left: 48, right: 20, top: 42, bottom: 32 }, xAxis: { type: 'category', data: points.map(x => x.date) }, yAxis: [{ type: 'value', name: '金额' }, { type: 'value', name: '订单', minInterval: 1 }], series: [{ name: '销售额', type: 'line', smooth: true, data: points.map(x => x.salesAmount), areaStyle: { opacity: .08 } }, { name: '订单量', type: 'bar', yAxisIndex: 1, data: points.map(x => x.orders), barMaxWidth: 20 }] })
  statusChart.setOption({ color: ['#25855a', '#e29a43', '#5586a5', '#c95e55', '#9c79a5'], tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: ['40%', '70%'], label: { formatter: '{b}\n{c}' }, data: Object.entries(dashboard.value.orderStatuses).map(([name, value]) => ({ name: statusName(name), value })) }] })
}
function statusName(value: string) { return ({ PENDING_PAYMENT: '待付款', PENDING_SHIPMENT: '待发货', PENDING_RECEIPT: '待收货', COMPLETED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[value] ?? value }
function money(value: number | null) { return Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function resize() { trendChart?.resize(); statusChart?.resize() }
watch(grain, () => { if (dashboard.value) void load() })
onMounted(() => { void load(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); trendChart?.dispose(); statusChart?.dispose() })
</script>

<style scoped>
.admin-workspace { max-width: 1180px; min-width: 0; }
.analytics-filters { display:flex; flex-wrap:wrap; align-items:end; gap:14px; padding:16px 0 22px; border-bottom:1px solid #e5e9e5; }
.analytics-filters label { display:grid; gap:6px; color:#52605a; font-size:13px; }
.analytics-filters select,.analytics-filters input { min-height:38px; min-width:150px; padding:0 10px; border:1px solid #cbd5ce; border-radius:4px; background:white; color:#26352d; }
.analytics-kpis { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:0; border-bottom:1px solid #e5e9e5; }
.analytics-kpis article { display:grid; gap:10px; padding:20px 16px 18px 0; }
.analytics-kpis article span { color:#65736b; font-size:13px; }
.analytics-kpis strong { color:#233b2e; font-size:22px; overflow-wrap:anywhere; }
.analytics-chart-grid,.analytics-ranks { display:grid; grid-template-columns:2fr 1fr; gap:28px; border-bottom:1px solid #e5e9e5; padding:20px 0; }
.analytics-ranks { grid-template-columns:1fr 1fr; }
.analytics-chart-grid h2,.analytics-ranks h2 { margin:0 0 12px; font-size:16px; }
.analytics-chart { height:300px; min-width:0; }
.analytics-ranks table { width:100%; border-collapse:collapse; text-align:left; font-size:13px; }
.analytics-ranks th,.analytics-ranks td { padding:10px 8px; border-bottom:1px solid #edf0ed; }
.analytics-ranks td:last-child,.analytics-ranks th:last-child { text-align:right; }
.analytics-ranks td[colspan] { text-align:center; color:#79847d; padding:22px; }
.page-status,.form-error { padding:18px 0; color:#647169; }
.form-error { color:#b54841; }
@media(max-width:760px) { .analytics-kpis { grid-template-columns:repeat(2,minmax(0,1fr)); } .analytics-kpis article { border-bottom:1px solid #edf0ed; } .analytics-chart-grid,.analytics-ranks { grid-template-columns:1fr; gap:16px; } .analytics-chart { height:260px; } }
</style>
