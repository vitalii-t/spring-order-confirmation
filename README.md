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

Set these environment variables before running the app.

Required:

- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `MAILTRAP_API_TOKEN`
- `MAILTRAP_TEMPLATE_UUID`
- `MAIL_FROM_ADDRESS`

Optional:

- `MAIL_FROM_NAME` default: `Order Confirmation`
- `MAIL_ORDER_RECIPIENT` used only when Stripe does not include a customer email
- `MAILTRAP_SANDBOX` default: `false`
- `MAILTRAP_INBOX_ID` required only when `MAILTRAP_SANDBOX=true`
- `SERVER_PORT` default: `8080`

Windows PowerShell:

```powershell
$env:STRIPE_SECRET_KEY="sk_test_..."
$env:STRIPE_WEBHOOK_SECRET="whsec_..."
$env:MAILTRAP_API_TOKEN="..."
$env:MAILTRAP_TEMPLATE_UUID="..."
$env:MAIL_FROM_ADDRESS="orders@example.com"
$env:MAIL_FROM_NAME="Order Confirmation"
$env:MAIL_ORDER_RECIPIENT="fallback@example.com"
$env:MAILTRAP_SANDBOX="false"
$env:SERVER_PORT="8080"
```

macOS/Linux:

```bash
export STRIPE_SECRET_KEY="sk_test_..."
export STRIPE_WEBHOOK_SECRET="whsec_..."
export MAILTRAP_API_TOKEN="..."
export MAILTRAP_TEMPLATE_UUID="..."
export MAIL_FROM_ADDRESS="orders@example.com"
export MAIL_FROM_NAME="Order Confirmation"
export MAIL_ORDER_RECIPIENT="fallback@example.com"
export MAILTRAP_SANDBOX="false"
export SERVER_PORT="8080"
```

If you use Mailtrap Sandbox API, also set:

```text
MAILTRAP_SANDBOX=true
MAILTRAP_INBOX_ID=0
```

## Run Locally

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

The app starts on `http://localhost:<SERVER_PORT>`. If `SERVER_PORT` is not set, it uses `8080`.

Webhook endpoint:

```text
POST http://localhost:<SERVER_PORT>/api/stripe/webhook
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

Windows PowerShell:

```powershell
$port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { 8080 }
stripe listen --forward-to "localhost:$port/api/stripe/webhook"
```

macOS/Linux:

```bash
stripe listen --forward-to localhost:${SERVER_PORT:-8080}/api/stripe/webhook
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

Windows PowerShell:

```powershell
.\gradlew.bat test
```

macOS/Linux:

```bash
./gradlew test
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
