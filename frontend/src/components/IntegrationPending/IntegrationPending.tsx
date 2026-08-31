import React from 'react'

export interface IntegrationPendingProps {
  title: string
  description?: string
  badgeLabel?: string
  className?: string
}

export const IntegrationPending: React.FC<IntegrationPendingProps> = ({
  title,
  description = 'Live data will appear once the required backend service is connected.',
  badgeLabel = 'Integration Pending',
  className = '',
}) => {
  return (
    <div
      className={`p-5 rounded-xl bg-slate-50/80 border border-dashed border-slate-300 text-center space-y-2.5 ${className}`}
    >
      <div className="w-10 h-10 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center mx-auto text-slate-500">
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M13 10V3L4 14h7v7l9-11h-7z"
          />
        </svg>
      </div>
      <div>
        <h4 className="text-sm font-bold text-slate-800">{title}</h4>
        <p className="text-xs text-slate-500 max-w-xs mx-auto mt-0.5">{description}</p>
      </div>
      <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-200/70 text-[11px] font-semibold text-slate-600 border border-slate-300/80">
        <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
        {badgeLabel}
      </div>
    </div>
  )
}

export default IntegrationPending
