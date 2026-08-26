import React from 'react'
import { Link } from 'react-router-dom'

export const AdminAmenitiesPage: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Header Breadcrumb & Title */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav className="flex items-center gap-2 text-xs text-brand-slate mb-1">
            <Link to="/admin" className="hover:text-brand-navy">Admin</Link>
            <span>/</span>
            <span className="text-brand-navy font-semibold">Amenities</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">Amenity Catalog</h1>
          <p className="text-brand-slate text-sm mt-1">
            Manage standard equipment, presentation amenities, videoconferencing gear, and facility features.
          </p>
        </div>
      </div>

      {/* Placeholder Workspace Card */}
      <div className="bg-brand-white rounded-xl border border-brand-slate/20 p-8 sm:p-12 text-center shadow-xs">
        <div className="w-16 h-16 bg-teal-50 text-teal-600 rounded-2xl flex items-center justify-center mx-auto mb-4 border border-teal-100">
          <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
          </svg>
        </div>
        <h2 className="text-xl font-bold text-brand-navy mb-2">Amenity Management Module</h2>
        <p className="text-brand-slate text-sm max-w-md mx-auto mb-6">
          This administrative workspace will host standard amenity definitions, hardware catalogs (projectors, AV, whiteboards), and room linkage tools in subsequent phases.
        </p>
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 text-xs font-semibold text-brand-slate border border-slate-200">
          <span className="w-2 h-2 rounded-full bg-teal-500 animate-pulse" />
          UI-12 Foundation Ready
        </div>
      </div>
    </div>
  )
}

export default AdminAmenitiesPage
