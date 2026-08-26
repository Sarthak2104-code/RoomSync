import type { PageResponse } from './api'
import type { LocationResponse } from './location'

export type RoomStatus = 'AVAILABLE' | 'LOCKED'

export interface AmenityResponse {
  id: number
  name: string
  icon?: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface RoomResponse {
  id: number
  name: string
  capacity: number
  location: LocationResponse
  description?: string
  status: RoomStatus
  amenities?: AmenityResponse[]
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export type RoomPageResponse = PageResponse<RoomResponse>
export type AmenityPageResponse = PageResponse<AmenityResponse>

export interface RoomSearchFilters {
  locationId?: number | null
  capacity?: number | ''
  amenityIds?: number[]
  date?: string
  startTime?: string
  endTime?: string
}
