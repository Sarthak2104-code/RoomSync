import apiClient from '@/api/client'
import type { UtilizationReportResponse } from '@/types/analytics'

export interface GetUtilizationParams {
  startDate?: string
  endDate?: string
  locationId?: number | null
  roomId?: number | null
}

export const analyticsAdminService = {
  /**
   * Fetch room utilization metrics from backend (ADMIN only)
   */
  async getUtilization(params?: GetUtilizationParams): Promise<UtilizationReportResponse> {
    const queryParams: Record<string, unknown> = {}

    if (params?.startDate) {
      queryParams.startDate = params.startDate
    }
    if (params?.endDate) {
      queryParams.endDate = params.endDate
    }
    if (params?.locationId != null) {
      queryParams.locationId = params.locationId
    }
    if (params?.roomId != null) {
      queryParams.roomId = params.roomId
    }

    const response = await apiClient.get<UtilizationReportResponse>('/admin/utilization', {
      params: queryParams,
    })
    return response.data
  },
}

export default analyticsAdminService
