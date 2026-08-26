import React, { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { roomService } from '@/api/roomService'
import { bookingService } from '@/api/bookingService'
import type { RoomResponse } from '@/types/room'
import type { BookingResponse } from '@/types/booking'
import type { BackendError } from '@/types/api'
import {
  Badge,
  Button,
  ConfirmDialog,
  DatePicker,
  ErrorState,
  Loading,
  TimePicker,
  toast,
} from '@/components'

function generateUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

// 15-minute boundary check
function isValid15MinuteBoundary(timeStr: string): boolean {
  if (!timeStr) return false
  const parts = timeStr.split(':')
  if (parts.length < 2) return false
  const minutes = Number(parts[1])
  return minutes % 15 === 0
}

export const BookingFormPage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>()
  const navigate = useNavigate()
  const idNum = roomId ? Number(roomId) : null

  // Room state
  const [room, setRoom] = useState<RoomResponse | null>(null)
  const [isRoomLoading, setIsRoomLoading] = useState<boolean>(true)
  const [roomError, setRoomError] = useState<BackendError | null>(null)

  // Form values
  const [date, setDate] = useState<string>(() => new Date().toISOString().split('T')[0])
  const [startTime, setStartTime] = useState<string>('10:00')
  const [endTime, setEndTime] = useState<string>('11:00')
  const [reason, setReason] = useState<string>('')

  // Validation errors
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({})

  // Review & Confirmation state
  const [step, setStep] = useState<'form' | 'review' | 'success'>('form')
  const [isConfirmOpen, setIsConfirmOpen] = useState<boolean>(false)
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false)
  const [mutationError, setMutationError] = useState<BackendError | null>(null)
  const [confirmedBooking, setConfirmedBooking] = useState<BookingResponse | null>(null)

  // Stable Idempotency Key for the logical booking operation attempt
  const idempotencyKeyRef = useRef<string>(generateUUID())

  // Fetch room details on mount
  const loadRoom = useCallback(async () => {
    if (!idNum) return
    setIsRoomLoading(true)
    setRoomError(null)
    try {
      const data = await roomService.getRoomById(idNum)
      setRoom(data)
    } catch (err: unknown) {
      setRoomError(err as BackendError)
    } finally {
      setIsRoomLoading(false)
    }
  }, [idNum])

  useEffect(() => {
    loadRoom()
  }, [loadRoom])

  // Validate form inputs
  const validateForm = (): boolean => {
    const errors: Record<string, string> = {}

    if (!date) {
      errors.date = 'Date is required.'
    } else {
      const today = new Date().toISOString().split('T')[0]
      if (date < today) {
        errors.date = 'Booking date cannot be in the past.'
      }
    }

    if (!startTime) {
      errors.startTime = 'Start time is required.'
    } else if (!isValid15MinuteBoundary(startTime)) {
      errors.startTime = 'Start time must be on a 15-minute boundary (e.g. :00, :15, :30, :45).'
    }

    if (!endTime) {
      errors.endTime = 'End time is required.'
    } else if (!isValid15MinuteBoundary(endTime)) {
      errors.endTime = 'End time must be on a 15-minute boundary (e.g. :00, :15, :30, :45).'
    }

    if (startTime && endTime) {
      if (startTime === endTime) {
        errors.endTime = 'End time must be after start time (minimum 15 minutes).'
      }
    }

    const trimmedReason = reason.trim()
    if (!trimmedReason) {
      errors.reason = 'Booking reason is required.'
    } else if (trimmedReason.length > 500) {
      errors.reason = 'Booking reason cannot exceed 500 characters.'
    }

    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }

  // Proceed from Form to Review
  const handleProceedToReview = (e: React.FormEvent) => {
    e.preventDefault()
    if (validateForm()) {
      setMutationError(null)
      setStep('review')
    }
  }

  // Submit booking mutation
  const handleConfirmBooking = async () => {
    if (!room) return
    setIsSubmitting(true)
    setMutationError(null)

    try {
      const timezoneOffset = '+05:30' // Asia/Kolkata location timezone offset
      const startTimeIso = `${date}T${startTime}:00${timezoneOffset}`

      // Handle cross-midnight if endTime < startTime
      let endDate = date
      if (endTime < startTime) {
        const nextDay = new Date(date)
        nextDay.setDate(nextDay.getDate() + 1)
        endDate = nextDay.toISOString().split('T')[0]
      }
      const endTimeIso = `${endDate}T${endTime}:00${timezoneOffset}`

      const payload = {
        roomId: room.id,
        startTime: startTimeIso,
        endTime: endTimeIso,
        reason: reason.trim(),
        localDate: date,
        timezone: 'Asia/Kolkata',
      }

      const response = await bookingService.createBooking(
        payload,
        idempotencyKeyRef.current,
      )

      setConfirmedBooking(response)
      setStep('success')
      setIsConfirmOpen(false)
      toast.success('Room successfully booked!')
    } catch (err: unknown) {
      const backendErr = err as BackendError
      setMutationError(backendErr)
      setIsConfirmOpen(false)

      if (backendErr.errorCode === 'BOOKING_CONFLICT' || backendErr.status === 409) {
        toast.error('The room is no longer available for this time.')
      } else {
        toast.error(backendErr.message || 'Failed to create booking.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isRoomLoading) {
    return <Loading fullPage text="Loading room details..." />
  }

  if (roomError || !room) {
    return (
      <div className="space-y-4">
        <Link
          to="/rooms"
          className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent"
        >
          &larr; Back to Rooms
        </Link>
        <ErrorState
          error={roomError || { message: 'Room not found' }}
          title="Cannot load room for booking"
          onRetry={loadRoom}
        />
      </div>
    )
  }

  // SUCCESS STEP VIEW
  if (step === 'success' && confirmedBooking) {
    return (
      <div className="max-w-2xl mx-auto space-y-6 animate-in fade-in duration-200">
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-md p-8 text-center">
          <div className="w-16 h-16 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-4 text-3xl font-bold">
            ✓
          </div>
          <h1 className="text-2xl font-bold text-brand-navy mb-1">Booking Confirmed!</h1>
          <p className="text-sm text-brand-slate mb-6">
            Your room reservation has been successfully confirmed.
          </p>

          <div className="bg-brand-light-gray rounded-lg p-6 text-left border border-brand-slate/20 space-y-3 mb-6">
            <div className="flex justify-between items-center pb-2 border-b border-brand-slate/10">
              <span className="text-xs font-semibold uppercase text-brand-slate">Booking ID</span>
              <span className="font-mono font-bold text-brand-navy">#{confirmedBooking.id}</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Room</span>
              <span className="font-medium text-brand-navy">{room.name}</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Location</span>
              <span className="text-brand-navy">{room.location?.name || '—'}</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Date</span>
              <span className="text-brand-navy">{date}</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Time</span>
              <span className="text-brand-navy font-medium">{startTime} – {endTime}</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Timezone</span>
              <span className="text-brand-slate text-xs">Asia/Kolkata (IST)</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Status</span>
              <Badge variant="success" size="sm">{confirmedBooking.status}</Badge>
            </div>
            <div className="pt-2 border-t border-brand-slate/10">
              <span className="text-xs text-brand-slate block mb-0.5">Reason</span>
              <p className="text-sm text-brand-navy bg-white p-2 rounded border border-brand-slate/10">
                {confirmedBooking.reason}
              </p>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link to="/my-bookings">
              <Button variant="primary" className="w-full sm:w-auto">
                View My Bookings &rarr;
              </Button>
            </Link>
            <Link to="/rooms">
              <Button variant="outline" className="w-full sm:w-auto">
                Return to Rooms
              </Button>
            </Link>
          </div>
        </div>
      </div>
    )
  }

  // REVIEW STEP VIEW
  if (step === 'review') {
    return (
      <div className="max-w-2xl mx-auto space-y-6 animate-in fade-in duration-200">
        <div>
          <button
            type="button"
            onClick={() => setStep('form')}
            className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent cursor-pointer"
          >
            &larr; Back to Edit Details
          </button>
          <h1 className="text-2xl font-bold text-brand-navy mt-2">Review Your Booking</h1>
          <p className="text-sm text-brand-slate">
            Please review the details before confirming your reservation.
          </p>
        </div>

        {mutationError && (
          <ErrorState
            error={mutationError}
            title={
              mutationError.errorCode === 'BOOKING_CONFLICT' || mutationError.status === 409
                ? 'Booking Conflict'
                : 'Booking Failed'
            }
            message={
              mutationError.errorCode === 'BOOKING_CONFLICT' || mutationError.status === 409
                ? 'The room is no longer available for this time. Please select another time or room.'
                : undefined
            }
          />
        )}

        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs p-6 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pb-4 border-b border-brand-slate/10">
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">Room</span>
              <p className="text-lg font-bold text-brand-navy mt-0.5">{room.name}</p>
            </div>
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">Location</span>
              <p className="text-sm font-medium text-brand-navy mt-0.5">
                {room.location?.name} ({room.location?.code})
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pb-4 border-b border-brand-slate/10">
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">Date</span>
              <p className="text-sm font-medium text-brand-navy mt-0.5">{date}</p>
            </div>
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">Time Window</span>
              <p className="text-sm font-medium text-brand-navy mt-0.5">{startTime} – {endTime}</p>
            </div>
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">Timezone</span>
              <p className="text-sm font-medium text-brand-navy mt-0.5">Asia/Kolkata (IST)</p>
            </div>
          </div>

          <div>
            <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">Booking Reason</span>
            <p className="text-sm text-brand-navy mt-1 p-3 bg-brand-light-gray rounded-md border border-brand-slate/10 whitespace-pre-wrap">
              {reason.trim()}
            </p>
          </div>

          <div className="rounded-md bg-blue-50 p-3 text-xs text-blue-900 border border-blue-200">
            Booking confirmation executes in an authoritative transaction. Final availability is enforced by the backend upon confirmation.
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-brand-slate/10">
            <Button
              variant="outline"
              onClick={() => setStep('form')}
              disabled={isSubmitting}
            >
              Edit Details
            </Button>
            <Button
              variant="primary"
              loading={isSubmitting}
              onClick={() => setIsConfirmOpen(true)}
            >
              Confirm Booking
            </Button>
          </div>
        </div>

        <ConfirmDialog
          isOpen={isConfirmOpen}
          title="Confirm Room Reservation?"
          description={`Are you sure you want to book ${room.name} on ${date} from ${startTime} to ${endTime}?`}
          confirmLabel="Yes, Book Room"
          cancelLabel="Cancel"
          loading={isSubmitting}
          onConfirm={handleConfirmBooking}
          onCancel={() => setIsConfirmOpen(false)}
        />
      </div>
    )
  }

  // FORM STEP VIEW
  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <Link
          to={`/rooms/${room.id}`}
          className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent"
        >
          &larr; Back to Room Details
        </Link>
        <h1 className="text-2xl font-bold text-brand-navy mt-2">Book Room: {room.name}</h1>
        <p className="text-sm text-brand-slate">
          Schedule a reservation for {room.name} ({room.location?.name}).
        </p>
      </div>

      {/* Room Context Summary Card */}
      <div className="bg-brand-white p-5 rounded-lg border border-brand-slate/20 shadow-xs flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-bold text-brand-navy text-lg">{room.name}</span>
            <Badge variant="success" size="sm">{room.status}</Badge>
          </div>
          <p className="text-xs text-brand-slate mt-0.5">
            {room.location?.name} ({room.location?.code}) &bull; Capacity: {room.capacity} people
          </p>
        </div>
        <div className="text-xs text-brand-slate sm:text-right font-medium">
          Authoritative Timezone: <span className="text-brand-navy font-semibold">Asia/Kolkata (IST)</span>
        </div>
      </div>

      {/* Booking Form */}
      <form
        onSubmit={handleProceedToReview}
        className="bg-brand-white p-6 rounded-lg border border-brand-slate/20 shadow-xs space-y-5"
        noValidate
      >
        <DatePicker
          label="Booking Date"
          required
          value={date}
          error={validationErrors.date}
          onChange={(e) => setDate(e.target.value)}
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <TimePicker
            label="Start Time (15-min intervals)"
            required
            step={900}
            value={startTime}
            error={validationErrors.startTime}
            onChange={(e) => setStartTime(e.target.value)}
          />

          <TimePicker
            label="End Time (15-min intervals)"
            required
            step={900}
            value={endTime}
            error={validationErrors.endTime}
            onChange={(e) => setEndTime(e.target.value)}
          />
        </div>

        <div>
          <label htmlFor="booking-reason" className="block text-sm font-medium text-brand-navy mb-1 text-left">
            Booking Reason <span className="text-red-500" aria-hidden="true">*</span>
          </label>
          <textarea
            id="booking-reason"
            rows={3}
            required
            maxLength={500}
            value={reason}
            placeholder="e.g. Customer architecture meeting, Project sprint review..."
            onChange={(e) => setReason(e.target.value)}
            className={`block w-full rounded-md border px-3 py-2 text-sm text-brand-navy shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-brand-accent focus:border-brand-accent ${
              validationErrors.reason ? 'border-red-500 focus:ring-red-500' : 'border-brand-slate/30'
            }`}
          />
          <div className="flex justify-between items-center mt-1">
            {validationErrors.reason ? (
              <p className="text-xs text-red-600 font-medium">{validationErrors.reason}</p>
            ) : (
              <p className="text-xs text-brand-slate">Plain text, maximum 500 characters.</p>
            )}
            <span className="text-xs text-brand-slate font-mono">{reason.length}/500</span>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t border-brand-slate/10">
          <Button
            variant="outline"
            onClick={() => navigate(`/rooms/${room.id}`)}
          >
            Cancel
          </Button>
          <Button type="submit" variant="primary">
            Review Booking &rarr;
          </Button>
        </div>
      </form>
    </div>
  )
}

export default BookingFormPage
