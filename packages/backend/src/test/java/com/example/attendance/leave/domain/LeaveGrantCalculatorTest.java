package com.example.attendance.leave.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveGrantCalculatorTest {

    private final LeaveGrantCalculator calculator = new LeaveGrantCalculator();

    @ParameterizedTest
    @DisplayName("勤続年数に応じた付与日数")
    @CsvSource({
        "0.5, 10",
        "1.5, 11",
        "2.5, 12",
        "3.5, 14",
        "4.5, 16",
        "5.5, 18",
        "6.5, 20",
        "7.5, 20",
        "10.0, 20"
    })
    void calculateGrantDays_byTenure(double tenureYears, int expectedDays) {
        BigDecimal result = calculator.calculateGrantDays(tenureYears);
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(expectedDays));
    }

    @Test
    @DisplayName("初回付与基準日は入社6ヶ月後")
    void calculateNextGrantDate_firstGrant_sixMonthsAfterHire() {
        LocalDate hireDate = LocalDate.of(2025, 4, 1);

        List<LocalDate> grantDates = calculator.calculateGrantDatesUpTo(hireDate, LocalDate.of(2025, 10, 1));

        assertThat(grantDates).containsExactly(LocalDate.of(2025, 10, 1));
    }

    @Test
    @DisplayName("2回目以降の付与基準日は前回+1年")
    void calculateNextGrantDate_subsequentGrants_oneYearAfterPrevious() {
        LocalDate hireDate = LocalDate.of(2024, 4, 1);

        List<LocalDate> grantDates = calculator.calculateGrantDatesUpTo(hireDate, LocalDate.of(2026, 10, 1));

        assertThat(grantDates).containsExactly(
            LocalDate.of(2024, 10, 1),
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2026, 10, 1)
        );
    }

    @Test
    @DisplayName("現在日が付与基準日より前なら付与なし")
    void calculateGrantDatesUpTo_beforeFirstGrant_returnsEmpty() {
        LocalDate hireDate = LocalDate.of(2026, 4, 1);

        List<LocalDate> grantDates = calculator.calculateGrantDatesUpTo(hireDate, LocalDate.of(2026, 9, 30));

        assertThat(grantDates).isEmpty();
    }

    @Test
    @DisplayName("年度判定: 4月は同年度")
    void determineFiscalYear_april_returnsSameYear() {
        int fiscalYear = calculator.determineFiscalYear(LocalDate.of(2026, 4, 1));
        assertThat(fiscalYear).isEqualTo(2026);
    }

    @Test
    @DisplayName("年度判定: 3月は前年度")
    void determineFiscalYear_march_returnsPreviousYear() {
        int fiscalYear = calculator.determineFiscalYear(LocalDate.of(2027, 3, 31));
        assertThat(fiscalYear).isEqualTo(2026);
    }

    @Test
    @DisplayName("年度判定: 1月は前年度")
    void determineFiscalYear_january_returnsPreviousYear() {
        int fiscalYear = calculator.determineFiscalYear(LocalDate.of(2027, 1, 15));
        assertThat(fiscalYear).isEqualTo(2026);
    }

    @Test
    @DisplayName("失効日は付与年度の翌々年度末(3/31)")
    void calculateExpiryDate_twoFiscalYearsLater() {
        LocalDate expiryDate = calculator.calculateExpiryDate(LocalDate.of(2025, 10, 1));
        assertThat(expiryDate).isEqualTo(LocalDate.of(2028, 3, 31));
    }

    @Test
    @DisplayName("4月付与の失効日")
    void calculateExpiryDate_aprilGrant() {
        LocalDate expiryDate = calculator.calculateExpiryDate(LocalDate.of(2026, 4, 1));
        assertThat(expiryDate).isEqualTo(LocalDate.of(2029, 3, 31));
    }

    @Test
    @DisplayName("付与基準日ごとの勤続年数計算")
    void calculateTenureYears_atGrantDate() {
        LocalDate hireDate = LocalDate.of(2024, 4, 1);
        LocalDate grantDate = LocalDate.of(2024, 10, 1);

        double tenure = calculator.calculateTenureYears(hireDate, grantDate);

        assertThat(tenure).isEqualTo(0.5);
    }

    @Test
    @DisplayName("2回目付与時の勤続年数")
    void calculateTenureYears_secondGrant() {
        LocalDate hireDate = LocalDate.of(2024, 4, 1);
        LocalDate grantDate = LocalDate.of(2025, 10, 1);

        double tenure = calculator.calculateTenureYears(hireDate, grantDate);

        assertThat(tenure).isEqualTo(1.5);
    }
}
