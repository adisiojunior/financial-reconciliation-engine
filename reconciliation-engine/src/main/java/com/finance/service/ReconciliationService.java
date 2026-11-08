package com.finance.service;

import com.finance.domain.TransactionEntity;
import com.finance.domain.TransactionStatus;
import com.finance.gateway.ExternalPaymentGatewayClient;
import com.finance.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TransactionRepository transactionRepository;
    private final ExternalPaymentGatewayClient gatewayClient;

    public ReconciliationService(TransactionRepository transactionRepository,
                                 ExternalPaymentGatewayClient gatewayClient) {
        this.transactionRepository = transactionRepository;
        this.gatewayClient = gatewayClient;
    }

    @Transactional
    public void reconcile(UUID transactionId) {
        log.info("A iniciar conciliacao para a transacao ID: {}", transactionId);

        TransactionEntity transaction = transactionRepository.findByIdWithLock(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacao nao encontrada: " + transactionId));

        if (transaction.getStatus() == TransactionStatus.RECONCILED) {
            log.warn("Transacao {} ja se encontra conciliada.", transactionId);
            return;
        }

        transaction.setStatus(TransactionStatus.PROCESSING);
        transactionRepository.save(transaction);

        try {
            boolean approved = gatewayClient.processPayment(
                    transaction.getExternalReference(),
                    transaction.getAmount(),
                    transaction.getCurrency()
            );

            if (approved) {
                transaction.setStatus(TransactionStatus.RECONCILED);
                log.info("Transacao {} conciliada com exito.", transactionId);
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
                log.warn("Transacao {} recusada pelo gateway.", transactionId);
            }

        } catch (Exception ex) {
            log.error("Falha ao conciliar transacao {}: {}", transactionId, ex.getMessage());
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw ex;
        }

        transactionRepository.save(transaction);
    }
}