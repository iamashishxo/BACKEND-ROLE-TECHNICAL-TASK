-- Plaid Cash Snapshot Database Schema
-- This file creates the necessary tables and views for the application

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Plaid items table (stores access tokens and item information)
CREATE TABLE IF NOT EXISTS items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id VARCHAR(255) UNIQUE NOT NULL,
    access_token TEXT NOT NULL, -- This will be encrypted
    institution_id VARCHAR(255),
    institution_name VARCHAR(255),
    cursor VARCHAR(255), -- For transactions sync
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Accounts table
CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    account_id VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    official_name VARCHAR(255),
    type VARCHAR(50) NOT NULL, -- 'depository', 'credit', 'loan', 'investment'
    subtype VARCHAR(50) NOT NULL, -- 'checking', 'savings', 'credit card', etc.
    mask VARCHAR(10),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    transaction_id VARCHAR(255) UNIQUE NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    iso_currency_code VARCHAR(3) DEFAULT 'USD',
    unofficial_currency_code VARCHAR(10),
    date DATE NOT NULL,
    authorized_date DATE,
    name VARCHAR(500) NOT NULL,
    merchant_name VARCHAR(255),
    category JSONB, -- Plaid categories as JSON array
    subcategory JSONB, -- Plaid subcategories
    account_owner VARCHAR(255),
    pending BOOLEAN DEFAULT FALSE,
    transaction_type VARCHAR(50), -- 'digital', 'place', 'special', 'unresolved'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Account balances table (for historical tracking)
CREATE TABLE IF NOT EXISTS account_balances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    available DECIMAL(12, 2),
    current_balance DECIMAL(12, 2) NOT NULL,
    limit_amount DECIMAL(12, 2),
    iso_currency_code VARCHAR(3) DEFAULT 'USD',
    unofficial_currency_code VARCHAR(10),
    last_updated_datetime TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Recurring transactions table (for custom detection)
CREATE TABLE IF NOT EXISTS recurring_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    merchant_name VARCHAR(255) NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('inflow', 'outflow')),
    frequency VARCHAR(20), -- 'weekly', 'biweekly', 'monthly', 'quarterly'
    avg_amount DECIMAL(12, 2) NOT NULL,
    min_amount DECIMAL(12, 2),
    max_amount DECIMAL(12, 2),
    occurrences INTEGER NOT NULL DEFAULT 1,
    last_date DATE NOT NULL,
    next_estimated_date DATE,
    confidence DECIMAL(3, 2) DEFAULT 0.0, -- 0.0 to 1.0
    category JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_items_user_id ON items(user_id);
CREATE INDEX IF NOT EXISTS idx_items_item_id ON items(item_id);
CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_account_id ON accounts(account_id);
CREATE INDEX IF NOT EXISTS idx_accounts_type_subtype ON accounts(type, subtype);
CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(date);
CREATE INDEX IF NOT EXISTS idx_transactions_merchant ON transactions(merchant_name);
CREATE INDEX IF NOT EXISTS idx_transactions_amount ON transactions(amount);
CREATE INDEX IF NOT EXISTS idx_account_balances_user_id ON account_balances(user_id);
CREATE INDEX IF NOT EXISTS idx_account_balances_account_id ON account_balances(account_id);
CREATE INDEX IF NOT EXISTS idx_recurring_user_id ON recurring_transactions(user_id);

-- Function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at (use OR REPLACE to handle existing triggers)
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_items_updated_at ON items;
CREATE TRIGGER update_items_updated_at BEFORE UPDATE ON items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_accounts_updated_at ON accounts;
CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_transactions_updated_at ON transactions;
CREATE TRIGGER update_transactions_updated_at BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_recurring_transactions_updated_at ON recurring_transactions;
CREATE TRIGGER update_recurring_transactions_updated_at BEFORE UPDATE ON recurring_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- VIEW 1: recurring_streams_v
-- CREATE OR REPLACE VIEW recurring_streams_v AS
-- SELECT 
--     user_id,
--     direction,
--     merchant_name as merchant,
--     frequency,
--     avg_amount,
--     occurrences,
--     last_date,
--     next_estimated_date,
--     confidence,
--     updated_at
-- FROM recurring_transactions
-- WHERE is_active = true
-- ORDER BY user_id, avg_amount DESC;
-- Recurring Streams View
CREATE OR REPLACE VIEW recurring_streams_v AS
WITH tx AS (
    SELECT
        user_id,
        merchant_name AS merchant,
        amount,
        date,
        (date - LAG(date) OVER (PARTITION BY user_id, merchant_name ORDER BY date))::int AS gap_days
    FROM transactions
    WHERE merchant_name IS NOT NULL
      AND merchant_name <> ''
      AND pending = false
),
agg AS (
    SELECT
        user_id,
        merchant,
        COUNT(*) AS occurrences,
        ROUND(AVG(amount)::numeric, 2) AS avg_amount,
        MIN(date) AS first_date,
        MAX(date) AS last_date,
        ROUND(AVG(gap_days)::numeric, 0) AS avg_frequency_days,
        STDDEV_POP(gap_days) AS gap_stddev
    FROM tx
    WHERE gap_days IS NOT NULL
    GROUP BY user_id, merchant
    HAVING COUNT(*) >= 2
)
SELECT
    user_id,
    CASE WHEN avg_amount > 0 THEN 'inflow' ELSE 'outflow' END AS direction,
    merchant,
    CASE 
        WHEN avg_frequency_days <= 8  THEN 'weekly'
        WHEN avg_frequency_days <= 16 THEN 'biweekly'
        WHEN avg_frequency_days <= 35 THEN 'monthly'
        WHEN avg_frequency_days <= 100 THEN 'quarterly'
        ELSE 'irregular'
    END AS frequency,
    ABS(avg_amount) AS avg_amount,
    occurrences,
    last_date,
    (last_date + (COALESCE(avg_frequency_days, 30)::int * INTERVAL '1 day'))::date AS next_estimated_date,
    LEAST(
      1.0,
      0.5
      + (occurrences / 10.0)
      - (COALESCE(gap_stddev, 0) / NULLIF(avg_frequency_days, 0) * 0.2)
    )::numeric(3,2) AS confidence
