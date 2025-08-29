package com.plaid_cash_snapshot.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SandboxPublicTokenRequest {
    /** e.g. ins_109508 (US), ins_117650 (UK) */
    private String institutionId = "ins_109508";
    /** e.g. ["transactions"] */
    private List<String> initialProducts = List.of("transactions");
    /** Optional: forwarded to Plaid as options.webhook */
    //private String webhook;
    /** Optional: extra options (e.g., override_username/password for sandbox) */
    private Map<String, Object> options;
}
