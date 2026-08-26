import React, { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { roomService } from '@/api/roomService'
import { locationService } from '@/api/locationService'
import { amenityService } from '@/api/amenityService'
import type { RoomResponse, AmenityResponse } from '@/types/room'
import type { LocationResponse } from '@/types/location'
import type { BackendError } from '@/types/api'
import {
  Badge,
  Button,
  DatePicker,
  EmptyState,
  ErrorState,
  Input,
  Loading,
  Pagination,
  Select,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TimePicker,
} from '@/components'

export const RoomSearchPage: React.FC = () => {
  // Metadata options
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [amenities, setAmenities] = useState<AmenityResponse[]>([])

  // Filter state
  const [selectedLocationId, setSelectedLocationId] = useState<string>('')
  const [minCapacity, setMinCapacity] = useState<string>('')
  const [selectedAmenityIds, setSelectedAmenityIds] = useState<number[]>([])
  const [searchDate, setSearchDate] = useState<string>('')
  const [startTime, setStartTime] = useState<string>('')
  const [endTime, setEndTime] = useState<string>('')

  // Search results state
  const [rooms, setRooms] = useState<RoomResponse[]>([])
  const [page, setPage] = useState<number>(0)
  const [totalPages, setTotalPages] = useState<number>(0)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<BackendError | null>(null)

  // Load locations and amenities metadata on mount
  useEffect(() => {
    async function loadMetadata() {
      try {
        const [locRes, amenRes] = await Promise.all([
          locationService.getLocations({ page: 0, size: 100 }),
          amenityService.getAmenities({ page: 0, size: 100 }),
        ])
        setLocations(locRes.content)
        setAmenities(amenRes.content)
      } catch (err: unknown) {
        console.error('Failed to load filter options:', err)
      }
    }
    loadMetadata()
  }, [])

  const executeSearch = useCallback(async (pageNumber = 0) => {
    setIsLoading(true)
    setError(null)
    try {
      const locId = selectedLocationId ? Number(selectedLocationId) : undefined
      const data = await roomService.getRooms({
        locationId: locId,
        page: pageNumber,
        size: 10,
      })

      let filtered = data.content

      // Client-side minimum capacity filter
      if (minCapacity && Number(minCapacity) > 0) {
        const cap = Number(minCapacity)
        filtered = filtered.filter((r) => r.capacity >= cap)
      }

      // Client-side amenities filter (AND semantics)
      if (selectedAmenityIds.length > 0) {
        filtered = filtered.filter((room) => {
          if (!room.amenities || room.amenities.length === 0) return false
          const roomAmenityIds = room.amenities.map((a) => a.id)
          return selectedAmenityIds.every((id) => roomAmenityIds.includes(id))
        })
      }

      setRooms(filtered)
      setPage(data.page)
      setTotalPages(data.totalPages)
    } catch (err: unknown) {
      setError(err as BackendError)
    } finally {
      setIsLoading(false)
    }
  }, [selectedLocationId, minCapacity, selectedAmenityIds])

  useEffect(() => {
    executeSearch(page)
  }, [executeSearch, page])

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setPage(0)
    executeSearch(0)
  }

  const handleResetFilters = () => {
    setSelectedLocationId('')
    setMinCapacity('')
    setSelectedAmenityIds([])
    setSearchDate('')
    setStartTime('')
    setEndTime('')
    setPage(0)
  }

  const toggleAmenity = (id: number) => {
    setSelectedAmenityIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    )
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-brand-navy">Conference & Meeting Rooms</h1>
        <p className="text-sm text-brand-slate mt-1">
          Search real-time room availability across active company sites.
        </p>
      </div>

      {/* Filter Card */}
      <form
        onSubmit={handleSearchSubmit}
        className="bg-brand-white p-6 rounded-lg border border-brand-slate/20 shadow-xs space-y-5"
      >
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <Select
            label="Location"
            value={selectedLocationId}
            onChange={(e) => setSelectedLocationId(e.target.value)}
            options={[
              { value: '', label: 'All Active Locations' },
              ...locations.map((loc) => ({
                value: loc.id,
                label: `${loc.name} (${loc.code})`,
              })),
            ]}
          />

          <Input
            label="Minimum Capacity"
            type="number"
            min="1"
            placeholder="e.g. 6"
            value={minCapacity}
            onChange={(e) => setMinCapacity(e.target.value)}
          />

          <DatePicker
            label="Date"
            value={searchDate}
            onChange={(e) => setSearchDate(e.target.value)}
          />

          <TimePicker
            label="Start Time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
          />

          <TimePicker
            label="End Time"
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
          />
        </div>

        {/* Amenities Selection */}
        {amenities.length > 0 && (
          <div className="pt-2">
            <label className="block text-xs font-semibold uppercase tracking-wider text-brand-navy mb-2">
              Required Amenities (Matches All Selected)
            </label>
            <div className="flex flex-wrap gap-2">
              {amenities.map((amenity) => {
                const isSelected = selectedAmenityIds.includes(amenity.id)
                return (
                  <button
                    key={amenity.id}
                    type="button"
                    onClick={() => toggleAmenity(amenity.id)}
                    className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors cursor-pointer ${
                      isSelected
                        ? 'bg-brand-navy text-brand-white border-brand-navy'
                        : 'bg-brand-light-gray text-brand-navy border-brand-slate/20 hover:bg-slate-200'
                    }`}
                  >
                    {amenity.name}
                  </button>
                )
              })}
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex justify-end gap-3 pt-3 border-t border-brand-slate/10">
          <Button variant="outline" size="sm" onClick={handleResetFilters}>
            Reset
          </Button>
          <Button type="submit" variant="primary" size="sm">
            Search Rooms
          </Button>
        </div>
      </form>

      {/* Informational Banner on Search and Booking */}
      <div className="rounded-md bg-blue-50/80 p-3.5 border border-blue-200/80 text-xs text-blue-900 flex items-start gap-2">
        <span className="font-bold text-sm">ℹ</span>
        <p>
          Room search results reflect current availability. Availability lookup is informational only and does not hold or reserve a room until final booking execution.
        </p>
      </div>

      {/* Search Results */}
      {isLoading && <Loading text="Searching available rooms..." />}

      {!isLoading && error && (
        <ErrorState
          error={error}
          title="Search Failed"
          onRetry={() => executeSearch(page)}
        />
      )}

      {!isLoading && !error && rooms.length === 0 && (
        <EmptyState
          title="No rooms found"
          description="There are no rooms matching your search criteria. Try modifying your location, capacity, or amenity filters."
        />
      )}

      {!isLoading && !error && rooms.length > 0 && (
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs overflow-hidden">
          <Table columnCount={5}>
            <TableHeader>
              <TableRow>
                <TableHead>Room Name</TableHead>
                <TableHead>Location</TableHead>
                <TableHead>Capacity</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rooms.map((room) => (
                <TableRow key={room.id}>
                  <TableCell className="font-semibold text-brand-navy">
                    <Link
                      to={`/rooms/${room.id}`}
                      className="hover:text-brand-accent hover:underline"
                    >
                      {room.name}
                    </Link>
                    {room.description && (
                      <p className="text-xs text-brand-slate font-normal mt-0.5 truncate max-w-xs">
                        {room.description}
                      </p>
                    )}
                  </TableCell>
                  <TableCell className="text-brand-slate">
                    {room.location ? `${room.location.name} (${room.location.code})` : '—'}
                  </TableCell>
                  <TableCell className="text-brand-navy">
                    <span className="inline-flex items-center gap-1">
                      <span className="font-semibold">{room.capacity}</span> people
                    </span>
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={room.status === 'AVAILABLE' ? 'success' : 'error'}
                      size="sm"
                    >
                      {room.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Link
                      to={`/rooms/${room.id}`}
                      className="inline-flex items-center px-3 py-1 text-xs font-medium rounded-md border border-brand-slate/30 text-brand-navy hover:bg-brand-light-gray transition-colors"
                    >
                      View Details
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          <Pagination
            currentPage={page + 1}
            totalPages={totalPages}
            onPageChange={(newPage) => setPage(newPage - 1)}
          />
        </div>
      )}
    </div>
  )
}

export default RoomSearchPage
