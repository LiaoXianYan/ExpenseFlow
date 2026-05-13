# M2 设计规格：网关安全 + 系统服务

## 概述

M2 里程碑实现 Gateway 安全层（JWT 验签 + Sentinel 限流）和 system-service 完整业务能力（租户/用户/角色/权限/部门/员工/字典/审计），同时补全多租户请求拦截、二级缓存、钉钉 OAuth2 SSO。

## 设计决策

| 决策点 | 选择 |
|--------|------|
| JWT 载荷 | 轻量：userId + tenantId + tokenId，角色/权限查 Redis |
| 钉钉登录 | 首次自动创建用户，分配"普通员工"角色 |
| Sentinel | 嵌入式模式，规则在 Nacos 配置 |

---

## 一、Gateway 安全层

### 1.1 JWT 验证过滤器

```
请求 → Gateway Filter Chain
  ├── 白名单: /auth/**, /oauth/** → 直接放行
  ├── 提取 Authorization: Bearer <token>
  ├── JJWT 解析 → userId, tenantId, tokenId
  ├── Redis 黑名单检查 (key: "token:blacklist:{tokenId}")
  ├── 写入请求头 X-User-Id, X-Tenant-Id 透传下游
  └── 失败 → 401 "未授权"
```

### 1.2 Sentinel 限流

- 嵌入 Gateway，规则配置在 Nacos `gateway-service.yaml`
- 默认：每租户每 API 100 req/s，超限返回 429 Too Many Requests
- 后续可在 Nacos 动态调整，无需重启

### 1.3 新增依赖

```xml
spring-boot-starter-data-redis-reactive
jjwt-api / jjwt-impl / jjwt-jackson 0.12.6
spring-cloud-starter-sentinel
spring-cloud-sentinel-datasource-nacos
```

---

## 二、system-service 业务模块

### 2.1 包结构

```
system-service/
├── controller/    # 8 个 Controller
│   ├── AuthController              # 登录/登出/刷新/钉钉回调
│   ├── TenantController            # 租户 CRUD
│   ├── UserController              # 用户 CRUD + 个人信息
│   ├── RoleController              # 角色 CRUD + 分配用户/权限
│   ├── PermissionController        # 权限树查询（只读）
│   ├── DepartmentController        # 部门 CRUD（树形）
│   ├── EmployeeController          # 员工（部门成员）
│   └── DictController              # 字典类型+数据 CRUD
├── service/ + impl/
├── mapper/        # MyBatis-Plus Mapper（12 张 sys_* 表）
├── entity/        # PO
├── dto/           # 入参/出参
├── vo/            # 前端展示（脱敏）
├── config/
│   ├── SecurityConfig              # Spring Security + JWT filter
│   ├── TenantContextFilter         # X-Tenant-Id → ThreadLocal
│   ├── MybatisPlusConfig           # 多租户插件 + 自动填充
│   └── CacheConfig                 # Caffeine + Redis 二级缓存
└── handler/
    └── AuditLogAspect              # @AuditLog 注解 AOP
```

### 2.2 Controller 路由

统一前缀 `/system`，Gateway StripPrefix=0 保持不变。

| Controller | 路径 | 主要接口 |
|---|---|---|
| AuthController | /system/auth | POST /login, /logout, /refresh, /me |
| TenantController | /system/tenant | CRUD + PATCH /{id}/status |
| UserController | /system/user | CRUD + PATCH /{id}/status, PATCH /{id}/password |
| RoleController | /system/role | CRUD + POST /{id}/users, POST /{id}/permissions |
| PermissionController | /system/permission | GET /tree（只读） |
| DepartmentController | /system/department | CRUD + GET /tree |
| EmployeeController | /system/employee | CRUD（部门成员管理） |
| DictController | /system/dict | 字典类型+数据 CRUD |

### 2.3 RBAC 权限模型

```
sys_user ──(N:M)── sys_role ──(N:M)── sys_permission
```

权限标识格式：`{模块}:{对象}:{操作}`，如 `system:user:create`、`expense:report:delete`。

`sys_permission` 表支持菜单/按钮/API 三级权限。

---

## 三、多租户拦截器

### 3.1 请求链路

```
浏览器 Header: X-Tenant-Id: 1
  → Gateway 透传
  → TenantContextFilter (OncePerRequestFilter)
    → ExpenseFlowTenantLineHandler.setTenant(tenantId)
    → MyBatis-Plus 自动注入 tenant_id 到所有 SQL
    → finally: clear()
```

- 职责分离：Gateway 负责 JWT 验签和透传，system-service 仅读取信任
- ExpenseFlowTenantLineHandler 已在 M1 实现，M2 只需补充 TenantContextFilter

---

## 四、OAuth2 钉钉 SSO

### 4.1 登录流程（Mock 模式）

开发阶段使用 Mock 模式，不依赖真实钉钉应用：

```
前端 → GET /system/oauth/dingtalk/authorize?mock=true
     → 返回模拟钉钉用户信息
     → 匹配 sys_user（手机号）→ 找到则登录
     → 未找到 → 自动创建用户（默认租户 1，角色 EMPLOYEE）
     → 签发 JWT，返回 token + 用户信息
```

### 4.2 创建 sys_oauth_user 记录

首次钉钉登录时写入 `sys_oauth_user`（provider=dingtalk, open_id=模拟值），后续可绑定真实钉钉 openId。

---

## 五、二级缓存

### 5.1 缓存架构

```
读请求 → Caffeine（本地 10min TTL）
       → 未命中 → Redis（30min TTL）
       → 未命中 → MySQL → 回写 Redis + Caffeine

写请求 → MySQL → 删 Redis key → Redis Pub/Sub 广播清 Caffeine
```

### 5.2 缓存对象

| Key | 内容 | TTL |
|-----|------|-----|
| `user:{userId}` | 用户基本信息 | Redis 30min |
| `user:perm:{userId}` | 角色编码列表 + 权限标识列表 | Redis 30min |
| `dept:tree:{tenantId}` | 部门树 JSON | Redis 10min |

---

## 六、操作审计 AOP

### 6.1 使用方式

```java
@AuditLog(module = "用户管理", operation = "UPDATE")
public Result<Void> updateUser(UserUpdateDTO dto) { ... }
```

### 6.2 AOP 行为

- 切点：`@AuditLog` 注解的方法
- 入参序列化（自动脱敏 password/phone 等字段）
- 变更前后值（UPDATE 时对比）
- 异步写入 `sys_audit_log`（线程池，不阻塞主流程）
- 自动获取当前 userId/tenantId/username/IP

---

## 七、测试验证

### 7.1 启动验证

1. `mvn clean package -DskipTests`
2. Docker 中间件确认运行
3. 启动 6 个服务
4. 验证所有 health endpoint 返回 200
5. 验证 Nacos 注册 6 个实例

### 7.2 接口验证

- 无 Token 访问 `/system/user/page` → 401
- 登录获取 Token → 携带 Token 访问 → 200
- 不同租户用户访问 → 数据隔离（tenant_id 自动过滤）

---

## 八、关键文件清单

| 类型 | 文件 | 位置 |
|------|------|------|
| 新增 | JwtAuthFilter | gateway-service |
| 新增 | SecurityConfig | system-service |
| 新增 | TenantContextFilter | expense-common |
| 新增 | CacheConfig | system-service |
| 新增 | AuditLog 注解 + AOP | expense-common |
| 新增 | 8 Controller + Service + Mapper | system-service |
| 已有 | ExpenseFlowTenantLineHandler | expense-common（不修改） |
| 已有 | BaseEntity / Result / GlobalExceptionHandler | expense-common（不修改） |
