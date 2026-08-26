export type BookingStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED'

export interface CreateBookingPayload {
  roomId: number
  startTime: string
  endTime: string
  reason: string
  localDate?: string
  timezone?: string
}

export interface BookingResponse {
  id: number
  roomId: number
  roomName: string
  userId: number
  seriesId?: number | null
  occurrenceIndex?: number | null
  startTime: string
  endTime: string
  reason: string
  cancelledReason?: string | null
  rescheduledFromId?: number | null
  status: BookingStatus
  createdAt: string
  updatedAt: string
}

export interface BookingFormValues {
  date: string
  startTime: string
  endTime: string
  reason: string
}
