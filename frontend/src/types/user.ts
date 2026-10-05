export interface LocationSummary {
  id: number
  name: string
  code: string
  timezone: string
}

export interface UserProfileResponse {
  id: number
  wissenId: string
  name: string
  email: string
  role: string
  location?: LocationSummary | null
  active: boolean
  bookingEnabled: boolean
  createdAt: string
}
