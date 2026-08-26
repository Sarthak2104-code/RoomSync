import type { PageResponse } from './api'

export interface LocationResponse {
  id: number
  name: string
  code: string
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export type LocationPageResponse = PageResponse<LocationResponse>
