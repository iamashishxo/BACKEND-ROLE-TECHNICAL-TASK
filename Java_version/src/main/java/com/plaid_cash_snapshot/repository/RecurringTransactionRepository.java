package com.plaid_cash_snapshot.repository;


import com.plaid_cash_snapshot.model.RecurringTransaction;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RecurringTransactionRepository extends ReactiveCrudRepository<RecurringTransaction, UUID> {
    Flux<RecurringTransaction> findByUserIdAndDirection(UUID userId, String direction);
    Mono<RecurringTransaction> findByUserIdAndDirectionAndMerchantNameAndFrequency(
            UUID userId, String direction, String merchantName, String frequency);
}

