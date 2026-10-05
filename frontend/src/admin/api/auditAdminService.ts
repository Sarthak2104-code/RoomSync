import apiClient from '@/api/client'
import type { AuditLogPageResponse } from '@/types/audit'

export interface GetAuditLogsParams {
  actorUserId?: number | null
  action?: string | null
  entityType?: string | null
  locationId?: number | null
  roomId?: number | null
  bookingId?: number | null
  startDate?: string | null
  endDate?: string | null
  page?: number
  size?: number
  sort?: string
}

export const auditAdminService = {
  /**
   * Fetch paginated administrative audit logs with filters (ADMIN only)
   */
  async getAuditLogs(params?: GetAuditLogsParams): Promise<AuditLogPageResponse> {
    const queryParams: Record<string, unknown> = {
      page: params?.page ?? 0,
      size: params?.size ?? 10,
      sort: params?.sort ?? 'createdAt,desc',
    }

    if (params?.actorUserId != null) queryParams.actorUserId = params.actorUserId
    if (params?.action) queryParams.action = params.action
    if (params?.entityType) queryParams.entityType = params.entityType
    if (params?.locationId != null) queryParams.locationId = params.locationId
    if (params?.roomId != null) queryParams.roomId = params.roomId
    if (params?.bookingId != null) queryParams.bookingId = params.bookingId
    if (params?.startDate) queryParams.startDate = params.startDate
    if (params?.endDate) queryParams.endDate = params.endDate

    const response = await apiClient.get<AuditLogPageResponse>('/admin/audit-logs', {
      params: queryParams,
    })
    return response.data
  },
}

export default auditAdminService
