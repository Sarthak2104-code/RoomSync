/**
 * Generic pagination response structure matching Spring Boot PageResponse<T>.
 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/**
 * Standard backend error representation matching RoomSync Spring Boot ErrorResponse payload.
 */
export interface BackendError {
  status?: number
  errorCode?: string
  message: string
  validationErrors?: Record<string, string>
  correlationId?: string
  path?: string
  timestamp?: string
}

/**
 * Backend standardized error response structure matching RoomSync Spring Boot ErrorResponse.
 */
export interface BackendErrorPayload {
  timestamp?: string
  status?: number
  error?: string
  errorCode?: string
  message?: string
  path?: string
  correlationId?: string
  validationErrors?: Record<string, string>
}

/**
 * Normalized API error representation preserving all backend error metadata.
 */
export interface ApiError {
  status?: number
  code?: string
  message: string
  correlationId?: string
  validationErrors?: Record<string, string>
  path?: string
  timestamp?: string
  rawResponse?: unknown
  isAuthError?: boolean
}
