// src/utils/customRecurringDetector.js

function normalizeMerchant(name = "") {
    return name
      .toLowerCase()
      .replace(/\d+/g, "") // remove numbers (phone, ids)
      .replace(/[^a-z\s]/g, "") // remove symbols
      .trim();
  }
  
  function groupByMerchantAndAmount(transactions) {
    const groups = {};
  
    for (const tx of transactions) {
      const merchant = normalizeMerchant(tx.merchant_name || tx.name);
      const amount = Math.round(Math.abs(tx.amount)); // round to nearest integer
      const key = `${merchant}_${amount}`;
  
      if (!groups[key]) {
        groups[key] = [];
      }
      groups[key].push(tx);
    }
  
    return groups;
  }
  
  function inferCadence(dates) {
    if (dates.length < 2) return null;
  
    const deltas = [];
    for (let i = 1; i < dates.length; i++) {
      const diff =
        (new Date(dates[i]) - new Date(dates[i - 1])) / (1000 * 60 * 60 * 24);
      deltas.push(diff);
    }
  
    const avg = deltas.reduce((a, b) => a + b, 0) / deltas.length;
  
    if (avg >= 27 && avg <= 32) return "monthly";
    if (avg >= 13 && avg <= 15) return "biweekly";
    if (avg >= 6 && avg <= 8) return "weekly";
    return "irregular";
  }
  
  function detectRecurringTransactions(transactions) {
    const groups = groupByMerchantAndAmount(transactions);
    const recurring = [];
  
    for (const key in groups) {
      const txs = groups[key];
      if (txs.length < 3) continue; // require at least 3 occurrences
  
      // sort by date ascending
      const sorted = txs.sort(
        (a, b) => new Date(a.date) - new Date(b.date)
      );
  
      const dates = sorted.map((t) => t.date);
      const cadence = inferCadence(dates);
  
      recurring.push({
        merchant_name: normalizeMerchant(sorted[0].merchant_name || sorted[0].name),
        avg_amount:
          txs.reduce((sum, t) => sum + Math.abs(t.amount), 0) / txs.length,
        occurrences: txs.length,
        cadence,
        last_date: sorted[sorted.length - 1].date,
        next_estimated_date:
          cadence === "monthly"
            ? new Date(
                new Date(sorted[sorted.length - 1].date).setMonth(
                  new Date(sorted[sorted.length - 1].date).getMonth() + 1
                )
              )
                .toISOString()
                .split("T")[0]
            : cadence === "weekly"
            ? new Date(
                new Date(sorted[sorted.length - 1].date).setDate(
                  new Date(sorted[sorted.length - 1].date).getDate() + 7
                )
              )
                .toISOString()
                .split("T")[0]
            : null,
        confidence: cadence === "irregular" ? 0.5 : 0.9,
      });
    }
  
    return recurring;
  }
  
  module.exports = { detectRecurringTransactions };
  