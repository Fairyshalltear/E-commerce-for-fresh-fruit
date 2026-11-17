import { readSse, type SseFrame } from '../ai/sse'
import type { Page } from './catalog'

export interface AiIdentity { buyer: boolean; token: string }
export interface AiContext { productId?: number; orderId?: number; afterSaleId?: number; budget?: number; month?: number; giftBox?: boolean; useCase?: string; taste?: string; quantity?: number }
export interface AiCard { type: 'PRODUCT' | 'ORDER' | 'AFTER_SALE'; id: number; title: string; subtitle: string; price: string | null; image: string | null; path: string; context: AiContext }
export interface AiSource { ordinal: number; type: string; id: number | null; version: number | null; title: string; excerpt: string; path: string | null }
export type AiStatus = 'ACCEPTED' | 'RUNNING' | 'COMPLETED' | 'DEGRADED' | 'FAILED' | 'CANCELLED' | 'BLOCKED'
export interface AiRequest { id: number; conversationId: number; questionId: number; answerId: number; status: AiStatus; scene: string; mode: string | null; errorCode: string | null; errorMessage: string | null; createdAt: string; finishedAt: string | null }
export interface AiConversation { id: number; title: string; ownerKind: string; context: AiContext; createdAt: string; updatedAt: string; expiresAt: string }
export interface AiMessage { id: number; sequence: number; role: 'USER' | 'ASSISTANT'; content: string; requestId: number | null; status: AiStatus | null; mode: string | null; scene: string | null; errorCode: string | null; errorMessage: string | null; cards: AiCard[]; sources: AiSource[]; helpful: boolean | null; feedbackReason: string | null; createdAt: string }
export interface AiDetail { conversation: AiConversation; messages: AiMessage[] }
export class AiApiError extends Error { constructor(public readonly status: number, public readonly code: string, message: string) { super(message) } }
const base = import.meta.env.VITE_API_BASE_URL ?? ''
function headers(identity?: AiIdentity) { return { 'Content-Type': 'application/json', ...(identity ? identity.buyer ? { Authorization: `Bearer ${identity.token}` } : { 'X-AI-Guest-Token': identity.token } : {}) } }
function prefix(identity: AiIdentity) { return `/api/v1/${identity.buyer ? 'buyer' : 'public'}/ai` }
async function failure(response: Response, identity?: AiIdentity): Promise<never> {
  const data = await response.json().catch(() => ({ code: 'AI_NETWORK_ERROR', message: '咨询连接失败，请稍后重试' })) as { code: string; message: string }
  if (response.status === 401 && identity?.buyer) { let current: { accessToken?: string } | null = null; try { current = JSON.parse(localStorage.getItem('fresh-fruit-session') ?? 'null') } catch { /* Invalid session will be cleared by auth. */ } if (current?.accessToken === identity.token) { localStorage.removeItem('fresh-fruit-session'); window.dispatchEvent(new Event('fresh-fruit-auth-expired')) } }
  throw new AiApiError(response.status, data.code, data.message)
}
async function request<T>(path: string, method = 'GET', payload?: unknown, identity?: AiIdentity): Promise<T> {
  const response = await fetch(base + path, { method, headers: headers(identity), body: payload === undefined ? undefined : JSON.stringify(payload) })
  if (!response.ok) return failure(response, identity)
  const envelope = await response.json() as { data: T }
  return envelope.data
}
export const createGuest = () => request<{ token: string; conversationId: number; expiresAt: string }>('/api/v1/public/ai/guest-session', 'POST', {})
export const createAiConversation = (identity: AiIdentity, context: AiContext = {}) => request<AiConversation>(prefix(identity) + '/conversations', 'POST', { context }, identity)
export const getAiConversation = (identity: AiIdentity, id: number) => request<AiDetail>(prefix(identity) + '/conversations/' + id, 'GET', undefined, identity)
export const listAiConversations = (identity: AiIdentity, page = 1) => request<Page<AiConversation>>(prefix(identity) + `/conversations?page=${page}`, 'GET', undefined, identity)
export const deleteAiConversation = (identity: AiIdentity, id: number) => request<void>(prefix(identity) + '/conversations/' + id, 'DELETE', undefined, identity)
export const sendAiMessage = (identity: AiIdentity, id: number, key: string, text: string, context?: AiContext) => request<AiRequest>(prefix(identity) + `/conversations/${id}/messages`, 'POST', { clientRequestKey: key, text, context }, identity)
export const retryAiRequest = (identity: AiIdentity, id: number, key: string) => request<AiRequest>(prefix(identity) + `/requests/${id}/retry`, 'POST', { clientRequestKey: key }, identity)
export const stopAiRequest = (identity: AiIdentity, id: number) => request<AiRequest>(prefix(identity) + `/requests/${id}/stop`, 'POST', {}, identity)
export const getAiRequest = (identity: AiIdentity, id: number) => request<AiRequest>(prefix(identity) + `/requests/${id}`, 'GET', undefined, identity)
export const rateAiAnswer = (identity: AiIdentity, id: number, helpful: boolean, reason = '') => request<void>(prefix(identity) + `/answers/${id}/feedback`, 'PUT', { helpful, reason }, identity)
export async function streamAiRequest(identity: AiIdentity, id: number, signal: AbortSignal, onFrame: (frame: SseFrame) => void) {
  const response = await fetch(base + prefix(identity) + `/requests/${id}/stream`, { headers: headers(identity), signal })
  if (!response.ok) return failure(response, identity)
  let done = false
  await readSse(response, frame => { if (frame.event === 'done') done = true; onFrame(frame) })
  if (!done && !signal.aborted) throw new Error('回答连接已中断，请读取当前记录或重试')
}
