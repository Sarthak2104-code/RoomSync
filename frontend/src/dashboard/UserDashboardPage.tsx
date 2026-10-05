import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import { Link } from 'react-router-dom'
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
  Dialog,
  RoomImage,
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
  const { user } = useAuth()

  // Dashboard Data State
  const [upcomingBookings, setUpcomingBookings] = useState<BookingResponse[]>([])
  const [todayBookings, setTodayBookings] = useState<BookingResponse[]>([])
  const [allRooms, setAllRooms] = useState<RoomResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [dashboardError, setDashboardError] = useState<BackendError | null>(null)

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
        bookingService.getMyBookings({ page: 0, size: 50, sort: 'startTime,asc' }),
        roomService.getRooms({ page: 0, size: 50 }),
        locationService.getLocations({ page: 0, size: 50 }),
      ])

      const now = new Date()
      const nowIso = now.toISOString()
      const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString()
      const endOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999).toISOString()

      const allBookings = bookingsRes.content || []

      // Upcoming confirmed bookings
      const upcoming = allBookings
        .filter((b) => b.status === 'CONFIRMED' && b.endTime >= nowIso)
        .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())

      // Today's bookings
      const today = allBookings.filter(
        (b) => b.status === 'CONFIRMED' && b.startTime >= startOfToday && b.startTime <= endOfToday
      )

      setUpcomingBookings(upcoming)
      setTodayBookings(today)
      setAllRooms(roomsRes.content || [])
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

  // Computed Greeting
  const greeting = useMemo(() => {
    const hour = new Date().getHours()
    if (hour < 12) return 'Good morning'
    if (hour < 17) return 'Good afternoon'
    return 'Good evening'
  }, [])

  const userDisplayName = useMemo(() => {
    if (user?.name) {
      return user.name.split(' ')[0]
    }
    if (user?.email) {
      const prefix = user.email.split('@')[0]
      return prefix.charAt(0).toUpperCase() + prefix.slice(1)
    }
    return 'User'
  }, [user])

  // Next upcoming booking details
  const nextUpcoming = useMemo(() => {
    if (upcomingBookings.length === 0) return null
    return upcomingBookings[0]
  }, [upcomingBookings])

  const nextTodayBooking = useMemo(() => {
    if (todayBookings.length === 0) return null
    return todayBookings[0]
  }, [todayBookings])

  // Active Recurring Series computation from bookings
  const activeSeriesMap = useMemo(() => {
    const map = new Map<number, BookingResponse[]>()
    upcomingBookings.forEach((b) => {
      if (b.seriesId != null) {
        if (!map.has(b.seriesId)) {
          map.set(b.seriesId, [])
        }
        map.get(b.seriesId)!.push(b)
      }
    })
    return map
  }, [upcomingBookings])

  const activeSeriesList = useMemo(() => {
    return Array.from(activeSeriesMap.entries()).map(([seriesId, bookings]) => {
      const nextBooking = bookings[0]
      return {
        seriesId,
        nextBooking,
        count: bookings.length,
      }
    })
  }, [activeSeriesMap])

  // Room status counts
  const availableRoomsCount = useMemo(() => {
    return allRooms.filter((r) => r.status === 'AVAILABLE').length
  }, [allRooms])

  const lockedRoomsCount = useMemo(() => {
    return allRooms.filter((r) => r.status === 'LOCKED').length
  }, [allRooms])

  const occupiedRoomsCount = useMemo(() => {
    // Current live occupancy estimate
    return Math.max(0, allRooms.length - availableRoomsCount - lockedRoomsCount)
  }, [allRooms, availableRoomsCount, lockedRoomsCount])

  // Date/Time formatters
  const formatDateTime = (isoString?: string) => {
    if (!isoString) return { date: '—', time: '—', full: '—' }
    try {
      const d = new Date(isoString)
      const datePrefix = d.toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      })

      const time = d.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      })

      return {
        date: datePrefix,
        time,
        full: `${datePrefix} • ${time}`,
      }
    } catch {
      return { date: isoString, time: '', full: isoString }
    }
  }

  // Format Next Subtitle for Summary Card
  const nextUpcomingSubtitle = useMemo(() => {
    if (!nextUpcoming) return 'No upcoming meetings'
    const start = formatDateTime(nextUpcoming.startTime)
    const isToday = new Date().toDateString() === new Date(nextUpcoming.startTime).toDateString()
    return isToday ? `Next: Today, ${start.time}` : `Next: ${start.date}, ${start.time}`
  }, [nextUpcoming])

  const nextTodaySubtitle = useMemo(() => {
    if (!nextTodayBooking) return 'No meetings today'
    return `Next: ${nextTodayBooking.roomName || 'Room'}`
  }, [nextTodayBooking])

  // Action Handlers
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
        cancellationReason.trim() || 'Cancelled via User Dashboard',
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

  return (
    <div className="space-y-7 animate-in fade-in duration-150">
      {/* 1. Header Greeting & Primary CTA */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
              {greeting}, {userDisplayName}!
            </h1>
            <span className="text-2xl sm:text-3xl" role="img" aria-label="wave">
              👋
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Find a room and manage your meetings with ease.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link to="/rooms">
            <Button
              variant="primary"
              className="bg-[#0B1528] hover:bg-slate-900 text-white font-semibold shadow-xs flex items-center gap-2 px-4 py-2.5 rounded-lg"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <span>Find a Room</span>
            </Button>
          </Link>
        </div>
      </div>

      {/* 2. Key Metrics Summary Cards (4 Cards Grid) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Card 1: Upcoming Bookings */}
        {isLoading ? (
          <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs animate-pulse h-28" />
        ) : (
          <Link
            to="/my-bookings"
            className="group bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-blue-300 transition-all flex items-center justify-between"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0 border border-blue-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-500 block">Upcoming Bookings</span>
                <div className="text-2xl font-bold text-slate-900 tracking-tight">
                  {String(upcomingBookings.length).padStart(2, '0')}
                </div>
                <span className="text-[11px] font-medium text-blue-600 truncate block max-w-[150px] mt-0.5">
                  {nextUpcomingSubtitle}
                </span>
              </div>
            </div>
            <svg className="w-5 h-5 text-slate-400 group-hover:text-blue-600 group-hover:translate-x-0.5 transition-all shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        )}

        {/* Card 2: Today's Bookings */}
        {isLoading ? (
          <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs animate-pulse h-28" />
        ) : (
          <Link
            to="/my-bookings"
            className="group bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-emerald-300 transition-all flex items-center justify-between"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-12 h-12 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0 border border-emerald-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-500 block">Today&apos;s Bookings</span>
                <div className="text-2xl font-bold text-slate-900 tracking-tight">
                  {String(todayBookings.length).padStart(2, '0')}
                </div>
                <span className="text-[11px] font-medium text-emerald-600 truncate block max-w-[150px] mt-0.5">
                  {nextTodaySubtitle}
                </span>
              </div>
            </div>
            <svg className="w-5 h-5 text-slate-400 group-hover:text-emerald-600 group-hover:translate-x-0.5 transition-all shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        )}

        {/* Card 3: Active Series */}
        {isLoading ? (
          <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs animate-pulse h-28" />
        ) : (
          <Link
            to="/bookings/recurring"
            className="group bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-purple-300 transition-all flex items-center justify-between"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-12 h-12 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0 border border-purple-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-500 block">Active Series</span>
                <div className="text-2xl font-bold text-slate-900 tracking-tight">
                  {String(activeSeriesList.length).padStart(2, '0')}
                </div>
                <span className="text-[11px] font-medium text-purple-600 truncate block max-w-[150px] mt-0.5">
                  {activeSeriesList.length > 0 ? `${activeSeriesList.length} active recurring` : 'No active series'}
                </span>
              </div>
            </div>
            <svg className="w-5 h-5 text-slate-400 group-hover:text-purple-600 group-hover:translate-x-0.5 transition-all shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        )}

        {/* Card 4: Available Rooms */}
        {isLoading ? (
          <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs animate-pulse h-28" />
        ) : (
          <Link
            to="/rooms"
            className="group bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-amber-300 transition-all flex items-center justify-between"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-12 h-12 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0 border border-amber-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                </svg>
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-500 block">Available Rooms</span>
                <div className="text-2xl font-bold text-slate-900 tracking-tight">
                  {String(availableRoomsCount || allRooms.length).padStart(2, '0')}
                </div>
                <span className="text-[11px] font-medium text-amber-600 truncate block max-w-[150px] mt-0.5">
                  Across {locations.length} location{locations.length !== 1 ? 's' : ''}
                </span>
              </div>
            </div>
            <svg className="w-5 h-5 text-slate-400 group-hover:text-amber-600 group-hover:translate-x-0.5 transition-all shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        )}
      </div>

      {/* 3. Quick Actions Grid (4 Actions) */}
      <div className="space-y-3">
        <h2 className="text-base font-bold text-slate-900">Quick Actions</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
          {/* Action 1: Find a Room */}
          <Link
            to="/rooms"
            className="group p-4 bg-white rounded-xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-blue-300 transition-all flex items-center justify-between"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center shrink-0 border border-blue-100">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800 group-hover:text-blue-600 transition-colors">
                  Find a Room
                </h3>
                <p className="text-[11px] text-slate-500 leading-tight mt-0.5">
                  Search available rooms across locations
                </p>
              </div>
            </div>
            <svg className="w-4 h-4 text-slate-400 group-hover:text-blue-600 group-hover:translate-x-0.5 transition-all shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>

          {/* Action 2: Book a Room */}
          <Link
            to="/rooms"
            className="group p-4 bg-white rounded-xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-emerald-300 transition-all flex items-center justify-between"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0 border border-emerald-100">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800 group-hover:text-emerald-600 transition-colors">
                  Book a Room
                </h3>
                <p className="text-[11px] text-slate-500 leading-tight mt-0.5">
                  Create a new one-time booking
                </p>
              </div>
            </div>
            <svg className="w-4 h-4 text-slate-400 group-hover:text-emerald-600 group-hover:translate-x-0.5 transition-all shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>

          {/* Action 3: Recurring Booking */}
          <Link
            to="/bookings/recurring"
            className="group p-4 bg-white rounded-xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-purple-300 transition-all flex items-center justify-between"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center shrink-0 border border-purple-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800 group-hover:text-purple-600 transition-colors">
                  Recurring Booking
                </h3>
                <p className="text-[11px] text-slate-500 leading-tight mt-0.5">
                  Create and manage recurring bookings
                </p>
              </div>
            </div>
            <svg className="w-4 h-4 text-slate-400 group-hover:text-purple-600 group-hover:translate-x-0.5 transition-all shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>

          {/* Action 4: My Bookings */}
          <Link
            to="/my-bookings"
            className="group p-4 bg-white rounded-xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-amber-300 transition-all flex items-center justify-between"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center shrink-0 border border-amber-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800 group-hover:text-amber-600 transition-colors">
                  My Bookings
                </h3>
                <p className="text-[11px] text-slate-500 leading-tight mt-0.5">
                  View and manage all your bookings
                </p>
              </div>
            </div>
            <svg className="w-4 h-4 text-slate-400 group-hover:text-amber-600 group-hover:translate-x-0.5 transition-all shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        </div>
      </div>

      {/* 4. Main Two Column Work Area */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-7 items-start">
        {/* LEFT COLUMN: Upcoming Bookings (7 / 12 width) */}
        <div className="lg:col-span-7 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-slate-900">Upcoming Bookings</h2>
            <Link
              to="/my-bookings"
              className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
            >
              View All
            </Link>
          </div>

          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="bg-white rounded-2xl border border-slate-200/90 p-4 animate-pulse h-36" />
              ))}
            </div>
          ) : dashboardError ? (
            <div className="bg-white p-6 rounded-2xl border border-slate-200 text-center space-y-3">
              <p className="text-sm text-slate-600">Unable to load your upcoming bookings.</p>
              <Button variant="primary" size="sm" onClick={loadDashboardData}>
                Retry
              </Button>
            </div>
          ) : upcomingBookings.length === 0 ? (
            /* Intentional Professional Empty State */
            <div className="bg-white rounded-2xl border border-slate-200/90 p-8 sm:p-12 text-center shadow-2xs space-y-4">
              <div className="w-14 h-14 rounded-2xl bg-blue-50 text-blue-600 border border-blue-100 flex items-center justify-center mx-auto">
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-900">No upcoming bookings</h3>
                <p className="text-xs text-slate-500 max-w-sm mx-auto mt-1">
                  You don&apos;t have any upcoming room reservations. Use Find a Room to search available spaces across campuses.
                </p>
              </div>
              <Link to="/rooms">
                <Button variant="primary" size="sm" className="bg-[#0B1528] text-white">
                  Find a Room
                </Button>
              </Link>
            </div>
          ) : (
            /* Upcoming Booking Cards List */
            <div className="space-y-3.5">
              {upcomingBookings.slice(0, 4).map((booking) => {
                const timeData = formatDateTime(booking.startTime)
                const endTimeData = formatDateTime(booking.endTime)

                return (
                  <div
                    key={booking.id}
                    className="bg-white rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md transition-all p-4 sm:p-4.5 flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between"
                  >
                    {/* Room Image Placeholder / Component */}
                    <div className="w-full sm:w-36 h-28 shrink-0 rounded-xl overflow-hidden">
                      <RoomImage
                        roomName={booking.roomName || 'Meeting Room'}
                        className="w-full h-full"
                      />
                    </div>

                    {/* Booking Information */}
                    <div className="flex-1 min-w-0 space-y-1.5">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-bold text-sm sm:text-base text-slate-900 truncate">
                          {booking.reason || 'Meeting Reservation'}
                        </h3>
                        <Badge variant="success" size="sm">
                          {booking.status}
                        </Badge>
                      </div>

                      <div className="flex items-center gap-3 text-xs text-slate-600 font-medium flex-wrap">
                        <span className="flex items-center gap-1">
                          <svg className="w-3.5 h-3.5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                          </svg>
                          <span className="text-slate-800 font-semibold">{booking.roomName || `Room #${booking.roomId}`}</span>
                        </span>
                      </div>

                      <div className="flex items-center gap-3 text-xs text-slate-500 flex-wrap">
                        <span className="flex items-center gap-1">
                          <svg className="w-3.5 h-3.5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                          <span>{timeData.date}</span>
                        </span>
                        <span className="flex items-center gap-1">
                          <svg className="w-3.5 h-3.5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                          </svg>
                          <span>{timeData.time} – {endTimeData.time}</span>
                        </span>
                      </div>

                      {/* Mandatory Booking Reason */}
                      <p className="text-xs text-slate-500 truncate pt-0.5">
                        <strong className="text-slate-700 font-semibold">Reason:</strong> {booking.reason}
                      </p>
                    </div>

                    {/* Action Links */}
                    <div className="flex sm:flex-col items-center sm:items-end justify-between w-full sm:w-auto gap-2 pt-2 sm:pt-0 border-t sm:border-t-0 border-slate-100 shrink-0">
                      <button
                        type="button"
                        onClick={() => handleOpenDetails(booking)}
                        className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                        <span>View Details</span>
                      </button>

                      <Link
                        to={`/bookings/${booking.id}/reschedule`}
                        className="inline-flex items-center gap-1 text-xs font-semibold text-slate-600 hover:text-slate-800 transition-colors"
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        <span>Reschedule</span>
                      </Link>

                      <button
                        type="button"
                        onClick={() => handleOpenCancelDialog(booking)}
                        className="inline-flex items-center gap-1 text-xs font-semibold text-rose-600 hover:text-rose-700 transition-colors"
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                        <span>Cancel</span>
                      </button>
                    </div>
                  </div>
                )
              })}

              {upcomingBookings.length > 4 && (
                <div className="pt-2 text-center">
                  <Link
                    to="/my-bookings"
                    className="inline-flex items-center gap-1 text-xs font-bold text-blue-600 hover:text-blue-700 transition-colors"
                  >
                    <span>View All Bookings</span>
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                    </svg>
                  </Link>
                </div>
              )}
            </div>
          )}
        </div>

        {/* RIGHT COLUMN: Room Availability + Active Series (5 / 12 width) */}
        <div className="lg:col-span-5 space-y-6">
          {/* Section 1: Room Availability (Now) */}
          <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-2xs space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-slate-900">Room Availability (Now)</h2>
              <Link
                to="/rooms"
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
              >
                View Rooms
              </Link>
            </div>

            <div className="grid grid-cols-3 gap-2.5">
              {/* AVAILABLE tile */}
              <div className="p-3.5 rounded-xl bg-emerald-50/70 border border-emerald-200/70 text-center">
                <span className="text-[10px] font-bold tracking-wider text-emerald-800 uppercase block">
                  Available
                </span>
                <span className="text-2xl font-bold text-emerald-700 mt-1 block">
                  {availableRoomsCount}
                </span>
              </div>

              {/* OCCUPIED tile */}
              <div className="p-3.5 rounded-xl bg-rose-50/70 border border-rose-200/70 text-center">
                <span className="text-[10px] font-bold tracking-wider text-rose-800 uppercase block">
                  Occupied
                </span>
                <span className="text-2xl font-bold text-rose-700 mt-1 block">
                  {occupiedRoomsCount}
                </span>
              </div>

              {/* LOCKED tile */}
              <div className="p-3.5 rounded-xl bg-amber-50/70 border border-amber-200/70 text-center">
                <span className="text-[10px] font-bold tracking-wider text-amber-800 uppercase block">
                  Locked
                </span>
                <span className="text-2xl font-bold text-amber-700 mt-1 block">
                  {lockedRoomsCount}
                </span>
              </div>
            </div>
          </div>

          {/* Section 2: Your Active Series */}
          <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-2xs space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-slate-900">Your Active Series</h2>
              <Link
                to="/bookings/recurring"
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
              >
                View All
              </Link>
            </div>

            {isLoading ? (
              <div className="space-y-2">
                {[1, 2].map((i) => (
                  <div key={i} className="h-16 bg-slate-50 rounded-xl animate-pulse" />
                ))}
              </div>
            ) : activeSeriesList.length === 0 ? (
              <div className="py-6 px-4 text-center rounded-xl bg-slate-50 border border-dashed border-slate-200 space-y-2.5">
                <div className="w-10 h-10 rounded-full bg-purple-50 text-purple-600 border border-purple-100 flex items-center justify-center mx-auto">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                  </svg>
                </div>
                <div>
                  <h3 className="text-xs font-bold text-slate-800">No active recurring bookings</h3>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    Create a recurring booking to reserve a room on a repeating cadence.
                  </p>
                </div>
                <Link to="/bookings/recurring">
                  <Button variant="outline" size="sm" className="text-xs">
                    Create Recurring Booking
                  </Button>
                </Link>
              </div>
            ) : (
              <div className="space-y-2.5">
                {activeSeriesList.slice(0, 3).map(({ seriesId, nextBooking }) => {
                  const timeData = nextBooking ? formatDateTime(nextBooking.startTime) : null

                  return (
                    <Link
                      key={seriesId}
                      to={`/bookings/recurring/${seriesId}`}
                      className="group p-3.5 rounded-xl bg-slate-50/70 hover:bg-purple-50/40 border border-slate-200/80 hover:border-purple-200 transition-all flex items-center justify-between"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="w-9 h-9 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center shrink-0 border border-purple-100">
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                          </svg>
                        </div>
                        <div className="min-w-0">
                          <h3 className="text-xs font-bold text-slate-900 group-hover:text-purple-700 transition-colors truncate">
                            Recurring Series #{seriesId}
                          </h3>
                          <p className="text-[11px] text-slate-500 truncate mt-0.5">
                            {nextBooking ? `Cadence active • ${timeData?.time}` : 'Active schedule'}
                          </p>
                        </div>
                      </div>

                      <div className="text-right shrink-0 ml-3 flex items-center gap-2">
                        <div>
                          <span className="text-[11px] font-semibold text-slate-700 block">
                            {timeData ? timeData.date : 'Upcoming'}
                          </span>
                          <span className="text-[10px] text-slate-500 block truncate max-w-[100px]">
                            {nextBooking?.roomName || 'Room'}
                          </span>
                        </div>
                        <svg className="w-4 h-4 text-slate-400 group-hover:text-purple-600 group-hover:translate-x-0.5 transition-all" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                        </svg>
                      </div>
                    </Link>
                  )
                })}
              </div>
            )}
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
          <div className="space-y-4 py-1 text-left">
            <div className="flex justify-between items-center pb-2.5 border-b border-slate-100">
              <span className="text-xs font-semibold uppercase text-slate-500">
                Reservation Status
              </span>
              <Badge variant={selectedBooking.status === 'CONFIRMED' ? 'success' : 'neutral'} size="sm">
                {selectedBooking.status}
              </Badge>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 text-xs">
              <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
                <span className="text-[11px] font-semibold uppercase text-slate-500 block">
                  Meeting Room
                </span>
                <p className="text-sm font-bold text-slate-900 mt-0.5">
                  {selectedBooking.roomName || `Room #${selectedBooking.roomId}`}
                </p>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
                <span className="text-[11px] font-semibold uppercase text-slate-500 block">
                  Timezone
                </span>
                <p className="text-sm font-medium text-slate-900 mt-0.5">Asia/Kolkata (IST)</p>
              </div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 text-xs">
              <span className="text-[11px] font-semibold uppercase text-slate-500 block">
                Scheduled Timing
              </span>
              <p className="text-sm font-bold text-slate-900 mt-0.5">
                {formatDateTime(selectedBooking.startTime).date}
              </p>
              <p className="text-xs text-slate-600 mt-0.5">
                {formatDateTime(selectedBooking.startTime).time} – {formatDateTime(selectedBooking.endTime).time}
              </p>
            </div>

            <div>
              <span className="text-xs font-semibold uppercase text-slate-700 block mb-1">
                Mandatory Booking Reason
              </span>
              <p className="text-xs text-slate-800 p-3 bg-slate-50 rounded-lg border border-slate-200 whitespace-pre-wrap leading-relaxed">
                {selectedBooking.reason}
              </p>
            </div>

            {selectedBooking.seriesId && (
              <div className="p-3 bg-purple-50 rounded-lg border border-purple-200 flex justify-between items-center text-xs">
                <span className="text-purple-900 font-semibold">Part of Recurring Series #{selectedBooking.seriesId}</span>
                <Link to={`/bookings/recurring/${selectedBooking.seriesId}`}>
                  <Button variant="outline" size="sm" className="text-xs">
                    View Series &rarr;
                  </Button>
                </Link>
              </div>
            )}

            <div className="flex justify-end gap-2 pt-3 border-t border-slate-100">
              <Button variant="primary" size="sm" onClick={() => setIsDetailsOpen(false)}>
                Close
              </Button>
            </div>
          </div>
        )}
      </Dialog>

      {/* Cancellation Confirmation Dialog */}
      <Dialog
        isOpen={isCancelDialogOpen}
        title="Cancel Room Booking?"
        onClose={() => !isCancelling && setIsCancelDialogOpen(false)}
      >
        {bookingToCancel && (
          <div className="space-y-4 py-1 text-left">
            <p className="text-xs text-slate-600 leading-relaxed">
              Are you sure you want to cancel the reservation for{' '}
              <strong className="font-semibold text-slate-900">
                {bookingToCancel.roomName || `Room #${bookingToCancel.roomId}`}
              </strong>
              ? The slot will be released and the status marked as <span className="font-semibold text-rose-600">CANCELLED</span>.
            </p>

            {cancelError && (
              <div className="p-3 bg-rose-50 text-rose-700 text-xs rounded-lg border border-rose-200">
                {cancelError.message || 'Failed to cancel booking.'}
              </div>
            )}

            <div>
              <label
                htmlFor="cancel-reason"
                className="block text-xs font-semibold uppercase text-slate-700 mb-1"
              >
                Cancellation Reason (Optional)
              </label>
              <textarea
                id="cancel-reason"
                rows={3}
                value={cancellationReason}
                maxLength={500}
                placeholder="e.g. Client requested postponement to next quarter..."
                onChange={(e) => setCancellationReason(e.target.value)}
                className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="flex justify-end gap-2.5 pt-3 border-t border-slate-200">
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
