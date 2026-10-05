import type { PageResponse } from './api'

export interface AuditLogResponse {
  id: number
  actorUserId?: number | null
  actorWissenId?: string | null
  actorName?: string | null
  actorEmail?: string | null
  affectedUserId?: number | null
  affectedUserWissenId?: string | null
  affectedUserName?: string | null
  affectedUserEmail?: string | null
  action: string
  entityType: string
  entityId?: string | null
  locationId?: number | null
  locationName?: string | null
  roomId?: number | null
  roomName?: string | null
  bookingId?: number | null
  result?: string | null
  correlationId?: string | null
  createdAt: string
}

export type AuditLogPageResponse = PageResponse<AuditLogResponse>
