export type UserRole = 'USER' | 'ADMIN'

export interface User {
  id: number
  email: string
  role: UserRole
  locationId?: number | null
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken?: string
  tokenType: string
  expiresIn?: number
  userId: number
  email: string
  role: string
  locationId?: number | null
}

export interface AuthContextType {
  authenticated: boolean
  user: User | null
  role: UserRole | null
  accessToken: string | null
  isLoading: boolean
  login: (credentials: LoginRequest) => Promise<AuthResponse>
  logout: () => void
}
