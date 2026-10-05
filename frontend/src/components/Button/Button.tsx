import React from 'react'

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  icon?: React.ReactNode
  iconPosition?: 'left' | 'right'
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'primary',
      size = 'md',
      loading = false,
      icon,
      iconPosition = 'left',
      disabled,
      className = '',
      children,
      type = 'button',
      ...props
    },
    ref,
  ) => {
    const isDisabled = disabled || loading

    const baseStyles =
      'inline-flex items-center justify-center font-medium rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-brand-accent disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer'

    const sizeStyles = {
      sm: 'px-3 py-1.5 text-xs gap-1.5',
      md: 'px-4 py-2 text-sm gap-2',
      lg: 'px-5 py-2.5 text-base gap-2.5',
    }[size]

    const variantStyles = {
      primary: 'bg-brand-navy text-brand-white hover:bg-brand-navy/90 border border-transparent shadow-sm',
      secondary: 'bg-brand-light-gray text-brand-navy hover:bg-slate-200 border border-brand-slate/20',
      outline: 'bg-transparent text-brand-navy border border-brand-slate/30 hover:bg-brand-light-gray',
      danger: 'bg-red-600 text-white hover:bg-red-700 border border-transparent shadow-sm',
    }[variant]

    return (
      <button
        ref={ref}
        type={type}
        disabled={isDisabled}
        aria-busy={loading}
        className={`${baseStyles} ${sizeStyles} ${variantStyles} ${className}`}
        {...props}
      >
        {loading ? (
          <>
            <span
              className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin"
              role="status"
              aria-label="Loading"
            />
            {children}
          </>
        ) : (
          <>
            {icon && iconPosition === 'left' && <span className="inline-flex shrink-0">{icon}</span>}
            {children}
            {icon && iconPosition === 'right' && <span className="inline-flex shrink-0">{icon}</span>}
          </>
        )}
      </button>
    )
  },
)

Button.displayName = 'Button'

export default Button
