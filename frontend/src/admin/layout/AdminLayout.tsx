import React, { useState } from 'react'
import { Outlet } from 'react-router-dom'
import AdminHeader from './AdminHeader'
import AdminSidebar from './AdminSidebar'

export const AdminLayout: React.FC = () => {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)

  const toggleSidebar = () => {
    setIsSidebarOpen((prev) => !prev)
  }

  const closeSidebar = () => {
    setIsSidebarOpen(false)
  }

  return (
    <div className="min-h-screen bg-slate-50 flex antialiased">
      {/* Admin Sidebar (Fixed on mobile / Sticky full-height on desktop) */}
      <AdminSidebar isOpen={isSidebarOpen} onClose={closeSidebar} />

      {/* Right Column: Top Admin Header + Page Content */}
      <div className="flex-1 flex flex-col min-w-0 min-h-screen">
        {/* Top Admin Header */}
        <AdminHeader onToggleSidebar={toggleSidebar} isSidebarOpen={isSidebarOpen} />

        {/* Content Area */}
        <main className="flex-1 w-full min-w-0 px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
          <div className="max-w-7xl mx-auto">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}

export default AdminLayout
