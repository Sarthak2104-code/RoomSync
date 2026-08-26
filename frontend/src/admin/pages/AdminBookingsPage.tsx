import React from 'react'
import { Link } from 'react-router-dom'

export const AdminBookingsPage: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Header Breadcrumb & Title */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav className="flex items-center gap-2 text-xs text-brand-slate mb-1">
            <Link to="/admin" className="hover:text-brand-navy">Admin</Link>
            <span>/</span>
            <span className="text-brand-navy font-semibold">Bookings</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Booking Administration</h1>
          <p className="text-brand-slate text-sm mt-1">
            View and administer one-time and recurring bookings across all locations, manage cancellations, and resolve conflicts.
          </p>
        </div>
      </div>

      {/* Placeholder Workspace Card */}
      <div className="bg-brand-white rounded-xl border border-brand-slate/20 p-8 sm:p-12 text-center shadow-xs">
        <div className="w-16 h-16 bg-emerald-50 text-emerald-600 rounded-2xl flex items-center justify-center mx-auto mb-4 border border-emerald-100">
          <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
        </div>
        <h2 className="text-xl font-bold text-brand-navy mb-2">Booking Supervision Module</h2>
        <p className="text-brand-slate text-sm max-w-md mx-auto mb-6">
          This administrative workspace will provide enterprise-wide booking oversight, admin cancellation authority, occurrence conflict management, and schedule reallocation in subsequent phases.
        </p>
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 text-xs font-semibold text-brand-slate border border-slate-200">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          UI-12 Foundation Ready
        </div>
      </div>
    </div>
  )
}

export default AdminBookingsPage
