import apiClient from './client'
import type { AmenityPageResponse, AmenityResponse } from '@/types/room'

export interface GetAmenitiesParams {
  page?: number
  size?: number
  sort?: string
}

export const amenityService = {
  async getAmenities(params?: GetAmenitiesParams): Promise<AmenityPageResponse> {
    const response = await apiClient.get<AmenityPageResponse>('/amenities', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 50,
        sort: params?.sort ?? 'name,asc',
      },
    })
    return response.data
  },

  async getAmenityById(id: number): Promise<AmenityResponse> {
    const response = await apiClient.get<AmenityResponse>(`/amenities/${id}`)
    return response.data
  },
}

export default amenityService
