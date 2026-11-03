import { useState } from 'react';
import clsx from 'clsx';
import { Bell, Building2, FileText, LayoutDashboard, LogOut, Menu, Users, X } from 'lucide-react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '@/auth/useAuth';
import { NotificationBell } from './NotificationBell';

const navigation = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/invoices', label: 'Invoices', icon: FileText },
  { to: '/customers', label: 'Customers', icon: Users },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/settings', label: 'Business profile', icon: Building2 },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  async function handleLogout() {
    await logout();
    toast.success('Signed out');
    navigate('/login');
  }

  const nav = (
    <nav aria-label="Main" className="flex flex-col gap-1">
      {navigation.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={() => setMobileOpen(false)}
          className={({ isActive }) =>
            clsx(
              'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium',
              isActive
                ? 'bg-brand-50 text-brand-700'
                : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
            )
          }
        >
          <Icon className="h-4 w-4" aria-hidden="true" />
          {label}
        </NavLink>
      ))}
    </nav>
  );

  return (
    <div className="min-h-screen">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:shadow"
      >
        Skip to content
      </a>

      <aside className="fixed inset-y-0 left-0 hidden w-60 flex-col border-r border-slate-200 bg-white px-4 py-5 lg:flex">
        <Brand />
        <div className="mt-8 flex-1">{nav}</div>
        <UserBlock name={user?.fullName} business={user?.businessName} onLogout={handleLogout} />
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div
            className="absolute inset-0 bg-slate-900/40"
            aria-hidden="true"
            onClick={() => setMobileOpen(false)}
          />
          <div className="absolute inset-y-0 left-0 flex w-64 flex-col bg-white px-4 py-5 shadow-xl">
            <div className="flex items-center justify-between">
              <Brand />
              <button
                type="button"
                onClick={() => setMobileOpen(false)}
                className="rounded p-1 text-slate-500"
                aria-label="Close menu"
              >
                <X className="h-5 w-5" aria-hidden="true" />
              </button>
            </div>
            <div className="mt-8 flex-1">{nav}</div>
            <UserBlock name={user?.fullName} business={user?.businessName} onLogout={handleLogout} />
          </div>
        </div>
      )}

      <div className="lg:pl-60">
        <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-slate-200 bg-white/90 px-4 backdrop-blur sm:px-6">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="rounded p-1.5 text-slate-600 hover:bg-slate-100 lg:hidden"
            aria-label="Open menu"
          >
            <Menu className="h-5 w-5" aria-hidden="true" />
          </button>
          <p className="hidden text-sm text-slate-500 sm:block">{user?.businessName}</p>
          <NotificationBell />
        </header>
        <main id="main" className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function Brand() {
  return (
    <div className="flex items-center gap-2 px-2">
      <img src="/favicon.svg" alt="" className="h-7 w-7" />
      <span className="text-base font-semibold text-slate-900">Invoice Tracker</span>
    </div>
  );
}

function UserBlock({ name, business, onLogout }: { name?: string; business?: string; onLogout: () => void }) {
  return (
    <div className="border-t border-slate-200 pt-4">
      <p className="truncate px-2 text-sm font-medium text-slate-900">{name}</p>
      <p className="truncate px-2 text-xs text-slate-500">{business}</p>
      <button
        type="button"
        onClick={onLogout}
        className="mt-3 flex w-full items-center gap-2 rounded-md px-2 py-2 text-sm text-slate-600 hover:bg-slate-100"
      >
        <LogOut className="h-4 w-4" aria-hidden="true" /> Sign out
      </button>
    </div>
  );
}
