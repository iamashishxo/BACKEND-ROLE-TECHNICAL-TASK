// /**
//  * Database Reset Utility
//  * Use this to clean up and reinitialize the database
//  */

//  require('dotenv').config();
//  const db = require('./connection');
//  const logger = require('../utils/logger');
 
//  async function resetDatabase() {
//    try {
//      logger.info('Starting database reset...');
 
//      // Drop all tables in correct order (respecting foreign keys)
//      const dropTablesQuery = `
//        DROP TABLE IF EXISTS account_balances CASCADE;
//        DROP TABLE IF EXISTS recurring_transactions CASCADE;
//        DROP TABLE IF EXISTS transactions CASCADE;
//        DROP TABLE IF EXISTS accounts CASCADE;
//        DROP TABLE IF EXISTS items CASCADE;
//        DROP TABLE IF EXISTS users CASCADE;
//      `;
 
//      await db.query(dropTablesQuery);
//      logger.info('All tables dropped');
 
//      // Drop views
//      const dropViewsQuery = `
//        DROP VIEW IF EXISTS recurring_streams_v CASCADE;
//        DROP VIEW IF EXISTS cash_snapshot_v CASCADE;
//      `;
 
//      await db.query(dropViewsQuery);
//      logger.info('All views dropped');
 
//      // Drop function
//      const dropFunctionQuery = `
//        DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
//      `;
 
//      await db.query(dropFunctionQuery);
//      logger.info('Functions dropped');
 
//      // Now run the initialization
//      const { initializeDatabase } = require('./init');
//      await initializeDatabase();
 
//      logger.info('Database reset completed successfully');
 
//    } catch (error) {
//      logger.error('Database reset failed:', error);
//      throw error;
//    }
//  }
 
//  // Run if called directly
//  if (require.main === module) {
//    resetDatabase()
//      .then(() => {
//        logger.info('Database reset script completed');
//        process.exit(0);
//      })
//      .catch((error) => {
//        logger.error('Database reset script failed:', error);
//        process.exit(1);
//      });
//  }
 
//  module.exports = {
//    resetDatabase
//  };