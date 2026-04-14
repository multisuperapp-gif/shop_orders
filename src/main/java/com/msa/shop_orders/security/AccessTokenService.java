package com.msa.shop_orders.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.shop_orders.common.exception.UnauthorizedException;
import com.msa.shop_orders.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AccessTokenService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessTokenService.class);
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;

    public AccessTokenService(ApplicationProperties applicationProperties, ObjectMapper objectMapper) {
        this.applicationProperties = applicationProperties;
        this.objectMapper = objectMapper;
    }

    public String generateToken(AuthenticatedUser authenticatedUser) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", authenticatedUser.userId());
            payload.put("sid", authenticatedUser.sessionId());
            payload.put("public_user_id", authenticatedUser.publicUserId());
            payload.put("roles", authenticatedUser.roles());
            payload.put("active_role", authenticatedUser.activeRole());
            payload.put("exp", Instant.now().getEpochSecond() + applicationProperties.security().accessTokenValiditySeconds());
            String payloadEncoded = encode(objectMapper.writeValueAsBytes(payload));

            String signature = sign(header + "." + payloadEncoded);
            LOGGER.debug("Generated access token for userId={}, sessionId={}", authenticatedUser.userId(), authenticatedUser.sessionId());
            return header + "." + payloadEncoded + "." + signature;
        } catch (Exception exception) {
            LOGGER.error("Unable to create access token for userId={}, sessionId={}", authenticatedUser.userId(), authenticatedUser.sessionId(), exception);
            throw new IllegalStateException("Unable to create access token", exception);
        }
    }

    public AuthenticatedUser parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                LOGGER.warn("Access token rejected because it does not have three parts");
                throw new UnauthorizedException("Invalid access token");
            }

            String expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!expectedSignature.equals(parts[2])) {
                LOGGER.warn("Access token signature mismatch");
                throw new UnauthorizedException("Access token signature mismatch");
            }

            byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(decodedPayload, new TypeReference<>() {
            });
            long expiration = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > expiration) {
                LOGGER.warn("Access token expired");
                throw new UnauthorizedException("Access token expired");
            }

            Long userId = ((Number) payload.get("sub")).longValue();
            Number sessionIdValue = (Number) payload.get("sid");
            if (sessionIdValue == null) {
                throw new UnauthorizedException("Access token session is missing");
            }
            Long sessionId = sessionIdValue.longValue();
            String publicUserId = (String) payload.get("public_user_id");
            @SuppressWarnings("unchecked")
            Set<String> roles = Set.copyOf((List<String>) payload.get("roles"));
            String activeRole = (String) payload.get("active_role");
            LOGGER.debug("Parsed access token for userId={}, sessionId={}", userId, sessionId);
            return new AuthenticatedUser(userId, sessionId, publicUserId, roles, activeRole);
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("Unable to parse access token", exception);
            throw new UnauthorizedException("Unable to parse access token");
        }
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(applicationProperties.security().accessTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return encode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
