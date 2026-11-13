import http, { type ApiResponse } from './http'

export interface BuyerProfile {
  id: number
  username: string
  phone: string
  nickname: string
  avatarUrl: string | null
}

export interface UpdateBuyerProfilePayload {
  nickname: string
  phone: string
}

export interface AvatarReference {
  avatarUrl: string
}

export interface ChangeBuyerPasswordPayload {
  currentPassword: string
  newPassword: string
}

export interface DeliveryAddress {
  id: number
  recipientName: string
  phone: string
  province: string
  city: string
  district: string
  detailAddress: string
  addressTag: 'HOME' | 'COMPANY'
  defaultAddress: boolean
}

export interface DeliveryAddressPayload {
  recipientName: string
  phone: string
  province: string
  city: string
  district: string
  detailAddress: string
  addressTag: 'HOME' | 'COMPANY'
}

export interface CreateDeliveryAddressPayload extends DeliveryAddressPayload {
  defaultAddress: boolean
}

export async function getBuyerProfile() {
  const response = await http.get<ApiResponse<BuyerProfile>>('/api/v1/buyer/profile')
  return response.data.data
}

export async function updateBuyerProfile(payload: UpdateBuyerProfilePayload) {
  const response = await http.patch<ApiResponse<BuyerProfile>>('/api/v1/buyer/profile', payload)
  return response.data.data
}

export async function uploadBuyerAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiResponse<AvatarReference>>('/api/v1/buyer/profile/avatar', form)
  return response.data.data
}

export async function changeBuyerPassword(payload: ChangeBuyerPasswordPayload) {
  await http.put<ApiResponse<null>>('/api/v1/buyer/profile/password', payload)
}

export async function listDeliveryAddresses() {
  const response = await http.get<ApiResponse<DeliveryAddress[]>>('/api/v1/buyer/addresses')
  return response.data.data
}

export async function createDeliveryAddress(payload: CreateDeliveryAddressPayload) {
  const response = await http.post<ApiResponse<DeliveryAddress>>('/api/v1/buyer/addresses', payload)
  return response.data.data
}

export async function updateDeliveryAddress(id: number, payload: DeliveryAddressPayload) {
  const response = await http.put<ApiResponse<DeliveryAddress>>(`/api/v1/buyer/addresses/${id}`, payload)
  return response.data.data
}

export async function deleteDeliveryAddress(id: number) {
  await http.delete<ApiResponse<null>>(`/api/v1/buyer/addresses/${id}`)
}

export async function setDefaultDeliveryAddress(id: number) {
  const response = await http.put<ApiResponse<DeliveryAddress>>(`/api/v1/buyer/addresses/${id}/default`)
  return response.data.data
}
