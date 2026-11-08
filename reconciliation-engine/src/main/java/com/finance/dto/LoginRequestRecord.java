package com.finance.dto;

import javax.validation.constraints.NotBlank;

public record LoginRequestRecord(
        @NotBlank String username,
        @NotBlank String role // Ex: ROLE_ADMIN ou ROLE_OPERATOR
) {}