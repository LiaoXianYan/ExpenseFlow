package com.expenseflow.common.handler;

import com.expenseflow.common.annotation.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final HttpServletRequest request;
    private final JdbcTemplate jdbcTemplate;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        boolean hasError = false;
        String errorMsg = null;
        try {
            Object result = pjp.proceed();
            return result;
        } catch (Throwable e) {
            hasError = true;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            if (!hasError) {
                saveLog(auditLog, start, null);
            } else {
                saveLog(auditLog, start, errorMsg);
            }
        }
    }

    @Async
    void saveLog(AuditLog auditLog, long start, String errorMsg) {
        try {
            String tenantIdStr = request.getHeader("X-Tenant-Id");
            String userIdStr = request.getHeader("X-User-Id");
            Long tenantId = tenantIdStr != null ? Long.valueOf(tenantIdStr) : 0L;
            Long userId = userIdStr != null ? Long.valueOf(userIdStr) : null;

            jdbcTemplate.update(
                "INSERT INTO sys_audit_log (tenant_id, user_id, username, operation, module, " +
                "target_type, target_id, request_params, ip, user_agent, duration, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                tenantId, userId, request.getHeader("X-Username"),
                auditLog.operation(), auditLog.module(),
                null, null, sanitizeParams(request.getQueryString()),
                getClientIp(request), request.getHeader("User-Agent"),
                System.currentTimeMillis() - start
            );
        } catch (Exception e) {
            log.error("审计日志写入失败", e);
        }
    }

    private String sanitizeParams(String params) {
        if (params == null) return null;
        return params.replaceAll("(password|token)=[^&]+", "$1=***");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        return ip;
    }
}
