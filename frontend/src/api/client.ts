import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { ApiError, BackendErrorPayload } from '@/types/api'
import { clearAuthSession, getAccessToken, notifyUnauthorized } from '@/utils/token'

export type { ApiError, BackendErrorPayload } from '@/types/api'


/**
 * UUID generator for request correlation tracing.
 */
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

/**
 * Central Axios client for communication between React frontend and Spring Boot backend.
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request Interceptor
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 1. Strict Security Rule: NEVER send X-User-Id from frontend
    if (config.headers) {
      if (typeof config.headers.delete === 'function') {
        config.headers.delete('X-User-Id')
        config.headers.delete('x-user-id')
      } else {
        delete config.headers['X-User-Id']
        delete config.headers['x-user-id']
      }
    }

    // 2. JWT Authorization header: attach Bearer token only when valid token exists
    const token = getAccessToken()
    if (token && token.trim().length > 0) {
      config.headers.set('Authorization', `Bearer ${token}`)
    } else {
      // Ensure no undefined or empty Authorization header is sent
      if (typeof config.headers.delete === 'function') {
        config.headers.delete('Authorization')
      } else {
        delete config.headers['Authorization']
      }
    }

    // 3. Correlation ID: Attach X-Correlation-Id if not already explicitly provided by caller
    if (!config.headers.has('X-Correlation-Id') && !config.headers.has('x-correlation-id')) {
      config.headers.set('X-Correlation-Id', generateUUID())
    }

    // 4. Idempotency-Key: Preserved if explicitly supplied by caller; NOT automatically added to every request

    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  },
)

// Response Interceptor
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status
    const isAuthError = status === 401

    // Central 401 handling: clear locally stored invalid session and notify
    if (isAuthError) {
      clearAuthSession()
      notifyUnauthorized()
    }

    const backendData = error.response?.data as BackendErrorPayload | undefined

    // Preserve backend correlationId, fallback to response header or request header
    const correlationId =
      backendData?.correlationId ||
      (error.response?.headers?.['x-correlation-id'] as string) ||
      (error.response?.headers?.['X-Correlation-Id'] as string) ||
      (error.config?.headers?.['X-Correlation-Id'] as string) ||
      undefined

    // Build normalized ApiError preserving full backend error payload
    const normalizedError: ApiError = {
      status,
      code: backendData?.errorCode || backendData?.error || undefined,
      message: backendData?.message || error.message || 'An unexpected error occurred',
      correlationId,
      validationErrors: backendData?.validationErrors,
      path: backendData?.path,
      timestamp: backendData?.timestamp,
      rawResponse: error.response?.data,
      isAuthError,
    }

    return Promise.reject(normalizedError)
  },
)

export default apiClient
