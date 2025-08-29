package com.plaid_cash_snapshot.plaid.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

/** Plaid /institutions/get_by_id response - minimal */
public record PlaidInstitutionResponse(
        Institution institution
) {
    public record Institution(
            @JsonProperty("institution_id") String institutionId,
            String name
    ) {}
}
