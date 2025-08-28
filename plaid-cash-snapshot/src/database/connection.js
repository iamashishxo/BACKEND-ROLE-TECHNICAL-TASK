/**
 * PostgreSQL Database Connection
 */

 const { Pool } = require('pg');
 const logger = require('../utils/logger');
 
 class Database {
   constructor() {
     this.pool = null;
     this.config = {
       host: process.env.DB_HOST || 'localhost',
       port: process.env.DB_PORT || 5432,
       database: process.env.DB_NAME || 'plaid_task',
       user: process.env.DB_USER || 'plaid_user',
       password: process.env.DB_PASSWORD,
       max: 20, // maximum number of clients in the pool
       idleTimeoutMillis: 30000, // how long a client is allowed to remain idle
       connectionTimeoutMillis: 2000, // how long to try connecting before timing out
       ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false
     };
   }
 
   async connect() {
     try {
       this.pool = new Pool(this.config);
       
       // Test the connection
       const client = await this.pool.connect();
       client.release();
       
       logger.info('Database connection pool created successfully');
       return this.pool;
     } catch (error) {
       logger.error('Failed to create database connection pool:', error);
       throw error;
     }
   }
 
   async testConnection() {
     try {
       if (!this.pool) {
         await this.connect();
       }
       
       const result = await this.pool.query('SELECT NOW()');
       logger.info('Database connection test successful:', result.rows[0].now);
       return true;
     } catch (error) {
       logger.error('Database connection test failed:', error);
       throw error;
     }
   }
 
   async query(text, params) {
     try {
       if (!this.pool) {
         await this.connect();
       }
       
       const start = Date.now();
       const result = await this.pool.query(text, params);
       const duration = Date.now() - start;
       
       logger.debug('Executed query', {
         text: text.substring(0, 100) + (text.length > 100 ? '...' : ''),
         duration: `${duration}ms`,
         rows: result.rowCount
       });
       
       return result;
     } catch (error) {
       logger.error('Database query error:', {
         error: error.message,
         query: text.substring(0, 100) + (text.length > 100 ? '...' : ''),
         params: params
       });
       throw error;
     }
   }
 
   async getClient() {
     if (!this.pool) {
       await this.connect();
     }
     return this.pool.connect();
   }
 
   async transaction(callback) {
     const client = await this.getClient();
     
     try {
       await client.query('BEGIN');
       const result = await callback(client);
       await client.query('COMMIT');
       return result;
     } catch (error) {
       await client.query('ROLLBACK');
       throw error;
     } finally {
       client.release();
     }
   }
 
   close() {
     if (this.pool) {
       this.pool.end();
       logger.info('Database connection pool closed');
     }
   }
 }
 
 // Export singleton instance
 const database = new Database();
 module.exports = database;