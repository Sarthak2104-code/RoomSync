import React from 'react'
import type { BookingResponse } from '@/types/booking'
import Modal from '@/components/Modal/Modal'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'

interface BookingDetailModalProps {
  isOpen: boolean
  onClose: () => void
  booking: BookingResponse | null
  onPromptCancel: (booking: BookingResponse) => void
  onPromptReschedule: (booking: BookingResponse) => void
}

export const BookingDetailModal: React.FC<BookingDetailModalProps> = ({
  isOpen,
  onClose,
  booking,
  onPromptCancel,
  onPromptReschedule,
}) => {
  if (!booking) return null

  const isConfirmed = booking.status === 'CONFIRMED'

  const formatDate = (isoString: string) => {
    try {
      return new Date(isoString).toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      })
    } catch {
      return isoString
    }
  }

  const formatTime = (isoString: string) => {
    try {
      return new Date(isoString).toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
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
      onClose={onClose}
      title={`Booking Details #${booking.id}`}
      description="Authoritative booking and lifecycle context."
      size="lg"
    >
      <div className="space-y-5 pt-2">
        {/* Status Banner */}
        <div className="flex items-center justify-between p-3.5 bg-slate-50 border border-slate-200 rounded-lg">
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Booking Status:
            </span>
            {booking.status === 'CONFIRMED' && (
              <Badge variant="success" size="md">
                CONFIRMED
              </Badge>
            )}
            {booking.status === 'COMPLETED' && (
              <Badge variant="info" size="md">
                COMPLETED
              </Badge>
            )}
            {booking.status === 'CANCELLED' && (
              <Badge variant="neutral" size="md" className="bg-red-50 text-red-700 border-red-200">
                CANCELLED
              </Badge>
            )}
          </div>

          <div className="text-xs font-mono text-brand-slate">
            ID: #{booking.id}
          </div>
        </div>

        {/* Core Attributes Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          {/* User / Booked By */}
          <div className="p-3 bg-white rounded-lg border border-brand-slate/20">
            <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
              Booked By / Wissen ID
            </div>
            {(booking.wissenId || booking.userWissenId) ? (
              <div>
                <div className="font-bold text-brand-navy flex items-center gap-2">
                  <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-brand-navy border border-slate-200">
                    {booking.wissenId || booking.userWissenId}
                  </span>
                  {booking.userName && <span>{booking.userName}</span>}
                </div>
                <div className="text-[11px] font-mono text-brand-slate mt-1">
                  Internal ID: #{booking.userId}
                </div>
              </div>
            ) : (
              <div className="font-bold text-brand-navy flex items-center gap-2">
                <span className="w-6 h-6 rounded-full bg-brand-navy text-brand-white flex items-center justify-center text-xs font-bold">
                  U
                </span>
                {booking.userName || `User #${booking.userId}`}
              </div>
            )}
          </div>

          {/* Room */}
          <div className="p-3 bg-white rounded-lg border border-brand-slate/20">
            <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
              Meeting Room
            </div>
            <div className="font-bold text-brand-navy">
              {booking.roomName || `Room #${booking.roomId}`}
            </div>
            <div className="text-xs text-brand-slate mt-0.5">
              Room ID: #{booking.roomId}
            </div>
          </div>

          {/* Schedule */}
          <div className="p-3 bg-white rounded-lg border border-brand-slate/20">
            <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
              Date
            </div>
            <div className="font-semibold text-brand-navy">
              {formatDate(booking.startTime)}
            </div>
          </div>

          {/* Time Interval */}
          <div className="p-3 bg-white rounded-lg border border-brand-slate/20">
            <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
              Time Window
            </div>
            <div className="font-semibold text-brand-navy">
              {formatTime(booking.startTime)} – {formatTime(booking.endTime)}
            </div>
          </div>
        </div>

        {/* Series / Rescheduled Relationship */}
        {(booking.seriesId != null || booking.rescheduledFromId != null) && (
          <div className="p-3 bg-blue-50/60 rounded-lg border border-blue-100 text-xs space-y-1 text-blue-900">
            {booking.seriesId != null && (
              <div className="flex items-center gap-1.5 font-medium">
                <span className="font-bold">Recurring Series:</span>
                <span>Series #{booking.seriesId}</span>
                {booking.occurrenceIndex != null && (
                  <span className="text-blue-700">(Occurrence #{booking.occurrenceIndex})</span>
                )}
              </div>
            )}
            {booking.rescheduledFromId != null && (
              <div className="flex items-center gap-1.5 font-medium">
                <span className="font-bold">Rescheduled From:</span>
                <span>Original Booking #{booking.rescheduledFromId}</span>
              </div>
            )}
          </div>
        )}

        {/* Booking Reason */}
        <div className="p-3.5 bg-slate-50/70 rounded-lg border border-brand-slate/20">
          <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
            Booking Reason
          </div>
          <p className="text-sm text-brand-navy whitespace-pre-wrap">
            {booking.reason || 'No reason provided.'}
          </p>
        </div>

        {/* Cancelled Reason if applicable */}
        {booking.status === 'CANCELLED' && booking.cancelledReason && (
          <div className="p-3.5 bg-red-50/70 rounded-lg border border-red-200">
            <div className="text-xs font-semibold text-red-700 uppercase tracking-wider mb-1">
              Cancellation Context
            </div>
            <p className="text-sm text-red-900 font-medium">
              {booking.cancelledReason}
            </p>
          </div>
        )}

        {/* Notification Delivery Context */}
        <div className="p-2.5 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-700 flex items-center gap-2">
          <svg className="w-4 h-4 text-slate-500 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>
            <strong>Notification Delivery:</strong> Booking lifecycle events (creation, reschedule, completion, and cancellation) trigger asynchronous notifications via the backend outbox.
          </span>
        </div>

        {/* Audit Timestamps */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between text-xs text-brand-slate pt-2 border-t border-slate-100">
          <span>Created: {formatTimestamp(booking.createdAt)}</span>
          <span>Last Updated: {formatTimestamp(booking.updatedAt)}</span>
        </div>

        {/* Modal Action Controls */}
        <div className="flex items-center justify-between pt-4 border-t border-slate-200">
          <div className="flex items-center gap-2">
            {isConfirmed && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    onClose()
                    onPromptReschedule(booking)
                  }}
                  className="text-xs"
                >
                  Reschedule
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    onClose()
                    onPromptCancel(booking)
                  }}
                  className="text-xs text-red-600 hover:text-red-700 hover:bg-red-50 border-red-200"
                >
                  Cancel Booking
                </Button>
              </>
            )}
          </div>

          <Button variant="primary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  )
}

export default BookingDetailModal
