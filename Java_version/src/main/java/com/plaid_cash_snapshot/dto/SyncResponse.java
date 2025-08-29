package com.plaid_cash_snapshot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record SyncResponse(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("total_transactions_synced") int totalTransactionsSynced,
        @JsonProperty("items_synced") int itemsSynced,
        @JsonProperty("sync_results") List<SyncResult> syncResults,
        @JsonProperty("full_sync") boolean fullSync
) {
    @Builder
    public record SyncResult(
            @JsonProperty("item_id") String itemId,
            @JsonProperty("transactions_synced") int transactionsSynced,
            @JsonProperty("cursor") String cursor
    ) {}
}
