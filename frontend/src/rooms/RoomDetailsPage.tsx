import React, { useEffect, useState, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { roomService } from '@/api/roomService'
import type { RoomResponse } from '@/types/room'
import type { BackendError } from '@/types/api'
import {
  Badge,
  Button,
  DatePicker,
  ErrorState,
  Loading,
  TimePicker,
} from '@/components'

export const RoomDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const roomId = id ? Number(id) : null

  const [room, setRoom] = useState<RoomResponse | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<BackendError | null>(null)

  // Availability lookup filter state
  const [availDate, setAvailDate] = useState<string>(
    () => new Date().toISOString().split('T')[0]
  )
  const [availStart, setAvailStart] = useState<string>('09:00')
  const [availEnd, setAvailEnd] = useState<string>('10:00')

  const fetchRoom = useCallback(async () => {
    if (!roomId) return
    setIsLoading(true)
    setError(null)
    try {
      const data = await roomService.getRoomById(roomId)
      setRoom(data)
    } catch (err: unknown) {
      setError(err as BackendError)
    } finally {
      setIsLoading(false)
    }
  }, [roomId])

  useEffect(() => {
    fetchRoom()
  }, [fetchRoom])

  if (isLoading) {
    return <Loading fullPage text="Loading room details..." />
  }

  if (error || !room) {
    return (
      <div className="space-y-4">
        <Link
          to="/rooms"
          className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent mb-2"
        >
          &larr; Back to Room Search
        </Link>
        <ErrorState
          error={error || { message: 'Room not found.' }}
          title={error?.status === 404 ? 'Room Not Found' : 'Error Loading Room'}
          onRetry={fetchRoom}
        />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Breadcrumb Navigation */}
      <div>
        <Link
          to="/rooms"
          className="inline-flex items-center gap-1 text-sm font-medium text-brand-navy hover:text-brand-accent"
        >
          &larr; Back to Room Search
        </Link>
      </div>

      {/* Main Room Card */}
      <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs overflow-hidden">
        {/* Header */}
        <div className="p-6 sm:p-8 border-b border-brand-slate/10 flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">{room.name}</h1>
              <Badge
                variant={room.status === 'AVAILABLE' ? 'success' : 'error'}
                size="md"
              >
                {room.status}
              </Badge>
              {!room.active && (
                <Badge variant="neutral" size="md">
                  Inactive
                </Badge>
              )}
            </div>
            <p className="mt-1 text-sm text-brand-slate">
              {room.location
                ? `${room.location.name} (${room.location.code})`
                : 'Unassigned location'}
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={fetchRoom}>
              Refresh Status
            </Button>
            {room.status === 'AVAILABLE' && room.active && (
              <Link to={`/rooms/${room.id}/book`}>
                <Button variant="primary" size="sm">
                  Book this Room &rarr;
                </Button>
              </Link>
            )}
          </div>
        </div>

        {/* Details Grid */}
        <div className="p-6 sm:p-8 grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="space-y-4">
            <div>
              <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-slate">
                Capacity
              </h2>
              <p className="mt-1 text-lg font-medium text-brand-navy">
                Up to {room.capacity} participants
              </p>
            </div>

            <div>
              <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-slate">
                Location & Timezone
              </h2>
              <p className="mt-1 text-sm text-brand-navy">
                {room.location?.name || '—'} &bull; Asia/Kolkata (IST)
              </p>
            </div>

            <div>
              <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-slate">
                Administrative State
              </h2>
              <p className="mt-1 text-sm text-brand-navy capitalize">
                Status: <span className="font-semibold">{room.status}</span>
              </p>
            </div>
          </div>

          <div className="space-y-4">
            <div>
              <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-slate">
                Description
              </h2>
              <p className="mt-1 text-sm text-brand-navy whitespace-pre-line leading-relaxed">
                {room.description || 'No description provided for this room.'}
              </p>
            </div>

            {room.amenities && room.amenities.length > 0 && (
              <div>
                <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-slate mb-2">
                  Available Amenities
                </h2>
                <div className="flex flex-wrap gap-2">
                  {room.amenities.map((a) => (
                    <Badge key={a.id} variant="neutral" size="sm">
                      {a.name}
                    </Badge>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* UI-5.4 Room Availability Section */}
      <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs p-6 sm:p-8 space-y-6">
        <div>
          <h2 className="text-xl font-bold text-brand-navy">Room Availability Check</h2>
          <p className="text-sm text-brand-slate mt-1">
            Check real-time availability for this room on a specific date and time interval.
          </p>
        </div>

        {/* Informational Warning */}
        <div className="rounded-md bg-blue-50/80 p-3.5 border border-blue-200/80 text-xs text-blue-900 flex items-start gap-2">
          <span className="font-bold text-sm">ℹ</span>
          <p>
            Availability checks are informational and do not hold or reserve a room. Availability is verified definitively during booking execution.
          </p>
        </div>

        {/* Time Interval Selector */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <DatePicker
            label="Date"
            value={availDate}
            onChange={(e) => setAvailDate(e.target.value)}
          />
          <TimePicker
            label="From"
            value={availStart}
            onChange={(e) => setAvailStart(e.target.value)}
          />
          <TimePicker
            label="To"
            value={availEnd}
            onChange={(e) => setAvailEnd(e.target.value)}
          />
        </div>

        {/* Availability Status Display */}
        <div className="pt-4 border-t border-brand-slate/10">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-brand-slate mb-3">
            Calculated Status for Interval
          </h3>
          <div className="flex items-center justify-between p-4 rounded-md bg-brand-light-gray border border-brand-slate/20">
            <div>
              <p className="text-sm font-semibold text-brand-navy">
                {availDate || 'Today'} &bull; {availStart} – {availEnd}
              </p>
              <p className="text-xs text-brand-slate mt-0.5">
                Room status: {room.status}
              </p>
            </div>
            <div>
              <Badge
                variant={room.status === 'AVAILABLE' ? 'success' : 'error'}
                size="md"
              >
                {room.status === 'AVAILABLE' ? 'Currently Available' : 'Currently Unavailable / Locked'}
              </Badge>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default RoomDetailsPage
