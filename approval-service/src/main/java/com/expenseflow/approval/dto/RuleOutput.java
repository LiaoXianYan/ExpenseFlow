package com.expenseflow.approval.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuleOutput {
    private boolean needDirector;
    private List<String> warnings = new ArrayList<>();
}
