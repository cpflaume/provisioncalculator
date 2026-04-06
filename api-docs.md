# API Documentation

This service provides interactive API documentation via **Swagger UI** (SpringDoc OpenAPI).

## Accessing the Documentation

Once the service is running:

| Resource | Local URL | Production URL |
|----------|-----------|----------------|
| **Swagger UI** (interactive) | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | [https://provisioncalculator.copf-demo.de/swagger-ui.html](https://provisioncalculator.copf-demo.de/swagger-ui.html) |
| OpenAPI JSON spec | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | [https://provisioncalculator.copf-demo.de/v3/api-docs](https://provisioncalculator.copf-demo.de/v3/api-docs) |
| OpenAPI YAML spec | [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml) | [https://provisioncalculator.copf-demo.de/v3/api-docs.yaml](https://provisioncalculator.copf-demo.de/v3/api-docs.yaml) |

The Swagger UI lets you **try out every endpoint directly in the browser** — no curl or Postman needed.

## Quick Start

1. Open the Swagger UI in your browser
2. Use `acme` as the `tenantId` for testing
3. Follow these steps in order:

   | # | Endpoint | Action |
   |---|----------|--------|
   | 1 | `POST /settlements` | Create settlement with name `"March 2026"` |
   | 2 | `PUT /settlements/{id}/config` | Upload tree and rates (see example below) |
   | 3 | `POST /settlements/{id}/purchases` | Submit purchases |
   | 4 | `POST /settlements/{id}/calculate` | Calculate commissions |
   | 5 | `GET /settlements/{id}/calculation` | View results |
   | 6 | `POST /settlements/{id}/approve` | Finalize |

## Example Configuration

Use this as the request body for Step 2 (configure):

```json
{
  "rates": [
    { "depth": 1, "ratePercent": 10.0 },
    { "depth": 2, "ratePercent": 5.0 }
  ],
  "tree": [
    { "customerId": "alice", "parentCustomerId": null },
    { "customerId": "bob",   "parentCustomerId": "alice" },
    { "customerId": "carol", "parentCustomerId": "alice" },
    { "customerId": "diana", "parentCustomerId": "bob" }
  ]
}
```

This creates a tree:
```
Alice (root)
├── Bob
│   └── Diana
└── Carol
```

## Example Purchases

Use this for Step 3:

```json
{
  "purchases": [
    { "buyerCustomerId": "diana", "amount": 1000.00, "purchasedAt": "2026-03-15T14:30:00" },
    { "buyerCustomerId": "carol", "amount": 500.00,  "purchasedAt": "2026-03-16T09:00:00" }
  ]
}
```

## Expected Results

After calculating (Step 4):

| Recipient | Commission | Breakdown |
|-----------|-----------|-----------|
| Bob | 100.00 | Diana's 1,000 x 10% (depth 1) |
| Alice | 100.00 | Diana's 1,000 x 5% (depth 2) + Carol's 500 x 10% (depth 1) |
