package com.example.attendance.leave.repository;

import com.example.attendance.common.config.JpaAuditingConfig;
import com.example.attendance.department.entity.Department;
import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.entity.Role;
import com.example.attendance.leave.entity.LeaveGrant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class LeaveGrantRepositoryTest {

    @Autowired
    private LeaveGrantRepository repository;

    @Autowired
    private EntityManager em;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = Department.builder()
            .id(UUID.randomUUID())
            .name("開発部")
            .build();
        em.persist(dept);

        employee = Employee.builder()
            .id(UUID.randomUUID())
            .name("田中太郎")
            .email("tanaka@example.com")
            .password("$2a$10$dummy")
            .department(dept)
            .role(Role.EMPLOYEE)
            .isManager(false)
            .hireDate(LocalDate.of(2024, 4, 1))
            .build();
        em.persist(employee);
        em.flush();
    }

    @Test
    @DisplayName("有効な付与レコードのみ返す（expiryDate > 指定日）")
    void findByEmployeeAndExpiryDateAfter_returnsValidGrants() {
        LeaveGrant validGrant = LeaveGrant.builder()
            .id(UUID.randomUUID())
            .employee(employee)
            .fiscalYear(2025)
            .grantDate(LocalDate.of(2025, 10, 1))
            .grantedDays(BigDecimal.TEN)
            .usedDays(BigDecimal.ZERO)
            .expiryDate(LocalDate.of(2028, 3, 31))
            .build();
        em.persist(validGrant);

        LeaveGrant expiredGrant = LeaveGrant.builder()
            .id(UUID.randomUUID())
            .employee(employee)
            .fiscalYear(2023)
            .grantDate(LocalDate.of(2024, 10, 1))
            .grantedDays(BigDecimal.TEN)
            .usedDays(BigDecimal.valueOf(5))
            .expiryDate(LocalDate.of(2026, 3, 31))
            .build();
        em.persist(expiredGrant);
        em.flush();

        LocalDate today = LocalDate.of(2026, 7, 1);
        List<LeaveGrant> results = repository.findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(
            employee.getId(), today);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFiscalYear()).isEqualTo(2025);
    }

    @Test
    @DisplayName("社員ID+付与日で付与レコードを検索")
    void findByEmployeeIdAndGrantDate_returnsExisting() {
        LocalDate grantDate = LocalDate.of(2025, 10, 1);
        LeaveGrant grant = LeaveGrant.builder()
            .id(UUID.randomUUID())
            .employee(employee)
            .fiscalYear(2025)
            .grantDate(grantDate)
            .grantedDays(BigDecimal.TEN)
            .usedDays(BigDecimal.ZERO)
            .expiryDate(LocalDate.of(2028, 3, 31))
            .build();
        em.persist(grant);
        em.flush();

        var result = repository.findByEmployeeIdAndGrantDate(employee.getId(), grantDate);

        assertThat(result).isPresent();
        assertThat(result.get().getGrantedDays()).isEqualByComparingTo(BigDecimal.TEN);
    }
}
