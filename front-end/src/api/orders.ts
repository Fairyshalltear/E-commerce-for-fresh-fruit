import http, { type ApiResponse } from './http'

export type OrderStatus = 'PENDING_PAYMENT' | 'PENDING_SHIPMENT' | 'PENDING_RECEIPT' | 'COMPLETED' | 'CANCELLED'
export type OrderFilter = 'ALL' | OrderStatus
export interface PurchaseItem { skuId: number; quantity: number }
export interface CheckoutRequest { addressId: number; requestKey: string; items?: PurchaseItem[] }
export interface CheckoutLine { skuId: number; productName: string; specification: string; imageUrl: string | null; quantity: number; unitPrice: string; lineAmount: string; availableStock: number; purchasable: boolean; unavailableReason: string | null; priceChanged: boolean }
export interface CheckoutPreview { items: CheckoutLine[]; addressId: number; recipientName: string; phone: string; formattedAddress: string; subtotal: string; shippingFee: string; total: string; valid: boolean; message: string }
export interface OrderItem { id: number; productId: number; skuId: number; productName: string; specification: string; imageUrl: string | null; quantity: number; shippedQuantity: number; refundedQuantity: number; unitPrice: string; lineAmount: string; reviewed: boolean }
export interface StatusEvent { fromStatus: string | null; toStatus: string; eventType: string; actorType: string; note: string; createdAt: string }
export interface TrackingEvent { description: string; occurredAt: string }
export interface OrderSummary { id: number; orderNo: string; status: OrderStatus; itemCount: number; totalAmount: string; createdAt: string }
export interface OrderDetail { id: number; orderNo: string; status: OrderStatus; recipientName: string; phone: string; province: string; city: string; district: string; detailAddress: string; subtotal: string; shippingFee: string; totalAmount: string; createdAt: string; paidAt: string | null; shippedAt: string | null; completedAt: string | null; carrier: string | null; trackingNumber: string | null; items: OrderItem[]; statusEvents: StatusEvent[]; trackingEvents: TrackingEvent[] }
export interface Page<T> { items: T[]; total: number; page: number; size: number }
export interface AfterSaleItem { orderItemId: number; productName: string; specification: string; requestedQuantity: number; refundQuantity: number; restockQuantity: number; unitPrice: string }
export interface AfterSale { id: number; requestNo: string; orderId: number; orderNo: string; type: 'REFUND' | 'RETURN'; status: 'PENDING_REVIEW' | 'WAITING_RETURN' | 'WAITING_RECEIPT' | 'COMPLETED' | 'REJECTED'; reason: string; description: string | null; requestedAmount: string; reviewReason: string | null; returnCarrier: string | null; returnTrackingNumber: string | null; createdAt: string; reviewedAt: string | null; receivedAt: string | null; items: AfterSaleItem[] }
export interface AdminReview { id: number; productId: number; skuId: number; orderItemId: number; buyerId: number; buyerNickname: string; rating: number; content: string | null; specification: string; createdAt: string; deleted: boolean; deletedBy: number | null; deletionReason: string | null; deletedAt: string | null }

const buyer = '/api/v1/buyer/orders'
const admin = '/api/v1/admin/orders'
const afterSales = '/api/v1/buyer/after-sales'
export async function checkoutPreview(payload: CheckoutRequest) { return (await http.post<ApiResponse<CheckoutPreview>>(`${buyer}/checkout-preview`, payload)).data.data }
export async function submitOrder(payload: CheckoutRequest) { return (await http.post<ApiResponse<OrderDetail>>(buyer, payload)).data.data }
export async function listOrders(status: OrderFilter = 'ALL', page = 1, size = 12) { return (await http.get<ApiResponse<Page<OrderSummary>>>(buyer, { params: { status, page, size } })).data.data }
export async function getOrder(id: number) { return (await http.get<ApiResponse<OrderDetail>>(`${buyer}/${id}`)).data.data }
export async function payOrder(id: number, channel: 'ALIPAY' | 'WECHAT', result: 'SUCCESS' | 'FAILED' | 'CANCELLED') { return (await http.post<ApiResponse<OrderDetail>>(`${buyer}/${id}/payment`, { channel, result, requestKey: `pay-${id}-${crypto.randomUUID()}` })).data.data }
export async function cancelOrder(id: number) { return (await http.post<ApiResponse<OrderDetail>>(`${buyer}/${id}/cancel`)).data.data }
export async function confirmReceipt(id: number) { return (await http.post<ApiResponse<OrderDetail>>(`${buyer}/${id}/confirm-receipt`)).data.data }
export async function submitAfterSale(orderId: number, payload: { type: 'REFUND' | 'RETURN'; reason: string; description?: string; items: { orderItemId: number; quantity: number }[] }) { return (await http.post<ApiResponse<AfterSale>>(`${buyer}/${orderId}/after-sales`, payload)).data.data }
export async function listBuyerAfterSales(page = 1, size = 12) { return (await http.get<ApiResponse<Page<AfterSale>>>(afterSales, { params: { page, size } })).data.data }
export async function returnShipment(id: number, carrier: string, trackingNumber: string) { return (await http.post<ApiResponse<AfterSale>>(`${afterSales}/${id}/return-shipment`, { carrier, trackingNumber })).data.data }
export async function submitReview(orderItemId: number, rating: number, content: string) { await http.post<ApiResponse<null>>('/api/v1/buyer/reviews', { orderItemId, rating, content }) }

export async function listAdminOrders(params: Record<string, unknown>) { return (await http.get<ApiResponse<Page<OrderSummary>>>(admin, { params })).data.data }
export async function getAdminOrder(id: number) { return (await http.get<ApiResponse<OrderDetail>>(`${admin}/${id}`)).data.data }
export async function dispatchOrder(id: number, carrier: string, trackingNumber: string) { return (await http.post<ApiResponse<OrderDetail>>(`${admin}/${id}/dispatch`, { carrier, trackingNumber })).data.data }
export async function addTrackingEvent(id: number, description: string, occurredAt: string) { return (await http.post<ApiResponse<OrderDetail>>(`${admin}/${id}/tracking-events`, { description, occurredAt })).data.data }
export async function listAdminAfterSales(params: Record<string, unknown>) { return (await http.get<ApiResponse<Page<AfterSale>>>('/api/v1/admin/after-sales', { params })).data.data }
export async function reviewAfterSale(id: number, approved: boolean, reason: string) { return (await http.post<ApiResponse<AfterSale>>(`/api/v1/admin/after-sales/${id}/review`, { approved, reason })).data.data }
export async function receiveAfterSale(id: number, items: { orderItemId: number; refundQuantity: number; restockQuantity: number }[]) { return (await http.post<ApiResponse<AfterSale>>(`/api/v1/admin/after-sales/${id}/receive`, { items })).data.data }
export async function listAdminReviews(params: Record<string, unknown>) { return (await http.get<ApiResponse<Page<AdminReview>>>('/api/v1/admin/reviews', { params })).data.data }
export async function deleteAdminReview(id: number, reason: string) { await http.post<ApiResponse<null>>(`/api/v1/admin/reviews/${id}/delete`, { reason }) }
