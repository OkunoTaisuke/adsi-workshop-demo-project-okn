package com.example.attendance.leave.controller;

import com.example.attendance.common.config.CorsConfig;
import com.example.attendance.common.config.SecurityConfig;
import com.example.attendance.leave.domain.LeaveBalance;
import com.example.attendance.leave.domain.LeaveRequestStatus;
import com.example.attendance.leave.domain.LeaveType;
import com.example.attendance.leave.dto.LeavePendingResponse;
import com.example.attendance.leave.dto.LeaveRequestCreateRequest;
import com.example.attendance.leave.dto.LeaveRequestResponse;
import com.example.attendance.leave.service.LeaveGrantService;
import com.example.attendance.leave.service.LeaveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = LeaveController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {SecurityConfig.class, CorsConfig.class}
    )
)
@Import(LeaveControllerTest.TestSecurityConfig.class)
@ActiveProfiles("test")
class LeaveControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LeaveGrantService leaveGrantService;

    @MockitoBean
    private LeaveService leaveService;

    private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Test
    @DisplayName("GET /api/leaves/balance は200を返す")
    void getBalance_returns200() throws Exception {
        LeaveBalance balance = new LeaveBalance(
            2026, BigDecimal.valueOf(21), BigDecimal.valueOf(3), BigDecimal.valueOf(18),
            8, 32, BigDecimal.valueOf(11), BigDecimal.valueOf(7), List.of());
        when(leaveGrantService.getBalance(EMPLOYEE_ID)).thenReturn(balance);

        mockMvc.perform(get("/api/leaves/balance").param("employeeId", EMPLOYEE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fiscalYear").value(2026))
            .andExpect(jsonPath("$.remainingDays").value(18));
    }

    @Test
    @DisplayName("POST /api/leaves/requests は201を返す")
    void createRequest_returns201() throws Exception {
        var response = new LeaveRequestResponse(
            REQUEST_ID, LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null,
            "私用", LeaveRequestStatus.PENDING, null, null, Instant.now());
        when(leaveService.createRequest(eq(EMPLOYEE_ID), any(LeaveRequestCreateRequest.class))).thenReturn(response);

        var body = new LeaveRequestCreateRequest(LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null, "私用");

        mockMvc.perform(post("/api/leaves/requests")
                .param("employeeId", EMPLOYEE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.leaveType").value("FULL_DAY"));
    }

    @Test
    @DisplayName("GET /api/leaves/requests は200を返す")
    void getMyRequests_returns200() throws Exception {
        var response = new LeaveRequestResponse(
            REQUEST_ID, LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null,
            "私用", LeaveRequestStatus.APPROVED, "鈴木部長", null, Instant.now());
        when(leaveService.getMyRequests(EMPLOYEE_ID, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/leaves/requests").param("employeeId", EMPLOYEE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/leaves/requests/pending は200を返す")
    void getPendingRequests_returns200() throws Exception {
        var response = new LeavePendingResponse(
            REQUEST_ID, EMPLOYEE_ID, "田中太郎", LocalDate.of(2026, 8, 1),
            LeaveType.FULL_DAY, null, "私用", LeaveRequestStatus.PENDING, Instant.now());
        when(leaveService.getPendingRequests(EMPLOYEE_ID)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/leaves/requests/pending").param("approverId", EMPLOYEE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].requesterName").value("田中太郎"));
    }

    @Test
    @DisplayName("PATCH /api/leaves/requests/{id}/approve は200を返す")
    void approve_returns200() throws Exception {
        var response = new LeaveRequestResponse(
            REQUEST_ID, LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null,
            "私用", LeaveRequestStatus.APPROVED, "鈴木部長", null, Instant.now());
        when(leaveService.approve(REQUEST_ID, EMPLOYEE_ID)).thenReturn(response);

        mockMvc.perform(patch("/api/leaves/requests/{id}/approve", REQUEST_ID)
                .param("approverId", EMPLOYEE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("PATCH /api/leaves/requests/{id}/reject は200を返す")
    void reject_returns200() throws Exception {
        var response = new LeaveRequestResponse(
            REQUEST_ID, LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null,
            "私用", LeaveRequestStatus.REJECTED, "鈴木部長", "業務都合", Instant.now());
        when(leaveService.reject(eq(REQUEST_ID), eq(EMPLOYEE_ID), any())).thenReturn(response);

        mockMvc.perform(patch("/api/leaves/requests/{id}/reject", REQUEST_ID)
                .param("approverId", EMPLOYEE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"業務都合\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectReason").value("業務都合"));
    }

    @Test
    @DisplayName("PATCH /api/leaves/requests/{id}/withdraw は200を返す")
    void withdraw_returns200() throws Exception {
        var response = new LeaveRequestResponse(
            REQUEST_ID, LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null,
            "私用", LeaveRequestStatus.WITHDRAWN, null, null, Instant.now());
        when(leaveService.withdraw(REQUEST_ID, EMPLOYEE_ID)).thenReturn(response);

        mockMvc.perform(patch("/api/leaves/requests/{id}/withdraw", REQUEST_ID)
                .param("employeeId", EMPLOYEE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }
}
