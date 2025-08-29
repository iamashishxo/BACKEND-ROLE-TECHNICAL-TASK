package com.plaid_cash_snapshot.plaid.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PlaidSyncResponse(
        List<PlaidTransaction> added,
        List<PlaidTransaction> modified,
        List<String> removed, // Plaid only gives transaction_ids for removed
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("request_id") String requestId
) {}
