import { z } from 'zod'

export const roomSchema = z.object({
  locationId: z.coerce
    .number({
      required_error: 'Location is required',
      invalid_type_error: 'Please select a location',
    })
    .positive('Please select a valid location'),
  name: z
    .string()
    .trim()
    .min(1, 'Room name is required and cannot be blank')
    .max(255, 'Room name cannot exceed 255 characters'),
  capacity: z.coerce
    .number({
      required_error: 'Capacity is required',
      invalid_type_error: 'Capacity must be a number',
    })
    .int('Capacity must be an integer')
    .positive('Capacity must be a positive integer greater than zero'),
  description: z.string().trim().optional(),
})

export type RoomFormData = z.infer<typeof roomSchema>
