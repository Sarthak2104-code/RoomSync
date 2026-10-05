import apiClient from '@/api/client'
import type { LocationPageResponse, LocationResponse } from '@/types/location'

export interface CreateLocationDto {
  name: string
  code: string
}

export interface UpdateLocationDto {
  name: string
  code: string
}

export interface GetAdminLocationsParams {
  page?: number
  size?: number
  sort?: string
}

export const locationAdminService = {
  /**
   * Fetch paginated locations list
   */
  async getLocations(params?: GetAdminLocationsParams): Promise<LocationPageResponse> {
    const response = await apiClient.get<LocationPageResponse>('/locations', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 50,
        sort: params?.sort ?? 'name,asc',
      },
    })
    return response.data
  },

  /**
   * Fetch single location by ID
   */
  async getLocationById(id: number): Promise<LocationResponse> {
    const response = await apiClient.get<LocationResponse>(`/locations/${id}`)
    return response.data
  },

  /**
   * Create a new location (ADMIN only)
   */
  async createLocation(data: CreateLocationDto): Promise<LocationResponse> {
    const response = await apiClient.post<LocationResponse>('/locations', data)
    return response.data
  },

  /**
   * Update an existing location (ADMIN only)
   */
  async updateLocation(id: number, data: UpdateLocationDto): Promise<LocationResponse> {
    const response = await apiClient.put<LocationResponse>(`/locations/${id}`, data)
    return response.data
  },

  /**
   * Activate an inactive location (ADMIN only)
   */
  async activateLocation(id: number): Promise<LocationResponse> {
    const response = await apiClient.patch<LocationResponse>(`/locations/${id}/activate`)
    return response.data
  },

  /**
   * Deactivate an active location (ADMIN only)
   */
  async deactivateLocation(id: number): Promise<void> {
    await apiClient.patch<void>(`/locations/${id}/deactivate`)
  },
}

export default locationAdminService
