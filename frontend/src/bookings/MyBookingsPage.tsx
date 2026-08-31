import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import { Link } from 'react-router-dom'
import { bookingService } from '@/api/bookingService'
import type { BookingResponse } from '@/types/booking'
import type { BackendError } from '@/types/api'
import {
  Badge,
  Button,
  Dialog,
  EmptyState,
  ErrorState,
  Loading,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  toast,
} from '@/components'

type BookingCategory = 'ALL' | 'UPCOMING' | 'PAST' | 'CANCELLED'

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

export const MyBookingsPage: React.FC = () => {
  const [bookings, setBookings] = useState<BookingResponse[]>([])
  const [page, setPage] = useState<number>(0)
  const [totalPages, setTotalPages] = useState<number>(0)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<BackendError | null>(null)

  // Category filter
  const [activeCategory, setActiveCategory] = useState<BookingCategory>('ALL')

  // Selected booking for detailed view modal
  const [selectedBooking, setSelectedBooking] = useState<BookingResponse | null>(null)
  const [isDetailsOpen, setIsDetailsOpen] = useState<boolean>(false)

  // Cancellation state
  const [bookingToCancel, setBookingToCancel] = useState<BookingResponse | null>(null)
  const [isCancelDialogOpen, setIsCancelDialogOpen] = useState<boolean>(false)
  const [cancellationReason, setCancellationReason] = useState<string>('')
  const [isCancelling, setIsCancelling] = useState<boolean>(false)
  const [cancellationError, setCancellationError] = useState<BackendError | null>(null)
  const cancelIdempotencyKeyRef = useRef<string>(generateUUID())

  const fetchBookings = useCallback(async (pageNumber = 0) => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await bookingService.getMyBookings({
        page: pageNumber,
        size: 10,
        sort: 'startTime,desc',
      })
      setBookings(data.content)
      setPage(data.page)
      setTotalPages(data.totalPages)
    } catch (err: unknown) {
      setError(err as BackendError)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchBookings(page)
  }, [fetchBookings, page])

  // Filter bookings based on active tab
  const displayedBookings = useMemo(() => {
    if (activeCategory === 'ALL') return bookings
    if (activeCategory === 'UPCOMING') {
      return bookings.filter((b) => b.status === 'CONFIRMED')
    }
    if (activeCategory === 'PAST') {
      return bookings.filter((b) => b.status === 'COMPLETED')
    }
    if (activeCategory === 'CANCELLED') {
      return bookings.filter((b) => b.status === 'CANCELLED')
    }
    return bookings
  }, [bookings, activeCategory])

  const formatDateTime = (isoString?: string): { date: string; time: string } => {
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

  const formatCreatedAt = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      })
    } catch {
      return isoString
    }
  }

  const getStatusBadgeVariant = (status: string) => {
    switch (status) {
      case 'CONFIRMED':
        return 'success'
      case 'CANCELLED':
        return 'error'
      case 'COMPLETED':
        return 'neutral'
      default:
        return 'neutral'
    }
  }

  const handleOpenDetails = (booking: BookingResponse) => {
    setSelectedBooking(booking)
    setIsDetailsOpen(true)
  }

  const handleInitiateCancel = (booking: BookingResponse) => {
    setBookingToCancel(booking)
    setCancellationReason('')
    setCancellationError(null)
    cancelIdempotencyKeyRef.current = generateUUID()
    setIsCancelDialogOpen(true)
  }

  const handleExecuteCancel = async () => {
    if (!bookingToCancel) return
    setIsCancelling(true)
    setCancellationError(null)

    try {
      await bookingService.cancelBooking(
        bookingToCancel.id,
        cancellationReason.trim(),
        cancelIdempotencyKeyRef.current
      )

      toast.success('Booking successfully cancelled.')
      setIsCancelDialogOpen(false)

      // Refresh bookings
      await fetchBookings(page)

      // If details dialog is open for this booking, update it
      if (selectedBooking && selectedBooking.id === bookingToCancel.id) {
        setSelectedBooking((prev) =>
          prev
            ? {
                ...prev,
                status: 'CANCELLED',
                cancelledReason: cancellationReason.trim() || 'Cancelled by user',
              }
            : null
        )
      }
    } catch (err: unknown) {
      const backendErr = err as BackendError
      setCancellationError(backendErr)

      let friendlyMsg = backendErr.message || 'Failed to cancel booking.'
      if (
        backendErr.errorCode === 'BOOKING_ALREADY_CANCELLED' ||
        backendErr.message?.toLowerCase().includes('already cancelled')
      ) {
        friendlyMsg = 'This booking has already been cancelled.'
      } else if (
        backendErr.errorCode === 'BOOKING_ALREADY_COMPLETED' ||
        backendErr.message?.toLowerCase().includes('already completed') ||
        backendErr.message?.toLowerCase().includes('completed')
      ) {
        friendlyMsg = 'This booking can no longer be cancelled because it has completed.'
      } else if (backendErr.status === 403) {
        friendlyMsg = 'You do not have permission to cancel this booking.'
      } else if (backendErr.status === 404) {
        friendlyMsg = 'Booking not found.'
      }

      toast.error(friendlyMsg)
    } finally {
      setIsCancelling(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-brand-navy">My Bookings</h1>
          <p className="text-sm text-brand-slate mt-1">
            Review your upcoming, past, and cancelled room reservations.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => fetchBookings(page)}>
          Refresh
        </Button>
      </div>

      {/* Category Tabs */}
      <div className="flex border-b border-brand-slate/20 space-x-4">
        {(['ALL', 'UPCOMING', 'PAST', 'CANCELLED'] as BookingCategory[]).map((cat) => {
          const isActive = activeCategory === cat
          const labels: Record<BookingCategory, string> = {
            ALL: 'All Bookings',
            UPCOMING: 'Upcoming',
            PAST: 'Past',
            CANCELLED: 'Cancelled',
          }
          return (
            <button
              key={cat}
              type="button"
              onClick={() => setActiveCategory(cat)}
              className={`pb-3 text-sm font-medium border-b-2 transition-colors cursor-pointer ${
                isActive
                  ? 'border-brand-navy text-brand-navy'
                  : 'border-transparent text-brand-slate hover:text-brand-navy hover:border-slate-300'
              }`}
            >
              {labels[cat]}
            </button>
          )
        })}
      </div>

      {/* Loading State */}
      {isLoading && <Loading text="Loading your reservations..." />}

      {/* Error State */}
      {!isLoading && error && (
        <ErrorState
          error={error}
          title="Failed to load bookings"
          onRetry={() => fetchBookings(page)}
        />
      )}

      {/* Empty State */}
      {!isLoading && !error && displayedBookings.length === 0 && (
        <EmptyState
          title={
            activeCategory === 'ALL'
              ? 'No bookings found'
              : `No ${activeCategory.toLowerCase()} bookings`
          }
          description={
            activeCategory === 'ALL'
              ? 'You have not made any room reservations yet.'
              : `You have no bookings matching the "${activeCategory.toLowerCase()}" category.`
          }
        />
      )}

      {/* Bookings Table */}
      {!isLoading && !error && displayedBookings.length > 0 && (
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs overflow-hidden">
          <Table columnCount={7}>
            <TableHeader>
              <TableRow>
                <TableHead>Room</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Time Window</TableHead>
                <TableHead>Timezone</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {displayedBookings.map((b) => {
                const start = formatDateTime(b.startTime)
                const end = formatDateTime(b.endTime)
                const isEligibleForCancel = b.status === 'CONFIRMED'

                return (
                  <TableRow key={b.id}>
                    <TableCell className="font-semibold text-brand-navy">
                      {b.roomName || `Room #${b.roomId}`}
                    </TableCell>
                    <TableCell className="text-brand-slate whitespace-nowrap">
                      {start.date}
                    </TableCell>
                    <TableCell className="text-brand-navy font-medium whitespace-nowrap">
                      {start.time} – {end.time}
                    </TableCell>
                    <TableCell className="text-xs text-brand-slate whitespace-nowrap">
                      Asia/Kolkata (IST)
                    </TableCell>
                    <TableCell className="text-brand-slate max-w-xs truncate">
                      {b.reason}
                    </TableCell>
                    <TableCell>
                      <Badge variant={getStatusBadgeVariant(b.status)} size="sm">
                        {b.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleOpenDetails(b)}
                        >
                          Details
                        </Button>
                        {isEligibleForCancel && (
                          <>
                            <Link to={`/bookings/${b.id}/reschedule`}>
                              <Button variant="outline" size="sm">
                                Reschedule
                              </Button>
                            </Link>
                            <Button
                              variant="danger"
                              size="sm"
                              onClick={() => handleInitiateCancel(b)}
                            >
                              Cancel
                            </Button>
                          </>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>

          <Pagination
            currentPage={page + 1}
            totalPages={totalPages}
            onPageChange={(newPage) => setPage(newPage - 1)}
          />
        </div>
      )}

      {/* Booking Details Modal (UI-7.2 & UI-8 Cancel Trigger) */}
      <Dialog
        isOpen={isDetailsOpen}
        title={`Booking Details #${selectedBooking?.id ?? ''}`}
        onClose={() => setIsDetailsOpen(false)}
      >
        {selectedBooking && (
          <div className="space-y-4 py-2 text-left">
            <div className="flex justify-between items-center pb-3 border-b border-brand-slate/10">
              <span className="text-xs font-semibold uppercase text-brand-slate">
                Current Status
              </span>
              <Badge variant={getStatusBadgeVariant(selectedBooking.status)} size="sm">
                {selectedBooking.status}
              </Badge>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <span className="text-xs font-semibold uppercase text-brand-slate block">
                  Room
                </span>
                <p className="text-sm font-bold text-brand-navy mt-0.5">
                  {selectedBooking.roomName || `Room #${selectedBooking.roomId}`}
                </p>
              </div>
              <div>
                <span className="text-xs font-semibold uppercase text-brand-slate block">
                  Location & Timezone
                </span>
                <p className="text-sm text-brand-navy mt-0.5">
                  Asia/Kolkata (IST)
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <span className="text-xs font-semibold uppercase text-brand-slate block">
                  Booking Date
                </span>
                <p className="text-sm text-brand-navy mt-0.5">
                  {formatDateTime(selectedBooking.startTime).date}
                </p>
              </div>
              <div>
                <span className="text-xs font-semibold uppercase text-brand-slate block">
                  Time Window
                </span>
                <p className="text-sm font-medium text-brand-navy mt-0.5">
                  {formatDateTime(selectedBooking.startTime).time} –{' '}
                  {formatDateTime(selectedBooking.endTime).time}
                </p>
              </div>
            </div>

            <div>
              <span className="text-xs font-semibold uppercase text-brand-slate block mb-1">
                Booking Reason
              </span>
              <p className="text-sm text-brand-navy p-3 bg-brand-light-gray rounded-md border border-brand-slate/10 whitespace-pre-wrap">
                {selectedBooking.reason}
              </p>
            </div>

            {selectedBooking.cancelledReason && (
              <div>
                <span className="text-xs font-semibold uppercase text-red-600 block mb-1">
                  Cancellation Reason
                </span>
                <p className="text-sm text-red-700 p-2 bg-red-50 rounded-md border border-red-200">
                  {selectedBooking.cancelledReason}
                </p>
              </div>
            )}

            <div className="flex items-center gap-2 p-2.5 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-700">
              <svg className="w-4 h-4 text-slate-500 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>
                <strong>Notification Delivery:</strong> Status transitions (confirmation, rescheduling, cancellation, and completion) dispatch asynchronous notifications via the system outbox.
              </span>
            </div>

            <div className="pt-3 border-t border-brand-slate/10 text-xs text-brand-slate flex justify-between">
              <span>Created: {formatCreatedAt(selectedBooking.createdAt)}</span>
              {selectedBooking.updatedAt && (
                <span>Updated: {formatCreatedAt(selectedBooking.updatedAt)}</span>
              )}
            </div>

            <div className="flex justify-between items-center pt-4 border-t border-brand-slate/10">
              {selectedBooking.status === 'CONFIRMED' ? (
                <div className="flex items-center gap-2">
                  <Link to={`/bookings/${selectedBooking.id}/reschedule`}>
                    <Button variant="outline" size="sm">
                      Reschedule
                    </Button>
                  </Link>
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => {
                      setIsDetailsOpen(false)
                      handleInitiateCancel(selectedBooking)
                    }}
                  >
                    Cancel This Booking
                  </Button>
                </div>
              ) : (
                <div />
              )}
              <Button variant="primary" size="sm" onClick={() => setIsDetailsOpen(false)}>
                Close
              </Button>
            </div>
          </div>
        )}
      </Dialog>

      {/* Cancellation Confirmation Dialog (UI-8.1) */}
      <Dialog
        isOpen={isCancelDialogOpen}
        title="Cancel Booking?"
        onClose={() => !isCancelling && setIsCancelDialogOpen(false)}
      >
        {bookingToCancel && (
          <div className="space-y-4 py-2 text-left">
            <p className="text-sm text-brand-slate">
              Are you sure you want to cancel the reservation below? This action is a durable state change and will mark the booking as <span className="font-semibold text-red-600">CANCELLED</span>.
            </p>

            <div className="bg-brand-light-gray p-4 rounded-md border border-brand-slate/20 space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-brand-slate">Room:</span>
                <span className="font-bold text-brand-navy">{bookingToCancel.roomName || `Room #${bookingToCancel.roomId}`}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brand-slate">Date:</span>
                <span className="font-medium text-brand-navy">{formatDateTime(bookingToCancel.startTime).date}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brand-slate">Time:</span>
                <span className="font-medium text-brand-navy">
                  {formatDateTime(bookingToCancel.startTime).time} – {formatDateTime(bookingToCancel.endTime).time}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-brand-slate">Timezone:</span>
                <span className="text-brand-navy">Asia/Kolkata (IST)</span>
              </div>
            </div>

            {cancellationError && (
              <div className="p-3 bg-red-50 text-red-700 text-xs rounded-md border border-red-200">
                {cancellationError.message || 'Failed to cancel booking.'}
              </div>
            )}

            <div>
              <label htmlFor="cancel-reason" className="block text-xs font-semibold uppercase text-brand-slate mb-1">
                Optional Cancellation Reason
              </label>
              <textarea
                id="cancel-reason"
                rows={2}
                maxLength={500}
                placeholder="e.g. Meeting rescheduled by client, Conflict with another event..."
                value={cancellationReason}
                onChange={(e) => setCancellationReason(e.target.value)}
                disabled={isCancelling}
                className="block w-full rounded-md border border-brand-slate/30 p-2 text-sm text-brand-navy shadow-sm focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-red-500"
              />
              <div className="flex justify-end mt-1">
                <span className="text-xs text-brand-slate font-mono">{cancellationReason.length}/500</span>
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-3 border-t border-brand-slate/10">
              <Button
                variant="outline"
                size="sm"
                disabled={isCancelling}
                onClick={() => setIsCancelDialogOpen(false)}
              >
                Keep Booking
              </Button>
              <Button
                variant="danger"
                size="sm"
                loading={isCancelling}
                onClick={handleExecuteCancel}
              >
                Cancel Booking
              </Button>
            </div>
          </div>
        )}
      </Dialog>
    </div>
  )
}

export default MyBookingsPage
