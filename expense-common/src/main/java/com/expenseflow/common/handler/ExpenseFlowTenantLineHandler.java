package com.expenseflow.common.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

public class ExpenseFlowTenantLineHandler implements TenantLineHandler {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    @Override
    public boolean ignoreTable(String tableName) {
        return "sys_tenant".equalsIgnoreCase(tableName)
                || "sys_permission".equalsIgnoreCase(tableName)
                || "sys_user_role".equalsIgnoreCase(tableName)
                || "sys_role_permission".equalsIgnoreCase(tableName);
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            return new LongValue(0);
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    public static void setTenant(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
