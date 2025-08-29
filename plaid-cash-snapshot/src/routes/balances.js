/**
 * Balance Routes
 * Handles balance summary calculations
 */

const express = require("express");
const plaidService = require("../services/plaidService");
const db = require("../database/connection");
const encryption = require("../utils/encryption");
const logger = require("../utils/logger");
const { AppError } = require("../middleware/errorHandler");

const router = express.Router();

/**
 * GET /balances/summary
 * Get cash snapshot summary for user
 */
router.get("/balances/summary", async (req, res, next) => {
  try {
    const { user_id } = req.query;

    if (!user_id) {
      throw new AppError("User ID is required", 400);
    }

    // Get all items for the user
    const itemsQuery = `
       SELECT id, item_id, access_token
       FROM items 
       WHERE user_id = $1
     `;

    const itemsResult = await db.query(itemsQuery, [user_id]);

    if (itemsResult.rows.length === 0) {
      throw new AppError("No linked accounts found for user", 404);
    }

    let allBalances = [];
    const balanceTimestamp = new Date();

    // Fetch fresh balance data from Plaid for each item
    for (const item of itemsResult.rows) {
      try {
        const accessToken = encryption.decrypt(item.access_token);
        const balanceData = await plaidService.getBalances(accessToken);

        // Store balance data in database
        for (const account of balanceData.accounts) {
          // Get account UUID
          const accountQuery = `
             SELECT id FROM accounts 
             WHERE account_id = $1 AND user_id = $2
           `;

          const accountResult = await db.query(accountQuery, [
            account.account_id,
            user_id,
          ]);

          if (accountResult.rows.length > 0) {
            const accountUuid = accountResult.rows[0].id;

            // Store balance record
            //  const balanceQuery = `
            //    INSERT INTO account_balances (
            //      user_id, account_id, available, current_balance, limit_amount,
            //      iso_currency_code, unofficial_currency_code, last_updated_datetime
            //    )
            //    VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            //  `;

            //  await db.query(balanceQuery, [
            //    user_id,
            //    accountUuid,
            //    account.balances.available,
            //    account.balances.current,
            //    account.balances.limit,
            //    account.balances.iso_currency_code,
            //    account.balances.unofficial_currency_code,
            //    balanceTimestamp
            //  ]);
            const balanceQuery = `
  INSERT INTO account_balances (
    user_id, account_id, available, current_balance, limit_amount,
    iso_currency_code, unofficial_currency_code, last_updated_datetime
  )
  VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
  ON CONFLICT (user_id, account_id) 
  DO UPDATE SET
    available = EXCLUDED.available,
    current_balance = EXCLUDED.current_balance,
    limit_amount = EXCLUDED.limit_amount,
    iso_currency_code = EXCLUDED.iso_currency_code,
    unofficial_currency_code = EXCLUDED.unofficial_currency_code,
    last_updated_datetime = EXCLUDED.last_updated_datetime
`;

            await db.query(balanceQuery, [
              user_id,
              accountUuid,
              account.balances.available,
              account.balances.current,
              account.balances.limit,
              account.balances.iso_currency_code,
              account.balances.unofficial_currency_code,
              balanceTimestamp,
            ]);

            // Add to processing array
            allBalances.push({
              account_id: account.account_id,
              type: account.type,
              subtype: account.subtype,
              name: account.name,
              balances: account.balances,
            });
          }
        }
      } catch (itemError) {
        logger.error(
          `Failed to fetch balances for item ${item.item_id}:`,
          itemError
        );
      }
    }

    // Calculate summary using the view
    const summaryQuery = `
       SELECT 
         chequing_total,
         savings_total, 
         credit_cards_total_owed,
         net_cash,
         as_of
       FROM cash_snapshot_v 
       WHERE user_id = $1
     `;

    const summaryResult = await db.query(summaryQuery, [user_id]);

    let summary;
    if (summaryResult.rows.length > 0) {
      summary = summaryResult.rows[0];
    } else {
      // Calculate manually if view doesn't have data yet
      summary = await calculateManualSummary(allBalances, balanceTimestamp);
    }

    // Format response
    const response = {
      success: true,
      data: {
        user_id: user_id,
        chequing_total: parseFloat(summary.chequing_total || 0),
        savings_total: parseFloat(summary.savings_total || 0),
        credit_cards_total_owed: parseFloat(
          summary.credit_cards_total_owed || 0
        ),
        net_cash: parseFloat(summary.net_cash || 0),
        as_of: summary.as_of || balanceTimestamp,
        account_breakdown: allBalances.map((account) => ({
          account_id: account.account_id,
          name: account.name,
          type: account.type,
          subtype: account.subtype,
          current_balance: account.balances.current,
          available: account.balances.available,
        })),
      },
    };

    res.json(response);

    logger.info("Balance summary generated", {
      userId: user_id,
      accountCount: allBalances.length,
      netCash: summary.net_cash,
    });
  } catch (error) {
    logger.error("Balance summary failed:", error);
    next(error);
  }
});

/**
 * GET /balances/accounts
 * Get detailed balance information for all accounts
 */
router.get("/balances/accounts", async (req, res, next) => {
  try {
    const { user_id } = req.query;

    if (!user_id) {
      throw new AppError("User ID is required", 400);
    }

    const query = `
       SELECT 
         a.account_id,
         a.name,
         a.official_name,
         a.type,
         a.subtype,
         a.mask,
         ab.current_balance,
         ab.available,
         ab.limit_amount,
         ab.iso_currency_code,
         ab.last_updated_datetime,
         i.institution_name
       FROM accounts a
       LEFT JOIN LATERAL (
         SELECT * FROM account_balances ab2
         WHERE ab2.account_id = a.id
         ORDER BY ab2.created_at DESC
         LIMIT 1
       ) ab ON true
       JOIN items i ON a.item_id = i.id
       WHERE a.user_id = $1
       ORDER BY a.type, a.subtype, a.name
     `;

    const result = await db.query(query, [user_id]);

    const accounts = result.rows.map((row) => ({
      account_id: row.account_id,
      name: row.name,
      official_name: row.official_name,
      type: row.type,
      subtype: row.subtype,
      mask: row.mask,
      institution: row.institution_name,
      current_balance: parseFloat(row.current_balance || 0),
      available: parseFloat(row.available || 0),
      limit_amount: parseFloat(row.limit_amount || 0),
      currency: row.iso_currency_code || "USD",
      last_updated: row.last_updated_datetime,
    }));

    res.json({
      success: true,
      data: {
        user_id: user_id,
        accounts: accounts,
        total_accounts: accounts.length,
      },
    });
  } catch (error) {
    logger.error("Account balances fetch failed:", error);
    next(error);
  }
});

/**
 * Helper function to calculate summary manually
 */
async function calculateManualSummary(balances, timestamp) {
  let chequing_total = 0;
  let savings_total = 0;
  let credit_cards_total_owed = 0;

  for (const account of balances) {
    const balance = parseFloat(account.balances.current || 0);

    if (account.type === "depository") {
      if (account.subtype === "checking") {
        chequing_total += balance;
      } else if (account.subtype === "savings") {
        savings_total += balance;
      }
    } else if (account.type === "credit") {
      credit_cards_total_owed += balance;
    }
  }

  const net_cash = chequing_total + savings_total - credit_cards_total_owed;

  return {
    chequing_total,
    savings_total,
    credit_cards_total_owed,
    net_cash,
    as_of: timestamp,
  };
}

// Attach helper function to router
router.calculateManualSummary = calculateManualSummary;

module.exports = router;
