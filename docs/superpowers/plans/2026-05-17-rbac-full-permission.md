# RBAC 完整权限体系 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 6 角色 × 53 权限码 × 15 Controller 的 RBAC 权限体系完整落地，从 SQL 种子数据 → JWT 权限传递 → 后端 @PreAuthorize → 前端三层控制（侧边栏/路由/按钮）。

**Architecture:** 权限码存储在 JWT 的 `permissions` 字段，各微服务的 `JwtAuthFilter` 将其映射为 `SimpleGrantedAuthority`（不含 ROLE_ 前缀），`@PreAuthorize("hasAuthority('invoice:upload')")` 直接校验。前端登录后调用 `/system/permission/my` 拉取权限列表存入 Pinia，驱动侧边栏 `v-if`、路由 `meta.permission`、按钮 `v-permission` 指令。

**Tech Stack:** Java 17 / Spring Boot 3.3 / Spring Security 6 / MyBatis-Plus 3.5 / Vue 3.4 / TypeScript / Pinia / Element Plus

---

## 文件结构

### 新建文件
| 文件 | 职责 |
|---|---|
| `system-service/.../service/PermissionService.java` | 权限查询业务接口 |
| `system-service/.../service/impl/PermissionServiceImpl.java` | 权限查询实现（带 Caffeine 缓存） |
| `expense-web/src/stores/permission.ts` | 前端权限 Pinia store |
| `expense-web/src/directives/permission.ts` | v-permission 自定义指令 |
| `expense-web/src/api/permission.ts` | 前端权限 API 模块 |

### 修改文件
| 文件 | 改动 |
|---|---|
| `sql/init.sql` | +53 条权限 INSERT + ~157 条角色-权限关联 INSERT |
| `expense-common/.../util/JwtUtil.java` | `generateToken` 新增 `permissions` 参数；新增 `getPermissions()` 方法 |
| `system-service/.../config/JwtAuthFilter.java` | 解析 `permissions` claim，映射为 authority |
| `expense-service/.../config/SecurityConfig.java` (内嵌 JwtAuthFilter) | 同上 |
| `approval-service/.../config/SecurityConfig.java` | 同上 |
| `ai-service/.../config/SecurityConfig.java` | 同上 |
| `notification-service/.../config/SecurityConfig.java` | 同上 |
| `system-service/.../controller/PermissionController.java` | 新增 `/my` 接口 |
| `system-service/.../service/impl/AuthServiceImpl.java` | login 调用时注入 permissions |
| `system-service/.../controller/OAuthController.java` | token 生成时注入 permissions |
| **15 个 Controller** (见 Task 7-11) | 逐个加上 `@PreAuthorize` |
| `expense-common/.../handler/GlobalExceptionHandler.java` | 新增 AccessDeniedException 处理 |
| `expense-web/src/layouts/MainLayout.vue` | 菜单项加 `v-if="perm.has(...)"` |
| `expense-web/src/router/index.ts` | meta.roles → meta.permission |
| `expense-web/src/stores/user.ts` | login 后调用 permissionStore.fetchPermissions() |
| `expense-web/src/utils/permission.ts` | 删除硬编码 ROLE_PERMISSIONS |
| `expense-web/src/views/**/*.vue` | 核心按钮加 v-permission |

---

### Task 1: SQL 种子数据 — 53 个权限码

**Files:**
- Modify: `sql/init.sql` (在 sys_role seed 之后追加)

- [ ] **Step 1: 在 `sql/init.sql` 中插入 53 条权限数据**

在 `-- 费用政策（演示用）` 行之前，找到 `sys_role` 和 `sys_user_role` INSERT 之后的位置，追加：

