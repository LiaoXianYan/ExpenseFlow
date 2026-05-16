# P1-2 Drools 规则库扩充 — 设计文档

> 版本：v1.0 | 日期：2026-05-16 | 状态：已确认，待实施

## 背景

当前 Drools 仅有 3 条金额阈值规则（>5000 总监审批、>10000 警告、>20000 复核），RuleInput 仅含 `businessType` + `amount`。P1 目标是将规则引擎深度融入审批流程，增加费用类型合规检查、重复报销拦截、异常行为识别。

## 架构方案：多文件分治（方案 B）

DroolsConfig 已使用 `classpath:rules/*.drl` 通配扫描，天然支持多 DRL 文件。按关注点拆为 4 个文件：

| DRL 文件 | 规则数 | 职责 |
|---|---|---|
| `amount-threshold.drl` | 3 | 金额阈值：总监审批/警告/复核（现有，不改）|
| `type-compliance.drl` | 3 | 费用项 vs 政策标准匹配 |
| `duplicate-detection.drl` | 3 | 发票/金额/消费记录重复拦截 |
| `anomaly-detection.drl` | 3 | 金额异常/频次异常/政策上限 |

## DTO 设计

### RuleInput

```java
@Data
public class RuleInput {
    // 现有
    private String businessType;      // TRAVEL_REQUEST / EXPENSE_REPORT
    private double amount;

    // 新增
    private List<ExpenseItemInput> items;
    private List<InvoiceInput> invoices;
    private ApplicantHistory history;
    private List<PolicyInput> policies;
}

@Data
public class ExpenseItemInput {
    private String expenseType;     // TRANSPORT/HOTEL/MEAL/OTHER
    private double amount;
    private LocalDate expenseDate;
}

@Data
public class InvoiceInput {
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private double amount;
    private String sellerName;
}

@Data
public class ApplicantHistory {
    private Long applicantId;
    private int recentReportCount;          // 近 30 天报销单数
    private double avgAmount;               // 历史报销均值
    private List<String> usedInvoiceNos;    // 历史已用发票号
    private List<Long> usedCostRecordIds;   // 历史已关联消费记录 ID
}

@Data
public class PolicyInput {
    private String expenseType;
    private double maxAmount;
    private double dailyLimit;      // 可空
    private String cityTier;        // 可空
}
```

### RuleOutput

```java
@Data
public class RuleOutput {
    // 现有
    private boolean needDirector;
    private List<String> warnings = new ArrayList<>();

    // 新增：结构化违规项
    private List<Violation> violations = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class Violation {
        private String type;        // TYPE_MISMATCH / DUPLICATE / ANOMALY / POLICY_VIOLATION
        private String message;
        private String severity;    // BLOCK / WARN
    }
}
```

## DRL 规则详情

### amount-threshold.drl（现有改名）

| # | 规则名 | 条件 | 动作 |
|---|--------|------|------|
| 1 | High amount travel request needs director | TRAVEL_REQUEST, amount > 5000 | needDirector=true |
| 2 | High amount expense report warning | EXPENSE_REPORT, amount > 10000 | warnings.add |
| 3 | Very high amount needs extra review | EXPENSE_REPORT, amount > 20000 | warnings.add |

### type-compliance.drl（新增）

| # | 规则名 | 条件 | 动作 |
|---|--------|------|------|
| 4 | Expense type not in policy | item.expenseType 不在任何 policy.expenseType 中 | violation: TYPE_MISMATCH / BLOCK |
| 5 | Item exceeds max amount | item.amount > policy.maxAmount（同类型）| violation: POLICY_VIOLATION / BLOCK |
| 6 | Daily limit exceeded | 同 expenseType + 同日期 sum > policy.dailyLimit | violation: POLICY_VIOLATION / WARN |

### duplicate-detection.drl（新增）

| # | 规则名 | 条件 | 动作 |
|---|--------|------|------|
| 7 | Duplicate invoice number | invoice.invoiceNo in history.usedInvoiceNos | violation: DUPLICATE / BLOCK |
| 8 | Duplicate by amount+date+vendor | (amount, invoiceDate, sellerName) 三元组匹配历史 | violation: DUPLICATE / WARN |
| 9 | Cost record already used | costRecordId in history.usedCostRecordIds | violation: DUPLICATE / BLOCK |

### anomaly-detection.drl（新增）

| # | 规则名 | 条件 | 动作 |
|---|--------|------|------|
| 10 | Amount deviates from average | item.amount > history.avgAmount × 3 | violation: ANOMALY / WARN |
| 11 | High frequency reports | history.recentReportCount ≥ 5（近 30 天）| violation: ANOMALY / WARN |
| 12 | Split report suspicion | 高频 + 本次金额 < 历史均值 | violation: ANOMALY / WARN |

## Service 层改动

### DroolsRuleService

- 方法签名改为 `evaluate(RuleInput input)`
- session 中插入 RuleInput、子对象列表、history、policies、RuleOutput
- Java fallback 保持与 DRL 规则等价
- 将现有 `evaluate(String, BigDecimal)` 标记 `@Deprecated`

### ApprovalProcessServiceImpl

- 调用 evaluate 前，通过 Feign 查询组装 RuleInput：
  1. expenseItems（按 report_id）
  2. invoices（按 report_id）
  3. applicantHistory（按 applicant_id，近 30 天统计）
  4. policies（按 tenant_id + 涉及的 expenseTypes）
- 将 violations 写入 Flowable 流程变量
- BLOCK 级违规 → 拒绝启动流程，返回违规原因

### Feign 接口新增（expense-service 提供）

| 接口 | 用途 |
|------|------|
| `GET /expense/reports/{id}/items` | 获取报销单费用项 |
| `GET /expense/reports/{id}/invoices` | 获取报销单关联发票 |
| `GET /expense/applicants/{id}/history` | 获取申请人历史摘要 |
| `GET /expense/policies` | 按 tenant + expenseTypes 查政策 |

## 测试策略

### 单元测试（DroolsRuleServiceTest）

Java fallback 模式，16 个测试覆盖全部 12 条规则：

- 5 个现有测试保留（金额阈值 3 + 边界 2）
- 3 个类型合规测试
- 3 个重复检测测试
- 3 个异常检测测试
- 2 个组合场景测试（多违规同时触发）

### 验证标准

1. `mvn test` approval-service 全量通过
2. Docker 启动全服务，创建含违规项的报销单，验证 BLOCK 拦截
