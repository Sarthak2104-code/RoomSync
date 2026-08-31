import React, { useEffect, useState } from 'react'
import { NavLink, Link } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { adminRequestService } from '../api/adminRequestService'

interface AdminSidebarProps {
  isOpen?: boolean
  onClose?: () => void
}

export const AdminSidebar: React.FC<AdminSidebarProps> = ({ isOpen, onClose }) => {
  const { user } = useAuth()
  const [openRequestsCount, setOpenRequestsCount] = useState<number | null>(null)

  useEffect(() => {
    adminRequestService.getAdminRequests({ status: 'OPEN', size: 1 })
      .then((res) => {
        if (res && typeof res.totalElements === 'number') {
          setOpenRequestsCount(res.totalElements)
        }
      })
      .catch(() => {
        // Fallback gracefully
      })
  }, [])

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-all ${
      isActive
        ? 'bg-blue-600 text-white font-semibold shadow-xs'
        : 'text-slate-300 hover:text-white hover:bg-slate-800/70'
    }`

  const adminDisplayName = user?.name || (user?.email ? user.email.split('@')[0] : 'Admin User')
  const adminInitial = adminDisplayName.charAt(0).toUpperCase()

  return (
    <>
      {/* Mobile Backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-slate-950/60 z-40 lg:hidden backdrop-blur-xs transition-opacity"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={`fixed lg:sticky top-0 bottom-0 left-0 z-40 w-64 h-screen bg-[#0B1528] text-slate-200 border-r border-slate-800/80 flex flex-col transition-transform duration-300 ease-in-out shrink-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
        }`}
        aria-label="Admin Sidebar Navigation"
      >
        {/* Brand Logo Header (Intersection of Header & Sidebar) */}
        <div className="h-16 px-5 border-b border-slate-800/80 flex items-center justify-between shrink-0">
          <Link to="/admin/dashboard" className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-lg bg-blue-600 flex items-center justify-center text-white font-bold text-sm shadow-xs">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
            <div className="flex flex-col">
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-lg text-white tracking-tight leading-none">RoomSync</span>
                <span className="text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800/60">
                  AI
                </span>
              </div>
              <span className="text-[10px] font-medium text-slate-400 mt-0.5">Admin Panel</span>
            </div>
          </Link>

          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-slate-800 lg:hidden"
            aria-label="Close sidebar"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Scrollable Navigation Groups */}
        <div className="flex-1 px-3 py-4 space-y-5 overflow-y-auto">
          {/* GROUP 1: OVERVIEW */}
          <div className="space-y-1">
            <p className="px-3 text-[10px] font-bold text-slate-400/80 uppercase tracking-wider mb-1.5">
              Overview
            </p>
            <NavLink to="/admin/dashboard" end onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
              </svg>
              <span>Dashboard</span>
            </NavLink>

            <NavLink to="/admin/analytics" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
              </svg>
              <span>Analytics</span>
            </NavLink>
          </div>

          {/* GROUP 2: BOOKING MANAGEMENT */}
          <div className="space-y-1">
            <p className="px-3 text-[10px] font-bold text-slate-400/80 uppercase tracking-wider mb-1.5">
              Booking Management
            </p>
            <NavLink to="/admin/bookings" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <span>All Bookings</span>
            </NavLink>

            <NavLink to="/bookings/recurring" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              <span>Recurring Bookings</span>
            </NavLink>
          </div>

          {/* GROUP 3: WORKSPACE */}
          <div className="space-y-1">
            <p className="px-3 text-[10px] font-bold text-slate-400/80 uppercase tracking-wider mb-1.5">
              Workspace
            </p>
            <NavLink to="/admin/locations" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              <span>Locations</span>
            </NavLink>

            <NavLink to="/admin/rooms" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
              <span>Rooms</span>
            </NavLink>

            <NavLink to="/admin/amenities" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
              </svg>
              <span>Amenities</span>
            </NavLink>
          </div>

          {/* GROUP 4: USER MANAGEMENT */}
          <div className="space-y-1">
            <p className="px-3 text-[10px] font-bold text-slate-400/80 uppercase tracking-wider mb-1.5">
              User Management
            </p>
            <NavLink to="/admin/users" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <span>Users</span>
            </NavLink>
          </div>

          {/* GROUP 5: SUPPORT & CONTROL */}
          <div className="space-y-1">
            <p className="px-3 text-[10px] font-bold text-slate-400/80 uppercase tracking-wider mb-1.5">
              Support &amp; Control
            </p>
            <NavLink to="/admin/requests" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              <span className="flex-1">Admin Requests</span>
              {openRequestsCount != null && openRequestsCount > 0 && (
                <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-rose-600 text-white">
                  {openRequestsCount}
                </span>
              )}
            </NavLink>

            <NavLink to="/admin/audit" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span>Audit Logs</span>
            </NavLink>

            <NavLink to="/admin/profile" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
              <span>Profile</span>
            </NavLink>

            <NavLink to="/admin/settings" onClick={onClose} className={navLinkClass}>
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              <span>Settings</span>
            </NavLink>
          </div>
        </div>

        {/* BOTTOM Admin Profile Box */}
        <div className="p-3 border-t border-slate-800/80 bg-[#091122]">
          <Link
            to="/admin/profile"
            onClick={onClose}
            className="group flex items-center justify-between p-2 rounded-xl bg-slate-900/60 hover:bg-slate-800/80 border border-slate-800 transition-all"
          >
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-8 h-8 rounded-full bg-blue-600 text-white flex items-center justify-center font-bold text-xs shadow-xs shrink-0">
                {adminInitial}
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-100 truncate">{adminDisplayName}</p>
                <p className="text-[10px] text-slate-400 truncate">Administrator</p>
              </div>
            </div>
            <svg className="w-4 h-4 text-slate-500 group-hover:text-white transition-colors shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>

          <Link
            to="/dashboard"
            onClick={onClose}
            className="flex items-center justify-center gap-2 w-full mt-2 px-3 py-1.5 text-xs font-semibold text-slate-300 hover:text-white bg-slate-800/50 rounded-lg hover:bg-slate-800 transition-colors"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            <span>Switch to User Portal</span>
          </Link>
        </div>
      </aside>
    </>
  )
}

export default AdminSidebar
