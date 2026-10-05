import type { User } from '@/types/auth'

const ACCESS_TOKEN_KEY = 'roomsync_access_token'
const USER_SESSION_KEY = 'roomsync_user_session'

export const AUTH_UNAUTHORIZED_EVENT = 'roomsync:auth:unauthorized'

export function getAccessToken(): string | null {
  try {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
  } catch {
    return null
  }
}

export function setAccessToken(token: string): void {
  try {
    localStorage.setItem(ACCESS_TOKEN_KEY, token)
  } catch {
    // Ignore storage errors
  }
}

export function clearAccessToken(): void {
  try {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
  } catch {
    // Ignore storage errors
  }
}

export function getStoredUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_SESSION_KEY)
    if (!raw) return null
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export function setStoredUser(user: User): void {
  try {
    localStorage.setItem(USER_SESSION_KEY, JSON.stringify(user))
  } catch {
    // Ignore storage errors
  }
}

export function clearStoredUser(): void {
  try {
    localStorage.removeItem(USER_SESSION_KEY)
  } catch {
    // Ignore storage errors
  }
}

export function clearAuthSession(): void {
  clearAccessToken()
  clearStoredUser()
}

export function notifyUnauthorized(): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT))
  }
}