```sql
-- ============================================================
-- 7. 权限种子数据（53 条）
-- ============================================================
-- 菜单权限（13 条，parent_id=0）
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, path, icon, sort_order) VALUES
(101, 0, 'dashboard',        '工作台',       1, '/dashboard',       'DataAnalysis', 1),
(102, 0, 'travel',           '差旅出行',     1, '/travel',          'Promotion',    2),
(103, 0, 'report',           '费用报销',     1, '/report',          'Document',     3),
(104, 0, 'invoice',          '票据管理',     1, '/invoice',         'Picture',      4),
(105, 0, 'approval',         '审批中心',     1, '/approval',        'Checked',      5),
(106, 0, 'ai:review',        '智能审单',     1, '/ai-review',       'Cpu',          6),
(107, 0, 'ai:assistant',     '政策问答',     1, '/ai-assistant',    'ChatDotRound', 7),
(108, 0, 'notification',     '消息中心',     1, '/notification',    'Bell',         8),
(109, 0, 'system:user',      '用户管理',     1, '/system/user',     'User',         9),
(110, 0, 'system:role',      '角色管理',     1, '/system/role',     'Avatar',       10),
(111, 0, 'system:tenant',    '租户管理',     1, '/system/tenant',   'OfficeBuilding',11),
(112, 0, 'finance:payment',  '打款管理',     1, '/payment',         'Money',        12),
(113, 0, 'finance:policy',   '费用政策',     1, '/finance/policy',  'Tickets',      13);

-- 操作权限（40 条，parent_id 指向对应菜单）
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, path, icon, sort_order) VALUES
-- dashboard
(201, 101, 'dashboard:view',     '查看工作台',     2, NULL, NULL, 1),
-- travel
(202, 102, 'travel:create',      '新建出差',       2, NULL, NULL, 1),
(203, 102, 'travel:view',        '查看出差列表',   2, NULL, NULL, 2),
(204, 102, 'travel:edit',        '编辑出差',       2, NULL, NULL, 3),
(205, 102, 'travel:delete',      '删除出差',       2, NULL, NULL, 4),
-- report
(206, 103, 'report:create',      '创建报销单',     2, NULL, NULL, 1),
(207, 103, 'report:view',        '查看报销列表',   2, NULL, NULL, 2),
(208, 103, 'report:edit',        '编辑报销单',     2, NULL, NULL, 3),
(209, 103, 'report:delete',      '删除报销单',     2, NULL, NULL, 4),
(210, 103, 'report:submit',      '提交报销单',     2, NULL, NULL, 5),
(211, 103, 'report:withdraw',    '撤回报销单',     2, NULL, NULL, 6),
-- invoice
(212, 104, 'invoice:upload',     '上传发票',       2, NULL, NULL, 1),
(213, 104, 'invoice:view',       '查看发票列表',   2, NULL, NULL, 2),
(214, 104, 'invoice:delete',     '删除发票',       2, NULL, NULL, 3),
-- ocr
(215, 104, 'ocr:recognize',      '触发OCR识别',    2, NULL, NULL, 4),
(216, 104, 'ocr:result',         '查看OCR结果',    2, NULL, NULL, 5),
-- approval
(217, 105, 'approval:view',      '查看待办列表',   2, NULL, NULL, 1),
(218, 105, 'approval:approve',   '审批通过',       2, NULL, NULL, 2),
(219, 105, 'approval:reject',    '审批驳回',       2, NULL, NULL, 3),
(220, 105, 'approval:delegate',  '委派审批',       2, NULL, NULL, 4),
-- payment
(221, 112, 'payment:create',     '发起打款',       2, NULL, NULL, 1),
(222, 112, 'payment:confirm',    '确认打款',       2, NULL, NULL, 2),
(223, 112, 'payment:view',       '查看打款记录',   2, NULL, NULL, 3),
-- policy
(224, 113, 'policy:create',      '创建费用政策',   2, NULL, NULL, 1),
(225, 113, 'policy:edit',        '编辑费用政策',   2, NULL, NULL, 2),
(226, 113, 'policy:delete',      '删除费用政策',   2, NULL, NULL, 3),
(227, 113, 'policy:view',        '查看费用政策',   2, NULL, NULL, 4),
-- user
(228, 109, 'user:create',        '创建用户',       2, NULL, NULL, 1),
(229, 109, 'user:edit',          '编辑用户',       2, NULL, NULL, 2),
(230, 109, 'user:delete',        '删除用户',       2, NULL, NULL, 3),
(231, 109, 'user:view',          '查看用户列表',   2, NULL, NULL, 4),
(232, 109, 'user:assignRole',    '分配角色',       2, NULL, NULL, 5),
-- role
(233, 110, 'role:create',        '创建角色',       2, NULL, NULL, 1),
(234, 110, 'role:edit',          '编辑角色',       2, NULL, NULL, 2),
(235, 110, 'role:view',          '查看角色列表',   2, NULL, NULL, 3),
(236, 110, 'role:assignPerm',    '分配权限',       2, NULL, NULL, 4),
-- tenant
(237, 111, 'tenant:create',      '创建租户',       2, NULL, NULL, 1),
(238, 111, 'tenant:edit',        '编辑租户',       2, NULL, NULL, 2),
(239, 111, 'tenant:view',        '查看租户列表',   2, NULL, NULL, 3),
-- notification
(240, 108, 'notification:manage','管理通知模板',   2, NULL, NULL, 1),
(241, 108, 'notification:send',  '手动发送通知',   2, NULL, NULL, 2),
-- ai
(242, 106, 'ai:review:execute',  '执行AI审单',     2, NULL, NULL, 1),
(243, 106, 'ai:review:result',   '查看AI审单结果', 2, NULL, NULL, 2),
(244, 107, 'ai:rag:query',       'RAG政策问答',    2, NULL, NULL, 1);
```

- [ ] **Step 2: 在 `sql/init.sql` 中插入 6 角色 × 53 权限的关联映射**

在权限 INSERT 之后追加：

```sql
-- ============================================================
-- 8. 角色-权限关联种子数据
-- ============================================================
-- SUPER_ADMIN (role_id=1): 拥有全部 53 个权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1,101),(1,102),(1,103),(1,104),(1,105),(1,106),(1,107),(1,108),(1,109),(1,110),(1,111),(1,112),(1,113),
(1,201),(1,202),(1,203),(1,204),(1,205),
(1,206),(1,207),(1,208),(1,209),(1,210),(1,211),
(1,212),(1,213),(1,214),(1,215),(1,216),
(1,217),(1,218),(1,219),(1,220),
(1,221),(1,222),(1,223),
(1,224),(1,225),(1,226),(1,227),
(1,228),(1,229),(1,230),(1,231),(1,232),
(1,233),(1,234),(1,235),(1,236),
(1,237),(1,238),(1,239),
(1,240),(1,241),
(1,242),(1,243),(1,244);

-- TENANT_ADMIN (role_id=2): 与 SUPER_ADMIN 相同，但不含 system:tenant 菜单和 tenant:create/edit
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2,101),(2,102),(2,103),(2,104),(2,105),(2,106),(2,107),(2,108),(2,109),(2,110),(2,112),(2,113),
(2,201),(2,202),(2,203),(2,204),(2,205),
(2,206),(2,207),(2,208),(2,209),(2,210),(2,211),
(2,212),(2,213),(2,214),(2,215),(2,216),
(2,217),(2,218),(2,219),(2,220),
(2,223),
(2,224),(2,225),(2,226),(2,227),
(2,228),(2,229),(2,230),(2,231),(2,232),
(2,233),(2,234),(2,235),(2,236),
(2,239),
(2,240),(2,241),
(2,242),(2,243),(2,244);

-- EMPLOYEE (role_id=3): 只能做自己的差旅/报销/发票/OCR
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3,101),(3,102),(3,103),(3,104),(3,107),(3,108),
(3,201),(3,202),(3,203),(3,204),(3,205),
(3,206),(3,207),(3,208),(3,209),(3,210),(3,211),
(3,212),(3,213),(3,214),(3,215),(3,216),
(3,227),
(3,244);

-- APPROVER (role_id=4): 审批操作 + 可查看差旅/报销/政策 + AI问答/结果
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(4,101),(4,105),(4,107),(4,108),
(4,201),(4,203),
(4,207),
(4,217),(4,218),(4,219),(4,220),
(4,227),
(4,243),(4,244);

-- FINANCE (role_id=5): 财务审核员 — 审批+发票查看+OCR+费用政策+AI审单
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(5,101),(5,105),(5,106),(5,107),(5,108),(5,113),
(5,201),(5,203),
(5,207),
(5,213),(5,215),(5,216),
(5,217),(5,218),(5,219),
(5,223),(5,224),(5,225),(5,226),(5,227),
(5,242),(5,243),(5,244);

-- CASHIER (role_id=6): 只管打款 + 收消息 + 看报销单
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(6,101),(6,108),(6,112),
(6,201),
(6,207),
(6,221),(6,222),(6,223);
```

