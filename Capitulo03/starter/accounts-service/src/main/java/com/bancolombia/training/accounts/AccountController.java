package com.bancolombia.training.accounts;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/accounts")
class AccountController {
    private final Map<Long, Account> accounts = Map.of(
            1L, new Account(1L, "ACC-001", new BigDecimal("5000.00"), true),
            2L, new Account(2L, "ACC-002", new BigDecimal("12500.50"), true));

    @GetMapping("/{id}")
    Mono<Account> findById(@PathVariable long id) {
        return Mono.justOrEmpty(accounts.get(id))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")));
    }

    @GetMapping("/admin/audit")
    Mono<String> audit() { return Mono.just("admin-only"); }
}

record Account(long id, String number, BigDecimal balance, boolean active) {}
