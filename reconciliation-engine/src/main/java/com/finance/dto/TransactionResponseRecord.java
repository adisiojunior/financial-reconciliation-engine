package com.finance.dto;

import com.finance.domain.TransactionEntity;
import com.finance.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponseRecord(
        UUID id,
        String externalReference,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        Long version,
        Instant createdAt
) {
    public static TransactionResponseRecord fromEntity(TransactionEntity entity) {
        return new TransactionResponseRecord(
                entity.getId(),
                entity.getExternalReference(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt()
        );
    }
}