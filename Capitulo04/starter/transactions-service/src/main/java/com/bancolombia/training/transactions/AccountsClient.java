package com.bancolombia.training.transactions;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
class AccountsClient {
    private final WebClient client;
    private final Duration timeout;
    private final Duration totalTimeout;
    AccountsClient(WebClient.Builder builder,
                   @Value("${services.accounts.base-url:http://localhost:8081}") String baseUrl,
                   @Value("${services.accounts.timeout:1s}") Duration timeout,
                   @Value("${services.accounts.total-timeout:3s}") Duration totalTimeout) {
        this.client = builder.baseUrl(baseUrl).build();
        this.timeout = timeout;
        this.totalTimeout = totalTimeout;
    }
    Mono<AccountView> find(long id, String authorization) {
        return client.get().uri("/accounts/{id}", id).header("Authorization", authorization).retrieve()
                .bodyToMono(AccountView.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)).filter(this::retryable))
                .timeout(totalTimeout);
    }

    private boolean retryable(Throwable error) {
        return error instanceof WebClientRequestException
                || error instanceof java.util.concurrent.TimeoutException
                || (error instanceof WebClientResponseException response && response.getStatusCode().is5xxServerError());
    }
}

record AccountView(long id, String number, java.math.BigDecimal balance, boolean active) {}
