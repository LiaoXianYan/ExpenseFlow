package com.expenseflow.ai.vo;

import lombok.Data;

@Data
public class RagAnswerVO {
    private String question;
    private String answer;
    private String model;
}
