import React from 'react'
import { Link } from 'react-router-dom'

export const AdminAuditPage: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Header Breadcrumb & Title */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav className="flex items-center gap-2 text-xs text-brand-slate mb-1">
            <Link to="/admin" className="hover:text-brand-navy">Admin</Link>
            <span>/</span>
            <span className="text-brand-navy font-semibold">Audit</span>
          </nav>
          <h1 className="text-2xl sm:text-3xl font-bold text-brand-navy">System Audit Logs</h1>
          <p className="text-brand-slate text-sm mt-1">
            Inspect immutable system audit trails, administrative actions, outbox delivery logs, and security events.
          </p>
        </div>
      </div>

      {/* Placeholder Workspace Card */}
      <div className="bg-brand-white rounded-xl border border-brand-slate/20 p-8 sm:p-12 text-center shadow-xs">
        <div className="w-16 h-16 bg-slate-100 text-slate-700 rounded-2xl flex items-center justify-center mx-auto mb-4 border border-slate-200">
          <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        </div>
        <h2 className="text-xl font-bold text-brand-navy mb-2">Audit & Compliance Trail Module</h2>
        <p className="text-brand-slate text-sm max-w-md mx-auto mb-6">
          This administrative workspace will host the immutable audit ledger, actor history filters, notification outbox statuses, and system compliance records in subsequent phases.
        </p>
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 text-xs font-semibold text-brand-slate border border-slate-200">
          <span className="w-2 h-2 rounded-full bg-slate-600 animate-pulse" />
          UI-12 Foundation Ready
        </div>
      </div>
    </div>
  )
}

export default AdminAuditPage
