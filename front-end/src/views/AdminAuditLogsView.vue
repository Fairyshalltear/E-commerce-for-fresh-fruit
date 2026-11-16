<template>
  <section class="audit-page">
    <div class="catalog-heading"><div><p class="eyebrow">安全与操作追踪</p><h1>系统日志</h1></div></div>
    <form class="audit-filters" @submit.prevent="load(1)">
      <label>操作者 ID<input v-model="filters.actorId" inputmode="numeric" placeholder="全部"></label>
      <label>事件类别<select v-model="filters.category"><option value="">全部</option><option value="AUTHENTICATION">登录</option><option value="BUSINESS">业务操作</option></select></label>
      <label>结果<select v-model="filters.result"><option value="">全部</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option><option value="DENIED">拒绝</option></select></label>
      <label>目标/操作<input v-model.trim="filters.target" placeholder="摘要或操作名称"></label>
      <label>来源 IP<input v-model.trim="filters.sourceIp"></label><label>请求路径<input v-model.trim="filters.requestPath"></label>
      <label>开始时间<input v-model="filters.from" type="datetime-local"></label><label>结束时间<input v-model="filters.to" type="datetime-local"></label>
      <button class="primary-button">筛选</button><button class="text-action" type="button" @click="clear">清空</button>
    </form>
    <p v-if="error" class="form-error" role="alert">{{ error }} <button class="text-action" @click="load(page)">重试</button></p>
    <p v-if="loading" class="page-status">正在读取日志…</p>
    <div v-else class="audit-table-wrap"><table class="audit-table"><thead><tr><th>时间</th><th>操作者</th><th>事件</th><th>目标</th><th>结果</th><th>来源 IP</th><th>路径</th><th></th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td>{{ format(item.createdAt) }}</td><td>{{ item.actorUsername || '未知账号' }}<small v-if="item.actorId">#{{ item.actorId }}</small></td><td>{{ item.operation }}</td><td>{{ item.targetSummary || '—' }}</td><td><span :class="['result',item.result.toLowerCase()]">{{ resultLabel(item.result) }}</span></td><td>{{ item.sourceIp || '—' }}</td><td class="path">{{ item.requestPath || '—' }}</td><td><button class="text-action" @click="show(item)">详情</button></td></tr><tr v-if="!rows.length"><td colspan="8">没有匹配的审计记录</td></tr></tbody></table></div>
    <div class="audit-pager"><span>共 {{ total }} 条 · 共 {{ totalPages }} 页 · 每页 {{ size }} 条</span><button class="secondary-button" :disabled="page<=1 || loading" @click="load(page-1)">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button class="secondary-button" :disabled="page>=totalPages || loading" @click="load(page+1)">下一页</button></div>
    <section v-if="selected" class="audit-detail"><div class="editor-heading"><h2>日志详情 #{{ selected.id }}</h2><button class="icon-close" aria-label="关闭详情" @click="selected=null">×</button></div><dl><dt>操作者</dt><dd>{{ selected.actorUsername || '未知账号' }} {{ selected.actorId ? `(#${selected.actorId})` : '' }}</dd><dt>事件类别</dt><dd>{{ selected.category }}</dd><dt>操作</dt><dd>{{ selected.operation }}</dd><dt>目标</dt><dd>{{ selected.targetSummary || '—' }}</dd><dt>结果</dt><dd>{{ resultLabel(selected.result) }} {{ selected.failureCategory || '' }}</dd><dt>来源 IP</dt><dd>{{ selected.sourceIp || '—' }}</dd><dt>请求路径</dt><dd>{{ selected.requestPath || '—' }}</dd><dt>时间</dt><dd>{{ format(selected.createdAt) }}</dd></dl></section>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import http,{type ApiResponse} from '../api/http'
interface Log { id:number; actorId:number|null; actorUsername:string|null; category:string; operation:string; targetSummary:string|null; result:string; failureCategory:string|null; sourceIp:string|null; requestPath:string|null; createdAt:string }
interface Page { items:Log[]; total:number; page:number; size:number }
const rows=ref<Log[]>([]),total=ref(0),page=ref(1),size=ref(20),loading=ref(false),error=ref(''),selected=ref<Log|null>(null)
const totalPages=computed(()=>Math.max(1,Math.ceil(total.value/size.value)))
const filters=reactive({actorId:'',category:'',result:'',target:'',sourceIp:'',requestPath:'',from:'',to:''})
async function load(next:number){page.value=next;loading.value=true;error.value='';try{const params=Object.fromEntries(Object.entries({...filters,page:page.value,size:size.value}).filter(([,v])=>v!==''));const response=await http.get<ApiResponse<Page>>('/api/v1/admin/audit-logs',{params});rows.value=response.data.data.items;total.value=response.data.data.total;page.value=response.data.data.page;size.value=response.data.data.size}catch{error.value='日志查询失败。'}finally{loading.value=false}}
async function show(item:Log){try{selected.value=(await http.get<ApiResponse<Log>>(`/api/v1/admin/audit-logs/${item.id}`)).data.data}catch{error.value='日志详情读取失败。'}}
function clear(){Object.assign(filters,{actorId:'',category:'',result:'',target:'',sourceIp:'',requestPath:'',from:'',to:''});void load(1)}
function format(value:string){return new Date(value).toLocaleString('zh-CN',{hour12:false})}
function resultLabel(value:string){return ({SUCCESS:'成功',FAILED:'失败',DENIED:'拒绝'} as Record<string,string>)[value]??value}
onMounted(()=>void load(1))
</script>
<style scoped>
.audit-page{max-width:1300px;min-width:0}.audit-filters{display:flex;flex-wrap:wrap;align-items:end;gap:10px;padding:16px 0 20px;border-bottom:1px solid #e3e9e4}.audit-filters label{display:grid;gap:5px;color:#57645d;font-size:12px}.audit-filters input,.audit-filters select{height:36px;max-width:190px;padding:0 8px;border:1px solid #cbd5ce;border-radius:4px}.audit-table-wrap{max-width:100%;overflow-x:auto}.audit-table{width:100%;min-width:900px;border-collapse:collapse;text-align:left;font-size:12px}.audit-table th,.audit-table td{padding:11px 8px;border-bottom:1px solid #e9eeea;vertical-align:top}.audit-table th{color:#65736b;font-weight:500}.audit-table small{display:block;color:#839087}.audit-table .path{max-width:190px;overflow-wrap:anywhere}.result{font-weight:600}.result.success{color:#278258}.result.failed,.result.denied{color:#ba5047}.audit-pager{display:flex;align-items:center;justify-content:flex-end;gap:12px;padding:16px 0}.audit-detail{margin-top:22px;padding-top:18px;border-top:1px solid #cbd5ce}.editor-heading{display:flex;justify-content:space-between;align-items:center}.editor-heading h2{font-size:17px}.icon-close{border:0;background:none;font-size:24px;color:#56645c}.audit-detail dl{display:grid;grid-template-columns:130px minmax(0,1fr);gap:10px;font-size:13px}.audit-detail dt{color:#66736b}.audit-detail dd{margin:0;overflow-wrap:anywhere}.form-error,.page-status{padding:16px 0;color:#b54841}@media(max-width:600px){.audit-filters label{flex:1 1 130px}.audit-detail dl{grid-template-columns:100px minmax(0,1fr)}}
</style>
