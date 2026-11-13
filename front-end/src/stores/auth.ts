import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import http, { type ApiResponse } from '../api/http'

export type AccountRole = 'BUYER' | 'ADMIN'

export interface AuthAccount {
  id: number
  username: string
  displayName: string
  role: AccountRole
}

export interface AuthSession {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  account: AuthAccount
}

interface RegisterPayload {
  username: string
  phone: string
  password: string
  nickname?: string
}

interface BuyerLoginPayload {
  usernameOrPhone: string
  password: string
}

interface AdminLoginPayload {
  username: string
  password: string
}

function loadSession(): AuthSession | null {
  try {
    const raw = localStorage.getItem('fresh-fruit-session')
    if (!raw) return null
    const session = JSON.parse(raw) as AuthSession
    return session.accessToken && session.account?.role ? session : null
  } catch {
    localStorage.removeItem('fresh-fruit-session')
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AuthSession | null>(loadSession())
  const isAuthenticated = computed(() => Boolean(session.value?.accessToken))

  window.addEventListener('fresh-fruit-auth-expired', () => {
    session.value = null
  })

  function saveSession(nextSession: AuthSession) {
    session.value = nextSession
    localStorage.setItem('fresh-fruit-session', JSON.stringify(nextSession))
  }

  async function register(payload: RegisterPayload) {
    const response = await http.post<ApiResponse<AuthSession>>('/api/v1/auth/register', payload)
    saveSession(response.data.data)
  }

  async function loginBuyer(payload: BuyerLoginPayload) {
    const response = await http.post<ApiResponse<AuthSession>>('/api/v1/auth/login', payload)
    saveSession(response.data.data)
  }

  async function loginAdmin(payload: AdminLoginPayload) {
    const response = await http.post<ApiResponse<AuthSession>>('/api/v1/admin/auth/login', payload)
    saveSession(response.data.data)
  }

  function logout() {
    session.value = null
    localStorage.removeItem('fresh-fruit-session')
  }

  function updateDisplayName(displayName: string) {
    if (!session.value) return
    saveSession({
      ...session.value,
      account: { ...session.value.account, displayName },
    })
  }

  return { session, isAuthenticated, register, loginBuyer, loginAdmin, logout, updateDisplayName }
})