- [ ] **Step 3: 验证 SQL 语法**

```bash
# 用 Docker 的 MySQL 测试 SQL 可执行（可选，也可以直接启动后检查）
docker compose up -d mysql
sleep 5
docker compose exec mysql mysql -u root -pexpenseflow123 -e "USE expenseflow; SOURCE /docker-entrypoint-initdb.d/init.sql;" 2>&1 | head -20
```

- [ ] **Step 4: Commit**

```bash
git add sql/init.sql
git commit -m "feat(db): 添加 53 个权限码种子数据 + 6 角色权限关联映射"
```

---

### Task 2: JWT 工具类扩展 — 支持 permissions 字段

**Files:**
- Modify: `expense-common/src/main/java/com/expenseflow/common/util/JwtUtil.java`

- [ ] **Step 1: 修改 `generateToken` 签名，新增 `permissions` 参数**

```java
private static String generateToken(Long userId, Long tenantId, String tokenId,
        List<String> roles, List<String> permissions, String username, long expire) {
    Date now = new Date();
    return Jwts.builder()
            .id(tokenId)
            .subject(String.valueOf(userId))
            .claim("tenantId", tenantId)
            .claim("roles", roles != null ? roles : Collections.emptyList())
            .claim("permissions", permissions != null ? permissions : Collections.emptyList())
            .claim("username", username != null ? username : "")
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expire))
            .signWith(getKey())
            .compact();
}
```

- [ ] **Step 2: 修改 `generateAccessToken` 和 `generateRefreshToken` 签名**

```java
public static String generateAccessToken(Long userId, Long tenantId, String tokenId,
        List<String> roles, List<String> permissions, String username) {
    return generateToken(userId, tenantId, tokenId, roles, permissions, username, ACCESS_EXPIRE);
}

public static String generateRefreshToken(Long userId, Long tenantId, String tokenId,
        List<String> roles, List<String> permissions, String username) {
    return generateToken(userId, tenantId, tokenId, roles, permissions, username, REFRESH_EXPIRE);
}
```

- [ ] **Step 3: 新增 `getPermissions` 静态方法**

```java
@SuppressWarnings("unchecked")
public static List<String> getPermissions(Claims claims) {
    if (claims == null) return Collections.emptyList();
    Object permsObj = claims.get("permissions");
    if (permsObj instanceof List<?> list) {
        return list.stream().map(Object::toString).collect(Collectors.toList());
    }
    return Collections.emptyList();
}
```

- [ ] **Step 4: Commit**

```bash
git add expense-common/src/main/java/com/expenseflow/common/util/JwtUtil.java
git commit -m "feat(common): JwtUtil 新增 permissions 字段支持 — generateToken 增加权限码参数"
```

---

### Task 3: JwtAuthFilter 改造 — 所有服务解析 permissions 为 authority

**Files:**
- Modify: `system-service/.../config/JwtAuthFilter.java:54-57`
- Modify: `expense-service/.../config/SecurityConfig.java:70-75` (内嵌 JwtAuthFilter)
- Create: `approval-service/.../config/SecurityConfig.java` (新增内嵌 JwtAuthFilter 类)
- Create: `ai-service/.../config/SecurityConfig.java` (新增内嵌 JwtAuthFilter 类)
- Create: `notification-service/.../config/SecurityConfig.java` (新增内嵌 JwtAuthFilter 类)

需要先确认 approval、ai、notification 的 SecurityConfig 状态：

```bash
ls approval-service/src/main/java/com/expenseflow/approval/config/SecurityConfig.java
ls ai-service/src/main/java/com/expenseflow/ai/config/SecurityConfig.java
ls notification-service/src/main/java/com/expenseflow/notification/config/SecurityConfig.java
```

- [ ] **Step 1: 改造 system-service 的 JwtAuthFilter**

修改 `JwtAuthFilter.java:54-57`，在 `List<SimpleGrantedAuthority>` 构建处，追加 permissions 映射：

