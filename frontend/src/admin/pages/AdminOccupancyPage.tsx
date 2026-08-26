import React from 'react'
import { Link } from 'react-router-dom'

export const AdminOccupancyPage: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Header Breadcrumb & Title */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav className="flex items-center gap-2 text-xs text-brand-slate mb-1">
            <Link to="/admin" className="hover:text-brand-navy">Admin</Link>
            <span>/</span>
            <span className="text-brand-navy font-semibold">Occupancy</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Room Occupancy & Scheduling</h1>
          <p className="text-brand-slate text-sm mt-1">
            Monitor live and scheduled room occupancy, real-time room utilization rates, and scheduling density.
          </p>
        </div>
      </div>

      {/* Placeholder Workspace Card */}
      <div className="bg-brand-white rounded-xl border border-brand-slate/20 p-8 sm:p-12 text-center shadow-xs">
        <div className="w-16 h-16 bg-amber-50 text-amber-600 rounded-2xl flex items-center justify-center mx-auto mb-4 border border-amber-100">
          <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <h2 className="text-xl font-bold text-brand-navy mb-2">Occupancy & Density Module</h2>
        <p className="text-brand-slate text-sm max-w-md mx-auto mb-6">
          This administrative workspace will host real-time occupancy heatmaps, active meeting room tracking, and vacancy analysis across campus zones in subsequent phases.
        </p>
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 text-xs font-semibold text-brand-slate border border-slate-200">
          <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
          UI-12 Foundation Ready
        </div>
      </div>
    </div>
  )
}

export default AdminOccupancyPage
