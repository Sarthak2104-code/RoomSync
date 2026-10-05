import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import type { RoomOccupancyResponse, OccupancyStatus } from '@/types/occupancy'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/api/client'
import { occupancyAdminService } from '../api/occupancyAdminService'
import { locationAdminService } from '../api/locationAdminService'
import Table, { TableBody, TableCell, TableHeader, TableHead, TableRow } from '@/components/Table/Table'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'
import Select from '@/components/Select/Select'
import EmptyState from '@/components/EmptyState/EmptyState'

export const AdminOccupancyPage: React.FC = () => {
  const [occupancyData, setOccupancyData] = useState<RoomOccupancyResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [fetchError, setFetchError] = useState<string | null>(null)
  const [lastRefreshedAt, setLastRefreshedAt] = useState<Date>(new Date())

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedLocationId, setSelectedLocationId] = useState<string>('ALL')
  const [statusFilter, setStatusFilter] = useState<'ALL' | OccupancyStatus>('ALL')

  // Load locations reference data
  useEffect(() => {
    async function loadLocations() {
      try {
        const res = await locationAdminService.getLocations({ page: 0, size: 100, sort: 'name,asc' })
        setLocations(res.content || [])
      } catch {
        // Non-critical reference load
      }
    }
    loadLocations()
  }, [])

  // Authoritative Occupancy fetcher
  const fetchOccupancy = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }
    setFetchError(null)

    try {
      const locationId = selectedLocationId !== 'ALL' ? Number(selectedLocationId) : undefined
      const response = await occupancyAdminService.getOccupancy({
        locationId,
        page: 0,
        size: 100,
        sort: 'name,asc',
      })
      setOccupancyData(response.content || [])
      setLastRefreshedAt(new Date())
      if (isManualRefresh) {
        toast.success('Room occupancy updated.')
      }
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || 'Failed to load occupancy data from server.'
      setFetchError(message)
      if (isManualRefresh) {
        toast.error(message)
      }
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [selectedLocationId])

  useEffect(() => {
    fetchOccupancy(false)
  }, [fetchOccupancy])

  // Filtered dataset
  const filteredData = useMemo(() => {
    return occupancyData.filter((item) => {
      const q = searchQuery.trim().toLowerCase()
      const matchesSearch =
        q === '' ||
        item.roomName.toLowerCase().includes(q) ||
        (item.locationName && item.locationName.toLowerCase().includes(q))

      const matchesStatus =
        statusFilter === 'ALL' || item.occupancyStatus === statusFilter

      return matchesSearch && matchesStatus
    })
  }, [occupancyData, searchQuery, statusFilter])

  // KPI counts
  const totalCount = occupancyData.length
  const availableCount = occupancyData.filter((i) => i.occupancyStatus === 'AVAILABLE').length
  const occupiedCount = occupancyData.filter((i) => i.occupancyStatus === 'OCCUPIED').length
  const lockedCount = occupancyData.filter((i) => i.occupancyStatus === 'LOCKED').length

  // Time format helper
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
      return `${start} – ${end}`
    } catch {
      return `${startIso} – ${endIso}`
    }
  }

  // Location filter options
  const locationOptions = [
    { value: 'ALL', label: 'All Locations' },
    ...locations.map((loc) => ({
      value: String(loc.id),
      label: `${loc.name} (${loc.code})`,
    })),
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
            <span className="text-brand-navy font-semibold">Occupancy</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Room Occupancy Monitoring</h1>
          <p className="text-brand-slate text-sm mt-1">
            Live monitor of physical space availability, active meetings, and administrative locks across campuses.
          </p>
        </div>

        {/* Refresh Action */}
        <div className="flex items-center gap-3 self-start sm:self-auto">
          <span className="text-xs text-brand-slate hidden sm:inline">
            Last updated: {lastRefreshedAt.toLocaleTimeString()}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => fetchOccupancy(true)}
            loading={refreshing}
            disabled={loading || refreshing}
            className="flex items-center gap-2"
          >
            <svg className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh Live
          </Button>
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Total Rooms Monitored
            </span>
            <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-brand-navy mt-1">
            {loading ? <span className="text-slate-300">...</span> : totalCount}
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-emerald-600 uppercase tracking-wider">
              Available Now
            </span>
            <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-emerald-700 mt-1">
            {loading ? <span className="text-slate-300">...</span> : availableCount}
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-indigo-600 uppercase tracking-wider">
              Occupied Now
            </span>
            <span className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-indigo-700 mt-1">
            {loading ? <span className="text-slate-300">...</span> : occupiedCount}
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-amber-600 uppercase tracking-wider">
              Administratively Locked
            </span>
            <span className="p-2 rounded-lg bg-amber-50 text-amber-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-amber-700 mt-1">
            {loading ? <span className="text-slate-300">...</span> : lockedCount}
          </div>
        </div>
      </div>

      {/* Filters and Search Bar */}
      <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs flex flex-col md:flex-row gap-4 items-stretch md:items-center justify-between">
        <div className="flex flex-col sm:flex-row gap-3 flex-1">
          {/* Room Search */}
          <div className="relative flex-1 max-w-sm">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-brand-slate">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <input
              type="text"
              placeholder="Search by room name..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-sm border border-brand-slate/30 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-brand-accent focus:border-brand-accent transition-colors"
            />
          </div>

          {/* Location Dropdown */}
          <div className="w-full sm:w-56">
            <Select
              options={locationOptions}
              value={selectedLocationId}
              onChange={(e) => setSelectedLocationId(e.target.value)}
            />
          </div>
        </div>

        {/* Status Filter Tabs */}
        <div className="flex items-center rounded-lg bg-slate-100 p-1 self-start md:self-auto flex-wrap">
          <button
            type="button"
            onClick={() => setStatusFilter('ALL')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              statusFilter === 'ALL'
                ? 'bg-brand-white text-brand-navy shadow-xs'
                : 'text-brand-slate hover:text-brand-navy'
            }`}
          >
            All ({totalCount})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter('AVAILABLE')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              statusFilter === 'AVAILABLE'
                ? 'bg-brand-white text-emerald-700 shadow-xs'
                : 'text-brand-slate hover:text-brand-navy'
            }`}
          >
            Available ({availableCount})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter('OCCUPIED')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              statusFilter === 'OCCUPIED'
                ? 'bg-brand-white text-indigo-700 shadow-xs'
                : 'text-brand-slate hover:text-brand-navy'
            }`}
          >
            Occupied ({occupiedCount})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter('LOCKED')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              statusFilter === 'LOCKED'
                ? 'bg-brand-white text-amber-700 shadow-xs'
                : 'text-brand-slate hover:text-brand-navy'
            }`}
          >
            Locked ({lockedCount})
          </button>
        </div>
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
          <Button variant="outline" size="sm" onClick={() => fetchOccupancy(false)}>
            Retry
          </Button>
        </div>
      )}

      {/* Main Occupancy Monitoring Table */}
      <Table
        loading={loading}
        empty={!loading && filteredData.length === 0}
        emptyMessage="No rooms found for the selected location and occupancy criteria."
        columnCount={5}
      >
        <TableHeader>
          <TableRow>
            <TableHead>Meeting Room</TableHead>
            <TableHead>Location</TableHead>
            <TableHead>Capacity</TableHead>
            <TableHead>Current Occupancy State</TableHead>
            <TableHead>Active Booking Details</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {filteredData.map((room) => {
            const isOccupied = room.occupancyStatus === 'OCCUPIED'
            const isAvailable = room.occupancyStatus === 'AVAILABLE'
            const isLocked = room.occupancyStatus === 'LOCKED'
            const booking = room.currentBooking

            return (
              <TableRow key={room.roomId} className="hover:bg-slate-50/70 transition-colors">
                {/* Room */}
                <TableCell>
                  <div className="font-bold text-brand-navy">{room.roomName}</div>
                  <div className="text-xs text-brand-slate font-mono">ID: #{room.roomId}</div>
                </TableCell>

                {/* Location */}
                <TableCell>
                  <div className="font-medium text-brand-navy text-sm">
                    {room.locationName || `Location #${room.locationId}`}
                  </div>
                  {room.locationTimezone && (
                    <div className="text-xs text-brand-slate font-mono">
                      {room.locationTimezone}
                    </div>
                  )}
                </TableCell>

                {/* Capacity */}
                <TableCell>
                  <div className="text-sm font-medium text-brand-navy">
                    {room.capacity} seats
                  </div>
                </TableCell>

                {/* Current Occupancy State (AVAILABLE / OCCUPIED / LOCKED) */}
                <TableCell>
                  {isAvailable && (
                    <Badge variant="success" size="md" className="font-semibold flex items-center gap-1.5 w-fit">
                      <span className="w-2 h-2 rounded-full bg-emerald-500" />
                      AVAILABLE
                    </Badge>
                  )}
                  {isOccupied && (
                    <Badge variant="info" size="md" className="font-semibold bg-indigo-50 text-indigo-700 border-indigo-200 flex items-center gap-1.5 w-fit">
                      <span className="w-2 h-2 rounded-full bg-indigo-500 animate-pulse" />
                      OCCUPIED
                    </Badge>
                  )}
                  {isLocked && (
                    <Badge variant="warning" size="md" className="font-semibold flex items-center gap-1.5 w-fit">
                      <span className="w-2 h-2 rounded-full bg-amber-500" />
                      LOCKED
                    </Badge>
                  )}
                </TableCell>

                {/* Current Booking Context */}
                <TableCell>
                  {isOccupied && booking ? (
                    <div className="p-2.5 rounded-lg bg-indigo-50/60 border border-indigo-100 max-w-sm space-y-1">
                      <div className="flex items-center justify-between text-xs font-bold text-indigo-900">
                        <span>{formatTimeRange(booking.startTime, booking.endTime)}</span>
                        <span className="font-mono text-[11px] font-normal text-indigo-700">Booking #{booking.bookingId}</span>
                      </div>
                      <div className="text-xs text-brand-navy flex flex-wrap items-center gap-1.5">
                        <span className="text-brand-slate font-medium">Booked by:</span>
                        {booking.userWissenId && (
                          <span className="font-mono text-[11px] font-semibold px-1.5 py-0.5 rounded bg-indigo-100 text-indigo-900 border border-indigo-200">
                            {booking.userWissenId}
                          </span>
                        )}
                        <span className="font-semibold">{booking.userName || (booking.userWissenId ? '' : `User #${booking.userId}`)}</span>
                        {booking.userEmail && <span className="text-brand-slate">({booking.userEmail})</span>}
                      </div>
                      {booking.reason && (
                        <div className="text-xs text-slate-600 line-clamp-1 italic">
                          "{booking.reason}"
                        </div>
                      )}
                    </div>
                  ) : isLocked ? (
                    <span className="text-xs text-brand-slate italic">Administratively blocked</span>
                  ) : (
                    <span className="text-xs text-emerald-700 font-medium flex items-center gap-1">
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                      </svg>
                      Vacant / No active reservation
                    </span>
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>

      {/* Empty State */}
      {!loading && filteredData.length === 0 && !fetchError && (
        <div className="pt-2">
          <EmptyState
            title="No rooms to monitor"
            description="No room occupancy data found matching your location or filter selection."
            action={
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setSelectedLocationId('ALL')
                  setStatusFilter('ALL')
                  setSearchQuery('')
                }}
              >
                Reset Filters
              </Button>
            }
          />
        </div>
      )}
    </div>
  )
}

export default AdminOccupancyPage