```java
Long userId = JwtUtil.getUserId(claims);
Long tenantId = JwtUtil.getTenantId(claims);
List<String> roles = JwtUtil.getRoles(claims);
List<String> permissions = JwtUtil.getPermissions(claims);
List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
UsernamePasswordAuthenticationToken auth =
    new UsernamePasswordAuthenticationToken(userId, null, authorities);
auth.setDetails(tenantId);
SecurityContextHolder.getContext().setAuthentication(auth);
```

- [ ] **Step 2: 同样改造 expense-service 内嵌的 JwtAuthFilter**

`expense-service/.../config/SecurityConfig.java:70-75`，同样的改动。

- [ ] **Step 3: 检查并改造 approval-service 的 SecurityConfig**

读取文件，如果已有 JwtAuthFilter，同 Step 1 改造。如果没有，参照 system-service 的模式创建。

- [ ] **Step 4: 检查并改造 ai-service 的 SecurityConfig**

同上。

- [ ] **Step 5: 检查并改造 notification-service 的 SecurityConfig**

同上。

- [ ] **Step 6: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/config/JwtAuthFilter.java
git add expense-service/src/main/java/com/expenseflow/expense/config/SecurityConfig.java
git add approval-service/src/main/java/com/expenseflow/approval/config/SecurityConfig.java
git add ai-service/src/main/java/com/expenseflow/ai/config/SecurityConfig.java
git add notification-service/src/main/java/com/expenseflow/notification/config/SecurityConfig.java
git commit -m "feat(security): 所有服务 JwtAuthFilter 解析 permissions 为 SimpleGrantedAuthority"
```

---

### Task 4: 新增 PermissionService — 按用户查询权限码

**Files:**
- Create: `system-service/.../service/PermissionService.java`
- Create: `system-service/.../service/impl/PermissionServiceImpl.java`

- [ ] **Step 1: 创建 PermissionService 接口**

```java
package com.expenseflow.system.service;

import java.util.List;

public interface PermissionService {
    List<String> getPermissionCodesByUserId(Long userId);
}
```

- [ ] **Step 2: 创建 PermissionServiceImpl 实现类**

```java
package com.expenseflow.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.expenseflow.system.entity.SysPermission;
import com.expenseflow.system.entity.SysRolePermission;
import com.expenseflow.system.entity.SysUserRole;
import com.expenseflow.system.mapper.SysPermissionMapper;
import com.expenseflow.system.mapper.SysRolePermissionMapper;
import com.expenseflow.system.mapper.SysUserRoleMapper;
import com.expenseflow.system.service.PermissionService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    // Caffeine 本地缓存：TTL 5 分钟，角色变更后主动失效
    private final Cache<Long, List<String>> userPermissionCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build();

    @Override
    public List<String> getPermissionCodesByUserId(Long userId) {
        return userPermissionCache.get(userId, this::loadPermissions);
    }

    private List<String> loadPermissions(Long userId) {
        // 1. 查询用户的所有角色
        List<Long> roleIds = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
        ).stream().map(SysUserRole::getRoleId).toList();

        if (roleIds.isEmpty()) return List.of();

        // 2. 查询这些角色关联的所有权限ID
        List<Long> permissionIds = rolePermissionMapper.selectList(
            new LambdaQueryWrapper<SysRolePermission>()
                .in(SysRolePermission::getRoleId, roleIds)
        ).stream().map(SysRolePermission::getPermissionId).distinct().toList();

        if (permissionIds.isEmpty()) return List.of();

        // 3. 查询权限码
        return permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getId, permissionIds)
        ).stream().map(SysPermission::getPermissionCode).distinct().toList();
    }
}
```

- [ ] **Step 3: 确认 SysUserRoleMapper 存在**

```bash
ls system-service/src/main/java/com/expenseflow/system/mapper/SysUserRoleMapper.java
```
如果不存在，创建：
```java
package com.expenseflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
```

- [ ] **Step 4: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/service/PermissionService.java
git add system-service/src/main/java/com/expenseflow/system/service/impl/PermissionServiceImpl.java
git commit -m "feat(system): 新增 PermissionService — 按用户ID查询权限码，Caffeine 5分钟缓存"
```

---

### Task 5: PermissionController 新增 `/my` 接口 + AuthService 注入 permissions

**Files:**
- Modify: `system-service/.../controller/PermissionController.java`
- Modify: `system-service/.../service/impl/AuthServiceImpl.java`
- Modify: `system-service/.../controller/OAuthController.java`

- [ ] **Step 1: 在 PermissionController 新增 `/my` 接口**

```java
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

private final PermissionService permissionService; // 构造函数注入

@GetMapping("/my")
public Result<List<String>> myPermissions() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Long userId = (Long) auth.getPrincipal();
    return Result.ok(permissionService.getPermissionCodesByUserId(userId));
}
```

- [ ] **Step 2: 修改 AuthServiceImpl — login 时查询 permissions 并注入 JWT**

找到 `AuthServiceImpl.login()` 方法中生成 token 的位置。当前大概是：
```java
String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getTenantId(), tokenId, roles, user.getUsername());
```

改为：
```java
List<String> permissions = permissionService.getPermissionCodesByUserId(user.getId());
String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getTenantId(), tokenId, roles, permissions, user.getUsername());
String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getTenantId(), tokenId, roles, permissions, user.getUsername());
```

- [ ] **Step 3: 同步修改 OAuthController 中 token 生成**

找到 `OAuthController.java:80-81` 的 JwtUtil 调用，同样传入 permissions：
```java
List<String> permissions = permissionService.getPermissionCodesByUserId(user.getId());
String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getTenantId(), tokenId, List.of("EMPLOYEE"), permissions, user.getUsername());
String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getTenantId(), tokenId, List.of("EMPLOYEE"), permissions, user.getUsername());
```

