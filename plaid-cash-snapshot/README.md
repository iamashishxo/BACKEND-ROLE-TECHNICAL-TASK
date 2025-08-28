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
- `GET /api/v1/auth/users` - List users (demo)
- `POST /api/v1/auth/register` - Create user (demo)
- `GET /api/v1/balances/accounts` - Get detailed account balances
- `POST /api/v1/recurring/detect` - Force recurring detection

## Quick Start

### Prerequisites
- Node.js 16+
- PostgreSQL 12+
- Plaid Sandbox account

### 1. Environment Setup

```bash
# Clone and setup
git clone <repository>
cd plaid-cash-snapshot

# Install dependencies
npm install

# Copy environment template
cp .env .env.local
```

### 2. Configure Environment

Edit `.env` with your settings:

```bash
# Plaid Configuration (provided)
PLAID_CLIENT_ID=6735026a8f4737001a0d676a
PLAID_SECRET=f88b809f7fe097f6265d9c33ed952f
PLAID_ENV=sandbox

# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=plaid_task
DB_USER=plaid_user
DB_PASSWORD=your_secure_password

# Security
ENCRYPTION_KEY=your_32_character_encryption_key_here
```

### 3. Database Setup

```bash
# Create PostgreSQL database
createdb plaid_task

# Initialize database schema
npm run db:init

# Or manually:
node src/database/init.js
```

### 4. Run the Application

```bash
# Development mode
npm run dev

# Production mode
npm start
```

The server will start on `http://localhost:3000`

## Usage Flow

### 1. Create a User
```bash
curl -X POST http://localhost:3000/api/v1/auth/register
```

### 2. Get Link Token
```bash
curl -X POST http://localhost:3000/api/v1/link-token \
  -H "Content-Type: application/json" \
  -d '{"user_id": "your-user-id"}'
```

### 3. Use Plaid Link (Frontend)
Use the link token with Plaid Link to connect accounts and get a public token.

### 4. Exchange Public Token
```bash
curl -X POST http://localhost:3000/api/v1/exchange \
  -H "Content-Type: application/json" \
  -d '{"public_token": "public-token-from-link", "user_id": "your-user-id"}'
```

### 5. Sync Transactions
```bash
curl -X POST http://localhost:3000/api/v1/sync \
  -H "Content-Type: application/json" \
  -d '{"user_id": "your-user-id"}'
```

### 6. Get Cash Snapshot
```bash
curl "http://localhost:3000/api/v1/balances/summary?user_id=your-user-id"
```

### 7. Get Recurring Transactions
```bash
curl "http://localhost:3000/api/v1/recurring?user_id=your-user-id&type=outflow"
```

## Database Views

### View 1: `recurring_streams_v`
```sql
SELECT * FROM recurring_streams_v WHERE user_id = 'your-user-id';
```

**Columns:**
- `user_id` - User identifier
- `direction` - 'inflow' or 'outflow'  
- `merchant` - Merchant name
- `frequency` - 'weekly', 'biweekly', 'monthly', 'quarterly'
- `avg_amount` - Average transaction amount
- `occurrences` - Number of transactions found
- `last_date` - Date of most recent transaction
- `next_estimated_date` - Predicted next transaction date
- `confidence` - Detection confidence (0.0 to 1.0)

### View 2: `cash_snapshot_v`
```sql
SELECT * FROM cash_snapshot_v WHERE user_id = 'your-user-id';
```

**Columns:**
- `user_id` - User identifier
- `chequing_total` - Sum of checking accounts
- `savings_total` - Sum of savings accounts  
- `credit_cards_total_owed` - Sum of credit card balances
- `net_cash` - chequing + savings - credit_owed
- `as_of` - Timestamp of balance data

## Project Structure

```
src/
├── server.js              # Main server entry point
├── app.js                 # Express app configuration
├── routes/                # API route handlers
│   ├── plaid.js          # Plaid integration endpoints
│   ├── balances.js       # Balance summary endpoints
│   ├── recurring.js      # Recurring transaction endpoints
│   └── auth.js           # User management (demo)
├── services/             # Business logic
│   └── plaidService.js   # Plaid API wrapper
├── database/             # Database related files
│   ├── connection.js     # Database connection
│   ├── init.sql         # Database schema
│   └── init.js          # Initialization script
├── middleware/           # Express middleware
│   └── errorHandler.js   # Global error handling
└── utils/               # Utility functions
    ├── logger.js        # Logging utility
    └── encryption.js    # Token encryption
```

## Key Features

### Security Features
- **Token Encryption**: Access tokens encrypted at rest using AES-256-GCM
- **Environment Variables**: Sensitive data stored in environment variables
- **SQL Injection Protection**: Parameterized queries throughout
- **Error Handling**: Comprehensive error handling with logging

### Recurring Transaction Detection

**Option A: Plaid API** (preferred when available)
- Uses `/transactions/recurring/get` endpoint
- High accuracy with official Plaid categorization
- Returns frequency, merchant, and confidence scores

**Option B: Custom Algorithm** (fallback)
- Analyzes transaction patterns over 6 months
- Groups by normalized merchant names
- Detects frequency based on date patterns
- Calculates confidence based on consistency
- Requires minimum 3 transactions for detection

