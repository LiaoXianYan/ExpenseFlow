# P2-2 Controller 集成测试 — 设计文档

> 版本：v1.0 | 日期：2026-05-16 | 状态：已确认，待实施

## 背景

当前 61 个测试全部是 Service 层 Mockito 单元测试，0 个 Controller 层集成测试。完整的 HTTP→Controller→Service→DB→Response 链路从未被自动化验证。

## 方案：MockMvc + H2 + JWT 测试辅助

- 每个业务服务 2 个集成测试，共 5 服务 × 2 = 10 测试
- H2 内存库（MySQL 兼容模式），每次测试自动回滚
- JWT 在测试内生成（`expense-common` 共享 `TestJwtHelper`）
- Nacos / RabbitMQ 在 test profile 下全部禁用

## 测试架构

### application-test.yml（每个服务一份）

```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false
      config:
        enabled: false
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
  rabbitmq:
    listener:
      auto-startup: false
```

### 公共测试基类（每个服务）

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseControllerTest {
    @Autowired protected MockMvc mockMvc;

    protected String authHeader(String... roles) {
        return "Bearer " + TestJwtHelper.generateToken(1L, 0L, roles);
    }

    protected ResultActions getWithJwt(String url, String... roles) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", authHeader(roles)));
    }

    protected ResultActions postWithJwt(String url, Object body, String... roles) throws Exception {
        return mockMvc.perform(post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(body))
            .header("Authorization", authHeader(roles)));
    }
}
```

### TestJwtHelper（expense-common/src/test/java）

```java
public class TestJwtHelper {
    private static final String SECRET = "test-secret-key-min-256-bits-long-enough-for-hs256";

    public static String generateToken(Long userId, Long tenantId, String... roles) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("tenantId", tenantId)
            .claim("roles", List.of(roles))
            .claim("username", "test-user")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
```

## 服务测试清单

| 服务 | 测试 1 | 测试 2 |
|---|---|---|
| **expense-service** | `GET /expense/report/page` → 200 + JSON 分页结构 | `POST /expense/report` → 创建草稿，status=DRAFT |
| **approval-service** | `GET /approval/task/todo` → 200 + 任务列表 | `POST /approval/process/start` → 200 + processInstanceId |
| **system-service** | `GET /system/user/{id}` → 查询用户 | `POST /system/role` → 创建角色（ADMIN 权限） |
| **ai-service** | `POST /ai/rag/ask` → RAG 问答返回 answer | `POST /ai/review` → AI 审单返回 riskLevel |
| **notification-service** | `GET /notification/message/page` → 站内消息分页 | `GET /notification/template/list` → 模板列表 |

## 测试规则

- 每个测试 `@Transactional`，自动回滚
- 使用 `assertj` 断言响应 JSON
- H2 `DB_CLOSE_DELAY=-1` 保证测试间 schema 不丢
- 测试方法命名：`shouldReturnXxxWhenYyy`

## 验证标准

1. 每个服务 `mvn test` 通过（含新增集成测试 + 原有单元测试）
2. 集成测试不依赖任何外部服务（Nacos/Redis/RabbitMQ 全禁用）
3. 10 个集成测试全部通过
