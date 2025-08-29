package com.plaid_cash_snapshot.repository;

import com.plaid_cash_snapshot.model.Transaction;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TransactionRepository extends ReactiveCrudRepository<Transaction, UUID> {
    Mono<Transaction> findByTransactionId(String transactionId);
    Flux<Transaction> findByAccountId(UUID accountId);
    Flux<Transaction> findByUserId(UUID userId);
}
