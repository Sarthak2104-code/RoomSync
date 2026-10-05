import React, { createContext, useContext, useEffect, useState } from 'react'
import type { AuthContextType, AuthResponse, LoginRequest, User, UserRole } from '@/types/auth'
import {
  AUTH_UNAUTHORIZED_EVENT,
  clearAuthSession,
  getAccessToken,
  getStoredUser,
  setAccessToken,
  setStoredUser,
} from '@/utils/token'
import authService from './authService'

export const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [accessToken, setAccessTokenState] = useState<string | null>(null)
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)

  // Initialize session on startup
  useEffect(() => {
    try {
      const token = getAccessToken()
      const storedUser = getStoredUser()

      if (token && storedUser) {
        setAccessTokenState(token)
        setUser(storedUser)
      } else {
        clearAuthSession()
        setAccessTokenState(null)
        setUser(null)
      }
    } catch {
      clearAuthSession()
      setAccessTokenState(null)
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }, [])

  // Listen for global 401 unauthorized events from Axios interceptor
  useEffect(() => {
    const handleUnauthorized = () => {
      setAccessTokenState(null)
      setUser(null)
    }

    if (typeof window !== 'undefined') {
      window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized)
      return () => {
        window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized)
      }
    }
  }, [])

  const login = async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await authService.login(credentials)

    const authenticatedUser: User = {
      id: response.userId,
      wissenId: response.wissenId,
      name: response.name,
      email: response.email,
      role: (response.role === 'ADMIN' ? 'ADMIN' : 'USER') as UserRole,
      locationId: response.locationId,
    }

    setAccessToken(response.accessToken)
    setStoredUser(authenticatedUser)

    setAccessTokenState(response.accessToken)
    setUser(authenticatedUser)

    return response
  }

  const logout = () => {
    clearAuthSession()
    setAccessTokenState(null)
    setUser(null)
  }

  const role = user?.role || null
  const authenticated = !!accessToken && !!user

  return (
    <AuthContext.Provider
      value={{
        authenticated,
        user,
        role,
        accessToken,
        isLoading,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

export default AuthContext
