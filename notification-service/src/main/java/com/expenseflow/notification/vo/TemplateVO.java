package com.expenseflow.notification.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateVO {
    private Long id;
    private String templateCode;
    private String templateName;
    private String channel;
    private String titleTemplate;
    private String contentTemplate;
    private Integer status;
    private LocalDateTime createTime;
}
