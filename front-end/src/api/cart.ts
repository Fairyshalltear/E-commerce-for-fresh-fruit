import http, { type ApiResponse } from './http'

export interface CartItem {
  id: number
  skuId: number
  productName: string
  specification: string
  imageUrl: string | null
  quantity: number
  selected: boolean
  currentUnitPrice: string
  acceptedUnitPrice: string
  availableStock: number
  purchasable: boolean
  priceChanged: boolean
  unavailableReason: string | null
  lineAmount: string
}

export interface Cart {
  items: CartItem[]
  selectedCount: number
  selectedSubtotal: string
  shippingFee: string
  total: string
}

export interface CheckoutValidation {
  valid: boolean
  cart: Cart
  attentionItemIds: number[]
  message: string
}

const path = '/api/v1/buyer/cart'
export async function getCart() { return (await http.get<ApiResponse<Cart>>(path)).data.data }
export async function addCartItem(skuId: number, quantity: number) { return (await http.post<ApiResponse<Cart>>(path, { skuId, quantity })).data.data }
export async function updateCartQuantity(itemId: number, quantity: number) { return (await http.patch<ApiResponse<Cart>>(`${path}/items/${itemId}/quantity`, { quantity })).data.data }
export async function selectCartItem(itemId: number, selected: boolean) { return (await http.patch<ApiResponse<Cart>>(`${path}/items/${itemId}/selection`, { selected })).data.data }
export async function selectAllCartItems(selected: boolean) { return (await http.put<ApiResponse<Cart>>(`${path}/selection`, { selected })).data.data }
export async function removeCartItem(itemId: number) { return (await http.delete<ApiResponse<Cart>>(`${path}/items/${itemId}`)).data.data }
export async function acceptCartPrice(itemId: number, price: string) { return (await http.put<ApiResponse<Cart>>(`${path}/items/${itemId}/accepted-price`, { price })).data.data }
export async function validateCartCheckout() { return (await http.post<ApiResponse<CheckoutValidation>>(`${path}/checkout-validation`)).data.data }
