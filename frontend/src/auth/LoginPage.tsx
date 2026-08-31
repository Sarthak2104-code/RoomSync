import React, { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import type { ApiError } from '@/types/api'
import { useAuth } from './AuthContext'

const loginSchema = z.object({
  wissenId: z
    .string()
    .min(1, 'Wissen ID is required')
    .regex(/^(WT|WI)[0-9]+$/i, 'Wissen ID must start with WT or WI followed by digits (e.g., WT5128, WI422)')
    .transform((val) => val.trim().toUpperCase()),
  password: z.string().min(1, 'Password is required'),
})

type LoginFormValues = z.infer<typeof loginSchema>

export const LoginPage: React.FC = () => {
  const { login, authenticated, role } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [authError, setAuthError] = useState<{ message: string; correlationId?: string } | null>(null)
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      wissenId: '',
      password: '',
    },
  })

  // If already authenticated, redirect
  React.useEffect(() => {
    if (authenticated) {
      const destination = location.state?.from?.pathname || (role === 'ADMIN' ? '/admin/dashboard' : '/dashboard')
      navigate(destination, { replace: true })
    }
  }, [authenticated, role, navigate, location.state])

  const onSubmit = async (data: LoginFormValues) => {
    setIsSubmitting(true)
    setAuthError(null)

    try {
      const response = await login(data)
      toast.success('Successfully logged in')
      const destination = location.state?.from?.pathname || (response.role === 'ADMIN' ? '/admin/dashboard' : '/dashboard')
      navigate(destination, { replace: true })
    } catch (err: unknown) {
      const apiErr = err as ApiError
      const safeMessage =
        apiErr.status === 401
          ? 'Invalid Wissen ID or password'
          : apiErr.message || 'Unable to sign in. Please try again.'

      setAuthError({
        message: safeMessage,
        correlationId: apiErr.correlationId,
      })
      toast.error(safeMessage)
    } finally {
      setIsSubmitting(false)
    }
  };

  return (
    <div className="min-h-screen bg-brand-light-gray flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="text-center">
          <h1 className="text-3xl font-bold tracking-tight text-brand-navy">RoomSync</h1>
          <p className="mt-2 text-sm text-brand-slate">Sign in to your account</p>
        </div>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-brand-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-brand-slate/20">
          {authError && (
            <div
              role="alert"
              className="mb-6 rounded-md bg-red-50 p-4 border border-red-200 text-sm text-red-700"
            >
              <p className="font-medium">{authError.message}</p>
              {authError.correlationId && (
                <p className="mt-1 text-xs text-red-500 font-mono">
                  Trace ID: {authError.correlationId}
                </p>
              )}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6" noValidate>
            <div>
              <label
                htmlFor="wissenId"
                className="block text-sm font-medium text-brand-navy text-left"
              >
                Wissen ID
              </label>
              <div className="mt-1">
                <input
                  id="wissenId"
                  type="text"
                  placeholder="e.g. WT5128, WI422"
                  autoComplete="username"
                  disabled={isSubmitting}
                  aria-invalid={!!errors.wissenId}
                  aria-describedby={errors.wissenId ? 'wissenId-error' : undefined}
                  {...register('wissenId')}
                  className={`block w-full rounded-md border px-3 py-2 text-brand-navy uppercase placeholder:normal-case shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-accent focus:border-brand-accent sm:text-sm ${
                    errors.wissenId ? 'border-red-500' : 'border-brand-slate/30'
                  }`}
                />
                {errors.wissenId && (
                  <p id="wissenId-error" className="mt-1 text-xs text-red-600 text-left">
                    {errors.wissenId.message}
                  </p>
                )}
              </div>
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-brand-navy text-left"
              >
                Password
              </label>
              <div className="mt-1">
                <input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  disabled={isSubmitting}
                  aria-invalid={!!errors.password}
                  aria-describedby={errors.password ? 'password-error' : undefined}
                  {...register('password')}
                  className={`block w-full rounded-md border px-3 py-2 text-brand-navy shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-accent focus:border-brand-accent sm:text-sm ${
                    errors.password ? 'border-red-500' : 'border-brand-slate/30'
                  }`}
                />
                {errors.password && (
                  <p id="password-error" className="mt-1 text-xs text-red-600 text-left">
                    {errors.password.message}
                  </p>
                )}
              </div>
            </div>

            <div>
              <button
                type="submit"
                disabled={isSubmitting}
                className="flex w-full justify-center rounded-md bg-brand-navy py-2.5 px-4 text-sm font-medium text-brand-white shadow-sm hover:bg-brand-navy/90 focus:outline-none focus:ring-2 focus:ring-brand-accent focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {isSubmitting ? (
                  <span className="flex items-center gap-2">
                    <span className="w-4 h-4 border-2 border-brand-white border-t-transparent rounded-full animate-spin" />
                    Signing in...
                  </span>
                ) : (
                  'Sign in'
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
