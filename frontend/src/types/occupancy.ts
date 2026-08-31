import type { PageResponse } from './api'

export type OccupancyStatus = 'AVAILABLE' | 'OCCUPIED' | 'LOCKED'

export interface CurrentBookingSummary {
  bookingId: number
  userId: number
  userWissenId?: string | null
  userName?: string
  userEmail?: string
  startTime: string
  endTime: string
  reason?: string
}

export interface RoomOccupancyResponse {
  roomId: number
  roomName: string
  capacity: number
  locationId: number
  locationName: string
  locationTimezone?: string
  administrativeState: 'AVAILABLE' | 'LOCKED'
  active: boolean
  occupancyStatus: OccupancyStatus
  currentBooking?: CurrentBookingSummary | null
}

export type RoomOccupancyPageResponse = PageResponse<RoomOccupancyResponse>
