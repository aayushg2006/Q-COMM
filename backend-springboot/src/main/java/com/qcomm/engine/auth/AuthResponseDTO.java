package com.qcomm.engine.auth;

public record AuthResponseDTO(
        String token,
        String tokenType,
        Long expiresInSeconds,
        String username,
        String role
) {
}

