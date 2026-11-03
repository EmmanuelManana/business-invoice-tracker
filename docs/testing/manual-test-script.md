# Manual test script

Follow the steps below in a browser, by hand or with a browser automation tool. Before you start:

1. The app is running locally (`docker compose up --build`) and http://localhost:3000 opens in Chrome.
2. Have [`manual-test-report.md`](manual-test-report.md) handy. Results are recorded in the same structure.

---

You are a manual QA tester. Test the **Business Invoice Tracker** web app at **http://localhost:3000** the way a real user would: by clicking, typing and reading the screen. Don't call the API directly and don't change any code.

## Ground rules

- Work through the test cases **in order**. Later tests reuse data from earlier ones.
- Before your first step, pick a **run ID**: the current time as `HHMM` (e.g. `1432`). Add it to every name you type, so repeated runs don't clash. Below, `<RUN>` means that ID.
- For each test, compare what you see with **Expected**. Mark it:
  - **PASS** if it matches;
  - **FAIL** if it doesn't. Write down exactly what you saw instead, and carry on with the next test where possible;
  - **BLOCKED** if an earlier failure makes the test impossible.
- 📸 marks a point where you must **take a screenshot as evidence**. Name each one `TC-XX-<short-name>.png`, e.g. `TC-05-invoice-totals.png`. If you can save or download images, save them with these names. If you can't, record the whole run with the GIF recorder and export it at the end. Either way, list each screenshot in the report and describe what it shows in one line.
- Money is shown South African style: some browsers display `R 3 450,00`, others `R 3,450.00`. Both are correct.
- If something unexpected happens (an error message, a blank page), take a screenshot, note it, and continue.
- **Always run TC-20 (reset the clock) at the end**, even if earlier tests failed.

## Test data

| Item | Value |
|---|---|
| Demo login | `demo@acme.co.za` / `Demo@12345` |
| New customer | Name `QA Customer <RUN>`, email `qa<RUN>@example.com`, phone `+27 82 000 <RUN>`, city `Cape Town` |
| Throwaway customer | Name `Delete Me <RUN>`, email `delete<RUN>@example.com` |
| New business (TC-18) | Business `QA Business <RUN>`, name `QA Tester`, email `qa-owner<RUN>@example.com`, password `QaTest123` |

---

## Test cases

### TC-01: Sign-in rejects wrong details
1. Open http://localhost:3000. You should land on the **Sign in** page.
2. Enter email `demo@acme.co.za` and password `wrong-password`, then click **Sign in**.

**Expected:** a red message "Invalid email or password" appears and you stay on the sign-in page. 📸 `TC-01-login-error`

### TC-02: Sign-in validation
1. Clear both fields, type `not-an-email` in **Email**, leave **Password** empty, click **Sign in**.

**Expected:** "Enter a valid email address" under Email and "Password is required" under Password. Nothing is submitted. 📸 `TC-02-login-validation`

### TC-03: Successful sign-in and dashboard
1. Sign in with `demo@acme.co.za` / `Demo@12345`.

**Expected:**
- The **Dashboard** opens and shows the cards **Total outstanding**, **Total overdue** and **Paid this month**, all with rand amounts.
- An **Invoices by status** row shows counts for Draft, Sent, Partially Paid, Overdue, Paid and Cancelled. Overdue is more than 0.
- An **Outstanding by age** bar chart, with a table of Current / 1-30 / 31-60 / 61-90 / 90+ days.
- A **Most overdue** list.
- An amber **Demo time machine** card showing today's business date.

📸 `TC-03-dashboard`

### TC-04: Create a customer, with validation
1. Click **Customers** in the left menu, then **Add customer**.
2. Click the dialog's **Add customer** button with everything empty. **Expected:** "Name is required" and "Email is required" appear.
3. Fill in the *New customer* data from the table above and click **Add customer**.

**Expected:** a green toast "QA Customer <RUN> added", the dialog closes, and the customer appears in the list. 📸 `TC-04-customer-created`

4. Type `QA Customer <RUN>` in **Search by name or email**. **Expected:** the list shrinks to that customer only.
5. Click the pencil (**Edit QA Customer <RUN>**), change the city to `Stellenbosch`, click **Save changes**. **Expected:** toast "Customer updated", and the City column shows Stellenbosch.

### TC-05: Create a draft invoice, with totals worked out by the server
1. Click **Invoices**, then **New invoice**.
2. Click **Save draft** without filling anything in. **Expected:** "Choose a customer" and "Description is required" appear.
3. Choose **Customer** `QA Customer <RUN>`. Leave **Issue date** as today and **Due date** as filled in (30 days from today).
4. Line 1: description `Consulting <RUN>`, quantity `2`, unit price `1500`, **VAT 15%** ticked.
5. Click **Add line**. Line 2: description `Travel <RUN>`, quantity `1`, unit price `250`, **untick VAT 15%**.

**Expected preview (before saving):** Subtotal R 3 250,00, VAT R 450,00, Total R 3 700,00. 📸 `TC-05-invoice-form-totals`

