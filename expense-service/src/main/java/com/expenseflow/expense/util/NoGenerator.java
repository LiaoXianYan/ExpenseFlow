package com.expenseflow.expense.util;

import com.expenseflow.expense.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class NoGenerator {

    private final ExTravelRequestMapper travelMapper;
    private final ExExpenseReportMapper reportMapper;
    private final ExPaymentRecordMapper paymentMapper;

    public String generateTravelNo() {
        return generate("TR");
    }
    public String generateReportNo() {
        return generate("ER");
    }
    public String generatePaymentNo() {
        return generate("PY");
    }

    private String generate(String prefix) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = System.currentTimeMillis() % 100000;
        return String.format("%s-%s-%04d", prefix, today, seq % 10000);
    }
}
