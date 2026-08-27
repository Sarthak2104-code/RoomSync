import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import type { RoomResponse } from '@/types/room'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/api/client'
import { roomAdminService } from '../api/roomAdminService'
import { locationAdminService } from '../api/locationAdminService'
import RoomFormModal from '../rooms/RoomFormModal'
import Table, { TableBody, TableCell, TableHeader, TableHead, TableRow } from '@/components/Table/Table'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'
import ConfirmDialog from '@/components/ConfirmDialog/ConfirmDialog'
import EmptyState from '@/components/EmptyState/EmptyState'
import Select from '@/components/Select/Select'

export const AdminRoomsPage: React.FC = () => {
  const [rooms, setRooms] = useState<RoomResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedLocationFilter, setSelectedLocationFilter] = useState<string>('ALL')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'AVAILABLE' | 'LOCKED'>('ALL')
  const [activeFilter, setActiveFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL')

  // Modal state
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [selectedRoom, setSelectedRoom] = useState<RoomResponse | null>(null)

  // Action states & Dialogs
  const [lockTargetRoom, setLockTargetRoom] = useState<RoomResponse | null>(null)
  const [isLocking, setIsLocking] = useState(false)

  const [deactivateTargetRoom, setDeactivateTargetRoom] = useState<RoomResponse | null>(null)
  const [isDeactivating, setIsDeactivating] = useState(false)

  const [togglingActionId, setTogglingActionId] = useState<number | null>(null)

  // Authoritative data fetcher
  const fetchData = useCallback(async () => {
    setLoading(true)
    setFetchError(null)
    try {
      const [roomsRes, locationsRes] = await Promise.all([
        roomAdminService.getRooms({
          page: 0,
          size: 200,
          sort: 'name,asc',
        }),
        locationAdminService.getLocations({
          page: 0,
          size: 100,
          sort: 'name,asc',
        }),
      ])
      setRooms(roomsRes.content || [])
      setLocations(locationsRes.content || [])
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || 'Failed to load rooms from server.'
      setFetchError(message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  // Filtered rooms list
  const filteredRooms = useMemo(() => {
    return rooms.filter((room) => {
      // 1. Search Query
      const q = searchQuery.toLowerCase().trim()
      const matchesSearch =
        q === '' ||
        room.name.toLowerCase().includes(q) ||
        (room.location?.name && room.location.name.toLowerCase().includes(q)) ||
        (room.location?.code && room.location.code.toLowerCase().includes(q))

      // 2. Location filter
      const matchesLocation =
        selectedLocationFilter === 'ALL' ||
        String(room.location?.id) === selectedLocationFilter

      // 3. Administrative Status filter (AVAILABLE / LOCKED)
      const matchesStatus =
        statusFilter === 'ALL' || room.status === statusFilter

      // 4. Active filter
      const matchesActive =
        activeFilter === 'ALL' ||
        (activeFilter === 'ACTIVE' && room.active) ||
        (activeFilter === 'INACTIVE' && !room.active)

      return matchesSearch && matchesLocation && matchesStatus && matchesActive
    })
  }, [rooms, searchQuery, selectedLocationFilter, statusFilter, activeFilter])

  // Handlers for Form Modal
  const handleOpenCreate = () => {
    setSelectedRoom(null)
    setIsFormOpen(true)
  }

  const handleOpenEdit = (room: RoomResponse) => {
    setSelectedRoom(room)
    setIsFormOpen(true)
  }

  const handleFormSuccess = () => {
    fetchData()
  }

  // Handlers for Lock action
  const handlePromptLock = (room: RoomResponse) => {
    setLockTargetRoom(room)
  }

  const handleConfirmLock = async () => {
    if (!lockTargetRoom) return

    setIsLocking(true)
    try {
      const updated = await roomAdminService.lockRoom(lockTargetRoom.id)
      toast.success(`Room "${updated.name}" locked successfully.`)
      setLockTargetRoom(null)
      await fetchData()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || `Failed to lock room "${lockTargetRoom.name}".`
      toast.error(message)
    } finally {
      setIsLocking(false)
    }
  }

  // Handler for Unlock action
  const handleUnlock = async (room: RoomResponse) => {
    setTogglingActionId(room.id)
    try {
      const updated = await roomAdminService.unlockRoom(room.id)
      toast.success(`Room "${updated.name}" unlocked successfully.`)
      await fetchData()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || `Failed to unlock room "${room.name}".`
      toast.error(message)
    } finally {
      setTogglingActionId(null)
    }
  }

  // Handler for Activate action
  const handleActivate = async (room: RoomResponse) => {
    setTogglingActionId(room.id)
    try {
      const updated = await roomAdminService.activateRoom(room.id)
      toast.success(`Room "${updated.name}" activated successfully.`)
      await fetchData()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || `Failed to activate room "${room.name}".`
      toast.error(message)
    } finally {
      setTogglingActionId(null)
    }
  }

  // Handlers for Deactivate action
  const handlePromptDeactivate = (room: RoomResponse) => {
    setDeactivateTargetRoom(room)
  }

  const handleConfirmDeactivate = async () => {
    if (!deactivateTargetRoom) return

    setIsDeactivating(true)
    try {
      await roomAdminService.deactivateRoom(deactivateTargetRoom.id)
      toast.success(`Room "${deactivateTargetRoom.name}" deactivated successfully.`)
      setDeactivateTargetRoom(null)
      await fetchData()
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message = apiErr.message || `Failed to deactivate room "${deactivateTargetRoom.name}".`
      toast.error(message)
    } finally {
      setIsDeactivating(false)
    }
  }

  // Summary counts
  const totalCount = rooms.length
  const availableCount = rooms.filter((r) => r.status === 'AVAILABLE' && r.active).length
  const lockedCount = rooms.filter((r) => r.status === 'LOCKED').length
  const inactiveCount = rooms.filter((r) => !r.active).length

  // Location filter options
  const locationFilterOptions = [
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
            <span className="text-brand-navy font-semibold">Rooms</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Room Management</h1>
          <p className="text-brand-slate text-sm mt-1">
            Configure meeting rooms, capacity, status, amenities, and room availability.
          </p>
        </div>

        <Button
          variant="primary"
          onClick={handleOpenCreate}
          disabled={locations.length === 0}
          className="flex items-center gap-2 self-start sm:self-auto shadow-xs"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add Room
        </Button>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Total Rooms
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
              Available (Admin)
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
            <span className="text-xs font-semibold text-amber-600 uppercase tracking-wider">
              Locked (Admin)
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

        <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              Inactive
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

      {/* Filters and Search Bar */}
      <div className="bg-brand-white p-4 rounded-xl border border-brand-slate/20 shadow-2xs flex flex-col lg:flex-row gap-4 items-stretch lg:items-center justify-between">
        {/* Search & Location Filter */}
        <div className="flex flex-col sm:flex-row gap-3 flex-1">
          <div className="relative flex-1 max-w-sm">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-brand-slate">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <input
              type="text"
              placeholder="Search rooms..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-sm border border-brand-slate/30 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-brand-accent focus:border-brand-accent transition-colors"
            />
          </div>

          <div className="w-full sm:w-56">
            <Select
              options={locationFilterOptions}
              value={selectedLocationFilter}
              onChange={(e) => setSelectedLocationFilter(e.target.value)}
            />
          </div>
        </div>

        {/* Status Filters */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Admin State Filter */}
          <div className="flex items-center rounded-lg bg-slate-100 p-1">
            <button
              type="button"
              onClick={() => setStatusFilter('ALL')}
              className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                statusFilter === 'ALL'
                  ? 'bg-brand-white text-brand-navy shadow-xs'
                  : 'text-brand-slate hover:text-brand-navy'
              }`}
            >
              All States
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
              Available
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
              Locked
            </button>
          </div>

          {/* Active / Inactive Filter */}
          <div className="flex items-center rounded-lg bg-slate-100 p-1">
            <button
              type="button"
              onClick={() => setActiveFilter('ALL')}
              className={`px-2.5 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeFilter === 'ALL'
                  ? 'bg-brand-white text-brand-navy shadow-xs'
                  : 'text-brand-slate hover:text-brand-navy'
              }`}
            >
              All
            </button>
            <button
              type="button"
              onClick={() => setActiveFilter('ACTIVE')}
              className={`px-2.5 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeFilter === 'ACTIVE'
                  ? 'bg-brand-white text-emerald-700 shadow-xs'
                  : 'text-brand-slate hover:text-brand-navy'
              }`}
            >
              Active
            </button>
            <button
              type="button"
              onClick={() => setActiveFilter('INACTIVE')}
              className={`px-2.5 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeFilter === 'INACTIVE'
                  ? 'bg-brand-white text-slate-700 shadow-xs'
                  : 'text-brand-slate hover:text-brand-navy'
              }`}
            >
              Inactive
            </button>
          </div>
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
          <Button variant="outline" size="sm" onClick={fetchData}>
            Retry
          </Button>
        </div>
      )}

      {/* Main Data Table View */}
      <Table
        loading={loading}
        empty={!loading && filteredRooms.length === 0}
        emptyMessage={
          searchQuery || selectedLocationFilter !== 'ALL' || statusFilter !== 'ALL' || activeFilter !== 'ALL'
            ? 'No rooms match your filter criteria.'
            : 'No meeting rooms configured yet.'
        }
        columnCount={6}
      >
        <TableHeader>
          <TableRow>
            <TableHead>Room</TableHead>
            <TableHead>Location</TableHead>
            <TableHead>Capacity</TableHead>
            <TableHead>Admin State</TableHead>
            <TableHead>Active</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {filteredRooms.map((room) => {
            const isTogglingThis = togglingActionId === room.id
            return (
              <TableRow key={room.id} className="hover:bg-slate-50/70 transition-colors">
                {/* Room Name & Description */}
                <TableCell>
                  <div className="font-bold text-brand-navy">{room.name}</div>
                  {room.description && (
                    <div className="text-xs text-brand-slate line-clamp-1 max-w-xs">{room.description}</div>
                  )}
                  <div className="text-[11px] text-slate-400 font-mono">ID: #{room.id}</div>
                </TableCell>

                {/* Location */}
                <TableCell>
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-brand-navy text-sm">
                      {room.location?.name || '—'}
                    </span>
                    {room.location?.code && (
                      <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-mono font-bold bg-slate-100 text-slate-700">
                        {room.location.code}
                      </span>
                    )}
                  </div>
                </TableCell>

                {/* Capacity */}
                <TableCell>
                  <div className="flex items-center gap-1.5 text-sm text-brand-navy font-medium">
                    <svg className="w-4 h-4 text-brand-slate" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                    </svg>
                    <span>{room.capacity} seats</span>
                  </div>
                </TableCell>

                {/* Administrative State: AVAILABLE / LOCKED */}
                <TableCell>
                  {room.status === 'LOCKED' ? (
                    <Badge variant="warning" size="sm" className="font-semibold flex items-center gap-1 w-fit">
                      <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                      </svg>
                      LOCKED
                    </Badge>
                  ) : (
                    <Badge variant="success" size="sm" className="font-semibold flex items-center gap-1 w-fit">
                      <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                      AVAILABLE
                    </Badge>
                  )}
                </TableCell>

                {/* Active / Inactive Status */}
                <TableCell>
                  {room.active ? (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-700">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                      Active
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold text-slate-500">
                      <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
                      Inactive
                    </span>
                  )}
                </TableCell>

                {/* Action Controls */}
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1.5 flex-wrap">
                    {/* Edit Room */}
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleOpenEdit(room)}
                      className="text-xs py-1 px-2.5"
                    >
                      Edit
                    </Button>

                    {/* Lock / Unlock Toggle */}
                    {room.status === 'LOCKED' ? (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => handleUnlock(room)}
                        loading={isTogglingThis}
                        disabled={isTogglingThis}
                        className="text-xs py-1 px-2.5 bg-blue-600 hover:bg-blue-700"
                        title="Unlock room to AVAILABLE"
                      >
                        Unlock
                      </Button>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handlePromptLock(room)}
                        disabled={isTogglingThis}
                        className="text-xs py-1 px-2.5 text-amber-700 hover:text-amber-800 hover:bg-amber-50 border-amber-300"
                        title="Administratively lock room"
                      >
                        Lock
                      </Button>
                    )}

                    {/* Activate / Deactivate Toggle */}
                    {room.active ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handlePromptDeactivate(room)}
                        className="text-xs py-1 px-2.5 text-red-600 hover:text-red-700 hover:bg-red-50 border-red-200"
                      >
                        Deactivate
                      </Button>
                    ) : (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => handleActivate(room)}
                        loading={isTogglingThis}
                        disabled={isTogglingThis}
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

      {/* Empty State Action */}
      {!loading && filteredRooms.length === 0 && !fetchError && (
        <div className="pt-2">
          {searchQuery || selectedLocationFilter !== 'ALL' || statusFilter !== 'ALL' || activeFilter !== 'ALL' ? (
            <EmptyState
              title="No rooms found"
              description="No meeting rooms match your search query or filter selections."
              action={
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setSearchQuery('')
                    setSelectedLocationFilter('ALL')
                    setStatusFilter('ALL')
                    setActiveFilter('ALL')
                  }}
                >
                  Reset Filters
                </Button>
              }
            />
          ) : (
            <EmptyState
              title="No rooms configured"
              description="Get started by configuring your first physical meeting room."
              action={
                <Button variant="primary" size="sm" onClick={handleOpenCreate} disabled={locations.length === 0}>
                  Add First Room
                </Button>
              }
            />
          )}
        </div>
      )}

      {/* Room Create / Edit Form Modal */}
      <RoomFormModal
        isOpen={isFormOpen}
        onClose={() => setIsFormOpen(false)}
        onSuccess={handleFormSuccess}
        initialData={selectedRoom}
        locations={locations}
      />

      {/* Lock Confirmation Dialog */}
      <ConfirmDialog
        isOpen={Boolean(lockTargetRoom)}
        title="Lock Meeting Room"
        description={`Are you sure you want to administratively lock "${lockTargetRoom?.name}" in ${lockTargetRoom?.location?.name}? Locked rooms cannot accept new bookings.`}
        confirmLabel="Lock Room"
        cancelLabel="Cancel"
        variant="primary"
        loading={isLocking}
        onConfirm={handleConfirmLock}
        onCancel={() => setLockTargetRoom(null)}
      />

      {/* Deactivate Confirmation Dialog */}
      <ConfirmDialog
        isOpen={Boolean(deactivateTargetRoom)}
        title="Deactivate Meeting Room"
        description={`Are you sure you want to deactivate "${deactivateTargetRoom?.name}" in ${deactivateTargetRoom?.location?.name}? Inactive rooms will not be visible for scheduling.`}
        confirmLabel="Deactivate Room"
        cancelLabel="Keep Active"
        variant="danger"
        loading={isDeactivating}
        onConfirm={handleConfirmDeactivate}
        onCancel={() => setDeactivateTargetRoom(null)}
      />
    </div>
  )
}

export default AdminRoomsPage
