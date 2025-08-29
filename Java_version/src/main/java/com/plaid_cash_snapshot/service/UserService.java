package com.plaid_cash_snapshot.service;



import com.plaid_cash_snapshot.model.User;
import com.plaid_cash_snapshot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final R2dbcEntityTemplate template;

    public Mono<UUID> ensureUser(UUID userId) {
        return userRepository.existsById(userId)
                .flatMap(exists -> {
                    if (exists) return Mono.just(userId);

                    User u = new User();
                    u.setId(userId); // explicit id
                    OffsetDateTime now = OffsetDateTime.now();
                    u.setCreatedAt(now);
                    u.setUpdatedAt(now);

                    // IMPORTANT: use insert (not save) since id is non-null
                    return template.insert(User.class).using(u).map(User::getId);
                });
    }
}

