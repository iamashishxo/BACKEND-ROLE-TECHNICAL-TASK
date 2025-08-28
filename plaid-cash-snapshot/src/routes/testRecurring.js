// src/routes/testRecurring.js
const express = require("express");
const { detectRecurringTransactions } = require("../utils/customRecurringDetector");
const mockTransactions = require("../utils/mockTransactions");

const router = express.Router();

router.get("/test-recurring", (req, res) => {
  const inflows = mockTransactions.filter((t) => t.amount > 0);
  const outflows = mockTransactions.filter((t) => t.amount < 0);

  const inflowRecurring = detectRecurringTransactions(inflows);
  const outflowRecurring = detectRecurringTransactions(outflows);

  res.json({
    success: true,
    inflow: inflowRecurring,
    outflow: outflowRecurring,
  });
});

module.exports = router;
