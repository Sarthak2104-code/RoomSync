import apiClient from '@/api/client'
import type { BookingResponse, BookingStatus, RescheduleBookingPayload } from '@/types/booking'
import type { PageResponse } from '@/types/api'

export interface GetAdminBookingsParams {
  locationId?: number | null
  roomId?: number | null
  userId?: number | null
  status?: BookingStatus | null
  page?: number
  size?: number
  sort?: string
}

export const bookingAdminService = {
  /**
   * Fetch paginated bookings across all users and locations (ADMIN only)
   */
  async getAdminBookings(params?: GetAdminBookingsParams): Promise<PageResponse<BookingResponse>> {
    const queryParams: Record<string, unknown> = {
      page: params?.page ?? 0,
      size: params?.size ?? 10,
      sort: params?.sort ?? 'startTime,desc',
    }

    if (params?.locationId != null) {
      queryParams.locationId = params.locationId
    }
    if (params?.roomId != null) {
      queryParams.roomId = params.roomId
    }
    if (params?.userId != null) {
      queryParams.userId = params.userId
    }
    if (params?.status != null) {
      queryParams.status = params.status
    }

    const response = await apiClient.get<PageResponse<BookingResponse>>('/admin/bookings', {
      params: queryParams,
    })
    return response.data
  },

  /**
   * Fetch single booking by ID (ADMIN only)
   */
  async getAdminBookingById(id: number): Promise<BookingResponse> {
    const response = await apiClient.get<BookingResponse>(`/admin/bookings/${id}`)
    return response.data
  },

  /**
   * Cancel an eligible booking as administrator
   */
  async cancelBooking(id: number, reason?: string, idempotencyKey?: string): Promise<void> {
    const headers: Record<string, string> = {}
    if (idempotencyKey && idempotencyKey.trim().length > 0) {
      headers['Idempotency-Key'] = idempotencyKey
    }

    const body = reason && reason.trim().length > 0 ? { reason: reason.trim() } : { reason: 'Cancelled by admin' }

    await apiClient.post<void>(`/bookings/${id}/cancel`, body, {
      headers,
    })
  },

  /**
   * Reschedule an eligible booking as administrator
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

export default bookingAdminService
