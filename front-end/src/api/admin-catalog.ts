import http, { type ApiResponse } from './http'
import type { Category, ImageRef, Page, ProductCard, SearchQuery } from './catalog'
export interface AdminSku { id?: number; specification: string; price: string; onHand: number; reserved?: number; availableStock?: number; lowStockThreshold: number; lowStock?: boolean; status: number; inventoryVersion?: number }
export interface AdminProduct { id: number; name: string; categoryId: number; description: string; origin: string; taste: string; storageMethod: string; status: number; recommended: boolean; recommendSort: number; version: number; images: ImageRef[]; retainedImages: ImageRef[]; skus: AdminSku[] }
export interface SkuPayload { id?: number; specification: string; price: string; status: number; onHand?: number }
export interface ProductPayload { name: string; categoryId: number; description: string; origin: string; taste: string; storageMethod: string; recommended: boolean; recommendSort: number; version?: number; imageIds: number[]; skus: SkuPayload[] }
export interface AdminCard { product: ProductCard; status: number; recommended: boolean; recommendSort: number; version: number; updatedAt: string }
export interface CategoryPayload { name: string; parentId: number | null; status: number; sortOrder: number }
export const adminCategories = async () => (await http.get<ApiResponse<Category[]>>('/api/v1/admin/categories')).data.data
export const saveCategory = async (id: number | null, payload: CategoryPayload) => (id === null ? await http.post<ApiResponse<Category>>('/api/v1/admin/categories', payload) : await http.put<ApiResponse<Category>>('/api/v1/admin/categories/' + id, payload)).data.data
export const deleteCategory = async (id: number) => { await http.delete('/api/v1/admin/categories/' + id) }
export const adminProducts = async (params: SearchQuery & { status?: number; recommended?: boolean }) => (await http.get<ApiResponse<Page<AdminCard>>>('/api/v1/admin/products', { params })).data.data
export const adminProduct = async (id: number) => (await http.get<ApiResponse<AdminProduct>>('/api/v1/admin/products/' + id)).data.data
export const saveProduct = async (id: number | null, payload: ProductPayload) => (id === null ? await http.post<ApiResponse<AdminProduct>>('/api/v1/admin/products', payload) : await http.put<ApiResponse<AdminProduct>>('/api/v1/admin/products/' + id, payload)).data.data
export const changeStatus = async (id: number, status: number, version: number) => (await http.patch<ApiResponse<AdminProduct>>('/api/v1/admin/products/' + id + '/status', { status, version })).data.data
export const deleteProduct = async (id: number, version: number) => { await http.delete('/api/v1/admin/products/' + id, { params: { version } }) }
export async function uploadProductImage(file: File) { const data = new FormData(); data.append('file', file); return (await http.post<ApiResponse<ImageRef>>('/api/v1/admin/product-images', data)).data.data }
