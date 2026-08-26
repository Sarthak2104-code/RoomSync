import React from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'

interface AdminHeaderProps {
  onToggleSidebar?: () => void
  isSidebarOpen?: boolean
}

export const AdminHeader: React.FC<AdminHeaderProps> = ({ onToggleSidebar, isSidebarOpen }) => {
  const { user, role, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const currentDate = new Date().toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })

  return (
    <header className="bg-brand-white border-b border-brand-slate/20 sticky top-0 z-30 shadow-xs">
      <div className="px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16 items-center">
          {/* Brand & Mobile Toggle */}
          <div className="flex items-center gap-3 sm:gap-4">
            <button
              type="button"
              onClick={onToggleSidebar}
              className="lg:hidden p-2 rounded-md text-brand-slate hover:text-brand-navy hover:bg-slate-100 focus:outline-hidden focus:ring-2 focus:ring-brand-navy"
              aria-label={isSidebarOpen ? 'Close sidebar' : 'Open sidebar'}
              aria-expanded={isSidebarOpen}
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                {isSidebarOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                )}
              </svg>
            </button>

            <Link to="/admin" className="flex items-center gap-2.5 group">
              <div className="w-9 h-9 rounded-lg bg-brand-navy flex items-center justify-center text-brand-white font-bold shadow-xs">
                <svg className="w-5 h-5 text-blue-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
              <div className="flex flex-col">
                <div className="flex items-center gap-2">
                  <span className="text-lg font-bold tracking-tight text-brand-navy">RoomSync</span>
                  <span className="text-xs font-semibold px-2 py-0.5 rounded bg-brand-navy text-brand-white tracking-wide uppercase">
                    Admin
                  </span>
                </div>
                <span className="text-xs text-brand-slate font-medium hidden sm:inline">Administration Console</span>
              </div>
            </Link>
          </div>

          {/* Context & Admin Controls */}
          <div className="flex items-center gap-3 sm:gap-5">
            <div className="hidden md:flex items-center gap-2 text-xs text-brand-slate bg-slate-50 border border-slate-200 px-3 py-1.5 rounded-md font-medium">
              <svg className="w-4 h-4 text-brand-slate" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <span>{currentDate}</span>
            </div>

            <div className="hidden sm:flex flex-col items-end text-xs">
              <span className="font-semibold text-brand-navy">{user?.email || 'admin@roomsync.com'}</span>
              <span className="text-brand-slate">Role: {role || 'ADMIN'}</span>
            </div>

            <Badge variant="warning" size="sm" className="font-bold">
              {role || 'ADMIN'}
            </Badge>

            <Link
              to="/dashboard"
              className="hidden lg:inline-flex items-center gap-1.5 text-xs font-medium text-brand-slate hover:text-brand-navy border border-brand-slate/30 px-2.5 py-1.5 rounded-md hover:bg-slate-50 transition-colors"
              title="Switch to user booking dashboard"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
              User Portal
            </Link>

            <Button variant="outline" size="sm" onClick={handleLogout} className="text-xs">
              Sign out
            </Button>
          </div>
        </div>
      </div>
    </header>
  )
}

export default AdminHeader
