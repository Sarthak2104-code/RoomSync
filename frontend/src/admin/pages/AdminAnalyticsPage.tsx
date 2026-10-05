import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import type { UtilizationReportResponse } from '@/types/analytics'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/api/client'
import { analyticsAdminService } from '../api/analyticsAdminService'
import { locationAdminService } from '../api/locationAdminService'
import Table, { TableBody, TableCell, TableHeader, TableHead, TableRow } from '@/components/Table/Table'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'
import Select from '@/components/Select/Select'
import Input from '@/components/Input/Input'
import EmptyState from '@/components/EmptyState/EmptyState'

function getInitialDates() {
  const today = new Date()
  const sevenDaysAgo = new Date()
  sevenDaysAgo.setDate(today.getDate() - 7)

  return {
    start: sevenDaysAgo.toISOString().split('T')[0],
    end: today.toISOString().split('T')[0],
  }
}

export const AdminAnalyticsPage: React.FC = () => {
  const initialDates = useMemo(() => getInitialDates(), [])
  const [startDate, setStartDate] = useState(initialDates.start)
  const [endDate, setEndDate] = useState(initialDates.end)
  const [selectedLocationId, setSelectedLocationId] = useState<string>('ALL')

  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [report, setReport] = useState<UtilizationReportResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [fetchError, setFetchError] = useState<string | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)

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

  // Authoritative Utilization fetcher
  const fetchUtilization = useCallback(
    async (isManualRefresh = false) => {
      setValidationError(null)
      if (startDate && endDate && endDate < startDate) {
        setValidationError('End date cannot be earlier than start date.')
        return
      }

      if (isManualRefresh) {
        setRefreshing(true)
      } else {
        setLoading(true)
      }
      setFetchError(null)

      try {
        const locationId = selectedLocationId !== 'ALL' ? Number(selectedLocationId) : undefined
        const data = await analyticsAdminService.getUtilization({
          startDate: startDate || undefined,
          endDate: endDate || undefined,
          locationId,
        })
        setReport(data)
        if (isManualRefresh) {
          toast.success('Utilization analytics refreshed.')
        }
      } catch (err: unknown) {
        const apiErr = err as ApiError
        const message = apiErr.message || 'Failed to load utilization data from server.'
        setFetchError(message)
        if (isManualRefresh) {
          toast.error(message)
        }
      } finally {
        setLoading(false)
        setRefreshing(false)
      }
    },
    [startDate, endDate, selectedLocationId],
  )

  useEffect(() => {
    fetchUtilization(false)
  }, [fetchUtilization])

  const handleApplyFilter = (e: React.FormEvent) => {
    e.preventDefault()
    fetchUtilization(false)
  }

  // Location filter options
  const locationOptions = [
    { value: 'ALL', label: 'All Locations' },
    ...locations.map((loc) => ({
      value: String(loc.id),
      label: `${loc.name} (${loc.code})`,
    })),
  ]

  // Derived aggregates from backend-returned room items
  const rooms = report?.rooms || []
  const totalBookedMinutes = rooms.reduce((acc, r) => acc + (r.totalBookedMinutes || 0), 0)
  const totalAvailableMinutes = rooms.reduce((acc, r) => acc + (r.totalAvailableMinutes || 0), 0)
  const totalBookingsCount = rooms.reduce((acc, r) => acc + (r.bookingCount || 0), 0)

  const overallUtilPercent = report ? report.overallUtilizationPercentage : 0

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
            <span className="text-brand-navy font-semibold">Analytics</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Analytics & Room Utilization</h1>
          <p className="text-brand-slate text-sm mt-1">
            Authoritative room utilization, operating hours, and booking density analytics across enterprise campuses.
          </p>
        </div>

        {/* Refresh Action */}
        <div className="flex items-center gap-3 self-start sm:self-auto">
          <Button
            variant="outline"
            size="sm"
            onClick={() => fetchUtilization(true)}
            loading={refreshing}
            disabled={loading || refreshing}
            className="flex items-center gap-2"
          >
            <svg className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh Analytics
          </Button>
        </div>
      </div>

      {/* Date Range & Location Controls */}
      <form
        onSubmit={handleApplyFilter}
        className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs flex flex-col md:flex-row gap-4 items-end flex-wrap"
      >
        {/* Start Date */}
        <div className="w-full sm:w-44">
          <Input
            label="Start Date"
            type="date"
            required
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            disabled={loading}
          />
        </div>

        {/* End Date */}
        <div className="w-full sm:w-44">
          <Input
            label="End Date"
            type="date"
            required
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            disabled={loading}
          />
        </div>

        {/* Location Selector */}
        <div className="w-full sm:w-52">
          <label className="block text-sm font-medium text-brand-navy mb-1 text-left">
            Location Scope
          </label>
          <Select
            options={locationOptions}
            value={selectedLocationId}
            onChange={(e) => setSelectedLocationId(e.target.value)}
            disabled={loading}
          />
        </div>

        {/* Apply Button */}
        <div className="w-full sm:w-auto">
          <Button
            type="submit"
            variant="primary"
            size="md"
            loading={loading && !refreshing}
            disabled={loading}
            className="w-full sm:w-auto"
          >
            Apply Range
          </Button>
        </div>
      </form>

      {/* Validation Warning */}
      {validationError && (
        <div
          role="alert"
          className="p-3.5 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-800 font-medium"
        >
          {validationError}
        </div>
      )}

      {/* Backend Error Banner with Retry */}
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
          <Button variant="outline" size="sm" onClick={() => fetchUtilization(false)}>
            Retry
          </Button>
        </div>
      )}

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Overall Utilization % */}
        <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Overall Utilization
            </span>
            <span className="p-2 rounded-lg bg-purple-50 text-purple-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
              </svg>
            </span>
          </div>
          <div className="text-3xl font-bold text-brand-navy mt-2">
            {loading ? <span className="text-slate-300">...</span> : `${overallUtilPercent.toFixed(1)}%`}
          </div>
          <div className="mt-2 w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
            <div
              className="bg-brand-accent h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${Math.min(100, Math.max(0, overallUtilPercent))}%` }}
            />
          </div>
        </div>

        {/* Total Booked Hours */}
        <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Total Booked Hours
            </span>
            <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </span>
          </div>
          <div className="text-3xl font-bold text-brand-navy mt-2">
            {loading ? <span className="text-slate-300">...</span> : `${(totalBookedMinutes / 60).toFixed(1)} h`}
          </div>
          <div className="text-xs text-brand-slate mt-1 font-medium">
            Across {rooms.length} room(s)
          </div>
        </div>

        {/* Total Operating / Available Hours */}
        <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Operating / Available
            </span>
            <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </span>
          </div>
          <div className="text-3xl font-bold text-brand-navy mt-2">
            {loading ? <span className="text-slate-300">...</span> : `${(totalAvailableMinutes / 60).toFixed(1)} h`}
          </div>
          <div className="text-xs text-brand-slate mt-1 font-medium">
            Window: {report?.startDate || startDate} to {report?.endDate || endDate}
          </div>
        </div>

        {/* Total Meetings */}
        <div className="bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Total Meetings
            </span>
            <span className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
            </span>
          </div>
          <div className="text-3xl font-bold text-brand-navy mt-2">
            {loading ? <span className="text-slate-300">...</span> : totalBookingsCount}
          </div>
          <div className="text-xs text-brand-slate mt-1 font-medium">
            Completed / Scheduled
          </div>
        </div>
      </div>

      {/* Room-Level Utilization Table */}
      <Table
        loading={loading}
        empty={!loading && rooms.length === 0}
        emptyMessage="No utilization records found for the selected dates and filters."
        columnCount={6}
      >
        <TableHeader>
          <TableRow>
            <TableHead>Meeting Room</TableHead>
            <TableHead>Location</TableHead>
            <TableHead>Booked Time</TableHead>
            <TableHead>Available Operating Time</TableHead>
            <TableHead>Total Meetings</TableHead>
            <TableHead>Utilization %</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rooms.map((room) => {
            const bookedHours = (room.totalBookedMinutes / 60).toFixed(1)
            const availableHours = (room.totalAvailableMinutes / 60).toFixed(1)
            const utilPercent = room.utilizationPercentage

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

                {/* Booked Time */}
                <TableCell>
                  <div className="font-semibold text-brand-navy text-sm">
                    {bookedHours} h
                  </div>
                  <div className="text-xs text-brand-slate font-mono">
                    {room.totalBookedMinutes} min
                  </div>
                </TableCell>

                {/* Available Operating Time */}
                <TableCell>
                  <div className="font-semibold text-brand-navy text-sm">
                    {availableHours} h
                  </div>
                  <div className="text-xs text-brand-slate font-mono">
                    {room.totalAvailableMinutes} min
                  </div>
                </TableCell>

                {/* Meetings Count */}
                <TableCell>
                  <div className="text-sm font-medium text-brand-navy">
                    {room.bookingCount}
                  </div>
                </TableCell>

                {/* Utilization % */}
                <TableCell>
                  <div className="space-y-1.5 max-w-xs">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-sm font-bold text-brand-navy font-mono">
                        {utilPercent.toFixed(1)}%
                      </span>
                      {utilPercent < 30 ? (
                        <Badge variant="warning" size="sm">
                          Underutilized
                        </Badge>
                      ) : utilPercent > 70 ? (
                        <Badge variant="success" size="sm">
                          High Demand
                        </Badge>
                      ) : (
                        <Badge variant="info" size="sm">
                          Optimal
                        </Badge>
                      )}
                    </div>
                    <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                      <div
                        className={`h-1.5 rounded-full transition-all duration-300 ${
                          utilPercent < 30
                            ? 'bg-amber-500'
                            : utilPercent > 70
                              ? 'bg-emerald-500'
                              : 'bg-brand-accent'
                        }`}
                        style={{ width: `${Math.min(100, Math.max(0, utilPercent))}%` }}
                      />
                    </div>
                  </div>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>

      {/* Empty State */}
      {!loading && rooms.length === 0 && !fetchError && (
        <div className="pt-2">
          <EmptyState
            title="No utilization records found"
            description="No room utilization data available for the chosen date range and location filter."
            action={
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  const initial = getInitialDates()
                  setStartDate(initial.start)
                  setEndDate(initial.end)
                  setSelectedLocationId('ALL')
                }}
              >
                Reset Date Range
              </Button>
            }
          />
        </div>
      )}
    </div>
  )
}

export default AdminAnalyticsPage
