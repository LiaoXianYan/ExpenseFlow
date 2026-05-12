# M1: 基础设施搭建与开发环境就绪 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 6 个微服务 Spring Boot 3.3 骨架 + 公共模块 + Docker Compose 中间件，所有服务成功注册到 Nacos

**Architecture:** Maven 多模块项目，父 POM 统一依赖管理，expense-common 公共模块被所有服务依赖，各服务通过 Nacos 注册发现

**Tech Stack:** Spring Boot 3.3 + Spring Cloud 2023.0.x + Nacos 2.3 + MySQL 8 + Redis 7 + RabbitMQ 3.13 + JDK 17

**Context:** 项目目录 D:\RecoginitionOCR，已有 CLAUDE.md 和 sql/init.sql

---

## 文件结构

```
D:\RecoginitionOCR\
├── pom.xml                              # 父 POM（版本统一管理）
├── docker-compose.yml                   # 中间件编排
├── sql/init.sql                         # DDL（已存在）
├── expense-common/                      # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/expenseflow/common/
│       ├── result/Result.java
│       ├── result/PageResult.java
│       ├── exception/BusinessException.java
│       ├── exception/GlobalExceptionHandler.java
│       ├── entity/BaseEntity.java
│       └── handler/TenantLineHandler.java
├── gateway-service/                     # 网关服务
│   ├── pom.xml
│   └── src/main/java/com/expenseflow/gateway/
│       └── GatewayApplication.java
│   └── src/main/resources/application.yml
├── system-service/                      # 系统服务
│   ├── pom.xml
│   └── src/main/java/com/expenseflow/system/
│       └── SystemApplication.java
│   └── src/main/resources/application.yml
├── expense-service/                     # 报销服务
│   ├── pom.xml
│   └── src/main/java/com/expenseflow/expense/
│       └── ExpenseApplication.java
│   └── src/main/resources/application.yml
├── approval-service/                    # 审批引擎服务
│   ├── pom.xml
│   └── src/main/java/com/expenseflow/approval/
│       └── ApprovalApplication.java
│   └── src/main/resources/application.yml
├── ai-service/                          # AI 智能服务
│   ├── pom.xml
│   └── src/main/java/com/expenseflow/ai/
│       └── AiApplication.java
│   └── src/main/resources/application.yml
└── notification-service/                # 通知服务
    ├── pom.xml
    └── src/main/java/com/expenseflow/notification/
        └── NotificationApplication.java
    └── src/main/resources/application.yml
```

---

### Task 1: 创建父 POM 与项目骨架

**Files:**
- Create: `D:\RecoginitionOCR\pom.xml`

- [ ] **Step 1: 编写父 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.expenseflow</groupId>
    <artifactId>expense-flow</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>ExpenseFlow</name>
    <description>差旅与费用报销智能管理平台</description>

    <modules>
        <module>expense-common</module>
        <module>gateway-service</module>
        <module>system-service</module>
        <module>expense-service</module>
        <module>approval-service</module>
        <module>ai-service</module>
        <module>notification-service</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <spring-boot.version>3.3.5</spring-boot.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <flowable.version>7.0.1</flowable.version>
        <drools.version>9.44.0.Final</drools.version>
        <langchain4j.version>0.35.0</langchain4j.version>
        <mysql.version>8.0.33</mysql.version>
        <hutool.version>5.8.25</hutool.version>
        <knife4j.version>4.4.0</knife4j.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud Alibaba BOM -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- MyBatis-Plus -->
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <!-- MySQL -->
            <dependency>
                <groupId>com.mysql</groupId>
                <artifactId>mysql-connector-j</artifactId>
                <version>${mysql.version}</version>
            </dependency>
            <!-- Hutool -->
            <dependency>
                <groupId>cn.hutool</groupId>
                <artifactId>hutool-all</artifactId>
                <version>${hutool.version}</version>
            </dependency>
            <!-- Knife4j -->
            <dependency>
                <groupId>com.github.xiaoymin</groupId>
                <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
                <version>${knife4j.version}</version>
            </dependency>
            <!-- Flowable -->
            <dependency>
                <groupId>org.flowable</groupId>
                <artifactId>flowable-spring-boot-starter</artifactId>
                <version>${flowable.version}</version>
            </dependency>
            <!-- Drools -->
            <dependency>
                <groupId>org.drools</groupId>
                <artifactId>drools-core</artifactId>
                <version>${drools.version}</version>
            </dependency>
            <dependency>
                <groupId>org.drools</groupId>
                <artifactId>drools-compiler</artifactId>
                <version>${drools.version}</version>
            </dependency>
            <!-- LangChain4j -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-spring-boot-starter</artifactId>
                <version>${langchain4j.version}</version>
            </dependency>
            <!-- Common Module -->
            <dependency>
                <groupId>com.expenseflow</groupId>
                <artifactId>expense-common</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 验证 pom.xml 格式正确**

