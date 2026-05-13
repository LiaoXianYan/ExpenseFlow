# M2: Gateway 安全 + 系统服务 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Gateway JWT 验签 + Sentinel 限流，system-service 完整 RBAC 业务（租户/用户/角色/权限/部门/员工/字典），多租户请求拦截器，钉钉 OAuth2 Mock SSO，Redis 二级缓存，操作审计 AOP。

**Architecture:** Gateway 层负责 JWT 验签和透传 userId+tenantId 到下游请求头，system-service 读取并注入多租户 SQL。Spring Security + JWT 无状态认证，RBAC 基于 `sys_user_role` / `sys_role_permission` 关联表。Caffeine 本地缓存 + Redis 二级缓存。

**Tech Stack:** Spring Boot 3.3 + Spring Security + JJWT 0.12.6 + MyBatis-Plus 3.5（多租户插件）+ Redis 7 + Caffeine + Sentinel 嵌入式

---

### Task 1: 添加 M2 依赖

**Files:**
- Modify: `gateway-service/pom.xml`
- Modify: `system-service/pom.xml`
- Modify: `pom.xml`（父 POM 版本管理）

- [ ] **Step 1: 父 POM 添加 JJWT 和 Sentinel 版本管理**

在 `pom.xml` 的 `<properties>` 中添加：
```xml
<jjwt.version>0.12.6</jjwt.version>
<sentinel.version>2023.0.1.0</sentinel.version>
```

- [ ] **Step 2: gateway-service 添加依赖**

在 `gateway-service/pom.xml` 的 `<dependencies>` 中添加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
</dependency>
```

- [ ] **Step 3: system-service 添加依赖**

在 `system-service/pom.xml` 的 `<dependencies>` 中添加：
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

- [ ] **Step 4: Commit**

```bash
git add pom.xml gateway-service/pom.xml system-service/pom.xml
git commit -m "chore(m2): 添加 Gateway JWT/Sentinel + system-service JJWT/Caffeine 依赖"
```

---

### Task 2: expense-common 公共组件 — JwtUtil

**Files:**
- Create: `expense-common/src/main/java/com/expenseflow/common/util/JwtUtil.java`

- [ ] **Step 1: 创建 JwtUtil**

```java
package com.expenseflow.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class JwtUtil {

    private static final String SECRET = "ExpenseFlow2026SecretKeyForJWTTokenGenerationMustBeLongEnough!!";
    private static final long ACCESS_EXPIRE = 2 * 60 * 60 * 1000L; // 2h
    private static final long REFRESH_EXPIRE = 7 * 24 * 60 * 60 * 1000L; // 7d

    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateAccessToken(Long userId, Long tenantId, String tokenId) {
        return generateToken(userId, tenantId, tokenId, ACCESS_EXPIRE);
    }

    public static String generateRefreshToken(Long userId, Long tenantId, String tokenId) {
        return generateToken(userId, tenantId, tokenId, REFRESH_EXPIRE);
    }

    private static String generateToken(Long userId, Long tenantId, String tokenId, long expire) {
        Date now = new Date();
        return Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expire))
                .signWith(getKey())
                .compact();
    }

    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public static Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static Long getTenantId(Claims claims) {
        return claims.get("tenantId", Long.class);
    }

    public static String getTokenId(Claims claims) {
        return claims.getId();
    }

    public static boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add expense-common/src/main/java/com/expenseflow/common/util/
git commit -m "feat(common): 添加 JwtUtil — 生成/解析 JWT (userId+tenantId+tokenId)"
```

---

### Task 3: expense-common 公共组件 — TenantContextFilter

**Files:**
- Create: `expense-common/src/main/java/com/expenseflow/common/handler/TenantContextFilter.java`

- [ ] **Step 1: 创建 TenantContextFilter**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add expense-common/src/main/java/com/expenseflow/common/handler/TenantContextFilter.java
git commit -m "feat(common): 添加 TenantContextFilter — 从 X-Tenant-Id 请求头提取租户并写入 ThreadLocal"
```

---

### Task 4: expense-common 公共组件 — @AuditLog 注解 + AOP

**Files:**
- Create: `expense-common/src/main/java/com/expenseflow/common/annotation/AuditLog.java`
- Create: `expense-common/src/main/java/com/expenseflow/common/handler/AuditLogAspect.java`

- [ ] **Step 1: 创建 @AuditLog 注解**

```java
package com.expenseflow.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String module() default "";
    String operation() default "";
}
```

- [ ] **Step 2: 创建 AuditLogAspect**

```java
package com.expenseflow.common.handler;

import com.expenseflow.common.annotation.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        String errorMsg = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable e) {
            errorMsg = e.getMessage();
            throw e;
        } finally {
            saveLog(auditLog, start, errorMsg);
        }
    }

    @Async
    void saveLog(AuditLog auditLog, long start, String errorMsg) {
        try {
            String tenantIdStr = request.getHeader("X-Tenant-Id");
            String userIdStr = request.getHeader("X-User-Id");
            Long tenantId = tenantIdStr != null ? Long.valueOf(tenantIdStr) : 0L;
            Long userId = userIdStr != null ? Long.valueOf(userIdStr) : null;

            String params = sanitizeParams(request.getQueryString());

            jdbcTemplate.update(
                "INSERT INTO sys_audit_log (tenant_id, user_id, username, operation, module, " +
                "target_type, target_id, request_params, ip, user_agent, duration, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                tenantId, userId, request.getHeader("X-Username"),
                auditLog.operation(), auditLog.module(),
                null, null, params,
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
```

- [ ] **Step 3: 在 expense-common 启用异步**

