package com.equitycommittee.voting.config;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String CASE_TOPIC_PREFIX = "/topic/cases/";
    private static final Set<String> CASE_TOPIC_ALLOWED_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_CREDIT_OFFICER",
            "ROLE_MANAGER",
            "ROLE_COMMITTEE_MEMBER",
            "ROLE_CHAIRPERSON",
            "ROLE_SECRETARY"
    );
    private static final Set<String> CASE_TOPIC_ALLOWED_SUFFIXES = Set.of(
            "/messages",
            "/votes",
            "/verdict"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final CaseRepository caseRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new AccessDeniedException("Missing Authorization header");
        }

        String prefix = "bearer ";
        if (!authorization.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            throw new AccessDeniedException("Invalid Authorization header format");
        }

        String token = authorization.substring(prefix.length()).trim();
        if (!jwtTokenProvider.validateAccessToken(token)) {
            throw new AccessDeniedException("Invalid access token");
        }

        Claims claims = jwtTokenProvider.parseClaims(token);
        String role = String.valueOf(claims.get("role"));
        UUID userId = jwtTokenProvider.getUserId(token);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        accessor.setUser(authentication);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Authentication authentication = (Authentication) accessor.getUser();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        if ("/topic/cases".equals(destination)) {
            requireAnyCommitteeRole(authentication);
            return;
        }

        if (destination.startsWith(CASE_TOPIC_PREFIX)) {
            requireAnyCommitteeRole(authentication);

            UUID caseId = extractCaseId(destination);
            String suffix = extractCaseTopicSuffix(destination);
            if (!CASE_TOPIC_ALLOWED_SUFFIXES.contains(suffix)) {
                throw new AccessDeniedException("Case topic destination not allowed");
            }
            CaseEntry caseEntry = caseRepository.findById(caseId)
                    .orElseThrow(() -> new AccessDeniedException("Case does not exist"));
            requireCaseVisibility(authentication, caseEntry);
        }
    }

    private UUID extractCaseId(String destination) {
        String tail = destination.substring(CASE_TOPIC_PREFIX.length());
        int slashIndex = tail.indexOf('/');
        String caseIdSegment = slashIndex >= 0 ? tail.substring(0, slashIndex) : tail;
        try {
            return UUID.fromString(caseIdSegment);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid case id in destination");
        }
    }

    private String extractCaseTopicSuffix(String destination) {
        String tail = destination.substring(CASE_TOPIC_PREFIX.length());
        int slashIndex = tail.indexOf('/');
        if (slashIndex < 0) {
            return "";
        }
        return tail.substring(slashIndex);
    }

    private void requireAnyCommitteeRole(Authentication authentication) {
        boolean hasAllowedRole = authentication.getAuthorities().stream()
                .map(Object::toString)
                .anyMatch(CASE_TOPIC_ALLOWED_ROLES::contains);
        if (!hasAllowedRole) {
            throw new AccessDeniedException("Insufficient role for case topic");
        }
    }

    private void requireCaseVisibility(Authentication authentication, CaseEntry caseEntry) {
        String role = authentication.getAuthorities().stream()
                .map(Object::toString)
                .filter(CASE_TOPIC_ALLOWED_ROLES::contains)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Insufficient role for case topic"));

        UUID userId = parseUserId(authentication.getName());
        boolean isCreator = caseEntry.getCreatedBy() != null
                && caseEntry.getCreatedBy().getId() != null
                && caseEntry.getCreatedBy().getId().equals(userId);
        if (isCreator || "ROLE_ADMIN".equals(role)) {
            return;
        }

        if ("ROLE_MANAGER".equals(role) && caseEntry.getStatus() != CaseStatus.DRAFT) {
            return;
        }

        boolean managerApproved = caseEntry.getStatus() != CaseStatus.DRAFT
                && caseEntry.getStatus() != CaseStatus.SUBMITTED;
        boolean committeeViewer = "ROLE_COMMITTEE_MEMBER".equals(role)
                || "ROLE_SECRETARY".equals(role)
                || "ROLE_CHAIRPERSON".equals(role);
        if (committeeViewer && managerApproved) {
            return;
        }

        throw new AccessDeniedException("Case topic not visible for role");
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid user id");
        }
    }
}
