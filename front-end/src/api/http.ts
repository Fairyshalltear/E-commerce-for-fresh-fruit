import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const rawSession = localStorage.getItem('fresh-fruit-session')
  if (rawSession) {
    try {
      const session = JSON.parse(rawSession) as { accessToken?: string }
      if (session.accessToken) {
        config.headers.Authorization = `Bearer ${session.accessToken}`
      }
    } catch {
      localStorage.removeItem('fresh-fruit-session')
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      localStorage.removeItem('fresh-fruit-session')
      window.dispatchEvent(new Event('fresh-fruit-auth-expired'))
    }
    return Promise.reject(error)
  },
)

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export default http
