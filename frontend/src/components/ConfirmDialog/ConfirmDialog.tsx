import React from 'react'
import Button from '../Button/Button'
import Dialog from '../Dialog/Dialog'

export interface ConfirmDialogProps {
  isOpen: boolean
  title: string
  description: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: 'primary' | 'danger'
  loading?: boolean
  disabled?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  isOpen,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'primary',
  loading = false,
  disabled = false,
  onConfirm,
  onCancel,
}) => {
  return (
    <Dialog
      isOpen={isOpen}
      onClose={loading ? () => {} : onCancel}
      title={title}
      description={description}
      role="alertdialog"
      size="sm"
      actions={
        <>
          <Button
            variant="outline"
            onClick={onCancel}
            disabled={disabled || loading}
          >
            {cancelLabel}
          </Button>
          <Button
            variant={variant === 'danger' ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
            disabled={disabled || loading}
          >
            {confirmLabel}
          </Button>
        </>
      }
    />
  )
}

export default ConfirmDialog
