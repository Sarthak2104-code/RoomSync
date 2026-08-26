import { createBrowserRouter, Navigate } from 'react-router-dom'
import LoginPage from '@/auth/LoginPage'
import ProtectedRoute from '@/auth/ProtectedRoute'
import RoleGuard from '@/auth/RoleGuard'
import UserPlaceholderPage from '@/auth/UserPlaceholderPage'
import AdminPlaceholderPage from '@/auth/AdminPlaceholderPage'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/user',
    element: (
      <ProtectedRoute>
        <RoleGuard allowedRoles={['USER', 'ADMIN']}>
          <UserPlaceholderPage />
        </RoleGuard>
      </ProtectedRoute>
    ),
  },
  {
    path: '/admin',
    element: (
      <ProtectedRoute>
        <RoleGuard allowedRoles={['ADMIN']}>
          <AdminPlaceholderPage />
        </RoleGuard>
      </ProtectedRoute>
    ),
  },
  {
    path: '/',
    element: <Navigate to="/user" replace />,
  },
  {
    path: '*',
    element: <Navigate to="/user" replace />,
  },
])

export default router
