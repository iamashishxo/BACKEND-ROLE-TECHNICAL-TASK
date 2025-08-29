package com.plaid_cash_snapshot.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.plaid_cash_snapshot.dto.SyncRequest;
import com.plaid_cash_snapshot.dto.SyncResponse;
import com.plaid_cash_snapshot.model.Account;
import com.plaid_cash_snapshot.model.Item;
import com.plaid_cash_snapshot.model.Transaction;
import com.plaid_cash_snapshot.plaid.dto.PlaidSyncResponse;
import com.plaid_cash_snapshot.plaid.dto.PlaidTransaction;
import com.plaid_cash_snapshot.repository.AccountRepository;
import com.plaid_cash_snapshot.repository.ItemRepository;
import com.plaid_cash_snapshot.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionSyncService {

    private final ItemRepository itemRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

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

    public Mono<SyncResponse> syncUser(SyncRequest req) {
        UUID userId = req.userId();
        WebClient http = client();

        return itemRepository.findByUserId(userId)
                .flatMap(item ->
                        // Load all accounts for this item to map Plaid account_id -> accounts.id (UUID)
                        accountRepository.findByItemId(item.getId())
                                .collectMap(Account::getAccountId, Account::getId)
                                .flatMap(accMap -> syncOneItem(http, item, accMap))
                )
                .collectList()
                .map(results -> {
                    int total = results.stream().mapToInt(SyncResponse.SyncResult::transactionsSynced).sum();
                    boolean anyHadMore = results.stream().anyMatch(r -> r.cursor() == null || r.cursor().isBlank()); // if cursor missing, treat as not fully synced
                    // If every item returned a non-empty cursor after finishing pages, we consider "full_sync" true.
                    boolean fullSync = !anyHadMore;
                    return SyncResponse.builder()
                            .userId(userId)
                            .totalTransactionsSynced(total)
                            .itemsSynced(results.size())
                            .syncResults(results)
                            .fullSync(fullSync)
                            .build();
                });
    }

    private Mono<SyncResponse.SyncResult> syncOneItem(WebClient http, Item item, Map<String, UUID> accountIdToUuid) {
        String accessToken = item.getAccessToken();
        String startingCursor = item.getCursor(); // may be null on first sync

        // Recursive page loop
        return syncPage(http, accessToken, startingCursor, 0, null, accountIdToUuid, item)
                .flatMap(finalState -> {
                    // Update item cursor with the latest cursor we received
                    Item updated = item.toBuilder()
                            .cursor(finalState.latestCursor)
                            .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                            .build();
                    return itemRepository.save(updated)
                            .thenReturn(SyncResponse.SyncResult.builder()
                                    .itemId(item.getItemId())
                                    .transactionsSynced(finalState.totalCount)
                                    .cursor(finalState.latestCursor)
                                    .build());
                });
    }

    private record PageState(int totalCount, String latestCursor) {}

    private Mono<PageState> syncPage(
            WebClient http,
            String accessToken,
            String cursor,
            int accumulated,
            String latestCursor,
            Map<String, UUID> accountIdToUuid,
            Item item
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("access_token", accessToken);
        body.put("client_id", plaidClientId);
        body.put("secret", plaidSecret);
        if (cursor != null) body.put("cursor", cursor);
        // Optional: body.put("count", 500); // Plaid page size

        return http.post()
                .uri("/transactions/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PlaidSyncResponse.class)
                .flatMap(res -> {
                    List<PlaidTransaction> added = Optional.ofNullable(res.added()).orElse(List.of());
                    List<PlaidTransaction> modified = Optional.ofNullable(res.modified()).orElse(List.of());

                    // combine added + modified
                    List<PlaidTransaction> toUpsert = Stream.concat(added.stream(), modified.stream()).toList();

                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                    // Upsert each transaction
                    return Flux.fromIterable(toUpsert)
                            .flatMap(pt -> upsertTransaction(pt, item, accountIdToUuid, now))
                            .count()
                            .flatMap(savedCount -> {
                                int newTotal = accumulated + savedCount.intValue();
                                String next = res.nextCursor();

                                if (res.hasMore()) {
                                    // Continue with next page
                                    return syncPage(http, accessToken, next, newTotal, next, accountIdToUuid, item);
                                } else {
                                    // Done for this item
                                    String finalCursor = (next != null ? next : latestCursor);
                                    return Mono.just(new PageState(newTotal, finalCursor));
                                }
                            });
                });
    }

    private Mono<Transaction> upsertTransaction(
            PlaidTransaction pt,
            Item item,
            Map<String, UUID> accountIdToUuid,
            OffsetDateTime now
    ) {
        UUID accountUuid = accountIdToUuid.get(pt.accountId());
        if (accountUuid == null) {
            // If account mapping is missing, skip gracefully.
            log.warn("Missing account mapping for item {} plaidAccountId {}. Skipping txn {}.",
                    item.getId(), pt.accountId(), pt.transactionId());
            return Mono.empty();
        }

        return transactionRepository.findByTransactionId(pt.transactionId())
                .defaultIfEmpty(Transaction.builder()
                        .userId(item.getUserId())
                        .accountId(accountUuid)
                        .transactionId(pt.transactionId())
                        .createdAt(now)
                        .build())
                .flatMap(existingOrNew -> {
                    Transaction toSave = existingOrNew.toBuilder()
                            .amount(pt.amount())
                            .isoCurrencyCode(pt.isoCurrencyCode())
                            .unofficialCurrencyCode(pt.unofficialCurrencyCode())
                            .date(pt.date())
                            .authorizedDate(pt.authorizedDate())
                            .name(pt.name())
                            .merchantName(pt.merchantName())
                            .category(toJsonString(pt.category()))     // <-- serialize
                            .subcategory(toJsonString(pt.category()))  // or split if you want
                            .accountOwner(pt.accountOwner())
                            .pending(Boolean.TRUE.equals(pt.pending()))
                            .transactionType(pt.transactionType())
                            .updatedAt(now)
                            .build();
                    return transactionRepository.save(toSave);
                });
    }

    private static String toJsonString(Object nodeOrNull) {
        return nodeOrNull == null ? null : nodeOrNull.toString(); // Jackson records already give proper toString() JSON
    }
}

