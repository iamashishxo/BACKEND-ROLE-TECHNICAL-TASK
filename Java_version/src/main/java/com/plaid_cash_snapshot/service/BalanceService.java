package com.plaid_cash_snapshot.service;

import com.plaid_cash_snapshot.dto.BalancesSummary;
import com.plaid_cash_snapshot.plaid.dto.PlaidBalancesResponse;
import com.plaid_cash_snapshot.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final DatabaseClient db;
    private final ItemRepository itemRepository;

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

    /**
     * Minimal implementation: assumes a single Plaid item per user.
     * If you have multiple items, switch findByUserId(userId).collectList()
     * and loop over each item/access_token before aggregating.
     */
    public Mono<BalancesSummary> getSummary(UUID userId) {
        WebClient http = client();

        return itemRepository.findByUserId(userId)
                .single() // use .next() if you might have multiple items
                .flatMap(item -> http.post()
                        .uri("/accounts/balance/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "access_token", item.getAccessToken(),
                                "client_id", plaidClientId,
                                "secret", plaidSecret
                        ))
                        .retrieve()
                        .bodyToMono(PlaidBalancesResponse.class)
                        .flatMapMany(res -> {
                            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                            return reactor.core.publisher.Flux.fromIterable(res.accounts())
                                    .flatMap(acc -> {
                                        // Idempotent upsert by (user_id, account_id)
                                        String sql = """
                                            INSERT INTO account_balances
                                              (user_id, account_id, available, current_balance, limit_amount,
                                               iso_currency_code, unofficial_currency_code, last_updated_datetime, created_at)
                                            VALUES (:userId, (SELECT id FROM accounts WHERE account_id = :accId LIMIT 1),
                                                    :available, :current, :limit, :iso, :unofficial, :asOf, now())
                                            ON CONFLICT (user_id, account_id)
                                            DO UPDATE SET
                                                available                = EXCLUDED.available,
                                                current_balance          = EXCLUDED.current_balance,
                                                limit_amount             = EXCLUDED.limit_amount,
                                                iso_currency_code        = EXCLUDED.iso_currency_code,
                                                unofficial_currency_code = EXCLUDED.unofficial_currency_code,
                                                last_updated_datetime    = EXCLUDED.last_updated_datetime
                                            """;

                                        var insert = db.sql(sql)
                                                .bind("userId", userId)
                                                .bind("accId", acc.accountId())
                                                .bind("asOf", now);

                                        // current is typically present; still bind safely
                                        if (acc.balances().current() != null) {
                                            insert = insert.bind("current", acc.balances().current());
                                        } else {
                                            insert = insert.bindNull("current", Double.class);
                                        }

                                        // available (nullable)
                                        if (acc.balances().available() != null) {
                                            insert = insert.bind("available", acc.balances().available());
                                        } else {
                                            insert = insert.bindNull("available", Double.class);
                                        }

                                        // limit (nullable; mostly for credit)
                                        if (acc.balances().limit() != null) {
                                            insert = insert.bind("limit", acc.balances().limit());
                                        } else {
                                            insert = insert.bindNull("limit", Double.class);
                                        }

                                        // iso_currency_code (nullable)
                                        if (acc.balances().isoCurrencyCode() != null) {
                                            insert = insert.bind("iso", acc.balances().isoCurrencyCode());
                                        } else {
                                            insert = insert.bindNull("iso", String.class);
                                        }

                                        // unofficial_currency_code (nullable)
                                        if (acc.balances().unofficialCurrencyCode() != null) {
                                            insert = insert.bind("unofficial", acc.balances().unofficialCurrencyCode());
                                        } else {
                                            insert = insert.bindNull("unofficial", String.class);
                                        }

                                        return insert.fetch().rowsUpdated();
                                    });
                        })
                        .then(
                                // Read the summary from the view
                                db.sql("""
                                      SELECT user_id,
                                             chequing_total,
                                             savings_total,
                                             credit_cards_total_owed,
                                             net_cash,
                                             as_of
                                        FROM cash_snapshot_v
                                       WHERE user_id = :uid
                                      """)
                                        .bind("uid", userId)
                                        .map((row, meta) -> new BalancesSummary(
                                                row.get("user_id", UUID.class),
                                                row.get("chequing_total", Double.class),
                                                row.get("savings_total", Double.class),
                                                row.get("credit_cards_total_owed", Double.class),
                                                row.get("net_cash", Double.class),
                                                row.get("as_of", OffsetDateTime.class)
                                        ))
                                        .one()
                        )
                );
    }
}
