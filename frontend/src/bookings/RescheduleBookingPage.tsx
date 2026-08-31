import React, { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { bookingService } from '@/api/bookingService'
import { roomService } from '@/api/roomService'
import type { BookingResponse, RescheduleBookingPayload } from '@/types/booking'
import type { RoomResponse } from '@/types/room'
import type { BackendError } from '@/types/api'
import {
  Badge,
  Button,
  ConfirmDialog,
  DatePicker,
  ErrorState,
  Loading,
  OperationStatus,
  Select,
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

function isValid15MinuteBoundary(timeStr: string): boolean {
  if (!timeStr) return false
  const parts = timeStr.split(':')
  if (parts.length < 2) return false
  const minutes = Number(parts[1])
  return minutes % 15 === 0
}

interface ReplacementSelection {
  roomId: number
  roomName: string
  locationName?: string
  date: string
  startTime: string
  endTime: string
  startTimeIso: string
  endTimeIso: string
}

export const RescheduleBookingPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const bookingId = id ? Number(id) : null

  // Original booking state
  const [originalBooking, setOriginalBooking] = useState<BookingResponse | null>(null)
  const [availableRooms, setAvailableRooms] = useState<RoomResponse[]>([])
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [loadError, setLoadError] = useState<BackendError | null>(null)

  // Reschedule form state
  const [selectedRoomId, setSelectedRoomId] = useState<string>('')
  const [date, setDate] = useState<string>('')
  const [startTime, setStartTime] = useState<string>('')
  const [endTime, setEndTime] = useState<string>('')
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({})

  // Replacement selection snapshot for Review & Confirmation
  const [replacementSelection, setReplacementSelection] = useState<ReplacementSelection | null>(null)

  // Flow steps: 'form' -> 'review' -> 'success'
  const [step, setStep] = useState<'form' | 'review' | 'success'>('form')
  const [isConfirmOpen, setIsConfirmOpen] = useState<boolean>(false)
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false)
  const [mutationError, setMutationError] = useState<BackendError | null>(null)
  const [replacementBooking, setReplacementBooking] = useState<BookingResponse | null>(null)

  // Stable Idempotency Key for the logical reschedule operation attempt
  const idempotencyKeyRef = useRef<string>(generateUUID())

  // Load existing booking and candidate rooms
  const loadBookingAndRooms = useCallback(async () => {
    if (!bookingId) return
    setIsLoading(true)
    setLoadError(null)

    try {
      const [bookingData, roomsData] = await Promise.all([
        bookingService.getBookingById(bookingId),
        roomService.getRooms({ page: 0, size: 50 }),
      ])

      setOriginalBooking(bookingData)
      setAvailableRooms(roomsData.content)
      setSelectedRoomId(String(bookingData.roomId))

      // Initialize form values from original booking (ISO format extraction)
      if (bookingData.startTime && bookingData.startTime.includes('T')) {
        const startIsoDate = bookingData.startTime.split('T')[0]
        const startIsoTime = bookingData.startTime.split('T')[1].slice(0, 5)
        setDate(startIsoDate)
        setStartTime(startIsoTime)
      } else {
        setDate(new Date().toISOString().split('T')[0])
        setStartTime('10:00')
      }

      if (bookingData.endTime && bookingData.endTime.includes('T')) {
        const endIsoTime = bookingData.endTime.split('T')[1].slice(0, 5)
        setEndTime(endIsoTime)
      } else {
        setEndTime('11:00')
      }
    } catch (err: unknown) {
      setLoadError(err as BackendError)
    } finally {
      setIsLoading(false)
    }
  }, [bookingId])

  useEffect(() => {
    loadBookingAndRooms()
  }, [loadBookingAndRooms])

  const formatDateTime = (isoString?: string) => {
    if (!isoString) return { date: '—', time: '—' }
    try {
      const d = new Date(isoString)
      return {
        date: d.toLocaleDateString('en-US', {
          year: 'numeric',
          month: 'short',
          day: 'numeric',
        }),
        time: d.toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
          hour12: false,
        }),
      }
    } catch {
      return { date: isoString, time: '' }
    }
  }

  // Validate form
  const validateForm = (): boolean => {
    const errors: Record<string, string> = {}

    if (!selectedRoomId) {
      errors.roomId = 'Room selection is required.'
    }

    if (!date) {
      errors.date = 'Date is required.'
    } else {
      const today = new Date().toISOString().split('T')[0]
      if (date < today) {
        errors.date = 'Rescheduled date cannot be in the past.'
      }
    }

    if (!startTime) {
      errors.startTime = 'Start time is required.'
    } else if (!isValid15MinuteBoundary(startTime)) {
      errors.startTime = 'Start time must be on a 15-minute interval (:00, :15, :30, :45).'
    }

    if (!endTime) {
      errors.endTime = 'End time is required.'
    } else if (!isValid15MinuteBoundary(endTime)) {
      errors.endTime = 'End time must be on a 15-minute interval (:00, :15, :30, :45).'
    }

    if (startTime && endTime) {
      if (startTime === endTime) {
        errors.endTime = 'End time must be after start time (minimum 15 minutes).'
      }
    }

    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleProceedToReview = (e: React.FormEvent) => {
    e.preventDefault()
    if (validateForm()) {
      setMutationError(null)

      const timezoneOffset = '+05:30' // Asia/Kolkata
      const startTimeIso = `${date}T${startTime}:00${timezoneOffset}`

      // Handle cross-midnight if endTime < startTime
      let endDate = date
      if (endTime < startTime) {
        const nextDay = new Date(date)
        nextDay.setDate(nextDay.getDate() + 1)
        endDate = nextDay.toISOString().split('T')[0]
      }
      const endTimeIso = `${endDate}T${endTime}:00${timezoneOffset}`

      const chosenRoom = availableRooms.find((r) => String(r.id) === String(selectedRoomId))

      const selectionSnapshot: ReplacementSelection = {
        roomId: Number(selectedRoomId),
        roomName: chosenRoom?.name || `Room #${selectedRoomId}`,
        locationName: chosenRoom?.location?.name,
        date,
        startTime,
        endTime,
        startTimeIso,
        endTimeIso,
      }

      setReplacementSelection(selectionSnapshot)
      // Fresh idempotency key for this new logical reschedule selection
      idempotencyKeyRef.current = generateUUID()
      setStep('review')
    }
  }

  const handleExecuteReschedule = async () => {
    if (!originalBooking || !replacementSelection) return
    setIsSubmitting(true)
    setMutationError(null)

    try {
      const payload: RescheduleBookingPayload = {
        roomId: replacementSelection.roomId,
        startTime: replacementSelection.startTimeIso,
        endTime: replacementSelection.endTimeIso,
      }

      const response = await bookingService.rescheduleBooking(
        originalBooking.id,
        payload,
        idempotencyKeyRef.current
      )

      setReplacementBooking(response)
      setStep('success')
      setIsConfirmOpen(false)
      toast.success('Booking rescheduled successfully!')
    } catch (err: unknown) {
      const backendErr = err as BackendError
      setMutationError(backendErr)
      setIsConfirmOpen(false)

      if (
        backendErr.errorCode === 'BOOKING_CONFLICT' ||
        backendErr.status === 409 ||
        backendErr.message?.toLowerCase().includes('overlap')
      ) {
        toast.error('The selected time slot in this room is no longer available.')
      } else {
        toast.error(backendErr.message || 'Failed to reschedule booking.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isLoading) {
    return <Loading fullPage text="Loading booking and room details..." />
  }

  if (loadError || !originalBooking) {
    return (
      <div className="space-y-4">
        <Link
          to="/my-bookings"
          className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent"
        >
          &larr; Back to My Bookings
        </Link>
        <ErrorState
          error={loadError || { message: 'Booking not found.' }}
          title={loadError?.status === 404 ? 'Booking Not Found' : 'Error Loading Booking'}
          onRetry={loadBookingAndRooms}
        />
      </div>
    )
  }

  // SUCCESS STEP VIEW (UI-9.2)
  if (step === 'success' && replacementBooking && replacementSelection) {
    return (
      <div className="max-w-2xl mx-auto space-y-6 animate-in fade-in duration-200">
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-md p-8 text-center">
          <div className="w-16 h-16 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-4 text-3xl font-bold">
            ✓
          </div>
          <h1 className="text-2xl font-bold text-brand-navy mb-1">Booking Rescheduled Successfully</h1>
          <p className="text-sm text-brand-slate mb-6">
            The original booking has been cancelled and a replacement booking has been confirmed.
          </p>

          {/* Replacement Booking Card */}
          <div className="bg-emerald-50/50 rounded-lg p-6 text-left border border-emerald-200 space-y-3 mb-4">
            <div className="flex justify-between items-center pb-2 border-b border-emerald-200">
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-800">
                  Replacement Booking (Active)
                </span>
                <span className="font-mono font-bold text-brand-navy ml-2">#{replacementBooking.id}</span>
              </div>
              <Badge variant="success" size="sm">CONFIRMED</Badge>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Room</span>
              <span className="font-semibold text-brand-navy">
                {replacementBooking.roomName || replacementSelection.roomName}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Date</span>
              <span className="text-brand-navy">{replacementSelection.date}</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Time Window</span>
              <span className="text-brand-navy font-medium">
                {replacementSelection.startTime} – {replacementSelection.endTime}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-xs text-brand-slate">Timezone</span>
              <span className="text-xs text-brand-slate">Asia/Kolkata (IST)</span>
            </div>
            {replacementBooking.operationId && (
              <div className="flex justify-between items-center text-xs">
                <span className="text-emerald-800 font-medium">Operation ID</span>
                <span className="font-mono text-brand-navy font-bold">{replacementBooking.operationId}</span>
              </div>
            )}
            <div className="pt-2 border-t border-emerald-200">
              <span className="text-xs text-brand-slate block mb-0.5">Reason (Preserved)</span>
              <p className="text-sm text-brand-navy bg-white p-2 rounded border border-emerald-100">
                {replacementBooking.reason || originalBooking.reason}
              </p>
            </div>

            {replacementBooking.operationId && (
              <div className="pt-2 border-t border-emerald-200">
                <OperationStatus
                  operationId={replacementBooking.operationId}
                  className="mt-2"
                />
              </div>
            )}

            <div className="flex items-center gap-2 p-2.5 bg-blue-50/70 border border-blue-200 rounded-lg text-xs text-blue-800">
              <svg className="w-4 h-4 text-blue-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
              <span>
                <strong>Notification Delivery:</strong> Reschedule updates have been queued in the system outbox for background delivery.
              </span>
            </div>
          </div>

          {/* Original Booking State Card */}
          <div className="bg-brand-light-gray rounded-lg p-4 text-left border border-brand-slate/20 space-y-2 text-xs mb-6">
            <div className="flex justify-between items-center pb-1 border-b border-brand-slate/10">
              <span className="font-bold text-brand-slate">Original Booking #{originalBooking.id}</span>
              <Badge variant="error" size="sm">CANCELLED (RESCHEDULED)</Badge>
            </div>
            <div className="flex justify-between">
              <span className="text-brand-slate">Previous Room:</span>
              <span className="text-brand-navy">{originalBooking.roomName || `Room #${originalBooking.roomId}`}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-brand-slate">Previous Time:</span>
              <span className="text-brand-navy">
                {formatDateTime(originalBooking.startTime).date} &bull; {formatDateTime(originalBooking.startTime).time} – {formatDateTime(originalBooking.endTime).time}
              </span>
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

  // REVIEW STEP VIEW (UI-9.1)
  if (step === 'review' && replacementSelection) {
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
          <h1 className="text-2xl font-bold text-brand-navy mt-2">Review Reschedule</h1>
          <p className="text-sm text-brand-slate">
            Please review the original reservation and replacement reservation before confirming.
          </p>
        </div>

        {mutationError && (
          <ErrorState
            error={mutationError}
            title={
              mutationError.errorCode === 'BOOKING_CONFLICT' || mutationError.status === 409
                ? 'Reschedule Conflict'
                : 'Reschedule Failed'
            }
            message={
              mutationError.errorCode === 'BOOKING_CONFLICT' || mutationError.status === 409
                ? 'The requested time slot in this room is no longer available. Please select another time or room.'
                : undefined
            }
          />
        )}

        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs p-6 space-y-5">
          {/* Original vs Replacement Comparison */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Original Box */}
            <div className="p-4 rounded-lg bg-slate-50 border border-slate-200 space-y-2 text-xs">
              <div className="flex justify-between items-center pb-1 border-b border-slate-200">
                <span className="font-bold text-slate-700 uppercase">Original Booking</span>
                <Badge variant="neutral" size="sm">Will be CANCELLED</Badge>
              </div>
              <div>
                <span className="text-brand-slate block">Room:</span>
                <span className="font-semibold text-brand-navy text-sm">
                  {originalBooking.roomName || `Room #${originalBooking.roomId}`}
                </span>
              </div>
              <div>
                <span className="text-brand-slate block">Date & Time:</span>
                <span className="text-brand-navy">
                  {formatDateTime(originalBooking.startTime).date} ({formatDateTime(originalBooking.startTime).time} – {formatDateTime(originalBooking.endTime).time})
                </span>
              </div>
            </div>

            {/* Replacement Box */}
            <div className="p-4 rounded-lg bg-blue-50/50 border border-blue-200 space-y-2 text-xs">
              <div className="flex justify-between items-center pb-1 border-b border-blue-200">
                <span className="font-bold text-blue-900 uppercase">Replacement Booking</span>
                <Badge variant="success" size="sm">Will be CONFIRMED</Badge>
              </div>
              <div>
                <span className="text-brand-slate block">Room:</span>
                <span className="font-semibold text-brand-navy text-sm">
                  {replacementSelection.roomName}
                </span>
              </div>
              <div>
                <span className="text-brand-slate block">Date & Time:</span>
                <span className="text-brand-navy font-semibold">
                  {replacementSelection.date} ({replacementSelection.startTime} – {replacementSelection.endTime})
                </span>
              </div>
            </div>
          </div>

          <div>
            <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">
              Booking Reason (Carried Forward)
            </span>
            <p className="text-sm text-brand-navy mt-1 p-3 bg-brand-light-gray rounded-md border border-brand-slate/10 whitespace-pre-wrap">
              {originalBooking.reason}
            </p>
          </div>

          <div className="rounded-md bg-amber-50 p-3 text-xs text-amber-900 border border-amber-200">
            <strong>Important:</strong> This will cancel the existing booking (#{originalBooking.id}) and create a replacement confirmed booking in an atomic backend operation.
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-brand-slate/10">
            <Button
              variant="outline"
              onClick={() => setStep('form')}
              disabled={isSubmitting}
            >
              Back
            </Button>
            <Button
              variant="primary"
              loading={isSubmitting}
              onClick={() => setIsConfirmOpen(true)}
            >
              Confirm Reschedule
            </Button>
          </div>
        </div>

        <ConfirmDialog
          isOpen={isConfirmOpen}
          title="Confirm Reschedule?"
          description={`Are you sure you want to reschedule booking #${originalBooking.id} to ${replacementSelection.date} from ${replacementSelection.startTime} to ${replacementSelection.endTime}?`}
          confirmLabel="Yes, Reschedule"
          cancelLabel="Keep Current Booking"
          loading={isSubmitting}
          onConfirm={handleExecuteReschedule}
          onCancel={() => setIsConfirmOpen(false)}
        />
      </div>
    )
  }

  // FORM STEP VIEW (UI-9.1)
  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <Link
          to="/my-bookings"
          className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent"
        >
          &larr; Back to My Bookings
        </Link>
        <h1 className="text-2xl font-bold text-brand-navy mt-2">Reschedule Booking #{originalBooking.id}</h1>
        <p className="text-sm text-brand-slate">
          Select a new room, date, or time slot for this reservation.
        </p>
      </div>

      {/* Original Booking Context Card */}
      <div className="bg-brand-white p-5 rounded-lg border border-brand-slate/20 shadow-xs space-y-2">
        <div className="flex justify-between items-center pb-2 border-b border-brand-slate/10">
          <span className="text-xs font-bold uppercase tracking-wider text-brand-slate">
            Original Booking Details
          </span>
          <Badge variant="success" size="sm">{originalBooking.status}</Badge>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div>
            <span className="text-brand-slate block">Current Room:</span>
            <span className="font-semibold text-brand-navy text-sm">
              {originalBooking.roomName || `Room #${originalBooking.roomId}`}
            </span>
          </div>
          <div>
            <span className="text-brand-slate block">Current Schedule:</span>
            <span className="font-medium text-brand-navy">
              {formatDateTime(originalBooking.startTime).date} &bull; {formatDateTime(originalBooking.startTime).time} – {formatDateTime(originalBooking.endTime).time}
            </span>
          </div>
        </div>
      </div>

      {/* Reschedule Form */}
      <form
        onSubmit={handleProceedToReview}
        className="bg-brand-white p-6 rounded-lg border border-brand-slate/20 shadow-xs space-y-5"
        noValidate
      >
        <Select
          label="Target Room"
          value={selectedRoomId}
          error={validationErrors.roomId}
          onChange={(e) => setSelectedRoomId(e.target.value)}
          options={availableRooms.map((r) => ({
            value: r.id,
            label: `${r.name} (${r.location ? r.location.name : 'Active Site'}) — Cap: ${r.capacity}`,
          }))}
        />

        <DatePicker
          label="New Booking Date"
          required
          value={date}
          error={validationErrors.date}
          onChange={(e) => setDate(e.target.value)}
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <TimePicker
            label="New Start Time (15-min intervals)"
            required
            step={900}
            value={startTime}
            error={validationErrors.startTime}
            onChange={(e) => setStartTime(e.target.value)}
          />

          <TimePicker
            label="New End Time (15-min intervals)"
            required
            step={900}
            value={endTime}
            error={validationErrors.endTime}
            onChange={(e) => setEndTime(e.target.value)}
          />
        </div>

        <div className="text-xs text-brand-slate">
          Authoritative Timezone: <span className="text-brand-navy font-semibold">Asia/Kolkata (IST)</span>
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t border-brand-slate/10">
          <Button
            variant="outline"
            onClick={() => navigate('/my-bookings')}
          >
            Cancel
          </Button>
          <Button type="submit" variant="primary">
            Review Reschedule &rarr;
          </Button>
        </div>
      </form>
    </div>
  )
}

export default RescheduleBookingPage
