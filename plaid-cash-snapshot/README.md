# Plaid Cash Snapshot Backend

A backend service that integrates with Plaid's Sandbox API to provide recurring transaction detection and cash flow analysis.

## Features

- **Plaid Integration**: Link accounts, sync transactions, get real-time balances
- **Recurring Transaction Detection**: Both Plaid API and custom algorithm detection
- **Cash Flow Analysis**: Automatic calculation of checking, savings, and credit balances
- **PostgreSQL Storage**: Secure storage with encrypted access tokens
- **Database Views**: Easy data access through pre-built views

## API Endpoints

### Core Endpoints
- `POST /api/v1/link-token` - Create Plaid link token
- `POST /api/v1/exchange` - Exchange public token for access token
- `POST /api/v1/sync` - Sync transactions from Plaid
- `GET /api/v1/balances/summary` - Get cash snapshot summary
- `GET /api/v1/recurring?type=inflow|outflow` - Get recurring transactions

### Additional Endpoints
- `GET /health` - Health check
- `GET /` - Give all endpoint 
