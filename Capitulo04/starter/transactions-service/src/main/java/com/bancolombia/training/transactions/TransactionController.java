package com.bancolombia.training.transactions;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
@RequestMapping("/transactions")
class TransactionController {
    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);
    private final AccountsClient accounts;
    private final Counter accepted;
    private final Counter failed;
    TransactionController(AccountsClient accounts, MeterRegistry registry) {
        this.accounts = accounts;
        this.accepted = registry.counter("course.transactions", "outcome", "accepted");
        this.failed = registry.counter("course.transactions", "outcome", "failed");
    }

    @PostMapping
    Mono<ResponseEntity<TransactionResult>> create(@RequestBody TransactionRequest request,
            @RequestHeader("Authorization") String authorization) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        long started = System.nanoTime();
        return accounts.find(request.accountId(), authorization)
                .filter(AccountView::active)
                .switchIfEmpty(Mono.error(new IllegalStateException("Inactive account")))
                .map(account -> { accepted.increment(); return ResponseEntity.status(HttpStatus.CREATED)
                        .body(new TransactionResult(traceId, request.accountId(), request.amount(), "ACCEPTED")); })
                .doOnSuccess(r -> log.info("event=transaction_created traceId={} accountId={} durationMs={}",
                        traceId, request.accountId(), (System.nanoTime() - started) / 1_000_000))
                .onErrorResume(WebClientResponseException.NotFound.class,
                        e -> { failed.increment(); return Mono.just(ResponseEntity.unprocessableEntity()
                                .body(new TransactionResult(traceId, request.accountId(), request.amount(), "ACCOUNT_NOT_FOUND"))); })
                .onErrorResume(TimeoutException.class,
                        e -> { failed.increment(); return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                                .body(new TransactionResult(traceId, request.accountId(), request.amount(), "TIMEOUT"))); })
                .onErrorResume(e -> { failed.increment(); return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new TransactionResult(traceId, request.accountId(), request.amount(), "DEPENDENCY_ERROR"))); });
    }
}

record TransactionRequest(long accountId, BigDecimal amount) {}
record TransactionResult(String traceId, long accountId, BigDecimal amount, String status) {}
