import apiClient from '@/api/client'
import type { UserProfileResponse } from '@/types/user'

/**
 * Service for user operations.
 * Derives caller identity exclusively from the backend-authenticated session.
 */
export const userService = {
  async getCurrentUserProfile(): Promise<UserProfileResponse> {
    const response = await apiClient.get<UserProfileResponse>('/users/me')
    return response.data
  },
}

export default userService