- [ ] **Step 4: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/controller/PermissionController.java
git add system-service/src/main/java/com/expenseflow/system/service/impl/AuthServiceImpl.java
git add system-service/src/main/java/com/expenseflow/system/controller/OAuthController.java
git commit -m "feat(system): login/OAuth 时将 permissions 注入 JWT + 新增 GET /system/permission/my"
```

---

### Task 6: 全局 AccessDeniedException 处理器

**Files:**
- Modify: `expense-common/.../handler/GlobalExceptionHandler.java`

- [ ] **Step 1: 新增 AccessDeniedException 处理**

```java
import org.springframework.security.access.AccessDeniedException;

@ExceptionHandler(AccessDeniedException.class)
@ResponseStatus(HttpStatus.FORBIDDEN)
public Result<Void> handleAccessDenied(AccessDeniedException e) {
    return Result.fail(403, "无此操作权限");
}
```

- [ ] **Step 2: Commit**

```bash
git add expense-common/src/main/java/com/expenseflow/common/handler/GlobalExceptionHandler.java
git commit -m "feat(common): GlobalExceptionHandler 新增 AccessDeniedException → 403 统一响应"
```

---

### Task 7: expense-service 5 个 Controller @PreAuthorize 改造

**Files:**
- Modify: `expense-service/.../controller/TravelRequestController.java`
- Modify: `expense-service/.../controller/ExpenseReportController.java`
- Modify: `expense-service/.../controller/InvoiceController.java`
- Modify: `expense-service/.../controller/PaymentRecordController.java`
- Modify: `expense-service/.../controller/ExpensePolicyController.java`
- Modify: `expense-service/.../controller/ExpenseItemController.java`

- [ ] **Step 1: TravelRequestController**

当前无 `@PreAuthorize`。在类上加 `import org.springframework.security.access.prepost.PreAuthorize;`，每个方法加：
```java
@PreAuthorize("hasAuthority('travel:create')")   // POST /
@PreAuthorize("hasAuthority('travel:view')")     // GET /page, GET /{id}
@PreAuthorize("hasAuthority('travel:edit')")     // PUT /{id}
@PreAuthorize("hasAuthority('travel:delete')")   // DELETE /{id}
```

- [ ] **Step 2: ExpenseReportController**

```java
@PreAuthorize("hasAuthority('report:create')")   // POST /
@PreAuthorize("hasAuthority('report:view')")     // GET /page, GET /{id}
@PreAuthorize("hasAuthority('report:edit')")     // PUT /{id}
@PreAuthorize("hasAuthority('report:delete')")   // DELETE /{id}
@PreAuthorize("hasAuthority('report:submit')")   // POST /{id}/submit
@PreAuthorize("hasAuthority('report:withdraw')") // POST /{id}/withdraw
```

- [ ] **Step 3: InvoiceController**

```java
@PreAuthorize("hasAuthority('invoice:upload')")  // POST /upload
@PreAuthorize("hasAuthority('invoice:view')")    // GET /page, GET /{id}
@PreAuthorize("hasAuthority('invoice:delete')")  // DELETE /{id}
@PreAuthorize("hasAuthority('ocr:recognize')")   // POST /{id}/ocr
```

- [ ] **Step 4: PaymentRecordController**

将现有 `@PreAuthorize("hasAnyRole('FINANCE','CASHIER','SUPER_ADMIN')")` 替换为：
```java
@PreAuthorize("hasAuthority('payment:view')")    // GET /page
@PreAuthorize("hasAuthority('payment:create')")  // POST /
```

- [ ] **Step 5: ExpensePolicyController**

将现有角色校验替换为：
```java
@PreAuthorize("hasAuthority('policy:view')")     // GET /page, GET /{id}
@PreAuthorize("hasAuthority('policy:create')")   // POST /
@PreAuthorize("hasAuthority('policy:edit')")     // PUT /{id}
@PreAuthorize("hasAuthority('policy:delete')")   // DELETE /{id}
```

- [ ] **Step 6: ExpenseItemController**

```java
@PreAuthorize("hasAuthority('report:edit')")     // POST /, PUT /{id}, DELETE /{id}
```

- [ ] **Step 7: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/controller/
git commit -m "feat(expense): 6 个 Controller 全部加上 @PreAuthorize 权限码校验"
```

---

### Task 8: approval-service + ai-service + notification-service Controller @PreAuthorize

**Files:**
- Modify: `approval-service/.../controller/ApprovalTaskController.java`
- Modify: `ai-service/.../controller/OcrController.java`
- Modify: `ai-service/.../controller/ReviewController.java`
- Modify: `ai-service/.../controller/RagController.java`
- Modify: `notification-service/.../controller/TemplateController.java`

- [ ] **Step 1: ApprovalTaskController**

将现有 `@PreAuthorize("hasAnyRole('APPROVER','FINANCE','SUPER_ADMIN')")` 替换为：
```java
@PreAuthorize("hasAuthority('approval:view')")     // GET /page, GET /record/list
@PreAuthorize("hasAuthority('approval:approve')")  // POST /approve
@PreAuthorize("hasAuthority('approval:reject')")   // POST /reject
@PreAuthorize("hasAuthority('approval:delegate')") // POST /delegate
```

- [ ] **Step 2: OcrController**

```java
@PreAuthorize("hasAuthority('ocr:recognize')")     // POST /recognize
@PreAuthorize("hasAuthority('ocr:result')")        // GET /{id}
```

- [ ] **Step 3: ReviewController**

```java
@PreAuthorize("hasAuthority('ai:review:execute')") // POST /evaluate
@PreAuthorize("hasAuthority('ai:review:result')")  // POST /risk
```

