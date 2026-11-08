package com.finance.service;

import com.finance.domain.TransactionEntity;
import com.finance.domain.TransactionStatus;
import com.finance.gateway.ExternalPaymentGatewayClient;
import com.finance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ExternalPaymentGatewayClient gatewayClient;

    @InjectMocks
    private ReconciliationService reconciliationService;

    private TransactionEntity transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setExternalReference("TX-UNIT-001");
        transaction.setAmount(new BigDecimal("250.00"));
        transaction.setCurrency("BRL");
        transaction.setStatus(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Deve conciliar com sucesso quando gateway aprovar")
    void shouldReconcileSuccessfullyWhenGatewayApproves() {
        when(transactionRepository.findByIdWithLock(transaction.getId())).thenReturn(Optional.of(transaction));
        when(gatewayClient.processPayment(anyString(), any(BigDecimal.class), anyString())).thenReturn(true);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        reconciliationService.reconcile(transaction.getId());

        assertEquals(TransactionStatus.RECONCILED, transaction.getStatus());
        verify(transactionRepository, times(2)).save(any(TransactionEntity.class));
        verify(gatewayClient, times(1)).processPayment(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("Deve definir como FAILED quando gateway rejeitar o pagamento")
    void shouldMarkAsFailedWhenGatewayRejects() {
        when(transactionRepository.findByIdWithLock(transaction.getId())).thenReturn(Optional.of(transaction));
        when(gatewayClient.processPayment(anyString(), any(BigDecimal.class), anyString())).thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        reconciliationService.reconcile(transaction.getId());

        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
        verify(transactionRepository, times(2)).save(any(TransactionEntity.class));
    }

    @Test
    @DisplayName("Deve marcar FAILED e propagar exceção em caso de indisponibilidade")
    void shouldMarkAsFailedWhenGatewayThrowsException() {
        when(transactionRepository.findByIdWithLock(transaction.getId())).thenReturn(Optional.of(transaction));
        when(gatewayClient.processPayment(anyString(), any(BigDecimal.class), anyString()))
                .thenThrow(new RuntimeException("Gateway Timeout"));
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(RuntimeException.class, () -> reconciliationService.reconcile(transaction.getId()));
        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
    }
}