6. Click **Save draft**.

**Expected:**
- A toast "Draft INV-…created" appears, and the invoice page opens with a number like `INV-2026-00NN` and status **Draft**.
- Totals show Subtotal R 3 250,00, VAT R 450,00, Total R 3 700,00, Balance due R 3 700,00.
- **Activity** shows "Invoice … created".

**Write down this invoice number as INVOICE-A.** 📸 `TC-05-draft-invoice`

### TC-06: Edit the draft
1. On INVOICE-A, click **Edit**. Change line 1 quantity to `3` and click **Save changes**.

**Expected:** toast "INVOICE-A updated". Total is now R 5 425,00 (3 × 1 500 + 15% VAT = R 5 175,00, plus R 250,00 travel), and Activity shows "Draft updated".

2. Click **Edit** again, set line 1 quantity back to `2`, click **Save changes**. **Expected:** the total is back to R 3 700,00.

### TC-07: Send the invoice
1. On INVOICE-A, click **Mark as sent**.

**Expected:**
- Toast "INVOICE-A marked as sent". The status badge shows **Sent**.
- The **Edit** and **Mark as sent** buttons disappear, and **Record payment** appears.
- Activity shows "Invoice sent to qa<RUN>@example.com".

📸 `TC-07-invoice-sent`

### TC-08: Record a partial payment, with validation
1. Click **Record payment**. Change **Amount** to `5000` and click **Record payment** in the dialog. **Expected:** "Cannot exceed the balance of 3700.00".
2. Set **Amount** to `1000`, **Method** `EFT`, **Reference** `QA-<RUN>-1`, and click **Record payment**.

**Expected:**
- Toast "Payment recorded". Status is **Partially Paid** and Balance due is R 2 700,00.
- The Payments section lists R 1 000,00 · EFT · QA-<RUN>-1.

📸 `TC-08-partial-payment`

### TC-09: Pay the rest
1. Click **Record payment**. The amount is pre-filled with 2700. Click **Record payment**.

**Expected:**
- Status **Paid**, Balance due R 0,00. Two payments are listed, and Activity includes "Invoice paid in full".
- **Record payment** and **Cancel invoice** are no longer shown.

📸 `TC-09-paid`

### TC-10: Download the PDF
1. On INVOICE-A, click **PDF**.

**Expected:** a file `INVOICE-A.pdf` downloads (or opens). If you can view it, it shows "TAX INVOICE", the invoice number, both line items and the totals. 📸 `TC-10-pdf` (screenshot the download or the opened PDF)

### TC-11: Cancel an invoice
1. Create another invoice: **Invoices → New invoice**, customer `QA Customer <RUN>`, one line `Cancel test <RUN>`, quantity `1`, price `100`, **Save draft**. Note the number as **INVOICE-C**.
2. Click **Cancel invoice**. In the dialog, type reason `QA cancellation` and click **Cancel invoice**.

**Expected:**
- Toast "INVOICE-C cancelled". Status **Cancelled** (shown with a strike-through).
- The Customer panel shows "Cancelled: QA cancellation", and Activity shows "Invoice cancelled: QA cancellation".

📸 `TC-11-cancelled`

### TC-12: Search, filter and sort invoices
1. Go to **Invoices** and type `<RUN>` into **Search number or customer**. Wait a second.
   **Expected:** only your QA invoices appear (INVOICE-A, INVOICE-C, plus INVOICE-B later). Clear the search.
2. Set the status filter to **Overdue**. **Expected:** only Overdue invoices are listed, and the page address now contains `status=OVERDUE`. 📸 `TC-12-filter-overdue`
3. Set the status back to **All statuses**. Click the **Total** column header twice. **Expected:** the order changes between highest-first and lowest-first, with an arrow on the header.

### TC-13: Customers with invoices can't be deleted, others can
1. Go to **Customers**. Click the bin icon for `QA Customer <RUN>`, then **Delete** in the dialog.
   **Expected:** a red toast "This customer has invoices and cannot be deleted", and the customer stays. 📸 `TC-13-delete-blocked`
2. Click **Add customer**, add the *Throwaway customer* from the table, then delete it.
   **Expected:** toast "Customer deleted", and it disappears from the list.

### TC-14: An invoice goes overdue (time machine)
1. Create **INVOICE-B**: **Invoices → New invoice**, customer `QA Customer <RUN>`, **Due date = tomorrow**, one line `Overdue test <RUN>`, quantity `1`, price `1000`, VAT ticked. **Save draft**, then **Mark as sent**.
2. Go to the **Dashboard**. Note the business date and the number on the bell (top right).
3. In **Demo time machine** click **+7 days**. **Expected:** toast "Business date is now …" (7 days later), and the card says "(+7 days simulated)".
4. Click **Run overdue check now**.

**Expected:** a toast like "Overdue check done: N newly overdue, N reminder(s) and N alert(s) sent." with each number at least 1, and the bell count goes up. 📸 `TC-14-overdue-run`

5. Open INVOICE-B (search for it in **Invoices**).

