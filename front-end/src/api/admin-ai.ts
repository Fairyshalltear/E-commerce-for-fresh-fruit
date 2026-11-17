import http, { type ApiResponse } from './http'
import type { Page } from './catalog'
import type { AiSource, AiStatus } from './ai'
export interface Knowledge { id: number; type: string; originKind: string; productId: number | null; title: string; body: string; standardQuestion: string | null; aliases: string[]; sourceLabel: string; enabled: boolean; version: number; activeRevision: number | null; latestRevision: number | null; indexStatus: string; indexError: string | null; updatedAt: string; revisions: { id: number; version: number; title: string; sourceLabel: string; productVersion: number | null; createdAt: string }[] }
export interface KnowledgeInput { type: string; productId?: number | null; title: string; body: string; standardQuestion?: string; aliases: string[]; sourceLabel: string; version?: number }
export interface Rule { id: number; name: string; scope: string; matchMode: string; terms: string[]; priority: number; message: string; enabled: boolean; version: number }
export type RuleInput = Omit<Rule, 'id' | 'version'> & { version?: number }
export interface ModelParameters { temperature: number; maxOutputTokens: number; historyTurns: number; connectTimeoutSeconds: number; firstContentSeconds: number; idleTimeoutSeconds: number; totalTimeoutSeconds: number; buyerPerMinute: number; guestPerMinute: number; buyerDaily: number; guestDaily: number; globalDailyCalls: number }
export interface ModelConfig { id: number; name: string; chatBaseUrl: string; chatModel: string; hasChatKey: boolean; maskedChatKey: string | null; embeddingBaseUrl: string; embeddingModel: string; embeddingDimensions: number; hasEmbeddingKey: boolean; maskedEmbeddingKey: string | null; promptTemplate: string; parameters: ModelParameters; active: boolean; encryptionReady: boolean; createdAt: string; deletionMode?: 'DELETE' | 'ARCHIVE' | 'BLOCKED'; deletionReason?: string }
export interface ModelInput { baseId?: number; name: string; chatBaseUrl: string; chatModel: string; chatKey?: string; clearChatKey: boolean; embeddingBaseUrl: string; embeddingModel: string; embeddingKey?: string; clearEmbeddingKey: boolean; reuseChatKey: boolean; embeddingDimensions: number; promptTemplate: string; parameters: ModelParameters }
export interface Capability { name: string; success: boolean; elapsedMs: number; message: string; dimensions: number | null }
export interface Profile { id: number; configId: number; model: string; dimensions: number; status: string; createdAt: string }
export interface RecordSummary { buyerId: number | null; id: number; title: string; ownerKind: string; questionCount: number; status: AiStatus | null; createdAt: string; updatedAt: string }
export interface Attempt { id: number; questionId: number; answerId: number; retryOf: number | null; status: AiStatus; scene: string; mode: string | null; retrievalMode: string | null; configId: number | null; model: string | null; promptTokens: number | null; completionTokens: number | null; totalMs: number | null; firstContentMs: number | null; errorCode: string | null; createdAt: string; finishedAt: string | null }
export interface RecordDetail { id: number; title: string; ownerKind: string; createdAt: string; updatedAt: string; messages: { id: number; role: string; content: string; status: AiStatus | null; mode: string | null; errorMessage: string | null; helpful: boolean | null; feedbackReason: string | null; sources: Pick<AiSource, 'type' | 'title' | 'version' | 'excerpt'>[]; createdAt: string }[]; attempts: Attempt[]; ruleHits: { ruleId: number; version: number; stage: string; summary: string; createdAt: string }[] }
export interface Statistics { from: string; to: string; timezone: string; questions: number; attempts: number; retries: number; successful: number; degraded: number; failed: number; cancelled: number; blocked: number; processing: number; successRate: number | null; rated: number; helpful: number; satisfaction: number | null; feedbackCoverage: number | null; averageMs: number | null; firstContentMs: number | null; trend: { date: string; questions: number; attempts: number; successful: number; failed: number; degraded: number }[]; scenes: { scene: string; count: number }[]; topics: { topic: string; count: number }[] }
const root = '/api/v1/admin/ai'
async function get<T>(path: string, params?: object) { return (await http.get<ApiResponse<T>>(root + path, { params })).data.data }
async function post<T>(path: string, data?: unknown) { return (await http.post<ApiResponse<T>>(root + path, data)).data.data }
export const listKnowledge = (params: object) => get<Page<Knowledge>>('/knowledge', params)
export const getKnowledge = (id: number) => get<Knowledge>('/knowledge/' + id)
export const saveKnowledge = async (id: number | null, input: KnowledgeInput) => id === null ? post<Knowledge>('/knowledge', input) : (await http.put<ApiResponse<Knowledge>>(root + '/knowledge/' + id, input)).data.data
export const publishKnowledge = (id: number, version: number) => post<Knowledge>(`/knowledge/${id}/publish`, { version })
export const retryKnowledge = (id: number, version: number) => post<Knowledge>(`/knowledge/${id}/retry`, { version })
export const enableKnowledge = async (id: number, version: number, enabled: boolean) => (await http.patch<ApiResponse<Knowledge>>(root + `/knowledge/${id}/enabled`, { version, enabled })).data.data
export const businessPolicy = () => get<string>('/knowledge/business-policy')
export const searchKnowledge = (question: string) => post<{ mode: string; hits: { source: AiSource; keywordScore: number; vectorScore: number; exactFaq: boolean }[] }>('/knowledge/search-test', { question })
export const listRules = (page = 1) => get<Page<Rule>>('/rules', { page })
export const saveRule = async (id: number | null, input: RuleInput) => id === null ? post<Rule>('/rules', input) : (await http.put<ApiResponse<Rule>>(root + '/rules/' + id, input)).data.data
export const enableRule = async (rule: Rule) => (await http.patch<ApiResponse<Rule>>(root + `/rules/${rule.id}/enabled`, { enabled: !rule.enabled, version: rule.version })).data.data
export const testRules = (question: string, scope: string) => post<{ blocked: boolean; rules: Rule[] }>('/rules/test?scope=' + scope, { question })
export const listModels = () => get<ModelConfig[]>('/models')
export const saveModel = (input: ModelInput) => post<ModelConfig>('/models', input)
export const activateModel = (id: number) => post<ModelConfig>(`/models/${id}/activate`)
export const disableModel = (id: number) => post<ModelConfig>(`/models/${id}/disable`)
export const deleteModel = async (id: number) => (await http.delete<ApiResponse<{ id: number; mode: 'DELETE' | 'ARCHIVE' }>>(root + `/models/${id}`)).data.data
export const testModel = async (id: number, capability: string) => (await http.post<ApiResponse<Capability[]>>(root + `/models/${id}/test?capability=${capability}`, undefined, { timeout: 200000 })).data.data
export const listProfiles = () => get<Profile[]>('/models/profiles')
export const buildProfile = (id: number) => post<{ profileId: number }>(`/models/${id}/profiles`)
export const switchProfile = (id: number) => post<void>(`/models/profiles/${id}/switch`)
export const cancelProfile = (id: number) => post<void>(`/models/profiles/${id}/cancel`)
export const listRecords = (params: object) => get<Page<RecordSummary>>('/conversations', params)
export const getRecord = (id: number) => get<RecordDetail>('/conversations/' + id)
export const statistics = (params: object) => get<Statistics>('/statistics', params)
export const aiSceneLabel = (scene: string) => ({ GENERAL: '其他咨询', PRODUCT: '水果与商品', RECOMMEND: '商品推荐', ORDER: '订单与物流', AFTER_SALE: '售后与退款' } as Record<string, string>)[scene] ?? scene
export const aiStatusLabel = (status: string | null) => ({ ACCEPTED: '待连接', RUNNING: '回答中', COMPLETED: '已完成', DEGRADED: '基础回答', FAILED: '失败', CANCELLED: '已停止', BLOCKED: '已拦截' } as Record<string, string>)[status ?? ''] ?? '—'

export const knowledgeProducts = () => get<{ id: number; name: string; facts: string }[]>('/knowledge/products')
