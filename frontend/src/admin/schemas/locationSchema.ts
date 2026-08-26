import { z } from 'zod'

export const locationSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Location name is required')
    .min(2, 'Location name must be between 2 and 100 characters')
    .max(100, 'Location name must be between 2 and 100 characters'),
  code: z
    .string()
    .trim()
    .min(1, 'Location code is required')
    .min(2, 'Location code must be between 2 and 20 characters')
    .max(20, 'Location code must be between 2 and 20 characters')
    .regex(
      /^[A-Za-z0-9_-]+$/,
      'Location code must contain only alphanumeric characters, underscores, or hyphens',
    ),
})

export type LocationFormData = z.infer<typeof locationSchema>
