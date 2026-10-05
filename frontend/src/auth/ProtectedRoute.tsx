import React from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

interface ProtectedRouteProps {
  children?: React.ReactNode
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
  const { authenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <div className="min-h-screen bg-brand-light-gray flex items-center justify-center">
        <div className="text-center">
          <div className="w-10 h-10 border-4 border-brand-navy border-t-transparent rounded-full animate-spin mx-auto mb-3" />
          <p className="text-brand-slate text-sm font-medium">Initializing session...</p>
        </div>
      </div>
    )
  }

  if (!authenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return children ? <>{children}</> : <Outlet />
}

export default ProtectedRoute
