import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { LoadingState } from '@/components/ui/Spinner';
import { useAuth } from './useAuth';

/** Renders child routes only for signed-in users; otherwise redirects to login and comes back after. */
export function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <LoadingState label="Checking your session…" />;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}

/** Login/register pages: bounce signed-in users to the app. */
export function PublicOnlyRoute() {
  const { status } = useAuth();
  if (status === 'loading') {
    return <LoadingState />;
  }
  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
