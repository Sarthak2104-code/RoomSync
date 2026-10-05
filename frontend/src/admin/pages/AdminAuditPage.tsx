import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { AuditLogResponse } from '@/types/audit'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/api/client'
import { auditAdminService } from '../api/auditAdminService'
import { locationAdminService } from '../api/locationAdminService'
import AuditLogDetailModal from '../audit/AuditLogDetailModal'
import Table, { TableBody, TableCell, TableHeader, TableHead, TableRow } from '@/components/Table/Table'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'
import Select from '@/components/Select/Select'
import EmptyState from '@/components/EmptyState/EmptyState'

export const AdminAuditPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLogResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)

  // Pagination state
  const [page, setPage] = useState(0)
  const [pageSize] = useState(10)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  // Filter state
  const [selectedEntityType, setSelectedEntityType] = useState<string>('ALL')
  const [selectedLocationId, setSelectedLocationId] = useState<string>('ALL')
  const [selectedAction, setSelectedAction] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Detail Modal state
  const [selectedLogForDetail, setSelectedLogForDetail] = useState<AuditLogResponse | null>(null)

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

  // Authoritative Audit Logs fetcher
  const fetchAuditLogs = useCallback(async () => {
    setLoading(true)
    setFetchError(null)
    try {
      const locationId = selectedLocationId !== 'ALL' ? Number(selectedLocationId) : undefined
      const entityType = selectedEntityType !== 'ALL' ? selectedEntityType : undefined
      const action = selectedAction !== 'ALL' ? selectedAction : undefined

      const response = await auditAdminService.getAuditLogs({
        locationId,
        entityType,
        action,
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      })

      setLogs(response.content || [])
      setTotalPages(response.totalPages || 1)
      setTotalElements(response.totalElements || 0)
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || 'Failed to load audit logs from server.'
      setFetchError(message)
    } finally {
      setLoading(false)
    }
  }, [selectedLocationId, selectedEntityType, selectedAction, page, pageSize])

  useEffect(() => {
    fetchAuditLogs()
  }, [fetchAuditLogs])

  // Filter change handlers
  const handleEntityTypeChange = (val: string) => {
    setSelectedEntityType(val)
    setPage(0)
  }

  const handleLocationChange = (val: string) => {
    setSelectedLocationId(val)
    setPage(0)
  }

  const handleActionChange = (val: string) => {
    setSelectedAction(val)
    setPage(0)
  }

  // Client-side quick filter within loaded page
  const filteredLogs = logs.filter((log) => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return true
    return (
      String(log.id).includes(q) ||
      log.action.toLowerCase().includes(q) ||
      log.entityType.toLowerCase().includes(q) ||
      (log.actorName && log.actorName.toLowerCase().includes(q)) ||
      (log.actorWissenId && log.actorWissenId.toLowerCase().includes(q)) ||
      (log.actorEmail && log.actorEmail.toLowerCase().includes(q)) ||
      (log.affectedUserWissenId && log.affectedUserWissenId.toLowerCase().includes(q)) ||
      (log.affectedUserName && log.affectedUserName.toLowerCase().includes(q)) ||
      (log.entityId && log.entityId.toLowerCase().includes(q))
    )
  })

  // Format date helper
  const formatTimestamp = (isoString: string) => {
    try {
      return new Date(isoString).toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  const getActionBadgeVariant = (action: string) => {
    if (action.includes('CANCEL') || action.includes('DEACTIVATE') || action.includes('DELETE')) {
      return 'error'
    }
    if (action.includes('LOCKED') || action.includes('LOCK')) {
      return 'warning'
    }
    if (action.includes('CREATED') || action.includes('ACTIVE') || action.includes('RESOLVED')) {
      return 'success'
    }
    return 'info'
  }

  // Options
  const locationOptions = [
    { value: 'ALL', label: 'All Locations' },
    ...locations.map((loc) => ({
      value: String(loc.id),
      label: `${loc.name} (${loc.code})`,
    })),
  ]

  const entityTypeOptions = [
    { value: 'ALL', label: 'All Entity Types' },
    { value: 'BOOKING', label: 'Bookings' },
    { value: 'ROOM', label: 'Rooms' },
    { value: 'LOCATION', label: 'Locations' },
    { value: 'ADMIN_REQUEST', label: 'Admin Requests' },
  ]

  const actionOptions = [
    { value: 'ALL', label: 'All Actions' },
    { value: 'BOOKING_CREATED', label: 'Booking Created' },
    { value: 'BOOKING_CANCELLED', label: 'Booking Cancelled' },
    { value: 'BOOKING_RESCHEDULED', label: 'Booking Rescheduled' },
    { value: 'BOOKING_COMPLETED', label: 'Booking Completed' },
    { value: 'ROOM_LOCKED', label: 'Room Locked' },
    { value: 'ROOM_UNLOCKED', label: 'Room Unlocked' },
    { value: 'ROOM_ACTIVATED', label: 'Room Activated' },
    { value: 'ROOM_DEACTIVATED', label: 'Room Deactivated' },
    { value: 'ADMIN_REQUEST_RESOLVED', label: 'Admin Request Resolved' },
  ]

  return (
    <div className="space-y-6">
      {/* Header Breadcrumb & Title */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav className="flex items-center gap-2 text-xs text-brand-slate mb-1">
            <Link to="/admin" className="hover:text-brand-navy">
              Admin
            </Link>
            <span>/</span>
            <span className="text-brand-navy font-semibold">Audit Logs</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">System Audit Logs</h1>
          <p className="text-brand-slate text-sm mt-1">
            Inspect immutable transactional audit trails, security actions, and administrative operations.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={fetchAuditLogs}
            disabled={loading}
            className="flex items-center gap-1.5"
          >
            <svg
              className={`w-4 h-4 text-brand-slate ${loading ? 'animate-spin' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            Refresh
          </Button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Total Audit Events
            </span>
            <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
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
              Booking Events
            </span>
            <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-emerald-700 mt-1">
            {logs.filter((l) => l.entityType === 'BOOKING').length} <span className="text-xs font-normal text-slate-500">on page</span>
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-indigo-600 uppercase tracking-wider">
              Administrative Actions
            </span>
            <span className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-indigo-700 mt-1">
            {logs.filter((l) => l.entityType !== 'BOOKING').length} <span className="text-xs font-normal text-slate-500">on page</span>
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Ledger State
            </span>
            <span className="p-2 rounded-lg bg-slate-100 text-slate-700">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </span>
          </div>
          <div className="text-base font-bold text-brand-navy mt-2">
            Immutable Trail
          </div>
        </div>
      </div>

      {/* Filters Bar */}
      <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs flex flex-col md:flex-row gap-4 items-stretch md:items-center justify-between flex-wrap">
        <div className="flex flex-col sm:flex-row gap-3 flex-1 flex-wrap">
          {/* Search */}
          <div className="relative w-full sm:w-60">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-brand-slate">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <input
              type="text"
              placeholder="Search action, actor, ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-sm border border-brand-slate/30 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-brand-accent focus:border-brand-accent transition-colors"
            />
          </div>

          {/* Entity Type Filter */}
          <div className="w-full sm:w-44">
            <Select
              options={entityTypeOptions}
              value={selectedEntityType}
              onChange={(e) => handleEntityTypeChange(e.target.value)}
            />
          </div>

          {/* Action Filter */}
          <div className="w-full sm:w-48">
            <Select
              options={actionOptions}
              value={selectedAction}
              onChange={(e) => handleActionChange(e.target.value)}
            />
          </div>

          {/* Location Filter */}
          <div className="w-full sm:w-48">
            <Select
              options={locationOptions}
              value={selectedLocationId}
              onChange={(e) => handleLocationChange(e.target.value)}
            />
          </div>
        </div>

        {/* Reset Filters */}
        {(selectedEntityType !== 'ALL' || selectedLocationId !== 'ALL' || selectedAction !== 'ALL' || searchQuery !== '') && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setSelectedEntityType('ALL')
              setSelectedLocationId('ALL')
              setSelectedAction('ALL')
              setSearchQuery('')
              setPage(0)
            }}
          >
            Reset Filters
          </Button>
        )}
      </div>

      {/* Error State Banner */}
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
          <Button variant="outline" size="sm" onClick={fetchAuditLogs}>
            Retry
          </Button>
        </div>
      )}

      {/* Audit Logs Table */}
      <Table
        loading={loading}
        empty={!loading && filteredLogs.length === 0}
        emptyMessage="No audit log records found matching your filter criteria."
        columnCount={5}
      >
        <TableHeader>
          <TableRow>
            <TableHead>Actor</TableHead>
            <TableHead>Action</TableHead>
            <TableHead>Resource</TableHead>
            <TableHead>Timestamp</TableHead>
            <TableHead className="text-right">Details</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {filteredLogs.map((log) => (
            <TableRow key={log.id} className="hover:bg-slate-50/70 transition-colors">
              {/* Actor */}
              <TableCell>
                <div className="font-bold text-brand-navy">
                  {log.actorName || 'SYSTEM'}
                </div>
                {log.actorWissenId ? (
                  <div className="font-mono text-xs font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-brand-navy border border-slate-200 inline-block mt-0.5">
                    {log.actorWissenId}
                  </div>
                ) : log.actorEmail ? (
                  <div className="text-xs text-brand-slate">{log.actorEmail}</div>
                ) : log.actorUserId != null ? (
                  <div className="text-[11px] font-mono text-brand-slate">
                    User #{log.actorUserId}
                  </div>
                ) : null}
              </TableCell>

              {/* Action */}
              <TableCell>
                <Badge variant={getActionBadgeVariant(log.action)} size="sm">
                  {log.action}
                </Badge>
              </TableCell>

              {/* Resource */}
              <TableCell>
                <div className="font-semibold text-brand-navy text-sm">
                  {log.entityType} {log.entityId ? `#${log.entityId}` : ''}
                </div>
                <div className="text-xs text-brand-slate">
                  {log.roomName ? `Room: ${log.roomName}` : log.roomId ? `Room #${log.roomId}` : ''}
                  {log.locationName ? ` • ${log.locationName}` : log.locationId ? ` • Loc #${log.locationId}` : ''}
                </div>
              </TableCell>

              {/* Timestamp */}
              <TableCell>
                <div className="text-xs font-medium text-brand-navy">
                  {formatTimestamp(log.createdAt)}
                </div>
              </TableCell>

              {/* Action Controls */}
              <TableCell className="text-right">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setSelectedLogForDetail(log)}
                  className="text-xs py-1 px-3"
                >
                  View Details
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between p-4 bg-brand-white rounded-xl border border-brand-slate/20 shadow-2xs">
          <span className="text-xs text-brand-slate">
            Showing Page <span className="font-semibold text-brand-navy">{page + 1}</span> of{' '}
            <span className="font-semibold text-brand-navy">{totalPages}</span> ({totalElements} total audit events)
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
      {!loading && filteredLogs.length === 0 && !fetchError && (
        <div className="pt-2">
          <EmptyState
            title="No audit records found"
            description="There are currently no audit records matching your filter parameters."
            action={
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setSelectedEntityType('ALL')
                  setSelectedLocationId('ALL')
                  setSelectedAction('ALL')
                  setSearchQuery('')
                  setPage(0)
                }}
              >
                Reset Filters
              </Button>
            }
          />
        </div>
      )}

      {/* Detail Modal */}
      <AuditLogDetailModal
        isOpen={Boolean(selectedLogForDetail)}
        onClose={() => setSelectedLogForDetail(null)}
        auditLog={selectedLogForDetail}
      />
    </div>
  )
}

export default AdminAuditPage
