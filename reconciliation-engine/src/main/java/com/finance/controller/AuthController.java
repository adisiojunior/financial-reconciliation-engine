package com.finance.controller;

import com.finance.dto.LoginRequestRecord;
import com.finance.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacao", description = "Geracao de tokens JWT com RBAC")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "Gerar token JWT", description = "Emite um token com perfis (ex: ROLE_ADMIN ou ROLE_OPERATOR)")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestRecord request) {
        UserDetails user = new User(
                request.username(),
                "N/A",
                Collections.singletonList(new SimpleGrantedAuthority(request.role()))
        );

        String token = jwtService.generateToken(user);
        Map<String, String> response = new HashMap<>();
        response.put("accessToken", token);
        response.put("tokenType", "Bearer");

        return ResponseEntity.ok(response);
    }
}