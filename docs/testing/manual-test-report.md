# Manual Test Report — Business Invoice Tracker

## 1. Summary
| Item | Value |
|---|---|
| Date / time | Tue 03 Nov 2026, 15:14–15:24 SAST |
| Run ID | 1514 |
| Browser | Google Chrome, viewport 1150×631 |
| Tester | Manual run in Google Chrome |
| App URL | http://localhost:3000 |
| **Totals** | **PASS 19 · FAIL 0 · BLOCKED 1** (out of 20) |

All required functional behaviour passed. Six defects were logged: two Medium and four Low. The most important is that invoice statuses stay "Overdue" after the demo clock is reset (D1).

The demo data already contained records from an earlier run today (run 1458, e.g. INV-2026-0021 to 0023). Those records are what make D1 visible from the very first dashboard view.

All screenshots are in [`evidence/`](evidence/).

## 2. Results
| ID | Test | Result | Notes |
|---|---|---|---|
| TC-01 | Sign-in rejects wrong details | PASS | |
| TC-02 | Sign-in validation | PASS | Old "Invalid email or password" banner stays visible (D4) |
| TC-03 | Successful sign-in and dashboard | PASS | 13 Overdue, but only 5 invoices past due by age (D1) |
| TC-04 | Create / search / edit customer | PASS | |
| TC-05 | Draft invoice, server totals | PASS | INVOICE-A = INV-2026-0024 |
| TC-06 | Edit draft | PASS | R 5 425,00, then back to R 3 700,00 |
| TC-07 | Send invoice | PASS | |
| TC-08 | Partial payment + validation | PASS | |
| TC-09 | Pay the rest | PASS | |
| TC-10 | Download PDF | PASS | Download triggered from the button; contents not viewable from the tester's tools |
| TC-11 | Cancel invoice | PASS | INVOICE-C = INV-2026-0025; balance still shown (D6) |
| TC-12 | Search, filter, sort | PASS | 8 of 13 "Overdue" invoices not yet due (D1); horizontal overflow (D2) |
| TC-13 | Delete rules | PASS | Confirm dialog stays open after rejection (D5) |
| TC-14 | Invoice goes overdue | PASS | INVOICE-B = INV-2026-0026; bell 2 → 5 |
| TC-15 | Notification centre | PASS | |
| TC-16 | Reminders sent only once | PASS | |
| TC-17 | Business profile | PASS | |
| TC-18 | Sign out, protection, tenant isolation | PASS | Time machine is shared across tenants (D3) |
| TC-19 | Mobile layout | BLOCKED | Window could not be resized, and the app (correctly) refuses to be framed |
| TC-20 | Reset clock | PASS | Overdue count is 14 after the reset (D1) |

## 3. Details

**TC-01 — PASS.** Signing in with demo@acme.co.za and "wrong-password" showed a red "Invalid email or password" banner, and the page stayed on Sign in.

![TC-01 login error](evidence/TC-01-login-error.png)
*Red error banner on the sign-in page.*

**TC-02 — PASS.** Field errors appeared: "Enter a valid email address" under Email and "Password is required" under Password. Nothing was submitted. The red banner from TC-01 was still showing above the form (D4).

![TC-02 login validation](evidence/TC-02-login-validation.png)
*Both field errors, with the stale server banner still visible.*

**TC-03 — PASS.**
- Cards: Total outstanding R 161 575,00, Total overdue R 77 970,00, Paid this month R 40 500,00.
- Invoices by status: Draft 2, Sent 1, Partially Paid 1, Overdue 13, Paid 4, Cancelled 2.
- Outstanding by age: Current 10, 1-30 days 2, 31-60 days 1, 61-90 days 1, 90+ days 1.
- Most overdue listed 5 invoices; the amber time machine card showed 03 Nov 2026.

The status row says 13 Overdue, but only 5 invoices are past due by age (D1).

![TC-03 dashboard](evidence/TC-03-dashboard.png)
*Dashboard top (cards, status row, time machine) and bottom (age chart, most overdue).*

**TC-04 — PASS.**
- Empty submit showed "Name is required" and "Email is required".
- Saving showed the toast "QA Customer 1514 added", and the dialog closed.
- Search narrowed the list to 1 result.
- Editing the city showed the toast "Customer updated", and the City column changed to Stellenbosch.

![TC-04 customer created](evidence/TC-04-customer-created.png)
*"QA Customer 1514 added" toast, with the search narrowed to the new customer.*

