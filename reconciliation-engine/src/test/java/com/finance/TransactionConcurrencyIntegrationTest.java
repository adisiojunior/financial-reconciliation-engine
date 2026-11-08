package com.finance.integration;

import com.finance.domain.TransactionEntity;
import com.finance.domain.TransactionStatus;
import com.finance.repository.TransactionRepository;
import com.finance.service.ReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class TransactionConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.11-management-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    @DisplayName("Concorrência: Múltiplas threads disputando o mesmo registro devem respeitar o lock sem inconsistência")
    void shouldHandleConcurrentReconciliationSafely() throws InterruptedException {
        TransactionEntity entity = new TransactionEntity();
        entity.setExternalReference("LOCK-TX-" + UUID.randomUUID());
        entity.setAmount(new BigDecimal("990.00"));
        entity.setCurrency("BRL");
        entity.setStatus(TransactionStatus.PENDING);
        TransactionEntity saved = transactionRepository.saveAndFlush(entity);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCounter = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    reconciliationService.reconcile(saved.getId());
                    successCounter.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        TransactionEntity finalEntity = transactionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TransactionStatus.RECONCILED, finalEntity.getStatus());
    }
}