package com.example.attendance.attendance.domain;

import com.example.attendance.attendance.entity.AttendanceRecord;

import java.time.Duration;
import java.util.List;

public record WorkDuration(
    int totalMinutes,
    int breakMinutes,
    int workMinutes,
    int overtimeMinutes
) {
    private static final int STANDARD_WORK_MINUTES = 480;
    private static final int BREAK_THRESHOLD_SHORT = 360;
    private static final int BREAK_THRESHOLD_LONG = 480;
    private static final int BREAK_MINUTES_SHORT = 45;
    private static final int BREAK_MINUTES_LONG = 60;

    public static WorkDuration calculate(List<AttendanceRecord> records, int standardWorkMinutes) {
        int total = records.stream()
            .filter(r -> r.getClockOut() != null)
            .mapToInt(r -> (int) Duration.between(r.getClockIn(), r.getClockOut()).toMinutes())
            .sum();

        int breakMin;
        if (total > BREAK_THRESHOLD_LONG) {
            breakMin = BREAK_MINUTES_LONG;
        } else if (total > BREAK_THRESHOLD_SHORT) {
            breakMin = BREAK_MINUTES_SHORT;
        } else {
            breakMin = 0;
        }

        int work = total - breakMin;
        int overtime = Math.max(0, work - standardWorkMinutes);

        return new WorkDuration(total, breakMin, work, overtime);
    }

    public static WorkDuration calculate(List<AttendanceRecord> records) {
        int total = records.stream()
            .filter(r -> r.getClockOut() != null)
            .mapToInt(r -> (int) Duration.between(r.getClockIn(), r.getClockOut()).toMinutes())
            .sum();

        int breakMin;
        if (total > BREAK_THRESHOLD_LONG) {
            breakMin = BREAK_MINUTES_LONG;
        } else if (total > BREAK_THRESHOLD_SHORT) {
            breakMin = BREAK_MINUTES_SHORT;
        } else {
            breakMin = 0;
        }

        int work = total - breakMin;
        int overtime = Math.max(0, work - STANDARD_WORK_MINUTES);

        return new WorkDuration(total, breakMin, work, overtime);
    }
}
