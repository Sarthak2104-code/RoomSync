import type { PageResponse } from './api'

export type OperationStatus =
  | 'REQUESTED'
  | 'PLANNING'
  | 'NEEDS_CLARIFICATION'
  | 'VALIDATED'
  | 'AWAITING_CONFIRMATION'
  | 'CONFIRMED'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CONFLICT'
  | 'PARTIAL_SUCCESS'
  | 'TIMEOUT_UNKNOWN'
  | 'CANCELLED'

export interface OperationResponse {
  operationId: string
  operationType: string
  actingUserId?: number | null
  status: OperationStatus
  resourceType?: string | null
  resourceId?: string | null
  requestHash?: string | null
  result?: Record<string, unknown> | null
  errorCode?: string | null
  errorMessage?: string | null
  correlationId?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  expiresAt?: string | null
}

export type OperationPageResponse = PageResponse<OperationResponse>
