import React, { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import type { RoomResponse } from '@/types/room'
import type { LocationResponse } from '@/types/location'
import type { ApiError } from '@/api/client'
import { roomAdminService } from '../api/roomAdminService'
import { roomSchema, type RoomFormData } from '../schemas/roomSchema'
import Modal from '@/components/Modal/Modal'
import Input from '@/components/Input/Input'
import Select from '@/components/Select/Select'
import Button from '@/components/Button/Button'

interface RoomFormModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess: (room: RoomResponse) => void
  initialData?: RoomResponse | null
  locations: LocationResponse[]
}

export const RoomFormModal: React.FC<RoomFormModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  initialData,
  locations,
}) => {
  const isEditMode = Boolean(initialData)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<RoomFormData>({
    resolver: zodResolver(roomSchema),
    defaultValues: {
      locationId: locations[0]?.id || 1,
      name: '',
      capacity: 10,
      description: '',
    },
  })

  // Synchronize form values whenever initialData or isOpen changes
  useEffect(() => {
    if (isOpen) {
      setServerError(null)
      if (initialData) {
        reset({
          locationId: initialData.location?.id || 1,
          name: initialData.name,
          capacity: initialData.capacity,
          description: initialData.description || '',
        })
      } else {
        reset({
          locationId: locations[0]?.id || 1,
          name: '',
          capacity: 10,
          description: '',
        })
      }
    }
  }, [isOpen, initialData, locations, reset])

  const onSubmit = async (data: RoomFormData) => {
    setSubmitting(true)
    setServerError(null)

    try {
      if (isEditMode && initialData) {
        const updated = await roomAdminService.updateRoom(initialData.id, {
          name: data.name.trim(),
          capacity: Number(data.capacity),
          description: data.description?.trim() || undefined,
        })
        toast.success(`Room "${updated.name}" updated successfully.`)
        onSuccess(updated)
        onClose()
      } else {
        const created = await roomAdminService.createRoom({
          locationId: Number(data.locationId),
          name: data.name.trim(),
          capacity: Number(data.capacity),
          description: data.description?.trim() || undefined,
        })
        toast.success(`Room "${created.name}" created successfully.`)
        onSuccess(created)
        onClose()
      }
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const message =
        apiErr.message ||
        (isEditMode ? 'Failed to update room.' : 'Failed to create room.')
      setServerError(message)
      toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  const locationOptions = locations.map((loc) => ({
    value: String(loc.id),
    label: `${loc.name} (${loc.code})`,
  }))

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? () => {} : onClose}
      title={isEditMode ? 'Edit Meeting Room' : 'Create Meeting Room'}
      description={
        isEditMode
          ? `Update configuration for room #${initialData?.id}`
          : 'Add a new physical meeting space to a campus location.'
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

        {/* Location Dropdown */}
        <div>
          <label className="block text-sm font-medium text-brand-navy mb-1 text-left">
            Location <span className="text-red-500">*</span>
          </label>
          {isEditMode ? (
            <div className="px-3 py-2 text-sm bg-slate-100 border border-brand-slate/20 rounded-md text-brand-navy font-medium">
              {initialData?.location?.name} ({initialData?.location?.code})
            </div>
          ) : (
            <Select
              options={locationOptions}
              disabled={submitting || locationOptions.length === 0}
              error={errors.locationId?.message}
              {...register('locationId')}
            />
          )}
          {errors.locationId && (
            <p className="mt-1 text-xs text-red-600 font-medium">
              {errors.locationId.message}
            </p>
          )}
        </div>

        {/* Room Name */}
        <Input
          label="Room Name"
          placeholder="e.g. Conference Alpha, Boardroom 3A"
          required
          autoFocus={!isEditMode}
          disabled={submitting}
          error={errors.name?.message}
          helperText="Unique name within the assigned location (up to 255 characters)."
          {...register('name')}
        />

        {/* Seating Capacity */}
        <Input
          label="Seating Capacity"
          type="number"
          min={1}
          max={1000}
          placeholder="e.g. 10"
          required
          disabled={submitting}
          error={errors.capacity?.message}
          helperText="Maximum seating capacity (must be greater than 0)."
          {...register('capacity')}
        />

        {/* Description */}
        <div>
          <label className="block text-sm font-medium text-brand-navy mb-1 text-left">
            Description <span className="text-xs text-brand-slate font-normal">(Optional)</span>
          </label>
          <textarea
            rows={3}
            placeholder="e.g. Equipped with 4K display, whiteboard, video conferencing system."
            disabled={submitting}
            className="block w-full rounded-md border border-brand-slate/30 px-3 py-2 text-sm text-brand-navy shadow-xs placeholder:text-slate-400 focus:outline-hidden focus:ring-2 focus:ring-brand-accent focus:border-brand-accent disabled:opacity-50 disabled:bg-slate-50 transition-colors"
            {...register('description')}
          />
        </div>

        {/* Actions */}
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
            {isEditMode ? 'Save Changes' : 'Create Room'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

export default RoomFormModal
