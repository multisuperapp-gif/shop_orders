package com.msa.shop_orders.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.common.exception.UnauthorizedException;
import com.msa.shop_orders.persistence.entity.UserSessionEntity;
import com.msa.shop_orders.persistence.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.stream.Collectors;

@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(BearerAuthenticationFilter.class);
    
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    private final AccessTokenService accessTokenService;
    private final UserSessionRepository userSessionRepository;
    private final ObjectMapper objectMapper;

    public BearerAuthenticationFilter(AccessTokenService accessTokenService, UserSessionRepository userSessionRepository, ObjectMapper objectMapper) {
        this.accessTokenService = accessTokenService;
        this.userSessionRepository = userSessionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            try {
                AuthenticatedUser authenticatedUser = accessTokenService.parseToken(token);
                validateSession(authenticatedUser);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        token,
                        authenticatedUser.roles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet())
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                LOGGER.debug(
                        "Authenticated request for userId={}, sessionId={}, path={}",
                        authenticatedUser.userId(),
                        authenticatedUser.sessionId(),
                        request.getRequestURI()
                );
            } catch (UnauthorizedException exception) {
                LOGGER.warn("Authorization failed for path={}: {}", request.getRequestURI(), exception.getMessage());
                writeUnauthorized(response, exception.getMessage());
                return;
            }
        } else {
            LOGGER.debug("Request without bearer token for path={}", request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure("UNAUTHORIZED", message)));
    }

    private void validateSession(AuthenticatedUser authenticatedUser) {
        UserSessionEntity sessionEntity = userSessionRepository.findByIdAndUser_Id(authenticatedUser.sessionId(), authenticatedUser.userId())
                .orElseThrow(() -> new UnauthorizedException("Active session not found for access token"));
        if (sessionEntity.getRevokedAt() != null) {
            LOGGER.warn("Rejected revoked session: sessionId={}, userId={}", authenticatedUser.sessionId(), authenticatedUser.userId());
            throw new UnauthorizedException("Session has been revoked");
        }
        if (Instant.now().isAfter(sessionEntity.getExpiresAt())) {
            LOGGER.warn("Rejected expired session: sessionId={}, userId={}", authenticatedUser.sessionId(), authenticatedUser.userId());
            throw new UnauthorizedException("Session has expired");
        }
    }
}
