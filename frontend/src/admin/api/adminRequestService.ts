import apiClient from '@/api/client'
import type {
  AdminRequestPageResponse,
  AdminRequestResponse,
  AdminRequestStatus,
  ResolveAdminRequestPayload,
} from '@/types/adminRequest'

export interface GetAdminRequestsParams {
  status?: AdminRequestStatus | null
  locationId?: number | null
  page?: number
  size?: number
  sort?: string
}

export const adminRequestService = {
  /**
   * Fetch paginated administrative requests with filters (ADMIN only)
   */
  async getAdminRequests(params?: GetAdminRequestsParams): Promise<AdminRequestPageResponse> {
    const queryParams: Record<string, unknown> = {
      page: params?.page ?? 0,
      size: params?.size ?? 10,
      sort: params?.sort ?? 'createdAt,desc',
    }

    if (params?.status != null) {
      queryParams.status = params.status
    }
    if (params?.locationId != null) {
      queryParams.locationId = params.locationId
    }

    const response = await apiClient.get<AdminRequestPageResponse>('/admin/requests', {
      params: queryParams,
    })
    return response.data
  },

  /**
   * Fetch single administrative request by ID (ADMIN only)
   */
  async getAdminRequestById(id: number): Promise<AdminRequestResponse> {
    const response = await apiClient.get<AdminRequestResponse>(`/admin/requests/${id}`)
    return response.data
  },

  /**
   * Update request status (e.g. IN_PROGRESS, RESOLVED, CANCELLED) with optional resolution notes (ADMIN only)
   */
  async patchAdminRequestStatus(
    id: number,
    payload: ResolveAdminRequestPayload,
  ): Promise<AdminRequestResponse> {
    const response = await apiClient.patch<AdminRequestResponse>(
      `/admin/requests/${id}`,
      payload,
    )
    return response.data
  },
}

export default adminRequestService
