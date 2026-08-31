import { apiClient } from '@/api/client'
import type { PageResponse } from '@/types/api'

export interface AdminUserSummary {
  id: number
  wissenId: string
  name: string
  email: string
  role: string
  location?: {
    id: number
    name: string
    code: string
    timezone: string
  } | null
  active: boolean
  bookingEnabled: boolean
  createdAt: string
}

export interface AdminUserDetail extends AdminUserSummary {
  updatedAt?: string
}

export interface UpdateUserBookingAccessPayload {
  bookingEnabled: boolean
  reason?: string
}

export interface AdminUserFilterParams {
  search?: string
  locationId?: number
  bookingEnabled?: boolean
  active?: boolean
  role?: string
  page?: number
  size?: number
}

export const userAdminService = {
  async getUsers(params: AdminUserFilterParams = {}): Promise<PageResponse<AdminUserSummary>> {
    const response = await apiClient.get<PageResponse<AdminUserSummary>>('/admin/users', {
      params,
    })
    return response.data
  },

  async getUserById(id: number): Promise<AdminUserDetail> {
    const response = await apiClient.get<AdminUserDetail>(`/admin/users/${id}`)
    return response.data
  },

  async updateBookingAccess(id: number, payload: UpdateUserBookingAccessPayload): Promise<AdminUserDetail> {
    const response = await apiClient.patch<AdminUserDetail>(`/admin/users/${id}/booking-access`, payload)
    return response.data
  },
}
