import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '@/api/client';
import { renderWithProviders } from '@/test/render';
import { PaymentForm } from './PaymentForm';

// A business date that differs from the browser's: e.g. the demo clock moved a week ahead.
const BUSINESS_TODAY = '2030-06-15';

function renderForm(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  renderWithProviders(
    <PaymentForm
      balanceDue={2700}
      currency="ZAR"
      today={BUSINESS_TODAY}
      onSubmit={onSubmit}
      onCancel={vi.fn()}
    />,
  );
  return onSubmit;
}

describe('PaymentForm', () => {
  it("defaults to the business's date, not the browser's", () => {
    renderForm();
    expect(screen.getByLabelText(/payment date/i)).toHaveValue(BUSINESS_TODAY);
  });

  it("accepts the business's today and rejects anything after it", async () => {
    const onSubmit = renderForm();
    const date = screen.getByLabelText(/payment date/i);

    await userEvent.clear(date);
    await userEvent.type(date, '2030-06-16');
    await userEvent.click(screen.getByRole('button', { name: /record payment/i }));
    expect(await screen.findByText(/cannot be after today/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();

    await userEvent.clear(date);
    await userEvent.type(date, BUSINESS_TODAY);
    await userEvent.click(screen.getByRole('button', { name: /record payment/i }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ paidOn: BUSINESS_TODAY, amount: 2700 }));
  });

  it('rejects more than two decimal places like the server does', async () => {
    const onSubmit = renderForm();
    const amount = screen.getByLabelText(/amount/i);

    await userEvent.clear(amount);
    await userEvent.type(amount, '10.005');
    await userEvent.click(screen.getByRole('button', { name: /record payment/i }));

    expect(await screen.findByText('Use at most 2 decimal places')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('shows server field errors next to the field', async () => {
    renderForm(
      vi.fn().mockRejectedValue(
        new ApiError({
          status: 400,
          code: 'validation_failed',
          detail: 'One or more fields are invalid.',
          errors: [{ field: 'reference', message: 'size must be between 0 and 100' }],
        }),
      ),
    );

    await userEvent.click(screen.getByRole('button', { name: /record payment/i }));

    expect(await screen.findByText('size must be between 0 and 100')).toBeInTheDocument();
    expect(screen.getByLabelText(/reference/i)).toHaveAttribute('aria-invalid', 'true');
  });
});
