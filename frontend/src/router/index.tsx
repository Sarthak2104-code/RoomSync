import { createBrowserRouter, Navigate } from 'react-router-dom'
import AppLayout from '@/layouts/AppLayout'
import LoginPage from '@/auth/LoginPage'
import ProtectedRoute from '@/auth/ProtectedRoute'
import RoleGuard from '@/auth/RoleGuard'
import LocationListPage from '@/rooms/LocationListPage'
import RoomSearchPage from '@/rooms/RoomSearchPage'
import RoomDetailsPage from '@/rooms/RoomDetailsPage'
import AdminPlaceholderPage from '@/auth/AdminPlaceholderPage'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      {
        path: '/rooms',
        element: <RoomSearchPage />,
      },
      {
        path: '/rooms/:id',
        element: <RoomDetailsPage />,
      },
      {
        path: '/locations',
        element: <LocationListPage />,
      },
      {
        path: '/user',
        element: <Navigate to="/rooms" replace />,
      },
      {
        path: '/admin',
        element: (
          <RoleGuard allowedRoles={['ADMIN']}>
            <AdminPlaceholderPage />
          </RoleGuard>
        ),
      },
      {
        path: '/',
        element: <Navigate to="/rooms" replace />,
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/rooms" replace />,
  },
])

export default router
