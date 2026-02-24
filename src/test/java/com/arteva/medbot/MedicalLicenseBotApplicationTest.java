package com.arteva.medbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies the Spring ApplicationContext loads 
 * with all beans wired correctly (Telegram disabled).
 *
 * Requires a running Qdrant for full context load;
 * we use a test profile that disables external dependencies.
 */
@SpringBootTest
@ActiveProfiles("test")
class MedicalLicenseBotApplicationTest {

    @Test
    void contextLoads() {
        // If this test passes, the Spring context initializes without errors.
    }
}
