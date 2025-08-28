/**
 * Global Error Handler Middleware
 */

 const logger = require('../utils/logger');

 class AppError extends Error {
   constructor(message, statusCode, isOperational = true) {
     super(message);
     this.statusCode = statusCode;
     this.isOperational = isOperational;
     this.name = this.constructor.name;
     
     Error.captureStackTrace(this, this.constructor);
   }
 }
 
 const errorHandler = (error, req, res, next) => {
   let err = { ...error };
   err.message = error.message;
 
   logger.error('Error caught by middleware:', {
     error: err.message,
     stack: error.stack,
     url: req.url,
     method: req.method,
     ip: req.ip,
     userAgent: req.get('User-Agent')
   });
 
   // Plaid API errors
   if (error.name === 'PlaidError') {
     const message = error.error_message || 'Plaid API Error';
     err = new AppError(message, 400);
   }
 
   // PostgreSQL errors
   if (error.code === '23505') { // Unique violation
     const message = 'Duplicate field value entered';
     err = new AppError(message, 400);
   }
 
   if (error.code === '23503') { // Foreign key violation
     const message = 'Invalid reference to related resource';
     err = new AppError(message, 400);
   }
 
   if (error.code === '23502') { // Not null violation
     const message = 'Required field missing';
     err = new AppError(message, 400);
   }
 
   // JWT errors
   if (error.name === 'JsonWebTokenError') {
     const message = 'Invalid token';
     err = new AppError(message, 401);
   }
 
   if (error.name === 'TokenExpiredError') {
     const message = 'Token expired';
     err = new AppError(message, 401);
   }
 
   // Validation errors
   if (error.name === 'ValidationError') {
     const message = Object.values(error.errors).map(val => val.message);
     err = new AppError(message, 400);
   }
 
   // Default to 500 server error
   if (!err.statusCode) {
     err.statusCode = 500;
   }
 
   res.status(err.statusCode).json({
     success: false,
     error: {
       message: err.message || 'Internal Server Error',
       ...(process.env.NODE_ENV === 'development' && { stack: error.stack })
     },
     timestamp: new Date().toISOString(),
     path: req.url
   });
 };
 
 // Export the function directly as default, and AppError as named export
 module.exports = errorHandler;
 module.exports.AppError = AppError;