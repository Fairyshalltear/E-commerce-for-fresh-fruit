import axios from 'axios'
import type { ApiResponse } from './http'
export interface Page<T> { items: T[]; total: number; page: number; size: number }
export interface Category { id: number; parentId: number | null; name: string; sortOrder: number; status: number; children: Category[] }
export interface ImageRef { id: number; url: string }
export interface ProductCard { id: number; name: string; categoryId: number; categoryName: string; mainImage: string | null; minPrice: string | null; maxPrice: string | null; availableStock: number; soldOut: boolean }
export interface Sku { id: number; specification: string; price: string; availableStock: number }
export interface ReviewSummary { count: number; averageRating: number | null }
export interface ProductConsultation { sweetnessDescription: string | null; seasonalMonths: number[]; giftBox: boolean; useCases: string[] }
export const productUseCases: Record<string, string> = { DAILY: '日常食用', ELDER_GIFT: '长辈送礼', FRIEND_GIFT: '亲友送礼', BUSINESS_GIFT: '商务送礼', FAMILY: '家庭分享' }
export interface ProductDetail extends ProductConsultation { id: number; name: string; categoryId: number; categoryPath: Category[]; description: string; origin: string; taste: string; storageMethod: string; images: ImageRef[]; skus: Sku[]; reviewSummary: ReviewSummary }
export interface Review { id: number; rating: number; content: string; specification: string; createdAt: string; nickname: string }
export type Sort = 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC'
export interface SearchQuery { keyword?: string; categoryId?: number; minPrice?: string; maxPrice?: string; sort?: Sort; page?: number; size?: number }
const publicHttp = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '', timeout: 10000 })
export async function categories() { return (await publicHttp.get<ApiResponse<Category[]>>('/api/v1/public/categories')).data.data }
export async function recommendations() { return (await publicHttp.get<ApiResponse<ProductCard[]>>('/api/v1/public/products/recommendations')).data.data }
export async function searchProducts(params: SearchQuery) { return (await publicHttp.get<ApiResponse<Page<ProductCard>>>('/api/v1/public/products', { params })).data.data }
export async function productDetail(id: number) { return (await publicHttp.get<ApiResponse<ProductDetail>>('/api/v1/public/products/' + id)).data.data }
export async function productReviews(id: number, page = 1) { return (await publicHttp.get<ApiResponse<Page<Review>>>('/api/v1/public/products/' + id + '/reviews', { params: { page } })).data.data }
export function price(value: string | null) { return value === null ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
export function imageUrl(url: string | null) {
  if (!url) return ''
  const base = import.meta.env.VITE_API_BASE_URL
  return base && /^https?:/.test(base) ? new URL(url, base).href : url
}
export function errorMessage(error: unknown) {
  return axios.isAxiosError<{ message?: string }>(error) ? error.response?.data?.message ?? '无法连接服务器，请稍后重试。' : '操作失败，请稍后重试。'
}
