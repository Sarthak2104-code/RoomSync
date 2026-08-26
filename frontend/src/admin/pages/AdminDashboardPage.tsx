import React from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import Badge from '@/components/Badge/Badge'

export const AdminDashboardPage: React.FC = () => {
  const { user, role } = useAuth()

  const adminModules = [
    {
      title: 'Locations',
      description: 'Manage RoomSync locations, timezone configuration, activation status, and site-level parameters.',
      to: '/admin/locations',
      icon: (
        <svg className="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      ),
      badge: 'Configuration',
    },
    {
      title: 'Rooms',
      description: 'Configure meeting spaces, maximum seating capacity, amenities, maintenance schedules, and active status.',
      to: '/admin/rooms',
      icon: (
        <svg className="w-6 h-6 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
        </svg>
      ),
      badge: 'Facilities',
    },
    {
      title: 'Bookings',
      description: 'Supervise all one-time and recurring bookings, inspect conflict resolutions, and execute administrative overrides.',
      to: '/admin/bookings',
      icon: (
        <svg className="w-6 h-6 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      ),
      badge: 'Operations',
    },
    {
      title: 'Occupancy',
      description: 'Monitor real-time room occupancy, peak density periods, and schedule utilization across campuses.',
      to: '/admin/occupancy',
      icon: (
        <svg className="w-6 h-6 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
      badge: 'Monitoring',
    },
    {
      title: 'Analytics',
      description: 'Review comprehensive room utilization statistics, capacity forecasts, and department usage reports.',
      to: '/admin/analytics',
      icon: (
        <svg className="w-6 h-6 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
      ),
      badge: 'Reports',
    },
    {
      title: 'Amenities',
      description: 'Catalog room hardware, video conferencing systems, whiteboards, presentation display equipment, and fixtures.',
      to: '/admin/amenities',
      icon: (
        <svg className="w-6 h-6 text-teal-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
        </svg>
      ),
      badge: 'Inventory',
    },
    {
      title: 'Admin Requests',
      description: 'Review and approve recurring conflict escalation requests, room change requests, and priority booking petitions.',
      to: '/admin/requests',
      icon: (
        <svg className="w-6 h-6 text-rose-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
      ),
      badge: 'Escalations',
    },
    {
      title: 'Audit Logs',
      description: 'Inspect immutable system event logs, actor histories, outbox notification dispatches, and compliance trails.',
      to: '/admin/audit',
      icon: (
        <svg className="w-6 h-6 text-slate-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      ),
      badge: 'Security',
    },
  ]

  const currentDate = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  })

  return (
    <div className="space-y-6 sm:space-y-8">
      {/* Welcome Banner */}
      <div className="bg-brand-white rounded-xl p-6 sm:p-8 border border-brand-slate/20 shadow-xs">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="text-2xl">👋</span>
              <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">
                Welcome back, Admin!
              </h1>
            </div>
            <p className="text-brand-slate text-sm sm:text-base max-w-2xl">
              RoomSync Administration Foundation. Manage locations, rooms, bookings, system occupancy, and audit records across the organization.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="bg-slate-50 border border-slate-200 px-3.5 py-1.5 rounded-lg text-xs font-medium text-brand-slate flex items-center gap-2">
              <svg className="w-4 h-4 text-brand-slate" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <span>{currentDate}</span>
            </div>
            <Badge variant="warning" size="md" className="font-bold">
              {role || 'ADMIN'}
            </Badge>
          </div>
        </div>

        {/* Foundation Notice */}
        <div className="mt-6 pt-6 border-t border-slate-100 flex items-start gap-3 bg-blue-50/50 p-4 rounded-lg border-blue-100">
          <div className="w-5 h-5 text-blue-600 shrink-0 mt-0.5">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <div className="text-xs sm:text-sm text-brand-navy">
            <span className="font-semibold text-blue-900">Admin Foundation Mode:</span> Authenticated session active for{' '}
            <span className="font-semibold">{user?.email}</span>. Individual administration services and live data management will connect in subsequent phases.
          </div>
        </div>
      </div>

      {/* Quick Navigation / Module Grid */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-brand-navy">Administration Modules</h2>
          <span className="text-xs text-brand-slate font-medium">8 functional workspaces</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
          {adminModules.map((module) => (
            <Link
              key={module.to}
              to={module.to}
              className="group bg-brand-white p-5 rounded-xl border border-brand-slate/20 shadow-2xs hover:shadow-md hover:border-brand-navy/30 transition-all flex flex-col justify-between"
            >
              <div>
                <div className="flex items-center justify-between mb-3">
                  <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 group-hover:scale-105 transition-transform">
                    {module.icon}
                  </div>
                  <span className="text-[11px] font-semibold text-slate-500 bg-slate-100 px-2 py-0.5 rounded-full">
                    {module.badge}
                  </span>
                </div>
                <h3 className="font-bold text-brand-navy text-base group-hover:text-blue-600 transition-colors">
                  {module.title}
                </h3>
                <p className="text-xs text-brand-slate mt-1.5 leading-relaxed">
                  {module.description}
                </p>
              </div>

              <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs font-semibold text-brand-navy group-hover:text-blue-600">
                <span>Manage module</span>
                <svg className="w-4 h-4 transform group-hover:translate-x-1 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}

export default AdminDashboardPage