创建 `expense-common/src/main/java/com/expenseflow/common/config/AsyncConfig.java`：
```java
package com.expenseflow.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
}
```

- [ ] **Step 4: Commit**

```bash
git add expense-common/src/main/java/com/expenseflow/common/annotation/ \
        expense-common/src/main/java/com/expenseflow/common/handler/AuditLogAspect.java \
        expense-common/src/main/java/com/expenseflow/common/config/AsyncConfig.java
git commit -m "feat(common): 添加 @AuditLog 注解 + AOP 切面，异步写入 sys_audit_log"
```

---

### Task 5: system-service Entity 层 (12 个 PO)

**Files:**
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysTenant.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysUser.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysRole.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysPermission.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysUserRole.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysRolePermission.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysDepartment.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysEmployee.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysDictType.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysDictData.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysAuditLog.java`
- Create: `system-service/src/main/java/com/expenseflow/system/entity/SysOauthUser.java`

- [ ] **Step 1: 创建 SysTenant**

```java
package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private LocalDateTime expireTime;
}
```

- [ ] **Step 2: 创建 SysUser**

```java
package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private LocalDateTime lastLoginTime;
}
```

- [ ] **Step 3: 创建 SysRole**

```java
package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {
    private String roleCode;
    private String roleName;
    private Integer roleType;
    private Integer status;
}
```

- [ ] **Step 4: 创建 SysPermission**

```java
package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {
    private Long parentId;
    private String permissionCode;
    private String permissionName;
    private Integer permissionType;
    private String path;
    private String icon;
    private Integer sortOrder;
}
```

- [ ] **Step 5: 创建 SysUserRole**

```java
package com.expenseflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_role")
public class SysUserRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long roleId;
}
```

- [ ] **Step 6: 创建 SysRolePermission**

```java
package com.expenseflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role_permission")
public class SysRolePermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long permissionId;
}
```

- [ ] **Step 7: 创建 SysDepartment**

```java
package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_department")
public class SysDepartment extends BaseEntity {
    private Long parentId;
    private String deptName;
    private String deptCode;
    private Long leaderId;
    private Integer sortOrder;
}
```

- [ ] **Step 8: 创建 SysEmployee**

```java
package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_employee")
public class SysEmployee extends BaseEntity {
    private Long userId;
    private Long departmentId;
    private String employeeNo;
    private String position;
    private LocalDate hireDate;
}
```

- [ ] **Step 9: 创建 SysDictType**

```java
package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {
    private String dictCode;
    private String dictName;
    private Integer status;
}
```

- [ ] **Step 10: 创建 SysDictData**

```java
package com.expenseflow.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {
    private Long dictTypeId;
    private String dictLabel;
    private String dictValue;
    private Integer sortOrder;
    private Integer status;
}
```

- [ ] **Step 11: 创建 SysAuditLog**

```java
package com.expenseflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String operation;
    private String module;
    private String targetType;
    private String targetId;
    private String requestParams;
    private String oldValue;
    private String newValue;
    private String ip;
    private String userAgent;
    private Long duration;
    private LocalDateTime createTime;
}
```

- [ ] **Step 12: 创建 SysOauthUser**

```java
package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oauth_user")
public class SysOauthUser extends BaseEntity {
    private Long userId;
    private String provider;
    private String openId;
    private String unionId;
    private String accessToken;
    private String refreshToken;
}
```

- [ ] **Step 13: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/entity/
git commit -m "feat(system): 添加 12 个 Entity (sys_tenant ~ sys_oauth_user)"
```

---

### Task 6: system-service Mapper 层 (12 个)

**Files:**
- Create: `system-service/src/main/java/com/expenseflow/system/mapper/` 下 12 个 Mapper 接口

- [ ] **Step 1: 批量创建 12 个 Mapper**

```java
// SysTenantMapper.java
package com.expenseflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.system.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}
```

其余 11 个 Mapper 同理：`SysUserMapper`、`SysRoleMapper`、`SysPermissionMapper`、`SysUserRoleMapper`、`SysRolePermissionMapper`、`SysDepartmentMapper`、`SysEmployeeMapper`、`SysDictTypeMapper`、`SysDictDataMapper`、`SysAuditLogMapper`、`SysOauthUserMapper`，均继承 `BaseMapper<对应Entity>`，标注 `@Mapper`。

- [ ] **Step 2: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/mapper/
git commit -m "feat(system): 添加 12 个 MyBatis-Plus Mapper 接口"
```

---

### Task 7: system-service DTO + VO

**Files:**
- Create: `system-service/src/main/java/com/expenseflow/system/dto/` 下 DTO 类
- Create: `system-service/src/main/java/com/expenseflow/system/vo/` 下 VO 类

- [ ] **Step 1: 创建核心 DTO**

LoginDTO:
```java
package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
}
```

TenantDTO（创建/更新共用）:
```java
package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantDTO {
    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;
    @NotBlank(message = "租户名称不能为空")
    private String tenantName;
    private String contactName;
    private String contactPhone;
}
```

UserDTO（创建/更新共用）:
```java
package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    private String password;
    @NotBlank(message = "姓名不能为空")
    private String realName;
    private String phone;
    private String email;
    private Long roleId;
}
```

RoleDTO:
```java
package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class RoleDTO {
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
}

@Data
public class RoleAssignUsersDTO {
    private List<Long> userIds;
}

