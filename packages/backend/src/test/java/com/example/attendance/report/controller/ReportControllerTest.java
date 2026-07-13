package com.example.attendance.report.controller;

import com.example.attendance.common.config.CorsConfig;
import com.example.attendance.common.config.SecurityConfig;
import com.example.attendance.report.dto.EmployeeMonthlyRecord;
import com.example.attendance.report.dto.MonthlyReportResponse;
import com.example.attendance.report.service.CsvExportService;
import com.example.attendance.report.service.PdfExportService;
import com.example.attendance.report.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = ReportController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {SecurityConfig.class, CorsConfig.class}
    )
)
@Import(ReportControllerTest.TestSecurityConfig.class)
@ActiveProfiles("test")
class ReportControllerTest {

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

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private CsvExportService csvExportService;

    @MockitoBean
    private PdfExportService pdfExportService;

    private final List<EmployeeMonthlyRecord> sampleRecords = List.of(
            new EmployeeMonthlyRecord(
                    UUID.randomUUID(), "田中太郎", "Engineering",
                    20, 9600, 480, 3,
                    java.math.BigDecimal.valueOf(2), java.math.BigDecimal.valueOf(15))
    );

    @Test
    @DisplayName("GET /api/reports/monthly は200とJSONを返す")
    void getMonthlyReport_returns200() throws Exception {
        // Arrange
        var response = new MonthlyReportResponse("2025-01", sampleRecords);
        when(reportService.getMonthlyReport(eq("2025-01"), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/reports/monthly")
                        .param("month", "2025-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2025-01"))
                .andExpect(jsonPath("$.records[0].employeeName").value("田中太郎"));
    }

    @Test
    @DisplayName("GET /api/reports/monthly/csv は200とtext/csvヘッダーを返す")
    void getCsv_returns200WithCsvContentType() throws Exception {
        // Arrange
        var response = new MonthlyReportResponse("2025-01", sampleRecords);
        when(reportService.getMonthlyReport(eq("2025-01"), any())).thenReturn(response);
        when(csvExportService.exportToCsv(eq("2025-01"), any()))
                .thenReturn("header\ndata".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Act & Assert
        mockMvc.perform(get("/api/reports/monthly/csv")
                        .param("month", "2025-01"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"attendance-2025-01.csv\""));
    }

    @Test
    @DisplayName("GET /api/reports/monthly/pdf は200とapplication/pdfヘッダーを返す")
    void getPdf_returns200WithPdfContentType() throws Exception {
        // Arrange
        var response = new MonthlyReportResponse("2025-01", sampleRecords);
        when(reportService.getMonthlyReport(eq("2025-01"), any())).thenReturn(response);
        when(pdfExportService.exportToPdf(eq("2025-01"), any()))
                .thenReturn(new byte[]{0x25, 0x50, 0x44, 0x46});

        // Act & Assert
        mockMvc.perform(get("/api/reports/monthly/pdf")
                        .param("month", "2025-01"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"attendance-2025-01.pdf\""));
    }
}
