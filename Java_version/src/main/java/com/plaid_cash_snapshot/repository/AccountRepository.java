package com.plaid_cash_snapshot.repository;

import com.plaid_cash_snapshot.model.Account;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AccountRepository extends ReactiveCrudRepository<Account, UUID> {
    Flux<Account> findByUserId(UUID userId);
    Flux<Account> findByItemId(UUID itemId);
    Mono<Account> findByItemIdAndAccountId(UUID itemId, String accountId);
}