@Data
public class RoleAssignPermsDTO {
    private List<Long> permissionIds;
}
```

DepartmentDTO:
```java
package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentDTO {
    @NotBlank(message = "部门名称不能为空")
    private String deptName;
    private String deptCode;
    private Long parentId;
    private Long leaderId;
    private Integer sortOrder;
}
```

EmployeeDTO:
```java
package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class EmployeeDTO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotNull(message = "部门ID不能为空")
    private Long departmentId;
    private String employeeNo;
    private String position;
    private LocalDate hireDate;
}
```

DictTypeDTO:
```java
package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictTypeDTO {
    @NotBlank(message = "字典编码不能为空")
    private String dictCode;
    @NotBlank(message = "字典名称不能为空")
    private String dictName;
}
```

DictDataDTO:
```java
package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DictDataDTO {
    @NotNull(message = "字典类型ID不能为空")
    private Long dictTypeId;
    @NotBlank(message = "标签不能为空")
    private String dictLabel;
    @NotBlank(message = "值不能为空")
    private String dictValue;
    private Integer sortOrder;
}
```

- [ ] **Step 2: 创建 VO**

TenantVO:
```java
package com.expenseflow.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TenantVO {
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
```

UserVO:
```java
package com.expenseflow.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private Long tenantId;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}
```

TokenVO:
```java
package com.expenseflow.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenVO {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserVO user;
}
```

DeptTreeVO:
```java
package com.expenseflow.system.vo;

import lombok.Data;
import java.util.List;

@Data
public class DeptTreeVO {
    private Long id;
    private Long parentId;
    private String deptName;
    private String deptCode;
    private Long leaderId;
    private Integer sortOrder;
    private List<DeptTreeVO> children;
}
```

PermissionTreeVO:
```java
package com.expenseflow.system.vo;

import lombok.Data;
import java.util.List;

@Data
public class PermissionTreeVO {
    private Long id;
    private Long parentId;
    private String permissionCode;
    private String permissionName;
    private Integer permissionType;
    private String path;
    private String icon;
    private Integer sortOrder;
    private List<PermissionTreeVO> children;
}
```

其余 VO（RoleVO、EmployeeVO、DictTypeVO、DictDataVO）同理，按 Entity 字段映射（排除敏感字段如 password）。

- [ ] **Step 3: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/dto/ \
        system-service/src/main/java/com/expenseflow/system/vo/
git commit -m "feat(system): 添加 DTO (Login/Tenant/User/Role/Dept/Employee/Dict) + VO"
```

---

### Task 8: system-service Config 层

**Files:**
- Create: `system-service/src/main/java/com/expenseflow/system/config/SecurityConfig.java`
- Create: `system-service/src/main/java/com/expenseflow/system/config/JwtAuthFilter.java`
- Create: `system-service/src/main/java/com/expenseflow/system/config/MybatisPlusConfig.java`
- Create: `system-service/src/main/java/com/expenseflow/system/config/CacheConfig.java`

- [ ] **Step 1: 创建 SecurityConfig**

```java
package com.expenseflow.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/system/auth/**", "/system/oauth/**",
                    "/actuator/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 2: 创建 JwtAuthFilter（system-service 侧）**

```java
package com.expenseflow.system.config;

