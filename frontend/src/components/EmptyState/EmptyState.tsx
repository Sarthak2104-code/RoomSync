import React from 'react'

export interface EmptyStateProps {
  title: string
  description?: string
  icon?: React.ReactNode
  action?: React.ReactNode
  className?: string
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  description,
  icon,
  action,
  className = '',
}) => {
  return (
    <div
      className={`flex flex-col items-center justify-center p-8 text-center bg-brand-white rounded-lg border border-dashed border-brand-slate/30 ${className}`}
    >
      {icon && <div className="mb-4 text-brand-slate/70">{icon}</div>}
      <h3 className="text-base font-semibold text-brand-navy">{title}</h3>
      {description && (
        <p className="mt-1 text-sm text-brand-slate max-w-sm">{description}</p>
      )}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export default EmptyState
