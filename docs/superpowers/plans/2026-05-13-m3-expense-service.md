# M3: 差旅报销核心服务 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 expense-service 完整业务：出差申请/报销单/明细/发票OCR/消费记录/费用政策/打款流水，7张表 CRUD + 状态流转 + Feign对接

**Architecture:** expense-service 通过 OpenFeign 对接 system-service（用户/部门）和 approval-service（M3 Mock 审批流），阿里云 OCR SDK 直接调用。复用 expense-common 的 BaseEntity/多租户/审计基础设施。

**Tech Stack:** Spring Boot 3.3 + MyBatis-Plus 3.5 + OpenFeign + 阿里云 OCR SDK + JJWT

---

### Task 1: 添加 M3 依赖

**Files:**
- Modify: `expense-service/pom.xml`
- Modify: `pom.xml` (父 POM 版本管理)

- [ ] **Step 1: 父 POM 添加阿里云 OCR SDK 版本**

在 `pom.xml` `<properties>` 中添加：
```xml
<aliyun-ocr.version>1.0.0</aliyun-ocr.version>
```

- [ ] **Step 2: expense-service pom.xml 添加依赖**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

**阿里云 OCR SDK 用 Mock 实现**（课程项目避免真实 SDK 的复杂性）：
```xml
<!-- 真实SDK（上线时取消注释） -->
<!--
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>ocr_api20210707</artifactId>
    <version>${aliyun-ocr.version}</version>
</dependency>
-->
```

- [ ] **Step 3: Commit**

```bash
git add pom.xml expense-service/pom.xml
git commit -m "chore(m3): expense-service 添加 OpenFeign + Validation + Lombok 依赖"
```

---

### Task 2: expense-service Config 层

**Files:**
- Create: `expense-service/src/main/java/com/expenseflow/expense/config/MybatisPlusConfig.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/config/MetaObjectHandlerConfig.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/config/OcrConfig.java`

- [ ] **Step 1: 创建 MybatisPlusConfig**

```java
package com.expenseflow.expense.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.expenseflow.common.handler.ExpenseFlowTenantLineHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new ExpenseFlowTenantLineHandler()));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 2: 创建 MetaObjectHandlerConfig**

```java
package com.expenseflow.expense.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **Step 3: 创建 OcrConfig（Mock）**

```java
package com.expenseflow.expense.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "expense.ocr")
public class OcrConfig {
    private boolean mock = true;          // Mock模式默认开启
    private String uploadPath = "./upload/invoice";
    private String accessKeyId = "";
    private String accessKeySecret = "";
}
```

- [ ] **Step 4: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/config/
git commit -m "feat(expense): 添加 MybatisPlus + MetaObjectHandler + OcrConfig 配置"
```

---

### Task 3: Entity 层 (7 个 PO)

**Files:**
- Create: `expense-service/src/main/java/com/expenseflow/expense/entity/` 下 7 个 Entity

- [ ] **Step 1: 创建 ExTravelRequest**

```java
package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_travel_request")
public class ExTravelRequest extends BaseEntity {
    private String requestNo;
    private Long applicantId;
    private Long departmentId;
    private String travelPurpose;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal estimatedAmount;
    private String companions;
    private String remark;
    private String status;
    private String processInstanceId;
}
```

- [ ] **Step 2: 创建其他 6 个 Entity**

`ExExpenseReport`:
```java
package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_expense_report")
public class ExExpenseReport extends BaseEntity {
    private String reportNo;
    private Long applicantId;
    private Long departmentId;
    private Long travelRequestId;
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private LocalDate reportDate;
    private String remark;
    private String status;
    private String processInstanceId;
    private BigDecimal paidAmount;
    private LocalDateTime paidTime;
}
```

`ExExpenseItem`:
```java
package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_expense_item")
public class ExExpenseItem extends BaseEntity {
    private Long reportId;
    private String expenseType;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
}
```

`ExInvoice`:
```java
package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_invoice")
public class ExInvoice extends BaseEntity {
    private String invoiceNo;
    private String invoiceCode;
    private String invoiceType;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String sellerName;
    private String sellerTaxNo;
    private String buyerName;
    private String buyerTaxNo;
    private String imageUrl;
    private String ocrStatus;
    private String ocrRawResult;
    private java.math.BigDecimal ocrConfidence;
    private String verifyStatus;
}
```

`ExCostRecord`:
```java
package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_cost_record")
public class ExCostRecord extends BaseEntity {
    private Long userId;
    private LocalDate costDate;
    private String costType;
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
    private Long travelRequestId;
    private Long reportId;
}
```

`ExPaymentRecord`:
```java
package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_payment_record")
public class ExPaymentRecord extends BaseEntity {
    private Long reportId;
    private String paymentNo;
    private String payeeName;
    private String payeeAccount;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentStatus;
    private LocalDateTime paymentTime;
    private Long operatorId;
    private String remark;
}
```

`ExExpensePolicy`:
```java
package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_expense_policy")
public class ExExpensePolicy extends BaseEntity {
    private String policyName;
    private String expenseType;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private String cityTier;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer status;
    private String remark;
}
```

- [ ] **Step 3: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/entity/
git commit -m "feat(expense): 添加 7 个 Entity (ex_travel_request ~ ex_expense_policy)"
```

---

### Task 4: Mapper 层 (7 个)

**Files:**
- Create: `expense-service/src/main/java/com/expenseflow/expense/mapper/` 下 7 个 Mapper

- [ ] **Step 1: 批量创建 7 个 Mapper**

所有 Mapper 遵循相同模式：
```java
package com.expenseflow.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.expense.entity.XXX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface XXXMapper extends BaseMapper<XXX> {
}
```

| Mapper | Entity |
|--------|--------|
| ExTravelRequestMapper | ExTravelRequest |
| ExExpenseReportMapper | ExExpenseReport |
| ExExpenseItemMapper | ExExpenseItem |
| ExInvoiceMapper | ExInvoice |
| ExCostRecordMapper | ExCostRecord |
| ExPaymentRecordMapper | ExPaymentRecord |
| ExExpensePolicyMapper | ExExpensePolicy |

