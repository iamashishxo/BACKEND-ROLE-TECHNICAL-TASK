package com.plaid_cash_snapshot.service;


import com.plaid_cash_snapshot.config.PlaidProperties;
import com.plaid_cash_snapshot.dto.LinkTokenResponse;
import com.plaid_cash_snapshot.dto.SandboxPublicTokenRequest;
import com.plaid_cash_snapshot.dto.SandboxPublicTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlaidService {

    @Qualifier("plaidWebClient")
    private final WebClient plaidClient;
    private final PlaidProperties props;

    // ... (existing methods)

    public Mono<LinkTokenResponse> createLinkToken(String userId, String clientNameOverride) {
        String clientName = (clientNameOverride != null && !clientNameOverride.isBlank())
                ? clientNameOverride
                : "Plaid Cash Snapshot";

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> user = Map.of("client_user_id", userId);
        body.put("user", user);
        body.put("client_name", clientName);
        body.put("products", List.of("transactions"));
        body.put("country_codes", props.getCountryCodes());
        body.put("language", "en");

        if (props.getWebhook() != null && !props.getWebhook().isBlank()) {
            body.put("webhook", props.getWebhook());
        }
        if (props.getRedirectUri() != null && !props.getRedirectUri().isBlank()) {
            body.put("redirect_uri", props.getRedirectUri());
        }

        return plaidClient.post()
                .uri("/link/token/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> LinkTokenResponse.builder()
                        .userId(userId)
                        .linkToken((String) resp.get("link_token"))
                        .expiration((String) resp.get("expiration"))
                        .requestId((String) resp.get("request_id"))
                        .build());
    }

    public Mono<SandboxPublicTokenResponse> createSandboxPublicToken(SandboxPublicTokenRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("institution_id", req.getInstitutionId());
        body.put("initial_products", req.getInitialProducts());

        // Build options object only if needed
        Map<String, Object> options = new HashMap<>();
//        if (req.getWebhook() != null && !req.getWebhook().isBlank()) {
//            options.put("webhook", req.getWebhook());
//        }
        if (req.getOptions() != null && !req.getOptions().isEmpty()) {
            options.putAll(req.getOptions());
        }
        if (!options.isEmpty()) {
            body.put("options", options);
        }

        return plaidClient.post()
                .uri("/sandbox/public_token/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> SandboxPublicTokenResponse.builder()
                        .publicToken((String) resp.get("public_token"))
                        .requestId((String) resp.get("request_id"))
                        .build());
    }
}

