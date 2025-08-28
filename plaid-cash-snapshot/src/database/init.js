/**
 * Database Initialization Script
 * Run this to set up the database schema
 */

 require('dotenv').config();
 const fs = require('fs');
 const path = require('path');
 const db = require('./connection');
 const logger = require('../utils/logger');
 
 async function initializeDatabase() {
   try {
     logger.info('Starting database initialization...');
 
     // Read the SQL schema file
     const schemaPath = path.join(__dirname, 'init.sql');
     const schema = fs.readFileSync(schemaPath, 'utf8');
 
     // Execute the schema
     await db.query(schema);
 
     logger.info('Database schema created successfully');
 
     // Test the connection and views
     await testDatabase();
 
     logger.info('Database initialization completed successfully');
 
   } catch (error) {
     logger.error('Database initialization failed:', error);
     process.exit(1);
   }
 }
 
 async function testDatabase() {
   try {
     logger.info('Testing database setup...');
 
     // Test basic connection
     const timeResult = await db.query('SELECT NOW() as current_time');
     logger.info('Database connection test passed:', timeResult.rows[0].current_time);
 
     // Test tables exist
     const tablesResult = await db.query(`
       SELECT table_name 
       FROM information_schema.tables 
       WHERE table_schema = 'public' 
         AND table_type = 'BASE TABLE'
       ORDER BY table_name
     `);
     
     const tables = tablesResult.rows.map(row => row.table_name);
     logger.info('Created tables:', tables);
 
     // Test views exist
     const viewsResult = await db.query(`
       SELECT table_name 
       FROM information_schema.views 
       WHERE table_schema = 'public'
       ORDER BY table_name
     `);
     
     const views = viewsResult.rows.map(row => row.table_name);
     logger.info('Created views:', views);
 
     // Verify required tables
     const requiredTables = [
       'users',
       'items', 
       'accounts',
       'transactions',
       'account_balances',
       'recurring_transactions'
     ];
 
     const missingTables = requiredTables.filter(table => !tables.includes(table));
     if (missingTables.length > 0) {
       throw new Error(`Missing required tables: ${missingTables.join(', ')}`);
     }
 
     // Verify required views
     const requiredViews = ['recurring_streams_v', 'cash_snapshot_v'];
     const missingViews = requiredViews.filter(view => !views.includes(view));
     if (missingViews.length > 0) {
       throw new Error(`Missing required views: ${missingViews.join(', ')}`);
     }
 
     logger.info('All database components verified successfully');
 
   } catch (error) {
     logger.error('Database test failed:', error);
     throw error;
   }
 }
 
 // Run if called directly
 if (require.main === module) {
   initializeDatabase()
     .then(() => {
       logger.info('Database initialization script completed');
       process.exit(0);
     })
     .catch((error) => {
       logger.error('Database initialization script failed:', error);
       process.exit(1);
     });
 }
 
 module.exports = {
   initializeDatabase,
   testDatabase
 };