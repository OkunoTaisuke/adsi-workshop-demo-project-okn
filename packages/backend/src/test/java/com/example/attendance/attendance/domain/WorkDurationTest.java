package com.example.attendance.attendance.domain;

import com.example.attendance.attendance.entity.AttendanceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkDurationTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 1, 15);

    @Test
    @DisplayName("6時間以下の勤務では休憩0分・残業0分")
    void calculate_sixHoursOrLess_noBreakNoOvertime() {
        // Arrange
        var record = buildRecord(
                Instant.parse("2025-01-15T00:00:00Z"),
                Instant.parse("2025-01-15T05:00:00Z")
        );

        // Act
        var result = WorkDuration.calculate(List.of(record));

        // Assert
        assertThat(result.totalMinutes()).isEqualTo(300);
        assertThat(result.breakMinutes()).isZero();
        assertThat(result.workMinutes()).isEqualTo(300);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    @DisplayName("6時間超〜8時間以下の勤務では休憩45分")
    void calculate_moreThanSixUpToEightHours_breakFortyFiveMinutes() {
        // Arrange
        var record = buildRecord(
                Instant.parse("2025-01-15T00:00:00Z"),
                Instant.parse("2025-01-15T07:00:00Z")
        );

        // Act
        var result = WorkDuration.calculate(List.of(record));

        // Assert
        assertThat(result.totalMinutes()).isEqualTo(420);
        assertThat(result.breakMinutes()).isEqualTo(45);
        assertThat(result.workMinutes()).isEqualTo(375);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    @DisplayName("8時間超の勤務では休憩60分・残業あり")
    void calculate_moreThanEightHours_breakSixtyMinutesWithOvertime() {
        // Arrange
        var record = buildRecord(
                Instant.parse("2025-01-15T00:00:00Z"),
                Instant.parse("2025-01-15T10:00:00Z")
        );

        // Act
        var result = WorkDuration.calculate(List.of(record));

        // Assert
        assertThat(result.totalMinutes()).isEqualTo(600);
        assertThat(result.breakMinutes()).isEqualTo(60);
        assertThat(result.workMinutes()).isEqualTo(540);
        assertThat(result.overtimeMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("複数レコードの合算で勤務時間が計算される")
    void calculate_multipleRecords_sumsTotalMinutes() {
        // Arrange
        var record1 = buildRecord(
                Instant.parse("2025-01-15T00:00:00Z"),
                Instant.parse("2025-01-15T04:00:00Z")
        );
        var record2 = buildRecord(
                Instant.parse("2025-01-15T05:00:00Z"),
                Instant.parse("2025-01-15T09:00:00Z")
        );

        // Act
        var result = WorkDuration.calculate(List.of(record1, record2));

        // Assert
        assertThat(result.totalMinutes()).isEqualTo(480);
        assertThat(result.breakMinutes()).isEqualTo(45);
        assertThat(result.workMinutes()).isEqualTo(435);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    @DisplayName("clockOutがnullのレコードはスキップされる")
    void calculate_nullClockOut_skipsRecord() {
        // Arrange
        var completedRecord = buildRecord(
                Instant.parse("2025-01-15T00:00:00Z"),
                Instant.parse("2025-01-15T04:00:00Z")
        );
        var openRecord = buildRecord(
                Instant.parse("2025-01-15T05:00:00Z"),
                null
        );

        // Act
        var result = WorkDuration.calculate(List.of(completedRecord, openRecord));

        // Assert
        assertThat(result.totalMinutes()).isEqualTo(240);
        assertThat(result.breakMinutes()).isZero();
        assertThat(result.workMinutes()).isEqualTo(240);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    @DisplayName("半日休暇の日: 所定4h、4h超過分が残業")
    void calculateWithStandard_halfDay_overtimeAfterFourHours() {
        var record = buildRecord(
                Instant.parse("2025-01-15T00:00:00Z"),
                Instant.parse("2025-01-15T05:00:00Z")
        );

        var result = WorkDuration.calculate(List.of(record), 240);

        assertThat(result.totalMinutes()).isEqualTo(300);
        assertThat(result.workMinutes()).isEqualTo(300);
        assertThat(result.overtimeMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("時間単位休暇(2h)の日: 所定6h、6h超過分が残業")
    void calculateWithStandard_hourlyLeave_adjustedStandard() {
        var record = buildRecord(
                Instant.parse("2025-01-15T00:00:00Z"),
                Instant.parse("2025-01-15T07:30:00Z")
        );

        var result = WorkDuration.calculate(List.of(record), 360);

        assertThat(result.totalMinutes()).isEqualTo(450);
        assertThat(result.breakMinutes()).isEqualTo(45);
        assertThat(result.workMinutes()).isEqualTo(405);
        assertThat(result.overtimeMinutes()).isEqualTo(45);
    }

    private AttendanceRecord buildRecord(Instant clockIn, Instant clockOut) {
        return AttendanceRecord.builder()
                .id(UUID.randomUUID())
                .workDate(TODAY)
                .clockIn(clockIn)
                .clockOut(clockOut)
                .corrected(false)
                .build();
    }
}
