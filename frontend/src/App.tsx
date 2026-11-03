import { useState } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { AuthProvider } from '@/auth/AuthContext';
import { ProtectedRoute, PublicOnlyRoute } from '@/auth/ProtectedRoute';
import { AppLayout } from '@/components/layout/AppLayout';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { CustomersPage } from '@/features/customers/CustomersPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { InvoiceDetailPage } from '@/features/invoices/InvoiceDetailPage';
import { InvoiceEditorPage } from '@/features/invoices/InvoiceEditorPage';
import { InvoicesPage } from '@/features/invoices/InvoicesPage';
import { NotificationsPage } from '@/features/notifications/NotificationsPage';
import { SettingsPage } from '@/features/settings/SettingsPage';
import { createQueryClient } from '@/lib/queryClient';

export function App() {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <Routes>
            <Route element={<PublicOnlyRoute />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
            </Route>
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route index element={<DashboardPage />} />
                <Route path="/invoices" element={<InvoicesPage />} />
                <Route path="/invoices/new" element={<InvoiceEditorPage />} />
                <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
                <Route path="/invoices/:id/edit" element={<InvoiceEditorPage />} />
                <Route path="/customers" element={<CustomersPage />} />
                <Route path="/notifications" element={<NotificationsPage />} />
                <Route path="/settings" element={<SettingsPage />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
        <Toaster richColors position="top-right" closeButton />
      </AuthProvider>
    </QueryClientProvider>
  );
}
