export interface RoomUtilizationResponse {
  roomId: number
  roomName: string
  locationId: number
  locationName: string
  locationTimezone?: string
  reportingPeriodStart?: string
  reportingPeriodEnd?: string
  totalAvailableMinutes: number
  totalBookedMinutes: number
  utilizationPercentage: number
  bookingCount: number
}

export interface UtilizationReportResponse {
  startDate: string
  endDate: string
  rooms: RoomUtilizationResponse[]
  overallUtilizationPercentage: number
}
