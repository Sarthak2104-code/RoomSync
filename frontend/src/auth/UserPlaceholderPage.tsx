import React from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

export const UserPlaceholderPage: React.FC = () => {
  const { user, role, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-brand-light-gray flex flex-col items-center justify-center p-6 text-brand-navy">
      <div className="bg-brand-white p-8 rounded-lg shadow-md max-w-md w-full text-center border border-brand-slate/20">
        <h1 className="text-2xl font-bold text-brand-navy mb-2">User Protected Area</h1>
        <p className="text-brand-slate text-sm mb-4">
          Signed in as <span className="font-semibold text-brand-navy">{user?.email}</span>
        </p>
        <div className="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-brand-navy text-brand-white mb-6">
          Role: {role}
        </div>
        <div>
          <button
            onClick={handleLogout}
            className="w-full py-2 px-4 border border-brand-slate/30 rounded-md text-sm font-medium text-brand-navy hover:bg-brand-light-gray transition-colors"
          >
            Sign out
          </button>
        </div>
      </div>
    </div>
  )
}

export default UserPlaceholderPage
