<template>
  <section><div class="catalog-heading"><div><p class="eyebrow">你的咨询记录</p><h2>AI 问答历史</h2><p class="muted">记录保留180天。删除会立即清除正文，并停止该对话的活动回答。</p></div><button type="button" class="secondary-button" @click="ai.open = true">打开客服</button></div><PageStatus :loading="loading" :error="error" :empty="!items?.items.length" empty-text="暂无问答记录，可以从商城右下角开始咨询。" @retry="load" />
    <div v-if="items?.items.length" class="history-layout"><div><article v-for="item in items.items" :key="item.id" class="history-item" :class="{ selected: ai.conversation?.id === item.id }"><button type="button" @click="ai.load(item.id, false)"><strong>{{ item.title }}</strong><span>{{ date(item.updatedAt) }}</span></button><button type="button" class="text-action danger-text" @click="remove(item.id)">删除</button></article><div class="pagination"><button :disabled="page === 1" @click="page--; load()">上一页</button><span>{{ page }} / {{ Math.max(1, Math.ceil(items.total / items.size)) }}</span><button :disabled="page * items.size >= items.total" @click="page++; load()">下一页</button></div></div>
      <section v-if="ai.conversation" class="history-detail"><div class="catalog-heading"><h3>{{ ai.conversation.title }}</h3><button type="button" class="primary-button" @click="ai.open = true">继续咨询</button></div><p v-if="ai.error" class="form-message-error" role="alert">{{ ai.error }}</p><p v-if="ai.loading" class="muted">正在读取…</p><article v-for="item in ai.messages" :key="item.id" class="history-message"><header><strong>{{ item.role === 'USER' ? '你' : '鲜果小助手' }}</strong><span>{{ date(item.createdAt) }}</span><span v-if="item.status">{{ aiStatusLabel(item.status) }}</span></header><p class="history-content">{{ item.content || '本次回答没有正文' }}</p><p v-if="item.errorMessage" class="muted">{{ item.errorMessage }}</p><details v-if="item.sources.length"><summary>查看回答依据</summary><p v-for="source in item.sources" :key="source.ordinal"><strong>{{ source.title }}</strong><br>{{ source.excerpt }}</p></details><div v-if="item.status === 'COMPLETED' || item.status === 'DEGRADED'" class="history-feedback"><span>这条回答有帮助吗？</span><button type="button" :aria-pressed="item.helpful === true" :class="{ selected: item.helpful === true }" @click="ai.feedback(item, true)">有帮助</button><button type="button" :aria-pressed="item.helpful === false" :class="{ selected: item.helpful === false }" @click="ai.feedback(item, false)">没帮助</button></div></article><p v-if="!ai.messages.length" class="muted">这段对话还没有消息。</p></section><section v-else class="history-placeholder">选择一段对话查看记录。</section>
    </div>
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import PageStatus from '../components/PageStatus.vue'
import { useAiStore } from '../stores/ai'
import { useAuthStore } from '../stores/auth'
import { listAiConversations } from '../api/ai'
import type { AiConversation } from '../api/ai'
import type { Page } from '../api/catalog'
import { aiStatusLabel } from '../api/admin-ai'
const ai = useAiStore(), auth = useAuthStore(), items = ref<Page<AiConversation> | null>(null), loading = ref(false), error = ref(''), page = ref(1)
const identity = () => ({ buyer: true, token: auth.session!.accessToken })
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
async function load() { loading.value = true; error.value = ''; try { items.value = await listAiConversations(identity(), page.value) } catch (failure) { items.value = null; error.value = failure instanceof Error ? failure.message : '历史读取失败' } finally { loading.value = false } }
async function remove(id: number) { try { await ElMessageBox.confirm('删除后无法恢复问答正文，正在进行的回答会停止。', '删除这段对话？', { confirmButtonText: '删除', cancelButtonText: '保留' }) } catch { return } try { await ai.remove(id); await load() } catch (failure) { error.value = failure instanceof Error ? failure.message : '删除失败' } }
watch(() => ai.conversation?.updatedAt, () => { if (auth.session?.account.role === 'BUYER') void load() })
onMounted(load)
</script>
<style scoped>
.history-layout{display:grid;grid-template-columns:260px minmax(0,1fr);gap:28px;margin-top:24px}.history-item{padding:12px 10px;border-bottom:1px solid #e4eadf;display:flex;align-items:center;gap:12px}.history-item>button:first-child{flex:1;border:0;text-align:left;background:none;padding:0;cursor:pointer;min-width:0;font:inherit}.history-item strong{display:block;font-size:13px;color:#415b42}.history-item span{display:block;font-size:10px;color:#8b9686;margin-top:7px}.history-item.selected{background:#f1f6ed;border-radius:7px}.history-detail{min-width:0;border-left:1px solid #e1e9dc;padding-left:28px}.history-message{padding:18px 0;border-bottom:1px solid #e9eee4}.history-message header{display:flex;gap:14px;align-items:center;font-size:10px;color:#89947f}.history-message header strong{font-size:12px;color:#37563b}.history-content{font-size:13px;line-height:1.9;white-space:pre-wrap;overflow-wrap:anywhere}.history-message details{color:#849078;font-size:11px;line-height:1.7}.history-feedback{display:flex;gap:15px;align-items:center;font-size:10px;color:#8f9889}.history-feedback button{border:0;background:none;color:#537a46;cursor:pointer;font:inherit}.history-feedback .selected{text-decoration:underline;font-weight:700}.history-placeholder{display:grid;place-items:center;min-height:280px;color:#929b8b;font-size:13px}@media(max-width:750px){.history-layout{grid-template-columns:1fr;gap:20px}.history-detail{padding-left:0;border-left:0}.history-layout>div:first-child{max-height:290px;overflow:auto}.history-message header{flex-wrap:wrap;gap:8px}.history-feedback{flex-wrap:wrap}}
</style>
