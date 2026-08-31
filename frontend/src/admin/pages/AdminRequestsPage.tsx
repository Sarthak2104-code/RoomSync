import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { AdminRequestResponse, AdminRequestStatus } from '@/types/adminRequest'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/api/client'
import { adminRequestService } from '../api/adminRequestService'
import { locationAdminService } from '../api/locationAdminService'
import AdminRequestDetailModal from '../requests/AdminRequestDetailModal'
import Table, { TableBody, TableCell, TableHeader, TableHead, TableRow } from '@/components/Table/Table'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'
import Select from '@/components/Select/Select'
import EmptyState from '@/components/EmptyState/EmptyState'

export const AdminRequestsPage: React.FC = () => {
  const [requests, setRequests] = useState<AdminRequestResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)

  // Pagination state
  const [page, setPage] = useState(0)
  const [pageSize] = useState(10)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  // Filter state
  const [selectedLocationId, setSelectedLocationId] = useState<string>('ALL')
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Detail Modal state
  const [selectedRequestForDetail, setSelectedRequestForDetail] = useState<AdminRequestResponse | null>(null)

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

  // Authoritative Requests fetcher
  const fetchRequests = useCallback(async () => {
    setLoading(true)
    setFetchError(null)
    try {
      const locationId = selectedLocationId !== 'ALL' ? Number(selectedLocationId) : undefined
      const status = selectedStatus !== 'ALL' ? (selectedStatus as AdminRequestStatus) : undefined

      const response = await adminRequestService.getAdminRequests({
        locationId,
        status,
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      })

      setRequests(response.content || [])
      setTotalPages(response.totalPages || 1)
      setTotalElements(response.totalElements || 0)
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || 'Failed to load administrative requests from server.'
      setFetchError(message)
    } finally {
      setLoading(false)
    }
  }, [selectedLocationId, selectedStatus, page, pageSize])

  useEffect(() => {
    fetchRequests()
  }, [fetchRequests])

  // Filter handlers
  const handleLocationChange = (val: string) => {
    setSelectedLocationId(val)
    setPage(0)
  }

  const handleStatusChange = (val: string) => {
    setSelectedStatus(val)
    setPage(0)
  }

  const handleRequestUpdated = () => {
    fetchRequests()
  }

  // Client-side search filtering within the current page
  const filteredRequests = requests.filter((r) => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return true
    return (
      String(r.id).includes(q) ||
      r.requestType.toLowerCase().includes(q) ||
      (r.message && r.message.toLowerCase().includes(q)) ||
      (r.requesterUserWissenId && r.requesterUserWissenId.toLowerCase().includes(q)) ||
      (r.requesterUserName && r.requesterUserName.toLowerCase().includes(q)) ||
      String(r.requesterUserId).includes(q)
    )
  })

  // Format date helper
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

  // Location filter options
  const locationOptions = [
    { value: 'ALL', label: 'All Locations' },
    ...locations.map((loc) => ({
      value: String(loc.id),
      label: `${loc.name} (${loc.code})`,
    })),
  ]

  const statusOptions = [
    { value: 'ALL', label: 'All Statuses' },
    { value: 'OPEN', label: 'Open' },
    { value: 'IN_PROGRESS', label: 'In Progress' },
    { value: 'RESOLVED', label: 'Resolved' },
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
            <span className="text-brand-navy font-semibold">Admin Requests</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Admin Requests & Escalations</h1>
          <p className="text-brand-slate text-sm mt-1">
            Review and resolve user booking escalations, resource access tickets, and policy exceptions.
          </p>
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Total Requests
            </span>
            <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-brand-navy mt-1">
            {loading ? <span className="text-slate-300">...</span> : totalElements}
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-indigo-600 uppercase tracking-wider">
              Open Requests
            </span>
            <span className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-indigo-700 mt-1">
            {requests.filter((r) => r.status === 'OPEN').length} <span className="text-xs font-normal text-slate-500">on page</span>
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-amber-600 uppercase tracking-wider">
              In Progress
            </span>
            <span className="p-2 rounded-lg bg-amber-50 text-amber-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-amber-700 mt-1">
            {requests.filter((r) => r.status === 'IN_PROGRESS').length} <span className="text-xs font-normal text-slate-500">on page</span>
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-emerald-600 uppercase tracking-wider">
              Resolved
            </span>
            <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-emerald-700 mt-1">
            {requests.filter((r) => r.status === 'RESOLVED').length} <span className="text-xs font-normal text-slate-500">on page</span>
          </div>
        </div>
      </div>

      {/* Filters Bar */}
      <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs flex flex-col md:flex-row gap-4 items-stretch md:items-center justify-between flex-wrap">
        <div className="flex flex-col sm:flex-row gap-3 flex-1 flex-wrap">
          {/* Search */}
          <div className="relative w-full sm:w-64">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-brand-slate">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <input
              type="text"
              placeholder="Search type, msg, ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-sm border border-brand-slate/30 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-brand-accent focus:border-brand-accent transition-colors"
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

          {/* Status Filter */}
          <div className="w-full sm:w-44">
            <Select
              options={statusOptions}
              value={selectedStatus}
              onChange={(e) => handleStatusChange(e.target.value)}
            />
          </div>
        </div>

        {/* Reset Filter Button */}
        {(selectedLocationId !== 'ALL' || selectedStatus !== 'ALL' || searchQuery !== '') && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setSelectedLocationId('ALL')
              setSelectedStatus('ALL')
              setSearchQuery('')
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
          <Button variant="outline" size="sm" onClick={fetchRequests}>
            Retry
          </Button>
        </div>
      )}

      {/* Main Requests Table */}
      <Table
        loading={loading}
        empty={!loading && filteredRequests.length === 0}
        emptyMessage="No administrative requests found matching your filter selection."
        columnCount={7}
      >
        <TableHeader>
          <TableRow>
            <TableHead>Request</TableHead>
            <TableHead>Requester / Wissen ID</TableHead>
            <TableHead>Location</TableHead>
            <TableHead>Associated Resource</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {filteredRequests.map((req) => (
            <TableRow key={req.id} className="hover:bg-slate-50/70 transition-colors">
              {/* Request ID & Type */}
              <TableCell>
                <div className="font-bold text-brand-navy">{req.requestType}</div>
                <div className="text-xs font-mono text-brand-slate">#{req.id}</div>
              </TableCell>

              {/* Requester User */}
              <TableCell>
                {req.requesterUserWissenId ? (
                  <div>
                    <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-brand-navy border border-slate-200 inline-block">
                      {req.requesterUserWissenId}
                    </span>
                    {req.requesterUserName && (
                      <div className="text-xs text-brand-slate mt-0.5 font-medium">
                        {req.requesterUserName}
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="font-semibold text-brand-navy text-sm">
                    {req.requesterUserName || `User #${req.requesterUserId}`}
                  </div>
                )}
              </TableCell>

              {/* Location */}
              <TableCell>
                <div className="text-sm font-medium text-brand-navy">
                  Location #{req.locationId}
                </div>
              </TableCell>

              {/* Associated Resource */}
              <TableCell>
                {req.roomId != null ? (
                  <div className="text-xs text-brand-navy font-medium">Room #{req.roomId}</div>
                ) : req.bookingId != null ? (
                  <div className="text-xs text-brand-navy font-medium">Booking #{req.bookingId}</div>
                ) : req.bookingSeriesId != null ? (
                  <div className="text-xs text-blue-600 font-medium">Series #{req.bookingSeriesId}</div>
                ) : (
                  <span className="text-xs text-brand-slate italic">General</span>
                )}
              </TableCell>

              {/* Status Badge */}
              <TableCell>
                {req.status === 'OPEN' && (
                  <Badge variant="info" size="sm" className="font-semibold">
                    OPEN
                  </Badge>
                )}
                {req.status === 'IN_PROGRESS' && (
                  <Badge variant="warning" size="sm" className="font-semibold">
                    IN PROGRESS
                  </Badge>
                )}
                {req.status === 'RESOLVED' && (
                  <Badge variant="success" size="sm" className="font-semibold">
                    RESOLVED
                  </Badge>
                )}
                {req.status === 'CANCELLED' && (
                  <Badge variant="neutral" size="sm" className="font-semibold bg-red-50 text-red-700 border-red-200">
                    CANCELLED
                  </Badge>
                )}
              </TableCell>

              {/* Created Date */}
              <TableCell>
                <div className="text-xs font-medium text-brand-navy">
                  {formatDate(req.createdAt)}
                </div>
              </TableCell>

              {/* Actions */}
              <TableCell className="text-right">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setSelectedRequestForDetail(req)}
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
            <span className="font-semibold text-brand-navy">{totalPages}</span> ({totalElements} total requests)
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
      {!loading && filteredRequests.length === 0 && !fetchError && (
        <div className="pt-2">
          <EmptyState
            title="No administrative requests found"
            description="No requests match your current location or status filter."
            action={
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setSelectedLocationId('ALL')
                  setSelectedStatus('ALL')
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

      {/* Request Detail & Resolution Modal */}
      <AdminRequestDetailModal
        isOpen={Boolean(selectedRequestForDetail)}
        onClose={() => setSelectedRequestForDetail(null)}
        request={selectedRequestForDetail}
        onSuccess={handleRequestUpdated}
      />
    </div>
  )
}

export default AdminRequestsPage
