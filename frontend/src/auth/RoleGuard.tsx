import React from 'react'
import { Link, Outlet } from 'react-router-dom'
import type { UserRole } from '@/types/auth'
import { useAuth } from './AuthContext'

interface RoleGuardProps {
  allowedRoles: UserRole[]
  children?: React.ReactNode
}

export const RoleGuard: React.FC<RoleGuardProps> = ({ allowedRoles, children }) => {
  const { role, user } = useAuth()

  if (!role || !allowedRoles.includes(role)) {
    return (
      <div className="min-h-screen bg-brand-light-gray flex flex-col items-center justify-center p-6 text-brand-navy">
        <div className="bg-brand-white p-8 rounded-lg shadow-md max-w-md w-full text-center border border-brand-slate/20">
          <div className="w-12 h-12 bg-red-100 text-red-600 rounded-full flex items-center justify-center mx-auto mb-4 text-xl font-bold">
            !
          </div>
          <h1 className="text-xl font-bold text-brand-navy mb-2">Access Denied (403)</h1>
          <p className="text-brand-slate text-sm mb-6">
            Your account ({user?.email} with role: <span className="font-semibold">{role || 'UNKNOWN'}</span>) does not have permission to view this page.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link
              to="/user"
              className="inline-flex justify-center items-center px-4 py-2 text-sm font-medium rounded-md bg-brand-navy text-brand-white hover:bg-brand-navy/90 transition-colors"
            >
              Return to User Dashboard
            </Link>
          </div>
        </div>
      </div>
    )
  }

  return children ? <>{children}</> : <Outlet />
}

export default RoleGuard
