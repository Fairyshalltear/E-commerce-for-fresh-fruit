import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import * as api from '../api/ai'
import type { SseFrame } from '../ai/sse'

const active = (status: api.AiStatus | null) => status === 'ACCEPTED' || status === 'RUNNING'
const guestStorage = 'fresh-fruit-ai-guest'
export const useAiStore = defineStore('ai', () => {
  const auth = useAuthStore()
  const open = ref(false), loading = ref(false), error = ref('')
  const conversation = ref<api.AiConversation | null>(null), messages = ref<api.AiMessage[]>([])
  const context = ref<api.AiContext>({}), currentRequest = ref<api.AiRequest | null>(null)
  const busy = computed(() => loading.value || active(currentRequest.value?.status ?? null))
  const buyer = computed(() => auth.session?.account.role === 'BUYER')
  let guest: { token: string; conversationId: number; expiresAt: string } | null = null
  let reader: AbortController | null = null, epoch = 0
  let pending: { key: string; text: string; context: string } | null = null
  const key = () => crypto.randomUUID()
  const message = (error: unknown) => error instanceof Error ? error.message : '咨询失败，请稍后重试'
  function identity(): api.AiIdentity | null {
    if (buyer.value && auth.session) return { buyer: true, token: auth.session.accessToken }
    return guest ? { buyer: false, token: guest.token } : null
  }
  async function ensure() {
    const snapshot = epoch
    if (!buyer.value && !guest) {
      try { const stored = JSON.parse(sessionStorage.getItem(guestStorage) ?? 'null'); if (stored?.token && Date.parse(stored.expiresAt) > Date.now()) guest = stored } catch { sessionStorage.removeItem(guestStorage) }
      if (!guest) { const created = await api.createGuest(); if (snapshot !== epoch) throw new Error('登录身份已变化'); guest = created; sessionStorage.setItem(guestStorage, JSON.stringify(guest)) }
    }
    const actor = identity()!
    if (!buyer.value && !conversation.value && guest) {
      const session = guest
      try { const detail = await api.getAiConversation(actor, session.conversationId); if (snapshot !== epoch) throw new Error('登录身份已变化'); apply(detail) }
      catch (failure) { if (snapshot === epoch && failure instanceof api.AiApiError && [401, 404].includes(failure.status)) { guest = null; sessionStorage.removeItem(guestStorage); throw new Error('访客会话已失效，请重新开始咨询') } throw failure }
    }
    if (!conversation.value) { const created = await api.createAiConversation(actor, context.value); if (snapshot !== epoch) throw new Error('登录身份已变化'); conversation.value = created }
    return actor
  }
  function apply(detail: api.AiDetail) {
    conversation.value = detail.conversation; context.value = detail.conversation.context ?? {}; messages.value = detail.messages
    const latest = [...detail.messages].reverse().find(item => item.requestId && active(item.status))
    currentRequest.value = latest ? { id: latest.requestId!, answerId: latest.id, questionId: 0, conversationId: detail.conversation.id, status: latest.status!, scene: latest.scene ?? 'GENERAL', mode: latest.mode, errorCode: latest.errorCode, errorMessage: latest.errorMessage, createdAt: latest.createdAt, finishedAt: null } : null
  }
  async function refresh(actor: api.AiIdentity, id: number, snapshot: number) { const detail = await api.getAiConversation(actor, id); if (snapshot === epoch) apply(detail) }
  async function listen(actor: api.AiIdentity, request: api.AiRequest, snapshot: number) {
    reader?.abort(); const control = new AbortController(); reader = control
    currentRequest.value = request
    const answer = () => messages.value.find(item => item.id === request.answerId)
    const onFrame = (frame: SseFrame) => {
      if (snapshot !== epoch) return
      const data = JSON.parse(frame.data) as Record<string, unknown>
      const item = answer()
      if (frame.event === 'status') { currentRequest.value = { ...request, status: data.status as api.AiStatus }; if (item) item.status = data.status as api.AiStatus }
      if (frame.event === 'delta' && item) item.content += String(data.text ?? '')
      if (frame.event === 'cards' && item) item.cards = data.items as api.AiCard[]
      if (frame.event === 'sources' && item) item.sources = data.items as api.AiSource[]
      if (frame.event === 'done') {
        const final = data.request as api.AiRequest | undefined
        if (item) { item.content = String(data.content ?? item.content); item.status = final?.status ?? 'CANCELLED'; item.mode = final?.mode ?? null; item.errorCode = final?.errorCode ?? null; item.errorMessage = final?.errorMessage ?? null }
        currentRequest.value = final ?? null
      }
    }
    try { await api.streamAiRequest(actor, request.id, control.signal, onFrame) }
    catch (failure) {
      if (snapshot === epoch && !control.signal.aborted) {
        error.value = message(failure)
        try { await refresh(actor, request.conversationId, snapshot) } catch { /* Keep the readable local checkpoint. */ }
      }
    } finally {
      if (reader === control) reader = null
      if (snapshot === epoch && !control.signal.aborted) { try { await refresh(actor, request.conversationId, snapshot) } catch { /* Explicit refresh remains available. */ } }
    }
  }
  async function send(text: string, selected?: api.AiContext) {
    if (busy.value || !text.trim()) return
    loading.value = true; error.value = ''; const snapshot = epoch
    const selectedContext = { ...(selected ?? context.value) }
    try {
      const actor = await ensure(); if (snapshot !== epoch) return
      const fingerprint = JSON.stringify(selectedContext)
      if (!pending || pending.text !== text || pending.context !== fingerprint) pending = { key: key(), text, context: fingerprint }
      const request = await api.sendAiMessage(actor, conversation.value!.id, pending.key, text, selectedContext)
      if (snapshot !== epoch) { await api.stopAiRequest(actor, request.id).catch(() => undefined); return }
      pending = null; await refresh(actor, request.conversationId, snapshot)
      loading.value = false; void listen(actor, request, snapshot)
    } catch (failure) { if (snapshot === epoch) error.value = message(failure) }
    finally { if (snapshot === epoch) loading.value = false }
  }
  async function stop() {
    const actor = identity(), request = currentRequest.value, snapshot = epoch
    if (!actor || !request || !active(request.status)) return
    error.value = ''
    try { const final = await api.stopAiRequest(actor, request.id); if (snapshot === epoch) { currentRequest.value = final; reader?.abort(); await refresh(actor, request.conversationId, snapshot) } }
    catch (failure) { if (snapshot === epoch) error.value = message(failure); throw failure }
  }
  async function retry(id: number) {
    if (busy.value) return
    const actor = identity(); if (!actor) return
    loading.value = true; error.value = ''; const snapshot = epoch
    try { const request = await api.retryAiRequest(actor, id, key()); if (snapshot !== epoch) { await api.stopAiRequest(actor, request.id).catch(() => undefined); return } await refresh(actor, request.conversationId, snapshot); loading.value = false; void listen(actor, request, snapshot) }
    catch (failure) { if (snapshot === epoch) error.value = message(failure) }
    finally { if (snapshot === epoch) loading.value = false }
  }
  async function startNew() {
    if (currentRequest.value && active(currentRequest.value.status)) { try { await stop() } catch { return } }
    const actor = identity(), snapshot = ++epoch
    reader?.abort(); reader = null; messages.value = []; conversation.value = null; context.value = {}; currentRequest.value = null; pending = null; error.value = ''
    if (actor) { loading.value = true; try { const created = await api.createAiConversation(actor); if (snapshot === epoch) { conversation.value = created; if (guest) { guest.conversationId = created.id; sessionStorage.setItem(guestStorage, JSON.stringify(guest)) } } } catch (failure) { if (snapshot === epoch) error.value = message(failure) } finally { if (snapshot === epoch) loading.value = false } }
  }
  async function load(id: number, show = true) {
    if (currentRequest.value && active(currentRequest.value.status)) { try { await stop() } catch { return } }
    const actor = identity() ?? await ensure(), snapshot = ++epoch
    reader?.abort(); loading.value = true; error.value = ''; open.value = show
    try { await refresh(actor, id, snapshot) } catch (failure) { if (snapshot === epoch) error.value = message(failure) } finally { if (snapshot === epoch) loading.value = false }
  }
  async function feedback(item: api.AiMessage, helpful: boolean) {
    const actor = identity(); if (!actor) return
    try { await api.rateAiAnswer(actor, item.id, helpful); item.helpful = helpful } catch (failure) { error.value = message(failure) }
  }
  async function remove(id: number) {
    const actor = identity(); if (!actor) return
    await api.deleteAiConversation(actor, id)
    if (conversation.value?.id === id) { epoch++; reader?.abort(); reader = null; conversation.value = null; messages.value = []; currentRequest.value = null; context.value = {}; pending = null; loading.value = false; error.value = '' }
  }
  function openWithContext(selected: api.AiContext = {}) { const existing = { ...context.value }; if (selected.productId) { delete existing.orderId; delete existing.afterSaleId } if (selected.orderId || selected.afterSaleId) delete existing.productId; if (selected.orderId && selected.orderId !== existing.orderId) delete existing.afterSaleId; context.value = { ...existing, ...selected }; open.value = true }
  async function recover() { const actor = identity(); if (!actor || !conversation.value) return; const snapshot = epoch; try { await refresh(actor, conversation.value.id, snapshot) } catch (failure) { error.value = message(failure) } }
  watch(() => `${auth.session?.account.role ?? 'GUEST'}:${auth.session?.account.id ?? ''}:${auth.session?.accessToken ?? ''}`, (_next, old) => {
    const parts = old.split(':'); const previous: api.AiIdentity | null = parts[0] === 'BUYER' ? { buyer: true, token: parts.slice(2).join(':') } : guest ? { buyer: false, token: guest.token } : null; const request = currentRequest.value
    epoch++; reader?.abort(); reader = null
    if (previous && request && active(request.status)) void api.stopAiRequest(previous, request.id).catch(() => undefined)
    guest = null; sessionStorage.removeItem(guestStorage); conversation.value = null; messages.value = []; context.value = {}; currentRequest.value = null; pending = null; loading.value = false; error.value = ''
  }, { flush: 'sync' })
  return { open, loading, error, conversation, messages, context, currentRequest, busy, buyer, send, stop, retry, startNew, load, feedback, openWithContext, recover, identity, remove }
})
