package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.messages.MessageResponse;
import com.equitycommittee.voting.api.exception.GlobalExceptionHandler;
import com.equitycommittee.voting.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MessageControllerHttpTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageController messageController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(messageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendMessage_returnsCreatedWhenServiceAllows() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(messageService.sendMessage(eq(caseId), any()))
                .thenReturn(new MessageResponse(
                        messageId,
                        caseId,
                        UUID.randomUUID(),
                        "Sender",
                        "hello",
                        null,
                        LocalDateTime.now()
                ));

        mockMvc.perform(post("/api/cases/{caseId}/messages", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("messageText", "hello"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(messageId.toString()))
                .andExpect(jsonPath("$.caseId").value(caseId.toString()));
    }

    @Test
    void sendMessage_returnsForbiddenWhenServiceDenies() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(messageService.sendMessage(eq(caseId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access discussion for draft case"));

        mockMvc.perform(post("/api/cases/{caseId}/messages", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("messageText", "hello"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Not allowed to access discussion for draft case"));
    }
}
