package com.example.attendance.leave.repository;

import com.example.attendance.leave.entity.LeaveGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveGrantRepository extends JpaRepository<LeaveGrant, UUID> {

    List<LeaveGrant> findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(UUID employeeId, LocalDate date);

    Optional<LeaveGrant> findByEmployeeIdAndGrantDate(UUID employeeId, LocalDate grantDate);

    List<LeaveGrant> findByEmployeeIdOrderByGrantDateAsc(UUID employeeId);
}
