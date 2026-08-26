import React, { useEffect, useId } from 'react'

export interface DialogProps {
  isOpen: boolean
  onClose: () => void
  title: React.ReactNode
  description?: React.ReactNode
  children?: React.ReactNode
  actions?: React.ReactNode
  size?: 'sm' | 'md' | 'lg'
  role?: 'dialog' | 'alertdialog'
}

export const Dialog: React.FC<DialogProps> = ({
  isOpen,
  onClose,
  title,
  description,
  children,
  actions,
  size = 'md',
  role = 'dialog',
}) => {
  const generatedId = useId()
  const titleId = `${generatedId}-dialog-title`
  const descriptionId = `${generatedId}-dialog-desc`

  useEffect(() => {
    if (!isOpen) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => {
      document.body.style.overflow = ''
    }
  }, [isOpen])

  if (!isOpen) return null

  const sizeStyles = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
  }[size]

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto bg-black/50 backdrop-blur-xs animate-in fade-in duration-200"
      role={role}
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
    >
      <div
        className={`relative w-full ${sizeStyles} bg-brand-white rounded-lg shadow-xl border border-brand-slate/20 p-6 text-left my-8 overflow-hidden`}
        onClick={(e) => e.stopPropagation()}
      >
        <div>
          <h3 id={titleId} className="text-lg font-bold text-brand-navy">
            {title}
          </h3>
          {description && (
            <p id={descriptionId} className="mt-2 text-sm text-brand-slate leading-relaxed">
              {description}
            </p>
          )}
        </div>

        {children && <div className="mt-4">{children}</div>}

        {actions && <div className="mt-6 flex flex-wrap justify-end gap-3">{actions}</div>}
      </div>
    </div>
  )
}

export default Dialog
