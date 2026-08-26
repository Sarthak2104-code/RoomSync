import apiClient from './client'
import type { BookingResponse, CreateBookingPayload, RescheduleBookingPayload } from '@/types/booking'
import type { PageResponse } from '@/types/api'

export interface GetMyBookingsParams {
  page?: number
  size?: number
  sort?: string
}

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

  /**
   * Retrieves the authenticated user's bookings with pagination.
   */
  async getMyBookings(params?: GetMyBookingsParams): Promise<PageResponse<BookingResponse>> {
    const response = await apiClient.get<PageResponse<BookingResponse>>('/bookings/my', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 10,
        sort: params?.sort ?? 'startTime,desc',
      },
    })
    return response.data
  },

  /**
   * Cancels an eligible booking using POST /api/bookings/{id}/cancel.
   */
  async cancelBooking(
    id: number,
    reason?: string,
    idempotencyKey?: string,
  ): Promise<void> {
    const headers: Record<string, string> = {}
    if (idempotencyKey && idempotencyKey.trim().length > 0) {
      headers['Idempotency-Key'] = idempotencyKey
    }

    const body = reason && reason.trim().length > 0 ? { reason: reason.trim() } : {}

    await apiClient.post<void>(`/bookings/${id}/cancel`, body, {
      headers,
    })
  },

  /**
   * Reschedules an eligible booking using PUT /api/bookings/{id}/reschedule.
   * Atomically soft-cancels the original booking and creates a confirmed replacement booking.
   */
  async rescheduleBooking(
    id: number,
    payload: RescheduleBookingPayload,
    idempotencyKey?: string,
  ): Promise<BookingResponse> {
    const headers: Record<string, string> = {}
    if (idempotencyKey && idempotencyKey.trim().length > 0) {
      headers['Idempotency-Key'] = idempotencyKey
    }

    const response = await apiClient.put<BookingResponse>(
      `/bookings/${id}/reschedule`,
      payload,
      { headers },
    )
    return response.data
  },
}

export default bookingService