import com.expenseflow.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);
        Claims claims = JwtUtil.parseToken(token);
        if (claims != null && !JwtUtil.isExpired(claims)) {
            Long userId = JwtUtil.getUserId(claims);
            Long tenantId = JwtUtil.getTenantId(claims);
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            auth.setDetails(tenantId);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: 创建 MybatisPlusConfig**

```java
package com.expenseflow.system.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantInnerInterceptor;
import com.expenseflow.common.handler.ExpenseFlowTenantLineHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户插件
        interceptor.addInnerInterceptor(new TenantInnerInterceptor(new ExpenseFlowTenantLineHandler()));
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 4: 创建 CacheConfig**

```java
package com.expenseflow.system.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        CaffeineCache userCache = new CaffeineCache("user",
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build());
        CaffeineCache userPermCache = new CaffeineCache("userPerm",
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build());
        CaffeineCache deptTreeCache = new CaffeineCache("deptTree",
            Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(100).build());
        manager.setCaches(Arrays.asList(userCache, userPermCache, deptTreeCache));
        return manager;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/config/
git commit -m "feat(system): 添加 SecurityConfig + JwtAuthFilter + MybatisPlusConfig + CacheConfig"
```

---

### Task 9: system-service Service 层（Auth + User）

**Files:**
- Create: `system-service/src/main/java/com/expenseflow/system/service/AuthService.java`
- Create: `system-service/src/main/java/com/expenseflow/system/service/impl/AuthServiceImpl.java`
- Create: `system-service/src/main/java/com/expenseflow/system/service/UserService.java`
- Create: `system-service/src/main/java/com/expenseflow/system/service/impl/UserServiceImpl.java`

- [ ] **Step 1: 创建 AuthService 接口**

```java
package com.expenseflow.system.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.LoginDTO;
import com.expenseflow.system.vo.TokenVO;
import com.expenseflow.system.vo.UserVO;

public interface AuthService {
    Result<TokenVO> login(LoginDTO dto);
    Result<Void> logout(String token);
    Result<TokenVO> refresh(String refreshToken);
    Result<UserVO> me(Long userId);
}
```

- [ ] **Step 2: 创建 AuthServiceImpl**

```java
package com.expenseflow.system.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.common.util.JwtUtil;
import com.expenseflow.system.dto.LoginDTO;
import com.expenseflow.system.entity.SysUser;
import com.expenseflow.system.mapper.SysUserMapper;
import com.expenseflow.system.vo.TokenVO;
import com.expenseflow.system.vo.UserVO;
import com.expenseflow.system.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result<TokenVO> login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            return Result.fail(401, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            return Result.fail(403, "账号已被禁用");
        }
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getTenantId(), tokenId);
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getTenantId(), tokenId);

        UserVO userVO = toUserVO(user);
        redisTemplate.opsForValue().set("user:" + user.getId(), userVO, 30, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set("token:" + tokenId, "1", 2, TimeUnit.HOURS);

        return Result.ok(new TokenVO(accessToken, refreshToken, 7200, userVO));
    }

    @Override
    public Result<Void> logout(String token) {
        Claims claims = JwtUtil.parseToken(token);
        if (claims != null) {
            String tokenId = JwtUtil.getTokenId(claims);
            long expire = claims.getExpiration().getTime() - System.currentTimeMillis();
            redisTemplate.opsForValue().set("token:blacklist:" + tokenId, "1",
                Math.max(expire, 1000), TimeUnit.MILLISECONDS);
        }
        return Result.ok();
    }

    @Override
    public Result<TokenVO> refresh(String refreshToken) {
        Claims claims = JwtUtil.parseToken(refreshToken);
        if (claims == null || JwtUtil.isExpired(claims)) {
            return Result.fail(401, "RefreshToken 无效或已过期");
        }
        Long userId = JwtUtil.getUserId(claims);
        Long tenantId = JwtUtil.getTenantId(claims);
        String newTokenId = UUID.randomUUID().toString().replace("-", "");
        String newAccessToken = JwtUtil.generateAccessToken(userId, tenantId, newTokenId);
        String newRefreshToken = JwtUtil.generateRefreshToken(userId, tenantId, newTokenId);

        redischesTemplate.opsForValue().set("token:" + newTokenId, "1", 2, TimeUnit.HOURS);

        SysUser user = userMapper.selectById(userId);
        return Result.ok(new TokenVO(newAccessToken, newRefreshToken, 7200, toUserVO(user)));
    }

    @Override
    public Result<UserVO> me(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) return Result.fail(404, "用户不存在");
        return Result.ok(toUserVO(user));
    }

    private UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setTenantId(user.getTenantId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
```

- [ ] **Step 3: 创建 UserService 接口和实现**

UserService:
```java
package com.expenseflow.system.service;

import com.expenseflow.common.result.PageResult;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.UserDTO;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface UserService {
    Result<Page<UserVO>> page(int page, int size, String keyword);
    Result<UserVO> getById(Long id);
    Result<UserVO> create(UserDTO dto);
    Result<UserVO> update(Long id, UserDTO dto);
    Result<Void> delete(Long id);
    Result<Void> updateStatus(Long id, Integer status);
    Result<Void> resetPassword(Long id, String newPassword);
}
```

UserServiceImpl:
```java
package com.expenseflow.system.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.common.handler.ExpenseFlowTenantLineHandler;
import com.expenseflow.system.dto.UserDTO;
import com.expenseflow.system.entity.SysUser;
import com.expenseflow.system.mapper.SysUserMapper;
import com.expenseflow.system.service.UserService;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result<Page<UserVO>> page(int page, int size, String keyword) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        qw.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> pg = userMapper.selectPage(new Page<>(page, size), qw);
        Page<UserVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(this::toVO).toList());
        return Result.ok(voPage);
    }

    @Override
    public Result<UserVO> getById(Long id) {
        SysUser user = userMapper.selectById(id);
        return user == null ? Result.fail(404, "用户不存在") : Result.ok(toVO(user));
    }

    @Override
    @Transactional
    public Result<UserVO> create(UserDTO dto) {
        long count = userMapper.selectCount(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) return Result.fail(400, "用户名已存在");
        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(passwordEncoder.encode(dto.getPassword() != null ? dto.getPassword() : "123456"));
        user.setStatus(1);
        user.setTenantId(ExpenseFlowTenantLineHandler.getTenant());
        userMapper.insert(user);
        return Result.ok(toVO(user));
    }

    @Override
    @Transactional
    public Result<UserVO> update(Long id, UserDTO dto) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return Result.fail(404, "用户不存在");
        if (dto.getUsername() != null) user.setUsername(dto.getUsername());
        if (dto.getRealName() != null) user.setRealName(dto.getRealName());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPassword() != null) user.setPassword(passwordEncoder.encode(dto.getPassword()));
        userMapper.updateById(user);
        redisTemplate.delete("user:" + id);
        return Result.ok(toVO(user));
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id) {
        userMapper.deleteById(id);
        redisTemplate.delete("user:" + id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> updateStatus(Long id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return Result.fail(404, "用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
        redisTemplate.delete("user:" + id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> resetPassword(Long id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return Result.fail(404, "用户不存在");
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.ok();
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/service/AuthService.java \
        system-service/src/main/java/com/expenseflow/system/service/impl/AuthServiceImpl.java \
        system-service/src/main/java/com/expenseflow/system/service/UserService.java \
        system-service/src/main/java/com/expenseflow/system/service/impl/UserServiceImpl.java
git commit -m "feat(system): 添加 AuthService (登录/登出/刷新/个人信息) + UserService CRUD"
```

---

### Task 10: system-service Controller 层（Auth + Tenant + User + Role）

**Files:**
- Create: `system-service/src/main/java/com/expenseflow/system/controller/AuthController.java`
- Create: `system-service/src/main/java/com/expenseflow/system/controller/TenantController.java`
- Create: `system-service/src/main/java/com/expenseflow/system/controller/UserController.java`
- Create: `system-service/src/main/java/com/expenseflow/system/controller/RoleController.java`

- [ ] **Step 1: 创建 AuthController**

```java
package com.expenseflow.system.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.LoginDTO;
import com.expenseflow.system.service.AuthService;
import com.expenseflow.system.vo.TokenVO;
import com.expenseflow.system.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO dto) {
        return authService.login(dto);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        return authService.logout(token);
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        return authService.refresh(token);
    }

    @GetMapping("/me")
    public Result<UserVO> me(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return authService.me(userId);
    }
}
```

- [ ] **Step 2: 创建 TenantController**

```java
package com.expenseflow.system.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.TenantDTO;
import com.expenseflow.system.entity.SysTenant;
import com.expenseflow.system.mapper.SysTenantMapper;
import com.expenseflow.system.vo.TenantVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final SysTenantMapper tenantMapper;

    @GetMapping("/page")
    public Result<Page<TenantVO>> page(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysTenant> qw = new LambdaQueryWrapper<>();
        if (keyword != null) {
            qw.like(SysTenant::getTenantName, keyword).or().like(SysTenant::getTenantCode, keyword);
        }
        qw.orderByDesc(SysTenant::getCreateTime);
        Page<SysTenant> pg = tenantMapper.selectPage(new Page<>(page, size), qw);
        Page<TenantVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(this::toVO).toList());
        return Result.ok(voPage);
    }

    @GetMapping("/{id}")
    public Result<TenantVO> getById(@PathVariable Long id) {
        SysTenant t = tenantMapper.selectById(id);
        return t == null ? Result.fail(404, "租户不存在") : Result.ok(toVO(t));
    }

    @PostMapping
    public Result<TenantVO> create(@Valid @RequestBody TenantDTO dto) {
        SysTenant t = new SysTenant();
        BeanUtils.copyProperties(dto, t);
        t.setStatus(1);
        tenantMapper.insert(t);
        return Result.ok(toVO(t));
    }

    @PutMapping("/{id}")
    public Result<TenantVO> update(@PathVariable Long id, @Valid @RequestBody TenantDTO dto) {
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) return Result.fail(404, "租户不存在");
        BeanUtils.copyProperties(dto, t);
        tenantMapper.updateById(t);
        return Result.ok(toVO(t));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tenantMapper.deleteById(id);
        return Result.ok();
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) return Result.fail(404, "租户不存在");
        t.setStatus(status);
        tenantMapper.updateById(t);
        return Result.ok();
    }

    private TenantVO toVO(SysTenant t) {
        TenantVO vo = new TenantVO();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }
}
```

- [ ] **Step 3: 创建 UserController**

```java
package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.UserDTO;
import com.expenseflow.system.service.UserService;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/page")
    public Result<Page<UserVO>> page(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String keyword) {
        return userService.page(page, size, keyword);
    }

    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PostMapping
    @AuditLog(module = "用户管理", operation = "CREATE")
    public Result<UserVO> create(@Valid @RequestBody UserDTO dto) {
        return userService.create(dto);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "用户管理", operation = "UPDATE")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        return userService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "用户管理", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return userService.delete(id);
    }

    @PatchMapping("/{id}/status")
    @AuditLog(module = "用户管理", operation = "UPDATE_STATUS")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return userService.updateStatus(id, status);
    }

    @PatchMapping("/{id}/password")
    @AuditLog(module = "用户管理", operation = "RESET_PASSWORD")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        return userService.resetPassword(id, password);
    }
}
```

- [ ] **Step 4: 创建 RoleController + RoleService**

RoleController 完整 CRUD + 分配用户 + 分配权限：
```java
package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.RoleAssignPermsDTO;
import com.expenseflow.system.dto.RoleAssignUsersDTO;
import com.expenseflow.system.dto.RoleDTO;
import com.expenseflow.system.entity.SysRole;
import com.expenseflow.system.entity.SysUserRole;
import com.expenseflow.system.entity.SysRolePermission;
import com.expenseflow.system.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermMapper;

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
            .orderByDesc(SysRole::getCreateTime)));
    }

    @PostMapping
    @AuditLog(module = "角色管理", operation = "CREATE")
    public Result<SysRole> create(@Valid @RequestBody RoleDTO dto) {
        SysRole role = new SysRole();
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setRoleType(2);
        role.setStatus(1);
        roleMapper.insert(role);
        return Result.ok(role);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "角色管理", operation = "UPDATE")
    public Result<SysRole> update(@PathVariable Long id, @Valid @RequestBody RoleDTO dto) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) return Result.fail(404, "角色不存在");
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        roleMapper.updateById(role);
        return Result.ok(role);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "角色管理", operation = "DELETE")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        roleMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        rolePermMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        return Result.ok();
    }

    @PostMapping("/{id}/users")
    @AuditLog(module = "角色管理", operation = "ASSIGN_USERS")
    @Transactional
    public Result<Void> assignUsers(@PathVariable Long id, @Valid @RequestBody RoleAssignUsersDTO dto) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        List<SysUserRole> records = new ArrayList<>();
        for (Long userId : dto.getUserIds()) {
            SysUserRole ur = new SysUserRole();
            ur.setRoleId(id);
            ur.setUserId(userId);
            records.add(ur);
        }
        if (!records.isEmpty()) {
            records.forEach(userRoleMapper::insert);
        }
        return Result.ok();
    }

    @PostMapping("/{id}/permissions")
    @AuditLog(module = "角色管理", operation = "ASSIGN_PERMISSIONS")
    @Transactional
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody RoleAssignPermsDTO dto) {
        rolePermMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        List<SysRolePermission> records = new ArrayList<>();
        for (Long permId : dto.getPermissionIds()) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(id);
            rp.setPermissionId(permId);
            records.add(rp);
        }
        if (!records.isEmpty()) {
            records.forEach(rolePermMapper::insert);
        }
        return Result.ok();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/controller/