**Expected:**
- Status **Overdue**, and "6 days overdue" next to the due date.
- Activity shows "Invoice became overdue" and "1 day overdue reminder sent via EMAIL" (and via SMS).

📸 `TC-14-invoice-overdue`

### TC-15: Notification centre
Moving the clock also triggers reminders for some of the demo invoices, so there may be quite a few new notifications.

1. Click the **bell**. **Expected:** a dropdown shows the six newest notifications, each with a channel badge (Email, SMS or Alert), a time and a subject. 📸 `TC-15-bell-dropdown`
2. Click **View all notifications**.
3. Find the row "INVOICE-B for QA Customer <RUN> is overdue" (use **Next** at the bottom if it isn't on the first page) and click it to expand it.

**Expected:**
- The full message is shown, with Attempts 1 and a "View INVOICE-B" link.
- Rows for the customer reminder (Email, and SMS) for INVOICE-B are also listed, with status **SENT**.

📸 `TC-15-notifications-page`

4. Expand the Email reminder for INVOICE-B. **Expected:** the message starts "Dear QA Customer <RUN>" and mentions INVOICE-B, "day(s) overdue" and the balance.

### TC-16: Reminders are sent only once
1. On the **Notifications** page, in the time machine card, click **Run overdue check now** again.

**Expected:** toast "Overdue check done: 0 newly overdue, 0 reminder(s) and 0 alert(s) sent." No new rows appear. 📸 `TC-16-no-duplicates`

*(If you see non-zero numbers here, check whether the business date changed between TC-14 and now, e.g. it passed midnight. Re-run once before marking FAIL.)*

2. Click **Mark all read**. **Expected:** the bell badge disappears, and ticking **Unread only** shows "All caught up".

### TC-17: Business profile
1. Click **Business profile** in the left menu.
2. Change **VAT number** to `123` and click **Save profile**. **Expected:** "A South African VAT number has 10 digits and starts with 4".
3. Change it back to `4123456789`, change **Phone** to `+27 21 555 <RUN>`, and click **Save profile**.

**Expected:** toast "Business profile saved". Reload the page: the phone number is still the new one. 📸 `TC-17-profile-saved`

### TC-18: Sign out, protected pages, and a second business sees nothing
1. Click **Sign out** (bottom of the left menu). **Expected:** toast "Signed out" and the sign-in page.
2. Type http://localhost:3000/invoices into the address bar. **Expected:** you're sent back to **Sign in**.
3. Click **Create a business account** and register with the *New business* data. **Expected:** toast "Welcome! Your business account is ready." and an empty dashboard (all amounts R 0,00, all counts 0).
4. Go to **Invoices** and **Customers**. **Expected:** "No invoices yet" and "No customers yet". None of the demo data is visible. 📸 `TC-18-new-business-empty`
5. Open INVOICE-A's address directly: the URL you were on in TC-05, `http://localhost:3000/invoices/<id>`.

**Expected:** "Couldn't load this" with a "was not found" message. Another business's invoice must never be shown. 📸 `TC-18-tenant-isolation`

### TC-19: Mobile layout
1. Resize the browser window to phone width (about 390 × 844), or use a mobile view.
2. Sign in as `qa-owner<RUN>@example.com` if you're signed out.

**Expected:** the left menu is hidden and a menu (☰) button appears at the top left. Tapping it opens the menu, and pages fit the width without sideways scrolling. 📸 `TC-19-mobile`

3. Restore the normal window size.

### TC-20: Reset the clock (always run this)
1. Sign in as `demo@acme.co.za` / `Demo@12345` (sign out first if needed).
2. On the **Dashboard**, click **Reset** in the time machine card.

**Expected:** toast "Clock reset to real time", and the card no longer says "days simulated". 📸 `TC-20-clock-reset`

---

## Writing the report

When you've finished, produce the test report as **one Markdown document**, using the structure of `docs/testing/manual-test-report.md`:

1. **Summary:** date and time, run ID, browser, tester, and totals of PASS / FAIL / BLOCKED.
2. **Results table:** one row per test case, with ID, title, result and a short note.
3. **Details:** for each test case:
   - the result;
   - what you actually saw, especially for anything that didn't match;
   - each screenshot, embedded as `![TC-05 invoice totals](evidence/TC-05-invoice-form-totals.png)` with a one-line caption.
4. **Defects found:** for each failure, steps to reproduce, expected vs. actual, and the screenshot.
5. **Test data created:** the invoice numbers (INVOICE-A, INVOICE-B, INVOICE-C), the customer and the business you registered.

Give me the whole report in a single Markdown code block, so I can paste it straight into `docs/testing/manual-test-report.md`. If you saved screenshots, tell me which files to put in `docs/testing/evidence/`.

**Optional: save it on GitHub.** Only if I say so in this chat, open https://github.com/EmmanuelManana/business-invoice-tracker:
- Use **Add file → Create new file** to save the report as `docs/testing/manual-test-report.md`.
- Use **Add file → Upload files** to add the screenshots to `docs/testing/evidence/`.
- Show me the commit screen and wait for my confirmation before committing.
