package com.plaid_cash_snapshot.plaid.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PlaidBalancesResponse(
        List<Account> accounts
) {
    public record Account(
            @JsonProperty("account_id") String accountId,
            Balances balances,
            String type,
            String subtype,
            @JsonProperty("mask") String mask
    ) {}

    public record Balances(
            Double available,
            @JsonProperty("current") Double current,
            Double limit,
            @JsonProperty("iso_currency_code") String isoCurrencyCode,
            @JsonProperty("unofficial_currency_code") String unofficialCurrencyCode
    ) {}
}
