import React, { useCallback, useEffect, useRef, useState } from 'react'
import type { OperationResponse, OperationStatus as OperationStatusType } from '@/types/operation'
import type { ApiError } from '@/api/client'
import operationService from '@/api/operationService'
import Badge from '../Badge/Badge'
import Button from '../Button/Button'

export interface OperationStatusProps {
  operationId: string
  initialData?: OperationResponse | null
  pollIntervalMs?: number
  maxPollAttempts?: number
  onTerminalStatus?: (operation: OperationResponse) => void
  onSucceeded?: (operation: OperationResponse) => void
  onFailed?: (operation: OperationResponse) => void
  className?: string
}

const TERMINAL_STATUSES: OperationStatusType[] = [
  'SUCCEEDED',
  'FAILED',
  'CONFLICT',
  'PARTIAL_SUCCESS',
  'CANCELLED',
]

export const OperationStatus: React.FC<OperationStatusProps> = ({
  operationId,
  initialData = null,
  pollIntervalMs = 2000,
  maxPollAttempts = 30,
  onTerminalStatus,
  onSucceeded,
  onFailed,
  className = '',
}) => {
  const [operation, setOperation] = useState<OperationResponse | null>(initialData)
  const [isLoading, setIsLoading] = useState<boolean>(!initialData)
  const [pollError, setPollError] = useState<string | null>(null)
  const [attempts, setAttempts] = useState<number>(0)
  const [isMaxAttemptsReached, setIsMaxAttemptsReached] = useState<boolean>(false)

  const isFetchingRef = useRef<boolean>(false)
  const timerRef = useRef<number | null>(null)
  const onTerminalStatusRef = useRef(onTerminalStatus)
  const onSucceededRef = useRef(onSucceeded)
  const onFailedRef = useRef(onFailed)

  onTerminalStatusRef.current = onTerminalStatus
  onSucceededRef.current = onSucceeded
  onFailedRef.current = onFailed

  const fetchStatus = useCallback(async (isManual = false) => {
    if (!operationId || isFetchingRef.current) return

    isFetchingRef.current = true
    if (isManual) {
      setIsLoading(true)
      setIsMaxAttemptsReached(false)
    }

    try {
      const data = await operationService.getOperation(operationId)
      setOperation(data)
      setPollError(null)
      setAttempts((prev) => prev + 1)

      if (TERMINAL_STATUSES.includes(data.status)) {
        onTerminalStatusRef.current?.(data)
        if (data.status === 'SUCCEEDED') {
          onSucceededRef.current?.(data)
        } else if (data.status === 'FAILED') {
          onFailedRef.current?.(data)
        }
      }
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || 'Unable to check the current status. We will retry.'
      setPollError(message)
    } finally {
      setIsLoading(false)
      isFetchingRef.current = false
    }
  }, [operationId])

  // Polling loop effect
  useEffect(() => {
    if (!operationId) return

    const currentStatus = operation?.status

    // Check if terminal
    if (currentStatus && TERMINAL_STATUSES.includes(currentStatus)) {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current)
        timerRef.current = null
      }
      return
    }

    // Check max attempts
    if (attempts >= maxPollAttempts) {
      setIsMaxAttemptsReached(true)
      if (timerRef.current) {
        window.clearTimeout(timerRef.current)
        timerRef.current = null
      }
      return
    }

    // Schedule next poll
    timerRef.current = window.setTimeout(() => {
      fetchStatus()
    }, pollIntervalMs)

    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [operationId, operation?.status, attempts, maxPollAttempts, pollIntervalMs, fetchStatus])

  // Initial fetch on mount if no initialData
  useEffect(() => {
    if (!initialData && operationId) {
      fetchStatus(true)
    }
  }, [initialData, operationId, fetchStatus])

  const handleManualRefresh = () => {
    fetchStatus(true)
  }

  // Format date helper
  const formatTimestamp = (isoString?: string | null) => {
    if (!isoString) return '—'
    try {
      return new Date(isoString).toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  const currentStatus = operation?.status ?? (isLoading ? 'EXECUTING' : null)

  return (
    <div
      role="region"
      aria-live="polite"
      aria-atomic="true"
      className={`rounded-xl border border-brand-slate/20 bg-brand-white p-5 shadow-2xs ${className}`}
    >
      {/* Header with Operation ID & Badge */}
      <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-brand-slate/10">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
            Operation:
          </span>
          <span className="font-mono text-xs font-bold text-brand-navy select-all">
            #{operationId}
          </span>
          {operation?.operationType && (
            <span className="text-xs text-brand-slate">({operation.operationType})</span>
          )}
        </div>

        <div>
          {currentStatus === 'EXECUTING' && (
            <Badge variant="info" size="sm" className="flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
              EXECUTING
            </Badge>
          )}
          {currentStatus === 'SUCCEEDED' && (
            <Badge variant="success" size="sm">
              SUCCEEDED
            </Badge>
          )}
          {currentStatus === 'FAILED' && (
            <Badge variant="error" size="sm">
              FAILED
            </Badge>
          )}
          {currentStatus === 'CONFLICT' && (
            <Badge variant="warning" size="sm" className="bg-amber-50 text-amber-800 border-amber-300">
              CONFLICT
            </Badge>
          )}
          {currentStatus === 'PARTIAL_SUCCESS' && (
            <Badge variant="info" size="sm" className="bg-blue-50 text-blue-800 border-blue-200">
              PARTIAL SUCCESS
            </Badge>
          )}
          {currentStatus === 'TIMEOUT_UNKNOWN' && (
            <Badge variant="warning" size="sm" className="bg-amber-50 text-amber-700 border-amber-200">
              CHECKING STATUS
            </Badge>
          )}
          {currentStatus === 'CANCELLED' && (
            <Badge variant="neutral" size="sm" className="bg-slate-100 text-slate-700 border-slate-300">
              CANCELLED
            </Badge>
          )}
          {!currentStatus && isLoading && (
            <Badge variant="neutral" size="sm">
              LOADING...
            </Badge>
          )}
        </div>
      </div>

      {/* Main Status Messaging & Context */}
      <div className="py-4 space-y-3">
        {/* EXECUTING / IN PROGRESS */}
        {(currentStatus === 'EXECUTING' || currentStatus === 'REQUESTED' || currentStatus === 'PLANNING' || currentStatus === 'CONFIRMED') && (
          <div className="p-4 bg-blue-50/60 rounded-lg border border-blue-100 text-blue-900 flex items-center gap-3">
            <svg
              className="w-5 h-5 text-blue-600 animate-spin shrink-0"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
            <div className="text-sm">
              <p className="font-semibold text-blue-950">Request in progress...</p>
              <p className="text-xs text-blue-700 mt-0.5">
                The backend is currently processing your request. Status updates automatically.
              </p>
            </div>
          </div>
        )}

        {/* SUCCEEDED */}
        {currentStatus === 'SUCCEEDED' && (
          <div className="p-4 bg-emerald-50/70 rounded-lg border border-emerald-200 text-emerald-900 flex items-start gap-3">
            <div className="w-5 h-5 rounded-full bg-emerald-500 text-white flex items-center justify-center text-xs font-bold shrink-0 mt-0.5">
              ✓
            </div>
            <div className="text-sm space-y-1">
              <p className="font-bold text-emerald-950">Operation completed successfully.</p>
              {operation?.errorMessage ? (
                <p className="text-xs text-emerald-800">{operation.errorMessage}</p>
              ) : (
                <p className="text-xs text-emerald-700">
                  The requested operation was committed and state has been updated.
                </p>
              )}
            </div>
          </div>
        )}

        {/* FAILED */}
        {currentStatus === 'FAILED' && (
          <div className="p-4 bg-red-50/80 rounded-lg border border-red-200 text-red-900 flex items-start gap-3">
            <div className="w-5 h-5 rounded-full bg-red-500 text-white flex items-center justify-center text-xs font-bold shrink-0 mt-0.5">
              ✕
            </div>
            <div className="text-sm space-y-1">
              <p className="font-bold text-red-950">Operation failed.</p>
              <p className="text-xs text-red-800">
                {operation?.errorMessage || 'The operation could not be completed.'}
              </p>
              {operation?.errorCode && (
                <p className="text-[11px] font-mono text-red-600">
                  Error Code: {operation.errorCode}
                </p>
              )}
            </div>
          </div>
        )}

        {/* CONFLICT */}
        {currentStatus === 'CONFLICT' && (
          <div className="p-4 bg-amber-50/80 rounded-lg border border-amber-200 text-amber-900 flex items-start gap-3">
            <svg
              className="w-5 h-5 text-amber-600 shrink-0 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
            <div className="text-sm space-y-1">
              <p className="font-bold text-amber-950">Operation Conflict</p>
              <p className="text-xs text-amber-800">
                {operation?.errorMessage ||
                  'This request could not be completed because the current state conflicts with another operation or resource state.'}
              </p>
            </div>
          </div>
        )}

        {/* PARTIAL_SUCCESS */}
        {currentStatus === 'PARTIAL_SUCCESS' && (
          <div className="p-4 bg-blue-50/70 rounded-lg border border-blue-200 text-blue-900 flex items-start gap-3">
            <svg
              className="w-5 h-5 text-blue-600 shrink-0 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <div className="text-sm space-y-1">
              <p className="font-bold text-blue-950">The request completed partially.</p>
              <p className="text-xs text-blue-800">
                {operation?.errorMessage || 'Some elements of the operation were processed successfully while others encountered issues.'}
              </p>
            </div>
          </div>
        )}

        {/* TIMEOUT_UNKNOWN - NON-NEGOTIABLE EXACT REQUIREMENT */}
        {currentStatus === 'TIMEOUT_UNKNOWN' && (
          <div className="p-4 bg-amber-50/70 rounded-lg border border-amber-200 text-amber-900 flex items-start gap-3">
            <svg
              className="w-5 h-5 text-amber-600 animate-spin shrink-0 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            <div className="text-sm space-y-1">
              <p className="font-bold text-amber-950">
                We are checking the final status of your request.
              </p>
              <p className="text-xs text-amber-800">
                The operation is undergoing verification. The final outcome has not yet been determined; your request has not failed.
              </p>
            </div>
          </div>
        )}

        {/* CANCELLED */}
        {currentStatus === 'CANCELLED' && (
          <div className="p-4 bg-slate-50 rounded-lg border border-slate-200 text-slate-800 flex items-start gap-3">
            <svg
              className="w-5 h-5 text-slate-500 shrink-0 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636"
              />
            </svg>
            <div className="text-sm space-y-1">
              <p className="font-bold text-slate-900">Operation was cancelled.</p>
              <p className="text-xs text-slate-600">
                {operation?.errorMessage || 'This operation was aborted before completion.'}
              </p>
            </div>
          </div>
        )}

        {/* Transient Network/Polling Failure Alert (Does NOT mean operation failed) */}
        {pollError && (
          <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 text-xs text-slate-700 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-amber-500" />
              <span>{pollError}</span>
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleManualRefresh}
              disabled={isLoading}
              className="text-xs py-1"
            >
              Retry Status Check
            </Button>
          </div>
        )}

        {/* Max Polling Attempts Safety Notice */}
        {isMaxAttemptsReached && !TERMINAL_STATUSES.includes(currentStatus ?? 'REQUESTED') && (
          <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 text-xs text-slate-700 flex items-center justify-between gap-3">
            <span>
              Background polling paused. The operation is still unresolved in the backend.
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleManualRefresh}
              disabled={isLoading}
              className="text-xs py-1"
            >
              Check status again
            </Button>
          </div>
        )}
      </div>

      {/* Footer Meta & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pt-3 border-t border-brand-slate/10 text-xs text-brand-slate">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          {operation?.correlationId && (
            <span>
              Correlation ID: <span className="font-mono">{operation.correlationId}</span>
            </span>
          )}
          {operation?.updatedAt && (
            <span>Last Updated: {formatTimestamp(operation.updatedAt)}</span>
          )}
        </div>

        {/* Manual Refresh Button for unresolved operations */}
        {!TERMINAL_STATUSES.includes(currentStatus ?? 'REQUESTED') && (
          <div className="flex justify-end">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleManualRefresh}
              disabled={isLoading}
              className="text-xs"
            >
              {isLoading ? 'Checking...' : 'Check status again'}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}

export default OperationStatus
