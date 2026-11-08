import { expect, test, type Page } from '@playwright/test';

/**
 * Happy path through the core of the brief: sign in, create and send an invoice, simulate time
 * passing until it is overdue, and see the overdue notification arrive.
 *
 * Needs the stack running with dev tools and demo seeding enabled (the docker compose default).
 */
const email = process.env.E2E_EMAIL ?? 'demo@acme.co.za';
const password = process.env.E2E_PASSWORD ?? 'Demo@12345';

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

test.afterEach(async ({ page }) => {
  // Leave the shared demo clock at real time for whoever uses the stack next.
  await page.goto('/');
  const reset = page.getByRole('button', { name: 'Reset' });
  if (await reset.isEnabled().catch(() => false)) await reset.click();
});

test('create invoice → simulate overdue → see notification', async ({ page }) => {
  await signIn(page);

  // Create a draft due tomorrow.
  await page.getByRole('link', { name: 'New invoice' }).first().click();
  await expect(page.getByRole('heading', { name: 'New invoice' })).toBeVisible();
  await page.getByLabel('Customer').selectOption({ label: 'Karoo Coffee Roasters' });
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const dueDate = tomorrow.toISOString().slice(0, 10);
  await page.getByLabel('Due date').fill(dueDate);
  await page.getByLabel('Line 1 description').fill('E2E brand workshop');
  await page.getByLabel('Quantity').fill('2');
  await page.getByLabel('Unit price').fill('1000');
  // 2 x 1000 + 15% VAT. Browsers differ on en-ZA separators ("2 300,00" vs "2,300.00").
  await expect(page.getByTestId('preview-total')).toHaveText(/R\s?2.300.00/);
  await page.getByRole('button', { name: 'Save draft' }).click();

  // Send it.
  const heading = page.getByRole('heading', { level: 1 });
  await expect(heading).toHaveText(/INV-\d{4}-\d{4}/);
  const invoiceNumber = (await heading.textContent())!.trim();
  await page.getByRole('button', { name: 'Mark as sent' }).click();
  // Sent invoices accept payments, so the action appears once the status changes.
  await expect(page.getByRole('button', { name: 'Record payment' })).toBeVisible();

  // Simulate time passing and run the overdue job.
  await page.getByRole('link', { name: 'Dashboard', exact: true }).click();
  await page.getByRole('button', { name: '+7 days' }).click();
  await expect(page.getByTestId('business-date')).toContainText('+7 days simulated');
  await page.getByRole('button', { name: 'Run overdue check now' }).click();
  await expect(page.getByText(/Overdue check done/)).toBeVisible();

  // The bell shows unread notifications and the overdue alert is listed.
  await expect(page.getByTestId('unread-count')).toBeVisible();
  await page.getByRole('link', { name: 'Notifications', exact: true }).click();
  // Moving the clock also triggers reminders for the demo invoices, so the alert can be on a later
  // page. Page through like a user would.
  const alert = page.getByRole('button', {
    name: new RegExp(`${invoiceNumber} for Karoo Coffee Roasters is overdue`),
  });
  await expect(page.getByRole('list', { name: 'Notifications' })).toBeVisible();
  for (let pageNo = 0; pageNo < 10 && !(await alert.isVisible()); pageNo++) {
    const next = page.getByRole('button', { name: 'Next' });
    if (!(await next.isEnabled())) break;
    await next.click();
    await page.waitForLoadState('networkidle');
  }
  await expect(alert).toBeVisible();

  // And the invoice itself is now overdue.
  await page.getByRole('link', { name: 'Invoices', exact: true }).click();
  await page.getByLabel('Search by number or customer').fill(invoiceNumber);
  const row = page.getByRole('row', { name: new RegExp(invoiceNumber) });
  await expect(row.getByText('Overdue', { exact: true })).toBeVisible();
});
