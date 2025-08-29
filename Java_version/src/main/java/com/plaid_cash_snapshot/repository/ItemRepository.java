package com.plaid_cash_snapshot.repository;

import com.plaid_cash_snapshot.model.Item;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ItemRepository extends ReactiveCrudRepository<Item, UUID> {
    Mono<Item> findByItemId(String itemId);
    Flux<Item> findByUserId(UUID userId);
    // add this method
    Mono<Item> findByUserIdAndItemId(UUID userId, String itemId);

}

