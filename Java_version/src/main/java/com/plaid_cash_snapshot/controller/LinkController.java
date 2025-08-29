package com.plaid_cash_snapshot.controller;

import com.plaid_cash_snapshot.dto.*;
import com.plaid_cash_snapshot.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class LinkController {

    private final PlaidService plaidService;

    private final UserService userService;

    private final TransactionSyncService transactionSyncService;

    private final BalanceService balanceService;

    private final ItemLinkService itemLinkService;

    private final RecurringService recurringService;



    @PostMapping("/link-token")
    public Mono<ResponseEntity<ApiResponse<LinkTokenResponse>>> createLinkToken(
            @RequestBody(required = false) LinkTokenRequest request) {

        if (request == null) request = new LinkTokenRequest();

        String picked = (request.getUserId() == null || request.getUserId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getUserId();

        UUID userId = UUID.fromString(picked);

        // Ensure the user exists, then create the link token
        return userService.ensureUser(userId)
                .then(plaidService.createLinkToken(userId.toString(), request.getClientName()))
                .map(data -> ResponseEntity.ok(
                        ApiResponse.<LinkTokenResponse>builder().success(true).data(data).build()
                ));
    }

//    @PostMapping("/link-token")
//    public Mono<ResponseEntity<ApiResponse<LinkTokenResponse>>> createLinkToken(
//            @RequestBody(required = false) LinkTokenRequest request) {
//
//        // If body missing → create an empty request
//        if (request == null) {
//            request = new LinkTokenRequest();
//        }
//
//        String userId = (request.getUserId() == null || request.getUserId().isBlank())
//                ? java.util.UUID.randomUUID().toString()
//                : request.getUserId();
//
//        return plaidService.createLinkToken(userId, request.getClientName())
//                .map(data -> ResponseEntity.ok(
//                        ApiResponse.<LinkTokenResponse>builder().success(true).data(data).build()
//                ));
//    }


    @PostMapping("/public-token")
    public Mono<ResponseEntity<ApiResponse<SandboxPublicTokenResponse>>> createPublicToken(
            @RequestBody(required = false) SandboxPublicTokenRequest request) {

        if (request == null) request = new SandboxPublicTokenRequest();

        return plaidService.createSandboxPublicToken(request)
                .map(data -> ResponseEntity.ok(
                        ApiResponse.<SandboxPublicTokenResponse>builder().success(true).data(data).build()
                ));
    }

    @PostMapping("/exchange")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ApiResponse<ExchangeResponse>> exchange(@RequestBody ExchangeRequest request) {
        return itemLinkService.exchangeAndSave(request)
                .map(data -> new ApiResponse<>(true, data, null));
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ApiResponse<SyncResponse>> sync(@Valid @RequestBody SyncRequest request) {
        return transactionSyncService.syncUser(request)
                .map(ApiResponse::ok);
    }

    @GetMapping("/summary")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ApiResponse<BalancesSummary>> summary(@RequestParam("user_id") @NotNull UUID userId) {
        return balanceService.getSummary(userId)
                .map(ApiResponse::ok);
    }

    @GetMapping("/recurring")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ApiResponse<RecurringResponse>> getRecurring(
            @RequestParam("user_id") @NotNull UUID userId,
            @RequestParam(name = "type", defaultValue = "outflow") String type
    ) {
        return recurringService.getRecurring(userId, type)
                .map(ApiResponse::ok);
    }

}