git commit -m "feat(system): 添加 Auth + Tenant + User + Role Controller"
```

---

### Task 11: system-service Controller 层（Permission + Department + Employee + Dict）

**Files:**
- Create: 4 个 Controller

- [ ] **Step 1: 创建 PermissionController（只读）**

```java
package com.expenseflow.system.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.entity.SysPermission;
import com.expenseflow.system.mapper.SysPermissionMapper;
import com.expenseflow.system.vo.PermissionTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionMapper permissionMapper;

    @GetMapping("/tree")
    public Result<List<PermissionTreeVO>> tree() {
        List<SysPermission> all = permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSortOrder));
        Map<Long, List<SysPermission>> parentMap = all.stream()
            .collect(Collectors.groupingBy(p -> p.getParentId() == null ? 0L : p.getParentId()));
        List<PermissionTreeVO> roots = buildTree(0L, parentMap);
        return Result.ok(roots);
    }

    private List<PermissionTreeVO> buildTree(Long parentId, Map<Long, List<SysPermission>> map) {
        List<SysPermission> children = map.getOrDefault(parentId, Collections.emptyList());
        return children.stream().map(p -> {
            PermissionTreeVO vo = new PermissionTreeVO();
            vo.setId(p.getId());
            vo.setParentId(p.getParentId());
            vo.setPermissionCode(p.getPermissionCode());
            vo.setPermissionName(p.getPermissionName());
            vo.setPermissionType(p.getPermissionType());
            vo.setPath(p.getPath());
            vo.setIcon(p.getIcon());
            vo.setSortOrder(p.getSortOrder());
            vo.setChildren(buildTree(p.getId(), map));
            return vo;
        }).collect(Collectors.toList());
    }
}
```

- [ ] **Step 2: 创建 DepartmentController（树形 CRUD）**

```java
package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.DepartmentDTO;
import com.expenseflow.system.entity.SysDepartment;
import com.expenseflow.system.mapper.SysDepartmentMapper;
import com.expenseflow.system.vo.DeptTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final SysDepartmentMapper deptMapper;

    @GetMapping("/tree")
    public Result<List<DeptTreeVO>> tree() {
        List<SysDepartment> all = deptMapper.selectList(
            new LambdaQueryWrapper<SysDepartment>().orderByAsc(SysDepartment::getSortOrder));
        Map<Long, List<SysDepartment>> parentMap = all.stream()
            .collect(Collectors.groupingBy(d -> d.getParentId() == null ? 0L : d.getParentId()));
        return Result.ok(buildTree(0L, parentMap));
    }

    @PostMapping
    @AuditLog(module = "部门管理", operation = "CREATE")
    public Result<DeptTreeVO> create(@Valid @RequestBody DepartmentDTO dto) {
        SysDepartment dept = new SysDepartment();
        BeanUtils.copyProperties(dto, dept);
        deptMapper.insert(dept);
        DeptTreeVO vo = new DeptTreeVO();
        BeanUtils.copyProperties(dept, vo);
        return Result.ok(vo);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "部门管理", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DepartmentDTO dto) {
        SysDepartment dept = deptMapper.selectById(id);
        if (dept == null) return Result.fail(404, "部门不存在");
        BeanUtils.copyProperties(dto, dept);
        dept.setId(id);
        deptMapper.updateById(dept);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "部门管理", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        deptMapper.deleteById(id);
        return Result.ok();
    }

    private List<DeptTreeVO> buildTree(Long parentId, Map<Long, List<SysDepartment>> map) {
        List<SysDepartment> children = map.getOrDefault(parentId, Collections.emptyList());
        return children.stream().map(d -> {
            DeptTreeVO vo = new DeptTreeVO();
            BeanUtils.copyProperties(d, vo);
            vo.setChildren(buildTree(d.getId(), map));
            return vo;
        }).collect(Collectors.toList());
    }
}
```

- [ ] **Step 3: 创建 EmployeeController**

```java
package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.EmployeeDTO;
import com.expenseflow.system.entity.SysEmployee;
import com.expenseflow.system.mapper.SysEmployeeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final SysEmployeeMapper employeeMapper;

    @GetMapping("/page")
    public Result<Page<SysEmployee>> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) Long departmentId) {
        LambdaQueryWrapper<SysEmployee> qw = new LambdaQueryWrapper<>();
        if (departmentId != null) qw.eq(SysEmployee::getDepartmentId, departmentId);
        qw.orderByDesc(SysEmployee::getCreateTime);
        return Result.ok(employeeMapper.selectPage(new Page<>(page, size), qw));
    }

    @PostMapping
    @AuditLog(module = "员工管理", operation = "CREATE")
    public Result<SysEmployee> create(@Valid @RequestBody EmployeeDTO dto) {
        SysEmployee emp = new SysEmployee();
        BeanUtils.copyProperties(dto, emp);
        employeeMapper.insert(emp);
        return Result.ok(emp);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "员工管理", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EmployeeDTO dto) {
        SysEmployee emp = employeeMapper.selectById(id);
        if (emp == null) return Result.fail(404, "员工不存在");
        BeanUtils.copyProperties(dto, emp);
        emp.setId(id);
        employeeMapper.updateById(emp);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "员工管理", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        employeeMapper.deleteById(id);
        return Result.ok();
    }
}
```

- [ ] **Step 4: 创建 DictController（DictType + DictData）**

```java
package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.DictDataDTO;
import com.expenseflow.system.dto.DictTypeDTO;
import com.expenseflow.system.entity.SysDictData;
import com.expenseflow.system.entity.SysDictType;
import com.expenseflow.system.mapper.SysDictDataMapper;
import com.expenseflow.system.mapper.SysDictTypeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class DictController {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    @GetMapping("/type/list")
    public Result<List<SysDictType>> typeList() {
        return Result.ok(dictTypeMapper.selectList(
            new LambdaQueryWrapper<SysDictType>().orderByDesc(SysDictType::getCreateTime)));
    }

    @PostMapping("/type")
    @AuditLog(module = "字典管理", operation = "CREATE_TYPE")
    public Result<SysDictType> createType(@Valid @RequestBody DictTypeDTO dto) {
        SysDictType t = new SysDictType();
        BeanUtils.copyProperties(dto, t);
        t.setStatus(1);
        dictTypeMapper.insert(t);
        return Result.ok(t);
    }

    @GetMapping("/data/list")
    public Result<List<SysDictData>> dataList(@RequestParam Long typeId) {
        return Result.ok(dictDataMapper.selectList(
            new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictTypeId, typeId)
                .orderByAsc(SysDictData::getSortOrder)));
    }

    @PostMapping("/data")
    @AuditLog(module = "字典管理", operation = "CREATE_DATA")
    public Result<SysDictData> createData(@Valid @RequestBody DictDataDTO dto) {
        SysDictData d = new SysDictData();
        BeanUtils.copyProperties(dto, d);
        d.setStatus(1);
        dictDataMapper.insert(d);
        return Result.ok(d);
    }

    @PutMapping("/data/{id}")
    @AuditLog(module = "字典管理", operation = "UPDATE_DATA")
    public Result<Void> updateData(@PathVariable Long id, @Valid @RequestBody DictDataDTO dto) {
        SysDictData d = dictDataMapper.selectById(id);
        if (d == null) return Result.fail(404, "字典数据不存在");
        BeanUtils.copyProperties(dto, d);
        d.setId(id);
        dictDataMapper.updateById(d);
        return Result.ok();
    }

    @DeleteMapping("/data/{id}")
    @AuditLog(module = "字典管理", operation = "DELETE_DATA")
    public Result<Void> deleteData(@PathVariable Long id) {
        dictDataMapper.deleteById(id);
        return Result.ok();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/controller/
git commit -m "feat(system): 添加 Permission + Department + Employee + Dict Controller"
```

---

### Task 12: OAuth2 钉钉 SSO Mock

**Files:**
- Create: `system-service/src/main/java/com/expenseflow/system/controller/OAuthController.java`

- [ ] **Step 1: 创建 OAuthController（Mock 钉钉登录）**

```java
package com.expenseflow.system.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.common.util.JwtUtil;
import com.expenseflow.system.entity.SysOauthUser;
import com.expenseflow.system.entity.SysUser;
import com.expenseflow.system.entity.SysUserRole;
import com.expenseflow.system.mapper.SysOauthUserMapper;
import com.expenseflow.system.mapper.SysUserMapper;
import com.expenseflow.system.mapper.SysUserRoleMapper;
import com.expenseflow.system.vo.TokenVO;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/system/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final SysUserMapper userMapper;
    private final SysOauthUserMapper oauthUserMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/dingtalk/login")
    public Result<TokenVO> dingtalkLogin(@RequestBody(required = false) Map<String, String> body) {
        boolean mock = body != null && "true".equals(body.get("mock"));
        String mockOpenId = "mock_dingtalk_" + UUID.randomUUID().toString().substring(0, 8);
        String mockUnionId = "mock_union_" + UUID.randomUUID().toString().substring(0, 8);

        SysOauthUser oauth = oauthUserMapper.selectOne(
            new LambdaQueryWrapper<SysOauthUser>()
                .eq(SysOauthUser::getProvider, "dingtalk")
                .eq(SysOauthUser::getOpenId, mock ? mockOpenId : body != null ? body.get("openId") : ""));
        SysUser user;
        if (oauth != null) {
            user = userMapper.selectById(oauth.getUserId());
            if (user == null || user.getStatus() == 0) {
                return Result.fail(403, "用户已被禁用");
            }
        } else {
            // 自动创建用户
            user = new SysUser();
            user.setTenantId(1L);
            user.setUsername("dingtalk_" + mockOpenId.substring(0, 12));
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRealName("钉钉用户" + mockOpenId.substring(0, 4));
            user.setStatus(1);
            userMapper.insert(user);

            SysOauthUser newOauth = new SysOauthUser();
            newOauth.setTenantId(1L);
            newOauth.setUserId(user.getId());
            newOauth.setProvider("dingtalk");
            newOauth.setOpenId(mock ? mockOpenId : body.get("openId"));
            newOauth.setUnionId(mockUnionId);
            oauthUserMapper.insert(newOauth);

            // 分配普通员工角色
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(3L); // EMPLOYEE 角色
            userRoleMapper.insert(ur);
        }

        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getTenantId(), tokenId);
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getTenantId(), tokenId);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        return Result.ok(new TokenVO(accessToken, refreshToken, 7200, userVO));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/controller/OAuthController.java
git commit -m "feat(system): 添加 OAuthController — 钉钉 SSO Mock 自动创建用户"
```

---

### Task 13: Gateway JWT Filter + Sentinel 配置

**Files:**
- Create: `gateway-service/src/main/java/com/expenseflow/gateway/filter/JwtAuthGatewayFilter.java`
- Create: `gateway-service/src/main/java/com/expenseflow/gateway/config/GatewayConfig.java`
- Modify: `gateway-service/src/main/resources/application.yml`

- [ ] **Step 1: 创建 JwtAuthGatewayFilter**

```java
package com.expenseflow.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/system/auth/login", "/system/auth/refresh", "/system/oauth/",
            "/actuator/", "/nacos/");
    private static final String SECRET = "ExpenseFlow2026SecretKeyForJWTTokenGenerationMustBeLongEnough!!";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public JwtAuthGatewayFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "未提供认证令牌");
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return unauthorized(exchange, "令牌无效或已过期");
        }

        String tokenId = claims.getId();
        Long userId = Long.valueOf(claims.getSubject());
        Long tenantId = claims.get("tenantId", Long.class);

        // 检查黑名单
        return redisTemplate.hasKey("token:blacklist:" + tokenId)
            .flatMap(isBlacklisted -> {
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    return unauthorized(exchange, "令牌已被注销");
                }
                ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            });
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        byte[] bytes = ("{\"code\":401,\"message\":\"" + msg + "\",\"data\":null}").getBytes();
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
```

- [ ] **Step 2: 创建 GatewayConfig（Sentinel 配置）**

```java
package com.expenseflow.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.annotation.PostConstruct;