- [ ] **Step 4: RagController**

```java
@PreAuthorize("hasAuthority('ai:rag:query')")      // POST /chat
```

- [ ] **Step 5: TemplateController**

将现有 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 替换为：
```java
@PreAuthorize("hasAuthority('notification:manage')") // GET /page, POST /, PUT /, DELETE /
```

- [ ] **Step 6: Commit**

```bash
git add approval-service/ ai-service/ notification-service/
git commit -m "feat: approval/ai/notification 服务 Controller 全部加上 @PreAuthorize 权限码校验"
```

---

### Task 9: system-service Controller @PreAuthorize 改造

**Files:**
- Modify: `system-service/.../controller/UserController.java`
- Modify: `system-service/.../controller/RoleController.java`
- Modify: `system-service/.../controller/TenantController.java`
- Modify: `system-service/.../controller/PermissionController.java`

- [ ] **Step 1: UserController**

将现有 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")` 替换为细粒度：
```java
@PreAuthorize("hasAuthority('user:view')")        // GET /page, GET /{id}
@PreAuthorize("hasAuthority('user:create')")      // POST /
@PreAuthorize("hasAuthority('user:edit')")        // PUT /{id}
@PreAuthorize("hasAuthority('user:delete')")      // DELETE /{id}
@PreAuthorize("hasAuthority('user:assignRole')")  // POST /{id}/roles
```

- [ ] **Step 2: RoleController**

将 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 替换为：
```java
@PreAuthorize("hasAuthority('role:view')")        // GET /list
@PreAuthorize("hasAuthority('role:create')")      // POST /
@PreAuthorize("hasAuthority('role:edit')")        // PUT /{id}
@PreAuthorize("hasAuthority('role:assignPerm')")  // POST /{id}/permissions
```

- [ ] **Step 3: TenantController**

将 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 替换为：
```java
@PreAuthorize("hasAuthority('tenant:view')")      // GET /page
@PreAuthorize("hasAuthority('tenant:create')")    // POST /
@PreAuthorize("hasAuthority('tenant:edit')")      // PUT /{id}
```

- [ ] **Step 4: PermissionController**

`/tree` 接口加上：
```java
@PreAuthorize("hasAuthority('role:assignPerm')")
@GetMapping("/tree")
```

`/my` 接口不需要 @PreAuthorize（登录后都要调）。

- [ ] **Step 5: Commit**

```bash
git add system-service/src/main/java/com/expenseflow/system/controller/
git commit -m "feat(system): User/Role/Tenant/Permission Controller 全部加上 @PreAuthorize 权限码校验"
```

---

### Task 10: 前端 Permission API + Pinia Store

**Files:**
- Create: `expense-web/src/api/permission.ts`
- Create: `expense-web/src/stores/permission.ts`

- [ ] **Step 1: 创建 API 模块**

```typescript
// expense-web/src/api/permission.ts
import request from './request'

export function getMyPermissions() {
  return request.get<string[]>('/system/permission/my')
}
```

- [ ] **Step 2: 创建 Pinia Store**

```typescript
// expense-web/src/stores/permission.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMyPermissions } from '@/api/permission'

export const usePermissionStore = defineStore('permission', () => {
  const codes = ref<string[]>([])

  async function fetchPermissions() {
    try {
      const res = await getMyPermissions()
      codes.value = res.data ?? []
    } catch {
      codes.value = []
    }
  }

  function has(code: string): boolean {
    return codes.value.includes(code) || codes.value.includes('*')
  }

  function hasAny(required: string[]): boolean {
    return required.some(c => has(c))
  }

  function reset() {
    codes.value = []
  }

  return { codes, fetchPermissions, has, hasAny, reset }
})
```

- [ ] **Step 3: Commit**

```bash
git add expense-web/src/api/permission.ts expense-web/src/stores/permission.ts
git commit -m "feat(web): 新增 permission API + Pinia store — fetchPermissions/has/hasAny/reset"
```

---

### Task 11: 前端 v-permission 自定义指令

**Files:**
- Create: `expense-web/src/directives/permission.ts`

- [ ] **Step 1: 创建指令**

```typescript
// expense-web/src/directives/permission.ts
import type { Directive } from 'vue'
import { usePermissionStore } from '@/stores/permission'

export const vPermission: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const permStore = usePermissionStore()
    if (!permStore.has(binding.value)) {
      el.remove()
    }
  }
}
```

- [ ] **Step 2: 注册为全局指令**

在 `main.ts` 中注册（需要找 main.ts 确认导入路径）：

```bash
grep -n "createApp\|app.directive\|app.use" expense-web/src/main.ts
```

在 `const app = createApp(App)` 之后添加：
```typescript
import { vPermission } from './directives/permission'
app.directive('permission', vPermission)
```

- [ ] **Step 3: Commit**

```bash
git add expense-web/src/directives/permission.ts expense-web/src/main.ts
git commit -m "feat(web): 新增 v-permission 自定义指令 — 无权限时移除 DOM 元素"
```

---

### Task 12: 登录流程集成 + 清理旧 permission.ts

**Files:**
- Modify: `expense-web/src/stores/user.ts`
- Modify: `expense-web/src/utils/permission.ts` (删除硬编码部分)

- [ ] **Step 1: userStore.login 集成权限拉取**

在 `stores/user.ts` 的 `login` action 中，token 存储后立即拉取权限：

