package com.plaid_cash_snapshot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BalancesSummary(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("chequing_total") Double chequingTotal,
        @JsonProperty("savings_total") Double savingsTotal,
        @JsonProperty("credit_cards_total_owed") Double creditCardsTotalOwed,
        @JsonProperty("net_cash") Double netCash,
        @JsonProperty("as_of") OffsetDateTime asOf
) {}
