import React, { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { userService } from '@/api/userService'
import type { UserProfileResponse } from '@/types/user'
import type { ApiError } from '@/types/api'
import { toast } from 'sonner'

export const ProfilePage: React.FC = () => {
  const location = useLocation()
  const isAdminPortal = location.pathname.startsWith('/admin')
  const [profile, setProfile] = useState<UserProfileResponse | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let isMounted = true

    const fetchProfile = async () => {
      setIsLoading(true)
      setError(null)
      try {
        const data = await userService.getCurrentUserProfile()
        if (isMounted) {
          setProfile(data)
        }
      } catch (err: unknown) {
        if (isMounted) {
          const apiErr = err as ApiError
          const msg = apiErr.message || 'Failed to load user profile'
          setError(msg)
          toast.error(msg)
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    fetchProfile()

    return () => {
      isMounted = false
    }
  }, [])

  if (isLoading) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
        <div className="bg-brand-white rounded-lg shadow-sm border border-brand-slate/20 p-8">
          <div className="flex items-center justify-center space-x-3 py-16">
            <div className="w-6 h-6 border-2 border-brand-navy border-t-transparent rounded-full animate-spin" />
            <span className="text-brand-navy font-medium">Loading profile...</span>
          </div>
        </div>
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
        <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
          <svg className="w-10 h-10 text-red-500 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <h3 className="text-base font-semibold text-red-800">Unable to load profile</h3>
          <p className="text-sm text-red-600 mt-1">{error || 'An unexpected error occurred.'}</p>
        </div>
      </div>
    )
  }

  const formattedDate = profile.createdAt
    ? new Date(profile.createdAt).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : 'N/A'

  return (
    <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-brand-navy tracking-tight">
          {isAdminPortal ? 'Admin Profile' : 'User Profile'}
        </h1>
        <p className="text-sm text-brand-slate mt-1">
          {isAdminPortal
            ? 'Your authoritative corporate identity, administrator privileges, and location boundaries.'
            : 'Your authoritative corporate identity and location boundaries.'}
        </p>
      </div>

      {/* Main Profile Card */}
      <div className="bg-brand-white rounded-lg shadow-sm border border-brand-slate/20 overflow-hidden">
        {/* Profile Banner */}
        <div className="bg-gradient-to-r from-brand-navy to-brand-navy/90 px-6 py-8 text-brand-white">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex items-center space-x-4">
              <div className="w-16 h-16 rounded-full bg-brand-white/10 border-2 border-brand-white/20 flex items-center justify-center text-brand-white font-bold text-2xl">
                {profile.name ? profile.name.charAt(0).toUpperCase() : 'U'}
              </div>
              <div>
                <h2 className="text-xl font-bold text-brand-white">{profile.name}</h2>
                <div className="flex items-center gap-2 mt-1">
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-brand-accent/20 text-brand-white border border-brand-accent/30 font-mono">
                    {profile.wissenId}
                  </span>
                  <span
                    className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                      profile.role === 'ADMIN'
                        ? 'bg-purple-500/20 text-purple-200 border border-purple-400/30'
                        : 'bg-blue-500/20 text-blue-200 border border-blue-400/30'
                    }`}
                  >
                    {profile.role}
                  </span>
                </div>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {profile.bookingEnabled ? (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-200 border border-emerald-400/30">
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  Booking Enabled
                </span>
              ) : (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/20 text-amber-200 border border-amber-400/30">
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  </svg>
                  Booking Blocked
                </span>
              )}

              {profile.active ? (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-emerald-500/20 text-emerald-200 border border-emerald-400/30">
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  Active Account
                </span>
              ) : (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-red-500/20 text-red-200 border border-red-400/30">
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                  Inactive Account
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Read-Only Attributes Grid */}
        <div className="p-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Identity Information Section */}
            <div className="space-y-4">
              <h3 className="text-xs font-semibold text-brand-slate uppercase tracking-wider border-b border-brand-slate/10 pb-2">
                Identity & Corporate Access
              </h3>

              <div className="flex items-start space-x-3">
                <svg className="w-5 h-5 text-brand-slate shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
                <div>
                  <div className="text-xs text-brand-slate font-medium">Full Name</div>
                  <div className="text-sm font-semibold text-brand-navy mt-0.5">{profile.name}</div>
                </div>
              </div>

              <div className="flex items-start space-x-3">
                <svg className="w-5 h-5 text-brand-slate shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
                <div>
                  <div className="text-xs text-brand-slate font-medium">Wissen ID</div>
                  <div className="text-sm font-mono font-bold text-brand-navy mt-0.5">{profile.wissenId}</div>
                </div>
              </div>

              <div className="flex items-start space-x-3">
                <svg className="w-5 h-5 text-brand-slate shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                <div>
                  <div className="text-xs text-brand-slate font-medium">Email Address</div>
                  <div className="text-sm text-brand-navy mt-0.5">{profile.email}</div>
                </div>
              </div>

              <div className="flex items-start space-x-3">
                <svg className="w-5 h-5 text-brand-slate shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
                <div>
                  <div className="text-xs text-brand-slate font-medium">Booking Capability</div>
                  <div className="text-sm font-semibold text-brand-navy mt-0.5 flex items-center gap-1.5">
                    {profile.bookingEnabled ? (
                      <span className="text-emerald-700 font-medium">Allowed to book rooms</span>
                    ) : (
                      <span className="text-amber-700 font-medium">Restricted by Administrator</span>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex items-start space-x-3">
                <svg className="w-5 h-5 text-brand-slate shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <div>
                  <div className="text-xs text-brand-slate font-medium">Member Since</div>
                  <div className="text-sm text-brand-navy mt-0.5">{formattedDate}</div>
                </div>
              </div>
            </div>

            {/* Location Boundaries Section */}
            <div className="space-y-4">
              <h3 className="text-xs font-semibold text-brand-slate uppercase tracking-wider border-b border-brand-slate/10 pb-2">
                Operational Location Boundary
              </h3>

              <div className="flex items-start space-x-3">
                <svg className="w-5 h-5 text-brand-slate shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                </svg>
                <div>
                  <div className="text-xs text-brand-slate font-medium">Assigned Location</div>
                  <div className="text-sm font-semibold text-brand-navy mt-0.5">
                    {profile.location?.name || 'Unassigned'}
                  </div>
                </div>
              </div>

              <div className="flex items-start space-x-3">
                <svg className="w-5 h-5 text-brand-slate shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
                <div>
                  <div className="text-xs text-brand-slate font-medium">Location Code</div>
                  <div className="text-sm font-mono text-brand-navy mt-0.5">
                    {profile.location?.code || 'N/A'}
                  </div>
                </div>
              </div>

              <div className="flex items-start space-x-3">
                <svg className="w-5 h-5 text-brand-slate shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <div>
                  <div className="text-xs text-brand-slate font-medium">Operating Timezone</div>
                  <div className="text-sm text-brand-navy mt-0.5">
                    {profile.location?.timezone || 'N/A'}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Read-Only Notice */}
          <div className="mt-8 rounded-md bg-brand-light-gray/50 border border-brand-slate/15 p-4">
            <div className="flex items-center gap-2">
              <svg className="w-4 h-4 text-brand-slate shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <p className="text-xs text-brand-slate">
                <span className="font-semibold text-brand-navy">Read-Only Profile:</span> User identity, Wissen ID, role assignment, and location boundaries are corporately managed and synchronized with backend security authority.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default ProfilePage
