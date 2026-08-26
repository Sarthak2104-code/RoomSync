import apiClient from './client'
import type { BookingResponse, CreateBookingPayload } from '@/types/booking'

export const bookingService = {
  /**
   * Creates a new one-time booking with optional Idempotency-Key.
   */
  async createBooking(
    payload: CreateBookingPayload,
    idempotencyKey?: string,
  ): Promise<BookingResponse> {
    const headers: Record<string, string> = {}
    if (idempotencyKey && idempotencyKey.trim().length > 0) {
      headers['Idempotency-Key'] = idempotencyKey
    }

    const response = await apiClient.post<BookingResponse>('/bookings', payload, {
      headers,
    })
    return response.data
  },

  /**
   * Retrieves an existing booking by ID.
   */
  async getBookingById(id: number): Promise<BookingResponse> {
    const response = await apiClient.get<BookingResponse>(`/bookings/${id}`)
    return response.data
  },
}

export default bookingService
