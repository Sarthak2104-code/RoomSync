import { createBrowserRouter, Navigate } from 'react-router-dom'
import AppLayout from '@/layouts/AppLayout'
import LoginPage from '@/auth/LoginPage'
import ProtectedRoute from '@/auth/ProtectedRoute'
import RoleGuard from '@/auth/RoleGuard'
import UserDashboardPage from '@/dashboard/UserDashboardPage'
import LocationListPage from '@/rooms/LocationListPage'
import RoomSearchPage from '@/rooms/RoomSearchPage'
import RoomDetailsPage from '@/rooms/RoomDetailsPage'
import BookingFormPage from '@/bookings/BookingFormPage'
import MyBookingsPage from '@/bookings/MyBookingsPage'
import RescheduleBookingPage from '@/bookings/RescheduleBookingPage'
import RecurringBookingPage from '@/recurring/RecurringBookingPage'

import {
  AdminLayout,
  AdminDashboardPage,
  AdminLocationsPage,
  AdminRoomsPage,
  AdminBookingsPage,
  AdminOccupancyPage,
  AdminAnalyticsPage,
  AdminAmenitiesPage,
  AdminRequestsPage,
  AdminAuditPage,
} from '@/admin'

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
        path: '/dashboard',
        element: <UserDashboardPage />,
      },
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
        path: '/rooms/:roomId/book/review',
        element: <BookingFormPage />,
      },
      {
        path: '/rooms/:roomId/recurring',
        element: <RecurringBookingPage />,
      },
      {
        path: '/rooms/:roomId/recurring/:seriesId',
        element: <RecurringBookingPage />,
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
        path: '/bookings/recurring',
        element: <RecurringBookingPage />,
      },
      {
        path: '/bookings/recurring/:seriesId',
        element: <RecurringBookingPage />,
      },
      {
        path: '/recurring/:seriesId',
        element: <RecurringBookingPage />,
      },
      {
        path: '/user',
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: '/',
        element: <Navigate to="/dashboard" replace />,
      },
    ],
  },
  {
    path: '/admin',
    element: (
      <ProtectedRoute>
        <RoleGuard allowedRoles={['ADMIN']}>
          <AdminLayout />
        </RoleGuard>
      </ProtectedRoute>
    ),
    children: [
      {
        index: true,
        element: <AdminDashboardPage />,
      },
      {
        path: 'dashboard',
        element: <Navigate to="/admin" replace />,
      },
      {
        path: 'locations',
        element: <AdminLocationsPage />,
      },
      {
        path: 'rooms',
        element: <AdminRoomsPage />,
      },
      {
        path: 'bookings',
        element: <AdminBookingsPage />,
      },
      {
        path: 'occupancy',
        element: <AdminOccupancyPage />,
      },
      {
        path: 'analytics',
        element: <AdminAnalyticsPage />,
      },
      {
        path: 'amenities',
        element: <AdminAmenitiesPage />,
      },
      {
        path: 'requests',
        element: <AdminRequestsPage />,
      },
      {
        path: 'audit',
        element: <AdminAuditPage />,
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/dashboard" replace />,
  },
])

export default router
