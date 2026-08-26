import React from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'

export const AppLayout: React.FC = () => {
  const { user, role, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-2 rounded-md text-sm font-medium transition-colors ${
      isActive
        ? 'bg-brand-navy text-brand-white'
        : 'text-brand-slate hover:text-brand-navy hover:bg-slate-100'
    }`

  return (
    <div className="min-h-screen bg-brand-light-gray flex flex-col">
      {/* Navigation Header */}
      <header className="bg-brand-white border-b border-brand-slate/20 sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center gap-8">
              <Link to="/dashboard" className="flex items-center gap-2">
                <span className="text-xl font-bold tracking-tight text-brand-navy">RoomSync</span>
              </Link>
              <nav className="flex space-x-2" aria-label="Main Navigation">
                <NavLink to="/dashboard" className={navLinkClass}>
                  Dashboard
                </NavLink>
                <NavLink to="/rooms" className={navLinkClass} end>
                  Rooms
                </NavLink>
                <NavLink to="/locations" className={navLinkClass}>
                  Locations
                </NavLink>
                <NavLink to="/my-bookings" className={navLinkClass}>
                  My Bookings
                </NavLink>
              </nav>
            </div>

            <div className="flex items-center gap-4">
              {role === 'ADMIN' && (
                <Link
                  to="/admin"
                  className="hidden md:inline-flex items-center gap-1.5 text-xs font-semibold text-amber-800 bg-amber-50 border border-amber-300 px-2.5 py-1.5 rounded-md hover:bg-amber-100 transition-colors shadow-2xs"
                  title="Switch to Admin Console"
                >
                  <svg className="w-3.5 h-3.5 text-amber-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                  Admin Panel
                </Link>
              )}
              <div className="hidden sm:flex flex-col items-end text-xs">
                <span className="font-medium text-brand-navy">{user?.email}</span>
                <span className="text-brand-slate">Role: {role}</span>
              </div>
              <Badge variant={role === 'ADMIN' ? 'warning' : 'neutral'} size="sm">
                {role}
              </Badge>
              <Button variant="outline" size="sm" onClick={handleLogout}>
                Sign out
              </Button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Outlet />
      </main>
    </div>
  )
}

export default AppLayout
