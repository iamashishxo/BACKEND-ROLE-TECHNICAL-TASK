package com.plaid_cash_snapshot.dto;



import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record RecurringResponse(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("type") String type, // "inflow" | "outflow"
        @JsonProperty("recurring_transactions") List<Stream> recurringTransactions,
        @JsonProperty("total_streams") int totalStreams,
        @JsonProperty("detection_methods") DetectionMethods detectionMethods
) {
    @Builder
    public record Stream(
            @JsonProperty("stream_id") String streamId,         // may be null for custom
            @JsonProperty("description") String description,     // merchant or stream description
            @JsonProperty("merchant_name") String merchantName,
            @JsonProperty("avg_amount") Double avgAmount,
            @JsonProperty("first_date") OffsetDateTime firstDate,
            @JsonProperty("last_date") OffsetDateTime lastDate,
            @JsonProperty("next_estimated_date") OffsetDateTime nextEstimatedDate,
            @JsonProperty("occurrences") int occurrences,
            @JsonProperty("frequency_days") Integer frequencyDays, // inferred cadence in days
            @JsonProperty("direction") String direction, // inflow|outflow
            @JsonProperty("source") String source        // "plaid" | "custom"
    ) {}

    @Builder
    public record DetectionMethods(
            @JsonProperty("plaid_api") int plaidApi,
            @JsonProperty("custom_detector") int customDetector
    ) {}
}