**TC-05 — PASS.**
- Empty save showed "Choose a customer" and "Description is required".
- The form preview showed Subtotal R 3 250,00, VAT R 450,00, Total R 3 700,00.
- Saving showed the toast "Draft INV-2026-0024 created" and status Draft.
- The saved invoice matched the preview, with Balance due R 3 700,00; Activity showed "Invoice INV-2026-0024 created".
- Page address: /invoices/6a24f915-8220-4907-9ae1-71765288d5f0

![TC-05 form totals](evidence/TC-05-invoice-form-totals.png)
*Preview totals before saving (line 2 has VAT unticked).*

![TC-05 draft invoice](evidence/TC-05-draft-invoice.png)
*Saved draft with server-calculated totals.*

**TC-06 — PASS.** Quantity 3 gave the toast "INV-2026-0024 updated", a total of R 5 425,00, and "Draft updated" in Activity. Setting quantity back to 2 returned the total to R 3 700,00.

**TC-07 — PASS.**
- Toast "INV-2026-0024 marked as sent"; the badge showed Sent.
- Edit and Mark as sent were gone; Record payment appeared.
- Activity showed "Invoice sent to qa1514@example.com".

![TC-07 invoice sent](evidence/TC-07-invoice-sent.png)
*Sent status, with Record payment now available.*

**TC-08 — PASS.**
- An amount of 5000 showed "Cannot exceed the balance of 3700.00".
- 1000 by EFT showed the toast "Payment recorded", status Partially Paid and Balance due R 2 700,00.
- Payments listed R 1 000,00 · EFT · QA-1514-1.

![TC-08 partial payment](evidence/TC-08-partial-payment.png)
*Partially Paid badge (top) and the recorded payment (bottom).*

**TC-09 — PASS.**
- The amount was pre-filled with 2700.
- After saving: status Paid, Balance due R 0,00, two payments listed, and "Invoice paid in full" in Activity.
- Only the PDF button remained.

![TC-09 paid](evidence/TC-09-paid.png)
*Paid invoice, with Record payment and Cancel invoice gone.*

**TC-10 — PASS.** Clicking PDF triggered the download. The PDF's contents (TAX INVOICE, lines, totals) could not be viewed from the tester's tools.

Opening the PDF URL directly in the tab returned HTTP 401 "Authentication required". That is expected, because the API uses a bearer token, and it is not a defect.

![TC-10 pdf](evidence/TC-10-pdf.png)
*Invoice page after clicking PDF.*

**TC-11 — PASS.**
- Created INV-2026-0025 (R 115,00) and cancelled it with the reason "QA cancellation".
- Toast "INV-2026-0025 cancelled"; the badge showed Cancelled, struck through.
- The Customer panel showed "Cancelled: QA cancellation"; Activity showed "Invoice cancelled: QA cancellation".
- The page still showed "Balance due R 115,00" (D6).

![TC-11 cancelled](evidence/TC-11-cancelled.png)
*Cancelled invoice, with the reason in the Customer panel.*

**TC-12 — PASS.**
- Searching "1514" listed only INV-2026-0025 and INV-2026-0024 (2 results).
- The Overdue filter changed the URL to ?status=OVERDUE and listed 13 invoices, all with the Overdue badge.
- Clicking the Total header sorted highest first (↓, R 36 800,00 at the top), then lowest first (↑, R 115,00 at the top).

Two issues showed up here:
- 8 of the 13 "Overdue" invoices are not yet due. Their due dates are 04–06 Nov 2026, and the business date is 03 Nov (D1).
- A horizontal scrollbar appears because the page is 1238px wide in an 1150px viewport (D2).

![TC-12 filter overdue](evidence/TC-12-filter-overdue.png)
*Overdue filter: the top rows are due 04 Nov, which is still in the future. Note the horizontal scrollbar.*

**TC-13 — PASS.**
- Deleting QA Customer 1514 showed the red toast "This customer has invoices and cannot be deleted", and the customer stayed. The confirm dialog stayed open (D5).
- "Delete Me 1514" was added, then deleted with the toast "Customer deleted"; the list showed "No matching customers".

![TC-13 delete blocked](evidence/TC-13-delete-blocked.png)
*Red toast, with the confirm dialog still open.*

