import React, { useState } from 'react'
import { Outlet } from 'react-router-dom'
import UserHeader from './UserHeader'
import UserSidebar from './UserSidebar'
import ContactAdminModal from '@/components/ContactAdminModal/ContactAdminModal'
import SettingsModal from '@/components/SettingsModal/SettingsModal'
import AskRoomSyncButton from '@/components/AskRoomSync/AskRoomSyncButton'

export const AppLayout: React.FC = () => {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)
  const [isContactAdminOpen, setIsContactAdminOpen] = useState(false)
  const [isSettingsOpen, setIsSettingsOpen] = useState(false)

  const toggleSidebar = () => {
    setIsSidebarOpen((prev) => !prev)
  }

  const closeSidebar = () => {
    setIsSidebarOpen(false)
  }

  return (
    <div className="min-h-screen bg-slate-50 flex antialiased">
      {/* User Sidebar (Fixed on mobile / Sticky full-height on desktop) */}
      <UserSidebar
        isOpen={isSidebarOpen}
        onClose={closeSidebar}
        onOpenContactAdmin={() => setIsContactAdminOpen(true)}
        onOpenSettings={() => setIsSettingsOpen(true)}
      />

      {/* Right Column: Top Header + Page Content */}
      <div className="flex-1 flex flex-col min-w-0 min-h-screen">
        {/* Top Header */}
        <UserHeader onToggleSidebar={toggleSidebar} isSidebarOpen={isSidebarOpen} />

        {/* Main Content Area */}
        <main className="flex-1 w-full min-w-0 px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
          <div className="max-w-7xl mx-auto">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Modals & Floating AI Button */}
      <ContactAdminModal
        isOpen={isContactAdminOpen}
        onClose={() => setIsContactAdminOpen(false)}
      />
      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
      />
      <AskRoomSyncButton />
    </div>
  )
}

export default AppLayout
