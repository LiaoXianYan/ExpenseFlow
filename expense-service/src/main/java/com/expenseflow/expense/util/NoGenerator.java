package com.expenseflow.expense.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class NoGenerator {

    private final AtomicLong counter = new AtomicLong(0);
    private volatile String currentDate = "";

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
        if (!today.equals(currentDate)) {
            synchronized (this) {
                if (!today.equals(currentDate)) {
                    currentDate = today;
                    counter.set(0);
                }
            }
        }
        long seq = counter.incrementAndGet() % 10000;
        return String.format("%s-%s-%04d", prefix, today, seq);
    }
}
