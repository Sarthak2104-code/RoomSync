import React from 'react'
import IntegrationPending from '@/components/IntegrationPending/IntegrationPending'

export const AdminSettingsPage: React.FC = () => {
  return (
    <div className="max-w-4xl mx-auto py-4 space-y-6 animate-in fade-in duration-150">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Admin Settings</h1>
        <p className="text-sm text-slate-500 mt-1">
          Configure organization-wide booking rules, notification dispatches, and system parameters.
        </p>
      </div>

      <IntegrationPending
        title="Admin Settings & Policy Configuration"
        description="System policy controls, security timeout rules, default booking lead times, and global notification dispatch preferences will be manageable here once connected."
        badgeLabel="Foundation Ready"
        className="bg-white rounded-2xl p-8 border border-slate-200 shadow-2xs"
      />
    </div>
  )
}

export default AdminSettingsPage
