package com.plaid_cash_snapshot.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table("transactions")
public class Transaction {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    // FK -> accounts.id (UUID from your accounts table)
    @Column("account_id")
    private UUID accountId;

    @Column("transaction_id")
    private String transactionId; // Plaid transaction_id (UNIQUE)

    @Column("amount")
    private Double amount;

    @Column("iso_currency_code")
    private String isoCurrencyCode;

    @Column("unofficial_currency_code")
    private String unofficialCurrencyCode;

    @Column("date")
    private LocalDate date;

    @Column("authorized_date")
    private LocalDate authorizedDate;

    @Column("name")
    private String name;

    @Column("merchant_name")
    private String merchantName;

    // JSONB columns; JsonNode keeps it flexible/minimal
    @Column("category")
    private String category;

    @Column("subcategory")
    private String subcategory;

    @Column("account_owner")
    private String accountOwner;

    @Column("pending")
    private Boolean pending;

    @Column("transaction_type")
    private String transactionType; // 'digital','place','special','unresolved'

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
