import React, { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { roomService } from '@/api/roomService'
import { recurringBookingService } from '@/api/recurringBookingService'
import type {
  CreateRecurringBookingPayload,
  RecurrenceFrequency,
  RecurringConfirmationResponse,
  RecurringOccurrenceResult,
  RecurringPreviewResponse,
} from '@/types/recurring'
import type { RoomResponse } from '@/types/room'
import type { BackendError } from '@/types/api'
import {
  Badge,
  Button,
  ConfirmDialog,
  DatePicker,
  Dialog,
  ErrorState,
  Input,
  Loading,
  Select,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
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

const DAYS_OF_WEEK = [
  { key: 'MONDAY', label: 'Mon' },
  { key: 'TUESDAY', label: 'Tue' },
  { key: 'WEDNESDAY', label: 'Wed' },
  { key: 'THURSDAY', label: 'Thu' },
  { key: 'FRIDAY', label: 'Fri' },
  { key: 'SATURDAY', label: 'Sat' },
  { key: 'SUNDAY', label: 'Sun' },
]

export const RecurringBookingPage: React.FC = () => {
  const { roomId, seriesId } = useParams<{ roomId?: string; seriesId?: string }>()
  const navigate = useNavigate()

  // Room context & candidates
  const [availableRooms, setAvailableRooms] = useState<RoomResponse[]>([])
  const [selectedRoomId, setSelectedRoomId] = useState<string>(roomId || '')
  const [isLoadingRooms, setIsLoadingRooms] = useState<boolean>(true)

  // Series fetch on mount / stable route
  const [isSeriesLoading, setIsSeriesLoading] = useState<boolean>(false)
  const [seriesFetchError, setSeriesFetchError] = useState<BackendError | null>(null)

  // Form parameters (UI-10.1)
  const [seriesName, setSeriesName] = useState<string>('')
  const [frequency, setFrequency] = useState<RecurrenceFrequency>('WEEKLY')
  const [startDate, setStartDate] = useState<string>(() => new Date().toISOString().split('T')[0])
  const [endType, setEndType] = useState<'date' | 'count'>('count')
  const [endDate, setEndDate] = useState<string>('')
  const [occurrenceCount, setOccurrenceCount] = useState<string>('4')
  const [selectedDays, setSelectedDays] = useState<string[]>(['MONDAY'])
  const [dayOfMonth, setDayOfMonth] = useState<string>('15')
  const [startTime, setStartTime] = useState<string>('10:00')
  const [endTime, setEndTime] = useState<string>('11:00')
  const [reason, setReason] = useState<string>('')
  const [formErrors, setFormErrors] = useState<Record<string, string>>({})

  // Flow states: 'form' | 'preview' | 'result'
  const [step, setStep] = useState<'form' | 'preview' | 'result'>('form')

  // Preview state (UI-10.2)
  const [isPreviewLoading, setIsPreviewLoading] = useState<boolean>(false)
  const [previewError, setPreviewError] = useState<BackendError | null>(null)
  const [previewData, setPreviewData] = useState<RecurringPreviewResponse | null>(null)

  // Confirmation & Idempotency state
  const [isConfirmOpen, setIsConfirmOpen] = useState<boolean>(false)
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false)
  const [mutationError, setMutationError] = useState<BackendError | null>(null)
  const [seriesResult, setSeriesResult] = useState<RecurringConfirmationResponse | null>(null)
  const idempotencyKeyRef = useRef<string>(generateUUID())

  // Conflict Resolution Modal states (UI-10.4)
  const [conflictTarget, setConflictTarget] = useState<RecurringOccurrenceResult | null>(null)
  const [conflictDisplayNumber, setConflictDisplayNumber] = useState<number>(1)
  const [isAlternateRoomModalOpen, setIsAlternateRoomModalOpen] = useState<boolean>(false)
  const [selectedAlternateRoomId, setSelectedAlternateRoomId] = useState<string>('')
  const [isResolvingAlternate, setIsResolvingAlternate] = useState<boolean>(false)

  const [isContactAdminModalOpen, setIsContactAdminModalOpen] = useState<boolean>(false)
  const [adminMessage, setAdminMessage] = useState<string>('')
  const [isSendingAdminMsg, setIsSendingAdminMsg] = useState<boolean>(false)

  // Fetch series details from backend
  const loadSeries = useCallback(async (id: number) => {
    setIsSeriesLoading(true)
    setSeriesFetchError(null)
    try {
      const data = await recurringBookingService.getSeries(id)
      const firstRoomName = data.occurrences?.find((o) => o.roomName)?.roomName || 'Room'
      const firstRoomId = data.occurrences?.find((o) => o.roomId)?.roomId || 0

      setSeriesResult({
        seriesId: data.id,
        seriesName: data.seriesName || `Recurring Series #${data.id}`,
        roomId: firstRoomId,
        roomName: firstRoomName,
        frequency: data.frequency,
        seriesStatus: data.status,
        totalOccurrences: data.totalOccurrences || data.occurrences?.length || 0,
        confirmedCount: data.confirmedCount ?? 0,
        conflictCount: data.conflictCount ?? 0,
        skippedCount: data.skippedCount ?? 0,
        occurrences: data.occurrences || [],
      })
      setStep('result')
    } catch (err: unknown) {
      setSeriesFetchError(err as BackendError)
    } finally {
      setIsSeriesLoading(false)
    }
  }, [])

  // Re-fetch series on mount or seriesId route param change
  useEffect(() => {
    if (seriesId) {
      const num = Number(seriesId)
      if (!isNaN(num) && num > 0) {
        loadSeries(num)
      }
    }
  }, [seriesId, loadSeries])

  // Load available rooms on mount
  useEffect(() => {
    async function loadRooms() {
      setIsLoadingRooms(true)
      try {
        const res = await roomService.getRooms({ page: 0, size: 100 })
        setAvailableRooms(res.content)
        if (!selectedRoomId && res.content.length > 0) {
          setSelectedRoomId(String(res.content[0].id))
        }
      } catch (err) {
        console.error('Failed to load rooms:', err)
      } finally {
        setIsLoadingRooms(false)
      }
    }
    loadRooms()
  }, [selectedRoomId])

  const toggleDayOfWeek = (day: string) => {
    setSelectedDays((prev) =>
      prev.includes(day) ? (prev.length > 1 ? prev.filter((d) => d !== day) : prev) : [...prev, day]
    )
  }

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {}

    if (!selectedRoomId) {
      errors.roomId = 'Please select a room.'
    }

    if (!startDate) {
      errors.startDate = 'Start date is required.'
    }

    if (endType === 'date') {
      if (!endDate) {
        errors.endDate = 'End date is required.'
      } else if (endDate < startDate) {
        errors.endDate = 'End date cannot be before start date.'
      }
    } else {
      const count = Number(occurrenceCount)
      if (!occurrenceCount || count < 1 || count > 52) {
        errors.occurrenceCount = 'Occurrence count must be between 1 and 52.'
      }
    }

    if (frequency === 'WEEKLY' && selectedDays.length === 0) {
      errors.daysOfWeek = 'Please select at least one day of the week.'
    }

    if (frequency === 'MONTHLY') {
      const dom = Number(dayOfMonth)
      if (!dayOfMonth || dom < 1 || dom > 31) {
        errors.dayOfMonth = 'Day of month must be between 1 and 31.'
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

    if (startTime && endTime && startTime === endTime) {
      errors.endTime = 'End time must be after start time.'
    }

    const trimmedReason = reason.trim()
    if (!trimmedReason) {
      errors.reason = 'Reason is required.'
    } else if (trimmedReason.length > 500) {
      errors.reason = 'Reason cannot exceed 500 characters.'
    }

    setFormErrors(errors)
    return Object.keys(errors).length === 0
  }

  const constructPayload = (): CreateRecurringBookingPayload => {
    return {
      roomId: Number(selectedRoomId),
      seriesName: seriesName.trim() || undefined,
      frequency,
      startDate,
      endDate: endType === 'date' ? endDate : undefined,
      occurrenceCount: endType === 'count' ? Number(occurrenceCount) : undefined,
      startLocalTime: startTime,
      endLocalTime: endTime,
      daysOfWeek: frequency === 'WEEKLY' ? selectedDays : undefined,
      dayOfMonth: frequency === 'MONTHLY' ? Number(dayOfMonth) : undefined,
      reason: reason.trim(),
    }
  }

  // Preview execution (UI-10.2)
  const handleGeneratePreview = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validateForm()) return

    setIsPreviewLoading(true)
    setPreviewError(null)

    try {
      const payload = constructPayload()
      const preview = await recurringBookingService.previewSeries(payload)
      setPreviewData(preview)
      idempotencyKeyRef.current = generateUUID()
      setStep('preview')
    } catch (err: unknown) {
      setPreviewError(err as BackendError)
      toast.error('Failed to generate recurrence preview.')
    } finally {
      setIsPreviewLoading(false)
    }
  }

  // Confirmation execution (UI-10.3)
  const handleConfirmSeries = async () => {
    setIsSubmitting(true)
    setMutationError(null)

    try {
      const payload = constructPayload()
      const result = await recurringBookingService.createAndConfirmSeries(
        payload,
        idempotencyKeyRef.current
      )

      setSeriesResult(result)
      setStep('result')
      setIsConfirmOpen(false)

      if (result.seriesStatus === 'PARTIALLY_CONFIRMED' || result.conflictCount > 0) {
        toast.warning(`Series created with ${result.conflictCount} conflicted occurrence(s).`)
      } else {
        toast.success('Recurring series confirmed successfully!')
      }

      // Navigate to stable series URL
      navigate(`/bookings/recurring/${result.seriesId}`)
    } catch (err: unknown) {
      const backendErr = err as BackendError
      setMutationError(backendErr)
      setIsConfirmOpen(false)
      toast.error(backendErr.message || 'Failed to create recurring booking series.')
    } finally {
      setIsSubmitting(false)
    }
  }

  // Conflict Resolution: Delete / Cancel Confirmed Occurrence (UI-10.4)
  // Sends actual backend occurrenceIndex while using human-friendly displayNumber for feedback
  const handleDeleteOccurrence = async (occurrence: RecurringOccurrenceResult, displayNumber: number) => {
    const currentSeriesId = seriesResult?.seriesId || (seriesId ? Number(seriesId) : null)
    if (!currentSeriesId) return
    try {
      await recurringBookingService.cancelOccurrence(
        currentSeriesId,
        occurrence.occurrenceIndex,
        'Cancelled by user'
      )
      toast.success(`Occurrence #${displayNumber} cancelled.`)

      // Re-fetch authoritative series data from backend
      await loadSeries(currentSeriesId)
    } catch (err: unknown) {
      toast.error((err as BackendError).message || 'Failed to cancel occurrence.')
    }
  }

  // Conflict Resolution: Skip Unresolved Occurrence
  const handleSkipOccurrence = async (occurrence: RecurringOccurrenceResult, displayNumber: number) => {
    const currentSeriesId = seriesResult?.seriesId || (seriesId ? Number(seriesId) : null)
    if (!currentSeriesId) return
    try {
      await recurringBookingService.skipOccurrence(
        currentSeriesId,
        occurrence.occurrenceIndex,
        'Skipped by user'
      )
      toast.success(`Occurrence #${displayNumber} was skipped.`)

      // Re-fetch authoritative series data from backend
      await loadSeries(currentSeriesId)
    } catch (err: unknown) {
      toast.error((err as BackendError).message || 'Failed to skip occurrence.')
    }
  }

  // Conflict Resolution: Alternate Room (UI-10.4)
  const handleOpenAlternateRoom = (occurrence: RecurringOccurrenceResult, displayNumber: number) => {
    setConflictTarget(occurrence)
    setConflictDisplayNumber(displayNumber)
    const otherRooms = availableRooms.filter((r) => r.id !== occurrence.roomId)
    if (otherRooms.length > 0) {
      setSelectedAlternateRoomId(String(otherRooms[0].id))
    }
    setIsAlternateRoomModalOpen(true)
  }

  const handleExecuteAlternateRoom = async () => {
    const currentSeriesId = seriesResult?.seriesId || (seriesId ? Number(seriesId) : null)
    if (!currentSeriesId || !conflictTarget || !selectedAlternateRoomId) return
    setIsResolvingAlternate(true)

    try {
      const altRoomId = Number(selectedAlternateRoomId)
      const updatedBooking = await recurringBookingService.resolveAlternateRoom(
        currentSeriesId,
        conflictTarget.occurrenceIndex,
        altRoomId
      )

      toast.success(`Occurrence #${conflictDisplayNumber} booked in ${updatedBooking.roomName}!`)
      setIsAlternateRoomModalOpen(false)

      // Re-fetch authoritative series data from backend
      await loadSeries(currentSeriesId)
    } catch (err: unknown) {
      toast.error((err as BackendError).message || 'Failed to assign alternate room.')
    } finally {
      setIsResolvingAlternate(false)
    }
  }

  // Conflict Resolution: Contact Admin (UI-10.4)
  const handleOpenContactAdmin = (occurrence: RecurringOccurrenceResult, displayNumber: number) => {
    setConflictTarget(occurrence)
    setConflictDisplayNumber(displayNumber)
    setAdminMessage(
      `Conflict assistance request for Occurrence #${displayNumber} on ${occurrence.date} (${occurrence.startTime} - ${occurrence.endTime}).`
    )
    setIsContactAdminModalOpen(true)
  }

  const handleExecuteContactAdmin = async () => {
    const currentSeriesId = seriesResult?.seriesId || (seriesId ? Number(seriesId) : null)
    if (!currentSeriesId || !conflictTarget || !adminMessage.trim()) return
    setIsSendingAdminMsg(true)

    try {
      await recurringBookingService.contactAdmin(
        currentSeriesId,
        conflictTarget.occurrenceIndex,
        adminMessage.trim()
      )
      toast.success('Admin assistance request submitted successfully!')
      setIsContactAdminModalOpen(false)
    } catch (err: unknown) {
      toast.error((err as BackendError).message || 'Failed to submit admin request.')
    } finally {
      setIsSendingAdminMsg(false)
    }
  }

  const formatIsoDate = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    } catch {
      return isoString
    }
  }

  const formatIsoTime = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      })
    } catch {
      return isoString
    }
  }

  if (seriesId && isSeriesLoading) {
    return <Loading fullPage text="Loading recurring series..." />
  }

  if (seriesId && seriesFetchError) {
    return (
      <div className="space-y-4 max-w-2xl mx-auto py-8">
        <Link
          to="/my-bookings"
          className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent"
        >
          &larr; Back to My Bookings
        </Link>
        <ErrorState
          error={seriesFetchError}
          title={seriesFetchError.status === 404 ? 'Recurring Series Not Found' : 'Cannot load recurring series'}
          onRetry={() => seriesId && loadSeries(Number(seriesId))}
        />
      </div>
    )
  }

  if (isLoadingRooms && !seriesId) {
    return <Loading fullPage text="Loading room details..." />
  }

  // ==========================================
  // STAGE 3: SERIES RESULT & CONFLICT RESOLUTION (UI-10.3 & UI-10.4)
  // ==========================================
  if (step === 'result' && seriesResult) {
    const isPartial = seriesResult.seriesStatus === 'PARTIALLY_CONFIRMED' || seriesResult.conflictCount > 0

    return (
      <div className="space-y-6 max-w-4xl mx-auto animate-in fade-in duration-200">
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs p-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-4 border-b border-brand-slate/10 gap-4">
            <div>
              <div className="flex items-center gap-3">
                <h1 className="text-2xl font-bold text-brand-navy">
                  {seriesResult.seriesName || `Recurring Series #${seriesResult.seriesId}`}
                </h1>
                <Badge variant={isPartial ? 'warning' : 'success'} size="md">
                  {seriesResult.seriesStatus}
                </Badge>
              </div>
              <p className="text-xs text-brand-slate mt-1">
                Frequency: <span className="font-semibold text-brand-navy">{seriesResult.frequency}</span> &bull; Target Room: <span className="font-semibold text-brand-navy">{seriesResult.roomName}</span>
              </p>
            </div>
            <div className="flex gap-2">
              <Link to="/my-bookings">
                <Button variant="primary" size="sm">
                  View My Bookings &rarr;
                </Button>
              </Link>
              <Link to="/rooms">
                <Button variant="outline" size="sm">
                  Rooms
                </Button>
              </Link>
            </div>
          </div>

          {/* Series Metrics */}
          <div className="grid grid-cols-3 gap-4 pt-4 text-center">
            <div className="p-3 bg-slate-50 rounded-md border border-slate-200">
              <span className="text-xs text-brand-slate block">Total Occurrences</span>
              <span className="text-xl font-bold text-brand-navy">{seriesResult.totalOccurrences}</span>
            </div>
            <div className="p-3 bg-emerald-50 rounded-md border border-emerald-200">
              <span className="text-xs text-emerald-800 block">Confirmed</span>
              <span className="text-xl font-bold text-emerald-700">{seriesResult.confirmedCount}</span>
            </div>
            <div className="p-3 bg-amber-50 rounded-md border border-amber-200">
              <span className="text-xs text-amber-800 block">Conflicts</span>
              <span className="text-xl font-bold text-amber-700">{seriesResult.conflictCount}</span>
            </div>
          </div>

          {isPartial && (
            <div className="mt-4 p-3 bg-amber-50 border border-amber-200 rounded-md text-xs text-amber-900">
              <strong>Partial Confirmation:</strong> Some occurrences could not be confirmed due to schedule conflicts. You can resolve each conflict below by selecting an alternate room, deleting the occurrence, or contacting the administrator.
            </div>
          )}
        </div>

        {/* Occurrence Results Table (UI-10.3 & UI-10.4) */}
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs overflow-hidden">
          <div className="p-4 border-b border-brand-slate/10 bg-slate-50/50">
            <h2 className="text-sm font-bold text-brand-navy">Series Occurrences Status</h2>
          </div>
          <Table columnCount={6}>
            <TableHeader>
              <TableRow>
                <TableHead>#</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Time Window</TableHead>
                <TableHead>Room</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {seriesResult.occurrences.map((occ, idx) => {
                const displayNumber = idx + 1
                const isConflict = occ.status === 'CONFLICT'
                const isConfirmed = occ.status === 'CONFIRMED'

                return (
                  <TableRow key={occ.occurrenceIndex}>
                    <TableCell className="font-mono text-xs text-brand-slate">
                      #{displayNumber}
                    </TableCell>
                    <TableCell className="font-medium text-brand-navy">
                      {formatIsoDate(occ.date ? `${occ.date}T00:00:00` : occ.startTime)}
                    </TableCell>
                    <TableCell className="text-brand-slate text-xs whitespace-nowrap">
                      {formatIsoTime(occ.startTime)} – {formatIsoTime(occ.endTime)}
                    </TableCell>
                    <TableCell className="text-brand-navy text-sm font-medium">
                      {occ.roomName || `Room #${occ.roomId}`}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant={isConfirmed ? 'success' : isConflict ? 'error' : 'neutral'}
                        size="sm"
                      >
                        {occ.status}
                      </Badge>
                      {occ.conflictReason && (
                        <p className="text-xs text-red-600 mt-0.5">{occ.conflictReason}</p>
                      )}
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap">
                      {isConflict ? (
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => handleOpenAlternateRoom(occ, displayNumber)}
                          >
                            Alt Room
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleSkipOccurrence(occ, displayNumber)}
                          >
                            Skip
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenContactAdmin(occ, displayNumber)}
                          >
                            Admin
                          </Button>
                        </div>
                      ) : isConfirmed ? (
                        <div className="flex items-center justify-end gap-2">
                          <span className="text-xs text-emerald-600 font-medium">✓ Booked</span>
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => handleDeleteOccurrence(occ, displayNumber)}
                          >
                            Cancel
                          </Button>
                        </div>
                      ) : occ.status === 'SKIPPED' ? (
                        <span className="text-xs text-brand-slate font-medium">⊘ Skipped</span>
                      ) : occ.status === 'CANCELLED' ? (
                        <span className="text-xs text-red-500 font-medium">✕ Cancelled</span>
                      ) : (
                        <span className="text-xs text-brand-slate">{occ.status}</span>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>

        {/* Modal: Select Alternate Room (UI-10.4) */}
        <Dialog
          isOpen={isAlternateRoomModalOpen}
          title={`Select Alternate Room for Occurrence #${conflictDisplayNumber}`}
          onClose={() => !isResolvingAlternate && setIsAlternateRoomModalOpen(false)}
        >
          {conflictTarget && (
            <div className="space-y-4 py-2 text-left">
              <p className="text-xs text-brand-slate">
                Select another available room for {conflictTarget.date} ({formatIsoTime(conflictTarget.startTime)} – {formatIsoTime(conflictTarget.endTime)}).
              </p>
              <Select
                label="Choose Alternate Room"
                value={selectedAlternateRoomId}
                onChange={(e) => setSelectedAlternateRoomId(e.target.value)}
                options={availableRooms
                  .filter((r) => r.id !== conflictTarget.roomId)
                  .map((r) => ({
                    value: r.id,
                    label: `${r.name} (${r.location ? r.location.name : 'Active Site'}) — Cap: ${r.capacity}`,
                  }))}
              />
              <div className="flex justify-end gap-2 pt-3 border-t border-brand-slate/10">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={isResolvingAlternate}
                  onClick={() => setIsAlternateRoomModalOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  loading={isResolvingAlternate}
                  onClick={handleExecuteAlternateRoom}
                >
                  Book Alternate Room
                </Button>
              </div>
            </div>
          )}
        </Dialog>

        {/* Modal: Contact Admin (UI-10.4) */}
        <Dialog
          isOpen={isContactAdminModalOpen}
          title={`Contact Admin regarding Occurrence #${conflictDisplayNumber}`}
          onClose={() => !isSendingAdminMsg && setIsContactAdminModalOpen(false)}
        >
          {conflictTarget && (
            <div className="space-y-4 py-2 text-left">
              <div>
                <label className="block text-xs font-semibold text-brand-slate mb-1">
                  Message for Administrator
                </label>
                <textarea
                  rows={3}
                  maxLength={2000}
                  value={adminMessage}
                  onChange={(e) => setAdminMessage(e.target.value)}
                  className="w-full border border-brand-slate/30 rounded-md p-2 text-sm text-brand-navy focus:ring-2 focus:ring-brand-accent focus:outline-none"
                />
              </div>
              <div className="flex justify-end gap-2 pt-3 border-t border-brand-slate/10">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={isSendingAdminMsg}
                  onClick={() => setIsContactAdminModalOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  loading={isSendingAdminMsg}
                  onClick={handleExecuteContactAdmin}
                >
                  Send Request
                </Button>
              </div>
            </div>
          )}
        </Dialog>
      </div>
    )
  }

  // ==========================================
  // STAGE 2: PREVIEW & OCCURRENCE EVALUATION (UI-10.2)
  // ==========================================
  if (step === 'preview' && previewData) {
    const hasConflicts = previewData.conflictCount > 0

    return (
      <div className="space-y-6 max-w-3xl mx-auto animate-in fade-in duration-200">
        <div>
          <button
            type="button"
            onClick={() => setStep('form')}
            className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent cursor-pointer"
          >
            &larr; Back to Recurrence Form
          </button>
          <h1 className="text-2xl font-bold text-brand-navy mt-2">Recurring Series Preview</h1>
          <p className="text-sm text-brand-slate">
            Review the calculated occurrences and availability evaluation before confirming.
          </p>
        </div>

        {mutationError && (
          <ErrorState
            error={mutationError}
            title="Failed to Create Series"
            onRetry={handleConfirmSeries}
          />
        )}

        {/* Preview Summary */}
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs p-6 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-3 border-b border-brand-slate/10 gap-3">
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">Target Room</span>
              <p className="text-lg font-bold text-brand-navy">{previewData.roomName}</p>
            </div>
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-brand-slate">Frequency</span>
              <p className="text-sm font-medium text-brand-navy">{previewData.frequency}</p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3 text-center">
            <div className="p-3 bg-slate-50 rounded-md border border-slate-200">
              <span className="text-xs text-brand-slate block">Total</span>
              <span className="text-lg font-bold text-brand-navy">{previewData.totalOccurrences}</span>
            </div>
            <div className="p-3 bg-emerald-50 rounded-md border border-emerald-200">
              <span className="text-xs text-emerald-800 block">Available</span>
              <span className="text-lg font-bold text-emerald-700">{previewData.availableCount}</span>
            </div>
            <div className="p-3 bg-amber-50 rounded-md border border-amber-200">
              <span className="text-xs text-amber-800 block">Conflicts</span>
              <span className="text-lg font-bold text-amber-700">{previewData.conflictCount}</span>
            </div>
          </div>

          <div className="rounded-md bg-blue-50 p-3 text-xs text-blue-900 border border-blue-200">
            <strong>Informational Preview:</strong> Occurrences are evaluated against current room reservations. Availability is definitively acquired and locked upon final confirmation.
          </div>
        </div>

        {/* Occurrence Preview Table */}
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs overflow-hidden">
          <div className="p-4 border-b border-brand-slate/10 bg-slate-50/50">
            <h2 className="text-sm font-bold text-brand-navy">Generated Occurrences Schedule</h2>
          </div>
          <Table columnCount={4}>
            <TableHeader>
              <TableRow>
                <TableHead>#</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Time Window</TableHead>
                <TableHead>Availability</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {previewData.occurrences.map((occ, idx) => {
                const displayNumber = idx + 1
                const isAvailable = occ.availability === 'AVAILABLE'
                return (
                  <TableRow key={occ.occurrenceIndex}>
                    <TableCell className="font-mono text-xs text-brand-slate">
                      #{displayNumber}
                    </TableCell>
                    <TableCell className="font-medium text-brand-navy">
                      {formatIsoDate(occ.date ? `${occ.date}T00:00:00` : occ.startTime)}
                    </TableCell>
                    <TableCell className="text-brand-slate text-xs whitespace-nowrap">
                      {formatIsoTime(occ.startTime)} – {formatIsoTime(occ.endTime)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={isAvailable ? 'success' : 'error'} size="sm">
                        {occ.availability}
                      </Badge>
                      {occ.conflictReason && (
                        <span className="text-xs text-red-600 ml-2">{occ.conflictReason}</span>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <Button
            variant="outline"
            onClick={() => setStep('form')}
            disabled={isSubmitting}
          >
            Edit Parameters
          </Button>
          <Button
            variant="primary"
            loading={isSubmitting}
            onClick={() => setIsConfirmOpen(true)}
          >
            {hasConflicts ? 'Confirm Series (Allow Partial)' : 'Confirm Recurring Booking'}
          </Button>
        </div>

        <ConfirmDialog
          isOpen={isConfirmOpen}
          title="Confirm Recurring Booking Series?"
          description={`Are you sure you want to create this ${previewData.frequency.toLowerCase()} series with ${previewData.totalOccurrences} occurrences for ${previewData.roomName}?`}
          confirmLabel="Yes, Confirm Series"
          cancelLabel="Cancel"
          loading={isSubmitting}
          onConfirm={handleConfirmSeries}
          onCancel={() => setIsConfirmOpen(false)}
        />
      </div>
    )
  }

  // ==========================================
  // STAGE 1: RECURRING BOOKING FORM (UI-10.1)
  // ==========================================
  const selectedRoomObj = availableRooms.find((r) => String(r.id) === String(selectedRoomId))

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <Link
          to="/rooms"
          className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent"
        >
          &larr; Back to Rooms
        </Link>
        <h1 className="text-2xl font-bold text-brand-navy mt-2">New Recurring Booking</h1>
        <p className="text-sm text-brand-slate">
          Schedule recurring reservations across active dates (Daily, Weekly, or Monthly).
        </p>
      </div>

      {previewError && (
        <ErrorState
          error={previewError}
          title="Preview Generation Failed"
        />
      )}

      <form
        onSubmit={handleGeneratePreview}
        className="bg-brand-white p-6 rounded-lg border border-brand-slate/20 shadow-xs space-y-5"
        noValidate
      >
        {/* Room Selection */}
        <Select
          label="Target Room"
          value={selectedRoomId}
          error={formErrors.roomId}
          onChange={(e) => setSelectedRoomId(e.target.value)}
          options={availableRooms.map((r) => ({
            value: r.id,
            label: `${r.name} (${r.location ? r.location.name : 'Active Site'}) — Cap: ${r.capacity}`,
          }))}
        />

        {selectedRoomObj && (
          <div className="text-xs text-brand-slate bg-brand-light-gray p-3 rounded-md border border-brand-slate/10">
            Selected: <span className="font-semibold text-brand-navy">{selectedRoomObj.name}</span> &bull; Timezone: <span className="font-semibold text-brand-navy">Asia/Kolkata (IST)</span>
          </div>
        )}

        {/* Series Name */}
        <Input
          label="Series Name (Optional)"
          placeholder="e.g. Weekly Engineering Sync"
          value={seriesName}
          onChange={(e) => setSeriesName(e.target.value)}
        />

        {/* Recurrence Frequency */}
        <div>
          <label className="block text-sm font-medium text-brand-navy mb-2 text-left">
            Recurrence Frequency <span className="text-red-500">*</span>
          </label>
          <div className="grid grid-cols-3 gap-2">
            {(['DAILY', 'WEEKLY', 'MONTHLY'] as RecurrenceFrequency[]).map((f) => (
              <button
                key={f}
                type="button"
                onClick={() => setFrequency(f)}
                className={`py-2 px-3 rounded-md text-xs font-semibold border transition-colors cursor-pointer ${
                  frequency === f
                    ? 'bg-brand-navy text-brand-white border-brand-navy'
                    : 'bg-white text-brand-slate border-brand-slate/30 hover:bg-slate-50'
                }`}
              >
                {f}
              </button>
            ))}
          </div>
        </div>

        {/* Weekly Day Selection */}
        {frequency === 'WEEKLY' && (
          <div>
            <label className="block text-xs font-semibold uppercase text-brand-slate mb-2 text-left">
              Days of Week <span className="text-red-500">*</span>
            </label>
            <div className="flex flex-wrap gap-2">
              {DAYS_OF_WEEK.map((d) => {
                const isSelected = selectedDays.includes(d.key)
                return (
                  <button
                    key={d.key}
                    type="button"
                    onClick={() => toggleDayOfWeek(d.key)}
                    className={`px-3 py-1.5 rounded-md text-xs font-semibold border transition-colors cursor-pointer ${
                      isSelected
                        ? 'bg-brand-navy text-brand-white border-brand-navy'
                        : 'bg-brand-light-gray text-brand-navy border-brand-slate/20 hover:bg-slate-200'
                    }`}
                  >
                    {d.label}
                  </button>
                )
              })}
            </div>
            {formErrors.daysOfWeek && (
              <p className="text-xs text-red-600 mt-1">{formErrors.daysOfWeek}</p>
            )}
          </div>
        )}

        {/* Monthly Day of Month Selection */}
        {frequency === 'MONTHLY' && (
          <Input
            label="Day of Month (1 - 31)"
            type="number"
            min="1"
            max="31"
            required
            value={dayOfMonth}
            error={formErrors.dayOfMonth}
            onChange={(e) => setDayOfMonth(e.target.value)}
          />
        )}

        {/* Start Date */}
        <DatePicker
          label="Start Date"
          required
          value={startDate}
          error={formErrors.startDate}
          onChange={(e) => setStartDate(e.target.value)}
        />

        {/* End Condition Toggle */}
        <div className="space-y-3 pt-1">
          <label className="block text-xs font-semibold uppercase text-brand-slate text-left">
            Recurrence End Limit
          </label>
          <div className="flex gap-4">
            <label className="inline-flex items-center text-xs font-medium text-brand-navy cursor-pointer">
              <input
                type="radio"
                name="endType"
                checked={endType === 'count'}
                onChange={() => setEndType('count')}
                className="mr-1.5 text-brand-accent focus:ring-brand-accent"
              />
              Number of occurrences
            </label>
            <label className="inline-flex items-center text-xs font-medium text-brand-navy cursor-pointer">
              <input
                type="radio"
                name="endType"
                checked={endType === 'date'}
                onChange={() => setEndType('date')}
                className="mr-1.5 text-brand-accent focus:ring-brand-accent"
              />
              Until specific end date
            </label>
          </div>

          {endType === 'count' ? (
            <Input
              label="Occurrence Count (1 - 52)"
              type="number"
              min="1"
              max="52"
              value={occurrenceCount}
              error={formErrors.occurrenceCount}
              onChange={(e) => setOccurrenceCount(e.target.value)}
            />
          ) : (
            <DatePicker
              label="End Date"
              required
              value={endDate}
              error={formErrors.endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          )}
        </div>

        {/* Time Interval */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <TimePicker
            label="Start Time (15-min intervals)"
            required
            step={900}
            value={startTime}
            error={formErrors.startTime}
            onChange={(e) => setStartTime(e.target.value)}
          />
          <TimePicker
            label="End Time (15-min intervals)"
            required
            step={900}
            value={endTime}
            error={formErrors.endTime}
            onChange={(e) => setEndTime(e.target.value)}
          />
        </div>

        {/* Reason */}
        <div>
          <label htmlFor="recurring-reason" className="block text-sm font-medium text-brand-navy mb-1 text-left">
            Booking Reason <span className="text-red-500">*</span>
          </label>
          <textarea
            id="recurring-reason"
            rows={3}
            maxLength={500}
            placeholder="e.g. Weekly Product Design Review with external partners..."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className={`block w-full rounded-md border p-2 text-sm text-brand-navy shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-brand-accent ${
              formErrors.reason ? 'border-red-500' : 'border-brand-slate/30'
            }`}
          />
          <div className="flex justify-between items-center mt-1">
            {formErrors.reason ? (
              <p className="text-xs text-red-600 font-medium">{formErrors.reason}</p>
            ) : (
              <p className="text-xs text-brand-slate">Plain text, maximum 500 characters.</p>
            )}
            <span className="text-xs text-brand-slate font-mono">{reason.length}/500</span>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t border-brand-slate/10">
          <Button
            variant="outline"
            onClick={() => navigate('/rooms')}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={isPreviewLoading}
          >
            Preview Occurrences &rarr;
          </Button>
        </div>
      </form>
    </div>
  )
}

export default RecurringBookingPage
