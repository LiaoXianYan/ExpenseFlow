package com.expenseflow.common.handler;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@Order(1)
public class TenantContextFilter implements Filter {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String tenantIdStr = req.getHeader(HEADER_TENANT_ID);
        Long tenantId = null;
        try {
            if (tenantIdStr != null && !tenantIdStr.isEmpty()) {
                tenantId = Long.valueOf(tenantIdStr);
                ExpenseFlowTenantLineHandler.setTenant(tenantId);
                log.debug("TenantContextFilter: set tenantId={}", tenantId);
            }
            chain.doFilter(request, response);
        } finally {
            if (tenantId != null) {
                ExpenseFlowTenantLineHandler.clear();
            }
        }
    }
}