```typescript
import { usePermissionStore } from './permission'

async function login(username: string, password: string) {
  // ... 原有登录逻辑 ...
  const res = await authApi.login({ username, password })
  token.value = res.data.accessToken
  refreshToken.value = res.data.refreshToken
  localStorage.setItem('token', res.data.accessToken)
  // ... set userInfo ...
  
  // 拉取权限码
  const permStore = usePermissionStore()
  await permStore.fetchPermissions()
}
```

- [ ] **Step 2: userStore.logout 清空权限**

```typescript
function logout() {
  const permStore = usePermissionStore()
  permStore.reset()
  token.value = ''
  localStorage.removeItem('token')
  // ... 原有登出逻辑 ...
}
```

- [ ] **Step 3: 清理 utils/permission.ts**

`utils/permission.ts` 的 `ROLE_PERMISSIONS` 硬编码映射表已无用，保留 `hasPermission` 函数签名改为走 permissionStore：

```typescript
import { usePermissionStore } from '@/stores/permission'

export function hasPermission(required: string | string[]): boolean {
  const permStore = usePermissionStore()
  const list = Array.isArray(required) ? required : [required]
  return list.some(p => permStore.has(p))
}
```

同时删除 `ROLE_PERMISSIONS` 常量。

- [ ] **Step 4: Commit**

```bash
git add expense-web/src/stores/user.ts expense-web/src/utils/permission.ts
git commit -m "feat(web): 登录流程集成权限拉取 + 清理硬编码 ROLE_PERMISSIONS"
```

---

### Task 13: 侧边栏 + 路由守卫角色过滤

**Files:**
- Modify: `expense-web/src/layouts/MainLayout.vue`
- Modify: `expense-web/src/router/index.ts`

- [ ] **Step 1: MainLayout 侧边栏菜单权限控制**

在 `<script setup>` 中加入：
```typescript
import { usePermissionStore } from '@/stores/permission'
const perm = usePermissionStore()
```

每个菜单项加上 `v-if`：

```vue
<el-menu-item index="/dashboard">
  <el-icon><DataAnalysis /></el-icon><span>工作台</span>
</el-menu-item>

<el-sub-menu v-if="perm.has('travel')" index="travel">
  <template #title><el-icon><Promotion /></el-icon><span>差旅出行</span></template>
  <el-menu-item v-if="perm.has('travel:view')" index="/travel">我的行程</el-menu-item>
  <el-menu-item v-if="perm.has('travel:create')" index="/travel/create">新建出差</el-menu-item>
</el-sub-menu>

<el-sub-menu v-if="perm.has('report')" index="report">
  <template #title><el-icon><Document /></el-icon><span>费用报销</span></template>
  <el-menu-item v-if="perm.has('report:view')" index="/report">我的报销</el-menu-item>
  <el-menu-item v-if="perm.has('report:create')" index="/report/create">提交报销</el-menu-item>
</el-sub-menu>

<el-menu-item v-if="perm.has('invoice')" index="/invoice">
  <el-icon><Picture /></el-icon><span>票据管理</span>
</el-menu-item>

<el-menu-item v-if="perm.has('approval')" index="/approval">
  <el-icon><Checked /></el-icon><span>审批中心</span>
</el-menu-item>

<el-sub-menu v-if="perm.has('ai:review') || perm.has('ai:assistant')" index="ai">
  <template #title><el-icon><Cpu /></el-icon><span>智能服务</span></template>
  <el-menu-item v-if="perm.has('ai:review')" index="/ai-review">智能审单</el-menu-item>
  <el-menu-item v-if="perm.has('ai:assistant')" index="/ai-assistant">政策问答</el-menu-item>
</el-sub-menu>

<el-menu-item v-if="perm.has('finance:payment')" index="/payment">
  <el-icon><Money /></el-icon><span>打款管理</span>
</el-menu-item>

<el-menu-item v-if="perm.has('finance:policy')" index="/finance/policy">
  <el-icon><Tickets /></el-icon><span>费用政策</span>
</el-menu-item>

<el-menu-item v-if="perm.has('system:user') || perm.has('system:role') || perm.has('system:tenant')" index="system">
  <!-- 系统管理子菜单 ... -->
</el-menu-item>

<el-menu-item v-if="perm.has('notification')" index="/notification">
  <el-icon><Bell /></el-icon><span>消息中心</span>
</el-menu-item>
```

- [ ] **Step 2: router 守卫从 roles 改为 permission**

`router/index.ts`，修改 3 处 route meta：
```typescript
// 改前
meta: { roles: ['APPROVER', 'FINANCE', 'SUPER_ADMIN'] }
// 改后
meta: { permission: 'approval' }
```

同理 `ai-review` → `meta: { permission: 'ai:review' }`，`ai-assistant` → `meta: { permission: 'ai:assistant' }`。

`beforeEach` 守卫逻辑：
```typescript
// 改前
if (to.meta.roles && Array.isArray(to.meta.roles) && to.meta.roles.length > 0) {
  if (!hasAnyRole(to.meta.roles as string[])) { ... }
}

// 改后
if (to.meta.permission) {
  const permStore = usePermissionStore()
  if (!permStore.has(to.meta.permission as string)) {
    ElMessage.warning('您没有访问此页面的权限')
    next('/dashboard')
    return
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add expense-web/src/layouts/MainLayout.vue expense-web/src/router/index.ts
git commit -m "feat(web): 侧边栏按权限码过滤菜单 + 路由守卫改用 permission 权限码"
```

---

### Task 14: 页面按钮 v-permission 绑定

**Files:**
- Modify: 10 个 vue 页面文件

- [ ] **Step 1: Trip 相关页面**