@Configuration
public class GatewayConfig {

    @PostConstruct
    public void initSentinelBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, t) -> {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(
                new MediaType("application", "json"));
            byte[] bytes = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}".getBytes();
            return exchange.getResponse()
                .writeWith(reactor.core.publisher.Mono.just(
                    exchange.getResponse().bufferFactory().wrap(bytes)));
        });
    }
}
```

- [ ] **Step 3: 更新 gateway application.yml 添加 Sentinel**

在现有的 `gateway-service/src/main/resources/application.yml` 末尾追加：
```yaml
  sentinel:
    transport:
      port: 8719
    datasource:
      ds1:
        nacos:
          server-addr: localhost:8848
          data-id: gateway-sentinel-rules
          group-id: DEFAULT_GROUP
          data-type: json
          rule-type: gw-flow
```

- [ ] **Step 4: Commit**

```bash
git add gateway-service/src/main/java/com/expenseflow/gateway/filter/ \
        gateway-service/src/main/java/com/expenseflow/gateway/config/ \
        gateway-service/src/main/resources/application.yml
git commit -m "feat(gateway): 添加 JWT 验签 GlobalFilter + Sentinel 嵌入侵入配置"
```

---

### Task 14: Fix GlobalExceptionHandler — 区分 404 vs 500

**Files:**
- Modify: `expense-common/src/main/java/com/expenseflow/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 新增 NoResourceFoundException 处理器**

