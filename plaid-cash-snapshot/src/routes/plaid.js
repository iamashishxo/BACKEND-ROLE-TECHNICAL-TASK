/**
 * Plaid API Routes
 * Handles link token creation, token exchange, and transaction syncing
 */

 const express = require('express');
 const { v4: uuidv4 } = require('uuid');
 const plaidService = require('../services/plaidService');
 const db = require('../database/connection');
 const encryption = require('../utils/encryption');
 const logger = require('../utils/logger');
 const { AppError } = require('../middleware/errorHandler');
 const transactionService = require('../services/transactionService');
 
 
 const router = express.Router();
 
 /**
  * POST /link-token
  * Create a Plaid link token for sandbox Link
  */
 router.post('/link-token', async (req, res, next) => {
   try {
     const { user_id } = req.body;
     
     // Generate user_id if not provided (for demo purposes)
     const userId = user_id || uuidv4();
     
     // Ensure user exists in database
     const userQuery = `
       INSERT INTO users (id) 
       VALUES ($1) 
       ON CONFLICT (id) DO NOTHING 
       RETURNING id
     `;
     await db.query(userQuery, [userId]);
 
     // Create link token
     const linkTokenData = await plaidService.createLinkToken(userId);
 
     res.json({
       success: true,
       data: {
         link_token: linkTokenData.link_token,
         expiration: linkTokenData.expiration,
         request_id: linkTokenData.request_id,
         user_id: userId
       }
     });
 
     logger.info('Link token created', { userId, requestId: linkTokenData.request_id });
 
   } catch (error) {
     logger.error('Link token creation failed:', error);
     next(error);
   }
 });
 
 /**
  * POST /exchange
  * Exchange public token for access token and store item data
  */
 router.post('/exchange', async (req, res, next) => {
   try {
     const { public_token, user_id } = req.body;
 
     if (!public_token) {
       throw new AppError('Public token is required', 400);
     }
 
     if (!user_id) {
       throw new AppError('User ID is required', 400);
     }
 
     // Exchange public token for access token
     const exchangeData = await plaidService.exchangePublicToken(public_token);
     const { access_token, item_id } = exchangeData;
 
     // Get item and institution information
     const itemData = await plaidService.getItem(access_token);
     const institution = itemData.item.institution_id ? 
       await plaidService.getInstitution(itemData.item.institution_id) : null;
 
     // Get accounts
     const accountsData = await plaidService.getAccounts(access_token);
 
     // Encrypt access token for storage
     const encryptedAccessToken = encryption.encrypt(access_token);
 
     // Start database transaction
     await db.transaction(async (client) => {
       // Store item information
       const itemQuery = `
         INSERT INTO items (user_id, item_id, access_token, institution_id, institution_name)
         VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (item_id) 
         DO UPDATE SET 
           access_token = EXCLUDED.access_token,
           institution_id = EXCLUDED.institution_id,
           institution_name = EXCLUDED.institution_name,
           updated_at = CURRENT_TIMESTAMP
         RETURNING id
       `;
 
       const itemResult = await client.query(itemQuery, [
         user_id,
         item_id,
         encryptedAccessToken,
         itemData.item.institution_id,
         institution ? institution.name : null
       ]);
 
       const dbItemId = itemResult.rows[0].id;
 
       // Store accounts
       for (const account of accountsData.accounts) {
         const accountQuery = `
           INSERT INTO accounts (user_id, item_id, account_id, name, official_name, type, subtype, mask)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
           ON CONFLICT (account_id)
           DO UPDATE SET
             name = EXCLUDED.name,
             official_name = EXCLUDED.official_name,
             type = EXCLUDED.type,
             subtype = EXCLUDED.subtype,
             mask = EXCLUDED.mask,
             updated_at = CURRENT_TIMESTAMP
         `;
 
         await client.query(accountQuery, [
           user_id,
           dbItemId,
           account.account_id,
           account.name,
           account.official_name,
           account.type,
           account.subtype,
           account.mask
         ]);
       }
     });
 
     res.json({
       success: true,
       data: {
         item_id: item_id,
         accounts: accountsData.accounts.length,
         institution: institution ? institution.name : 'Unknown',
         message: 'Account successfully linked'
       }
     });
 
     logger.info('Token exchange successful', {
       userId: user_id,
       itemId: item_id,
       accountCount: accountsData.accounts.length
     });
 
   } catch (error) {
     logger.error('Token exchange failed:', error);
     next(error);
   }
 });
 
 /**
  * POST /sync
  * Run transaction sync for all user items
  */
  router.post('/sync', async (req, res, next) => {
    try {
      const { user_id, full_sync } = req.body;
  
      if (!user_id) {
        throw new AppError('User ID is required', 400);
      }
  
      // Get all items for user
      const itemsQuery = `
        SELECT id, item_id, access_token, cursor
        FROM items 
        WHERE user_id = $1
      `;
      const itemsResult = await db.query(itemsQuery, [user_id]);
  
      if (itemsResult.rows.length === 0) {
        throw new AppError('No linked accounts found for user', 404);
      }
  
      let totalTransactions = 0;
      const syncResults = [];
  
      for (const item of itemsResult.rows) {
        try {
          const accessToken = encryption.decrypt(item.access_token);
          // start from stored cursor unless full_sync requested
          let cursor = full_sync ? null : item.cursor;
          let hasMore = true;
          let itemTransactions = 0;
  
          // paginate until has_more === false
          while (hasMore) {
            const syncData = await plaidService.syncTransactions(accessToken, cursor);
  
            // Use a DB transaction per sync page so it can commit/rollback safely
            await db.transaction(async (client) => {
              // added
              if (syncData.added && syncData.added.length) {
                await transactionService.upsertTransactions(client, user_id, item.id, syncData.added);
                itemTransactions += syncData.added.length;
              }
  
              // modified
              if (syncData.modified && syncData.modified.length) {
                await transactionService.upsertTransactions(client, user_id, item.id, syncData.modified);
              }
  
              // removed
              if (syncData.removed && syncData.removed.length) {
                const removedIds = syncData.removed.map(r => r.transaction_id);
                await transactionService.removeTransactions(client, removedIds);
              }
  
              // update cursor for this item in DB
              await client.query(
                'UPDATE items SET cursor = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
                [syncData.next_cursor, item.id]
              );
            });
  
            // advance cursor / loop flag
            cursor = syncData.next_cursor;
            hasMore = !!syncData.has_more;
          }
  
          syncResults.push({
            item_id: item.item_id,
            transactions_synced: itemTransactions,
            cursor: cursor
          });
  
          totalTransactions += itemTransactions;
  
        } catch (itemError) {
          logger.error(`Failed to sync item ${item.item_id}:`, itemError);
          syncResults.push({
            item_id: item.item_id,
            error: itemError.message
          });
        }
      }
  
      res.json({
        success: true,
        data: {
          user_id: user_id,
          total_transactions_synced: totalTransactions,
          items_synced: syncResults.length,
          sync_results: syncResults,
          full_sync: !!full_sync
        }
      });
  
      logger.info('Transaction sync completed', {
        userId: user_id,
        totalTransactions,
        itemsCount: syncResults.length
      });
  
    } catch (error) {
      logger.error('Transaction sync failed:', error);
      next(error);
    }
  });
