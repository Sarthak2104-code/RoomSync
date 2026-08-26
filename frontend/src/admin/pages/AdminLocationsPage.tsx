import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/api/client'
import { locationAdminService } from '../api/locationAdminService'
import LocationFormModal from '../locations/LocationFormModal'
import Table, { TableBody, TableCell, TableHeader, TableHead, TableRow } from '@/components/Table/Table'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'
import ConfirmDialog from '@/components/ConfirmDialog/ConfirmDialog'
import EmptyState from '@/components/EmptyState/EmptyState'

export const AdminLocationsPage: React.FC = () => {
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL')

  // Modal & Dialog state
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [selectedLocation, setSelectedLocation] = useState<LocationResponse | null>(null)

  // Activation / Deactivation state
  const [activatingId, setActivatingId] = useState<number | null>(null)
  const [deactivatingLocation, setDeactivatingLocation] = useState<LocationResponse | null>(null)
  const [isDeactivating, setIsDeactivating] = useState(false)

  // Authoritative data fetcher
  const fetchLocations = useCallback(async () => {
    setLoading(true)
    setFetchError(null)
    try {
      const response = await locationAdminService.getLocations({
        page: 0,
        size: 100,
        sort: 'name,asc',
      })
      setLocations(response.content || [])
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || 'Failed to load locations from server.'
      setFetchError(message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchLocations()
  }, [fetchLocations])

  // Filtered list based on search and status
  const filteredLocations = useMemo(() => {
    return locations.filter((loc) => {
      const matchesSearch =
        searchQuery.trim() === '' ||
        loc.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        loc.code.toLowerCase().includes(searchQuery.toLowerCase())

      const matchesStatus =
        statusFilter === 'ALL' ||
        (statusFilter === 'ACTIVE' && loc.active) ||
        (statusFilter === 'INACTIVE' && !loc.active)

      return matchesSearch && matchesStatus
    })
  }, [locations, searchQuery, statusFilter])

  // Handlers for Form Modal
  const handleOpenCreate = () => {
    setSelectedLocation(null)
    setIsFormOpen(true)
  }

  const handleOpenEdit = (location: LocationResponse) => {
    setSelectedLocation(location)
    setIsFormOpen(true)
  }

  const handleFormSuccess = () => {
    fetchLocations()
  }

  // Handler for Activation
  const handleActivate = async (location: LocationResponse) => {
    setActivatingId(location.id)
    try {
      const updated = await locationAdminService.activateLocation(location.id)
      toast.success(`Location "${updated.name}" activated successfully.`)
      // Refresh with backend confirmed data
      await fetchLocations()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || `Failed to activate location "${location.name}".`
      toast.error(message)
    } finally {
      setActivatingId(null)
    }
  }

  // Handlers for Deactivation Confirmation Dialog
  const handlePromptDeactivate = (location: LocationResponse) => {
    setDeactivatingLocation(location)
  }

  const handleConfirmDeactivate = async () => {
    if (!deactivatingLocation) return

    setIsDeactivating(true)
    try {
      await locationAdminService.deactivateLocation(deactivatingLocation.id)
      toast.success(`Location "${deactivatingLocation.name}" deactivated successfully.`)
      setDeactivatingLocation(null)
      // Refresh with backend confirmed data
      await fetchLocations()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || `Failed to deactivate location "${deactivatingLocation.name}".`
      toast.error(message)
    } finally {
      setIsDeactivating(false)
    }
  }

  // Summary counts
  const totalCount = locations.length
  const activeCount = locations.filter((l) => l.active).length
  const inactiveCount = totalCount - activeCount

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
            <span className="text-brand-navy font-semibold">Locations</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Location Management</h1>
          <p className="text-brand-slate text-sm mt-1">
            Create, view, edit, activate, and deactivate physical office locations across the enterprise.
          </p>
        </div>

        <Button
          variant="primary"
          onClick={handleOpenCreate}
          className="flex items-center gap-2 self-start sm:self-auto shadow-xs"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add Location
        </Button>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Total Locations
            </span>
            <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
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
              Active Locations
            </span>
            <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-emerald-700 mt-1">
            {loading ? <span className="text-slate-300">...</span> : activeCount}
          </div>
        </div>

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              Inactive Locations
            </span>
            <span className="p-2 rounded-lg bg-slate-100 text-slate-600">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
              </svg>
            </span>
          </div>
          <div className="text-2xl font-bold text-slate-700 mt-1">
            {loading ? <span className="text-slate-300">...</span> : inactiveCount}
          </div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs flex flex-col md:flex-row gap-4 items-stretch md:items-center justify-between">
        {/* Search Input */}
        <div className="relative flex-1 max-w-md">
          <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-brand-slate">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
          <input
            type="text"
            placeholder="Search by name or code (e.g. Mumbai, MUM)..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2 text-sm border border-brand-slate/30 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-brand-accent focus:border-brand-accent transition-colors"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
              className="absolute inset-y-0 right-0 pr-3 flex items-center text-xs text-brand-slate hover:text-brand-navy"
            >
              Clear
            </button>
          )}
        </div>

        {/* Status Filter Tabs */}
        <div className="flex items-center rounded-lg bg-slate-100 p-1 self-start md:self-auto">
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
            onClick={() => setStatusFilter('ACTIVE')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              statusFilter === 'ACTIVE'
                ? 'bg-brand-white text-emerald-700 shadow-xs'
                : 'text-brand-slate hover:text-brand-navy'
            }`}
          >
            Active ({activeCount})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter('INACTIVE')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              statusFilter === 'INACTIVE'
                ? 'bg-brand-white text-slate-700 shadow-xs'
                : 'text-brand-slate hover:text-brand-navy'
            }`}
          >
            Inactive ({inactiveCount})
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
          <Button variant="outline" size="sm" onClick={fetchLocations}>
            Retry
          </Button>
        </div>
      )}

      {/* Main Data Table View */}
      <Table
        loading={loading}
        empty={!loading && filteredLocations.length === 0}
        emptyMessage={
          searchQuery || statusFilter !== 'ALL'
            ? 'No locations match your current filter.'
            : 'No locations configured yet.'
        }
        columnCount={5}
      >
        <TableHeader>
          <TableRow>
            <TableHead>Code</TableHead>
            <TableHead>Location Name</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {filteredLocations.map((location) => {
            const isActivatingThis = activatingId === location.id
            return (
              <TableRow key={location.id} className="hover:bg-slate-50/70 transition-colors">
                {/* Location Code */}
                <TableCell>
                  <span className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-mono font-bold bg-slate-100 text-brand-navy border border-slate-200">
                    {location.code}
                  </span>
                </TableCell>

                {/* Location Name */}
                <TableCell>
                  <div className="font-semibold text-brand-navy">{location.name}</div>
                  <div className="text-xs text-brand-slate">ID: #{location.id}</div>
                </TableCell>

                {/* Active/Inactive Status */}
                <TableCell>
                  {location.active ? (
                    <Badge variant="success" size="sm" className="font-semibold">
                      Active
                    </Badge>
                  ) : (
                    <Badge variant="neutral" size="sm" className="font-semibold text-slate-600 bg-slate-100">
                      Inactive
                    </Badge>
                  )}
                </TableCell>

                {/* Created Date */}
                <TableCell className="text-xs text-brand-slate">
                  {location.createdAt
                    ? new Date(location.createdAt).toLocaleDateString('en-US', {
                        month: 'short',
                        day: 'numeric',
                        year: 'numeric',
                      })
                    : '—'}
                </TableCell>

                {/* Row Action Controls */}
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-2">
                    {/* Edit Button */}
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleOpenEdit(location)}
                      className="text-xs py-1 px-2.5"
                    >
                      Edit
                    </Button>

                    {/* Activate / Deactivate Toggle Button */}
                    {location.active ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handlePromptDeactivate(location)}
                        className="text-xs py-1 px-2.5 text-red-600 hover:text-red-700 hover:bg-red-50 border-red-200"
                      >
                        Deactivate
                      </Button>
                    ) : (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => handleActivate(location)}
                        loading={isActivatingThis}
                        disabled={isActivatingThis}
                        className="text-xs py-1 px-2.5 bg-emerald-600 hover:bg-emerald-700"
                      >
                        Activate
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>

      {/* Empty State Action when no locations found */}
      {!loading && filteredLocations.length === 0 && !fetchError && (
        <div className="pt-2">
          {searchQuery || statusFilter !== 'ALL' ? (
            <EmptyState
              title="No locations found"
              description="No office locations match your search criteria or active filter."
              action={
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setSearchQuery('')
                    setStatusFilter('ALL')
                  }}
                >
                  Reset Filters
                </Button>
              }
            />
          ) : (
            <EmptyState
              title="No locations configured"
              description="Get started by creating your organization's first office or campus location."
              action={
                <Button variant="primary" size="sm" onClick={handleOpenCreate}>
                  Add First Location
                </Button>
              }
            />
          )}
        </div>
      )}

      {/* Location Create / Edit Form Modal */}
      <LocationFormModal
        isOpen={isFormOpen}
        onClose={() => setIsFormOpen(false)}
        onSuccess={handleFormSuccess}
        initialData={selectedLocation}
      />

      {/* Deactivation Confirmation Dialog */}
      <ConfirmDialog
        isOpen={Boolean(deactivatingLocation)}
        title="Deactivate Location"
        description={`Are you sure you want to deactivate "${deactivatingLocation?.name}" (${deactivatingLocation?.code})? Inactive locations cannot accept new room bookings.`}
        confirmLabel="Deactivate Location"
        cancelLabel="Keep Active"
        variant="danger"
        loading={isDeactivating}
        onConfirm={handleConfirmDeactivate}
        onCancel={() => setDeactivatingLocation(null)}
      />
    </div>
  )
}

export default AdminLocationsPage
