import React from 'react'

export interface LoadingProps {
  size?: 'sm' | 'md' | 'lg'
  text?: string
  fullPage?: boolean
  className?: string
}

export const Loading: React.FC<LoadingProps> = ({
  size = 'md',
  text,
  fullPage = false,
  className = '',
}) => {
  const spinnerSizeStyles = {
    sm: 'w-4 h-4 border-2',
    md: 'w-8 h-8 border-3',
    lg: 'w-12 h-12 border-4',
  }[size]

  const containerClasses = fullPage
    ? 'fixed inset-0 z-50 flex flex-col items-center justify-center bg-brand-light-gray/80 backdrop-blur-xs'
    : 'flex flex-col items-center justify-center p-6'

  return (
    <div
      className={`${containerClasses} ${className}`}
      role="status"
      aria-busy="true"
      aria-live="polite"
    >
      <div
        className={`${spinnerSizeStyles} border-brand-navy border-t-transparent rounded-full animate-spin`}
        aria-hidden="true"
      />
      {text && <p className="mt-3 text-sm font-medium text-brand-slate">{text}</p>}
      <span className="sr-only">{text || 'Loading...'}</span>
    </div>
  )
}

export default Loading
