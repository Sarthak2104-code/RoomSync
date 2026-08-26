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
              <Link to="/rooms" className="flex items-center gap-2">
                <span className="text-xl font-bold tracking-tight text-brand-navy">RoomSync</span>
              </Link>
              <nav className="flex space-x-2" aria-label="Main Navigation">
                <NavLink to="/rooms" className={navLinkClass} end>
                  Rooms
                </NavLink>
                <NavLink to="/locations" className={navLinkClass}>
                  Locations
                </NavLink>
              </nav>
            </div>

            <div className="flex items-center gap-4">
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
