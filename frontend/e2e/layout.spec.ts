import { expect, test, type Page } from '@playwright/test';

/**
 * Layout checks the manual QA run flagged: the invoice filters overflowed at ~1150px (D2), and the
 * mobile layout could not be checked by hand (TC-19). Screenshots are saved under test-results/.
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

async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
}

test('invoice filters fit on a 1150px-wide screen without sideways scrolling', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1150, height: 800 });
  await signIn(page);

  await page.getByRole('link', { name: 'Invoices', exact: true }).click();
  await expect(page.getByLabel('Issued to')).toBeVisible();

  expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
  const box = await page.getByLabel('Issued to').boundingBox();
  expect(box!.x + box!.width).toBeLessThanOrEqual(1150);
  await page.screenshot({ path: testInfo.outputPath('invoices-1150px.png'), fullPage: true });
});

test('mobile layout: menu button, menu opens, no sideways scrolling', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page);

  const menuButton = page.getByRole('button', { name: 'Open menu' });
  await expect(menuButton).toBeVisible();
  expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);

  await menuButton.click();
  await expect(page.getByRole('link', { name: 'Invoices', exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Invoices', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Invoices' })).toBeVisible();
  expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
  await page.screenshot({ path: testInfo.outputPath('invoices-mobile.png'), fullPage: true });
});