**TC-14 — PASS.**
- INV-2026-0026 was created due 04 Nov and sent. The dashboard showed 03 Nov 2026, and the bell showed 2.
- "+7 days" showed the toast "Business date is now 10 Nov 2026", and the card said "(+7 days simulated)".
- "Run overdue check now" showed "Overdue check done: 1 newly overdue, 2 reminder(s) and 1 alert(s) sent." The bell went from 2 to 5.
- INVOICE-B showed status Overdue and "6 days overdue".
- INVOICE-B's Activity showed "Invoice became overdue", "1 day overdue reminder sent via EMAIL" and "1 day overdue reminder sent via SMS".

![TC-14 overdue run](evidence/TC-14-overdue-run.png)
*Overdue check toast at the simulated date of 10 Nov 2026.*

![TC-14 invoice overdue](evidence/TC-14-invoice-overdue.png)
*INVOICE-B overdue, with reminder activity.*

**TC-15 — PASS.**
- The bell dropdown listed the 6 newest items, each with an Alert/SMS/Email badge, a time and a subject.
- On the full page, the alert "INV-2026-0026 for QA Customer 1514 is overdue" expanded to the full message, "Attempts: 1" and a "View INV-2026-0026" link.
- The SMS and Email reminders for INV-2026-0026 were listed as SENT.
- The Email reminder began "Dear QA Customer 1514," and mentioned INV-2026-0026, "6 day(s) overdue" and ZAR 1150.00.

Observation: expanding a row marks it as read (the bell went 5 → 4 → 3).

![TC-15 bell dropdown](evidence/TC-15-bell-dropdown.png)
*Six newest notifications in the bell dropdown.*

![TC-15 notifications page](evidence/TC-15-notifications-page.png)
*Expanded alert for INVOICE-B, with the SMS and Email reminders below it.*

**TC-16 — PASS.** Re-running the check showed "Overdue check done: 0 newly overdue, 0 reminder(s) and 0 alert(s) sent.", and no new rows appeared. "Mark all read" cleared the bell badge, and ticking "Unread only" showed "All caught up".

![TC-16 no duplicates](evidence/TC-16-no-duplicates.png)
*Zero-count toast on the second run.*

**TC-17 — PASS.**
- VAT number 123 showed "A South African VAT number has 10 digits and starts with 4".
- VAT 4123456789 with phone +27 21 555 1514 showed the toast "Business profile saved".
- After a full reload of the page, the phone was still +27 21 555 1514.

![TC-17 profile saved](evidence/TC-17-profile-saved.png)
*Business profile after a full reload.*

**TC-18 — PASS.**
- Sign out showed the toast "Signed out" and the sign-in page. Opening /invoices redirected to /login.
- Registering "QA Business 1514" showed the toast "Welcome! Your business account is ready.", R 0,00 on every card and all counts 0.
- Invoices showed "No invoices yet", and Customers showed "No customers yet".
- Opening INVOICE-A's address showed "Couldn't load this" and "Invoice 6a24f915-… was not found".

The new business's dashboard showed "Business date: 10 Nov 2026 (+7 days simulated)" (D3).

![TC-18 new business empty](evidence/TC-18-new-business-empty.png)
*Empty Invoices (top) and Customers (bottom) for the new business.*

![TC-18 tenant isolation](evidence/TC-18-tenant-isolation.png)
*"was not found" when opening another business's invoice.*

**TC-19 — BLOCKED.** Two approaches were tried, and both failed:
- Resizing the window to 390×844 had no effect. The viewport stayed 1150px wide, because the window is maximised (outer width 1536px).
- Loading the app inside a 390px-wide frame was refused by the browser. The app blocks framing, which is good clickjacking protection and not a defect.

The mobile layout should be re-tested by hand on a phone, or with Chrome DevTools device mode.

![TC-19 mobile](evidence/TC-19-mobile.png)
*390px frame refused: the app does not allow itself to be embedded.*

**TC-20 — PASS.** Signed out and back in as demo, then clicked Reset. The toast said "Clock reset to real time", and the card showed "Business date: 03 Nov 2026" with no "simulated" label.

After the reset the Overdue count was **14**. INVOICE-B is not due until 04 Nov 2026 but is still marked Overdue (D1).

![TC-20 clock reset](evidence/TC-20-clock-reset.png)
*Clock reset; the Overdue count is still 14.*

## 4. Defects found

### D1 — Invoices stay "Overdue" after the clock is reset (Medium)
**Steps**
1. Create and send an invoice due tomorrow.
2. Click "+7 days", then "Run overdue check now".
3. Click Reset.
4. Open the invoice, the dashboard and Invoices filtered by Overdue.

