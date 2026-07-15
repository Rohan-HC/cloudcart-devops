# CloudCart Container Security Scan

## Scanner

- Tool: Trivy
- Version: 0.72.0
- Scan type: Container image and repository filesystem
- Severity reviewed: HIGH and CRITICAL
- Date: YYYY-MM-DD

## Images scanned

- cloudcart-user-service:1.0.0
- cloudcart-product-service:1.0.0
- cloudcart-order-service:1.0.0
- cloudcart-payment-service:1.0.0
- cloudcart-notification-service:1.0.0

## Findings

| Image | Critical | High | Action |
|---|---:|---:|---|
| user-service | 0 | 0 | No action |
| product-service | 0 | 0 | No action |
| order-service | 0 | 0 | No action |
| payment-service | 0 | 0 | No action |
| notification-service | 0 | 0 | No action |

## Remediation performed

- Updated vulnerable base images where fixes were available.
- Updated affected Maven or npm dependencies.
- Rebuilt and rescanned all images.
- Confirmed that runtime containers use non-root users.

## Security gate

Deployment will fail when a fixable CRITICAL vulnerability is detected.