package com.plaid_cash_snapshot.service;


import com.plaid_cash_snapshot.dto.RecurringResponse;
import com.plaid_cash_snapshot.model.RecurringTransaction;
import com.plaid_cash_snapshot.model.Transaction;
import com.plaid_cash_snapshot.repository.ItemRepository;
import com.plaid_cash_snapshot.repository.TransactionRepository;
import com.plaid_cash_snapshot.repository.RecurringTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecurringService {

    private final ItemRepository itemRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringRepo;

    @Value("${plaid.base-url:https://sandbox.plaid.com}")
    private String plaidBaseUrl;

    @Value("${plaid.client-id}")
    private String plaidClientId;

    @Value("${plaid.secret}")
    private String plaidSecret;

    private WebClient client() {
        return WebClient.builder().baseUrl(plaidBaseUrl).build();
    }

    public Mono<RecurringResponse> getRecurring(UUID userId, String type) {
        String direction = normalizeType(type);

        return itemRepository.findByUserId(userId)
                .next()
                .flatMap(item ->
                        fetchFromPlaid(item.getAccessToken(), direction)
                                .flatMap(plaidStreams -> {
                                    if (!plaidStreams.isEmpty()) {
                                        // Option A: persist Plaid streams
                                        return upsertRecurring(userId, direction, plaidStreams)
                                                .thenReturn(buildPlaidResponse(userId, direction, plaidStreams));
                                    }
                                    // Option B: custom detection (no persistence)
                                    return detectCustom(userId, direction)
                                            .map(customStreams -> buildCustomResponse(userId, direction, customStreams));
                                })
                                .onErrorResume(err -> {
                                    log.warn("Plaid recurring failed, falling back to custom: {}", err.toString());
                                    return detectCustom(userId, direction)
                                            .map(customStreams -> buildCustomResponse(userId, direction, customStreams));
                                })
                )
                .switchIfEmpty(
                        detectCustom(userId, direction)
                                .map(customStreams -> buildCustomResponse(userId, direction, customStreams))
                );
    }

    // ---------- Option A: Plaid ----------
    private Mono<List<RecurringResponse.Stream>> fetchFromPlaid(String accessToken, String direction) {
        WebClient http = client();
        Map<String, Object> body = Map.of(
                "access_token", accessToken,
                "client_id", plaidClientId,
                "secret", plaidSecret
        );

        return http.post()
                .uri("/transactions/recurring/get")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> mapToPlaidStreams(map, direction))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().is4xxClientError() || ex.getStatusCode().equals(HttpStatus.NOT_IMPLEMENTED)) {
                        return Mono.just(List.of());
                    }
                    return Mono.error(ex);
                });
    }

    @SuppressWarnings("unchecked")
    private List<RecurringResponse.Stream> mapToPlaidStreams(Map<?, ?> root, String direction) {
        Object key = direction.equals("inflow") ? root.get("inflow_streams") : root.get("outflow_streams");
        if (!(key instanceof List<?> raw)) return List.of();

        List<RecurringResponse.Stream> out = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> s)) continue;

            String streamId = val(s, "stream_id");
            String merchant = nestedString(s, "merchant_name", "name");
            if (merchant == null) merchant = val(s, "description");
            String description = val(s, "description");
            if (description == null) description = merchant;

            Double avgAmount = nestedDouble(s, "average_amount", "value");
            if (avgAmount == null) avgAmount = asDouble(s.get("amount"));
            if (avgAmount == null) avgAmount = 0d;
            if ("outflow".equals(direction) && avgAmount > 0) avgAmount = -avgAmount;

            OffsetDateTime first = parseDateTime(nestedString(s, "first_date"));
            OffsetDateTime last  = parseDateTime(nestedString(s, "last_date"));
            Integer frequencyDays = nestedInt(s, "frequency", "days");
            OffsetDateTime next = (last != null && frequencyDays != null) ? last.plusDays(frequencyDays) : null;

            Integer occurrences = asInt(s.get("occurrences"));
            if (occurrences == null) occurrences = 0;

            out.add(RecurringResponse.Stream.builder()
                    .streamId(streamId)
                    .description(description)
                    .merchantName(merchant)
                    .avgAmount(avgAmount)
                    .firstDate(first)
                    .lastDate(last)
                    .nextEstimatedDate(next)
                    .occurrences(occurrences)
                    .frequencyDays(frequencyDays)
                    .direction(direction)
                    .source("plaid")
                    .build());
        }
        return out;
    }

    // ---------- Option B: Custom detector ----------
    private Mono<List<RecurringResponse.Stream>> detectCustom(UUID userId, String direction) {
        return transactionRepository.findByUserId(userId)
                .filter(tx -> ("outflow".equals(direction) && safeDouble(tx.getAmount()) > 0)
                        || ("inflow".equals(direction)  && safeDouble(tx.getAmount()) < 0))
                .filter(tx -> tx.getMerchantName() != null && !tx.getMerchantName().isBlank())
                .collectList()
                .map(txns -> buildCustomStreams(txns, direction));
    }

    private List<RecurringResponse.Stream> buildCustomStreams(List<Transaction> txns, String direction) {
        if (txns.isEmpty()) return List.of();

        Map<Key, List<Transaction>> grouped = txns.stream()
                .collect(Collectors.groupingBy(tx -> new Key(norm(tx.getMerchantName()),
                        Math.round(Math.abs(safeDouble(tx.getAmount())) / 5.0) * 5.0)));

        List<RecurringResponse.Stream> streams = new ArrayList<>();
        for (Map.Entry<Key, List<Transaction>> e : grouped.entrySet()) {
            List<Transaction> group = e.getValue();
            if (group.size() < 3) continue;

            List<LocalDate> dates = group.stream().map(Transaction::getDate).filter(Objects::nonNull).sorted().toList();
            if (dates.size() < 3) continue;

            List<Long> deltas = new ArrayList<>();
            for (int i = 1; i < dates.size(); i++) {
                deltas.add(java.time.temporal.ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
            }
            int frequencyDays = (int) Math.round(median(deltas));
            OffsetDateTime firstDt = dates.get(0).atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime lastDt  = dates.get(dates.size() - 1).atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime nextEst = lastDt.plusDays(frequencyDays);

            double avgAbs = group.stream().mapToDouble(t -> Math.abs(safeDouble(t.getAmount()))).average().orElse(0d);
            double signedAvg = "outflow".equals(direction) ? -avgAbs : avgAbs;

            String merchant = group.get(0).getMerchantName();

            streams.add(RecurringResponse.Stream.builder()
                    .streamId(null)
                    .description(merchant)
                    .merchantName(merchant)
                    .avgAmount(round2(signedAvg))
                    .firstDate(firstDt)
                    .lastDate(lastDt)
                    .nextEstimatedDate(nextEst)
                    .occurrences(group.size())
                    .frequencyDays(frequencyDays)
                    .direction(direction)
                    .source("custom")
                    .build());
        }

        streams.sort(Comparator.comparingInt(RecurringResponse.Stream::occurrences).reversed());
        return streams;
    }

    // ---------- Persist only Plaid streams (idempotent UPSERT) ----------
    private Mono<Void> upsertRecurring(UUID userId, String direction, List<RecurringResponse.Stream> streams) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return Flux.fromIterable(streams)
                .flatMap(s -> {
                    String freq = freqFromDays(s.frequencyDays());
                    String merchant = (s.merchantName() != null && !s.merchantName().isBlank())
                            ? s.merchantName()
                            : (s.description() == null ? "Unknown" : s.description());

                    return recurringRepo.findByUserIdAndDirectionAndMerchantNameAndFrequency(userId, direction, merchant, freq)
                            .defaultIfEmpty(RecurringTransaction.builder()
                                    .userId(userId)
                                    .direction(direction)
                                    .merchantName(merchant)
                                    .frequency(freq)
                                    .createdAt(now)
                                    .isActive(true)
                                    .build())
                            .flatMap(existing -> {
                                RecurringTransaction toSave = existing.toBuilder()
                                        .avgAmount(s.avgAmount())
                                        .minAmount(s.avgAmount())
                                        .maxAmount(s.avgAmount())
                                        .occurrences(s.occurrences())
                                        .lastDate(toLocalDate(s.lastDate()))
                                        .nextEstimatedDate(toLocalDate(s.nextEstimatedDate()))
                                        .confidence(0.9) // Plaid only
                                        .updatedAt(now)
                                        .build();
                                return recurringRepo.save(toSave);
                            });
                })
                .then();
    }

    // ---------- Response builder ----------
    private RecurringResponse buildPlaidResponse(UUID userId, String direction,
                                                 List<RecurringResponse.Stream> plaidStreams) {
        return RecurringResponse.builder()
                .userId(userId)
                .type(direction)
                .recurringTransactions(plaidStreams)
                .totalStreams(plaidStreams.size())
                .detectionMethods(
                        RecurringResponse.DetectionMethods.builder()
                                .plaidApi(plaidStreams.size())
                                .customDetector(0)
                                .build()
                )
                .build();
    }

    private RecurringResponse buildCustomResponse(UUID userId, String direction,
                                                  List<RecurringResponse.Stream> customStreams) {
        return RecurringResponse.builder()
                .userId(userId)
                .type(direction)
                .recurringTransactions(customStreams)
                .totalStreams(customStreams.size())
                .detectionMethods(
                        RecurringResponse.DetectionMethods.builder()
                                .plaidApi(0)
                                .customDetector(customStreams.size())
                                .build()
                )
                .build();
    }


    // ---------- Helpers ----------
    private static String normalizeType(String t) {
        return "inflow".equalsIgnoreCase(t) ? "inflow" : "outflow";
    }
    private static String norm(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[^a-z0-9\\s]", "");
    }
    private static double median(List<Long> values) {
        if (values.isEmpty()) return 30;
        List<Long> v = new ArrayList<>(values);
        Collections.sort(v);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2.0;
    }
    private static Double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? null : Double.valueOf(o.toString()); } catch (Exception e) { return null; }
    }
    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return o == null ? null : Integer.valueOf(o.toString()); } catch (Exception e) { return null; }
    }
    private static String val(Map<?, ?> m, String key) {
        Object v = m.get(key); return v == null ? null : v.toString();
    }
    @SuppressWarnings("unchecked")
    private static String nestedString(Map<?, ?> m, String... path) {
        Object cur = m;
        for (String p : path) {
            if (!(cur instanceof Map<?, ?> mm)) return null;
            cur = mm.get(p);
            if (cur == null) return null;
        }
        return cur.toString();
    }
    @SuppressWarnings("unchecked")
    private static Double nestedDouble(Map<?, ?> m, String... path) {
        Object cur = m;
        for (String p : path) {
            if (!(cur instanceof Map<?, ?> mm)) return null;
            cur = mm.get(p);
            if (cur == null) return null;
        }
        return asDouble(cur);
    }
    @SuppressWarnings("unchecked")
    private static Integer nestedInt(Map<?, ?> m, String... path) {
        Object cur = m;
        for (String p : path) {
            if (!(cur instanceof Map<?, ?> mm)) return null;
            cur = mm.get(p);
            if (cur == null) return null;
        }
        return asInt(cur);
    }
    private static OffsetDateTime parseDateTime(String s) {
        try { return s == null ? null : OffsetDateTime.parse(s); } catch (Exception e) { return null; }
    }
    private static double safeDouble(Double d) { return d == null ? 0d : d; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static String freqFromDays(Integer d) {
        if (d == null) return "monthly";
        if (d <= 10) return "weekly";
        if (d <= 20) return "biweekly";
        if (d <= 45) return "monthly";
        return "quarterly";
    }
    private static LocalDate toLocalDate(OffsetDateTime odt) { return odt == null ? null : odt.toLocalDate(); }

    private record Key(String merchantNorm, double amountBucket) {}
}