**Expected:** after the reset the invoice is back to Sent, and the dashboard status count agrees with the age buckets.

**Actual:** the invoice stays Overdue. At the start of this run the dashboard showed 13 Overdue, but only 5 invoices were past due by age. After TC-20 the count is 14.

The invoices marked Overdue before their due date are INV-2026-0004, 0005, 0016, 0017, 0018, 0019, 0020, 0023 and 0026 (all due 04–06 Nov). They were flipped by earlier time-travel runs, and by this one.

**Impact:** wrong status badges and counts, a misleading dashboard, and each test run leaves the data worse for the next one.

**Evidence:** [TC-03](evidence/TC-03-dashboard.png), [TC-12](evidence/TC-12-filter-overdue.png), [TC-20](evidence/TC-20-clock-reset.png).

### D2 — Invoices filter row overflows on desktop (Low)
**Steps:** open Invoices in a 1150px-wide viewport.

**Expected:** the page fits, with no sideways scrolling.

**Actual:** a horizontal scrollbar appears. The document is 1238px wide in an 1150px viewport, and the second "Issued to" date field is cut off.

**Evidence:** [TC-12](evidence/TC-12-filter-overdue.png), [TC-18](evidence/TC-18-new-business-empty.png).

### D3 — The demo time machine is shared by all businesses (Medium)
**Steps:** move the clock as the demo business, then register or sign in as a different business.

**Expected:** each business has its own simulated date, or the feature is limited to the demo account.

**Actual:** the new business also showed "10 Nov 2026 (+7 days simulated)", and its user had the Reset and +N buttons. Any business can change the date that every other business sees.

**Evidence:** TC-18 (dashboard of QA Business 1514).

### D4 — Server error banner stays visible during client validation (Low)
**Steps:** sign in with a wrong password, then submit an invalid email with an empty password.

**Expected:** the old "Invalid email or password" banner is cleared.

**Actual:** the banner stays visible alongside the new field errors.

**Evidence:** [TC-02](evidence/TC-02-login-validation.png).

### D5 — Delete-customer dialog stays open after the server refuses (Low)
**Steps:** try to delete a customer who has invoices.

**Expected:** the dialog closes, and the error toast shows.

**Actual:** the toast shows, but the "Delete customer?" dialog stays open with the Delete button still active.

**Evidence:** [TC-13](evidence/TC-13-delete-blocked.png).

### D6 — Cancelled invoice still shows a balance due (Low)
**Steps:** cancel an unpaid draft.

**Expected:** Balance due shows R 0,00, is hidden, or is labelled as void.

**Actual:** the invoice page and the list's Balance column both show R 115,00 for INV-2026-0025.

**Evidence:** [TC-11](evidence/TC-11-cancelled.png).

## 5. Test data created
| Item | Value |
|---|---|
| Customer | QA Customer 1514 · qa1514@example.com · +27 82 000 1514 · Stellenbosch |
| Throwaway customer | Delete Me 1514 (deleted) |
| INVOICE-A | INV-2026-0024, R 3 700,00, **Paid** (EFT 1000 ref QA-1514-1, then EFT 2700) · /invoices/6a24f915-8220-4907-9ae1-71765288d5f0 |
| INVOICE-B | INV-2026-0026, R 1 150,00, due 04 Nov 2026, **Overdue** (still Overdue after the reset; see D1) · /invoices/aca10534-b21a-478b-af90-0f509467668c |
| INVOICE-C | INV-2026-0025, R 115,00, **Cancelled** ("QA cancellation") |
| Business profile | Phone changed to +27 21 555 1514 (VAT still 4123456789) |
| Registered business | QA Business 1514 · owner QA Tester · qa-owner1514@example.com |
| Clock | Reset to real time (03 Nov 2026) |

## 6. Evidence files
All 23 screenshots are in `docs/testing/evidence/`:

TC-01-login-error, TC-02-login-validation, TC-03-dashboard, TC-04-customer-created, TC-05-invoice-form-totals, TC-05-draft-invoice, TC-07-invoice-sent, TC-08-partial-payment, TC-09-paid, TC-10-pdf, TC-11-cancelled, TC-12-filter-overdue, TC-13-delete-blocked, TC-14-overdue-run, TC-14-invoice-overdue, TC-15-bell-dropdown, TC-15-notifications-page, TC-16-no-duplicates, TC-17-profile-saved, TC-18-new-business-empty, TC-18-tenant-isolation, TC-19-mobile, TC-20-clock-reset (all `.png`).
