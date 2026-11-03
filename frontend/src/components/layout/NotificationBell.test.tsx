import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { setSession } from '@/api/client';
import { jsonResponse, renderWithProviders } from '@/test/render';
import { NotificationBell } from './NotificationBell';

const notification = {
  id: 'n-1',
  kind: 'OVERDUE_ALERT',
  stage: 'OVERDUE',
  channel: 'IN_APP',
  recipient: 'accounts@acme.test',
  subject: 'INV-2026-0007 for Globex is overdue',
  body: 'Invoice INV-2026-0007 ...',
  status: 'SENT',
  attempts: 1,
  invoiceId: 'i-1',
  invoiceNumber: 'INV-2026-0007',
  createdAt: '2026-03-10T08:00:00Z',
  readAt: null,
};

describe('NotificationBell', () => {
  beforeEach(() => setSession(null));

  function mockApi(unread: number) {
    return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/unread-count')) return jsonResponse({ count: unread });
      if (url.startsWith('/api/v1/notifications')) {
        return jsonResponse({ items: [notification], page: 0, size: 6, totalItems: 1, totalPages: 1 });
      }
      return jsonResponse({}, 404);
    });
  }

  it('shows the unread count in the badge and accessible name', async () => {
    mockApi(3);
    renderWithProviders(<NotificationBell />);

    expect(await screen.findByTestId('unread-count')).toHaveTextContent('3');
    expect(screen.getByRole('button', { name: 'Notifications, 3 unread' })).toBeInTheDocument();
  });

  it('hides the badge when everything is read', async () => {
    const fetchMock = mockApi(0);
    renderWithProviders(<NotificationBell />);

    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.queryByTestId('unread-count')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument();
  });

  it('opens a dropdown with recent notifications', async () => {
    mockApi(1);
    renderWithProviders(<NotificationBell />);

    await userEvent.click(await screen.findByRole('button', { name: /notifications/i }));

    expect(await screen.findByText('INV-2026-0007 for Globex is overdue')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /view all notifications/i })).toHaveAttribute(
      'href',
      '/notifications',
    );
  });
});
