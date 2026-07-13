package com.example.attendance.report.service;

import com.example.attendance.report.dto.EmployeeMonthlyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportServiceTest {

    private CsvExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CsvExportServiceImpl();
    }

    @Test
    @DisplayName("CSV に正しいヘッダーとデータ行が出力される")
    void exportToCsv_validData_returnsCorrectCsv() {
        // Arrange
        var records = List.of(
                new EmployeeMonthlyRecord(
                        UUID.randomUUID(), "田中太郎", "Engineering",
                        20, 9600, 480, 3, java.math.BigDecimal.valueOf(2), java.math.BigDecimal.valueOf(15)),
                new EmployeeMonthlyRecord(
                        UUID.randomUUID(), "鈴木花子", "Sales",
                        22, 10560, 0, 1, java.math.BigDecimal.ONE, java.math.BigDecimal.valueOf(18))
        );

        // Act
        var bytes = service.exportToCsv("2025-01", records);
        var csv = new String(bytes, StandardCharsets.UTF_8);

        // Assert
        assertThat(csv).contains("社員名,部署,出勤日数,勤務時間（分）,残業時間（分）,欠勤日数,有給取得日数,有給残日数");
        assertThat(csv).contains("田中太郎,Engineering,20,9600,480,3,2,15");
        assertThat(csv).contains("鈴木花子,Sales,22,10560,0,1,1,18");
    }

    @Test
    @DisplayName("CSV が UTF-8 BOM 付きで出力される")
    void exportToCsv_hasBom() {
        // Arrange
        var records = List.of(
                new EmployeeMonthlyRecord(
                        UUID.randomUUID(), "田中太郎", "Engineering",
                        20, 9600, 480, 3, java.math.BigDecimal.valueOf(2), java.math.BigDecimal.valueOf(15))
        );

        // Act
        var bytes = service.exportToCsv("2025-01", records);

        // Assert
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }
}
