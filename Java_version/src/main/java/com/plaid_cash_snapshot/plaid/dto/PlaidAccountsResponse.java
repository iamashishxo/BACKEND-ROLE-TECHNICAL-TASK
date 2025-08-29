package com.plaid_cash_snapshot.plaid.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Plaid /accounts/get (or /accounts/balance/get) response - minimal */
public record PlaidAccountsResponse(
        List<Account> accounts
) {
    public record Account(
            @JsonProperty("account_id") String accountId,
            String name,
            @JsonProperty("official_name") String officialName,
            String type,         // e.g. "depository", "credit", "loan", "investment"
            String subtype,      // e.g. "checking", "savings", "credit card"
            String mask
    ) {}
}

