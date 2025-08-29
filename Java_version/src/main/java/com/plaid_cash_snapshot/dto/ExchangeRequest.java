package com.plaid_cash_snapshot.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for POST /api/v1/exchange
 * Takes the publicToken and your issued clientId (from prior step).
 */
public record ExchangeRequest(
        @NotBlank @JsonProperty("public_token")String publicToken,
        @NotNull @JsonProperty("user_id") UUID userId
) {}
