import { toast as sonnerToast } from 'sonner'

export interface ToastOptions {
  description?: string
  duration?: number
  action?: {
    label: string
    onClick: () => void
  }
}

/**
 * Standardized notification wrapper over Sonner toast system.
 */
export const toast = {
  success(message: string, options?: ToastOptions) {
    return sonnerToast.success(message, {
      description: options?.description,
      duration: options?.duration,
      action: options?.action
        ? {
            label: options.action.label,
            onClick: options.action.onClick,
          }
        : undefined,
    })
  },

  error(message: string, options?: ToastOptions) {
    return sonnerToast.error(message, {
      description: options?.description,
      duration: options?.duration,
      action: options?.action
        ? {
            label: options.action.label,
            onClick: options.action.onClick,
          }
        : undefined,
    })
  },

  warning(message: string, options?: ToastOptions) {
    return sonnerToast.warning(message, {
      description: options?.description,
      duration: options?.duration,
      action: options?.action
        ? {
            label: options.action.label,
            onClick: options.action.onClick,
          }
        : undefined,
    })
  },

  info(message: string, options?: ToastOptions) {
    return sonnerToast.info(message, {
      description: options?.description,
      duration: options?.duration,
      action: options?.action
        ? {
            label: options.action.label,
            onClick: options.action.onClick,
          }
        : undefined,
    })
  },

  dismiss(toastId?: string | number) {
    return sonnerToast.dismiss(toastId)
  },
}

export default toast
