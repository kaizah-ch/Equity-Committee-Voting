package com.equitycommittee.voting.config;

import com.equitycommittee.voting.domain.repository.CaseRepository;
import com.equitycommittee.voting.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CaseRepository caseRepository;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider, caseRepository);
    }

    @Test
    void connect_setsAuthenticatedUserWithRole() {
        UUID userId = UUID.randomUUID();
        String token = "access-token";

        Claims claims = mock(Claims.class);
        when(claims.get("role")).thenReturn("ADMIN");
        when(jwtTokenProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtTokenProvider.parseClaims(token)).thenReturn(claims);
        when(jwtTokenProvider.getUserId(token)).thenReturn(userId);

        Message<?> message = connectMessage("Bearer " + token);
        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        Authentication authentication = (Authentication) accessor.getUser();

        assertNotNull(authentication);
        assertEquals(userId.toString(), authentication.getName());
        assertTrue(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals));
    }

    @Test
    void subscribe_caseTopic_withAllowedRoleAndExistingCase_isAllowed() {
        UUID caseId = UUID.randomUUID();
        Authentication principal = authPrincipal("user-1", "ROLE_COMMITTEE_MEMBER");
        when(caseRepository.existsById(caseId)).thenReturn(true);

        Message<?> message = subscribeMessage("/topic/cases/" + caseId + "/messages", principal);

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertNotNull(result);
    }

    @Test
    void subscribe_caseListTopic_withAllowedRole_isAllowed() {
        Authentication principal = authPrincipal("user-1", "ROLE_SECRETARY");
        Message<?> message = subscribeMessage("/topic/cases", principal);

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertNotNull(result);
    }

    @Test
    void subscribe_caseListTopic_withDisallowedRole_isDenied() {
        Authentication principal = authPrincipal("user-1", "ROLE_VIEWER");
        Message<?> message = subscribeMessage("/topic/cases", principal);

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void subscribe_caseTopic_withDisallowedRole_isDenied() {
        UUID caseId = UUID.randomUUID();
        Authentication principal = authPrincipal("user-1", "ROLE_VIEWER");
        Message<?> message = subscribeMessage("/topic/cases/" + caseId + "/votes", principal);

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void subscribe_caseTopic_withInvalidCaseId_isDenied() {
        Authentication principal = authPrincipal("user-1", "ROLE_ADMIN");
        Message<?> message = subscribeMessage("/topic/cases/not-a-uuid/messages", principal);

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void subscribe_caseTopic_withMissingCase_isDenied() {
        UUID caseId = UUID.randomUUID();
        Authentication principal = authPrincipal("user-1", "ROLE_SECRETARY");
        when(caseRepository.existsById(caseId)).thenReturn(false);
        Message<?> message = subscribeMessage("/topic/cases/" + caseId + "/verdict", principal);

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void subscribe_caseTopic_withUnknownSuffix_isDenied() {
        UUID caseId = UUID.randomUUID();
        Authentication principal = authPrincipal("user-1", "ROLE_ADMIN");
        Message<?> message = subscribeMessage("/topic/cases/" + caseId + "/internal", principal);

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    private Message<?> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", authHeader);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> subscribeMessage(String destination, Authentication principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Authentication authPrincipal(String userId, String role) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userId,
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))
        );
    }
}
