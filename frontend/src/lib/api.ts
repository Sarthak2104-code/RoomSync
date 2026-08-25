import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

function generateUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export interface ApiErrorResponse {
  message: string
  status?: number
  correlationId?: string
  data?: unknown
}

export const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (!config.headers.has('X-Correlation-Id')) {
      config.headers.set('X-Correlation-Id', generateUUID())
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  },
)

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const normalizedError: ApiErrorResponse = {
      message: error.message || 'An unexpected error occurred',
      status: error.response?.status,
      correlationId: (error.response?.headers?.['x-correlation-id'] as string) || undefined,
      data: error.response?.data,
    }
    return Promise.reject(normalizedError)
  },
)

export default api
