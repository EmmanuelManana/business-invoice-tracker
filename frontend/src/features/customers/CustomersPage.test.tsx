import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { setSession } from '@/api/client';
import { jsonResponse, renderWithProviders } from '@/test/render';
import { CustomersPage } from './CustomersPage';

const customer = {
  id: 'c-1',
  name: 'Karoo Coffee Roasters',
  email: 'accounts@karoocoffee.co.za',
  phone: null,
  billingAddress: { city: 'Graaff-Reinet' },
  createdAt: '2026-01-01T00:00:00Z',
};

describe('CustomersPage', () => {
  beforeEach(() => setSession(null));

  it('closes the delete dialog and explains why when the server refuses', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'DELETE') {
        return jsonResponse(
          {
            status: 409,
            code: 'customer_has_invoices',
            detail: 'This customer has invoices and cannot be deleted',
          },
          409,
          'application/problem+json',
        );
      }
      return jsonResponse({ items: [customer], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    });
    renderWithProviders(<CustomersPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Delete Karoo Coffee Roasters' }));
    expect(screen.getByRole('dialog', { name: 'Delete customer?' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByText('Karoo Coffee Roasters')).toBeInTheDocument();
  });
});
