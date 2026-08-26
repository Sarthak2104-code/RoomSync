import apiClient from './client'
import type { LocationPageResponse, LocationResponse } from '@/types/location'

export interface GetLocationsParams {
  page?: number
  size?: number
  sort?: string
}

export const locationService = {
  async getLocations(params?: GetLocationsParams): Promise<LocationPageResponse> {
    const response = await apiClient.get<LocationPageResponse>('/locations', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 20,
        sort: params?.sort ?? 'name,asc',
      },
    })
    return response.data
  },

  async getLocationById(id: number): Promise<LocationResponse> {
    const response = await apiClient.get<LocationResponse>(`/locations/${id}`)
    return response.data
  },
}

export default locationService
