import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { Customer } from '@/api/types';
import { renderWithProviders } from '@/test/render';
import { InvoiceForm } from './InvoiceForm';
import { emptyLine, type InvoiceFormValues } from './invoiceSchema';

const customers: Customer[] = [
  {
    id: 'c-1',
    name: 'Globex',
    email: 'ap@globex.test',
    billingAddress: {},
    createdAt: '2026-01-01T00:00:00Z',
  },
];

const defaults: InvoiceFormValues = {
  customerId: '',
  issueDate: '2026-03-01',
  dueDate: '2026-03-31',
  notes: '',
  lineItems: [emptyLine()],
};

const plain = (s: string | null) => (s ?? '').replace(/\s/g, ' ');

function renderForm(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  renderWithProviders(
    <InvoiceForm
      customers={customers}
      defaultValues={defaults}
      submitLabel="Save draft"
      onSubmit={onSubmit}
      onCancel={vi.fn()}
    />,
  );
  return onSubmit;
}

async function fillLine(index: number, description: string, quantity: string, price: string) {
  const line = screen.getAllByTestId('line-item')[index]!;
  await userEvent.type(within(line).getByLabelText(/description/i), description);
  const qty = within(line).getByLabelText(/quantity/i);
  await userEvent.clear(qty);
  await userEvent.type(qty, quantity);
  const unitPrice = within(line).getByLabelText(/unit price/i);
  await userEvent.clear(unitPrice);
  await userEvent.type(unitPrice, price);
}

describe('InvoiceForm', () => {
  it('previews totals live as lines are edited', async () => {
    renderForm();

    await fillLine(0, 'Consulting', '2', '1500');

    expect(plain(screen.getByTestId('preview-subtotal').textContent)).toBe('R 3 000,00');
    expect(plain(screen.getByTestId('preview-vat').textContent)).toBe('R 450,00');
    expect(plain(screen.getByTestId('preview-total').textContent)).toBe('R 3 450,00');
  });

  it('adds and removes line items', async () => {
    renderForm();

    await userEvent.click(screen.getByRole('button', { name: /add line/i }));
    expect(screen.getAllByTestId('line-item')).toHaveLength(2);

    await userEvent.click(screen.getByRole('button', { name: /remove line 2/i }));
    expect(screen.getAllByTestId('line-item')).toHaveLength(1);
    expect(screen.getByRole('button', { name: /remove line 1/i })).toBeDisabled();
  });

  it('shows validation errors and does not submit an incomplete invoice', async () => {
    const onSubmit = renderForm();

    await userEvent.click(screen.getByRole('button', { name: /save draft/i }));

    expect(await screen.findByText('Choose a customer')).toBeInTheDocument();
    expect(screen.getByText('Description is required')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits valid values', async () => {
    const onSubmit = renderForm();

    await userEvent.selectOptions(screen.getByLabelText(/customer/i), 'c-1');
    await fillLine(0, 'Design work', '1', '999.99');
    await userEvent.click(screen.getByRole('button', { name: /save draft/i }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        customerId: 'c-1',
        lineItems: [{ description: 'Design work', quantity: 1, unitPrice: 999.99, vatApplicable: true }],
      }),
    );
  });
});
