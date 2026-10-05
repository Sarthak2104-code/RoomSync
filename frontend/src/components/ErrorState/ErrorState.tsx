import React from 'react'
import type { BackendError } from '@/types/api'
import { getFriendlyErrorMessage, normalizeError } from '@/utils/error'
import Button from '../Button/Button'
import Badge from '../Badge/Badge'

export interface ErrorStateProps {
  error?: unknown | BackendError | string
  title?: string
  message?: string
  onRetry?: () => void
  showCorrelationId?: boolean
  className?: string
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  error,
  title = 'Something went wrong',
  message,
  onRetry,
  showCorrelationId = true,
  className = '',
}) => {
  const normalized: BackendError = error ? normalizeError(error) : { message: message || 'An error occurred.' }
  const displayMessage = message || getFriendlyErrorMessage(normalized)
  const validationErrors = normalized.validationErrors ? Object.entries(normalized.validationErrors) : []

  return (
    <div
      role="alert"
      className={`flex flex-col items-center justify-center p-8 text-center bg-brand-white rounded-lg border border-red-200 shadow-xs ${className}`}
    >
      <div className="w-12 h-12 rounded-full bg-red-100 text-red-600 flex items-center justify-center mb-4 text-xl font-bold">
        !
      </div>

      <h3 className="text-base font-bold text-brand-navy mb-1">{title}</h3>

      {normalized.errorCode && (
        <div className="mb-2">
          <Badge variant="error" size="sm">
            {normalized.errorCode}
          </Badge>
        </div>
      )}

      <p className="text-sm text-brand-slate max-w-md mb-4">{displayMessage}</p>

      {validationErrors.length > 0 && (
        <div className="w-full max-w-md bg-red-50/60 rounded-md p-3 mb-4 text-left border border-red-100">
          <p className="text-xs font-semibold text-red-800 mb-1">Validation Issues:</p>
          <ul className="text-xs text-red-700 list-disc list-inside space-y-0.5">
            {validationErrors.map(([field, err]) => (
              <li key={field}>
                <span className="font-medium capitalize">{field}</span>: {err}
              </li>
            ))}
          </ul>
        </div>
      )}

      {showCorrelationId && normalized.correlationId && (
        <p className="text-xs text-brand-slate/80 font-mono mb-4">
          Reference: <span className="select-all">{normalized.correlationId}</span>
        </p>
      )}

      {onRetry && (
        <div className="mt-2">
          <Button variant="outline" size="sm" onClick={onRetry}>
            Try again
          </Button>
        </div>
      )}
    </div>
  )
}

export default ErrorState
