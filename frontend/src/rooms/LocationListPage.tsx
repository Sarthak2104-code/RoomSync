import React, { useEffect, useState, useCallback } from 'react'
import { locationService } from '@/api/locationService'
import type { LocationResponse } from '@/types/location'
import type { BackendError } from '@/types/api'
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Loading,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components'

export const LocationListPage: React.FC = () => {
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [page, setPage] = useState<number>(0)
  const [totalPages, setTotalPages] = useState<number>(0)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<BackendError | null>(null)

  const fetchLocations = useCallback(async (pageNumber = 0) => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await locationService.getLocations({ page: pageNumber, size: 10 })
      setLocations(data.content)
      setPage(data.page)
      setTotalPages(data.totalPages)
    } catch (err: unknown) {
      setError(err as BackendError)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchLocations(page)
  }, [fetchLocations, page])

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-brand-navy">Company Locations</h1>
          <p className="text-sm text-brand-slate mt-1">
            Active office sites available for room booking and collaboration.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => fetchLocations(page)}>
          Refresh
        </Button>
      </div>

      {isLoading && <Loading text="Loading active locations..." />}

      {!isLoading && error && (
        <ErrorState
          error={error}
          title="Failed to load locations"
          onRetry={() => fetchLocations(page)}
        />
      )}

      {!isLoading && !error && locations.length === 0 && (
        <EmptyState
          title="No active locations found"
          description="There are currently no active office locations available in the system."
        />
      )}

      {!isLoading && !error && locations.length > 0 && (
        <div className="bg-brand-white rounded-lg border border-brand-slate/20 shadow-xs overflow-hidden">
          <Table columnCount={4}>
            <TableHeader>
              <TableRow>
                <TableHead>Location Name</TableHead>
                <TableHead>Code</TableHead>
                <TableHead>Timezone</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {locations.map((loc) => (
                <TableRow key={loc.id}>
                  <TableCell className="font-semibold text-brand-navy">{loc.name}</TableCell>
                  <TableCell>
                    <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                      {loc.code}
                    </span>
                  </TableCell>
                  <TableCell className="text-brand-slate">Asia/Kolkata (IST)</TableCell>
                  <TableCell>
                    <Badge variant={loc.active ? 'success' : 'neutral'} size="sm">
                      {loc.active ? 'Active' : 'Inactive'}
                    </Badge>
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

export default LocationListPage
