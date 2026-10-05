import React, { useEffect, useState, useCallback } from 'react'
import { userAdminService, type AdminUserSummary } from '@/admin/api/userAdminService'
import { locationService } from '@/api/locationService'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/types/api'
import { useAuth } from '@/auth/AuthContext'
import { toast } from 'sonner'

export const AdminUsersPage: React.FC = () => {
  const { user: currentUser } = useAuth()
  const [users, setUsers] = useState<AdminUserSummary[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  // Filters
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [selectedLocationId, setSelectedLocationId] = useState<string>('')
  const [selectedBookingStatus, setSelectedBookingStatus] = useState<string>('')
  const [selectedAccountStatus, setSelectedAccountStatus] = useState<string>('')
  const [selectedRole, setSelectedRole] = useState<string>('')

  // Pagination
  const [currentPage, setCurrentPage] = useState<number>(0)
  const [totalPages, setTotalPages] = useState<number>(1)
  const [totalElements, setTotalElements] = useState<number>(0)
  const pageSize = 10

  // Action Modal
  const [actionTargetUser, setActionTargetUser] = useState<AdminUserSummary | null>(null)
  const [actionReason, setActionReason] = useState<string>('')
  const [isSubmittingAction, setIsSubmittingAction] = useState<boolean>(false)

  // Fetch locations for filter
  useEffect(() => {
    locationService
      .getLocations({ size: 100 })
      .then((res) => {
        setLocations(res.content || [])
      })
      .catch((err) => {
        console.error('Failed to load locations for user filter', err)
      })
  }, [])

  const fetchUsers = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const params: Parameters<typeof userAdminService.getUsers>[0] = {
        page: currentPage,
        size: pageSize,
      }

      if (searchTerm.trim()) {
        params.search = searchTerm.trim()
      }
      if (selectedLocationId) {
        params.locationId = Number(selectedLocationId)
      }
      if (selectedBookingStatus === 'ENABLED') {
        params.bookingEnabled = true
      } else if (selectedBookingStatus === 'BLOCKED') {
        params.bookingEnabled = false
      }
      if (selectedAccountStatus === 'ACTIVE') {
        params.active = true
      } else if (selectedAccountStatus === 'INACTIVE') {
        params.active = false
      }
      if (selectedRole) {
        params.role = selectedRole
      }

      const res = await userAdminService.getUsers(params)
      setUsers(res.content || [])
      setTotalPages(res.totalPages || 1)
      setTotalElements(res.totalElements || 0)
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const msg = apiErr.message || 'Failed to fetch user list'
      setError(msg)
      toast.error(msg)
    } finally {
      setIsLoading(false)
    }
  }, [
    currentPage,
    searchTerm,
    selectedLocationId,
    selectedBookingStatus,
    selectedAccountStatus,
    selectedRole,
  ])

  useEffect(() => {
    fetchUsers()
  }, [fetchUsers])

  const handleToggleBookingAccess = async () => {
    if (!actionTargetUser) return
    if (currentUser?.id === actionTargetUser.id) {
      toast.error('Administrators cannot modify their own booking access.')
      setActionTargetUser(null)
      return
    }

    setIsSubmittingAction(true)
    const targetState = !actionTargetUser.bookingEnabled
    try {
      const updated = await userAdminService.updateBookingAccess(actionTargetUser.id, {
        bookingEnabled: targetState,
        reason: actionReason.trim() || undefined,
      })

      toast.success(
        targetState
          ? `Booking access enabled for ${updated.name} (${updated.wissenId})`
          : `Booking access restricted for ${updated.name} (${updated.wissenId})`
      )

      // Update local state
      setUsers((prev) =>
        prev.map((u) => (u.id === updated.id ? { ...u, bookingEnabled: updated.bookingEnabled } : u))
      )
      setActionTargetUser(null)
      setActionReason('')
    } catch (err: unknown) {
      const apiErr = err as ApiError
      toast.error(apiErr.message || 'Failed to update user booking access')
    } finally {
      setIsSubmittingAction(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-brand-navy tracking-tight">User Management</h1>
          <p className="text-sm text-brand-slate mt-1">
            Manage employee corporate access, assigned location boundaries, and room booking permissions.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold px-3 py-1.5 rounded-md bg-slate-100 text-brand-slate border border-slate-200">
            Total Users: {totalElements}
          </span>
        </div>
      </div>

      {/* Filters Bar */}
      <div className="bg-brand-white rounded-lg shadow-xs border border-brand-slate/20 p-4 space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
          {/* Search Input */}
          <div className="lg:col-span-2">
            <label className="block text-xs font-medium text-brand-slate mb-1">Search Users</label>
            <div className="relative">
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value)
                  setCurrentPage(0)
                }}
                placeholder="Search by Name, Wissen ID, or Email..."
                className="w-full pl-9 pr-3 py-2 text-sm border border-slate-300 rounded-md focus:outline-hidden focus:ring-1 focus:ring-brand-navy focus:border-brand-navy"
              />
              <svg
                className="w-4 h-4 text-slate-400 absolute left-3 top-2.5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
          </div>

          {/* Location Filter */}
          <div>
            <label className="block text-xs font-medium text-brand-slate mb-1">Location</label>
            <select
              value={selectedLocationId}
              onChange={(e) => {
                setSelectedLocationId(e.target.value)
                setCurrentPage(0)
              }}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-md focus:outline-hidden focus:ring-1 focus:ring-brand-navy focus:border-brand-navy"
            >
              <option value="">All Locations</option>
              {locations.map((loc) => (
                <option key={loc.id} value={loc.id}>
                  {loc.name} ({loc.code})
                </option>
              ))}
            </select>
          </div>

          {/* Booking Access Filter */}
          <div>
            <label className="block text-xs font-medium text-brand-slate mb-1">Booking Access</label>
            <select
              value={selectedBookingStatus}
              onChange={(e) => {
                setSelectedBookingStatus(e.target.value)
                setCurrentPage(0)
              }}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-md focus:outline-hidden focus:ring-1 focus:ring-brand-navy focus:border-brand-navy"
            >
              <option value="">All Access States</option>
              <option value="ENABLED">Booking Enabled</option>
              <option value="BLOCKED">Booking Blocked</option>
            </select>
          </div>

          {/* Account Status Filter */}
          <div>
            <label className="block text-xs font-medium text-brand-slate mb-1">Account Status</label>
            <select
              value={selectedAccountStatus}
              onChange={(e) => {
                setSelectedAccountStatus(e.target.value)
                setCurrentPage(0)
              }}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-md focus:outline-hidden focus:ring-1 focus:ring-brand-navy focus:border-brand-navy"
            >
              <option value="">All Account States</option>
              <option value="ACTIVE">Active Account</option>
              <option value="INACTIVE">Inactive Account</option>
            </select>
          </div>

          {/* Role Filter */}
          <div>
            <label className="block text-xs font-medium text-brand-slate mb-1">Role</label>
            <select
              value={selectedRole}
              onChange={(e) => {
                setSelectedRole(e.target.value)
                setCurrentPage(0)
              }}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-md focus:outline-hidden focus:ring-1 focus:ring-brand-navy focus:border-brand-navy"
            >
              <option value="">All Roles</option>
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>
        </div>
      </div>

      {/* Users Table */}
      <div className="bg-brand-white rounded-lg shadow-xs border border-brand-slate/20 overflow-hidden">
        {isLoading ? (
          <div className="py-16 text-center">
            <div className="w-6 h-6 border-2 border-brand-navy border-t-transparent rounded-full animate-spin mx-auto mb-2" />
            <span className="text-sm font-medium text-brand-slate">Loading users...</span>
          </div>
        ) : error ? (
          <div className="p-8 text-center">
            <p className="text-sm font-semibold text-red-600 mb-2">Error loading users</p>
            <p className="text-xs text-brand-slate mb-4">{error}</p>
            <button
              type="button"
              onClick={() => fetchUsers()}
              className="px-3 py-1.5 text-xs font-semibold text-brand-navy bg-slate-100 rounded-md hover:bg-slate-200"
            >
              Retry
            </button>
          </div>
        ) : users.length === 0 ? (
          <div className="py-16 text-center">
            <svg
              className="w-10 h-10 text-slate-300 mx-auto mb-2"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
            <p className="text-sm font-medium text-brand-slate">No users found matching current filters.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200">
              <thead className="bg-slate-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                    User / Identity
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                    Wissen ID
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                    Role
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                    Location
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                    Account Status
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                    Booking Access
                  </th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-slate-600 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="bg-brand-white divide-y divide-slate-200">
                {users.map((user) => (
                  <tr key={user.id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="flex items-center space-x-3">
                        <div className="w-8 h-8 rounded-full bg-brand-navy/10 text-brand-navy flex items-center justify-center font-bold text-xs">
                          {user.name ? user.name.charAt(0).toUpperCase() : 'U'}
                        </div>
                        <div>
                          <div className="text-sm font-semibold text-brand-navy">{user.name}</div>
                          <div className="text-xs text-brand-slate">{user.email}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-brand-navy border border-slate-200">
                        {user.wissenId}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
                          user.role === 'ADMIN'
                            ? 'bg-purple-100 text-purple-800'
                            : 'bg-blue-100 text-blue-800'
                        }`}
                      >
                        {user.role}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="text-xs font-medium text-brand-navy">
                        {user.location?.name || 'Unassigned'}
                      </div>
                      {user.location && (
                        <div className="text-[11px] font-mono text-brand-slate">
                          {user.location.code} • {user.location.timezone}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {user.active ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                          Active
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
                          <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
                          Inactive
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {user.bookingEnabled ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                          </svg>
                          Enabled
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
                          </svg>
                          Blocked
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-right text-xs font-medium">
                      {currentUser?.id === user.id ? (
                        <span className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium bg-slate-100 text-slate-500 border border-slate-200 cursor-default">
                          Current Admin
                        </span>
                      ) : user.bookingEnabled ? (
                        <button
                          type="button"
                          onClick={() => {
                            setActionTargetUser(user)
                            setActionReason('')
                          }}
                          className="px-2.5 py-1 rounded-md text-xs font-semibold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-300 transition-colors"
                        >
                          Block Booking
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => {
                            setActionTargetUser(user)
                            setActionReason('')
                          }}
                          className="px-2.5 py-1 rounded-md text-xs font-semibold text-emerald-800 bg-emerald-50 hover:bg-emerald-100 border border-emerald-300 transition-colors"
                        >
                          Unblock Booking
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Footer */}
        {!isLoading && users.length > 0 && (
          <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
            <span className="text-xs text-slate-500">
              Showing page {currentPage + 1} of {totalPages} ({totalElements} users)
            </span>
            <div className="flex items-center space-x-2">
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.max(p - 1, 0))}
                disabled={currentPage === 0}
                className="px-2.5 py-1 text-xs font-semibold rounded-md border border-slate-300 bg-brand-white text-brand-navy hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.min(p + 1, totalPages - 1))}
                disabled={currentPage >= totalPages - 1}
                className="px-2.5 py-1 text-xs font-semibold rounded-md border border-slate-300 bg-brand-white text-brand-navy hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Confirmation Modal */}
      {actionTargetUser && (
        <div
          className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-xs flex items-center justify-center p-4"
          aria-labelledby="modal-title"
          role="dialog"
          aria-modal="true"
        >
          <div className="bg-brand-white rounded-lg max-w-md w-full p-6 shadow-xl border border-slate-200">
            <div className="flex items-center gap-3">
              <div
                className={`w-10 h-10 rounded-full flex items-center justify-center ${
                  actionTargetUser.bookingEnabled
                    ? 'bg-amber-100 text-amber-600'
                    : 'bg-emerald-100 text-emerald-600'
                }`}
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  {actionTargetUser.bookingEnabled ? (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  ) : (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  )}
                </svg>
              </div>
              <div>
                <h3 id="modal-title" className="text-base font-bold text-brand-navy">
                  {actionTargetUser.bookingEnabled ? 'Block User Room Booking' : 'Restore User Room Booking'}
                </h3>
                <p className="text-xs text-brand-slate">
                  {actionTargetUser.name} ({actionTargetUser.wissenId})
                </p>
              </div>
            </div>

            <div className="mt-4 space-y-3 text-xs text-brand-slate">
              <p>
                {actionTargetUser.bookingEnabled ? (
                  <span>
                    Restricting booking capability will prevent <strong>{actionTargetUser.name}</strong> from creating new room bookings or rescheduling existing reservations. Existing confirmed bookings will remain intact, and the user will still be able to log in and cancel bookings if necessary.
                  </span>
                ) : (
                  <span>
                    Restoring booking capability will allow <strong>{actionTargetUser.name}</strong> to make new room reservations and recurring series across their assigned location.
                  </span>
                )}
              </p>

              <div>
                <label className="block text-xs font-semibold text-brand-navy mb-1">
                  Reason for Action (Optional / Audit trail)
                </label>
                <textarea
                  value={actionReason}
                  onChange={(e) => setActionReason(e.target.value)}
                  placeholder={
                    actionTargetUser.bookingEnabled
                      ? 'e.g., Repeated no-show violations, excessive last-minute conflicts...'
                      : 'e.g., Issue resolved, permission restored...'
                  }
                  rows={3}
                  className="w-full px-3 py-2 text-xs border border-slate-300 rounded-md focus:outline-hidden focus:ring-1 focus:ring-brand-navy focus:border-brand-navy"
                />
              </div>
            </div>

            <div className="mt-6 flex items-center justify-end space-x-3">
              <button
                type="button"
                onClick={() => {
                  setActionTargetUser(null)
                  setActionReason('')
                }}
                disabled={isSubmittingAction}
                className="px-3.5 py-2 text-xs font-medium text-brand-slate hover:text-brand-navy hover:bg-slate-100 rounded-md transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleToggleBookingAccess}
                disabled={isSubmittingAction}
                className={`px-4 py-2 text-xs font-semibold rounded-md text-brand-white shadow-xs transition-colors flex items-center gap-1.5 ${
                  actionTargetUser.bookingEnabled
                    ? 'bg-amber-600 hover:bg-amber-700'
                    : 'bg-emerald-600 hover:bg-emerald-700'
                } disabled:opacity-50`}
              >
                {isSubmittingAction && (
                  <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                )}
                {actionTargetUser.bookingEnabled ? 'Confirm Block' : 'Confirm Unblock'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default AdminUsersPage
