import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { ApiError } from '@/api/client';
import { AuthContext, type AuthContextValue } from '@/auth/useAuth';
import { renderWithProviders } from '@/test/render';
import { LoginPage } from './LoginPage';

function renderLogin(login: AuthContextValue['login']) {
  const auth: AuthContextValue = {
    status: 'anonymous',
    user: null,
    login,
    register: vi.fn(),
    logout: vi.fn(),
  };
  return renderWithProviders(
    <AuthContext.Provider value={auth}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<p>Dashboard home</p>} />
      </Routes>
    </AuthContext.Provider>,
    { route: '/login' },
  );
}

describe('LoginPage', () => {
  it('validates fields before calling the API', async () => {
    const login = vi.fn();
    renderLogin(login);

    await userEvent.type(screen.getByLabelText(/email/i), 'not-an-email');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toHaveAttribute('aria-invalid', 'true');
    expect(login).not.toHaveBeenCalled();
  });

  it('signs in and navigates to the dashboard', async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    renderLogin(login);

    await userEvent.type(screen.getByLabelText(/email/i), 'demo@acme.co.za');
    await userEvent.type(screen.getByLabelText(/password/i), 'Demo@12345');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(login).toHaveBeenCalledWith('demo@acme.co.za', 'Demo@12345');
    expect(await screen.findByText('Dashboard home')).toBeInTheDocument();
  });

  it('shows the server error message on failure', async () => {
    const login = vi
      .fn()
      .mockRejectedValue(
        new ApiError({ status: 401, code: 'authentication_failed', detail: 'Invalid email or password' }),
      );
    renderLogin(login);

    await userEvent.type(screen.getByLabelText(/email/i), 'demo@acme.co.za');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });
});
