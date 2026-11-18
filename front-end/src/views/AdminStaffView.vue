<template>
  <section class="staff-page">
    <div class="catalog-heading"><div><p class="eyebrow">账号与访问控制</p><h1>店员与权限</h1></div><button class="primary-button" @click="startCreate">新增店员</button></div>
    <p v-if="error" class="form-error" role="alert">{{ error }} <button class="text-action" @click="load">重试</button></p>
    <p v-if="loading" class="page-status">正在读取店员账号…</p>
    <table v-else class="staff-table"><thead><tr><th>账号</th><th>模块权限</th><th>敏感操作</th><th>状态</th><th>操作</th></tr></thead><tbody>
      <tr v-for="person in staff" :key="person.id"><td><strong>{{ person.displayName }}</strong><small>{{ person.username }}</small></td><td>{{ labels(person.modulePermissions) }}</td><td>{{ labels(person.sensitivePermissions) }}</td><td>{{ person.enabled ? '启用' : '已停用' }}</td><td class="actions"><button class="text-action" @click="startEdit(person)">权限</button><button class="text-action" @click="toggle(person)">{{ person.enabled ? '停用' : '启用' }}</button><button class="text-action" @click="resetTarget = person">重置凭据</button></td></tr>
      <tr v-if="!staff.length"><td colspan="5">暂无店员账号</td></tr>
    </tbody></table>
    <ElDialog v-model="editorOpen" :title="editing ? (editing.id ? `编辑：${editing.displayName}` : '新增店员') : ''" width="min(760px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" class="staff-dialog">
      <form v-if="editing" class="staff-editor" @submit.prevent="save">
      <div class="staff-fields"><label v-if="!editing.id">登录账号<input v-model.trim="editing.username" required maxlength="50" autocomplete="off"></label><label>显示名称<input v-model.trim="editing.displayName" required maxlength="50"></label><label v-if="!editing.id">初始密码<input v-model="editing.password" type="password" required minlength="10" maxlength="72" autocomplete="new-password"></label></div>
      <fieldset><legend>模块权限</legend><label v-for="item in modules" :key="item.key" class="permission-option"><input v-model="editing.modulePermissions" type="checkbox" :value="item.key">{{ item.label }}</label></fieldset>
      <fieldset><legend>敏感操作权限</legend><label v-for="item in operations" :key="item.key" class="permission-option"><input v-model="editing.sensitivePermissions" type="checkbox" :value="item.key">{{ item.label }}</label></fieldset>
      <p v-if="saveError" class="form-error" role="alert">{{ saveError }}</p><button class="primary-button" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
      </form>
    </ElDialog>
    <form v-if="resetTarget" class="staff-editor reset-editor" @submit.prevent="resetPassword"><div class="editor-heading"><h2>重置凭据：{{ resetTarget.displayName }}</h2><button class="icon-close" type="button" aria-label="关闭" @click="resetTarget = null">×</button></div><label>新密码<input v-model="newPassword" type="password" minlength="10" maxlength="72" required autocomplete="new-password"></label><p v-if="saveError" class="form-error">{{ saveError }}</p><button class="primary-button" :disabled="saving">确认重置</button></form>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElDialog } from 'element-plus'
