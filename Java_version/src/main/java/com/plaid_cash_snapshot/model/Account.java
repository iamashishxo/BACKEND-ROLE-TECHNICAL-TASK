package com.plaid_cash_snapshot.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table("accounts")
public class Account {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("item_id")
    private UUID itemId;   // FK to items.id (UUID)

    @Column("account_id")
    private String accountId;   // Plaid account_id

    @Column("name")
    private String name;

    @Column("official_name")
    private String officialName;

    @Column("type")
    private String type;   // 'depository', 'credit', etc.

    @Column("subtype")
    private String subtype; // 'checking', 'savings', etc.

    @Column("mask")
    private String mask;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
