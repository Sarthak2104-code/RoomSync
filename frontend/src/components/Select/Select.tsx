import React, { useId } from 'react'

export interface SelectOption {
  value: string | number
  label: string
  disabled?: boolean
}

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  options: SelectOption[]
  error?: string
  helperText?: string
  placeholder?: string
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, options, error, helperText, placeholder, required, id, className = '', disabled, children, ...props }, ref) => {
    const generatedId = useId()
    const selectId = id || generatedId
    const errorId = `${selectId}-error`
    const helperId = `${selectId}-helper`

    const describedBy = [error ? errorId : null, helperText ? helperId : null]
      .filter(Boolean)
      .join(' ') || undefined

    return (
      <div className="w-full">
        {label && (
          <label htmlFor={selectId} className="block text-sm font-medium text-brand-navy mb-1 text-left">
            {label}
            {required && <span className="text-red-500 ml-1" aria-hidden="true">*</span>}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          disabled={disabled}
          required={required}
          aria-invalid={!!error}
          aria-describedby={describedBy}
          className={`block w-full rounded-md border px-3 py-2 text-sm text-brand-navy shadow-sm transition-colors bg-brand-white focus:outline-none focus:ring-2 focus:ring-brand-accent focus:border-brand-accent disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-slate-50 ${
            error ? 'border-red-500 focus:ring-red-500 focus:border-red-500' : 'border-brand-slate/30'
          } ${className}`}
          {...props}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((opt) => (
            <option key={String(opt.value)} value={opt.value} disabled={opt.disabled}>
              {opt.label}
            </option>
          ))}
          {children}
        </select>
        {error && (
          <p id={errorId} className="mt-1 text-xs text-red-600 text-left font-medium">
            {error}
          </p>
        )}
        {!error && helperText && (
          <p id={helperId} className="mt-1 text-xs text-brand-slate text-left">
            {helperText}
          </p>
        )}
      </div>
    )
  },
)

Select.displayName = 'Select'

export default Select
