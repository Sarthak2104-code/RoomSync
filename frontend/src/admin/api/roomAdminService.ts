import apiClient from '@/api/client'
import type { RoomPageResponse, RoomResponse, AmenityPageResponse } from '@/types/room'

export interface CreateRoomDto {
  locationId: number
  name: string
  capacity: number
  description?: string
}

export interface UpdateRoomDto {
  name: string
  capacity: number
  description?: string
}

export interface GetAdminRoomsParams {
  locationId?: number | null
  page?: number
  size?: number
  sort?: string
}

export const roomAdminService = {
  /**
   * Fetch paginated list of rooms
   */
  async getRooms(params?: GetAdminRoomsParams): Promise<RoomPageResponse> {
    const queryParams: Record<string, unknown> = {
      page: params?.page ?? 0,
      size: params?.size ?? 100,
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

  /**
   * Fetch single room by ID
   */
  async getRoomById(id: number): Promise<RoomResponse> {
    const response = await apiClient.get<RoomResponse>(`/rooms/${id}`)
    return response.data
  },

  /**
   * Create a new room (ADMIN only)
   */
  async createRoom(data: CreateRoomDto): Promise<RoomResponse> {
    const response = await apiClient.post<RoomResponse>('/rooms', data)
    return response.data
  },

  /**
   * Update an existing room (ADMIN only)
   */
  async updateRoom(id: number, data: UpdateRoomDto): Promise<RoomResponse> {
    const response = await apiClient.put<RoomResponse>(`/rooms/${id}`, data)
    return response.data
  },

  /**
   * Administratively lock a room (ADMIN only)
   */
  async lockRoom(id: number): Promise<RoomResponse> {
    const response = await apiClient.patch<RoomResponse>(`/rooms/${id}/lock`)
    return response.data
  },

  /**
   * Administratively unlock a room (ADMIN only)
   */
  async unlockRoom(id: number): Promise<RoomResponse> {
    const response = await apiClient.patch<RoomResponse>(`/rooms/${id}/unlock`)
    return response.data
  },

  /**
   * Activate an inactive room (ADMIN only)
   */
  async activateRoom(id: number): Promise<RoomResponse> {
    const response = await apiClient.patch<RoomResponse>(`/rooms/${id}/activate`)
    return response.data
  },

  /**
   * Deactivate an active room (ADMIN only)
   */
  async deactivateRoom(id: number): Promise<void> {
    await apiClient.patch<void>(`/rooms/${id}/deactivate`)
  },

  /**
   * Fetch amenities catalog for reference
   */
  async getAmenities(): Promise<AmenityPageResponse> {
    const response = await apiClient.get<AmenityPageResponse>('/amenities', {
      params: { page: 0, size: 100, sort: 'name,asc' },
    })
    return response.data
  },
}

export default roomAdminService
