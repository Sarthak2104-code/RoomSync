export type BookingStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED'

export interface CreateBookingPayload {
  roomId: number
  startTime: string
  endTime: string
  reason: string
  localDate?: string
  timezone?: string
}

export interface RescheduleBookingPayload {
  roomId?: number
  startTime: string
  endTime: string
}

export interface BookingResponse {
  id: number
  roomId: number
  roomName: string
  userId: number
  wissenId?: string | null
  userWissenId?: string | null
  userName?: string | null
  seriesId?: number | null
  occurrenceIndex?: number | null
  startTime: string
  endTime: string
  reason: string
  cancelledReason?: string | null
  rescheduledFromId?: number | null
  status: BookingStatus
  operationId?: string | null
  createdAt: string
  updatedAt: string
}

export interface BookingFormValues {
  date: string
  startTime: string
  endTime: string
  reason: string
}

export interface BookingDraft {
  roomId: number
  localDate: string
  startTime: string
  endTime: string
  timezone: string
  reason: string
  updatedAt?: number
}
