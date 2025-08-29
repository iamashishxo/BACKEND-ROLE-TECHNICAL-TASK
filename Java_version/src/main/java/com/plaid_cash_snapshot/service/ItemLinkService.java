package com.plaid_cash_snapshot.service;

import com.plaid_cash_snapshot.dto.ExchangeRequest;
import com.plaid_cash_snapshot.dto.ExchangeResponse;
import com.plaid_cash_snapshot.model.Account;
import com.plaid_cash_snapshot.model.Item;
import com.plaid_cash_snapshot.plaid.dto.PlaidAccountsResponse;
import com.plaid_cash_snapshot.plaid.dto.PlaidExchangeResponse;
import com.plaid_cash_snapshot.plaid.dto.PlaidInstitutionResponse;
import com.plaid_cash_snapshot.repository.AccountRepository;
import com.plaid_cash_snapshot.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ItemLinkService {

    private final ItemRepository itemRepository;
    private final AccountRepository accountRepository;

    @Value("${plaid.base-url:https://sandbox.plaid.com}")
    private String plaidBaseUrl;

    @Value("${plaid.client-id}")
    private String plaidClientId;

    @Value("${plaid.secret}")
    private String plaidSecret;

    private WebClient client() {
        return WebClient.builder()
                .baseUrl(plaidBaseUrl)
                .build();
    }

    public Mono<ExchangeResponse> exchangeAndSave(ExchangeRequest req) {
        WebClient http = client();

        // 1) exchange public_token -> access_token, item_id
        Mono<PlaidExchangeResponse> exchangeMono = http.post()
                .uri("/item/public_token/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "public_token", req.publicToken(),
                        "client_id", plaidClientId,
                        "secret", plaidSecret
                ))
                .retrieve()
                .bodyToMono(PlaidExchangeResponse.class);

        return exchangeMono.flatMap(ex -> {
            String accessToken = ex.accessToken();
            String plaidItemId = ex.itemId();

            // 2) get accounts list
            Mono<PlaidAccountsResponse> accountsMono = http.post()
                    .uri("/accounts/get")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "access_token", accessToken,
                            "client_id", plaidClientId,
                            "secret", plaidSecret
                    ))
                    .retrieve()
                    .bodyToMono(PlaidAccountsResponse.class);

            // 3) get institution_id via /item/get
            Mono<String> institutionIdMono = http.post()
                    .uri("/item/get")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "access_token", accessToken,
                            "client_id", plaidClientId,
                            "secret", plaidSecret
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(map -> {
                        Object item = map.get("item");
                        if (item instanceof Map<?, ?> m) {
                            Object instId = m.get("institution_id");
                            return instId == null ? null : instId.toString();
                        }
                        return null;
                    });

            // 4) fetch institution name via /institutions/get_by_id
            Mono<PlaidInstitutionResponse.Institution> institutionMono = institutionIdMono.flatMap(instId -> {
                if (instId == null || instId.isBlank()) {
                    return Mono.just(new PlaidInstitutionResponse.Institution(null, "Unknown Institution"));
                }
                return http.post()
                        .uri("/institutions/get_by_id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "institution_id", instId,
                                "country_codes", new String[]{"US"},
                                "client_id", plaidClientId,
                                "secret", plaidSecret
                        ))
                        .retrieve()
                        .bodyToMono(PlaidInstitutionResponse.class)
                        .map(PlaidInstitutionResponse::institution);
            });

            // 5) upsert item and accounts
            return Mono.zip(accountsMono, institutionIdMono, institutionMono)
                    .flatMap(tuple -> {
                        PlaidAccountsResponse accountsRes = tuple.getT1();
                        String institutionId = tuple.getT2();
                        PlaidInstitutionResponse.Institution institution = tuple.getT3();

                        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                        // upsert Item (find by userId + plaidItemId)
                        return itemRepository.findByUserIdAndItemId(req.userId(), plaidItemId)
                                .defaultIfEmpty(Item.builder()
                                        .userId(req.userId())
                                        .itemId(plaidItemId)
                                        .createdAt(now)
                                        .build())
                                .flatMap(existingOrNew -> {
                                    Item toSave = existingOrNew.toBuilder()
                                            .accessToken(accessToken) // update if rotated
                                            .institutionId(institutionId)
                                            .institutionName(institution != null ? institution.name() : null)
                                            .cursor(existingOrNew.getCursor()) // keep cursor if already set
                                            .updatedAt(now)
                                            .build();

                                    return itemRepository.save(toSave);
                                })
                                .flatMap(savedItem -> {
                                    UUID savedItemId = savedItem.getId();

                                    // upsert Accounts by (item_id, account_id)
                                    return Flux.fromIterable(accountsRes.accounts())
                                            .flatMap(acc -> accountRepository.findByItemIdAndAccountId(savedItemId, acc.accountId())
                                                    .defaultIfEmpty(Account.builder()
                                                            .userId(req.userId())
                                                            .itemId(savedItemId)
                                                            .accountId(acc.accountId())
                                                            .createdAt(now)
                                                            .build())
                                                    .flatMap(existingOrNewAcc -> {
                                                        Account toSaveAcc = existingOrNewAcc.toBuilder()
                                                                .name(acc.name())
                                                                .officialName(acc.officialName())
                                                                .type(acc.type())
                                                                .subtype(acc.subtype())
                                                                .mask(acc.mask())
                                                                .updatedAt(now)
                                                                .build();
                                                        return accountRepository.save(toSaveAcc);
                                                    })
                                            )
                                            .then(Mono.just(new ExchangeResponse(
                                                    plaidItemId,
                                                    accountsRes.accounts() != null ? accountsRes.accounts().size() : 0,
                                                    institution != null ? institution.name() : "Unknown Institution",
                                                    "Account successfully linked"
                                            )));
                                });
                    });
        });
    }
}
