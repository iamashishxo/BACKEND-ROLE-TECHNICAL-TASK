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
@Table("items")
public class Item {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("item_id")
    private String itemId;

    // Sensitive: store but never return in API responses
    @Column("access_token")
    private String accessToken;

    @Column("institution_id")
    private String institutionId;

    @Column("institution_name")
    private String institutionName;

    // Nullable initially; set after first /transactions/sync
    @Column("cursor")
    private String cursor;

    @Column("created_at")
    private OffsetDateTime createdAt;   // let DB default or set in service

    @Column("updated_at")
    private OffsetDateTime updatedAt;   // update on writes
}
