import http, { type ApiResponse } from './http'
import type { Page, ProductCard } from './catalog'
export interface Favorite { productId: number; nameSnapshot: string; mainImageSnapshot: string | null; available: boolean; unavailableReason: string | null; product: ProductCard | null }
export const favoriteState = async (id: number) => (await http.get<ApiResponse<{ favorited: boolean }>>('/api/v1/buyer/favorites/' + id)).data.data
export const addFavorite = async (id: number) => (await http.put<ApiResponse<{ favorited: boolean }>>('/api/v1/buyer/favorites/' + id)).data.data
export const removeFavorite = async (id: number) => { await http.delete('/api/v1/buyer/favorites/' + id) }
export const listFavorites = async (page = 1) => (await http.get<ApiResponse<Page<Favorite>>>('/api/v1/buyer/favorites', { params: { page } })).data.data
