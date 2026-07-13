package com.example.attendance.report.service;

import com.example.attendance.report.dto.EmployeeMonthlyRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class CsvExportServiceImpl implements CsvExportService {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public byte[] exportToCsv(String month, List<EmployeeMonthlyRecord> records) {
        var baos = new ByteArrayOutputStream();
        baos.writeBytes(UTF8_BOM);

        var writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
        writer.println("社員名,部署,出勤日数,勤務時間（分）,残業時間（分）,欠勤日数,有給取得日数,有給残日数");

        for (var record : records) {
            writer.printf("%s,%s,%d,%d,%d,%d,%s,%s%n",
                    record.employeeName(),
                    record.departmentName(),
                    record.workDays(),
                    record.totalWorkMinutes(),
                    record.totalOvertimeMinutes(),
                    record.absentDays(),
                    record.paidLeaveDays(),
                    record.remainingLeaveDays());
        }

        writer.flush();
        log.info("CSV exported: month={}, records={}", month, records.size());
        return baos.toByteArray();
    }
}
