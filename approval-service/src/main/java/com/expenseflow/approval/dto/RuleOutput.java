package com.expenseflow.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuleOutput {
    private boolean needDirector;
    private List<String> warnings = new ArrayList<>();
    private List<Violation> violations = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class Violation {
        private String type;
        private String message;
        private String severity;
    }
}
