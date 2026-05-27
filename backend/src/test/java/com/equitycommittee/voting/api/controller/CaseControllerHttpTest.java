package com.equitycommittee.voting.api.controller;

import com.equitycommittee.voting.api.dto.cases.CaseResponse;
import com.equitycommittee.voting.api.exception.GlobalExceptionHandler;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.service.CaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CaseControllerHttpTest {

    @Mock
    private CaseService caseService;

    @InjectMocks
    private CaseController caseController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(caseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCase_returnsOkWhenServiceAllows() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.getCase(caseId)).thenReturn(sampleCase(caseId));

        mockMvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caseId.toString()))
                .andExpect(jsonPath("$.status").value(CaseStatus.SUBMITTED.name()));
    }

    @Test
    void getCase_returnsForbiddenWhenServiceDenies() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.getCase(caseId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access draft case"));

        mockMvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Not allowed to access draft case"));
    }

    private CaseResponse sampleCase(UUID caseId) {
        return new CaseResponse(
                caseId,
                "EC-20260427-ABC123",
                "Client",
                BigDecimal.valueOf(1000),
                "TERM_LOAN",
                "12m",
                "summary",
                "risk",
                "collateral",
                CaseStatus.SUBMITTED,
                LocalDateTime.now().plusDays(1),
                null,
                UUID.randomUUID(),
                "Creator",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