//  router.post('/sync', async (req, res, next) => {
//    try {
//      const { user_id, full_sync } = req.body;
 
//      if (!user_id) {
//        throw new AppError('User ID is required', 400);
//      }
 
//      // Get all items for user
//      const itemsQuery = `
//        SELECT id, item_id, access_token, cursor
//        FROM items 
//        WHERE user_id = $1
//      `;
     
//      const itemsResult = await db.query(itemsQuery, [user_id]);
 
//      if (itemsResult.rows.length === 0) {
//        throw new AppError('No linked accounts found for user', 404);
//      }
 
//      let totalTransactions = 0;
//      const syncResults = [];
 
//      for (const item of itemsResult.rows) {
//        try {
//          // Decrypt access token
//          const accessToken = encryption.decrypt(item.access_token);
         
//          // Use cursor for incremental sync, or null for full sync
//          let cursor = full_sync ? null : item.cursor;
//          let hasMore = true;
//          let itemTransactions = 0;
 
//          while (hasMore) {
//            // Sync transactions
//            const syncData = await plaidService.syncTransactions(accessToken, cursor);
           
//            // Update cursor
//            cursor = syncData.next_cursor;
 
//            // Process added transactions
//            for (const transaction of syncData.added) {
//              await this.upsertTransaction(user_id, transaction);
//              itemTransactions++;
//            }
 
