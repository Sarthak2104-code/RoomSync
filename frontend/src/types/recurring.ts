export type RecurrenceFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type BookingSeriesStatus = 'ACTIVE' | 'PARTIALLY_CONFIRMED' | 'CANCELLED' | 'COMPLETED'
export type OccurrenceStatus = 'CONFIRMED' | 'CONFLICT' | 'SKIPPED' | 'CANCELLED' | string

export interface CreateRecurringBookingPayload {
  roomId: number
  seriesName?: string
  frequency: RecurrenceFrequency
  startDate: string
  endDate?: string
  occurrenceCount?: number
  startLocalTime: string
  endLocalTime: string
  daysOfWeek?: string[]
  dayOfMonth?: number
  reason: string
}

export interface RecurringOccurrencePreview {
  occurrenceIndex: number
  date: string
  startTime: string
  endTime: string
  availability: 'AVAILABLE' | 'CONFLICT' | string
  conflictReason?: string
}

export interface RecurringPreviewResponse {
  seriesName: string
  roomId: number
  roomName: string
  frequency: RecurrenceFrequency
  totalOccurrences: number
  availableCount: number
  conflictCount: number
  occurrences: RecurringOccurrencePreview[]
}

export interface RecurringOccurrenceResult {
  occurrenceIndex: number
  date: string
  bookingId?: number
  status: OccurrenceStatus
  roomId?: number
  roomName?: string
  startTime: string
  endTime: string
  reason?: string
  conflictReason?: string
  operationId?: string | null
}

export interface RecurringConfirmationResponse {
  seriesId: number
  seriesName: string
  roomId: number
  roomName: string
  frequency: RecurrenceFrequency
  seriesStatus: BookingSeriesStatus
  totalOccurrences: number
  confirmedCount: number
  conflictCount: number
  skippedCount?: number
  occurrences: RecurringOccurrenceResult[]
}

export interface RecurringSeriesResponse {
  id: number
  userId?: number
  userWissenId?: string | null
  userName?: string | null
  seriesName?: string
  name?: string
  frequency: RecurrenceFrequency
  startDate?: string
  endDate?: string
  occurrenceCount?: number
  startLocalTime?: string
  endLocalTime?: string
  timezone?: string
  daysOfWeek?: string
  dayOfMonth?: number
  status: BookingSeriesStatus
  totalOccurrences: number
  confirmedCount?: number
  conflictCount?: number
  skippedCount?: number
  activeOccurrences?: number
  cancelledOccurrences?: number
  occurrences?: RecurringOccurrenceResult[]
  createdAt: string
  updatedAt: string
}

export interface SkipOccurrencePayload {
  reason?: string
}

export interface ResolveAlternateRoomPayload {
  alternateRoomId: number
}

export interface ContactAdminOccurrencePayload {
  message: string
}

export interface AdminRequestResponse {
  id: number
  userId: number
  userEmail: string
  requestType: string
  status: string
  message: string
  seriesId?: number
  occurrenceIndex?: number
  createdAt: string
  updatedAt: string
}
