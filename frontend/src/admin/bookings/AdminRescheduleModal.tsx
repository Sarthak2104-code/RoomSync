import React, { useEffect, useState } from 'react'
import { toast } from 'sonner'
import type { BookingResponse } from '@/types/booking'
import type { RoomResponse } from '@/types/room'
import type { ApiError } from '@/api/client'
import { bookingAdminService } from '../api/bookingAdminService'
import Modal from '@/components/Modal/Modal'
import Select from '@/components/Select/Select'
import Input from '@/components/Input/Input'
import Button from '@/components/Button/Button'

interface AdminRescheduleModalProps {
  isOpen: boolean
  onClose: () => void
  booking: BookingResponse | null
  rooms: RoomResponse[]
  onSuccess: (updated: BookingResponse) => void
}

function getTimezoneOffsetString(): string {
  const date = new Date()
  const offset = -date.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const pad = (num: number) => String(Math.floor(Math.abs(num))).padStart(2, '0')
  return `${sign}${pad(offset / 60)}:${pad(offset % 60)}`
}

function isValid15MinuteBoundary(timeStr: string): boolean {
  if (!timeStr) return false
  const parts = timeStr.split(':')
  if (parts.length < 2) return false
  const minutes = Number(parts[1])
  return minutes % 15 === 0
}

export const AdminRescheduleModal: React.FC<AdminRescheduleModalProps> = ({
  isOpen,
  onClose,
  booking,
  rooms,
  onSuccess,
}) => {
  const [selectedRoomId, setSelectedRoomId] = useState<string>('')
  const [date, setDate] = useState<string>('')
  const [startTime, setStartTime] = useState<string>('')
  const [endTime, setEndTime] = useState<string>('')

  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)

  useEffect(() => {
    if (isOpen && booking) {
      setServerError(null)
      setValidationError(null)
      setSelectedRoomId(String(booking.roomId))

      // Extract Date and Times from booking.startTime and booking.endTime
      if (booking.startTime && booking.startTime.includes('T')) {
        const startParts = booking.startTime.split('T')
        setDate(startParts[0])
        setStartTime(startParts[1].slice(0, 5))
      }
      if (booking.endTime && booking.endTime.includes('T')) {
        const endParts = booking.endTime.split('T')
        setEndTime(endParts[1].slice(0, 5))
      }
    }
  }, [isOpen, booking])

  if (!booking) return null

  const handleReschedule = async (e: React.FormEvent) => {
    e.preventDefault()
    setServerError(null)
    setValidationError(null)

    if (!date) {
      setValidationError('Please select a booking date.')
      return
    }
    if (!startTime || !endTime) {
      setValidationError('Please select both start and end times.')
      return
    }
    if (startTime >= endTime) {
      setValidationError('Start time must be before end time.')
      return
    }
    if (!isValid15MinuteBoundary(startTime) || !isValid15MinuteBoundary(endTime)) {
      setValidationError('Start and end times must be in 15-minute intervals (e.g. :00, :15, :30, :45).')
      return
    }

    const tzOffset = getTimezoneOffsetString()
    const startTimeIso = `${date}T${startTime}:00${tzOffset}`
    const endTimeIso = `${date}T${endTime}:00${tzOffset}`

    // Check if start time is in the past
    if (new Date(startTimeIso).getTime() < Date.now()) {
      setValidationError('Rescheduled booking time cannot start in the past.')
      return
    }

    setSubmitting(true)
    try {
      const payload = {
        roomId: selectedRoomId ? Number(selectedRoomId) : booking.roomId,
        startTime: startTimeIso,
        endTime: endTimeIso,
      }

      const updated = await bookingAdminService.rescheduleBooking(booking.id, payload)
      toast.success(`Booking #${booking.id} rescheduled successfully to #${updated.id}.`)
      onSuccess(updated)
      onClose()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || 'Failed to reschedule booking.'
      setServerError(message)
      toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  const roomOptions = rooms.map((r) => ({
    value: String(r.id),
    label: `${r.name} (${r.location?.code || ''}) — ${r.capacity} seats`,
  }))

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? () => {} : onClose}
      title={`Reschedule Booking #${booking.id}`}
      description={`Rescheduling for User #${booking.userId} in ${booking.roomName}.`}
      size="md"
    >
      <form onSubmit={handleReschedule} className="space-y-4 pt-2">
        {/* Backend Error Alert */}
        {serverError && (
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
            <div className="flex-1 font-medium">{serverError}</div>
          </div>
        )}

        {/* Validation Error Alert */}
        {validationError && (
          <div
            role="alert"
            className="p-3 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-800 font-medium"
          >
            {validationError}
          </div>
        )}

        {/* Target Room */}
        <div>
          <label className="block text-sm font-medium text-brand-navy mb-1 text-left">
            Target Room <span className="text-xs text-brand-slate font-normal">(Optional change)</span>
          </label>
          <Select
            options={roomOptions}
            value={selectedRoomId}
            onChange={(e) => setSelectedRoomId(e.target.value)}
            disabled={submitting || roomOptions.length === 0}
          />
        </div>

        {/* New Date */}
        <Input
          label="New Date"
          type="date"
          required
          min={new Date().toISOString().split('T')[0]}
          value={date}
          onChange={(e) => setDate(e.target.value)}
          disabled={submitting}
        />

        {/* Start and End Times */}
        <div className="grid grid-cols-2 gap-3">
          <Input
            label="Start Time"
            type="time"
            step="900"
            required
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            disabled={submitting}
            helperText="15-min intervals"
          />
          <Input
            label="End Time"
            type="time"
            step="900"
            required
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            disabled={submitting}
            helperText="15-min intervals"
          />
        </div>

        {/* Reason Context (Read-only notice) */}
        <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 text-xs text-brand-slate">
          <span className="font-semibold text-brand-navy">Original Reason: </span>
          <span className="italic">{booking.reason || 'No reason provided'}</span>
        </div>

        {/* Action Controls */}
        <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={submitting}
            disabled={submitting}
          >
            Confirm Reschedule
          </Button>
        </div>
      </form>
    </Modal>
  )
}

export default AdminRescheduleModal
