import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { bookingService } from '@/api/bookingService'
import { roomService } from '@/api/roomService'
import { locationService } from '@/api/locationService'
import type { BookingResponse } from '@/types/booking'
import type { RoomResponse } from '@/types/room'
import type { LocationResponse } from '@/types/location'
import type { BackendError } from '@/types/api'
import {
  Badge,
  Button,
  DatePicker,
  Dialog,
  EmptyState,
  ErrorState,
  Loading,
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

export const UserDashboardPage: React.FC = () => {
  const { user, role } = useAuth()
  const navigate = useNavigate()

  // Dashboard Data State
  const [upcomingBookings, setUpcomingBookings] = useState<BookingResponse[]>([])
  const [recentBookings, setRecentBookings] = useState<BookingResponse[]>([])
  const [availableRooms, setAvailableRooms] = useState<RoomResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [dashboardError, setDashboardError] = useState<BackendError | null>(null)

  // Search Widget State
  const [searchLocationId, setSearchLocationId] = useState<string>('')
  const [searchDate, setSearchDate] = useState<string>(() => new Date().toISOString().split('T')[0])
  const [searchStartTime, setSearchStartTime] = useState<string>('10:00')
  const [searchEndTime, setSearchEndTime] = useState<string>('11:00')
  const [searchCapacity, setSearchCapacity] = useState<string>('')

  // Cancel Booking Modal State
  const [bookingToCancel, setBookingToCancel] = useState<BookingResponse | null>(null)
  const [isCancelDialogOpen, setIsCancelDialogOpen] = useState<boolean>(false)
  const [cancellationReason, setCancellationReason] = useState<string>('')
  const [isCancelling, setIsCancelling] = useState<boolean>(false)
  const [cancelError, setCancelError] = useState<BackendError | null>(null)
  const cancelIdempotencyKeyRef = useRef<string>(generateUUID())

  // Details Modal State
  const [selectedBooking, setSelectedBooking] = useState<BookingResponse | null>(null)
  const [isDetailsOpen, setIsDetailsOpen] = useState<boolean>(false)

  // Load Dashboard Data
  const loadDashboardData = useCallback(async () => {
    setIsLoading(true)
    setDashboardError(null)
    try {
      const [bookingsRes, roomsRes, locationsRes] = await Promise.all([
        bookingService.getMyBookings({ page: 0, size: 20, sort: 'startTime,asc' }),
        roomService.getRooms({ page: 0, size: 4 }),
        locationService.getLocations({ page: 0, size: 50 }),
      ])

      const now = new Date().toISOString()
      const allBookings = bookingsRes.content || []

      // Upcoming confirmed bookings
      const upcoming = allBookings
        .filter((b) => b.status === 'CONFIRMED' && b.endTime >= now)
        .slice(0, 5)

      // Recent bookings (by creation/start time)
      const recent = [...allBookings]
        .sort((a, b) => new Date(b.createdAt || b.startTime).getTime() - new Date(a.createdAt || a.startTime).getTime())
        .slice(0, 5)

      setUpcomingBookings(upcoming)
      setRecentBookings(recent)
      setAvailableRooms(roomsRes.content || [])
      setLocations(locationsRes.content || [])
    } catch (err: unknown) {
      setDashboardError(err as BackendError)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadDashboardData()
  }, [loadDashboardData])

  // Next upcoming booking
  const nextUpcoming = useMemo(() => {
    if (upcomingBookings.length === 0) return null
    return upcomingBookings[0]
  }, [upcomingBookings])

  const formatDateTime = (isoString?: string): { date: string; time: string } => {
    if (!isoString) return { date: '—', time: '—' }
    try {
      const d = new Date(isoString)
      return {
        date: d.toLocaleDateString('en-US', {
          weekday: 'short',
          month: 'short',
          day: 'numeric',
        }),
        time: d.toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
          hour12: true,
        }),
      }
    } catch {
      return { date: isoString, time: '' }
    }
  }

  const formatRelativeTime = (isoString?: string): string => {
    if (!isoString) return ''
    try {
      const diffMs = Date.now() - new Date(isoString).getTime()
      const diffMins = Math.floor(diffMs / 60000)
      if (diffMins < 1) return 'Just now'
      if (diffMins < 60) return `${diffMins} min ago`
      const diffHours = Math.floor(diffMins / 60)
      if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`
      const diffDays = Math.floor(diffHours / 24)
      return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`
    } catch {
      return ''
    }
  }

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const params = new URLSearchParams()
    if (searchLocationId) params.append('locationId', searchLocationId)
    if (searchCapacity) params.append('capacity', searchCapacity)
    if (searchDate) params.append('date', searchDate)
    if (searchStartTime) params.append('startTime', searchStartTime)
    if (searchEndTime) params.append('endTime', searchEndTime)
    navigate(`/rooms?${params.toString()}`)
  }

  const handleOpenCancelDialog = (booking: BookingResponse) => {
    setBookingToCancel(booking)
    setCancellationReason('')
    setCancelError(null)
    cancelIdempotencyKeyRef.current = generateUUID()
    setIsCancelDialogOpen(true)
  }

  const handleExecuteCancel = async () => {
    if (!bookingToCancel) return
    setIsCancelling(true)
    setCancelError(null)

    try {
      await bookingService.cancelBooking(
        bookingToCancel.id,
        cancellationReason.trim(),
        cancelIdempotencyKeyRef.current
      )
      toast.success('Booking successfully cancelled.')
      setIsCancelDialogOpen(false)
      if (isDetailsOpen && selectedBooking?.id === bookingToCancel.id) {
        setIsDetailsOpen(false)
      }
      await loadDashboardData()
    } catch (err: unknown) {
      const backendErr = err as BackendError
      setCancelError(backendErr)
      toast.error(backendErr.message || 'Failed to cancel booking.')
    } finally {
      setIsCancelling(false)
    }
  }

  const handleOpenDetails = (booking: BookingResponse) => {
    setSelectedBooking(booking)
    setIsDetailsOpen(true)
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

  const userName = user?.email ? user.email.split('@')[0] : 'User'
  const todayFormatted = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })

  if (isLoading) {
    return <Loading fullPage text="Loading dashboard..." />
  }

  if (dashboardError) {
    return (
      <div className="space-y-4 max-w-4xl mx-auto py-8">
        <ErrorState
          error={dashboardError}
          title="Cannot load dashboard"
          onRetry={loadDashboardData}
        />
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* 1. Header & Welcome Area */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 border-b border-brand-slate/10">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-3xl" role="img" aria-label="wave">
              👋
            </span>
            <h1 className="text-3xl font-bold tracking-tight text-brand-navy">
              Welcome, {userName}!
            </h1>
          </div>
          <p className="text-sm text-brand-slate mt-1">
            Find and book meeting rooms across all active locations.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs font-medium text-brand-slate bg-brand-white px-3 py-1.5 rounded-md border border-brand-slate/20 shadow-xs">
            📅 {todayFormatted}
          </span>
          <Badge variant={role === 'ADMIN' ? 'warning' : 'neutral'} size="sm">
            {role}
          </Badge>
        </div>
      </div>

      {/* 2. Key Metrics Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Metric 1: Upcoming Bookings */}
        <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center text-2xl font-bold">
            🗓️
          </div>
          <div>
            <span className="text-xs font-semibold uppercase text-brand-slate">
              Upcoming Bookings
            </span>
            <div className="text-2xl font-bold text-brand-navy">
              {upcomingBookings.length}
            </div>
            <span className="text-xs text-brand-slate truncate block max-w-[180px]">
              {nextUpcoming
                ? `Next: ${nextUpcoming.roomName}`
                : 'No upcoming meetings'}
            </span>
          </div>
        </div>

        {/* Metric 2: Available Rooms */}
        <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center text-2xl font-bold">
            ✓
          </div>
          <div>
            <span className="text-xs font-semibold uppercase text-brand-slate">
              Available Rooms
            </span>
            <div className="text-2xl font-bold text-brand-navy">
              {availableRooms.length}
            </div>
            <span className="text-xs text-brand-slate">Across all locations</span>
          </div>
        </div>

        {/* Metric 3: Active Locations */}
        <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center text-2xl font-bold">
            🏢
          </div>
          <div>
            <span className="text-xs font-semibold uppercase text-brand-slate">
              Active Locations
            </span>
            <div className="text-2xl font-bold text-brand-navy">
              {locations.length}
            </div>
            <span className="text-xs text-brand-slate">Campus network</span>
          </div>
        </div>

        {/* Metric 4: System Status */}
        <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center text-2xl font-bold">
            ⏱️
          </div>
          <div>
            <span className="text-xs font-semibold uppercase text-brand-slate">
              Authoritative Zone
            </span>
            <div className="text-lg font-bold text-brand-navy">
              Asia/Kolkata
            </div>
            <span className="text-xs text-emerald-600 font-medium">● 100% Operational</span>
          </div>
        </div>
      </div>

      {/* 3. Main Dashboard Grid (2 Columns) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left Column (2 Cols wide on lg): Search + Available Rooms */}
        <div className="lg:col-span-2 space-y-8">
          {/* Section: Find a Meeting Room (Search Widget) */}
          <div className="bg-brand-white p-6 rounded-xl border border-brand-slate/20 shadow-xs space-y-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-xl">🔍</span>
                <h2 className="text-lg font-bold text-brand-navy">Find a Meeting Room</h2>
              </div>
              <Link
                to="/rooms"
                className="text-xs font-semibold text-brand-navy hover:text-brand-accent transition-colors"
              >
                Advanced Search &rarr;
              </Link>
            </div>

            <form onSubmit={handleSearchSubmit} className="space-y-4" noValidate>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
                <div>
                  <Select
                    label="Location"
                    value={searchLocationId}
                    onChange={(e) => setSearchLocationId(e.target.value)}
                    options={[
                      { value: '', label: 'All Locations' },
                      ...locations.map((loc) => ({
                        value: String(loc.id),
                        label: `${loc.name} (${loc.code})`,
                      })),
                    ]}
                  />
                </div>
                <div>
                  <DatePicker
                    label="Date"
                    value={searchDate}
                    onChange={(e) => setSearchDate(e.target.value)}
                  />
                </div>
                <div>
                  <TimePicker
                    label="Start Time"
                    value={searchStartTime}
                    step={900}
                    onChange={(e) => setSearchStartTime(e.target.value)}
                  />
                </div>
                <div>
                  <TimePicker
                    label="End Time"
                    value={searchEndTime}
                    step={900}
                    onChange={(e) => setSearchEndTime(e.target.value)}
                  />
                </div>
              </div>

              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pt-2">
                <div className="w-full sm:w-48">
                  <Select
                    label="Min Capacity"
                    value={searchCapacity}
                    onChange={(e) => setSearchCapacity(e.target.value)}
                    options={[
                      { value: '', label: 'Any capacity' },
                      { value: '4', label: '4+ People' },
                      { value: '8', label: '8+ People' },
                      { value: '12', label: '12+ People' },
                      { value: '20', label: '20+ People' },
                    ]}
                  />
                </div>
                <div className="flex justify-end">
                  <Button type="submit" variant="primary" className="w-full sm:w-auto">
                    Search Rooms &rarr;
                  </Button>
                </div>
              </div>
            </form>
          </div>

          {/* Section: Available Rooms (Quick Book Cards) */}
          <div className="bg-brand-white rounded-xl border border-brand-slate/20 shadow-xs overflow-hidden">
            <div className="p-5 border-b border-brand-slate/10 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-xl">🚪</span>
                <h2 className="text-lg font-bold text-brand-navy">Available Rooms</h2>
              </div>
              <Link
                to="/rooms"
                className="text-xs font-semibold text-brand-navy hover:text-brand-accent transition-colors"
              >
                View All Rooms &rarr;
              </Link>
            </div>

            <div className="divide-y divide-brand-slate/10">
              {availableRooms.length === 0 ? (
                <div className="p-6">
                  <EmptyState
                    title="No rooms found"
                    description="No rooms are currently available in the directory."
                  />
                </div>
              ) : (
                availableRooms.map((room) => (
                  <div
                    key={room.id}
                    className="p-5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 hover:bg-slate-50/60 transition-colors"
                  >
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2">
                        <Link
                          to={`/rooms/${room.id}`}
                          className="font-bold text-base text-brand-navy hover:text-brand-accent transition-colors"
                        >
                          {room.name}
                        </Link>
                        <Badge variant="success" size="sm">
                          {room.status}
                        </Badge>
                      </div>
                      <p className="text-xs text-brand-slate">
                        📍 {room.location?.name} ({room.location?.code}) &bull; 👥 Capacity: {room.capacity} people
                      </p>
                      {room.amenities && room.amenities.length > 0 && (
                        <div className="flex flex-wrap gap-1 pt-1">
                          {room.amenities.map((amenity) => (
                            <span
                              key={amenity.id}
                              className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-slate-100 text-slate-700 border border-slate-200"
                            >
                              {amenity.name}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <Link to={`/rooms/${room.id}`}>
                        <Button variant="outline" size="sm">
                          Details
                        </Button>
                      </Link>
                      <Link to={`/rooms/${room.id}/book`}>
                        <Button variant="primary" size="sm">
                          Book Now
                        </Button>
                      </Link>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Right Column (1 Col wide on lg): Quick Actions + Upcoming Bookings + Recent Activity */}
        <div className="space-y-8">
          {/* Quick Actions Card */}
          <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-xs space-y-3">
            <h2 className="text-sm font-bold uppercase tracking-wider text-brand-slate flex items-center gap-1.5">
              ⚡ Quick Actions
            </h2>
            <div className="flex flex-col gap-2">
              <Link to="/rooms">
                <Button variant="primary" className="w-full justify-start text-left">
                  📅 Book a Room
                </Button>
              </Link>
              <Link to="/my-bookings">
                <Button variant="outline" className="w-full justify-start text-left">
                  📑 My Bookings
                </Button>
              </Link>
              <Link to="/bookings/recurring">
                <Button variant="outline" className="w-full justify-start text-left">
                  🔁 Recurring Booking
                </Button>
              </Link>
            </div>
          </div>

          {/* Upcoming Bookings Section */}
          <div className="bg-brand-white rounded-xl border border-brand-slate/20 shadow-xs overflow-hidden">
            <div className="p-4 border-b border-brand-slate/10 flex items-center justify-between bg-slate-50/50">
              <h2 className="text-sm font-bold text-brand-navy flex items-center gap-1.5">
                🗓️ Upcoming Bookings
              </h2>
              <Link
                to="/my-bookings"
                className="text-xs font-semibold text-brand-navy hover:text-brand-accent transition-colors"
              >
                View All &rarr;
              </Link>
            </div>

            <div className="divide-y divide-brand-slate/10">
              {upcomingBookings.length === 0 ? (
                <div className="p-6 text-center">
                  <p className="text-sm text-brand-slate mb-3">No upcoming bookings scheduled.</p>
                  <Link to="/rooms">
                    <Button variant="primary" size="sm">
                      Find a Room
                    </Button>
                  </Link>
                </div>
              ) : (
                upcomingBookings.map((b) => {
                  const start = formatDateTime(b.startTime)
                  const end = formatDateTime(b.endTime)
                  return (
                    <div key={b.id} className="p-4 space-y-2 hover:bg-slate-50/50 transition-colors">
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="text-sm font-bold text-brand-navy">{b.roomName || `Room #${b.roomId}`}</p>
                          <p className="text-xs text-brand-slate truncate max-w-[200px]">{b.reason}</p>
                        </div>
                        <Badge variant="success" size="sm">
                          {b.status}
                        </Badge>
                      </div>

                      <div className="flex items-center justify-between text-xs text-brand-slate pt-1">
                        <span>🕒 {start.date}, {start.time} – {end.time}</span>
                      </div>

                      <div className="flex justify-end gap-1.5 pt-1">
                        <Button variant="outline" size="sm" onClick={() => handleOpenDetails(b)}>
                          View
                        </Button>
                        <Link to={`/bookings/${b.id}/reschedule`}>
                          <Button variant="outline" size="sm">
                            Reschedule
                          </Button>
                        </Link>
                        <Button variant="danger" size="sm" onClick={() => handleOpenCancelDialog(b)}>
                          Cancel
                        </Button>
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </div>

          {/* Recent Activity / Booking Status Section */}
          <div className="bg-brand-white rounded-xl border border-brand-slate/20 shadow-xs overflow-hidden">
            <div className="p-4 border-b border-brand-slate/10 flex items-center justify-between bg-slate-50/50">
              <h2 className="text-sm font-bold text-brand-navy flex items-center gap-1.5">
                ⏱️ Recent Activity
              </h2>
              <Link
                to="/my-bookings"
                className="text-xs font-semibold text-brand-navy hover:text-brand-accent transition-colors"
              >
                View All &rarr;
              </Link>
            </div>

            <div className="divide-y divide-brand-slate/10">
              {recentBookings.length === 0 ? (
                <div className="p-4 text-xs text-brand-slate text-center">
                  No recent booking activity recorded.
                </div>
              ) : (
                recentBookings.map((b) => {
                  const isConfirmed = b.status === 'CONFIRMED'
                  const isCancelled = b.status === 'CANCELLED'
                  const icon = isConfirmed ? '✅' : isCancelled ? '❌' : 'ℹ️'
                  const statusText = isConfirmed
                    ? `Booking confirmed for ${b.roomName || 'Room'}`
                    : isCancelled
                    ? `Booking cancelled for ${b.roomName || 'Room'}`
                    : `Booking completed: ${b.roomName || 'Room'}`

                  return (
                    <div key={b.id} className="p-3.5 flex items-start gap-2.5 text-xs">
                      <span className="text-sm shrink-0">{icon}</span>
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-brand-navy truncate">{statusText}</p>
                        <p className="text-brand-slate text-[11px] truncate">
                          {b.reason} &bull; {formatDateTime(b.startTime).date}
                        </p>
                      </div>
                      <span className="text-[10px] text-brand-slate shrink-0 font-mono">
                        {formatRelativeTime(b.createdAt || b.startTime)}
                      </span>
                    </div>
                  )
                })
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Booking Details Modal */}
      <Dialog
        isOpen={isDetailsOpen}
        title="Booking Details"
        onClose={() => setIsDetailsOpen(false)}
      >
        {selectedBooking && (
          <div className="space-y-4 py-2 text-left">
            <div className="flex justify-between items-center pb-2 border-b border-brand-slate/10">
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
                  Timezone
                </span>
                <p className="text-sm text-brand-navy mt-0.5">Asia/Kolkata (IST)</p>
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
                Reason
              </span>
              <p className="text-sm text-brand-navy p-3 bg-brand-light-gray rounded-md border border-brand-slate/10 whitespace-pre-wrap">
                {selectedBooking.reason}
              </p>
            </div>

            {selectedBooking.seriesId && (
              <div className="p-3 bg-blue-50 rounded-md border border-blue-200 flex justify-between items-center text-xs">
                <span className="text-blue-900 font-medium">Part of Recurring Series #{selectedBooking.seriesId}</span>
                <Link to={`/bookings/recurring/${selectedBooking.seriesId}`}>
                  <Button variant="outline" size="sm">
                    View Series &rarr;
                  </Button>
                </Link>
              </div>
            )}

            <div className="flex justify-end gap-2 pt-4 border-t border-brand-slate/10">
              <Button variant="primary" size="sm" onClick={() => setIsDetailsOpen(false)}>
                Close
              </Button>
            </div>
          </div>
        )}
      </Dialog>

      {/* Cancellation Dialog */}
      <Dialog
        isOpen={isCancelDialogOpen}
        title="Cancel Booking?"
        onClose={() => !isCancelling && setIsCancelDialogOpen(false)}
      >
        {bookingToCancel && (
          <div className="space-y-4 py-2 text-left">
            <p className="text-sm text-brand-slate">
              Are you sure you want to cancel the reservation for{' '}
              <span className="font-semibold text-brand-navy">
                {bookingToCancel.roomName || `Room #${bookingToCancel.roomId}`}
              </span>
              ? This action will mark the booking as <span className="font-semibold text-red-600">CANCELLED</span>.
            </p>

            {cancelError && (
              <div className="p-3 bg-red-50 text-red-700 text-xs rounded-md border border-red-200">
                {cancelError.message || 'Failed to cancel booking.'}
              </div>
            )}

            <div>
              <label
                htmlFor="cancel-reason"
                className="block text-xs font-semibold uppercase text-brand-slate mb-1"
              >
                Cancellation Reason (Optional)
              </label>
              <textarea
                id="cancel-reason"
                rows={3}
                value={cancellationReason}
                maxLength={500}
                placeholder="e.g. Meeting rescheduled by customer..."
                onChange={(e) => setCancellationReason(e.target.value)}
                className="block w-full rounded-md border border-brand-slate/30 px-3 py-2 text-sm text-brand-navy shadow-xs focus:outline-none focus:ring-2 focus:ring-brand-accent focus:border-brand-accent"
              />
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t border-brand-slate/10">
              <Button
                variant="outline"
                disabled={isCancelling}
                onClick={() => setIsCancelDialogOpen(false)}
              >
                Keep Booking
              </Button>
              <Button
                variant="danger"
                loading={isCancelling}
                onClick={handleExecuteCancel}
              >
                Confirm Cancellation
              </Button>
            </div>
          </div>
        )}
      </Dialog>
    </div>
  )
}

export default UserDashboardPage
