import React, { useId } from 'react'

export interface DatePickerProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: string
  error?: string
  helperText?: string
}

export const DatePicker = React.forwardRef<HTMLInputElement, DatePickerProps>(
  ({ label, error, helperText, required, id, className = '', disabled, min, max, ...props }, ref) => {
    const generatedId = useId()
    const inputId = id || generatedId
    const errorId = `${inputId}-error`
    const helperId = `${inputId}-helper`

    const describedBy = [error ? errorId : null, helperText ? helperId : null]
      .filter(Boolean)
      .join(' ') || undefined

    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="block text-sm font-medium text-brand-navy mb-1 text-left">
            {label}
            {required && <span className="text-red-500 ml-1" aria-hidden="true">*</span>}
          </label>
        )}
        <input
          ref={ref}
          type="date"
          id={inputId}
          min={min}
          max={max}
          disabled={disabled}
          required={required}
          aria-invalid={!!error}
          aria-describedby={describedBy}
          className={`block w-full rounded-md border px-3 py-2 text-sm text-brand-navy shadow-sm transition-colors bg-brand-white focus:outline-none focus:ring-2 focus:ring-brand-accent focus:border-brand-accent disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-slate-50 ${
            error ? 'border-red-500 focus:ring-red-500 focus:border-red-500' : 'border-brand-slate/30'
          } ${className}`}
          {...props}
        />
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

DatePicker.displayName = 'DatePicker'

export default DatePicker