```bash
cd D:\RecoginitionOCR && mvn validate
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: 创建父 POM，统一依赖版本管理"
```

---

### Task 2: 创建 expense-common 公共模块

**Files:**
- Create: `D:\RecoginitionOCR\expense-common\pom.xml`
- Create: `D:\RecoginitionOCR\expense-common\src\main\java\com\expenseflow\common\result\Result.java`
- Create: `D:\RecoginitionOCR\expense-common\src\main\java\com\expenseflow\common\result\PageResult.java`
- Create: `D:\RecoginitionOCR\expense-common\src\main\java\com\expenseflow\common\exception\BusinessException.java`
- Create: `D:\RecoginitionOCR\expense-common\src\main\java\com\expenseflow\common\exception\GlobalExceptionHandler.java`
- Create: `D:\RecoginitionOCR\expense-common\src\main\java\com\expenseflow\common\entity\BaseEntity.java`
- Create: `D:\RecoginitionOCR\expense-common\src\main\java\com\expenseflow\common\handler\TenantLineHandler.java`

- [ ] **Step 1: 创建 common 模块 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.expenseflow</groupId>
        <artifactId>expense-flow</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>expense-common</artifactId>
    <packaging>jar</packaging>
    <name>expense-common</name>

    <dependencies>
        <!-- 每个微服务都需要的基础依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建统一响应体 Result.java**

```java
package com.expenseflow.common.result;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private long timestamp;
    private String requestId;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> Result<T> fail(String message) {
        return fail(400, message);
    }
}
```

- [ ] **Step 3: 创建分页响应体 PageResult.java**

```java
package com.expenseflow.common.result;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> r = new PageResult<>();
        r.records = records;
        r.total = total;
        r.page = page;
        r.size = size;
        return r;
    }
}
```

- [ ] **Step 4: 创建业务异常类 BusinessException.java**

```java
package com.expenseflow.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **Step 5: 创建全局异常处理器 GlobalExceptionHandler.java**

```java
package com.expenseflow.common.exception;

import com.expenseflow.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统内部错误");
    }
}
```

- [ ] **Step 6: 创建基础实体 BaseEntity.java**

```java
package com.expenseflow.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 7: 创建多租户处理器 TenantLineHandler.java**

```java
package com.expenseflow.common.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

public class ExpenseFlowTenantLineHandler implements TenantLineHandler {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    // 忽略多租户的表（系统表）
    @Override
    public boolean ignoreTable(String tableName) {
        return "sys_tenant".equalsIgnoreCase(tableName)
                || "sys_permission".equalsIgnoreCase(tableName);
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
```

- [ ] **Step 8: 验证 common 模块编译**