在 `GlobalExceptionHandler` 中添加（在 `handleException` 方法之前）：
```java
@ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
@ResponseStatus(HttpStatus.NOT_FOUND)
public Result<Void> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException e) {
    return Result.fail(404, "请求的资源不存在");
}
```

- [ ] **Step 2: Commit**

```bash
git add expense-common/src/main/java/com/expenseflow/common/exception/GlobalExceptionHandler.java
git commit -m "fix(common): GlobalExceptionHandler 新增 NoResourceFoundException 处理器，返回 404 而非 500"
```

---

### Task 15: Build + Start + Verify

- [ ] **Step 1: 编译**

```bash
cd D:/RecoginitionOCR && mvn clean package -DskipTests
```
Expected: BUILD SUCCESS（8 模块）

- [ ] **Step 2: 停旧进程**

```bash
for pid in $(netstat -ano | grep -E ':(8080|8081|8082|8083|8084|8085) ' | awk '{print $5}' | sort -u); do
  taskkill -PID $pid -F 2>/dev/null
done
```

- [ ] **Step 3: 启动全部服务**

```bash
nohup java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar > /tmp/gateway.log 2>&1 & disown
nohup java -jar system-service/target/system-service-1.0.0-SNAPSHOT.jar > /tmp/system.log 2>&1 & disown
nohup java -jar expense-service/target/expense-service-1.0.0-SNAPSHOT.jar > /tmp/expense.log 2>&1 & disown
nohup java -jar approval-service/target/approval-service-1.0.0-SNAPSHOT.jar > /tmp/approval.log 2>&1 & disown
nohup java -jar ai-service/target/ai-service-1.0.0-SNAPSHOT.jar > /tmp/ai.log 2>&1 & disown
nohup java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar > /tmp/notification.log 2>&1 & disown
```

