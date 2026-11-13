package com.fairyshalltear.freshfruit.security;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.fairyshalltear.freshfruit.account.dto.AccountSummary;
import com.fairyshalltear.freshfruit.account.dto.AuthSession;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final long expirationMinutes;

    public JwtTokenService(JwtEncoder encoder,
            @Value("${security.jwt.expiration-minutes:120}") long expirationMinutes) {
        this.encoder = encoder;
        this.expirationMinutes = expirationMinutes;
    }

    public AuthSession issue(Long accountId, String username, String displayName, String role) {
        return issue(accountId, username, displayName, role, null);
    }

    public AuthSession issueBuyer(Long accountId, String username, String displayName, long credentialVersion) {
        return issue(accountId, username, displayName, "BUYER", credentialVersion);
    }

    private AuthSession issue(Long accountId, String username, String displayName, String role,
            Long credentialVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationMinutes * 60);
        var claimsBuilder = JwtClaimsSet.builder()
                .issuer("fresh-fruit")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(accountId.toString())
                .claim("roles", List.of(role))
                .claim("role", role);
        if (credentialVersion != null) {
            claimsBuilder.claim("credentialVersion", credentialVersion);
        }
        JwtClaimsSet claims = claimsBuilder.build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthSession(token, "Bearer", expirationMinutes * 60,
                new AccountSummary(accountId, username, displayName, role));
    }
}
