package com.plaid_cash_snapshot.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload (no access_token exposed).
 * Matches:
 * {
 *   "success": true,
 *   "data": {
 *     "item_id": "...",
 *     "accounts": 6,
 *     "institution": "Royal Bank of Plaid",
 *     "message": "Account successfully linked"
 *   }
 * }
 */
public record ExchangeResponse(
        @JsonProperty("item_id") String itemId,
        int accounts,
        String institution,
        String message
) {}
