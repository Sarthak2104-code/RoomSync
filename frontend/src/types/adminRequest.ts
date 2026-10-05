import type { PageResponse } from './api'

export type AdminRequestStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CANCELLED'

export interface AdminRequestResponse {
  id: number
  requesterUserId: number
  requesterUserWissenId?: string | null
  requesterUserName?: string | null
  locationId: number
  roomId?: number | null
  bookingSeriesId?: number | null
  bookingId?: number | null
  requestType: string
  message: string
  status: AdminRequestStatus
  resolvedByUserId?: number | null
  resolvedByUserWissenId?: string | null
  resolvedByUserName?: string | null
  createdAt: string
  updatedAt: string
}

export interface ResolveAdminRequestPayload {
  status: AdminRequestStatus
  message?: string
  resolutionNotes?: string
}

export type AdminRequestPageResponse = PageResponse<AdminRequestResponse>
