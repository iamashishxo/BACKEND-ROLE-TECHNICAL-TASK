package com.plaid_cash_snapshot.repository;


import com.plaid_cash_snapshot.model.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface UserRepository extends R2dbcRepository<User, UUID> {}
