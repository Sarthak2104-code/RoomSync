import apiClient from './client'
import type { RoomPageResponse, RoomResponse } from '@/types/room'

export interface GetRoomsParams {
  locationId?: number | null
  page?: number
  size?: number
  sort?: string
}

export const roomService = {
  async getRooms(params?: GetRoomsParams): Promise<RoomPageResponse> {
    const queryParams: Record<string, unknown> = {
      page: params?.page ?? 0,
      size: params?.size ?? 20,
      sort: params?.sort ?? 'name,asc',
    }

    if (params?.locationId != null) {
      queryParams.locationId = params.locationId
    }

    const response = await apiClient.get<RoomPageResponse>('/rooms', {
      params: queryParams,
    })
    return response.data
  },

  async getRoomById(id: number): Promise<RoomResponse> {
    const response = await apiClient.get<RoomResponse>(`/rooms/${id}`)
    return response.data
  },
}

export default roomService
