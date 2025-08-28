#!/usr/bin/env node

/**
 * Plaid Cash Snapshot Server
 * Main server entry point
 */

 require('dotenv').config();
 const app = require('./app');
 const logger = require('./utils/logger');
 const db = require('./database/connection');
 
 const PORT = process.env.PORT || 3000;
 
 async function startServer() {
   try {
     // Test database connection
     await db.testConnection();
     logger.info('Database connection established successfully');
 
     // Start server
     const server = app.listen(PORT, () => {
       logger.info(`Server running on port ${PORT}`);
       logger.info(`Environment: ${process.env.NODE_ENV || 'development'}`);
       logger.info(`Plaid Environment: ${process.env.PLAID_ENV}`);
     });
 
     // Graceful shutdown
     process.on('SIGTERM', () => {
       logger.info('SIGTERM signal received: closing HTTP server');
       server.close(() => {
         logger.info('HTTP server closed');
         db.close();
         process.exit(0);
       });
     });
 
     process.on('SIGINT', () => {
       logger.info('SIGINT signal received: closing HTTP server');
       server.close(() => {
         logger.info('HTTP server closed');
         db.close();
         process.exit(0);
       });
     });
 
   } catch (error) {
     logger.error('Failed to start server:', error);
     process.exit(1);
   }
 }
 
 startServer();