import { createBrowserRouter, Navigate } from 'react-router-dom'
import AppLayout from '@/layouts/AppLayout'
import LoginPage from '@/auth/LoginPage'
import ProtectedRoute from '@/auth/ProtectedRoute'
import RoleGuard from '@/auth/RoleGuard'
import LocationListPage from '@/rooms/LocationListPage'
import RoomSearchPage from '@/rooms/RoomSearchPage'
import RoomDetailsPage from '@/rooms/RoomDetailsPage'
import BookingFormPage from '@/bookings/BookingFormPage'
import MyBookingsPage from '@/bookings/MyBookingsPage'
import RescheduleBookingPage from '@/bookings/RescheduleBookingPage'
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
        path: '/rooms/:roomId/book',
        element: <BookingFormPage />,
      },
      {
        path: '/locations',
        element: <LocationListPage />,
      },
      {
        path: '/my-bookings',
        element: <MyBookingsPage />,
      },
      {
        path: '/bookings/:id/reschedule',
        element: <RescheduleBookingPage />,
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