//            // Process modified transactions
//            for (const transaction of syncData.modified) {
//              await this.upsertTransaction(user_id, transaction);
//            }
 
//            // Process removed transactions
//            for (const removedTx of syncData.removed) {
//              await db.query(
//                'DELETE FROM transactions WHERE transaction_id = $1',
//                [removedTx.transaction_id]
//              );
//            }
 
//            hasMore = syncData.has_more;
//          }
 
//          // Update cursor in database
//          await db.query(
//            'UPDATE items SET cursor = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
//            [cursor, item.id]
//          );
 
//          syncResults.push({
//            item_id: item.item_id,
//            transactions_synced: itemTransactions,
//            cursor: cursor
//          });
 
//          totalTransactions += itemTransactions;
 
//        } catch (itemError) {
//          logger.error(`Failed to sync item ${item.item_id}:`, itemError);
//          syncResults.push({
//            item_id: item.item_id,
//            error: itemError.message
//          });
//        }
//      }
 
//      res.json({
//        success: true,
//        data: {
//          user_id: user_id,
//          total_transactions_synced: totalTransactions,
//          items_synced: syncResults.length,
//          sync_results: syncResults,
//          full_sync: !!full_sync
//        }
//      });
 
//      logger.info('Transaction sync completed', {
//        userId: user_id,
//        totalTransactions,
//        itemsCount: syncResults.length
//      });
 
//    } catch (error) {
//      logger.error('Transaction sync failed:', error);
//      next(error);
//    }
//  });
 
 /**
  * Helper function to upsert transaction
  */
 async function upsertTransaction(userId, transaction) {
   // Get account UUID from account_id
   const accountQuery = `
     SELECT id FROM accounts 
     WHERE account_id = $1 AND user_id = $2
   `;
   
   const accountResult = await db.query(accountQuery, [transaction.account_id, userId]);
   
   if (accountResult.rows.length === 0) {
     logger.warn(`Account not found for transaction: ${transaction.transaction_id}`);
     return;
   }
 
   const accountUuid = accountResult.rows[0].id;
 
   const transactionQuery = `
     INSERT INTO transactions (
       user_id, account_id, transaction_id, amount, iso_currency_code, 
       unofficial_currency_code, date, authorized_date, name, merchant_name,
       category, subcategory, account_owner, pending, transaction_type
     )
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
     ON CONFLICT (transaction_id)
     DO UPDATE SET
       amount = EXCLUDED.amount,
       date = EXCLUDED.date,
       authorized_date = EXCLUDED.authorized_date,
       name = EXCLUDED.name,
       merchant_name = EXCLUDED.merchant_name,
       category = EXCLUDED.category,
       subcategory = EXCLUDED.subcategory,
       pending = EXCLUDED.pending,
       updated_at = CURRENT_TIMESTAMP
   `;
 
   await db.query(transactionQuery, [
     userId,
     accountUuid,
     transaction.transaction_id,
     transaction.amount,
     transaction.iso_currency_code,
     transaction.unofficial_currency_code,
     transaction.date,
     transaction.authorized_date,
     transaction.name,
     transaction.merchant_name,
     JSON.stringify(transaction.category),
     JSON.stringify(transaction.subcategory),
     transaction.account_owner,
     transaction.pending,
     transaction.transaction_type
   ]);
 }
 
 // Attach helper function to router for access
 router.upsertTransaction = upsertTransaction;
 
 module.exports = router;