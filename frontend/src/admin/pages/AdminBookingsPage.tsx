import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import type { BookingResponse, BookingStatus } from '@/types/booking'
import type { LocationResponse } from '@/types/location'
import type { RoomResponse } from '@/types/room'
import type { ApiError } from '@/api/client'
import { bookingAdminService } from '../api/bookingAdminService'
import { locationAdminService } from '../api/locationAdminService'
import { roomAdminService } from '../api/roomAdminService'
import BookingDetailModal from '../bookings/BookingDetailModal'
import AdminRescheduleModal from '../bookings/AdminRescheduleModal'
import Table, { TableBody, TableCell, TableHeader, TableHead, TableRow } from '@/components/Table/Table'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'
import Select from '@/components/Select/Select'
import ConfirmDialog from '@/components/ConfirmDialog/ConfirmDialog'
import EmptyState from '@/components/EmptyState/EmptyState'

export const AdminBookingsPage: React.FC = () => {
  const [bookings, setBookings] = useState<BookingResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [rooms, setRooms] = useState<RoomResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)

  // Pagination state
  const [page, setPage] = useState(0)
  const [pageSize] = useState(10)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  // Filter state
  const [selectedLocationId, setSelectedLocationId] = useState<string>('ALL')
  const [selectedRoomId, setSelectedRoomId] = useState<string>('ALL')
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL')
  const [userIdFilter, setUserIdFilter] = useState<string>('')

  // Modal states
  const [selectedBookingForDetail, setSelectedBookingForDetail] = useState<BookingResponse | null>(null)
  const [selectedBookingForReschedule, setSelectedBookingForReschedule] = useState<BookingResponse | null>(null)

  // Cancel dialog state
  const [cancellingBooking, setCancellingBooking] = useState<BookingResponse | null>(null)
  const [isCancelling, setIsCancelling] = useState(false)

  // Load locations and rooms reference data once
  useEffect(() => {
    async function loadReferenceData() {
      try {
        const [locRes, roomRes] = await Promise.all([
          locationAdminService.getLocations({ page: 0, size: 100, sort: 'name,asc' }),
          roomAdminService.getRooms({ page: 0, size: 200, sort: 'name,asc' }),
        ])
        setLocations(locRes.content || [])
        setRooms(roomRes.content || [])
      } catch {
        // Non-critical reference load error
      }
    }
    loadReferenceData()
  }, [])

  // Authoritative Bookings fetcher
  const fetchBookings = useCallback(async () => {
    setLoading(true)
    setFetchError(null)
    try {
      const locationId = selectedLocationId !== 'ALL' ? Number(selectedLocationId) : undefined
      const roomId = selectedRoomId !== 'ALL' ? Number(selectedRoomId) : undefined
      const status = selectedStatus !== 'ALL' ? (selectedStatus as BookingStatus) : undefined
      const userId = userIdFilter.trim() !== '' && !isNaN(Number(userIdFilter)) ? Number(userIdFilter) : undefined

      const response = await bookingAdminService.getAdminBookings({
        locationId,
        roomId,
        userId,
        status,
        page,
        size: pageSize,
        sort: 'startTime,desc',
      })

      setBookings(response.content || [])
      setTotalPages(response.totalPages || 1)
      setTotalElements(response.totalElements || 0)
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || 'Failed to load bookings from server.'
      setFetchError(message)
    } finally {
      setLoading(false)
    }
  }, [selectedLocationId, selectedRoomId, selectedStatus, userIdFilter, page, pageSize])

  useEffect(() => {
    fetchBookings()
  }, [fetchBookings])

  // Reset pagination when filters change
  const handleLocationChange = (val: string) => {
    setSelectedLocationId(val)
    setSelectedRoomId('ALL')
    setPage(0)
  }

  const handleRoomChange = (val: string) => {
    setSelectedRoomId(val)
    setPage(0)
  }

  const handleStatusChange = (val: string) => {
    setSelectedStatus(val)
    setPage(0)
  }

  const handleUserIdChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setUserIdFilter(e.target.value)
    setPage(0)
  }

  // Cancel flow
  const handlePromptCancel = (booking: BookingResponse) => {
    setCancellingBooking(booking)
  }

  const handleConfirmCancel = async () => {
    if (!cancellingBooking) return
    setIsCancelling(true)
    try {
      await bookingAdminService.cancelBooking(cancellingBooking.id, 'Cancelled by admin')
      toast.success(`Booking #${cancellingBooking.id} cancelled successfully.`)
      setCancellingBooking(null)
      if (selectedBookingForDetail?.id === cancellingBooking.id) {
        setSelectedBookingForDetail(null)
      }
      await fetchBookings()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || `Failed to cancel booking #${cancellingBooking.id}.`
      toast.error(message)
    } finally {
      setIsCancelling(false)
    }
  }

  // Reschedule flow
  const handlePromptReschedule = (booking: BookingResponse) => {
    setSelectedBookingForReschedule(booking)
  }

  const handleRescheduleSuccess = () => {
    fetchBookings()
  }

  // Helpers for formatting
  const formatDate = (isoString: string) => {
    try {
      return new Date(isoString).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      })
    } catch {
      return isoString
    }
  }

  const formatTimeRange = (startIso: string, endIso: string) => {
    try {
      const start = new Date(startIso).toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
      })
      const end = new Date(endIso).toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
      })
      return `${start} - ${end}`
    } catch {
      return `${startIso} - ${endIso}`
    }
  }

  // Filter options
  const locationOptions = [
    { value: 'ALL', label: 'All Locations' },
    ...locations.map((loc) => ({
      value: String(loc.id),
      label: `${loc.name} (${loc.code})`,
    })),
  ]

  const filteredRoomsForDropdown = selectedLocationId === 'ALL'
    ? rooms
    : rooms.filter((r) => String(r.location?.id) === selectedLocationId)

  const roomOptions = [
    { value: 'ALL', label: 'All Rooms' },
    ...filteredRoomsForDropdown.map((r) => ({
      value: String(r.id),
      label: `${r.name} (${r.location?.code || ''})`,
    })),
  ]

  const statusOptions = [
    { value: 'ALL', label: 'All Statuses' },
    { value: 'CONFIRMED', label: 'Confirmed' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ]

  return (
    <div className="space-y-6">
      {/* Breadcrumb & Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav className="flex items-center gap-2 text-xs text-brand-slate mb-1">
            <Link to="/admin" className="hover:text-brand-navy">
              Admin
            </Link>
            <span>/</span>
            <span className="text-brand-navy font-semibold">Bookings</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Booking Management</h1>
          <p className="text-brand-slate text-sm mt-1">
            View, search, filter, inspect, reschedule, and cancel room reservations across all enterprise locations.
          </p>
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Total Bookings
            </span>
            <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-brand-navy mt-1">
            {loading ? <span className="text-slate-300">...</span> : totalElements}
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-emerald-600 uppercase tracking-wider">
              Confirmed
            </span>
            <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-emerald-700 mt-1">
            {bookings.filter((b) => b.status === 'CONFIRMED').length} <span className="text-xs font-normal text-slate-500">on page</span>
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-blue-600 uppercase tracking-wider">
              Completed
            </span>
            <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-blue-700 mt-1">
            {bookings.filter((b) => b.status === 'COMPLETED').length} <span className="text-xs font-normal text-slate-500">on page</span>
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-red-600 uppercase tracking-wider">
              Cancelled
            </span>
            <span className="p-2 rounded-lg bg-red-50 text-red-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-red-700 mt-1">
            {bookings.filter((b) => b.status === 'CANCELLED').length} <span className="text-xs font-normal text-slate-500">on page</span>
          </div>
        </div>
      </div>

      {/* Filters Bar */}
      <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs flex flex-col md:flex-row gap-4 items-stretch md:items-center justify-between flex-wrap">
        <div className="flex flex-col sm:flex-row gap-3 flex-1 flex-wrap">
          {/* Location Filter */}
          <div className="w-full sm:w-48">
            <Select
              options={locationOptions}
              value={selectedLocationId}
              onChange={(e) => handleLocationChange(e.target.value)}
            />
          </div>

          {/* Room Filter */}
          <div className="w-full sm:w-48">
            <Select
              options={roomOptions}
              value={selectedRoomId}
              onChange={(e) => handleRoomChange(e.target.value)}
            />
          </div>

          {/* Status Filter */}
          <div className="w-full sm:w-40">
            <Select
              options={statusOptions}
              value={selectedStatus}
              onChange={(e) => handleStatusChange(e.target.value)}
            />
          </div>

          {/* User ID Filter */}
          <div className="w-full sm:w-36">
            <input
              type="text"
              placeholder="User ID (e.g. 1)"
              value={userIdFilter}
              onChange={handleUserIdChange}
              className="w-full px-3 py-2 text-sm border border-brand-slate/30 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-brand-accent focus:border-brand-accent transition-colors"
            />
          </div>
        </div>

        {/* Reset Filter Button */}
        {(selectedLocationId !== 'ALL' || selectedRoomId !== 'ALL' || selectedStatus !== 'ALL' || userIdFilter !== '') && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setSelectedLocationId('ALL')
              setSelectedRoomId('ALL')
              setSelectedStatus('ALL')
              setUserIdFilter('')
              setPage(0)
            }}
          >
            Reset Filters
          </Button>
        )}
      </div>

      {/* Error Banner with Retry */}
      {fetchError && (
        <div
          role="alert"
          className="p-4 rounded-xl bg-red-50 border border-red-200 text-sm text-red-800 flex items-center justify-between gap-4"
        >
          <div className="flex items-center gap-3">
            <svg className="w-5 h-5 text-red-500 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{fetchError}</span>
          </div>
          <Button variant="outline" size="sm" onClick={fetchBookings}>
            Retry
          </Button>
        </div>
      )}

      {/* Main Data Table */}
      <Table
        loading={loading}
        empty={!loading && bookings.length === 0}
        emptyMessage="No bookings match your current filter criteria."
        columnCount={7}
      >
        <TableHeader>
          <TableRow>
            <TableHead>Booking</TableHead>
            <TableHead>User</TableHead>
            <TableHead>Room</TableHead>
            <TableHead>Date & Time</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Reason</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {bookings.map((booking) => {
            const isConfirmed = booking.status === 'CONFIRMED'
            return (
              <TableRow key={booking.id} className="hover:bg-slate-50/70 transition-colors">
                {/* Booking ID & Metadata */}
                <TableCell>
                  <div className="font-mono font-bold text-brand-navy">#{booking.id}</div>
                  {booking.seriesId != null && (
                    <div className="text-[11px] text-blue-600 font-medium">
                      Series #{booking.seriesId} (#{booking.occurrenceIndex})
                    </div>
                  )}
                  {booking.rescheduledFromId != null && (
                    <div className="text-[11px] text-slate-500">
                      From #{booking.rescheduledFromId}
                    </div>
                  )}
                </TableCell>

                {/* Affected User */}
                <TableCell>
                  <div className="font-semibold text-brand-navy text-sm">
                    User #{booking.userId}
                  </div>
                </TableCell>

                {/* Room Name */}
                <TableCell>
                  <div className="font-bold text-brand-navy">
                    {booking.roomName || `Room #${booking.roomId}`}
                  </div>
                  <div className="text-xs text-brand-slate">ID: #{booking.roomId}</div>
                </TableCell>

                {/* Date & Time */}
                <TableCell>
                  <div className="font-medium text-brand-navy text-sm">
                    {formatDate(booking.startTime)}
                  </div>
                  <div className="text-xs text-brand-slate">
                    {formatTimeRange(booking.startTime, booking.endTime)}
                  </div>
                </TableCell>

                {/* Status Badge */}
                <TableCell>
                  {booking.status === 'CONFIRMED' && (
                    <Badge variant="success" size="sm" className="font-semibold">
                      CONFIRMED
                    </Badge>
                  )}
                  {booking.status === 'COMPLETED' && (
                    <Badge variant="info" size="sm" className="font-semibold">
                      COMPLETED
                    </Badge>
                  )}
                  {booking.status === 'CANCELLED' && (
                    <Badge variant="neutral" size="sm" className="font-semibold bg-red-50 text-red-700 border-red-200">
                      CANCELLED
                    </Badge>
                  )}
                </TableCell>

                {/* Reason */}
                <TableCell>
                  <div className="text-xs text-brand-navy max-w-xs line-clamp-1">
                    {booking.reason || '—'}
                  </div>
                  {booking.cancelledReason && (
                    <div className="text-[11px] text-red-600 line-clamp-1 italic">
                      Cancel: {booking.cancelledReason}
                    </div>
                  )}
                </TableCell>

                {/* Actions */}
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1.5 flex-wrap">
                    {/* View Details */}
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setSelectedBookingForDetail(booking)}
                      className="text-xs py-1 px-2.5"
                    >
                      View
                    </Button>

                    {/* Reschedule Button */}
                    {isConfirmed && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handlePromptReschedule(booking)}
                        className="text-xs py-1 px-2.5 text-blue-600 hover:text-blue-700 hover:bg-blue-50 border-blue-200"
                      >
                        Reschedule
                      </Button>
                    )}

                    {/* Cancel Button */}
                    {isConfirmed && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handlePromptCancel(booking)}
                        className="text-xs py-1 px-2.5 text-red-600 hover:text-red-700 hover:bg-red-50 border-red-200"
                      >
                        Cancel
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between p-4 bg-brand-white rounded-xl border border-brand-slate/20 shadow-2xs">
          <span className="text-xs text-brand-slate">
            Showing Page <span className="font-semibold text-brand-navy">{page + 1}</span> of{' '}
            <span className="font-semibold text-brand-navy">{totalPages}</span> ({totalElements} total bookings)
          </span>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0 || loading}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || loading}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!loading && bookings.length === 0 && !fetchError && (
        <div className="pt-2">
          <EmptyState
            title="No bookings found"
            description="No room reservations match the selected search and filter criteria."
            action={
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setSelectedLocationId('ALL')
                  setSelectedRoomId('ALL')
                  setSelectedStatus('ALL')
                  setUserIdFilter('')
                  setPage(0)
                }}
              >
                Reset Filters
              </Button>
            }
          />
        </div>
      )}

      {/* Booking Detail Modal */}
      <BookingDetailModal
        isOpen={Boolean(selectedBookingForDetail)}
        onClose={() => setSelectedBookingForDetail(null)}
        booking={selectedBookingForDetail}
        onPromptCancel={handlePromptCancel}
        onPromptReschedule={handlePromptReschedule}
      />

      {/* Reschedule Modal */}
      <AdminRescheduleModal
        isOpen={Boolean(selectedBookingForReschedule)}
        onClose={() => setSelectedBookingForReschedule(null)}
        booking={selectedBookingForReschedule}
        rooms={rooms}
        onSuccess={handleRescheduleSuccess}
      />

      {/* Cancel Confirmation Dialog */}
      <ConfirmDialog
        isOpen={Boolean(cancellingBooking)}
        title="Cancel Booking"
        description={`Are you sure you want to cancel booking #${cancellingBooking?.id} for User #${cancellingBooking?.userId} in ${cancellingBooking?.roomName}? The booking status will be updated to CANCELLED.`}
        confirmLabel="Cancel Booking"
        cancelLabel="Keep Booking"
        variant="danger"
        loading={isCancelling}
        onConfirm={handleConfirmCancel}
        onCancel={() => setCancellingBooking(null)}
      />
    </div>
  )
}

export default AdminBookingsPage
