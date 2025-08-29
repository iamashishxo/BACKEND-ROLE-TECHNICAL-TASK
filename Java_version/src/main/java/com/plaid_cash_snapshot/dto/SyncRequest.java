package com.plaid_cash_snapshot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SyncRequest(
        @NotNull @JsonProperty("user_id") UUID userId
) {}
