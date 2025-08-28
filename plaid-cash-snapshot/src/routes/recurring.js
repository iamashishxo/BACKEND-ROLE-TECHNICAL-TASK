// src/routes/recurring.js

const express = require('express');
const router = express.Router();
const db = require('../database/connection');   // ✅ CommonJS import
const plaidService = require('../services/plaidService');
const logger = require('../utils/logger');
const AppError = require('../middleware/errorHandler');

// ✅ Get recurring transactions (Plaid + Custom)
router.get('/recurring', async (req, res, next) => {
  try {
    const { user_id, type } = req.query;

    if (!user_id) {
      throw new AppError('User ID is required', 400);
    }

    if (type && !['inflow', 'outflow'].includes(type)) {
      throw new AppError('Type must be either "inflow" or "outflow"', 400);
    }

    // 🔹 Call Plaid + Custom detection
    let plaidRecurring = await getPlaidRecurringTransactions(user_id);
    let customRecurring = await detectCustomRecurringTransactions(user_id, type);

    let recurringTransactions = [];

    if (plaidRecurring && plaidRecurring.length > 0) {
      recurringTransactions = recurringTransactions.concat(
        plaidRecurring.filter(tx => !type || tx.direction === type)
      );
    }

    if (customRecurring && customRecurring.length > 0) {
      recurringTransactions = recurringTransactions.concat(
        customRecurring.filter(tx => !type || tx.direction === type)
      );
    }

    // 🔹 Deduplicate
    const uniqueTransactions = removeDuplicateRecurring(recurringTransactions);

    // 🔹 Sort by avg amount
    uniqueTransactions.sort((a, b) => Math.abs(b.avg_amount) - Math.abs(a.avg_amount));

    res.json({
      success: true,
      data: {
        user_id: user_id,
        type: type || 'all',
        recurring_transactions: uniqueTransactions,
        total_streams: uniqueTransactions.length,
        detection_methods: {
          plaid_api: plaidRecurring ? plaidRecurring.length : 0,
          custom_detector: customRecurring ? customRecurring.length : 0
        }
      }
    });

    logger.info('Recurring transactions retrieved', {
      userId: user_id,
      type: type,
      totalStreams: uniqueTransactions.length
    });

  } catch (error) {
    logger.error('Get recurring transactions failed:', error);
    next(error);
  }
});

// ✅ Run detection manually
router.post('/recurring/detect', async (req, res, next) => {
  try {
    const { user_id, force_refresh } = req.body;

    if (!user_id) {
      throw new AppError('User ID is required', 400);
    }

    if (force_refresh) {
      await db.query('DELETE FROM recurring_transactions WHERE user_id = $1', [user_id]);
    }

    const detected = await runRecurringDetection(user_id);

    res.json({
      success: true,
      data: {
        user_id: user_id,
        detected_streams: detected.length,
        recurring_transactions: detected
      }
    });

    logger.info('Recurring detection completed', {
      userId: user_id,
      detectedStreams: detected.length
    });

  } catch (error) {
    logger.error('Recurring detection failed:', error);
    next(error);
  }
});

/* 
|--------------------------------------------------------------------------
| Helper Functions
|--------------------------------------------------------------------------
*/

// 🔹 Call Plaid recurring endpoint
async function getPlaidRecurringTransactions(userId) {
  try {
    const itemResult = await db.query(
      'SELECT access_token FROM items WHERE user_id = $1 LIMIT 1',
      [userId]
    );

    if (itemResult.rows.length === 0) {
      throw new AppError('No Plaid item found for user', 404);
    }

    const accessToken = itemResult.rows[0].access_token;

    const plaidData = await plaidService.getRecurringTransactions(accessToken);

    return plaidData.inflow_streams.concat(plaidData.outflow_streams).map(stream => ({
      stream_id: stream.stream_id,
      description: stream.description,
      merchant_name: stream.merchant_name,
      category: stream.category,
      avg_amount: stream.average_amount.amount,
      currency: stream.average_amount.iso_currency_code,
      first_date: stream.first_date,
      last_date: stream.last_date,
      frequency: stream.frequency,
      status: stream.status,
      direction: stream.stream_type, // inflow | outflow
      source: 'plaid'
    }));

  } catch (error) {
    logger.error('Plaid recurring fetch failed:', error);
    return [];
  }
}

// 🔹 Custom recurring detection
// 🔹 Custom recurring detection (fixed: no make_interval, proper casts)
async function detectCustomRecurringTransactions(userId, type) {
  try {
    const query = `
      WITH tx AS (
        SELECT
          merchant_name,
          amount,
          date
        FROM transactions
        WHERE user_id = $1
          AND merchant_name IS NOT NULL
          AND merchant_name <> ''
      ),
      gaps AS (
        SELECT
          merchant_name,
          amount,
          date,
          (date - LAG(date) OVER (PARTITION BY merchant_name ORDER BY date))::int AS gap_days
        FROM tx
      ),
      agg AS (
        SELECT
          merchant_name,
          COUNT(*) AS occurrences,                               -- count of gaps (>=2 ⇒ >=3 txns)
          ROUND(AVG(amount)::numeric, 2) AS avg_amount,
          MIN(date) AS first_date,
          MAX(date) AS last_date,
          ROUND(AVG(gap_days)::numeric, 0) AS avg_frequency_days -- average spacing in days
        FROM gaps
        WHERE gap_days IS NOT NULL
        GROUP BY merchant_name
        HAVING COUNT(*) >= 2
      )
      SELECT
        merchant_name,
        occurrences,
        avg_amount,
        first_date,
        last_date,
        avg_frequency_days,
        CASE WHEN avg_amount > 0 THEN 'inflow' ELSE 'outflow' END AS direction,
        -- add integer days to last_date
        (last_date + (COALESCE(avg_frequency_days, 30)::int) * INTERVAL '1 day')::date AS next_estimated_date
      FROM agg
      ORDER BY occurrences DESC;
    `;

    const result = await db.query(query, [userId]);

    let rows = result.rows;
    if (type) rows = rows.filter(r => r.direction === type);

    return rows.map(r => ({
      stream_id: null,
      description: r.merchant_name,
      merchant_name: r.merchant_name,
      avg_amount: Number(r.avg_amount),
      first_date: r.first_date,
      last_date: r.last_date,
      next_estimated_date: r.next_estimated_date,
      occurrences: Number(r.occurrences),
      frequency_days: r.avg_frequency_days ? Number(r.avg_frequency_days) : null,
      direction: r.direction,
      source: 'custom'
    }));

  } catch (error) {
    logger.error('Custom recurring detection failed:', error);
    return [];
  }
}


// 🔹 Deduplication
function removeDuplicateRecurring(transactions) {
  const seen = new Map();
  const unique = [];

  transactions.forEach(tx => {
    const key = tx.stream_id || `${tx.description}-${tx.avg_amount}`;
    if (!seen.has(key)) {
      seen.set(key, true);
      unique.push(tx);
    }
  });

  return unique;
}

// 🔹 Run detection (Plaid + Custom)
async function runRecurringDetection(userId) {
  const plaidRecurring = await getPlaidRecurringTransactions(userId);
  const customRecurring = await detectCustomRecurringTransactions(userId);

  let recurring = plaidRecurring.concat(customRecurring);
  recurring = removeDuplicateRecurring(recurring);

  // store in DB if needed
  return recurring;
}

module.exports = router;