```bash
cd D:\RecoginitionOCR && mvn compile -pl expense-common
```

Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add expense-common/
git commit -m "feat(common): 创建公共模块——统一响应/异常/基础实体/多租户处理器"
```

---

### Task 3: 创建 gateway-service（网关服务）

**Files:**
- Create: `D:\RecoginitionOCR\gateway-service\pom.xml`
- Create: `D:\RecoginitionOCR\gateway-service\src\main\java\com\expenseflow\gateway\GatewayApplication.java`
- Create: `D:\RecoginitionOCR\gateway-service\src\main\resources\application.yml`

- [ ] **Step 1: 创建 gateway pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.expenseflow</groupId>
        <artifactId>expense-flow</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>gateway-service</artifactId>
    <packaging>jar</packaging>
    <name>gateway-service</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 GatewayApplication.java**

```java
package com.expenseflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        - id: system-service
          uri: lb://system-service
          predicates:
            - Path=/system/**
          filters:
            - StripPrefix=0
        - id: expense-service
          uri: lb://expense-service
          predicates:
            - Path=/expense/**
          filters:
            - StripPrefix=0
        - id: approval-service
          uri: lb://approval-service
          predicates:
            - Path=/approval/**
          filters:
            - StripPrefix=0
        - id: ai-service
          uri: lb://ai-service
          predicates:
            - Path=/ai/**
          filters:
            - StripPrefix=0
        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/notification/**
          filters:
            - StripPrefix=0
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOriginPatterns: "*"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

- [ ] **Step 4: 编译 gateway-service**

```bash
cd D:\RecoginitionOCR && mvn compile -pl gateway-service
```

Expected: BUILD SUCCESS

---

### Task 4: 创建 system-service（系统服务）

**Files:**
- Create: `D:\RecoginitionOCR\system-service\pom.xml`
- Create: `D:\RecoginitionOCR\system-service\src\main\java\com\expenseflow\system\SystemApplication.java`
- Create: `D:\RecoginitionOCR\system-service\src\main\resources\application.yml`

- [ ] **Step 1: 创建 system-service pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.expenseflow</groupId>
        <artifactId>expense-flow</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>system-service</artifactId>
    <packaging>jar</packaging>
    <name>system-service</name>

    <dependencies>
        <dependency>
            <groupId>com.expenseflow</groupId>
            <artifactId>expense-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 SystemApplication.java**

```java
package com.expenseflow.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.expenseflow")
@EnableDiscoveryClient
@EnableFeignClients
public class SystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 application.yml**

```yaml
server:
  port: 8081

spring:
  application:
    name: system-service
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/expense_flow?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:root}
    hikari:
      maximum-pool-size: 20
  data:
    redis:
      host: localhost
      port: 6379
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
        file-extension: yaml

mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

- [ ] **Step 4: 编译 system-service**

```bash
cd D:\RecoginitionOCR && mvn compile -pl system-service
```

Expected: BUILD SUCCESS

---

### Task 5: 创建 expense-service（差旅报销服务）

**Files:**
- Create: `D:\RecoginitionOCR\expense-service\pom.xml`
- Create: `D:\RecoginitionOCR\expense-service\src\main\java\com\expenseflow\expense\ExpenseApplication.java`
- Create: `D:\RecoginitionOCR\expense-service\src\main\resources\application.yml`

- [ ] **Step 1: 创建 expense-service pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.expenseflow</groupId>
        <artifactId>expense-flow</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>expense-service</artifactId>
    <packaging>jar</packaging>
    <name>expense-service</name>

    <dependencies>
        <dependency>
            <groupId>com.expenseflow</groupId>
            <artifactId>expense-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 ExpenseApplication.java**

```java
package com.expenseflow.expense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.expenseflow")
@EnableDiscoveryClient
@EnableFeignClients
public class ExpenseApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExpenseApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 application.yml**（与 system-service 结构相同，改端口为 8082、application.name 为 expense-service）

```yaml
server:
  port: 8082

spring:
  application:
    name: expense-service
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/expense_flow?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:root}
    hikari:
      maximum-pool-size: 20
  data:
    redis:
      host: localhost
      port: 6379
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
        file-extension: yaml

mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

- [ ] **Step 4: 编译 expense-service**

```bash
cd D:\RecoginitionOCR && mvn compile -pl expense-service
```

Expected: BUILD SUCCESS

---

### Task 6: 创建 approval-service、ai-service、notification-service

**Files:**
- Create: `D:\RecoginitionOCR\approval-service\pom.xml`（额外依赖 flowable 和 drools）
- Create: `D:\RecoginitionOCR\approval-service\src\main\java\com\expenseflow\approval\ApprovalApplication.java`
- Create: `D:\RecoginitionOCR\approval-service\src\main\resources\application.yml`
- Create: `D:\RecoginitionOCR\ai-service\pom.xml`（额外依赖 langchain4j）
- Create: `D:\RecoginitionOCR\ai-service\src\main\java\com\expenseflow\ai\AiApplication.java`
- Create: `D:\RecoginitionOCR\ai-service\src\main\resources\application.yml`
- Create: `D:\RecoginitionOCR\notification-service\pom.xml`（额外依赖 rabbitmq）
- Create: `D:\RecoginitionOCR\notification-service\src\main\java\com\expenseflow\notification\NotificationApplication.java`
- Create: `D:\RecoginitionOCR\notification-service\src\main\resources\application.yml`

- [ ] **Step 1: 创建 approval-service pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.expenseflow</groupId>
        <artifactId>expense-flow</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>approval-service</artifactId>
    <packaging>jar</packaging>
    <name>approval-service</name>

    <dependencies>
        <dependency>
            <groupId>com.expenseflow</groupId>
            <artifactId>expense-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flowable</groupId>
            <artifactId>flowable-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.drools</groupId>
            <artifactId>drools-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.drools</groupId>
            <artifactId>drools-compiler</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <!-- Drools 编译期与 runtime 冲突，runtime 不需要 compiler -->
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 ApprovalApplication.java**

```java
package com.expenseflow.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.expenseflow")
@EnableDiscoveryClient
@EnableFeignClients
public class ApprovalApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 approval-service application.yml**（端口 8083，其余同 system-service 结构）

```yaml
server:
  port: 8083

spring:
  application:
    name: approval-service
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/expense_flow?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:root}
    hikari:
      maximum-pool-size: 20
  data:
    redis:
      host: localhost
      port: 6379
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
        file-extension: yaml

mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

flowable:
  database-schema-update: true
  async-executor-activate: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

- [ ] **Step 4: 创建 ai-service pom.xml, AiApplication.java, application.yml**（端口 8084，pom 增加 langchain4j 依赖）

ai-service pom.xml 在 expense-common 基础上增加：
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
</dependency>
```

AiApplication.java 与上述应用结构一致，类名 AiApplication，包 com.expenseflow.ai。

application.yml 端口 8084，无 flowable 配置（区别于 approval-service 的唯一差异）。

- [ ] **Step 5: 创建 notification-service pom.xml, NotificationApplication.java, application.yml**（端口 8085，pom 增加 rabbitmq 依赖）

notification-service pom.xml 在 expense-common 基础上增加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

NotificationApplication.java 包 com.expenseflow.notification。

application.yml 端口 8085，增加：
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

- [ ] **Step 6: 全量编译**

```bash
cd D:\RecoginitionOCR && mvn compile
```

Expected: BUILD SUCCESS（6 个模块全部编译通过）

- [ ] **Step 7: Commit**

```bash
git add approval-service/ ai-service/ notification-service/
git commit -m "feat: 创建 approval/ai/notification 三大服务骨架"
```

---

### Task 7: Docker Compose 中间件编排

**Files:**
- Create: `D:\RecoginitionOCR\docker-compose.yml`

- [ ] **Step 1: 创建 docker-compose.yml**

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: expense-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: expense_flow
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/01-init.sql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: expense-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: expense-rabbitmq
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
      TZ: Asia/Shanghai
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmqctl", "status"]
      interval: 10s
      timeout: 5s
      retries: 5

  nacos:
    image: nacos/nacos-server:v2.3.1
    container_name: expense-nacos
    restart: unless-stopped
    environment:
      MODE: standalone
      PREFER_HOST_MODE: hostname
      TZ: Asia/Shanghai
    ports:
      - "8848:8848"
      - "9848:9848"
    volumes:
      - nacos-data:/home/nacos/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/v1/console/health/liveness"]
      interval: 15s
      timeout: 5s
      retries: 10

  prometheus:
    image: prom/prometheus
    container_name: expense-prometheus
    restart: unless-stopped
    ports:
      - "9090:9090"
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'

  grafana:
    image: grafana/grafana:latest
    container_name: expense-grafana
    restart: unless-stopped
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASS: admin
    volumes:
      - grafana-data:/var/lib/grafana

volumes:
  mysql-data:
  redis-data:
  rabbitmq-data:
  nacos-data:
  prometheus-data:
  grafana-data:
```

- [ ] **Step 2: 创建 Prometheus 配置文件**

创建 `D:\RecoginitionOCR\config\prometheus.yml`：
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'expenseflow-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'host.docker.internal:8080'
          - 'host.docker.internal:8081'
          - 'host.docker.internal:8082'
          - 'host.docker.internal:8083'
          - 'host.docker.internal:8084'
          - 'host.docker.internal:8085'
```

- [ ] **Step 3: 启动中间件**

```bash
cd D:\RecoginitionOCR && docker compose up -d
```

Expected: 7 个容器全部启动。验证：
```bash
docker compose ps
```
Expected: 所有容器 STATUS = healthy/running

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml config/prometheus.yml
git commit -m "chore: Docker Compose 中间件编排——MySQL/Redis/RabbitMQ/Nacos/Prometheus/Grafana"
```

---

### Task 8: 数据库初始化验证

- [ ] **Step 1: 确认 init.sql 已自动执行**

```bash
docker exec expense-mysql mysql -uroot -proot expense_flow -e "SHOW TABLES;"
```

Expected: 输出 25 张表

- [ ] **Step 2: 验证种子数据**

```bash
docker exec expense-mysql mysql -uroot -proot expense_flow -e "SELECT id, tenant_code, tenant_name FROM sys_tenant;"
```

Expected:
```
id | tenant_code | tenant_name
0  | SYSTEM      | 系统默认租户
1  | DEMO        | 演示租户
```

---

### Task 9: Nacos 注册验证

- [ ] **Step 1: 启动 gateway-service**

```bash
cd D:\RecoginitionOCR && mvn spring-boot:run -pl gateway-service
```

在另一个终端中验证 Nacos 控制台 http://localhost:8848/nacos，服务列表可见 gateway-service。

- [ ] **Step 2: 逐一启动其余 5 个服务**

按 system → expense → approval → ai → notification 顺序各启动一个实例，每次验证 Nacos 服务列表。

```bash
mvn spring-boot:run -pl system-service &
mvn spring-boot:run -pl expense-service &
mvn spring-boot:run -pl approval-service &
mvn spring-boot:run -pl ai-service &
mvn spring-boot:run -pl notification-service &
```

- [ ] **Step 3: 验收——Nacos 控制台**

打开 http://localhost:8848/nacos → 服务管理 → 服务列表

Expected: 6 个服务全部注册（gateway-service, system-service, expense-service, approval-service, ai-service, notification-service），每个实例数=1，健康状态=UP

---

### Task 10: 最终提交

- [ ] **Step 1: 添加 .gitignore**

```bash
cat <<'EOF' >> D:\RecoginitionOCR\.gitignore
# Maven
target/
*.jar
*.war

# IDE
.idea/
*.iml
.vscode/

# Logs
logs/
*.log

# OS
.DS_Store
Thumbs.db

# Superpowers
.superpowers/

# Docker volumes
volumes/

# Env
.env
*.env.local
EOF
```

- [ ] **Step 2: Commit**

```bash
git init
git add .
git commit -m "chore: M1 完成——6服务骨架+common+Docker Compose+25表DDL+Nacos注册验证"
```

---

## M1 验收清单

| 检查项 | 标准 |
|--------|------|
| Maven 全量编译 | `mvn clean compile` BUILD SUCCESS |
| Docker Compose 启动 | 7 个容器 running |
| 数据库初始化 | 25 张表 + 种子数据就绪 |
| Nacos 注册 | 6 个服务全部健康 |
| Gateway 路由 | 健康检查 `/actuator/health` 可访问 |
| Git 提交 | 有意义的 commit message |
