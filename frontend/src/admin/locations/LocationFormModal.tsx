import React, { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/api/client'
import { locationAdminService } from '../api/locationAdminService'
import { locationSchema, type LocationFormData } from '../schemas/locationSchema'
import Modal from '@/components/Modal/Modal'
import Input from '@/components/Input/Input'
import Button from '@/components/Button/Button'

interface LocationFormModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess: (location: LocationResponse) => void
  initialData?: LocationResponse | null
}

export const LocationFormModal: React.FC<LocationFormModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  initialData,
}) => {
  const isEditMode = Boolean(initialData)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<LocationFormData>({
    resolver: zodResolver(locationSchema),
    defaultValues: {
      name: '',
      code: '',
    },
  })

  // Synchronize form values whenever initialData or isOpen changes
  useEffect(() => {
    if (isOpen) {
      setServerError(null)
      if (initialData) {
        reset({
          name: initialData.name,
          code: initialData.code,
        })
      } else {
        reset({
          name: '',
          code: '',
        })
      }
    }
  }, [isOpen, initialData, reset])

  const onSubmit = async (data: LocationFormData) => {
    setSubmitting(true)
    setServerError(null)

    try {
      if (isEditMode && initialData) {
        const updated = await locationAdminService.updateLocation(initialData.id, {
          name: data.name.trim(),
          code: data.code.trim().toUpperCase(),
        })
        toast.success(`Location "${updated.name}" updated successfully.`)
        onSuccess(updated)
        onClose()
      } else {
        const created = await locationAdminService.createLocation({
          name: data.name.trim(),
          code: data.code.trim().toUpperCase(),
        })
        toast.success(`Location "${created.name}" created successfully.`)
        onSuccess(created)
        onClose()
      }
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message =
        apiErr.message ||
        (isEditMode ? 'Failed to update location.' : 'Failed to create location.')
      setServerError(message)
      toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? () => {} : onClose}
      title={isEditMode ? 'Edit Location' : 'Create New Location'}
      description={
        isEditMode
          ? `Update configuration for location #${initialData?.id}`
          : 'Add a new physical office or campus location to RoomSync.'
      }
      size="md"
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-2">
        {/* Backend Error Alert */}
        {serverError && (
          <div
            role="alert"
            className="p-3.5 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 flex items-start gap-2"
          >
            <svg
              className="w-4 h-4 text-red-500 shrink-0 mt-0.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <div className="flex-1 font-medium">{serverError}</div>
          </div>
        )}

        {/* Location Name */}
        <Input
          label="Location Name"
          placeholder="e.g. Mumbai Headquarters, New York Tech Hub"
          required
          autoFocus
          disabled={submitting}
          error={errors.name?.message}
          helperText="Between 2 and 100 characters."
          {...register('name')}
        />

        {/* Location Code */}
        <Input
          label="Location Code"
          placeholder="e.g. MUM, NYC, LON_HQ"
          required
          disabled={submitting}
          error={errors.code?.message}
          helperText="Unique uppercase identifier (2-20 characters: letters, numbers, hyphens, underscores)."
          {...register('code')}
        />

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={submitting}
            disabled={submitting || (isEditMode && !isDirty)}
          >
            {isEditMode ? 'Save Changes' : 'Create Location'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

export default LocationFormModal
