import apiClient from './client'
import type {
  AdminRequestResponse,
  CreateRecurringBookingPayload,
  RecurringConfirmationResponse,
  RecurringOccurrenceResult,
  RecurringPreviewResponse,
  RecurringSeriesResponse,
} from '@/types/recurring'
import type { BookingResponse } from '@/types/booking'

export const recurringBookingService = {
  /**
   * Evaluates recurring series preview with occurrence list and conflict detection.
   */
  async previewSeries(payload: CreateRecurringBookingPayload): Promise<RecurringPreviewResponse> {
    const response = await apiClient.post<RecurringPreviewResponse>(
      '/bookings/recurring/preview',
      payload,
    )
    return response.data
  },

  /**
   * Atomically creates and confirms a recurring series with optional Idempotency-Key.
   */
  async createAndConfirmSeries(
    payload: CreateRecurringBookingPayload,
    idempotencyKey?: string,
  ): Promise<RecurringConfirmationResponse> {
    const headers: Record<string, string> = {}
    if (idempotencyKey && idempotencyKey.trim().length > 0) {
      headers['Idempotency-Key'] = idempotencyKey
    }

    const response = await apiClient.post<RecurringConfirmationResponse>(
      '/bookings/recurring',
      payload,
      { headers },
    )
    return response.data
  },

  /**
   * Retrieves recurring series details by ID.
   */
  async getSeries(id: number): Promise<RecurringSeriesResponse> {
    const response = await apiClient.get<RecurringSeriesResponse>(`/bookings/recurring/${id}`)
    return response.data
  },

  /**
   * Cancels/deletes an individual confirmed occurrence in a recurring series.
   */
  async cancelOccurrence(
    seriesId: number,
    occurrenceIndex: number,
    reason?: string,
  ): Promise<void> {
    const params: Record<string, string> = {}
    if (reason && reason.trim().length > 0) {
      params.reason = reason.trim()
    }

    await apiClient.delete(
      `/bookings/recurring/${seriesId}/occurrences/${occurrenceIndex}`,
      { params },
    )
  },

  /**
   * Persistently skips an unresolved conflict occurrence in a recurring series.
   */
  async skipOccurrence(
    seriesId: number,
    occurrenceIndex: number,
    reason?: string,
    idempotencyKey?: string,
  ): Promise<RecurringOccurrenceResult> {
    const headers: Record<string, string> = {}
    if (idempotencyKey && idempotencyKey.trim().length > 0) {
      headers['Idempotency-Key'] = idempotencyKey
    }

    const response = await apiClient.post<RecurringOccurrenceResult>(
      `/bookings/recurring/${seriesId}/occurrences/${occurrenceIndex}/skip`,
      { reason },
      { headers },
    )
    return response.data
  },

  /**
   * Resolves a conflicted occurrence by booking an alternate available room.
   */
  async resolveAlternateRoom(
    seriesId: number,
    occurrenceIndex: number,
    alternateRoomId: number,
  ): Promise<BookingResponse> {
    const response = await apiClient.post<BookingResponse>(
      `/bookings/recurring/${seriesId}/occurrences/${occurrenceIndex}/alternate-room`,
      { alternateRoomId },
    )
    return response.data
  },

  /**
   * Submits an admin request for an individual occurrence in a recurring series.
   */
  async contactAdmin(
    seriesId: number,
    occurrenceIndex: number,
    message: string,
  ): Promise<AdminRequestResponse> {
    const response = await apiClient.post<AdminRequestResponse>(
      `/bookings/recurring/${seriesId}/occurrences/${occurrenceIndex}/contact-admin`,
      { message: message.trim() },
    )
    return response.data
  },
}
