import apiClient from '@/api/client'
import type { AuthResponse, LoginRequest } from '@/types/auth'

/**
 * Authentication API service using central apiClient.
 */
export const authService = {
  async login(credentials: LoginRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/login', credentials)
    return response.data
  },
}

export default authService