FROM agg;



-- VIEW 2: cash_snapshot_v  
-- CREATE OR REPLACE VIEW cash_snapshot_v AS
-- WITH latest_balances AS (
--     SELECT DISTINCT ON (ab.account_id) 
--         ab.user_id,
--         ab.account_id,
--         ab.current_balance,
--         a.type,
--         a.subtype,
--         ab.created_at as as_of
--     FROM account_balances ab
--     JOIN accounts a ON ab.account_id = a.id
--     ORDER BY ab.account_id, ab.created_at DESC
-- ),
-- balance_summary AS (
--     SELECT 
--         user_id,
--         SUM(CASE 
--             WHEN type = 'depository' AND subtype = 'checking' 
--             THEN current_balance ELSE 0 
--         END) as chequing_total,
--         SUM(CASE 
--             WHEN type = 'depository' AND subtype = 'savings' 
--             THEN current_balance ELSE 0 
--         END) as savings_total,
--         SUM(CASE 
--             WHEN type = 'credit' 
--             THEN current_balance ELSE 0 
--         END) as credit_cards_total_owed,
--         MAX(as_of) as as_of
--     FROM latest_balances
--     GROUP BY user_id
-- )
-- SELECT 
--     user_id,
--     chequing_total,
--     savings_total,
--     credit_cards_total_owed,
--     (chequing_total + savings_total - credit_cards_total_owed) as net_cash,
--     as_of
-- FROM balance_summary
-- ORDER BY user_id;
-- Cash Snapshot View
-- Use the latest balance per account, then roll up by type/subtype
CREATE OR REPLACE VIEW cash_snapshot_v AS
WITH latest_balances AS (
  SELECT
    ab.user_id,
    ab.account_id,
    ab.current_balance,
    ab.available,
    ab.iso_currency_code,
    COALESCE(ab.last_updated_datetime, ab.created_at) AS as_of,
    ROW_NUMBER() OVER (
      PARTITION BY ab.account_id
      ORDER BY COALESCE(ab.last_updated_datetime, ab.created_at) DESC, ab.created_at DESC
    ) AS rn
  FROM account_balances ab
),
current_per_account AS (
  -- keep only the most recent row per account_id
  SELECT
    lb.user_id,
    lb.account_id,
    lb.current_balance,
    lb.available,
    lb.iso_currency_code,
    lb.as_of
  FROM latest_balances lb
  WHERE lb.rn = 1
),
joined AS (
  -- join to accounts to filter by type/subtype
  SELECT
    cpa.user_id,
    a.type,
    a.subtype,
    cpa.current_balance,
    cpa.as_of
  FROM current_per_account cpa
  JOIN accounts a
    ON a.id = cpa.account_id  -- assumes accounts.id is the FK target
)
SELECT
  j.user_id,
  COALESCE(SUM(CASE WHEN j.type = 'depository' AND j.subtype = 'checking' THEN j.current_balance ELSE 0 END), 0) AS chequing_total,
  COALESCE(SUM(CASE WHEN j.type = 'depository' AND j.subtype = 'savings'  THEN j.current_balance ELSE 0 END), 0) AS savings_total,
  COALESCE(SUM(CASE WHEN j.type = 'credit'                                    THEN j.current_balance ELSE 0 END), 0) AS credit_cards_total_owed,
  (
    COALESCE(SUM(CASE WHEN j.type = 'depository' AND j.subtype = 'checking' THEN j.current_balance ELSE 0 END), 0) +
    COALESCE(SUM(CASE WHEN j.type = 'depository' AND j.subtype = 'savings'  THEN j.current_balance ELSE 0 END), 0) -
    COALESCE(SUM(CASE WHEN j.type = 'credit'                                    THEN j.current_balance ELSE 0 END), 0)
  ) AS net_cash,
  MAX(j.as_of) AS as_of
FROM joined j
GROUP BY j.user_id;

