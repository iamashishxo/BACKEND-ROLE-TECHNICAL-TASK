/**
 * Authentication Routes
 * Simple authentication for demo purposes
 */

 const express = require('express');
 const { v4: uuidv4 } = require('uuid');
 const db = require('../database/connection');
 const logger = require('../utils/logger');
 const { AppError } = require('../middleware/errorHandler');
 
 const router = express.Router();
 

 router.post('/register', async (req, res, next) => {
   try {
     const userId = uuidv4();
 
     // Create user in database
     const query = `
       INSERT INTO users (id) 
       VALUES ($1) 
       RETURNING id, created_at
     `;
     
     const result = await db.query(query, [userId]);
     const user = result.rows[0];
 
     res.status(201).json({
       success: true,
       data: {
         user_id: user.id,
         created_at: user.created_at,
         message: 'User created successfully'
       }
     });
 
     logger.info('User registered', { userId: user.id });
 
   } catch (error) {
     logger.error('User registration failed:', error);
     next(error);
   }
 });
 

 router.get('/user/:user_id', async (req, res, next) => {
   try {
     const { user_id } = req.params;
 
     const query = `
       SELECT 
         u.id,
         u.created_at,
         u.updated_at,
         COUNT(DISTINCT i.id) as linked_items,
         COUNT(DISTINCT a.id) as total_accounts
       FROM users u
       LEFT JOIN items i ON u.id = i.user_id
       LEFT JOIN accounts a ON u.id = a.user_id
       WHERE u.id = $1
       GROUP BY u.id, u.created_at, u.updated_at
     `;
 
     const result = await db.query(query, [user_id]);
 
     if (result.rows.length === 0) {
       throw new AppError('User not found', 404);
     }
 
     const user = result.rows[0];
 
     res.json({
       success: true,
       data: {
         user_id: user.id,
         created_at: user.created_at,
         updated_at: user.updated_at,
         linked_items: parseInt(user.linked_items),
         total_accounts: parseInt(user.total_accounts)
       }
     });
 
   } catch (error) {
     logger.error('Get user failed:', error);
     next(error);
   }
 });
 

 router.get('/users', async (req, res, next) => {
   try {
     const query = `
       SELECT 
         u.id,
         u.created_at,
         COUNT(DISTINCT i.id) as linked_items,
         COUNT(DISTINCT a.id) as total_accounts
       FROM users u
       LEFT JOIN items i ON u.id = i.user_id
       LEFT JOIN accounts a ON u.id = a.user_id
       GROUP BY u.id, u.created_at
       ORDER BY u.created_at DESC
       LIMIT 50
     `;
 
     const result = await db.query(query);
 
     const users = result.rows.map(user => ({
       user_id: user.id,
       created_at: user.created_at,
       linked_items: parseInt(user.linked_items),
       total_accounts: parseInt(user.total_accounts)
     }));
 
     res.json({
       success: true,
       data: {
         users: users,
         total: users.length
       }
     });
 
   } catch (error) {
     logger.error('List users failed:', error);
     next(error);
   }
 });
 
 module.exports = router;