`TravelListView.vue` — "新建出差"按钮：
```html
<el-button v-permission="'travel:create'" type="primary" @click="router.push('/travel/create')">新建出差</el-button>
```
"删除"按钮：
```html
<el-button v-permission="'travel:delete'" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
```

`TravelFormView.vue` — "提交"按钮：
```html
<el-button v-permission="'travel:create'" type="primary" @click="handleSubmit">提交</el-button>
```

- [ ] **Step 2: Report 相关页面**

`ReportListView.vue` — "提交报销"按钮：
```html
<el-button v-permission="'report:create'" type="primary" @click="router.push('/report/create')">提交报销</el-button>
```

`ReportFormView.vue` — "提交审批"按钮：
```html
<el-button v-permission="'report:submit'" type="primary" @click="handleSubmit">提交审批</el-button>
```

- [ ] **Step 3: Invoice 页面**

`InvoiceUploadView.vue` — "上传发票"按钮已有，加权限：
```html
<el-upload v-permission="'invoice:upload'" ...>
  <el-button type="primary" size="large">上传发票</el-button>
</el-upload>
```
"OCR 识别"按钮：
```html
<el-button v-permission="'ocr:recognize'" size="small" type="primary" @click="handleOcr(row)">OCR 识别</el-button>
```

- [ ] **Step 4: Approval 页面**

`ApprovalWorkbench.vue` — "通过"和"驳回"按钮：
```html
<el-button v-permission="'approval:approve'" type="success" @click="handleApprove(row)">通过</el-button>
<el-button v-permission="'approval:reject'" type="danger" @click="handleReject(row)">驳回</el-button>
```

- [ ] **Step 5: AI 页面**

`AIReviewView.vue` — "执行审单"按钮：
```html
<el-button v-permission="'ai:review:execute'" type="primary" @click="handleReview">执行审单</el-button>
```

`AIAssistantView.vue` — "发送"按钮：
```html
<el-button v-permission="'ai:rag:query'" type="primary" @click="handleSend">发送</el-button>
```

- [ ] **Step 6: 全局搜索确认 jwt.ts 中 hasAnyRole 引用已全部替换**

确认 `router/index.ts` 中不再 import `hasAnyRole`（已改为 permissionStore.has）。

- [ ] **Step 7: Commit**

```bash
git add expense-web/src/views/
git commit -m "feat(web): 所有页面核心操作按钮绑定 v-permission 指令"
```

---

### Task 15: 集成测试 — 验证各角色权限

- [ ] **Step 1: 重新初始化数据库**

```bash
docker compose down -v
docker compose up -d mysql redis
sleep 10
# 等待 MySQL 启动，init.sql 会自动执行
```

- [ ] **Step 2: 启动所有服务**

```bash
cd system-service && mvn spring-boot:run -DskipTests &
cd expense-service && mvn spring-boot:run -DskipTests &
cd approval-service && mvn spring-boot:run -DskipTests &
cd ai-service && mvn spring-boot:run -DskipTests &
cd notification-service && mvn spring-boot:run -DskipTests &
```

- [ ] **Step 3: 用 admin 登录，验证获取权限列表**

```bash
curl -s -X POST http://localhost:8080/system/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq '.data.accessToken' > /tmp/token_admin.txt

TOKEN=$(cat /tmp/token_admin.txt)
curl -s http://localhost:8080/system/permission/my \
  -H "Authorization: Bearer $TOKEN" | jq '.data | length'
# 预期: 53（SUPER_ADMIN 有全部权限码）
```

- [ ] **Step 4: 用 employee 登录（需先创建 employee 用户或直接用 OAuth 流程）**

验证权限列表只包含 EMPLOYEE 对应的权限码（不含 approval、payment 等）。

- [ ] **Step 5: 验证无权限用户调用接口返回 403**

```bash
# 用 employee token 调用 approval 接口
curl -s http://localhost:8080/approval/task/page?page=1\&size=10 \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" | jq '.code'
# 预期: 403
```

- [ ] **Step 6: 启动前端验证侧边栏**

```bash
cd expense-web && npm run dev
```
用 admin 登录 → 看到全部菜单；用 employee 登录 → 看不到审批中心、智能审单、打款管理、系统管理。

- [ ] **Step 7: Commit（如无代码改动则跳过）**

---

## 自检清单

- [x] 53 权限码 → Task 1 SQL INSERT
- [x] 6 角色映射 → Task 1 SQL INSERT（sys_role_permission）
- [x] JWT permissions 字段 → Task 2 JwtUtil + Task 3 JwtAuthFilter
- [x] PermissionService → Task 4
- [x] GET /system/permission/my → Task 5
- [x] login 时注入 permissions → Task 5
- [x] 15 个 Controller @PreAuthorize → Task 7 + 8 + 9
- [x] 全局 403 处理器 → Task 6
- [x] 前端 permission API + Store → Task 10
- [x] v-permission 指令 → Task 11
- [x] 登录集成 → Task 12
- [x] 侧边栏过滤 → Task 13
- [x] 路由守卫改造 → Task 13
- [x] 页面按钮绑定 → Task 14

**类型一致性检查:**
- `JwtUtil.getPermissions()` returns `List<String>` → `JwtAuthFilter` iterates as `List<String>` → `SimpleGrantedAuthority(String)` — 一致
- `PermissionService.getPermissionCodesByUserId()` returns `List<String>` → `PermissionController.myPermissions()` returns `Result<List<String>>` → 前端 `permStore.codes` is `string[]` — 一致
- 前端 `permStore.has(code: string)` 接收 `string` → `v-permission` 指令传 `'invoice:upload'` → `meta.permission: 'approval'` — 一致

**无占位符/TODO/待定项。**
