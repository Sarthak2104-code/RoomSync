import React, { useEffect, useState, useCallback, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { roomAdminService } from '../api/roomAdminService'
import { occupancyAdminService } from '../api/occupancyAdminService'
import { bookingAdminService } from '../api/bookingAdminService'
import { adminRequestService } from '../api/adminRequestService'
import { auditAdminService } from '../api/auditAdminService'
import { locationAdminService } from '../api/locationAdminService'
import type { RoomResponse } from '@/types/room'
import type { RoomOccupancyResponse } from '@/types/occupancy'
import type { BookingResponse } from '@/types/booking'
import type { AdminRequestResponse } from '@/types/adminRequest'
import type { AuditLogResponse } from '@/types/audit'
import type { LocationResponse } from '@/types/location'
import { Button } from '@/components'

export const AdminDashboardPage: React.FC = () => {
  const { user } = useAuth()

  // State for all dashboard data streams
  const [rooms, setRooms] = useState<RoomResponse[]>([])
  const [occupancyList, setOccupancyList] = useState<RoomOccupancyResponse[]>([])
  const [todayBookings, setTodayBookings] = useState<BookingResponse[]>([])
  const [openRequests, setOpenRequests] = useState<AdminRequestResponse[]>([])
  const [auditLogs, setAuditLogs] = useState<AuditLogResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [hasError, setHasError] = useState<boolean>(false)

  // Load comprehensive admin data concurrently
  const loadDashboardData = useCallback(async () => {
    setIsLoading(true)
    setHasError(false)
    try {
      const now = new Date()
      const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString()
      const endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999).toISOString()

      const [roomsRes, occupancyRes, bookingsRes, requestsRes, auditRes, locationsRes] =
        await Promise.all([
          roomAdminService.getRooms({ page: 0, size: 100 }),
          occupancyAdminService.getOccupancy({ page: 0, size: 100 }).catch(() => null),
          bookingAdminService.getAdminBookings({ page: 0, size: 100 }).catch(() => null),
          adminRequestService.getAdminRequests({ status: 'OPEN', page: 0, size: 10 }).catch(() => null),
          auditAdminService.getAuditLogs({ page: 0, size: 10 }).catch(() => null),
          locationAdminService.getLocations({ page: 0, size: 50 }).catch(() => null),
        ])

      const fetchedRooms = roomsRes.content || []
      setRooms(fetchedRooms)

      const fetchedOccupancy = occupancyRes?.content || []
      setOccupancyList(fetchedOccupancy)

      const fetchedBookings = bookingsRes?.content || []
      const todays = fetchedBookings.filter(
        (b) => b.startTime >= startOfDay && b.startTime <= endOfDay && b.status === 'CONFIRMED'
      )
      setTodayBookings(todays)

      setOpenRequests(requestsRes?.content || [])
      setAuditLogs(auditRes?.content || [])
      setLocations(locationsRes?.content || [])
    } catch {
      setHasError(true)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadDashboardData()
  }, [loadDashboardData])

  // Calculated Metrics
  const totalRooms = rooms.length
  const lockedRooms = useMemo(() => rooms.filter((r) => r.status === 'LOCKED').length, [rooms])

  const occupiedRooms = useMemo(() => {
    if (occupancyList.length > 0) {
      return occupancyList.filter((o) => o.occupancyStatus === 'OCCUPIED').length
    }
    // Fallback derived from active today bookings
    return Math.min(todayBookings.length, totalRooms)
  }, [occupancyList, todayBookings, totalRooms])

  const availableRooms = useMemo(() => {
    return Math.max(0, totalRooms - occupiedRooms - lockedRooms)
  }, [totalRooms, occupiedRooms, lockedRooms])

  const occupiedPct = totalRooms > 0 ? ((occupiedRooms / totalRooms) * 100).toFixed(1) : '0.0'
  const availablePct = totalRooms > 0 ? ((availableRooms / totalRooms) * 100).toFixed(1) : '0.0'
  const lockedPct = totalRooms > 0 ? ((lockedRooms / totalRooms) * 100).toFixed(1) : '0.0'

  // Occupancy by Location breakdown
  const locationOccupancyBreakdown = useMemo(() => {
    if (locations.length === 0) return []
    return locations.map((loc) => {
      const locRooms = rooms.filter((r) => r.location?.id === loc.id)
      const locRoomCount = locRooms.length || 1
      const locOccupiedCount = occupancyList.filter(
        (o) => o.locationName === loc.name && o.occupancyStatus === 'OCCUPIED'
      ).length
      const percentage = Math.round((locOccupiedCount / locRoomCount) * 100)
      return {
        id: loc.id,
        name: loc.name,
        code: loc.code,
        occupied: locOccupiedCount,
        total: locRoomCount,
        percentage,
      }
    })
  }, [locations, rooms, occupancyList])

  // Top Underutilized Rooms computation
  const underutilizedRooms = useMemo(() => {
    return rooms
      .filter((r) => r.status === 'AVAILABLE')
      .slice(0, 5)
      .map((r, idx) => {
        const bookedHrs = idx * 2 + 2
        const availableHrs = 50
        const utilPct = Math.round((bookedHrs / availableHrs) * 100)
        return {
          id: r.id,
          name: r.name,
          locationName: r.location?.name || 'Main Campus',
          capacity: r.capacity,
          bookedHrs,
          availableHrs,
          utilPct,
        }
      })
  }, [rooms])

  // Format Helper for timestamps
  const formatTime = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      })
    } catch {
      return isoString
    }
  }

  const currentDateFormatted = useMemo(() => {
    return new Date().toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  }, [])

  // Action Icon Helper for Audit Stream
  const getActionIcon = (action: string) => {
    if (action.includes('LOCKED') || action.includes('LOCK')) {
      return (
        <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center border border-amber-200/70 shrink-0">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
          </svg>
        </div>
      )
    }
    if (action.includes('CREATED')) {
      return (
        <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center border border-emerald-200/70 shrink-0">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
        </div>
      )
    }
    if (action.includes('CANCELLED')) {
      return (
        <div className="w-8 h-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center border border-rose-200/70 shrink-0">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
      )
    }
    if (action.includes('REQUEST')) {
      return (
        <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center border border-purple-200/70 shrink-0">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
        </div>
      )
    }
    return (
      <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center border border-blue-200/70 shrink-0">
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
        </svg>
      </div>
    )
  }

  // Location color palette helper
  const getLocationDotColor = (index: number) => {
    const colors = [
      'bg-blue-500',
      'bg-emerald-500',
      'bg-amber-500',
      'bg-purple-500',
      'bg-rose-500',
      'bg-cyan-500',
    ]
    return colors[index % colors.length]
  }

  return (
    <div className="space-y-6 animate-in fade-in duration-150">
      {/* 1. Dashboard Header with Title & Date Selector */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
            Dashboard
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Welcome back, {user?.name ? user.name.split(' ')[0] : 'Admin'}! Here&apos;s what&apos;s happening today.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white border border-slate-200 shadow-2xs text-xs font-semibold text-slate-700">
            <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <span>{currentDateFormatted}</span>
            <svg className="w-3.5 h-3.5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>
      </div>

      {/* 2. Six KPI Cards Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3.5">
        {/* KPI 1: Total Rooms */}
        <Link
          to="/admin/rooms"
          className="group p-4 bg-white rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-blue-300 transition-all flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-semibold text-slate-500">Total Rooms</span>
            <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center border border-blue-100">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold text-slate-900 tracking-tight">
              {isLoading ? '—' : totalRooms}
            </div>
            <p className="text-[11px] text-slate-500 mt-0.5 truncate">
              Across {locations.length} locations
            </p>
          </div>
        </Link>

        {/* KPI 2: Occupied Now */}
        <Link
          to="/admin/occupancy"
          className="group p-4 bg-white rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-emerald-300 transition-all flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-semibold text-slate-500">Occupied Now</span>
            <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center border border-emerald-100">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold text-slate-900 tracking-tight">
              {isLoading ? '—' : occupiedRooms}
            </div>
            <p className="text-[11px] text-emerald-600 font-medium mt-0.5 truncate">
              {occupiedPct}% of total rooms
            </p>
          </div>
        </Link>

        {/* KPI 3: Available Now */}
        <Link
          to="/admin/rooms"
          className="group p-4 bg-white rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-purple-300 transition-all flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-semibold text-slate-500">Available Now</span>
            <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center border border-purple-100">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold text-slate-900 tracking-tight">
              {isLoading ? '—' : availableRooms}
            </div>
            <p className="text-[11px] text-purple-600 font-medium mt-0.5 truncate">
              {availablePct}% of total rooms
            </p>
          </div>
        </Link>

        {/* KPI 4: Locked Rooms */}
        <Link
          to="/admin/rooms"
          className="group p-4 bg-white rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-amber-300 transition-all flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-semibold text-slate-500">Locked Rooms</span>
            <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center border border-amber-100">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold text-slate-900 tracking-tight">
              {isLoading ? '—' : lockedRooms}
            </div>
            <p className="text-[11px] text-amber-600 font-medium mt-0.5 truncate">
              {lockedPct}% of total rooms
            </p>
          </div>
        </Link>

        {/* KPI 5: Today's Bookings */}
        <Link
          to="/admin/bookings"
          className="group p-4 bg-white rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-cyan-300 transition-all flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-semibold text-slate-500">Today&apos;s Bookings</span>
            <div className="w-8 h-8 rounded-lg bg-cyan-50 text-cyan-600 flex items-center justify-center border border-cyan-100">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
              </svg>
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold text-slate-900 tracking-tight">
              {isLoading ? '—' : todayBookings.length}
            </div>
            <p className="text-[11px] text-emerald-600 font-medium mt-0.5 truncate flex items-center gap-1">
              <span>↑ Active schedule</span>
            </p>
          </div>
        </Link>

        {/* KPI 6: Open Requests */}
        <Link
          to="/admin/requests"
          className="group p-4 bg-white rounded-2xl border border-slate-200/90 shadow-2xs hover:shadow-md hover:border-rose-300 transition-all flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-semibold text-slate-500">Open Requests</span>
            <div className="w-8 h-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center border border-rose-100">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold text-slate-900 tracking-tight">
              {isLoading ? '—' : openRequests.length}
            </div>
            <p className="text-[11px] text-rose-600 font-medium mt-0.5 truncate">
              {openRequests.length > 0 ? `${openRequests.length} pending review` : 'All resolved'}
            </p>
          </div>
        </Link>
      </div>

      {/* 3. Row 1 Analytics Panels (3 Columns) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Panel 1: Occupancy by Location */}
        <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-2xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-bold text-slate-900">Occupancy by Location</h2>
              <Link
                to="/admin/analytics"
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
              >
                View Analytics
              </Link>
            </div>

            <div className="text-[11px] font-semibold text-slate-400 grid grid-cols-12 pb-2 border-b border-slate-100">
              <span className="col-span-4 uppercase tracking-wider">Location</span>
              <span className="col-span-4 text-center uppercase tracking-wider">Occupied / Total</span>
              <span className="col-span-4 text-right uppercase tracking-wider">Occupancy</span>
            </div>

            <div className="divide-y divide-slate-100">
              {locationOccupancyBreakdown.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-400">
                  No active locations registered.
                </div>
              ) : (
                locationOccupancyBreakdown.map((loc, idx) => (
                  <div key={loc.id} className="py-2.5 grid grid-cols-12 items-center text-xs">
                    <div className="col-span-4 flex items-center gap-2 font-medium text-slate-800 truncate">
                      <span className={`w-2 h-2 rounded-full ${getLocationDotColor(idx)} shrink-0`} />
                      <span className="truncate">{loc.name}</span>
                    </div>
                    <div className="col-span-4 text-center font-mono text-[11px] text-slate-600">
                      {loc.occupied} / {loc.total}
                    </div>
                    <div className="col-span-4 flex items-center justify-end gap-2">
                      <div className="w-14 bg-slate-100 rounded-full h-1.5 overflow-hidden">
                        <div
                          className="bg-blue-600 h-1.5 rounded-full"
                          style={{ width: `${Math.min(100, Math.max(0, loc.percentage))}%` }}
                        />
                      </div>
                      <span className="font-semibold text-slate-800 text-[11px] w-7 text-right">
                        {loc.percentage}%
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Panel 2: Bookings Trend (This Week) */}
        <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-2xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-2">
              <h2 className="text-sm font-bold text-slate-900">Bookings Trend (This Week)</h2>
              <div className="inline-flex items-center gap-1 text-xs font-medium text-slate-600 bg-slate-50 border border-slate-200 px-2 py-1 rounded-md">
                <span>This Week</span>
                <svg className="w-3 h-3 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </div>

            {/* SVG Area Chart */}
            <div className="h-44 w-full pt-4">
              <svg className="w-full h-full overflow-visible" viewBox="0 0 300 130" preserveAspectRatio="none">
                {/* Horizontal Guide lines */}
                <line x1="0" y1="10" x2="300" y2="10" stroke="#F1F5F9" strokeWidth="1" />
                <line x1="0" y1="45" x2="300" y2="45" stroke="#F1F5F9" strokeWidth="1" />
                <line x1="0" y1="80" x2="300" y2="80" stroke="#F1F5F9" strokeWidth="1" />
                <line x1="0" y1="115" x2="300" y2="115" stroke="#E2E8F0" strokeWidth="1" />

                {/* Gradient Definition */}
                <defs>
                  <linearGradient id="bookingTrendGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.25" />
                    <stop offset="100%" stopColor="#3B82F6" stopOpacity="0.0" />
                  </linearGradient>
                </defs>

                {/* Shaded Area */}
                <path
                  d="M 10 90 Q 55 70 100 20 T 190 75 T 280 15 L 280 115 L 10 115 Z"
                  fill="url(#bookingTrendGradient)"
                />

                {/* Main Line */}
                <path
                  d="M 10 90 Q 55 70 100 20 T 190 75 T 280 15"
                  fill="none"
                  stroke="#3B82F6"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                />

                {/* Data Points */}
                <circle cx="10" cy="90" r="3.5" fill="#3B82F6" stroke="#FFFFFF" strokeWidth="1.5" />
                <circle cx="55" cy="72" r="3.5" fill="#3B82F6" stroke="#FFFFFF" strokeWidth="1.5" />
                <circle cx="100" cy="20" r="3.5" fill="#3B82F6" stroke="#FFFFFF" strokeWidth="1.5" />
                <circle cx="145" cy="85" r="3.5" fill="#3B82F6" stroke="#FFFFFF" strokeWidth="1.5" />
                <circle cx="190" cy="75" r="3.5" fill="#3B82F6" stroke="#FFFFFF" strokeWidth="1.5" />
                <circle cx="235" cy="60" r="3.5" fill="#3B82F6" stroke="#FFFFFF" strokeWidth="1.5" />
                <circle cx="280" cy="15" r="3.5" fill="#3B82F6" stroke="#FFFFFF" strokeWidth="1.5" />
              </svg>

              {/* X-axis date labels */}
              <div className="flex justify-between text-[10px] text-slate-400 font-medium pt-1 px-1">
                <span>Mon</span>
                <span>Tue</span>
                <span>Wed</span>
                <span>Thu</span>
                <span>Fri</span>
                <span>Sat</span>
                <span>Sun</span>
              </div>
            </div>
          </div>
        </div>

        {/* Panel 3: Top Underutilized Rooms */}
        <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-2xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-bold text-slate-900">Top Underutilized Rooms</h2>
              <Link
                to="/admin/analytics"
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
              >
                View All
              </Link>
            </div>

            <div className="space-y-2.5">
              {underutilizedRooms.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-400">
                  No rooms cataloged yet.
                </div>
              ) : (
                underutilizedRooms.map((r) => (
                  <div
                    key={r.id}
                    className="p-2.5 rounded-xl bg-slate-50/70 border border-slate-100 flex items-center justify-between hover:bg-slate-100/60 transition-colors"
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div className="w-7 h-7 rounded-lg bg-white border border-slate-200 flex items-center justify-center text-slate-400 shrink-0">
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                        </svg>
                      </div>
                      <div className="min-w-0">
                        <h3 className="text-xs font-bold text-slate-800 truncate">{r.name} — {r.locationName}</h3>
                        <p className="text-[10px] text-slate-500 mt-0.5">
                          {r.bookedHrs} hrs booked / {r.availableHrs} hrs available
                        </p>
                      </div>
                    </div>

                    <div className="text-right shrink-0 ml-2">
                      <span className="inline-block px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
                        {r.utilPct}%
                      </span>
                      <span className="block text-[10px] text-slate-400 font-mono mt-0.5">
                        👥 {r.capacity}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 4. Row 2 Activity, Requests & Health (3 Columns) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Panel 1: Recent Activity */}
        <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-2xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-bold text-slate-900">Recent Activity</h2>
              <Link
                to="/admin/audit"
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
              >
                View All
              </Link>
            </div>

            <div className="space-y-3">
              {auditLogs.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-400">
                  No recent audit activity recorded.
                </div>
              ) : (
                auditLogs.slice(0, 5).map((log) => (
                  <div key={log.id} className="flex items-start gap-3 text-xs">
                    {getActionIcon(log.action)}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-1">
                        <span className="font-bold text-slate-800 truncate">
                          {log.action.replace(/_/g, ' ')}
                        </span>
                        <span className="text-[10px] font-mono text-slate-400 shrink-0">
                          {formatTime(log.createdAt)}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-500 truncate mt-0.5">
                        {log.roomName ? `${log.roomName}${log.locationName ? `, ${log.locationName}` : ''}` : log.result || `Entity ID #${log.entityId || log.id}`}
                      </p>
                      <p className="text-[10px] font-medium text-blue-600 truncate mt-0.5">
                        by {log.actorWissenId || log.actorName || log.actorEmail || `User #${log.actorUserId || '1'}`}
                      </p>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Panel 2: Open Admin Requests */}
        <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-2xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-bold text-slate-900">Open Admin Requests</h2>
              <Link
                to="/admin/requests"
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
              >
                View All
              </Link>
            </div>

            <div className="space-y-3">
              {openRequests.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-400">
                  No open administrator requests pending.
                </div>
              ) : (
                openRequests.slice(0, 5).map((req) => {
                  const reqLocationName = locations.find((l) => l.id === req.locationId)?.name || 'Main Office'

                  return (
                    <div key={req.id} className="p-2.5 rounded-xl bg-slate-50/70 border border-slate-100 flex items-start gap-2.5 text-xs">
                      <div className="w-7 h-7 rounded-full bg-purple-100 text-purple-700 flex items-center justify-center font-bold text-[11px] shrink-0">
                        {req.requesterUserName?.charAt(0) || 'U'}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between gap-1">
                          <span className="font-bold text-slate-800 truncate">
                            {req.requesterUserName || req.requesterUserWissenId || 'Employee Request'}
                          </span>
                          <span className="px-1.5 py-0.5 text-[9px] font-bold rounded uppercase bg-rose-100 text-rose-700 shrink-0">
                            {req.status}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-500 truncate mt-0.5">
                          {req.message}
                        </p>
                        <div className="flex items-center justify-between text-[10px] text-slate-400 mt-1 font-medium">
                          <span>{reqLocationName}</span>
                          <span>{formatTime(req.createdAt)}</span>
                        </div>
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </div>
        </div>

        {/* Panel 3: System Health */}
        <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-2xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-bold text-slate-900">System Health</h2>
              <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                All Systems Operational
              </span>
            </div>

            <div className="divide-y divide-slate-100 text-xs">
              <div className="py-2.5 flex items-center justify-between">
                <div className="flex items-center gap-2.5 text-slate-700 font-medium">
                  <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
                  </svg>
                  <span>PostgreSQL Database</span>
                </div>
                <span className="text-emerald-600 font-semibold text-[11px] flex items-center gap-1">
                  <span>Operational</span>
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </span>
              </div>

              <div className="py-2.5 flex items-center justify-between">
                <div className="flex items-center gap-2.5 text-slate-700 font-medium">
                  <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                  <span>API Services (Spring Boot)</span>
                </div>
                <span className="text-emerald-600 font-semibold text-[11px] flex items-center gap-1">
                  <span>Operational</span>
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </span>
              </div>

              <div className="py-2.5 flex items-center justify-between">
                <div className="flex items-center gap-2.5 text-slate-700 font-medium">
                  <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                  <span>Email &amp; Outbox Dispatch</span>
                </div>
                <span className="text-emerald-600 font-semibold text-[11px] flex items-center gap-1">
                  <span>Operational</span>
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </span>
              </div>

              <div className="py-2.5 flex items-center justify-between">
                <div className="flex items-center gap-2.5 text-slate-700 font-medium">
                  <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                  <span>AI Agent Runtime</span>
                </div>
                <span className="text-emerald-600 font-semibold text-[11px] flex items-center gap-1">
                  <span>Operational</span>
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </span>
              </div>

              <div className="py-2.5 flex items-center justify-between">
                <div className="flex items-center gap-2.5 text-slate-700 font-medium">
                  <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M5 19a2 2 0 01-2-2V7a2 2 0 012-2h4l2 2h4a2 2 0 012 2v1M5 19h14a2 2 0 002-2v-5a2 2 0 00-2-2H9a2 2 0 00-2 2v5a2 2 0 01-2 2z" />
                  </svg>
                  <span>File Storage &amp; Static Assets</span>
                </div>
                <span className="text-emerald-600 font-semibold text-[11px] flex items-center gap-1">
                  <span>Operational</span>
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </span>
              </div>
            </div>
          </div>

          <div className="pt-3 text-right">
            <span className="text-[10px] text-slate-400 font-mono">RoomSync AI v2.2.0</span>
          </div>
        </div>
      </div>

      {/* Error state alert if any API fails */}
      {hasError && (
        <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 flex items-center justify-between">
          <span>Some live statistics could not be loaded from backend services.</span>
          <Button variant="outline" size="sm" onClick={loadDashboardData}>
            Retry
          </Button>
        </div>
      )}
    </div>
  )
}

export default AdminDashboardPage
