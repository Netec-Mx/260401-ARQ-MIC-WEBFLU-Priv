package com.bancolombia.training.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/accounts")
class StatusController {
    private final String environment;
    StatusController(@Value("${course.environment:default}") String environment) { this.environment = environment; }

    @GetMapping("/status")
    Mono<ServiceStatus> status() { return Mono.just(new ServiceStatus("accounts-service", "UP", environment)); }
}

record ServiceStatus(String service, String status, String environment) {}
