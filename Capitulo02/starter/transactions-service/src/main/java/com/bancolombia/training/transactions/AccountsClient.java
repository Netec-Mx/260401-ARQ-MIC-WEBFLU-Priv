package com.bancolombia.training.transactions;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
class AccountsClient {
    private final WebClient client;
    private final Duration timeout;
    AccountsClient(WebClient.Builder builder,
                   @Value("${services.accounts.base-url:http://localhost:8081}") String baseUrl,
                   @Value("${services.accounts.timeout:1s}") Duration timeout) {
        this.client = builder.baseUrl(baseUrl).build();
        this.timeout = timeout;
    }
    Mono<AccountView> find(long id) {
        return client.get().uri("/accounts/{id}", id).retrieve()
                .bodyToMono(AccountView.class).timeout(timeout);
    }
}

record AccountView(long id, String number, java.math.BigDecimal balance, boolean active) {}