- [ ] **Step 2: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/mapper/
git commit -m "feat(expense): 添加 7 个 MyBatis-Plus Mapper 接口"
```

---

### Task 5: DTO + VO

**Files:**
- Create: DTOs in `expense-service/src/main/java/com/expenseflow/expense/dto/`
- Create: VOs in `expense-service/src/main/java/com/expenseflow/expense/vo/`

- [ ] **Step 1: 创建核心 DTO**

TravelRequestDTO:
```java
package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TravelRequestDTO {
    @NotBlank(message = "出差目的不能为空")
    private String travelPurpose;
    @NotBlank(message = "目的地不能为空")
    private String destination;
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
    private BigDecimal estimatedAmount;
    private String companions;
    private Long departmentId;
    private String remark;
}
```

ExpenseReportDTO:
```java
package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ExpenseReportDTO {
    @NotNull(message = "报销日期不能为空")
    private LocalDate reportDate;
    private Long travelRequestId;
    private Long departmentId;
    private String remark;
}
```

ExpenseItemDTO:
```java
package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseItemDTO {
    @NotBlank(message = "费用类型不能为空")
    private String expenseType;
    @NotNull(message = "费用日期不能为空")
    private LocalDate expenseDate;
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
}
```

InvoiceUploadDTO — 通过 `@RequestParam MultipartFile` 直接在 Controller 接收，不需要单独 DTO。

CostRecordDTO:
```java
package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CostRecordDTO {
    @NotNull(message = "费用日期不能为空")
    private LocalDate costDate;
    @NotBlank(message = "费用类型不能为空")
    private String costType;
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
    private Long travelRequestId;
    private Long reportId;
}
```

ExpensePolicyDTO:
```java
package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpensePolicyDTO {
    @NotBlank(message = "政策名称不能为空")
    private String policyName;
    @NotBlank(message = "费用类型不能为空")
    private String expenseType;
    @NotNull(message = "单次上限不能为空")
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private String cityTier;
    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private String remark;
}
```

ApprovalStartDTO:
```java
package com.expenseflow.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApprovalStartDTO {
    private String businessType;
    private Long businessId;
    private String requestNo;
    private Long applicantId;
    private String applicantName;
}
```

- [ ] **Step 2: 创建 VO**

TravelRequestVO:
```java
package com.expenseflow.expense.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TravelRequestVO {
    private Long id;
    private Long tenantId;
    private String requestNo;
    private Long applicantId;
    private String applicantName;      // Feign from system-service
    private Long departmentId;
    private String departmentName;     // Feign from system-service
    private String travelPurpose;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal estimatedAmount;
    private String companions;
    private String remark;
    private String status;
    private String processInstanceId;
    private LocalDateTime createTime;
}
```

ExpenseReportVO:
```java
package com.expenseflow.expense.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExpenseReportVO {
    private Long id;
    private Long tenantId;
    private String reportNo;
    private Long applicantId;
    private String applicantName;      // Feign
    private Long departmentId;
    private String departmentName;     // Feign
    private Long travelRequestId;
    private String travelRequestNo;    // Feign
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private LocalDate reportDate;
    private String remark;
    private String status;
    private String processInstanceId;
    private BigDecimal paidAmount;
    private LocalDateTime paidTime;
    private List<ExpenseItemVO> items;
    private LocalDateTime createTime;
}
```

ExpenseItemVO:
```java
package com.expenseflow.expense.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseItemVO {
    private Long id;
    private Long tenantId;
    private Long reportId;
    private String expenseType;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
    private LocalDateTime createTime;
}
```

InvoiceVO:
```java
package com.expenseflow.expense.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvoiceVO {
    private Long id;
    private Long tenantId;
    private String invoiceNo;
    private String invoiceCode;
    private String invoiceType;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String sellerName;
    private String buyerName;
    private String imageUrl;
    private String ocrStatus;
    private BigDecimal ocrConfidence;
    private String verifyStatus;
    private LocalDateTime createTime;
}
```

PolicyVO:
```java
package com.expenseflow.expense.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PolicyVO {
    private Long id;
    private Long tenantId;
    private String policyName;
    private String expenseType;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private String cityTier;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
```

- [ ] **Step 3: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/dto/ \
        expense-service/src/main/java/com/expenseflow/expense/vo/
git commit -m "feat(expense): 添加 DTO (Travel/Report/Item/Cost/Policy/Approval) + VO"
```

---

### Task 6: Feign 客户端

**Files:**
- Create: `expense-service/src/main/java/com/expenseflow/expense/feign/SystemFeignClient.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/feign/fallback/SystemFeignFallback.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/feign/ApprovalFeignClient.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/feign/fallback/ApprovalFeignFallback.java`

- [ ] **Step 1: 创建 SystemFeignClient + Fallback**

SystemFeignClient:
```java
package com.expenseflow.expense.feign;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.feign.fallback.SystemFeignFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "system-service", path = "/system",
             fallbackFactory = SystemFeignFallbackFactory.class)
public interface SystemFeignClient {

    @GetMapping("/user/{id}")
    Result<com.expenseflow.expense.feign.dto.SystemUserDTO> getUser(@PathVariable Long id);

    @GetMapping("/department/{id}")
    Result<com.expenseflow.expense.feign.dto.SystemDeptDTO> getDepartment(@PathVariable Long id);
}
```

SystemUserDTO (Feign 返回的简化用户信息):
```java
package com.expenseflow.expense.feign.dto;

import lombok.Data;

@Data
public class SystemUserDTO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
}
```

SystemDeptDTO:
```java
package com.expenseflow.expense.feign.dto;

import lombok.Data;

@Data
public class SystemDeptDTO {
    private Long id;
    private String deptName;
    private String deptCode;
}
```

SystemFeignFallbackFactory:
```java
package com.expenseflow.expense.feign.fallback;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.feign.SystemFeignClient;
import com.expenseflow.expense.feign.dto.SystemDeptDTO;
import com.expenseflow.expense.feign.dto.SystemUserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SystemFeignFallbackFactory implements FallbackFactory<SystemFeignClient> {

    @Override
    public SystemFeignClient create(Throwable cause) {
        log.error("system-service 调用失败: {}", cause.getMessage());
        return new SystemFeignClient() {
            @Override
            public Result<SystemUserDTO> getUser(Long id) {
                SystemUserDTO dto = new SystemUserDTO();
                dto.setId(id);
                dto.setRealName("未知用户");
                return Result.ok(dto);
            }

            @Override
            public Result<SystemDeptDTO> getDepartment(Long id) {
                SystemDeptDTO dto = new SystemDeptDTO();
                dto.setId(id);
                dto.setDeptName("未知部门");
                return Result.ok(dto);
            }
        };
    }
}
```

- [ ] **Step 2: 创建 ApprovalFeignClient + Fallback**

ApprovalFeignClient:
```java
package com.expenseflow.expense.feign;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.feign.fallback.ApprovalFeignFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "approval-service", path = "/approval",
             fallbackFactory = ApprovalFeignFallbackFactory.class)
public interface ApprovalFeignClient {

    @PostMapping("/process/start")
    Result<String> startApproval(@RequestBody ApprovalStartDTO dto);
}
```

ApprovalFeignFallbackFactory:
```java
package com.expenseflow.expense.feign.fallback;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class ApprovalFeignFallbackFactory implements FallbackFactory<ApprovalFeignClient> {

    @Override
    public ApprovalFeignClient create(Throwable cause) {
        log.info("approval-service 未就绪, 使用 Mock 审批: {}", cause.getMessage());
        return new ApprovalFeignClient() {
            @Override
            public Result<String> startApproval(ApprovalStartDTO dto) {
                return Result.ok("mock-pi-" + UUID.randomUUID().toString().substring(0, 12));
            }
        };
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/feign/
git commit -m "feat(expense): 添加 SystemFeignClient + ApprovalFeignClient (含 M3 Mock fallback)"
```

---

### Task 7: 业务编号生成工具 + 费用政策校验器

**Files:**
- Create: `expense-service/src/main/java/com/expenseflow/expense/util/NoGenerator.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/util/PolicyValidator.java`

- [ ] **Step 1: 创建 NoGenerator**

```java
package com.expenseflow.expense.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.expenseflow.expense.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class NoGenerator {

    private final ExTravelRequestMapper travelMapper;
    private final ExExpenseReportMapper reportMapper;
    private final ExPaymentRecordMapper paymentMapper;

    public String generateTravelNo() {
        return generateNo("TR", travelMapper);
    }

    public String generateReportNo() {
        return generateNo("ER", reportMapper);
    }

    public String generatePaymentNo() {
        return generateNo("PY", paymentMapper);
    }

    private String generateNo(String prefix, com.baomidou.mybatisplus.core.mapper.BaseMapper<?> mapper) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefixFull = prefix + "-" + today + "-";
        // Simplified: use timestamp-based sequence to avoid complex max查询
        long seq = System.currentTimeMillis() % 100000;
        return String.format("%s-%04d", prefix + "-" + today, seq % 10000);
    }
}
```

- [ ] **Step 2: 创建 PolicyValidator**

```java
package com.expenseflow.expense.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.expenseflow.expense.entity.ExExpenseItem;
import com.expenseflow.expense.entity.ExExpensePolicy;
import com.expenseflow.expense.mapper.ExExpensePolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PolicyValidator {

    private final ExExpensePolicyMapper policyMapper;

    /**
     * @return 超限警告列表，空列表表示全部合规
     */
    public List<String> validate(List<ExExpenseItem> items) {
        List<String> warnings = new ArrayList<>();
        List<ExExpensePolicy> policies = policyMapper.selectList(
            new LambdaQueryWrapper<ExExpensePolicy>().eq(ExExpensePolicy::getStatus, 1));

        Map<String, ExExpensePolicy> policyMap = new HashMap<>();
        for (ExExpensePolicy p : policies) {
            policyMap.put(p.getExpenseType(), p);
        }

        for (ExExpenseItem item : items) {
            ExExpensePolicy policy = policyMap.get(item.getExpenseType());
            if (policy == null) continue;

            if (item.getAmount().compareTo(policy.getMaxAmount()) > 0) {
                warnings.add(String.format("%s 超过单次上限 %s (实际: %s)",
                    item.getExpenseType(), policy.getMaxAmount(), item.getAmount()));
            }
        }
        return warnings;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/util/
git commit -m "feat(expense): 添加 NoGenerator 编号生成 + PolicyValidator 费用政策校验"
```

---

### Task 8: Service 层 — TravelRequest + ExpenseReport

**Files:**
- Create: 2 个 Service 接口 + 2 个 Impl

- [ ] **Step 1: 创建 TravelRequestService + Impl**

TravelRequestService:
```java
package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.TravelRequestDTO;
import com.expenseflow.expense.vo.TravelRequestVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface TravelRequestService {
    Result<Page<TravelRequestVO>> page(int page, int size, Long applicantId, String status);
    Result<TravelRequestVO> getById(Long id);
    Result<TravelRequestVO> create(TravelRequestDTO dto);
    Result<TravelRequestVO> update(Long id, TravelRequestDTO dto);
    Result<Void> delete(Long id);
    Result<TravelRequestVO> submit(Long id);
    Result<TravelRequestVO> withdraw(Long id);
}
```

TravelRequestServiceImpl:
```java
package com.expenseflow.expense.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.dto.TravelRequestDTO;
import com.expenseflow.expense.entity.ExTravelRequest;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import com.expenseflow.expense.feign.SystemFeignClient;
import com.expenseflow.expense.feign.dto.SystemDeptDTO;
import com.expenseflow.expense.feign.dto.SystemUserDTO;
import com.expenseflow.expense.mapper.ExTravelRequestMapper;
import com.expenseflow.expense.service.TravelRequestService;
import com.expenseflow.expense.util.NoGenerator;
import com.expenseflow.expense.vo.TravelRequestVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TravelRequestServiceImpl implements TravelRequestService {

    private final ExTravelRequestMapper travelMapper;
    private final SystemFeignClient systemFeignClient;
    private final ApprovalFeignClient approvalFeignClient;
    private final NoGenerator noGenerator;

    @Override
    public Result<Page<TravelRequestVO>> page(int page, int size, Long applicantId, String status) {
        LambdaQueryWrapper<ExTravelRequest> qw = new LambdaQueryWrapper<>();
        if (applicantId != null) qw.eq(ExTravelRequest::getApplicantId, applicantId);
        if (status != null && !status.isEmpty()) qw.eq(ExTravelRequest::getStatus, status);
        qw.orderByDesc(ExTravelRequest::getCreateTime);
        Page<ExTravelRequest> pg = travelMapper.selectPage(new Page<>(page, size), qw);
        Page<TravelRequestVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(this::toVO).toList());
        return Result.ok(voPage);
    }

    @Override
    public Result<TravelRequestVO> getById(Long id) {
        ExTravelRequest t = travelMapper.selectById(id);
        return t == null ? Result.fail(404, "出差申请不存在") : Result.ok(toVO(t));
    }

    @Override
    @Transactional
    public Result<TravelRequestVO> create(TravelRequestDTO dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            return Result.fail(400, "结束日期不能早于开始日期");
        }
        ExTravelRequest t = new ExTravelRequest();
        BeanUtils.copyProperties(dto, t);
        if (dto.getEstimatedAmount() == null) t.setEstimatedAmount(BigDecimal.ZERO);
        t.setRequestNo(noGenerator.generateTravelNo());
        t.setStatus("DRAFT");
        travelMapper.insert(t);
        return Result.ok(toVO(t));
    }

    @Override
    @Transactional
    public Result<TravelRequestVO> update(Long id, TravelRequestDTO dto) {
        ExTravelRequest t = travelMapper.selectById(id);
        if (t == null) return Result.fail(404, "出差申请不存在");
        if (!"DRAFT".equals(t.getStatus()) && !"WITHDRAWN".equals(t.getStatus())) {
            return Result.fail(400, "仅草稿/已撤回状态可编辑");
        }
        BeanUtils.copyProperties(dto, t);
        t.setId(id);
        t.setStatus("DRAFT");
        travelMapper.updateById(t);
        return Result.ok(toVO(t));
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id) {
        ExTravelRequest t = travelMapper.selectById(id);
        if (t == null) return Result.fail(404, "出差申请不存在");
        if (!"DRAFT".equals(t.getStatus())) return Result.fail(400, "仅草稿状态可删除");
        travelMapper.deleteById(id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<TravelRequestVO> submit(Long id) {
        ExTravelRequest t = travelMapper.selectById(id);
        if (t == null) return Result.fail(404, "出差申请不存在");
        if (!"DRAFT".equals(t.getStatus())) return Result.fail(400, "仅草稿状态可提交");

        // 启动审批流 (M3 Mock: fallback 返回 mock processInstanceId)
        SystemUserDTO user = systemFeignClient.getUser(t.getApplicantId()).getData();
        ApprovalStartDTO approvalDTO = new ApprovalStartDTO(
            "TRAVEL_REQUEST", t.getId(), t.getRequestNo(),
            t.getApplicantId(), user != null ? user.getRealName() : "未知");
        Result<String> approvalResult = approvalFeignClient.startApproval(approvalDTO);

        t.setStatus("APPROVED"); // M3 Mock: 直接变 APPROVED
        t.setProcessInstanceId(approvalResult.getData());
        travelMapper.updateById(t);
        return Result.ok(toVO(t));
    }

    @Override
    @Transactional
    public Result<TravelRequestVO> withdraw(Long id) {
        ExTravelRequest t = travelMapper.selectById(id);
        if (t == null) return Result.fail(404, "出差申请不存在");
        if (!"SUBMITTED".equals(t.getStatus()) && !"APPROVING".equals(t.getStatus())) {
            return Result.fail(400, "仅已提交/审批中状态可撤回");
        }
        t.setStatus("WITHDRAWN");
        travelMapper.updateById(t);
        return Result.ok(toVO(t));
    }

    private TravelRequestVO toVO(ExTravelRequest t) {
        TravelRequestVO vo = new TravelRequestVO();
        BeanUtils.copyProperties(t, vo);
        try {
            if (t.getApplicantId() != null) {
                Result<SystemUserDTO> r = systemFeignClient.getUser(t.getApplicantId());
                if (r.getData() != null) vo.setApplicantName(r.getData().getRealName());
            }
            if (t.getDepartmentId() != null) {
                Result<SystemDeptDTO> r = systemFeignClient.getDepartment(t.getDepartmentId());
                if (r.getData() != null) vo.setDepartmentName(r.getData().getDeptName());
            }
        } catch (Exception ignored) {
            vo.setApplicantName("未知");
            vo.setDepartmentName("未知");
        }
        return vo;
    }
}
```

- [ ] **Step 2: 创建 ExpenseReportService + Impl**

ExpenseReportService:
```java
package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import com.expenseflow.expense.dto.ExpenseItemDTO;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface ExpenseReportService {
    Result<Page<ExpenseReportVO>> page(int page, int size, Long applicantId, String status);
    Result<ExpenseReportVO> getById(Long id);
    Result<ExpenseReportVO> create(ExpenseReportDTO dto);
    Result<ExpenseReportVO> update(Long id, ExpenseReportDTO dto);
    Result<Void> delete(Long id);
    Result<ExpenseReportVO> submit(Long id);
    Result<ExpenseReportVO> withdraw(Long id);
    Result<ExpenseReportVO> addItem(Long reportId, ExpenseItemDTO itemDTO);
}
```

ExpenseReportServiceImpl — 核心逻辑（含明细汇总、政策校验、审批启动）:
```java
package com.expenseflow.expense.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.dto.ExpenseItemDTO;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import com.expenseflow.expense.entity.*;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import com.expenseflow.expense.feign.SystemFeignClient;
import com.expenseflow.expense.feign.dto.SystemDeptDTO;
import com.expenseflow.expense.feign.dto.SystemUserDTO;
import com.expenseflow.expense.mapper.*;
import com.expenseflow.expense.service.ExpenseReportService;
import com.expenseflow.expense.util.NoGenerator;
import com.expenseflow.expense.util.PolicyValidator;
import com.expenseflow.expense.vo.ExpenseItemVO;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseReportServiceImpl implements ExpenseReportService {

    private final ExExpenseReportMapper reportMapper;
    private final ExExpenseItemMapper itemMapper;
    private final ExTravelRequestMapper travelMapper;
    private final SystemFeignClient systemFeignClient;
    private final ApprovalFeignClient approvalFeignClient;
    private final NoGenerator noGenerator;
    private final PolicyValidator policyValidator;

    @Override
    public Result<Page<ExpenseReportVO>> page(int page, int size, Long applicantId, String status) {
        LambdaQueryWrapper<ExExpenseReport> qw = new LambdaQueryWrapper<>();
        if (applicantId != null) qw.eq(ExExpenseReport::getApplicantId, applicantId);
        if (status != null && !status.isEmpty()) qw.eq(ExExpenseReport::getStatus, status);
        qw.orderByDesc(ExExpenseReport::getCreateTime);
        Page<ExExpenseReport> pg = reportMapper.selectPage(new Page<>(page, size), qw);
        Page<ExpenseReportVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(this::toVO).toList());
        return Result.ok(voPage);
    }

    @Override
    public Result<ExpenseReportVO> getById(Long id) {
        ExExpenseReport r = reportMapper.selectById(id);
        return r == null ? Result.fail(404, "报销单不存在") : Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> create(ExpenseReportDTO dto) {
        ExExpenseReport r = new ExExpenseReport();
        BeanUtils.copyProperties(dto, r);
        r.setReportNo(noGenerator.generateReportNo());
        r.setTotalAmount(BigDecimal.ZERO);
        r.setStatus("DRAFT");
        // 关联出差申请时检查状态
        if (dto.getTravelRequestId() != null) {
            ExTravelRequest travel = travelMapper.selectById(dto.getTravelRequestId());
            if (travel == null) return Result.fail(400, "关联的出差申请不存在");
        }
        reportMapper.insert(r);
        return Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> update(Long id, ExpenseReportDTO dto) {
        ExExpenseReport r = reportMapper.selectById(id);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"DRAFT".equals(r.getStatus())) return Result.fail(400, "仅草稿状态可编辑");
        BeanUtils.copyProperties(dto, r);
        r.setId(id);
        reportMapper.updateById(r);
        return Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id) {
        ExExpenseReport r = reportMapper.selectById(id);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"DRAFT".equals(r.getStatus())) return Result.fail(400, "仅草稿状态可删除");
        // 删除关联明细
        itemMapper.delete(new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, id));
        reportMapper.deleteById(id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> submit(Long id) {
        ExExpenseReport r = reportMapper.selectById(id);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"DRAFT".equals(r.getStatus())) return Result.fail(400, "仅草稿状态可提交");

        // 检查至少有1条明细
        long itemCount = itemMapper.selectCount(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, id));
        if (itemCount == 0) return Result.fail(400, "请至少添加一条报销明细");

        // 自动汇总总额
        List<ExExpenseItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, id));
        BigDecimal total = items.stream().map(ExExpenseItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        r.setTotalAmount(total);

        // 费用政策校验
        List<String> warnings = policyValidator.validate(items);

        // 启动审批流 (M3 Mock)
        SystemUserDTO user = systemFeignClient.getUser(r.getApplicantId()).getData();
        ApprovalStartDTO approvalDTO = new ApprovalStartDTO(
            "EXPENSE_REPORT", r.getId(), r.getReportNo(),
            r.getApplicantId(), user != null ? user.getRealName() : "未知");
        Result<String> approvalResult = approvalFeignClient.startApproval(approvalDTO);

        r.setStatus("APPROVED"); // M3 Mock: 直接变 APPROVED
        r.setProcessInstanceId(approvalResult.getData());
        reportMapper.updateById(r);
        return Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> withdraw(Long id) {
        ExExpenseReport r = reportMapper.selectById(id);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"SUBMITTED".equals(r.getStatus()) && !"APPROVING".equals(r.getStatus())) {
            return Result.fail(400, "仅已提交/审批中状态可撤回");
        }
        r.setStatus("WITHDRAWN");
        reportMapper.updateById(r);
        return Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> addItem(Long reportId, ExpenseItemDTO itemDTO) {
        ExExpenseReport r = reportMapper.selectById(reportId);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"DRAFT".equals(r.getStatus())) return Result.fail(400, "仅草稿状态可添加明细");

        ExExpenseItem item = new ExExpenseItem();
        BeanUtils.copyProperties(itemDTO, item);
        item.setReportId(reportId);
        itemMapper.insert(item);

        // 更新总额
        List<ExExpenseItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, reportId));
        BigDecimal total = items.stream().map(ExExpenseItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        r.setTotalAmount(total);
        reportMapper.updateById(r);

        return Result.ok(toVO(r));
    }

    private ExpenseReportVO toVO(ExExpenseReport r) {
        ExpenseReportVO vo = new ExpenseReportVO();
        BeanUtils.copyProperties(r, vo);
        try {
            if (r.getApplicantId() != null) {
                Result<SystemUserDTO> ru = systemFeignClient.getUser(r.getApplicantId());
                if (ru.getData() != null) vo.setApplicantName(ru.getData().getRealName());
            }
            if (r.getDepartmentId() != null) {
                Result<SystemDeptDTO> rd = systemFeignClient.getDepartment(r.getDepartmentId());
                if (rd.getData() != null) vo.setDepartmentName(rd.getData().getDeptName());
            }
            if (r.getTravelRequestId() != null) {
                ExTravelRequest travel = travelMapper.selectById(r.getTravelRequestId());
                if (travel != null) vo.setTravelRequestNo(travel.getRequestNo());
            }
        } catch (Exception ignored) {}
        // 加载明细
        List<ExExpenseItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, r.getId()));
        vo.setItems(items.stream().map(i -> {
            ExpenseItemVO iv = new ExpenseItemVO();
            BeanUtils.copyProperties(i, iv);
            return iv;
        }).toList());
        return vo;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/service/
git commit -m "feat(expense): 添加 TravelRequestService + ExpenseReportService (含状态流转/审批Mock/政策校验)"
```

---

### Task 9: Service 层 — Invoice + CostRecord + ExpensePolicy + PaymentRecord

**Files:**
- Create: 4 个 Service 接口 + 4 个 Impl

- [ ] **Step 1: 创建 InvoiceService + Impl（含 OCR Mock）**

InvoiceService:
```java
package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.vo.InvoiceVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;

public interface InvoiceService {
    Result<InvoiceVO> upload(MultipartFile file, String invoiceType);
    Result<Page<InvoiceVO>> page(int page, int size, String ocrStatus);
    Result<InvoiceVO> getById(Long id);
    Result<InvoiceVO> triggerOcr(Long id);
}
```

InvoiceServiceImpl:
```java
package com.expenseflow.expense.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.config.OcrConfig;
import com.expenseflow.expense.entity.ExInvoice;
import com.expenseflow.expense.mapper.ExInvoiceMapper;
import com.expenseflow.expense.service.InvoiceService;
import com.expenseflow.expense.vo.InvoiceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final ExInvoiceMapper invoiceMapper;
    private final OcrConfig ocrConfig;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    @Override
    @Transactional
    public Result<InvoiceVO> upload(MultipartFile file, String invoiceType) {
        if (file.isEmpty()) return Result.fail(400, "文件不能为空");
        if (file.getSize() > 10 * 1024 * 1024) return Result.fail(400, "文件大小不能超过10MB");
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            return Result.fail(400, "仅支持 PNG/JPG/PDF 格式");
        }

        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path dir = Paths.get(ocrConfig.getUploadPath(), "0"); // tenant 0
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());

            ExInvoice inv = new ExInvoice();
            inv.setInvoiceType(invoiceType != null ? invoiceType : "ELECTRONIC");
            inv.setImageUrl(target.toString());
            inv.setOcrStatus("PENDING");
            invoiceMapper.insert(inv);

            // 异步OCR
            doOcr(inv.getId(), target.toFile());

            InvoiceVO vo = new InvoiceVO();
            BeanUtils.copyProperties(inv, vo);
            return Result.ok(vo);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.fail(500, "文件上传失败");
        }
    }

    @Override
    public Result<Page<InvoiceVO>> page(int page, int size, String ocrStatus) {
        LambdaQueryWrapper<ExInvoice> qw = new LambdaQueryWrapper<>();
        if (ocrStatus != null && !ocrStatus.isEmpty()) qw.eq(ExInvoice::getOcrStatus, ocrStatus);
        qw.orderByDesc(ExInvoice::getCreateTime);
        Page<ExInvoice> pg = invoiceMapper.selectPage(new Page<>(page, size), qw);
        Page<InvoiceVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(i -> {
            InvoiceVO vo = new InvoiceVO();
            BeanUtils.copyProperties(i, vo);
            return vo;
        }).toList());
        return Result.ok(voPage);
    }

    @Override
    public Result<InvoiceVO> getById(Long id) {
        ExInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) return Result.fail(404, "发票不存在");
        InvoiceVO vo = new InvoiceVO();
        BeanUtils.copyProperties(inv, vo);
        return Result.ok(vo);
    }

    @Override
    public Result<InvoiceVO> triggerOcr(Long id) {
        ExInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) return Result.fail(404, "发票不存在");
        inv.setOcrStatus("PROCESSING");
        invoiceMapper.updateById(inv);
        doOcr(id, new File(inv.getImageUrl()));
        InvoiceVO vo = new InvoiceVO();
        BeanUtils.copyProperties(inv, vo);
        return Result.ok(vo);
    }

    @Async
    void doOcr(Long invoiceId, File file) {
        ExInvoice inv = invoiceMapper.selectById(invoiceId);
        try {
            // Mock OCR: 模拟识别结果
            Thread.sleep(1500);
            inv.setOcrStatus("SUCCESS");
            inv.setInvoiceNo("MOCK-" + invoiceId);
            inv.setTotalAmount(new BigDecimal("100.00"));
            inv.setAmount(new BigDecimal("94.34"));
            inv.setTaxAmount(new BigDecimal("5.66"));
            inv.setInvoiceDate(LocalDate.now());
            inv.setSellerName("模拟销售方");
            inv.setSellerTaxNo("91310000607335492B");
            inv.setOcrConfidence(new BigDecimal("0.95"));
            inv.setOcrRawResult("{\"mock\": true, \"invoice_id\": " + invoiceId + "}");
            invoiceMapper.updateById(inv);
        } catch (Exception e) {
            log.error("OCR 识别失败", e);
            inv.setOcrStatus("FAILED");
            inv.setOcrRawResult("{\"error\": \"" + e.getMessage() + "\"}");
            invoiceMapper.updateById(inv);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "jpg";
        int i = filename.lastIndexOf('.');
        return i == -1 ? "jpg" : filename.substring(i + 1).toLowerCase();
    }
}
```

- [ ] **Step 2: 创建 CostRecordService + Impl (简单CRUD)**

CostRecordService:
```java
package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.CostRecordDTO;
import com.expenseflow.expense.entity.ExCostRecord;
import com.expenseflow.expense.mapper.ExCostRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CostRecordService {

    private final ExCostRecordMapper costMapper;

    public Result<Page<ExCostRecord>> page(int page, int size, Long userId) {
        LambdaQueryWrapper<ExCostRecord> qw = new LambdaQueryWrapper<>();
        if (userId != null) qw.eq(ExCostRecord::getUserId, userId);
        qw.orderByDesc(ExCostRecord::getCreateTime);
        return Result.ok(costMapper.selectPage(new Page<>(page, size), qw));
    }

    @Transactional
    public Result<ExCostRecord> create(CostRecordDTO dto) {
        ExCostRecord r = new ExCostRecord();
        BeanUtils.copyProperties(dto, r);
        costMapper.insert(r);
        return Result.ok(r);
    }

    @Transactional
    public Result<Void> update(Long id, CostRecordDTO dto) {
        ExCostRecord r = costMapper.selectById(id);
        if (r == null) return Result.fail(404, "消费记录不存在");
        BeanUtils.copyProperties(dto, r);
        r.setId(id);
        costMapper.updateById(r);
        return Result.ok();
    }

    @Transactional
    public Result<Void> delete(Long id) {
        costMapper.deleteById(id);
        return Result.ok();
    }
}
```

- [ ] **Step 3: 创建 ExpensePolicyService + PaymentService (简单CRUD)**

ExpensePolicyService:
```java
package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpensePolicyDTO;
import com.expenseflow.expense.entity.ExExpensePolicy;
import com.expenseflow.expense.mapper.ExExpensePolicyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpensePolicyService {

    private final ExExpensePolicyMapper policyMapper;

    public Result<List<ExExpensePolicy>> list() {
        return Result.ok(policyMapper.selectList(
            new LambdaQueryWrapper<ExExpensePolicy>().orderByDesc(ExExpensePolicy::getCreateTime)));
    }

    @Transactional
    public Result<ExExpensePolicy> create(ExpensePolicyDTO dto) {
        ExExpensePolicy p = new ExExpensePolicy();
        BeanUtils.copyProperties(dto, p);
        p.setStatus(1);
        policyMapper.insert(p);
        return Result.ok(p);
    }

    @Transactional
    public Result<Void> update(Long id, ExpensePolicyDTO dto) {
        ExExpensePolicy p = policyMapper.selectById(id);
        if (p == null) return Result.fail(404, "费用政策不存在");
        BeanUtils.copyProperties(dto, p);
        p.setId(id);
        policyMapper.updateById(p);
        return Result.ok();
    }

    @Transactional
    public Result<Void> delete(Long id) {
        policyMapper.deleteById(id);
        return Result.ok();
    }
}
```

PaymentService:
```java
package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.entity.ExExpenseReport;
import com.expenseflow.expense.entity.ExPaymentRecord;
import com.expenseflow.expense.mapper.ExExpenseReportMapper;
import com.expenseflow.expense.mapper.ExPaymentRecordMapper;
import com.expenseflow.expense.util.NoGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ExPaymentRecordMapper paymentMapper;
    private final ExExpenseReportMapper reportMapper;
    private final NoGenerator noGenerator;

    public Result<Page<ExPaymentRecord>> page(int page, int size) {
        LambdaQueryWrapper<ExPaymentRecord> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(ExPaymentRecord::getCreateTime);
        return Result.ok(paymentMapper.selectPage(new Page<>(page, size), qw));
    }

    @Transactional
    public Result<ExPaymentRecord> pay(Long reportId, Long operatorId) {
        ExExpenseReport report = reportMapper.selectById(reportId);
        if (report == null) return Result.fail(404, "报销单不存在");
        if (!"APPROVED".equals(report.getStatus())) return Result.fail(400, "仅已审批的报销单可打款");

        ExPaymentRecord pr = new ExPaymentRecord();
        pr.setReportId(reportId);
        pr.setPaymentNo(noGenerator.generatePaymentNo());
        pr.setAmount(report.getTotalAmount());
        pr.setPaymentMethod("BANK_TRANSFER");
        pr.setPaymentStatus("SUCCESS");
        pr.setPaymentTime(LocalDateTime.now());
        pr.setOperatorId(operatorId);
        paymentMapper.insert(pr);

        report.setStatus("PAID");
        report.setPaidAmount(report.getTotalAmount());
        report.setPaidTime(LocalDateTime.now());
        reportMapper.updateById(report);

        return Result.ok(pr);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/service/
git commit -m "feat(expense): 添加 Invoice/CostRecord/Policy/Payment Service (含OCR Mock + 打款)"
```

---

### Task 10: Controller 层 — TravelRequest + ExpenseReport + ExpenseItem

**Files:**
- Create: 3 个 Controller

- [ ] **Step 1: 创建 TravelRequestController**

```java
package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.TravelRequestDTO;
import com.expenseflow.expense.service.TravelRequestService;
import com.expenseflow.expense.vo.TravelRequestVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/travel")
@RequiredArgsConstructor
public class TravelRequestController {

    private final TravelRequestService travelService;

    @GetMapping("/page")
    public Result<Page<TravelRequestVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return travelService.page(page, size, userId, status);
    }

    @GetMapping("/{id}")
    public Result<TravelRequestVO> getById(@PathVariable Long id) {
        return travelService.getById(id);
    }

    @PostMapping
    @AuditLog(module = "出差申请", operation = "CREATE")
    public Result<TravelRequestVO> create(@Valid @RequestBody TravelRequestDTO dto) {
        return travelService.create(dto);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "出差申请", operation = "UPDATE")
    public Result<TravelRequestVO> update(@PathVariable Long id, @Valid @RequestBody TravelRequestDTO dto) {
        return travelService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "出差申请", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return travelService.delete(id);
    }

    @PostMapping("/{id}/submit")
    @AuditLog(module = "出差申请", operation = "SUBMIT")
    public Result<TravelRequestVO> submit(@PathVariable Long id) {
        return travelService.submit(id);
    }

    @PostMapping("/{id}/withdraw")
    @AuditLog(module = "出差申请", operation = "WITHDRAW")
    public Result<TravelRequestVO> withdraw(@PathVariable Long id) {
        return travelService.withdraw(id);
    }
}
```

- [ ] **Step 2: 创建 ExpenseReportController + ExpenseItemController**

ExpenseReportController:
```java
package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import com.expenseflow.expense.dto.ExpenseItemDTO;
import com.expenseflow.expense.service.ExpenseReportService;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/report")
@RequiredArgsConstructor
public class ExpenseReportController {

    private final ExpenseReportService reportService;

    @GetMapping("/page")
    public Result<Page<ExpenseReportVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return reportService.page(page, size, userId, status);
    }

    @GetMapping("/{id}")
    public Result<ExpenseReportVO> getById(@PathVariable Long id) {
        return reportService.getById(id);
    }

    @PostMapping
    @AuditLog(module = "报销单", operation = "CREATE")
    public Result<ExpenseReportVO> create(@Valid @RequestBody ExpenseReportDTO dto) {
        return reportService.create(dto);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "报销单", operation = "UPDATE")
    public Result<ExpenseReportVO> update(@PathVariable Long id, @Valid @RequestBody ExpenseReportDTO dto) {
        return reportService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "报销单", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return reportService.delete(id);
    }

    @PostMapping("/{id}/submit")
    @AuditLog(module = "报销单", operation = "SUBMIT")
    public Result<ExpenseReportVO> submit(@PathVariable Long id) {
        return reportService.submit(id);
    }

    @PostMapping("/{id}/withdraw")
    @AuditLog(module = "报销单", operation = "WITHDRAW")
    public Result<ExpenseReportVO> withdraw(@PathVariable Long id) {
        return reportService.withdraw(id);
    }
}
```

ExpenseItemController:
```java
package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpenseItemDTO;
import com.expenseflow.expense.entity.ExExpenseItem;
import com.expenseflow.expense.mapper.ExExpenseItemMapper;
import com.expenseflow.expense.service.ExpenseReportService;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/expense/report/{reportId}/item")
@RequiredArgsConstructor
public class ExpenseItemController {

    private final ExExpenseItemMapper itemMapper;
    private final ExpenseReportService reportService;

    @GetMapping("/list")
    public Result<List<ExExpenseItem>> list(@PathVariable Long reportId) {
        return Result.ok(itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, reportId)));
    }

    @PostMapping
    @AuditLog(module = "报销明细", operation = "ADD")
    public Result<ExpenseReportVO> add(@PathVariable Long reportId, @Valid @RequestBody ExpenseItemDTO dto) {
        return reportService.addItem(reportId, dto);
    }

    @DeleteMapping("/{itemId}")
    @AuditLog(module = "报销明细", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long reportId, @PathVariable Long itemId) {
        itemMapper.deleteById(itemId);
        return Result.ok();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/controller/
git commit -m "feat(expense): 添加 TravelRequest + ExpenseReport + ExpenseItem Controller"
```

---

### Task 11: Controller 层 — Invoice + CostRecord + Policy + Payment

**Files:**
- Create: 4 个 Controller

- [ ] **Step 1: 创建 InvoiceController（含文件上传）**

```java
package com.expenseflow.expense.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.service.InvoiceService;
import com.expenseflow.expense.vo.InvoiceVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/expense/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/upload")
    public Result<InvoiceVO> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(defaultValue = "ELECTRONIC") String invoiceType) {
        return invoiceService.upload(file, invoiceType);
    }

    @GetMapping("/page")
    public Result<Page<InvoiceVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String ocrStatus) {
        return invoiceService.page(page, size, ocrStatus);
    }

    @GetMapping("/{id}")
    public Result<InvoiceVO> getById(@PathVariable Long id) {
        return invoiceService.getById(id);
    }

    @PostMapping("/{id}/ocr")
    public Result<InvoiceVO> triggerOcr(@PathVariable Long id) {
        return invoiceService.triggerOcr(id);
    }
}
```

- [ ] **Step 2: 创建 CostRecordController + ExpensePolicyController + PaymentController**

CostRecordController:
```java
package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.CostRecordDTO;
import com.expenseflow.expense.entity.ExCostRecord;
import com.expenseflow.expense.service.CostRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/cost")
@RequiredArgsConstructor
public class CostRecordController {

    private final CostRecordService costService;

    @GetMapping("/page")
    public Result<Page<ExCostRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return costService.page(page, size, userId);
    }

    @PostMapping
    @AuditLog(module = "消费记录", operation = "CREATE")
    public Result<ExCostRecord> create(@Valid @RequestBody CostRecordDTO dto) {
        return costService.create(dto);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "消费记录", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CostRecordDTO dto) {
        return costService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "消费记录", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return costService.delete(id);
    }
}
```

ExpensePolicyController:
```java
package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpensePolicyDTO;
import com.expenseflow.expense.entity.ExExpensePolicy;
import com.expenseflow.expense.service.ExpensePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/expense/policy")
@RequiredArgsConstructor
public class ExpensePolicyController {

    private final ExpensePolicyService policyService;

    @GetMapping("/list")
    public Result<List<ExExpensePolicy>> list() {
        return policyService.list();
    }

    @PostMapping
    @AuditLog(module = "费用政策", operation = "CREATE")
    public Result<ExExpensePolicy> create(@Valid @RequestBody ExpensePolicyDTO dto) {
        return policyService.create(dto);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "费用政策", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ExpensePolicyDTO dto) {
        return policyService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "费用政策", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return policyService.delete(id);
    }
}
```

PaymentRecordController:
```java
package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.entity.ExPaymentRecord;
import com.expenseflow.expense.service.PaymentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/payment")
@RequiredArgsConstructor
public class PaymentRecordController {

    private final PaymentService paymentService;

    @GetMapping("/page")
    public Result<Page<ExPaymentRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.page(page, size);
    }

    @PostMapping("/pay")
    @AuditLog(module = "打款管理", operation = "PAY")
    public Result<ExPaymentRecord> pay(@RequestParam Long reportId, Authentication auth) {
        Long operatorId = (Long) auth.getPrincipal();
        return paymentService.pay(reportId, operatorId);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/controller/
git commit -m "feat(expense): 添加 Invoice + CostRecord + Policy + Payment Controller"
```

---

### Task 12: expense-service 安全配置 + application.yml

**Files:**
- Create: `expense-service/src/main/java/com/expenseflow/expense/config/SecurityConfig.java`
- Modify: `expense-service/src/main/resources/application.yml`

- [ ] **Step 1: 创建 SecurityConfig（JWT无状态，与system-service一致）**

```java
package com.expenseflow.expense.config;

import com.expenseflow.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

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
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Slf4j
    @Component
    public static class JwtAuthFilter extends OncePerRequestFilter {
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
                List<SimpleGrantedAuthority> authorities = Collections.emptyList();
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
                auth.setDetails(tenantId);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            filterChain.doFilter(request, response);
        }
    }
}
```

- [ ] **Step 2: 更新 application.yml 添加上传路径配置**

在现有 `application.yml` 末尾追加：
```yaml
expense:
  ocr:
    mock: true
    upload-path: ./upload/invoice

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB
```

- [ ] **Step 3: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/config/SecurityConfig.java \
        expense-service/src/main/resources/application.yml
git commit -m "feat(expense): 添加 SecurityConfig (JWT无状态) + 文件上传配置"
```

---

### Task 13: Build + Start + 全链路验证

- [ ] **Step 1: 停旧进程**

```bash
for pid in $(netstat -ano | grep -E ':(8080|8081|8082|8083|8084|8085) ' | awk '{print $NF}' | sort -u); do
  taskkill -PID $pid -F 2>/dev/null
done
```

- [ ] **Step 2: 全量编译**

```bash
cd D:/RecoginitionOCR && mvn clean package -DskipTests
```
Expected: BUILD SUCCESS（8 模块）

- [ ] **Step 3: 启动全部 6 服务**

```bash
cd D:/RecoginitionOCR
nohup java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar > /tmp/gateway.log 2>&1 & disown
nohup java -jar system-service/target/system-service-1.0.0-SNAPSHOT.jar > /tmp/system.log 2>&1 & disown
nohup java -jar expense-service/target/expense-service-1.0.0-SNAPSHOT.jar > /tmp/expense.log 2>&1 & disown
nohup java -jar approval-service/target/approval-service-1.0.0-SNAPSHOT.jar > /tmp/approval.log 2>&1 & disown
nohup java -jar ai-service/target/ai-service-1.0.0-SNAPSHOT.jar > /tmp/ai.log 2>&1 & disown
nohup java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar > /tmp/notification.log 2>&1 & disown
```

等待 70 秒

- [ ] **Step 4: 验证 Health**

```bash
for port in 8080 8081 8082 8083 8084 8085; do
  curl -s --noproxy localhost -w "Port $port: %{http_code}\n" http://localhost:$port/actuator/health
done
```
Expected: 全部 200 UP

- [ ] **Step 5: 验证 Nacos**

```bash
curl -s --noproxy localhost "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10"
```
Expected: count=6

- [ ] **Step 6: 验证出差申请全链路**

```bash
# 1. 登录
TOKEN=$(curl -s --noproxy localhost -X POST http://localhost:8081/system/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 2. 创建出差申请
TRAVEL_RESP=$(curl -s --noproxy localhost -X POST http://localhost:8082/expense/travel \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"travelPurpose":"客户拜访","destination":"上海","startDate":"2026-05-20","endDate":"2026-05-22","estimatedAmount":5000}')
echo "Create: $(echo $TRAVEL_RESP | grep -o '"code":[0-9]*')"

TRAVEL_ID=$(echo $TRAVEL_RESP | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

# 3. 提交
SUBMIT_RESP=$(curl -s --noproxy localhost -X POST "http://localhost:8082/expense/travel/$TRAVEL_ID/submit" \
  -H "Authorization: Bearer $TOKEN")
echo "Submit: $(echo $SUBMIT_RESP | grep -o '"code":[0-9]*')"

# 4. 验证状态变更
curl -s --noproxy localhost "http://localhost:8082/expense/travel/$TRAVEL_ID" \
  -H "Authorization: Bearer $TOKEN" | grep -o '"status":"[^"]*"'
```
Expected: status=APPROVED

- [ ] **Step 7: 验证报销单 + 明细 + 政策校验**

```bash
# 1. 创建报销单
REPORT_RESP=$(curl -s --noproxy localhost -X POST http://localhost:8082/expense/report \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reportDate":"2026-05-25","travelRequestId":'$TRAVEL_ID'}')
REPORT_ID=$(echo $REPORT_RESP | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Report Create: $(echo $REPORT_RESP | grep -o '"code":[0-9]*')"

# 2. 添加明细
curl -s --noproxy localhost -X POST "http://localhost:8082/expense/report/$REPORT_ID/item" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"expenseType":"TRANSPORT","expenseDate":"2026-05-20","amount":1500,"description":"高铁"}'

curl -s --noproxy localhost -X POST "http://localhost:8082/expense/report/$REPORT_ID/item" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"expenseType":"HOTEL","expenseDate":"2026-05-21","amount":2000,"description":"住宿"}'

# 3. 提交
SUBMIT_RESP2=$(curl -s --noproxy localhost -X POST "http://localhost:8082/expense/report/$REPORT_ID/submit" \
  -H "Authorization: Bearer $TOKEN")
echo "Report Submit: $(echo $SUBMIT_RESP2 | grep -o '"code":[0-9]*')"

# 4. 验证总额汇总
curl -s --noproxy localhost "http://localhost:8082/expense/report/$REPORT_ID" \
  -H "Authorization: Bearer $TOKEN" | grep -o '"totalAmount":[0-9.]*'
```
Expected: totalAmount=3500.00

- [ ] **Step 8: 验证发票上传**

```bash
echo "test" > /tmp/test_invoice.png
curl -s --noproxy localhost -X POST http://localhost:8082/expense/invoice/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/test_invoice.png" \
  -F "invoiceType=ELECTRONIC" | grep -o '"code":[0-9]*'
```
Expected: 200

- [ ] **Step 9: 验证无 Token 访问被拒绝**

```bash
curl -s --noproxy localhost -w "\nHTTP %{http_code}\n" http://localhost:8082/expense/travel/page
```
Expected: 401 or 403

- [ ] **Step 10: 验证 Gateway → expense 路由**

```bash
curl -s --noproxy localhost -w "\nHTTP %{http_code}\n" http://localhost:8080/expense/policy/list \
  -H "Authorization: Bearer $TOKEN"
```
Expected: 200

- [ ] **Step 11: Commit 验证结果**

```bash
git commit --allow-empty -m "test(m3): 全部 6 服务 + expense 全链路验证通过 — 出差申请/报销单/明细/发票/政策/打款/Feign"
```
