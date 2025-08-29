package com.plaid_cash_snapshot.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LinkTokenRequest {

    private String userId;       // optional now
    private String clientName = "Plaid Cash Snapshot"; // optional override
}
