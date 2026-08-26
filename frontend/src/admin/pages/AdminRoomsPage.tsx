import React from 'react'
import { Link } from 'react-router-dom'

export const AdminRoomsPage: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Header Breadcrumb & Title */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav className="flex items-center gap-2 text-xs text-brand-slate mb-1">
            <Link to="/admin" className="hover:text-brand-navy">Admin</Link>
            <span>/</span>
            <span className="text-brand-navy font-semibold">Rooms</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Room Management</h1>
          <p className="text-brand-slate text-sm mt-1">
            Configure physical meeting rooms, capacity, status, amenities, and room availability.
          </p>
        </div>
      </div>

      {/* Placeholder Workspace Card */}
      <div className="bg-brand-white rounded-xl border border-brand-slate/20 p-8 sm:p-12 text-center shadow-xs">
        <div className="w-16 h-16 bg-indigo-50 text-indigo-600 rounded-2xl flex items-center justify-center mx-auto mb-4 border border-indigo-100">
          <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
          </svg>
        </div>
        <h2 className="text-xl font-bold text-brand-navy mb-2">Room Administration Module</h2>
        <p className="text-brand-slate text-sm max-w-md mx-auto mb-6">
          This administrative workspace will host meeting room inventory, seating capacities, floor mapping, status toggling, and amenity associations in subsequent phases.
        </p>
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 text-xs font-semibold text-brand-slate border border-slate-200">
          <span className="w-2 h-2 rounded-full bg-indigo-500 animate-pulse" />
          UI-12 Foundation Ready
        </div>
      </div>
    </div>
  )
}

export default AdminRoomsPage
