# Spring Order Confirmation

Spring Boot 3.4 application that receives Stripe `checkout.session.completed` webhooks, verifies the Stripe signature, extracts order details, and sends an order confirmation email through the Mailtrap template API.

## What It Does

1. Stripe sends `POST /api/stripe/webhook`
2. The app verifies the `Stripe-Signature` header against the raw request body
3. Duplicate event IDs are skipped with an in-memory Caffeine cache
4. For `checkout.session.completed`, the app parses:
   - `order_id`
   - `items`
   - `total`
   - `shipping_address`
5. The app sends a Mailtrap template email using a variables map
6. If mail delivery fails, the endpoint returns `500` so Stripe retries

The handler is payload-first. If the webhook payload does not include `line_items`, it falls back to Stripe's API and retrieves the Checkout Session with `expand[]=line_items`.

## Stack

- Java 21 target
- Spring Boot 3.4.5
- Stripe Java SDK 31.4.0
- Mailtrap Java SDK 1.2.0
- Caffeine cache
- Gradle

## Prerequisites

- JDK 21 or newer
- Stripe account in test mode
- Mailtrap account with:
  - API token
  - verified sender domain
  - email template UUID
- Stripe CLI

## Configuration

Set these environment variables before running the app:

```powershell
$env:STRIPE_SECRET_KEY="sk_test_..."
$env:STRIPE_WEBHOOK_SECRET="whsec_..."
$env:MAILTRAP_API_TOKEN="..."
$env:MAILTRAP_TEMPLATE_UUID="..."
$env:MAIL_FROM_ADDRESS="[email protected]"
$env:MAIL_FROM_NAME="Order Confirmation"
$env:MAIL_ORDER_RECIPIENT="[email protected]"
```

`MAIL_ORDER_RECIPIENT` is optional. It is only used as a fallback when Stripe does not include a customer email in the session.
`MAILTRAP_SANDBOX` is optional, by default it is set to false. It is only needed if you want to use Sandbox API.
`MAILTRAP_INBOX_ID` is required when `MAILTRAP_SANDBOX` is true.

## Run Locally

```powershell
.\gradlew.bat bootRun
```

The app starts on `http://localhost:8080`.

Webhook endpoint:

```text
POST http://localhost:8080/api/stripe/webhook
```

## Stripe Test Mode Setup

### 1. Create a test product

In Stripe test mode:

1. Open `Products`
2. Create a product, for example `Widget Pro`
3. Add a one-time price, for example `19.99 USD`

You can also do it with Stripe CLI:

```bash
stripe products create --name "Widget Pro"
stripe prices create --unit-amount 1999 --currency usd --product prod_xxx
```

### 2. Install Stripe CLI

Windows:

```powershell
scoop install stripe
stripe login
```

macOS:

```bash
brew install stripe/stripe-cli/stripe
stripe login
```

### 3. Forward webhooks locally

```bash
stripe listen --forward-to localhost:8080/api/stripe/webhook
```

Stripe CLI prints a signing secret like `whsec_...`. Copy that value into `STRIPE_WEBHOOK_SECRET`.

### 4. Trigger a test event

Synthetic event:

```bash
stripe trigger checkout.session.completed
```

Full end-to-end flow with real email and shipping details:

1. Create a Payment Link for your test product
2. Enable customer address collection
3. Open the link and pay with Stripe test card `4242 4242 4242 4242`
4. Use any future expiry date and any CVC

That produces a real `checkout.session.completed` webhook with customer data.

## Mailtrap Template Variables

The app sends a Mailtrap template email with this variables map:

| Variable | Type |
|---|---|
| `order_id` | string |
| `items` | array |
| `total` | string |
| `shipping_address` | object or `null` |

Example `items` entry:

```json
{
  "name": "Widget Pro",
  "quantity": 2,
  "price": "USD 39.98"
}
```

Example `shipping_address`:

```json
{
  "name": "Jane Doe",
  "line1": "123 Main St",
  "line2": "Apt 4",
  "city": "Springfield",
  "state": "IL",
  "postalCode": "62704",
  "country": "US"
}
```

## Behavior

- Valid `checkout.session.completed` event: returns `200 {"status":"ok"}`
- Duplicate event ID: returns `200 {"status":"already processed"}`
- Invalid or tampered signature: returns `400 {"error":"Invalid signature"}`
- Mailtrap failure or Stripe retrieval failure: returns `500 {"error":"Processing failed"}`

## Test

```powershell
.\gradlew.bat test
```

## Project Structure

```text
src/main/java/com/order/confirmation
  config/    Spring config, properties, cache
  email/     Mailtrap integration
  order/     Order payload models
  stripe/    Webhook verification, parsing, dedupe, Stripe fallback
  web/       REST controller
```
