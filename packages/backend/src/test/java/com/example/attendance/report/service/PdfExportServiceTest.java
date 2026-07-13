package com.example.attendance.report.service;

import com.example.attendance.report.dto.EmployeeMonthlyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportServiceTest {

    private PdfExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PdfExportServiceImpl();
    }

    @Test
    @DisplayName("PDF がバイト配列として生成される")
    void exportToPdf_validData_returnsPdfBytes() {
        // Arrange
        var records = List.of(
                new EmployeeMonthlyRecord(
                        UUID.randomUUID(), "Tanaka Taro", "Engineering",
                        20, 9600, 480, 3,
                        java.math.BigDecimal.valueOf(2), java.math.BigDecimal.valueOf(15))
        );

        // Act
        var bytes = service.exportToPdf("2025-01", records);

        // Assert
        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) '%');
        assertThat(bytes[1]).isEqualTo((byte) 'P');
        assertThat(bytes[2]).isEqualTo((byte) 'D');
        assertThat(bytes[3]).isEqualTo((byte) 'F');
    }
}
