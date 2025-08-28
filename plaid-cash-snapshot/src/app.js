/**
 * Express Application Configuration
 */

const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const logger = require("./utils/logger");
const errorHandler = require("./middleware/errorHandler");
const testRecurring = require("./routes/testRecurring");

// Import routes
const authRoutes = require("./routes/auth");
const plaidRoutes = require("./routes/plaid");
const balanceRoutes = require("./routes/balances");
const recurringRoutes = require("./routes/recurring");

const app = express();

// Security middleware
app.use(helmet());
app.use("/api/v1", testRecurring);

// CORS configuration
app.use(
  cors({
    origin: process.env.CORS_ORIGIN || "http://localhost:3000",
    credentials: true,
  })
);

// Body parsing middleware
app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ extended: true, limit: "10mb" }));

// Request logging middleware
app.use((req, res, next) => {
  logger.info(`${req.method} ${req.path} - ${req.ip}`);
  next();
});

// API routes
const apiPrefix = process.env.API_PREFIX || "/api/v1";

app.use(`${apiPrefix}/auth`, authRoutes);

app.use(`${apiPrefix}`, plaidRoutes);

app.use(`${apiPrefix}`, balanceRoutes);

app.use(`${apiPrefix}`, recurringRoutes);

// Health check endpoint
app.get("/health", (req, res) => {
  res.status(200).json({
    status: "OK",
    timestamp: new Date().toISOString(),
    environment: process.env.NODE_ENV || "development",
  });
});

// Root endpoint
app.get("/", (req, res) => {
  res.json({
    name: "Plaid Cash Snapshot API",
    version: "1.0.0",
    status: "Running",
    endpoints: {
      health: "/health",
      linkToken: `${apiPrefix}/link-token`,
      exchange: `${apiPrefix}/exchange`,
      sync: `${apiPrefix}/sync`,
      balances: `${apiPrefix}/balances/summary`,
      recurring: `${apiPrefix}/recurring`,
    },
  });
});

// 404 handler
app.use("*", (req, res) => {
  res.status(404).json({
    error: "Route not found",
    path: req.originalUrl,
  });
});

// Error handling middleware (should be last)
app.use(errorHandler);

module.exports = app;
