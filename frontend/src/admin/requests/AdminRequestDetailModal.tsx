import React, { useState } from 'react'
import { toast } from 'sonner'
import type { AdminRequestResponse, AdminRequestStatus } from '@/types/adminRequest'
import type { ApiError } from '@/api/client'
import { adminRequestService } from '../api/adminRequestService'
import Modal from '@/components/Modal/Modal'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'

interface AdminRequestDetailModalProps {
  isOpen: boolean
  onClose: () => void
  request: AdminRequestResponse | null
  onSuccess: (updated: AdminRequestResponse) => void
}

export const AdminRequestDetailModal: React.FC<AdminRequestDetailModalProps> = ({
  isOpen,
  onClose,
  request,
  onSuccess,
}) => {
  const [resolutionNotes, setResolutionNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!request) return null

  const isTerminal = request.status === 'RESOLVED' || request.status === 'CANCELLED'

  const handleStatusTransition = async (newStatus: AdminRequestStatus) => {
    setError(null)
    setSubmitting(true)
    try {
      const payload = {
        status: newStatus,
        resolutionNotes: resolutionNotes.trim() ? resolutionNotes.trim() : undefined,
      }
      const updated = await adminRequestService.patchAdminRequestStatus(request.id, payload)
      toast.success(`Request #${request.id} status updated to ${newStatus}.`)
      onSuccess(updated)
      onClose()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || `Failed to transition request to ${newStatus}.`
      setError(message)
      toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  const formatTimestamp = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      return new Date(isoString).toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? () => {} : onClose}
      title={`Request #${request.id} — ${request.requestType}`}
      description="Administrative escalation context, related resources, and lifecycle status."
      size="lg"
    >
      <div className="space-y-5 pt-2">
        {/* Error Alert */}
        {error && (
          <div
            role="alert"
            className="p-3.5 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 flex items-start gap-2"
          >
            <svg
              className="w-4 h-4 text-red-500 shrink-0 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <div className="flex-1 font-medium">{error}</div>
          </div>
        )}

        {/* Status & ID Banner */}
        <div className="flex items-center justify-between p-3.5 bg-slate-50 border border-slate-200 rounded-lg">
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Current Status:
            </span>
            {request.status === 'OPEN' && (
              <Badge variant="info" size="md">
                OPEN
              </Badge>
            )}
            {request.status === 'IN_PROGRESS' && (
              <Badge variant="warning" size="md">
                IN PROGRESS
              </Badge>
            )}
            {request.status === 'RESOLVED' && (
              <Badge variant="success" size="md">
                RESOLVED
              </Badge>
            )}
            {request.status === 'CANCELLED' && (
              <Badge variant="neutral" size="md" className="bg-red-50 text-red-700 border-red-200">
                CANCELLED
              </Badge>
            )}
          </div>
          <div className="text-xs font-mono text-brand-slate">
            ID: #{request.id}
          </div>
        </div>

        {/* Core Attributes Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          {/* Requester User */}
          <div className="p-3 bg-white rounded-lg border border-brand-slate/20">
            <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
              Requester / Wissen ID
            </div>
            {request.requesterUserWissenId ? (
              <div>
                <div className="font-bold text-brand-navy flex items-center gap-2">
                  <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-brand-navy border border-slate-200">
                    {request.requesterUserWissenId}
                  </span>
                  {request.requesterUserName && <span>{request.requesterUserName}</span>}
                </div>
                <div className="text-[11px] font-mono text-brand-slate mt-1">
                  Internal ID: #{request.requesterUserId}
                </div>
              </div>
            ) : (
              <div className="font-bold text-brand-navy flex items-center gap-2">
                <span className="w-6 h-6 rounded-full bg-brand-navy text-brand-white flex items-center justify-center text-xs font-bold">
                  U
                </span>
                {request.requesterUserName || `User #${request.requesterUserId}`}
              </div>
            )}
          </div>

          {/* Location */}
          <div className="p-3 bg-white rounded-lg border border-brand-slate/20">
            <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
              Location
            </div>
            <div className="font-bold text-brand-navy">
              Location ID: #{request.locationId}
            </div>
          </div>
        </div>

        {/* Related Resources (if any) */}
        {(request.roomId != null || request.bookingId != null || request.bookingSeriesId != null) && (
          <div className="p-3 bg-blue-50/60 rounded-lg border border-blue-100 text-xs space-y-1.5 text-blue-900">
            <div className="font-bold text-[11px] uppercase tracking-wider text-blue-800 mb-1">
              Associated System Resources
            </div>
            <div className="flex flex-wrap gap-3">
              {request.roomId != null && (
                <div className="flex items-center gap-1 font-medium">
                  <span className="font-bold">Room:</span>
                  <span className="font-mono">#{request.roomId}</span>
                </div>
              )}
              {request.bookingId != null && (
                <div className="flex items-center gap-1 font-medium">
                  <span className="font-bold">Booking:</span>
                  <span className="font-mono">#{request.bookingId}</span>
                </div>
              )}
              {request.bookingSeriesId != null && (
                <div className="flex items-center gap-1 font-medium">
                  <span className="font-bold">Recurring Series:</span>
                  <span className="font-mono">#{request.bookingSeriesId}</span>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Message Content */}
        <div className="p-3.5 bg-slate-50/70 rounded-lg border border-brand-slate/20">
          <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
            Request Message
          </div>
          <p className="text-sm text-brand-navy whitespace-pre-wrap">
            {request.message || 'No description provided.'}
          </p>
        </div>

        {/* Resolved By context if resolved */}
        {request.resolvedByUserId != null && (
          <div className="p-3 bg-emerald-50/60 rounded-lg border border-emerald-100 text-xs text-emerald-900 flex items-center justify-between">
            <span className="font-medium flex items-center gap-2">
              <span>Resolved by Administrator:</span>
              {request.resolvedByUserWissenId ? (
                <span className="font-mono font-bold px-1.5 py-0.5 rounded bg-emerald-100 border border-emerald-200">
                  {request.resolvedByUserWissenId}
                </span>
              ) : null}
              {request.resolvedByUserName && <span>({request.resolvedByUserName})</span>}
              <span className="text-slate-500 font-mono text-[11px]">(ID: #{request.resolvedByUserId})</span>
            </span>
          </div>
        )}

        {/* Action Controls & Resolution Notes (Only if not terminal) */}
        {!isTerminal ? (
          <div className="space-y-3 pt-2 border-t border-slate-100">
            <div>
              <label className="block text-xs font-semibold text-brand-navy uppercase tracking-wider mb-1 text-left">
                Resolution Notes <span className="text-brand-slate font-normal lowercase">(optional context)</span>
              </label>
              <textarea
                rows={2}
                placeholder="Add notes about your administrative decision..."
                value={resolutionNotes}
                onChange={(e) => setResolutionNotes(e.target.value)}
                disabled={submitting}
                className="w-full px-3 py-2 text-sm border border-brand-slate/30 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-brand-accent focus:border-brand-accent transition-colors"
              />
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 pt-2">
              <div className="flex flex-wrap items-center gap-2">
                {request.status === 'OPEN' && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    loading={submitting}
                    disabled={submitting}
                    onClick={() => handleStatusTransition('IN_PROGRESS')}
                    className="text-xs text-amber-700 hover:bg-amber-50 border-amber-300"
                  >
                    Move to In Progress
                  </Button>
                )}
                <Button
                  type="button"
                  variant="primary"
                  size="sm"
                  loading={submitting}
                  disabled={submitting}
                  onClick={() => handleStatusTransition('RESOLVED')}
                  className="text-xs bg-emerald-600 hover:bg-emerald-700"
                >
                  Mark Resolved
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  loading={submitting}
                  disabled={submitting}
                  onClick={() => handleStatusTransition('CANCELLED')}
                  className="text-xs text-red-600 hover:bg-red-50 border-red-200"
                >
                  Cancel Request
                </Button>
              </div>

              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onClose}
                disabled={submitting}
              >
                Close
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex items-center justify-between pt-4 border-t border-slate-100">
            <span className="text-xs text-brand-slate italic">
              This request is finalized and cannot be modified further.
            </span>
            <Button variant="primary" size="sm" onClick={onClose}>
              Close
            </Button>
          </div>
        )}

        {/* Audit Timestamps */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between text-xs text-brand-slate pt-2 border-t border-slate-100">
          <span>Created: {formatTimestamp(request.createdAt)}</span>
          <span>Last Updated: {formatTimestamp(request.updatedAt)}</span>
        </div>
      </div>
    </Modal>
  )
}

export default AdminRequestDetailModal
