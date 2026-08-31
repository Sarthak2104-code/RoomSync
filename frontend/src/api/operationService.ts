import apiClient from './client'
import type { OperationPageResponse, OperationResponse } from '@/types/operation'

export const operationService = {
  /**
   * Fetch single asynchronous operation status by operationId
   */
  async getOperation(operationId: string): Promise<OperationResponse> {
    const response = await apiClient.get<OperationResponse>(`/operations/${operationId}`)
    return response.data
  },

  /**
   * Fetch paginated operations initiated by user/agent
   */
  async getOperations(page = 0, size = 20): Promise<OperationPageResponse> {
    const response = await apiClient.get<OperationPageResponse>('/operations', {
      params: { page, size, sort: 'createdAt,desc' },
    })
    return response.data
  },
}

export default operationService
