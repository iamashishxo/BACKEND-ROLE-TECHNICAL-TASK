// src/services/transactionService.js
const logger = require("../utils/logger");

/**
 * Upsert a batch of transactions using provided DB client (within a transaction)
 * client: pg client returned by db.getClient() or provided by db.transaction callback
 * userId: UUID of the user (string)
 * itemDbId: internal items.id (int) - not Plaid item_id
 * transactions: array of Plaid tx objects (added or modified)
 */
async function upsertTransactions(client, userId, itemDbId, transactions = []) {
  for (const tx of transactions) {
    // 1) Find accounts.id (internal UUID) using Plaid account_id and user_id
    const accountRes = await client.query(
      `SELECT id FROM accounts WHERE account_id = $1 AND user_id = $2 LIMIT 1`,
      [tx.account_id, userId]
    );

    if (!accountRes.rows.length) {
      logger.warn("Account not found for transaction; skipping", {
        transaction_id: tx.transaction_id,
        account_id: tx.account_id,
        user_id: userId,
      });
      continue; // skip inserting if we don't have account mapping
    }

    const accountUuid = accountRes.rows[0].id;

    const upsertQuery = `
      INSERT INTO transactions (
        user_id, account_id, transaction_id, amount, iso_currency_code,
        unofficial_currency_code, date, authorized_date, name, merchant_name,
        category, subcategory, account_owner, pending, transaction_type
      )
      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)
      ON CONFLICT (transaction_id)
      DO UPDATE SET
        amount = EXCLUDED.amount,
        iso_currency_code = EXCLUDED.iso_currency_code,
        unofficial_currency_code = EXCLUDED.unofficial_currency_code,
        date = EXCLUDED.date,
        authorized_date = EXCLUDED.authorized_date,
        name = EXCLUDED.name,
        merchant_name = EXCLUDED.merchant_name,
        category = EXCLUDED.category,
        subcategory = EXCLUDED.subcategory,
        account_owner = EXCLUDED.account_owner,
        pending = EXCLUDED.pending,
        transaction_type = EXCLUDED.transaction_type,
        updated_at = CURRENT_TIMESTAMP
    `;

    const values = [
      userId,
      accountUuid,
      tx.transaction_id,
      tx.amount,
      tx.iso_currency_code,
      tx.unofficial_currency_code,
      tx.date,
      tx.authorized_date || null,
      tx.name,
      tx.merchant_name || null,
      tx.category ? JSON.stringify(tx.category) : null,
      tx.subcategory ? JSON.stringify(tx.subcategory) : null,
      tx.account_owner || null,
      tx.pending || false,
      tx.transaction_type || null,
    ];

    await client.query(upsertQuery, values);
  }
}

/**
 * Remove transactions by transaction_id (useful for removed items in Plaid sync)
 * client: pg client
 * transactionIds: array of strings
 */
async function removeTransactions(client, transactionIds = []) {
  if (!transactionIds.length) return;
  await client.query(
    "DELETE FROM transactions WHERE transaction_id = ANY($1)",
    [transactionIds]
  );
}

module.exports = {
  upsertTransactions,
  removeTransactions,
};
