import http, { type ApiResponse } from './http'
export interface Page<T> { items: T[]; total: number; page: number; size: number }
export type DocumentType = 'PURCHASE_IN' | 'SUPPLIER_RETURN_OUT' | 'STOCKTAKE'
export interface Stock { skuId: number; productId: number; productName: string; specification: string; categoryId: number; categoryName: string; productStatus: number; skuStatus: number; deleted: number; onHand: number; reserved: number; availableStock: number; lowStockThreshold: number; lowStock: boolean; inventoryVersion: number }
export interface Summary { skuCount: number; onHand: number; reserved: number; availableStock: number; lowStockCount: number }
export interface StockQuery { keyword?: string; categoryId?: number; status?: number; skuStatus?: number; deleted?: number; stockState?: string; skuId?: number; type?: DocumentType; page?: number; size?: number }
export interface Movement { id: number; skuId: number; productName: string; specification: string; type: string; beforeOnHand: number; afterOnHand: number; quantityDelta: number; reason: string; adminName: string; createdAt: string; sourceType: string | null; sourceId: number | null; sourceItemId: number | null; sourceNo: string | null }
export interface DocumentItem { id: number; skuId: number; productName: string; specification: string; quantity: number | null; countedQuantity: number | null; beforeOnHand: number | null; beforeReserved: number | null; beforeAvailable: number | null; inventoryVersion: number | null; afterOnHand: number | null; quantityDelta: number | null; currentStock: Stock }
export interface StockDocument { id: number; documentNo: string; type: DocumentType; status: 'DRAFT' | 'POSTED'; supplier: string; referenceNo: string; reason: string; version: number; creatorName: string; posterName: string | null; createdAt: string; updatedAt: string; postedAt: string | null; items: DocumentItem[] }
export interface Draft { type: DocumentType; supplier: string; referenceNo: string; reason: string; version?: number; items: { skuId: number; quantity?: number; countedQuantity?: number }[] }
export interface MovementQuery { keyword?: string; skuId?: number; type?: string; direction?: string; sourceNo?: string; from?: string; to?: string; page?: number; size?: number }
export interface DocumentQuery { keyword?: string; type?: DocumentType; status?: string; from?: string; to?: string; page?: number; size?: number }
const base = '/api/v1/admin/inventory'
export const listStocks = async (params: StockQuery) => (await http.get<ApiResponse<Page<Stock>>>(base + '/stocks', { params })).data.data
export const inventorySummary = async (params: StockQuery) => (await http.get<ApiResponse<Summary>>(base + '/summary', { params })).data.data
export const skuOptions = async (params: StockQuery) => (await http.get<ApiResponse<Page<Stock>>>(base + '/sku-options', { params })).data.data
export const alertCount = async () => (await http.get<ApiResponse<number>>(base + '/alerts/count')).data.data
export const setThreshold = async (skuId: number, lowStockThreshold: number, version: number) => (await http.patch<ApiResponse<Stock>>(base + '/stocks/' + skuId + '/threshold', { lowStockThreshold, version })).data.data
export const listMovements = async (params: MovementQuery) => (await http.get<ApiResponse<Page<Movement>>>(base + '/movements', { params })).data.data
export const listDocuments = async (params: DocumentQuery) => (await http.get<ApiResponse<Page<StockDocument>>>(base + '/documents', { params })).data.data
export const getDocument = async (id: number) => (await http.get<ApiResponse<StockDocument>>(base + '/documents/' + id)).data.data
export const saveDocument = async (id: number | null, draft: Draft) => (id === null ? await http.post<ApiResponse<StockDocument>>(base + '/documents', draft) : await http.put<ApiResponse<StockDocument>>(base + '/documents/' + id, draft)).data.data
export const postDocument = async (id: number, version: number) => (await http.post<ApiResponse<StockDocument>>(base + '/documents/' + id + '/post', { version })).data.data
export const refreshSnapshot = async (id: number, version: number) => (await http.post<ApiResponse<StockDocument>>(base + '/documents/' + id + '/refresh-snapshot', { version })).data.data
export const deleteDocument = async (id: number, version: number) => { await http.delete(base + '/documents/' + id, { params: { version } }) }
export function notifyInventoryChanged() { window.dispatchEvent(new Event('fresh-fruit-inventory-changed')) }
export const movementLabels: Record<string, string> = { INITIAL: '初始库存', PRODUCT_EDIT_ADJUSTMENT: '历史商品调整', PURCHASE_IN: '采购入库', SUPPLIER_RETURN_OUT: '退供出库', ORDER_DISPATCH: '销售出库', RETURN_RESTOCK: '客户退货入库', STOCKTAKE_GAIN: '盘盈', STOCKTAKE_LOSS: '盘亏', STOCKTAKE: '库存盘点' }
export const inventoryDate = (s: string | null) => s ? s.replace('T', ' ').slice(0, 16) : '—'
export function sourcePath(m: Movement) { if (!m.sourceId) return ''; return m.sourceType === 'DOCUMENT' ? '/admin/inventory/documents/' + m.sourceId : m.sourceType === 'ORDER' ? '/admin/orders?orderId=' + m.sourceId : m.sourceType === 'AFTER_SALE' ? '/admin/after-sales?afterSaleId=' + m.sourceId : '' }
