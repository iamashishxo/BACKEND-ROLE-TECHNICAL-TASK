package com.plaid_cash_snapshot.plaid.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

/** Plaid /item/public_token/exchange response */
public record PlaidExchangeResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("item_id") String itemId,
        @JsonProperty("request_id") String requestId
) {}

