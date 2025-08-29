package com.plaid_cash_snapshot.dto;



import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkTokenResponse {
    private String userId;
    private String linkToken;
    private String expiration;   // ISO timestamp from Plaid
    private String requestId;
}
