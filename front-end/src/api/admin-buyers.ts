import http, { type ApiResponse } from './http'
import type { Page } from './catalog'

export interface BuyerAccount {
  id: number
  username: string
  phone: string
  nickname: string | null
  avatarUrl: string | null
  status: number
  createdAt: string
}

export async function adminBuyers(params: { keyword?: string; status?: number; page?: number; size?: number }) {
  return (await http.get<ApiResponse<Page<BuyerAccount>>>('/api/v1/admin/buyers', { params })).data.data
}
export async function adminBuyer(id: number) {
  return (await http.get<ApiResponse<BuyerAccount>>(`/api/v1/admin/buyers/${id}`)).data.data
}
export async function setBuyerStatus(id: number, status: number) {
  return (await http.patch<ApiResponse<BuyerAccount>>(`/api/v1/admin/buyers/${id}/status`, { status })).data.data
}
export async function resetBuyerPassword(id: number, temporaryPassword: string) {
  await http.post<ApiResponse<null>>(`/api/v1/admin/buyers/${id}/password-reset`, { temporaryPassword })
}
