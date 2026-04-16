package com.qcomm.engine.auth;

import com.qcomm.engine.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String configuredUsername;
    private final String configuredRole;
    private final String encodedPassword;

    public AuthService(
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${engine.security.user.username:admin}") String configuredUsername,
            @Value("${engine.security.user.password:admin123}") String configuredPassword,
            @Value("${engine.security.user.role:ADMIN}") String configuredRole
    ) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.configuredUsername = configuredUsername;
        this.configuredRole = configuredRole == null || configuredRole.isBlank() ? "ADMIN" : configuredRole.trim().toUpperCase();
        this.encodedPassword = passwordEncoder.encode(configuredPassword);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        String incomingUsername = request.username().trim();
        if (!configuredUsername.equals(incomingUsername)) {
            throw new BadCredentialsException("Invalid username or password.");
        }
        if (!passwordEncoder.matches(request.password(), encodedPassword)) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        String token = jwtService.generateToken(configuredUsername, configuredRole);
        return new AuthResponseDTO(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                configuredUsername,
                configuredRole
        );
    }
}