import http, { type ApiResponse } from '../api/http'
interface Staff { id:number; username:string; displayName:string; enabled:boolean; modulePermissions:string[]; sensitivePermissions:string[] }
interface Editor { id?:number; username:string; displayName:string; password:string; modulePermissions:string[]; sensitivePermissions:string[] }
const modules=[{key:'PRODUCTS',label:'商品'},{key:'BUYERS',label:'买家'},{key:'ORDERS',label:'订单'},{key:'INVENTORY',label:'库存'},{key:'AFTER_SALES',label:'售后'},{key:'CONTENT',label:'内容'},{key:'STATISTICS',label:'统计'},{key:'AI',label:'AI问答管理'}]
const operations=[{key:'REFUND_EXECUTE',label:'执行退款'},{key:'PRODUCT_STATUS_CHANGE',label:'商品上架/下架'},{key:'INVENTORY_ADJUST',label:'直接调整库存'}]
const staff=ref<Staff[]>([]), loading=ref(false), saving=ref(false), error=ref(''), saveError=ref(''), editing=ref<Editor|null>(null), resetTarget=ref<Staff|null>(null), newPassword=ref('')
const editorOpen=computed({get:()=>editing.value!==null,set:(open:boolean)=>{if(!open&&!saving.value)editing.value=null}})
async function load(){loading.value=true;error.value='';try{staff.value=(await http.get<ApiResponse<Staff[]>>('/api/v1/admin/staff')).data.data}catch{error.value='店员列表加载失败。'}finally{loading.value=false}}
function startCreate(){saveError.value='';editing.value={username:'',displayName:'',password:'',modulePermissions:[],sensitivePermissions:[]}}
function startEdit(person:Staff){saveError.value='';editing.value={id:person.id,username:person.username,displayName:person.displayName,password:'',modulePermissions:[...person.modulePermissions],sensitivePermissions:[...person.sensitivePermissions]}}
async function save(){if(!editing.value)return;saving.value=true;saveError.value='';try{const e=editing.value;const payload={displayName:e.displayName,modulePermissions:e.modulePermissions,sensitivePermissions:e.sensitivePermissions};if(e.id)await http.put(`/api/v1/admin/staff/${e.id}`,payload);else await http.post('/api/v1/admin/staff',{...payload,username:e.username,password:e.password});editing.value=null;await load()}catch{saveError.value='保存失败，请检查账号与权限后重试。'}finally{saving.value=false}}
async function toggle(person:Staff){try{await http.patch(`/api/v1/admin/staff/${person.id}/enabled`,{enabled:!person.enabled});await load()}catch{error.value='账号状态更新失败。'}}
async function resetPassword(){if(!resetTarget.value)return;saving.value=true;saveError.value='';try{await http.post(`/api/v1/admin/staff/${resetTarget.value.id}/credentials`,{password:newPassword.value});resetTarget.value=null;newPassword.value=''}catch{saveError.value='凭据重置失败，请重试。'}finally{saving.value=false}}
function labels(values:string[]){return values.length?values.map(x=>({PRODUCTS:'商品',BUYERS:'买家',ORDERS:'订单',INVENTORY:'库存',AFTER_SALES:'售后',CONTENT:'内容',STATISTICS:'统计',AI:'AI问答管理',REFUND_EXECUTE:'退款',PRODUCT_STATUS_CHANGE:'上下架',INVENTORY_ADJUST:'库存调整'} as Record<string,string>)[x]??x).join('、'):'无'}
onMounted(load)
</script>
<style scoped>
.staff-dialog :deep(.el-dialog__body){max-height:calc(100dvh - 190px);overflow-y:auto}.staff-page{max-width:1180px}.staff-table{width:100%;border-collapse:collapse;text-align:left;font-size:13px}.staff-table th,.staff-table td{padding:12px 10px;border-bottom:1px solid #e5e9e5;vertical-align:top}.staff-table th{color:#66736b;font-weight:500}.staff-table td small{display:block;margin-top:4px;color:#738078;overflow-wrap:anywhere}.staff-table td:first-child{min-width:130px;max-width:240px;overflow-wrap:anywhere}.staff-table .actions{white-space:nowrap}.actions button{margin-right:9px}.staff-editor{padding:0;border:0}.staff-editor .primary-button{margin-top:8px}.staff-fields{display:flex;flex-wrap:wrap;gap:14px}.staff-fields label,.reset-editor label{display:grid;gap:6px;min-width:210px;color:#54625a;font-size:13px}.staff-fields input,.reset-editor input{height:38px;padding:0 10px;border:1px solid #cbd5ce;border-radius:4px}.staff-editor fieldset{margin:18px 0;padding:12px 0;border:0;border-top:1px solid #e5e9e5}.staff-editor legend{padding:8px 8px 8px 0;font-weight:600}.permission-option{display:inline-flex;align-items:center;gap:7px;margin:0 18px 10px 0;font-size:13px}.permission-option input{accent-color:#278258}.page-status,.form-error{padding:18px 0;color:#65736b}.form-error{color:#b54841}@media(max-width:700px){.staff-table{display:block;overflow-x:auto;white-space:nowrap}.staff-table td:nth-child(2),.staff-table td:nth-child(3){white-space:normal;min-width:110px}}
</style>
