package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.voting.VoteResponse;
import com.equitycommittee.voting.api.exception.GlobalExceptionHandler;
import com.equitycommittee.voting.domain.enums.VoteChoice;
import com.equitycommittee.voting.service.VotingService;
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
class VoteControllerHttpTest {

    @Mock
    private VotingService votingService;

    @InjectMocks
    private VoteController voteController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(voteController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void castVote_returnsCreatedWhenServiceAllows() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        when(votingService.castVote(eq(caseId), any()))
                .thenReturn(new VoteResponse(
                        voteId,
                        caseId,
                        UUID.randomUUID(),
                        "Voter",
                        VoteChoice.APPROVE,
                        "Looks good",
                        LocalDateTime.now()
                ));

        mockMvc.perform(post("/api/cases/{caseId}/vote", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("voteChoice", "APPROVE", "reason", "Looks good"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(voteId.toString()))
                .andExpect(jsonPath("$.voteChoice").value("APPROVE"));
    }

    @Test
    void castVote_returnsForbiddenWhenServiceDenies() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(votingService.castVote(eq(caseId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only committee members and chairperson can vote"));

        mockMvc.perform(post("/api/cases/{caseId}/vote", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("voteChoice", "APPROVE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only committee members and chairperson can vote"));
    }
}