等待 60 秒

- [ ] **Step 4: 验证健康检查**

```bash
for port in 8080 8081 8082 8083 8084 8085; do
  curl -s --noproxy localhost -w "Port $port: %{http_code}\n" http://localhost:$port/actuator/health
done
```
Expected: 全部返回 200

- [ ] **Step 5: 验证 Nacos 注册**

```bash
curl -s --noproxy localhost http://localhost:8848/nacos/v1/ns/service/list?pageNo=1\&pageSize=10
```
Expected: count=6

- [ ] **Step 6: 验证登录接口**

```bash
# 无Token访问 → 401
curl -s --noproxy localhost -w "HTTP %{http_code}\n" http://localhost:8080/system/user/page
# 登录
curl -s --noproxy localhost -X POST http://localhost:8081/system/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# 预期：返回 Token + 用户信息
```

- [ ] **Step 7: 验证多租户隔离**

```bash
# 携带 Token 访问 → 200（只返回当前租户数据）
curl -s --noproxy localhost -H "Authorization: Bearer <token>" \
  http://localhost:8081/system/user/page
```

- [ ] **Step 8: Commit 验证结果**

```bash
git commit --allow-empty -m "test(m2): 全部 6 服务启动验证通过 — health 200 + Nacos 6实例 + 登录/鉴权正常"
```
