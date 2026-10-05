import apiClient from '@/api/client'
import type { RoomOccupancyPageResponse } from '@/types/occupancy'

export interface GetOccupancyParams {
  locationId?: number | null
  roomId?: number | null
  page?: number
  size?: number
  sort?: string
}

export const occupancyAdminService = {
  /**
   * Fetch current room occupancy states across rooms and locations (ADMIN only)
   */
  async getOccupancy(params?: GetOccupancyParams): Promise<RoomOccupancyPageResponse> {
    const queryParams: Record<string, unknown> = {
      page: params?.page ?? 0,
      size: params?.size ?? 100,
      sort: params?.sort ?? 'name,asc',
    }

    if (params?.locationId != null) {
      queryParams.locationId = params.locationId
    }
    if (params?.roomId != null) {
      queryParams.roomId = params.roomId
    }

    const response = await apiClient.get<RoomOccupancyPageResponse>('/admin/occupancy', {
      params: queryParams,
    })
    return response.data
  },
}

export default occupancyAdminService
