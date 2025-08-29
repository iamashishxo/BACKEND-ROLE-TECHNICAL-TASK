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
@Table("recurring_transactions")
public class RecurringTransaction {
    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("merchant_name")
    private String merchantName;

    @Column("direction")
    private String direction; // inflow | outflow

    @Column("frequency")
    private String frequency; // weekly | biweekly | monthly | quarterly

    @Column("avg_amount")
    private Double avgAmount;

    @Column("min_amount")
    private Double minAmount;

    @Column("max_amount")
    private Double maxAmount;

    @Column("occurrences")
    private Integer occurrences;

    @Column("last_date")
    private LocalDate lastDate;

    @Column("next_estimated_date")
    private LocalDate nextEstimatedDate;

    @Column("confidence")
    private Double confidence; // 0.0 - 1.0

    @Column("category")
    private String category; // optional; use Json.of(...)

    @Column("is_active")
    private Boolean isActive;

////    @Column("created_at")
////    private OffsetDateTime createdAt;
//
//    @Column("updated_at")
//    private OffsetDateTime updatedAt;
@Column("created_at")
@org.springframework.data.annotation.CreatedDate
private OffsetDateTime createdAt;

    @Column("updated_at")
    @org.springframework.data.annotation.LastModifiedDate
    private OffsetDateTime updatedAt;
}

