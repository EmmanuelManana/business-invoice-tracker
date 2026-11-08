import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { setSession } from '@/api/client';
import { jsonResponse, renderWithProviders } from '@/test/render';
import { CustomerSelect } from './CustomerSelect';

const customer = (id: string, name: string) => ({
  id,
  name,
  email: `${id}@x.test`,
  billingAddress: {},
  createdAt: '2026-01-01T00:00:00Z',
});

describe('CustomerSelect', () => {
  beforeEach(() => setSession(null));

  it('searches the server so any customer can be found, not just the first page', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('search=Zulu')) {
        return jsonResponse({
          items: [customer('c-250', 'Zulu Traders')],
          page: 0,
          size: 50,
          totalItems: 1,
          totalPages: 1,
        });
      }
      const first = Array.from({ length: 50 }, (_, i) => customer(`c-${i}`, `Customer ${i}`));
      return jsonResponse({ items: first, page: 0, size: 50, totalItems: 250, totalPages: 5 });
    });
    const onChange = vi.fn();
    renderWithProviders(<CustomerSelect value="" onChange={onChange} emptyLabel="Select a customer…" />);

    expect(await screen.findByText(/showing 50 of 250/i)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/filter the list/i), 'Zulu');

    await screen.findByRole('option', { name: 'Zulu Traders' });
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('search=Zulu'), expect.anything());
    await userEvent.selectOptions(screen.getByRole('combobox'), 'Zulu Traders');
    expect(onChange).toHaveBeenCalledWith('c-250');
  });

  it('keeps the selected customer visible even when it is not in the results', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ items: [customer('c-1', 'Acme')], page: 0, size: 50, totalItems: 1, totalPages: 1 }),
    );
    renderWithProviders(
      <CustomerSelect value="c-999" selectedName="Old Customer" onChange={vi.fn()} emptyLabel="Select…" />,
    );

    expect(await screen.findByRole('option', { name: 'Acme' })).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toHaveDisplayValue('Old Customer');
  });
});
