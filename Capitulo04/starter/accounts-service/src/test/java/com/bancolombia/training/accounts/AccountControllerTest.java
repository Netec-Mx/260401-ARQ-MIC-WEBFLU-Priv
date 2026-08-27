package com.bancolombia.training.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.context.annotation.Import;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@WebFluxTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {
    @Autowired WebTestClient client;
    @Test void findsExistingAccount() {
        client.mutateWith(mockJwt()).get().uri("/accounts/1").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.number").isEqualTo("ACC-001");
    }
    @Test void returns404ForMissingAccount() {
        client.mutateWith(mockJwt()).get().uri("/accounts/999").exchange().expectStatus().isNotFound();
    }

    @Test void rejectsMissingToken() {
        client.get().uri("/accounts/1").exchange().expectStatus().isUnauthorized();
    }

    @Test void rejectsUserOnAdminRoute() {
        client.mutateWith(mockJwt().authorities(() -> "SCOPE_ROLE_USER"))
                .get().uri("/accounts/admin/audit").exchange().expectStatus().isForbidden();
    }
}
