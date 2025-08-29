package com.plaid_cash_snapshot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SandboxPublicTokenResponse {
    private String publicToken;
    private String requestId;
}