### Cash Flow Calculation
- **Checking Total**: Sum of `type='depository' & subtype='checking'`
- **Savings Total**: Sum of `type='depository' & subtype='savings'`
- **Credit Owed**: Sum of `type='credit'` balances
- **Net Cash**: `checking + savings - credit_owed`

## Testing with Plaid Sandbox

### Sandbox Test Accounts

Use these credentials in Plaid Link:
- **Username**: `user_good`
- **Password**: `pass_good`
- **PIN**: `1234` (if required)

### Available Test Scenarios
- Multiple account types (checking, savings, credit)
- Historical transactions (~150 days)
- Various transaction patterns for recurring detection
- Different balance scenarios

### Webhook Configuration (Optional)
Set `PLAID_WEBHOOK_URL` in environment for real-time updates:
```bash
PLAID_WEBHOOK_URL=https://your-domain.com/webhooks/plaid
```

## Database Schema Details

### Tables
- `users` - User records
- `items` - Plaid item/institution links (encrypted access tokens)
- `accounts` - Individual accounts (checking, savings, credit, etc.)
- `transactions` - Transaction history
- `account_balances` - Balance history for tracking
- `recurring_transactions` - Custom detected recurring patterns

### Indexes
Optimized for common queries:
- User lookups
- Transaction date ranges
- Account type filtering
- Merchant name searches

## Development

### Running Tests
```bash
# Add your test commands here
npm test
```

### Database Migrations
```bash
# Reset database (caution: destroys data)
dropdb plaid_task && createdb plaid_task
npm run db:init
```

### Debugging
Set `LOG_LEVEL=debug` for detailed logging:
```bash
LOG_LEVEL=debug npm run dev
```

### Code Style
```bash
# Format code
npm run format

# Lint code  
npm run lint
```

## Production Considerations

### Environment Variables
```bash
NODE_ENV=production
PLAID_ENV=production  # When ready for production
DB_SSL=true           # Enable SSL for production DB
```

### Security Checklist
- [ ] Use strong encryption keys (32+ characters)
- [ ] Enable database SSL in production
- [ ] Set up proper CORS origins
- [ ] Use HTTPS for all endpoints
- [ ] Implement rate limiting
- [ ] Set up monitoring and alerting
- [ ] Regular security audits

### Performance
- Database connection pooling (configured)
- Indexes on frequently queried columns
- Pagination for large result sets
- Caching for recurring calculations

## API Response Examples

### Link Token Response
```json
{
  "success": true,
  "data": {
    "link_token": "link-sandbox-12345...",
    "expiration": "2024-01-01T12:00:00Z",
    "request_id": "req_123",
    "user_id": "user_456"
  }
}
```

### Balance Summary Response
```json
{
  "success": true,
  "data": {
    "user_id": "user_456",
    "chequing_total": 1250.50,
    "savings_total": 5000.00,
    "credit_cards_total_owed": 750.25,
    "net_cash": 5500.25,
    "as_of": "2024-01-01T12:00:00Z",
    "account_breakdown": [
      {
        "account_id": "acc_123",
        "name": "Checking Account",
        "type": "depository",
        "subtype": "checking", 
        "current_balance": 1250.50,
        "available": 1250.50
      }
    ]
  }
}
```

### Recurring Transactions Response
```json
{
  "success": true,
  "data": {
    "user_id": "user_456",
    "type": "outflow",
    "recurring_transactions": [
      {
        "direction": "outflow",
        "merchant": "netflix",
        "frequency": "monthly",
        "avg_amount": 15.99,
        "occurrences": 6,
        "last_date": "2024-01-01",
        "next_estimated_date": "2024-02-01",
        "confidence": 0.95,
        "source": "plaid_api"
      }
    ],
    "total_streams": 1,
    "detection_methods": {
      "plaid_api": 1,
      "custom_detector": 0
    }
  }
}
```

## Troubleshooting

### Common Issues

**Database Connection Failed**
- Check PostgreSQL is running: `brew services list | grep postgres`
- Verify connection settings in `.env`
- Ensure database exists: `psql -l | grep plaid_task`

**Plaid API Errors**
- Verify client ID and secret in `.env`
- Check Plaid environment (sandbox/development/production)
- Review Plaid API logs in dashboard

**Encryption Errors**
- Ensure `ENCRYPTION_KEY` is set and consistent
- Key must be 32+ characters for production

**No Recurring Transactions Found**
- Ensure sufficient transaction history (3+ months recommended)
- Check transaction sync completed successfully
- Try force re-detection: `POST /recurring/detect`

### Logs
Check application logs:
```bash
tail -f logs/app.log
```

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For questions about this implementation:
1. Check the troubleshooting section
2. Review Plaid documentation: https://plaid.com/docs/
3. Check application logs for detailed error messages

## Acceptance Criteria Checklist

- [ ] Can link a Plaid Sandbox Item
- [ ] `/sync` endpoint fetches ~150 days of transactions
- [ ] `/balances/summary` returns correct totals for checking, savings, and credit
- [ ] `/recurring` returns streams with all required fields
- [ ] `recurring_streams_v` view accessible in pgAdmin
- [ ] `cash_snapshot_v` view accessible in pgAdmin
- [ ] Net cash calculation: `chequing + savings - credit_owed`
- [ ] Supports both Plaid recurring API and custom detection
- [ ] Access tokens encrypted at rest
- [ ] Proper error handling and logging