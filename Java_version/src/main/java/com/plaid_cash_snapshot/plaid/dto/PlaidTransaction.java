package com.plaid_cash_snapshot.plaid.dto;



import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;

public record PlaidTransaction(
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("account_id") String accountId,
        Double amount,
        @JsonProperty("iso_currency_code") String isoCurrencyCode,
        @JsonProperty("unofficial_currency_code") String unofficialCurrencyCode,
        LocalDate date,
        @JsonProperty("authorized_date") LocalDate authorizedDate,
        String name,
        @JsonProperty("merchant_name") String merchantName,
        JsonNode category,
        JsonNode location,       // optional if you want location info
        @JsonProperty("account_owner") String accountOwner,
        Boolean pending,
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("payment_channel") String paymentChannel // optional Plaid field
) {}
