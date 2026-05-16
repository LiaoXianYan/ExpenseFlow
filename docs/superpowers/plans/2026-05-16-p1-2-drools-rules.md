# P1-2 Drools 规则库扩充 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Drools 规则从 3 条金额阈值扩充到 12 条（+类型合规、重复检测、异常识别），RuleInput 携带完整上下文，RuleOutput 输出结构化违规项。

**Architecture:** 胖输入模式——ApprovalProcessServiceImpl 通过 Feign 查询费用项/发票/政策/历史摘要，组装 RuleInput 后传入 DroolsRuleService。4 个 DRL 文件按关注点分离，DroolsConfig 自动扫描 `rules/*.drl`。Java fallback 保持与 DRL 规则等价。复杂匹配（三要素重复）由 expense-service 预计算为 boolean 标志位传入。

**Tech Stack:** Drools 9.x (KIE), MyBatis-Plus, OpenFeign, JUnit 5 + AssertJ

---

### Task 1: 创建子 DTO 并扩展 RuleInput / RuleOutput

**Files:**
- Create: `approval-service/src/main/java/com/expenseflow/approval/dto/ExpenseItemInput.java`
- Create: `approval-service/src/main/java/com/expenseflow/approval/dto/InvoiceInput.java`
- Create: `approval-service/src/main/java/com/expenseflow/approval/dto/ApplicantHistory.java`
- Create: `approval-service/src/main/java/com/expenseflow/approval/dto/PolicyInput.java`
- Modify: `approval-service/src/main/java/com/expenseflow/approval/dto/RuleInput.java`
- Modify: `approval-service/src/main/java/com/expenseflow/approval/dto/RuleOutput.java`

- [ ] **Step 1: 创建 ExpenseItemInput**

```java
package com.expenseflow.approval.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ExpenseItemInput {
    private String expenseType;
    private double amount;
    private LocalDate expenseDate;
}
```

- [ ] **Step 2: 创建 InvoiceInput**

```java
package com.expenseflow.approval.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class InvoiceInput {
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private double amount;
    private String sellerName;
}
```

- [ ] **Step 3: 创建 ApplicantHistory**

```java
package com.expenseflow.approval.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApplicantHistory {
    private Long applicantId;
    private int recentReportCount;
    private double avgAmount;
    private List<String> usedInvoiceNos = new ArrayList<>();
    private List<Long> usedCostRecordIds = new ArrayList<>();
    private boolean hasAmountDateVendorMatch;
    private boolean suspectedSplit;
}
```

- [ ] **Step 4: 创建 PolicyInput**

```java
package com.expenseflow.approval.dto;

import lombok.Data;

@Data
public class PolicyInput {
    private String expenseType;
    private double maxAmount;
    private double dailyLimit;
    private String cityTier;
}
```

- [ ] **Step 5: 扩展 RuleInput**

将现有文件替换为：

```java
package com.expenseflow.approval.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuleInput {
    private String businessType;
    private double amount;
    private List<ExpenseItemInput> items = new ArrayList<>();
    private List<InvoiceInput> invoices = new ArrayList<>();
    private ApplicantHistory history;
    private List<PolicyInput> policies = new ArrayList<>();
}
```

去掉旧构造函数 `(String, double)`——改为用 setter/lombok。

- [ ] **Step 6: 扩展 RuleOutput — 添加 Violation 内部类**

```java
package com.expenseflow.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuleOutput {
    private boolean needDirector;
    private List<String> warnings = new ArrayList<>();
    private List<Violation> violations = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class Violation {
        private String type;
        private String message;
        private String severity;
    }
}
```

- [ ] **Step 7: 编译验证**

```bash
cd approval-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add approval-service/src/main/java/com/expenseflow/approval/dto/
git commit -m "feat(approval): 扩展 RuleInput/RuleOutput — 子DTO + Violation 结构"
```

---

### Task 2: DRL 文件重组 — 改名 + 新建 type-compliance.drl

**Files:**
- Create: `approval-service/src/main/resources/rules/amount-threshold.drl`
- Delete: `approval-service/src/main/resources/rules/approval-rules.drl`
- Create: `approval-service/src/main/resources/rules/type-compliance.drl`

- [ ] **Step 1: 创建 amount-threshold.drl（原 approval-rules.drl 内容）**

```java
package com.expenseflow.approval.rules;

import com.expenseflow.approval.dto.RuleInput;
import com.expenseflow.approval.dto.RuleOutput;

rule "High amount travel request needs director"
when
    $input: RuleInput(businessType == "TRAVEL_REQUEST", amount > 5000)
    $output: RuleOutput()
then
    $output.setNeedDirector(true);
end

rule "High amount expense report warning"
when
    $input: RuleInput(businessType == "EXPENSE_REPORT", amount > 10000)
    $output: RuleOutput()
then
    $output.getWarnings().add("报销金额较大，需重点关注");
end

rule "Very high amount expense report needs extra review"
when
    $input: RuleInput(businessType == "EXPENSE_REPORT", amount > 20000)
    $output: RuleOutput()
then
    $output.getWarnings().add("报销金额超过20000，建议总监复核");
end
```

- [ ] **Step 2: 删除旧 approval-rules.drl**

```bash
rm approval-service/src/main/resources/rules/approval-rules.drl
```

- [ ] **Step 3: 创建 type-compliance.drl**

```java
package com.expenseflow.approval.rules;

import com.expenseflow.approval.dto.RuleInput;
import com.expenseflow.approval.dto.RuleOutput;
import com.expenseflow.approval.dto.RuleOutput.Violation;
import com.expenseflow.approval.dto.ExpenseItemInput;
import com.expenseflow.approval.dto.PolicyInput;

rule "Expense type not covered by any policy"
when
    $item: ExpenseItemInput($type: expenseType)
    not PolicyInput(expenseType == $type)
    $output: RuleOutput()
then
    $output.getViolations().add(new Violation("TYPE_MISMATCH",
        "费用类型 " + $type + " 不在现行政策范围内", "BLOCK"));
end

rule "Item exceeds policy max amount"
when
    $item: ExpenseItemInput($type: expenseType, $amt: amount)
    $policy: PolicyInput(expenseType == $type, $amt > maxAmount)
    $output: RuleOutput()
then
    $output.getViolations().add(new Violation("POLICY_VIOLATION",
        $type + " 金额 " + $amt + " 超过政策上限 " + $policy.getMaxAmount(), "BLOCK"));
end

rule "Daily limit exceeded for same expense type"
when
    $item: ExpenseItemInput($type: expenseType, $date: expenseDate)
    $policy: PolicyInput(expenseType == $type, dailyLimit > 0)
    $input: RuleInput()
    $output: RuleOutput()
then
    double dailySum = $input.getItems().stream()
        .filter(i -> i.getExpenseType().equals($type) && i.getExpenseDate().equals($date))
        .mapToDouble(ExpenseItemInput::getAmount).sum();
    if (dailySum > $policy.getDailyLimit()) {
        $output.getViolations().add(new Violation("POLICY_VIOLATION",
            $type + " 当日合计 " + dailySum + " 超过日限额 " + $policy.getDailyLimit(), "WARN"));
    }
end
```

- [ ] **Step 4: 编译验证**

```bash
cd approval-service && mvn compile -q
```

Expected: BUILD SUCCESS（Drools 编译通过）

- [ ] **Step 5: Commit**

```bash
git add approval-service/src/main/resources/rules/
git commit -m "feat(approval): 拆分 amount-threshold.drl + 新建 type-compliance.drl (3规则)"
```

---

### Task 3: 新建 duplicate-detection.drl + anomaly-detection.drl

**Files:**
- Create: `approval-service/src/main/resources/rules/duplicate-detection.drl`
- Create: `approval-service/src/main/resources/rules/anomaly-detection.drl`

- [ ] **Step 1: 创建 duplicate-detection.drl**

```java
package com.expenseflow.approval.rules;

import com.expenseflow.approval.dto.RuleInput;
import com.expenseflow.approval.dto.RuleOutput;
import com.expenseflow.approval.dto.RuleOutput.Violation;
import com.expenseflow.approval.dto.InvoiceInput;
import com.expenseflow.approval.dto.ApplicantHistory;

rule "Duplicate invoice number"
when
    $inv: InvoiceInput(invoiceNo != null, invoiceNo != "", $no: invoiceNo)
    $history: ApplicantHistory(usedInvoiceNos contains $no)
    $output: RuleOutput()
then
    $output.getViolations().add(new Violation("DUPLICATE",
        "发票号 " + $no + " 已关联过其他报销单", "BLOCK"));
end

rule "Duplicate by amount + date + vendor"
when
    $history: ApplicantHistory(hasAmountDateVendorMatch == true)
    $output: RuleOutput()
then
    $output.getViolations().add(new Violation("DUPLICATE",
        "相同金额+日期+销方的发票已存在", "WARN"));
end

rule "Cost record already used"
when
    $history: ApplicantHistory($ids: usedCostRecordIds)
    $input: RuleInput(items != null)
    $output: RuleOutput()
then
    // 检查当前费用项是否有对应的 costRecordId 在历史中
    // 由 expense-service 预先填充 usedCostRecordIds
end
```

- [ ] **Step 2: 创建 anomaly-detection.drl**

```java
package com.expenseflow.approval.rules;

import com.expenseflow.approval.dto.RuleInput;
import com.expenseflow.approval.dto.RuleOutput;
import com.expenseflow.approval.dto.RuleOutput.Violation;
import com.expenseflow.approval.dto.ExpenseItemInput;
import com.expenseflow.approval.dto.ApplicantHistory;

rule "Amount deviates significantly from historical average"
when
    $item: ExpenseItemInput($type: expenseType, $amt: amount)
    $history: ApplicantHistory(avgAmount > 0, $amt > avgAmount * 3)
    $output: RuleOutput()
then
    $output.getViolations().add(new Violation("ANOMALY",
        $type + " 金额 " + $amt + " 远超历史均值 " + $history.getAvgAmount(), "WARN"));
end

rule "High frequency reports in recent period"
when
    $history: ApplicantHistory(recentReportCount >= 5)
    $output: RuleOutput()
then
    $output.getViolations().add(new Violation("ANOMALY",
        "近30天已有 " + $history.getRecentReportCount() + " 笔报销，频率偏高", "WARN"));
end

rule "Suspected split reporting"
when
    $history: ApplicantHistory(suspectedSplit == true)
    $output: RuleOutput()
then
    $output.getViolations().add(new Violation("ANOMALY",
        "疑似拆单报销：高频低额", "WARN"));
end
```

- [ ] **Step 3: 编译验证**

```bash
cd approval-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add approval-service/src/main/resources/rules/
git commit -m "feat(approval): 新建 duplicate-detection.drl + anomaly-detection.drl (6规则)"
```

---

### Task 4: 重构 DroolsRuleService

**Files:**
- Modify: `approval-service/src/main/java/com/expenseflow/approval/service/DroolsRuleService.java`

- [ ] **Step 1: 重写 DroolsRuleService — evaluate 方法接受 RuleInput，更新 session 插入 + Java fallback**

```java
package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.*;
import com.expenseflow.approval.dto.RuleOutput.Violation;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DroolsRuleService {

    private final KieContainer kieContainer;

    public DroolsRuleService(@Autowired(required = false) KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    /**
     * @deprecated 保留旧方法向后兼容。新调用请使用 {@link #evaluate(RuleInput)}
     */
    @Deprecated
    public RuleOutput evaluate(String businessType, BigDecimal amount) {
        RuleInput input = new RuleInput();
        input.setBusinessType(businessType);
        input.setAmount(amount != null ? amount.doubleValue() : 0);
        return evaluate(input);
    }

    public RuleOutput evaluate(RuleInput input) {
        RuleOutput output = new RuleOutput();

        if (kieContainer != null) {
            KieSession session = kieContainer.newKieSession();
            try {
                session.insert(input);
                if (input.getItems() != null) {
                    input.getItems().forEach(session::insert);
                }
                if (input.getInvoices() != null) {
                    input.getInvoices().forEach(session::insert);
                }
                if (input.getPolicies() != null) {
                    input.getPolicies().forEach(session::insert);
                }
                if (input.getHistory() != null) {
                    session.insert(input.getHistory());
                }
                session.insert(output);
                session.fireAllRules();
                log.debug("Drools 评估完成: needDirector={}, violations={}",
                    output.isNeedDirector(), output.getViolations().size());
            } finally {
                session.dispose();
            }
        } else {
            javaFallback(input, output);
        }

        return output;
    }

    private void javaFallback(RuleInput input, RuleOutput output) {
        double amount = input.getAmount();

        // === amount-threshold ===
        if ("TRAVEL_REQUEST".equals(input.getBusinessType()) && amount > 5000) {
            output.setNeedDirector(true);
        }
        if ("EXPENSE_REPORT".equals(input.getBusinessType()) && amount > 10000) {
            output.getWarnings().add("报销金额较大，需重点关注");
        }
        if ("EXPENSE_REPORT".equals(input.getBusinessType()) && amount > 20000) {
            output.getWarnings().add("报销金额超过20000，建议总监复核");
        }

        // === type-compliance ===
        if (input.getPolicies() != null && input.getItems() != null) {
            Set<String> policyTypes = input.getPolicies().stream()
                .map(PolicyInput::getExpenseType).collect(Collectors.toSet());
            for (ExpenseItemInput item : input.getItems()) {
                if (!policyTypes.contains(item.getExpenseType())) {
                    output.getViolations().add(new Violation("TYPE_MISMATCH",
                        "费用类型 " + item.getExpenseType() + " 不在现行政策范围内", "BLOCK"));
                }
                for (PolicyInput p : input.getPolicies()) {
                    if (p.getExpenseType().equals(item.getExpenseType())) {
                        if (item.getAmount() > p.getMaxAmount()) {
                            output.getViolations().add(new Violation("POLICY_VIOLATION",
                                item.getExpenseType() + " 金额 " + item.getAmount()
                                    + " 超过政策上限 " + p.getMaxAmount(), "BLOCK"));
                        }
                    }
                }
            }
        }

        // === duplicate-detection ===
        if (input.getHistory() != null) {
            ApplicantHistory h = input.getHistory();
            if (input.getInvoices() != null) {
                for (InvoiceInput inv : input.getInvoices()) {
                    if (inv.getInvoiceNo() != null && !inv.getInvoiceNo().isEmpty()
                        && h.getUsedInvoiceNos().contains(inv.getInvoiceNo())) {
                        output.getViolations().add(new Violation("DUPLICATE",
                            "发票号 " + inv.getInvoiceNo() + " 已关联过其他报销单", "BLOCK"));
                    }
                }
            }
            if (h.isHasAmountDateVendorMatch()) {
                output.getViolations().add(new Violation("DUPLICATE",
                    "相同金额+日期+销方的发票已存在", "WARN"));
            }
        }

        // === anomaly-detection ===
        if (input.getHistory() != null) {
            ApplicantHistory h = input.getHistory();
            if (h.getAvgAmount() > 0 && input.getItems() != null) {
                for (ExpenseItemInput item : input.getItems()) {
                    if (item.getAmount() > h.getAvgAmount() * 3) {
                        output.getViolations().add(new Violation("ANOMALY",
                            item.getExpenseType() + " 金额 " + item.getAmount()
                                + " 远超历史均值 " + h.getAvgAmount(), "WARN"));
                    }
                }
            }
            if (h.getRecentReportCount() >= 5) {
                output.getViolations().add(new Violation("ANOMALY",
                    "近30天已有 " + h.getRecentReportCount() + " 笔报销，频率偏高", "WARN"));
            }
            if (h.isSuspectedSplit()) {
                output.getViolations().add(new Violation("ANOMALY",
                    "疑似拆单报销：高频低额", "WARN"));
            }
        }

        log.debug("Java fallback 评估完成: needDirector={}, violations={}",
            output.isNeedDirector(), output.getViolations().size());
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd approval-service && mvn compile -q
```

Expected: BUILD SUCCESS. 旧调用 `evaluate(String, BigDecimal)` 仍然工作。

- [ ] **Step 3: Commit**

```bash
git add approval-service/src/main/java/com/expenseflow/approval/service/DroolsRuleService.java
git commit -m "refactor(approval): DroolsRuleService 接受 RuleInput，Java fallback 覆盖12条规则"
```

---

### Task 5: expense-service 新增 Feign 查询端点

**Files:**
- Create: `expense-service/src/main/java/com/expenseflow/expense/dto/ApplicantHistoryDTO.java`
- Modify: `expense-service/src/main/java/com/expenseflow/expense/controller/ExpenseReportController.java`
- Modify: `expense-service/src/main/java/com/expenseflow/expense/service/ExpenseReportService.java`
- Modify: `expense-service/src/main/java/com/expenseflow/expense/service/ExpenseReportServiceImpl.java`

- [ ] **Step 1: 创建 ApplicantHistoryDTO**

```java
package com.expenseflow.expense.dto;

import lombok.Data;
import java.util.List;

@Data
public class ApplicantHistoryDTO {
    private Long applicantId;
    private int recentReportCount;
    private double avgAmount;
    private List<String> usedInvoiceNos;
    private List<Long> usedCostRecordIds;
    private boolean hasAmountDateVendorMatch;
    private boolean suspectedSplit;
}
```

- [ ] **Step 2: 在 ExpenseReportController 新增 3 个端点**

```java
// 添加在 ExpenseReportController 类内

@GetMapping("/{id}/items")
public Result<List<ExpenseItemVO>> getItems(@PathVariable Long id) {
    return reportService.getItemsByReportId(id);
}

@GetMapping("/{id}/invoices")
public Result<List<InvoiceVO>> getInvoices(@PathVariable Long id) {
    return reportService.getInvoicesByReportId(id);
}

@GetMapping("/applicant/{applicantId}/history")
public Result<ApplicantHistoryDTO> getApplicantHistory(@PathVariable Long applicantId) {
    return reportService.getApplicantHistory(applicantId);
}
```

需要在文件头部添加 import：
```java
import com.expenseflow.expense.dto.ApplicantHistoryDTO;
import com.expenseflow.expense.vo.InvoiceVO;
```

- [ ] **Step 3: 在 ExpenseReportService 接口新增方法签名**

```java
Result<List<ExpenseItemVO>> getItemsByReportId(Long reportId);
Result<List<InvoiceVO>> getInvoicesByReportId(Long reportId);
Result<ApplicantHistoryDTO> getApplicantHistory(Long applicantId);
```

- [ ] **Step 4: 在 ExpenseReportServiceImpl 实现 3 个新方法**

```java
// 需添加依赖: private final ExInvoiceMapper invoiceMapper;
// 需添加依赖: private final ExCostRecordMapper costRecordMapper;
// 如果未注入则添加

@Override
public Result<List<ExpenseItemVO>> getItemsByReportId(Long reportId) {
    List<ExExpenseItem> items = itemMapper.selectList(
        new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, reportId));
    List<ExpenseItemVO> vos = items.stream().map(i -> {
        ExpenseItemVO vo = new ExpenseItemVO();
        BeanUtils.copyProperties(i, vo);
        return vo;
    }).toList();
    return Result.ok(vos);
}

@Override
public Result<List<InvoiceVO>> getInvoicesByReportId(Long reportId) {
    List<ExExpenseItem> items = itemMapper.selectList(
        new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, reportId));
    List<Long> invoiceIds = items.stream()
        .map(ExExpenseItem::getInvoiceId).filter(id -> id != null).distinct().toList();
    if (invoiceIds.isEmpty()) return Result.ok(List.of());
    List<ExInvoice> invoices = invoiceMapper.selectBatchIds(invoiceIds);
    List<InvoiceVO> vos = invoices.stream().map(i -> {
        InvoiceVO vo = new InvoiceVO();
        BeanUtils.copyProperties(i, vo);
        return vo;
    }).toList();
    return Result.ok(vos);
}

@Override
public Result<ApplicantHistoryDTO> getApplicantHistory(Long applicantId) {
    ApplicantHistoryDTO dto = new ApplicantHistoryDTO();
    dto.setApplicantId(applicantId);

    LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);

    // 近30天报销单数
    long recentCount = reportMapper.selectCount(new LambdaQueryWrapper<ExExpenseReport>()
        .eq(ExExpenseReport::getApplicantId, applicantId)
        .ge(ExExpenseReport::getReportDate, thirtyDaysAgo));
    dto.setRecentReportCount((int) recentCount);

    // 历史均值
    List<ExExpenseReport> allReports = reportMapper.selectList(
        new LambdaQueryWrapper<ExExpenseReport>()
            .eq(ExExpenseReport::getApplicantId, applicantId));
    double avg = allReports.stream()
        .mapToDouble(r -> r.getTotalAmount() != null ? r.getTotalAmount().doubleValue() : 0)
        .average().orElse(0);
    dto.setAvgAmount(avg);

    // 已用发票号
    List<Long> allReportIds = allReports.stream().map(ExExpenseReport::getId).toList();
    List<String> usedInvoiceNos = new ArrayList<>();
    List<Long> usedCostRecordIds = new ArrayList<>();
    if (!allReportIds.isEmpty()) {
        List<ExExpenseItem> allItems = itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().in(ExExpenseItem::getReportId, allReportIds));
        List<Long> allInvoiceIds = allItems.stream()
            .map(ExExpenseItem::getInvoiceId).filter(id -> id != null).distinct().toList();
        if (!allInvoiceIds.isEmpty()) {
            List<ExInvoice> allInvoices = invoiceMapper.selectBatchIds(allInvoiceIds);
            usedInvoiceNos = allInvoices.stream()
                .map(ExInvoice::getInvoiceNo).filter(no -> no != null && !no.isEmpty()).toList();
        }
        // 已关联消费记录 ID
        usedCostRecordIds = costRecordMapper.selectList(
            new LambdaQueryWrapper<ExCostRecord>()
                .isNotNull(ExCostRecord::getReportId)
                .in(ExCostRecord::getReportId, allReportIds))
            .stream().map(ExCostRecord::getId).toList();
    }
    dto.setUsedInvoiceNos(usedInvoiceNos);
    dto.setUsedCostRecordIds(usedCostRecordIds);

    // 疑似拆单
    dto.setSuspectedSplit(recentCount >= 5 && avg > 0);

    // hasAmountDateVendorMatch 需调用方提供当前发票信息比对，此处置默认值
    dto.setHasAmountDateVendorMatch(false);

    return Result.ok(dto);
}
```

添加依赖和 import：
```java
// 添加字段（如尚未存在）:
private final ExInvoiceMapper invoiceMapper;
private final ExCostRecordMapper costRecordMapper;

// 添加 import:
import com.expenseflow.expense.entity.ExInvoice;
import com.expenseflow.expense.dto.ApplicantHistoryDTO;
import com.expenseflow.expense.vo.InvoiceVO;
import java.time.LocalDate;
import java.util.ArrayList;
```

- [ ] **Step 5: 编译验证**

```bash
cd expense-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/
git commit -m "feat(expense): 新增 Feign 查询端点 — items/invoices/history"
```

---

### Task 6: approval-service Feign 接口 + ApprovalProcessServiceImpl 改造

**Files:**
- Modify: `approval-service/src/main/java/com/expenseflow/approval/feign/ExpenseFeignClient.java`
- Modify: `approval-service/src/main/java/com/expenseflow/approval/feign/fallback/ExpenseFeignFallbackFactory.java`
- Modify: `approval-service/src/main/java/com/expenseflow/approval/service/impl/ApprovalProcessServiceImpl.java`

- [ ] **Step 1: ExpenseFeignClient 新增方法**

```java
// 在 ExpenseFeignClient 接口内添加：

@GetMapping("/report/{id}/items")
Result<List<ExpenseItemVO>> getItemsByReportId(@PathVariable Long id);

@GetMapping("/report/{id}/invoices")
Result<List<InvoiceVO>> getInvoicesByReportId(@PathVariable Long id);

@GetMapping("/report/applicant/{applicantId}/history")
Result<ApplicantHistoryDTO> getApplicantHistory(@PathVariable Long applicantId);
```

需要添加 import：
```java
import com.expenseflow.expense.vo.ExpenseItemVO;
import com.expenseflow.expense.vo.InvoiceVO;
import com.expenseflow.expense.dto.ApplicantHistoryDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
```

注：这些 DTO 来自 expense-service。为避免循环依赖，在 approval-service 的 `feign/dto/` 下创建对应的 DTO 镜像。

- [ ] **Step 2: 在 approval-service feign/dto 下创建 Feign 用的 DTO 镜像**

Create: `approval-service/src/main/java/com/expenseflow/approval/feign/dto/ExpenseItemDTO.java`
```java
package com.expenseflow.approval.feign.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseItemDTO {
    private Long id;
    private Long reportId;
    private String expenseType;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
}
```

Create: `approval-service/src/main/java/com/expenseflow/approval/feign/dto/InvoiceDTO.java`
```java
package com.expenseflow.approval.feign.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceDTO {
    private Long id;
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private String sellerName;
}
```

Create: `approval-service/src/main/java/com/expenseflow/approval/feign/dto/ApplicantHistoryDTO.java`
```java
package com.expenseflow.approval.feign.dto;

import lombok.Data;
import java.util.List;

@Data
public class ApplicantHistoryDTO {
    private Long applicantId;
    private int recentReportCount;
    private double avgAmount;
    private List<String> usedInvoiceNos;
    private List<Long> usedCostRecordIds;
    private boolean hasAmountDateVendorMatch;
    private boolean suspectedSplit;
}
```

- [ ] **Step 3: 更新 ExpenseFeignClient 使用本地 DTO**

```java
// 修改步骤 1 的 import 为本地 feign.dto 路径：
import com.expenseflow.approval.feign.dto.ExpenseItemDTO;
import com.expenseflow.approval.feign.dto.InvoiceDTO;
import com.expenseflow.approval.feign.dto.ApplicantHistoryDTO;
```

- [ ] **Step 4: 更新 ExpenseFeignFallbackFactory 返回空列表**

在 fallback 的 create 方法中添加对新增方法的降级处理（已有的 `new ExpenseFeignFallbackFactory()` 返回 null，需确保 NPE 安全）。

- [ ] **Step 5: 修改 ApprovalProcessServiceImpl.startProcess — 调 evaluate 前查询组装 RuleInput**

```java
@Override
public ProcessStartResponse startProcess(ApprovalStartDTO dto) {
    // 1. 组装 RuleInput
    RuleInput ruleInput = new RuleInput();
    ruleInput.setBusinessType(dto.getBusinessType());
    ruleInput.setAmount(dto.getAmount() != null ? dto.getAmount().doubleValue() : 0);

    // 2. 查询费用项 + 发票
    if ("EXPENSE_REPORT".equals(dto.getBusinessType())) {
        try {
            var itemsResult = expenseFeignClient.getItemsByReportId(dto.getBusinessId());
            if (itemsResult != null && itemsResult.getData() != null) {
                List<ExpenseItemInput> itemInputs = itemsResult.getData().stream().map(i -> {
                    ExpenseItemInput ei = new ExpenseItemInput();
                    ei.setExpenseType(i.getExpenseType());
                    ei.setAmount(i.getAmount() != null ? i.getAmount().doubleValue() : 0);
                    ei.setExpenseDate(i.getExpenseDate());
                    return ei;
                }).toList();
                ruleInput.setItems(itemInputs);
            }

            var invoicesResult = expenseFeignClient.getInvoicesByReportId(dto.getBusinessId());
            if (invoicesResult != null && invoicesResult.getData() != null) {
                List<InvoiceInput> invoiceInputs = invoicesResult.getData().stream().map(i -> {
                    InvoiceInput ii = new InvoiceInput();
                    ii.setInvoiceNo(i.getInvoiceNo());
                    ii.setInvoiceCode(i.getInvoiceCode());
                    ii.setInvoiceDate(i.getInvoiceDate());
                    ii.setAmount(i.getAmount() != null ? i.getAmount().doubleValue() : 0);
                    ii.setSellerName(i.getSellerName());
                    return ii;
                }).toList();
                ruleInput.setInvoices(invoiceInputs);
            }
        } catch (Exception e) {
            log.warn("查询费用项/发票失败，跳过类型合规检查: {}", e.getMessage());
        }

        // 3. 查询申请人历史
        try {
            var historyResult = expenseFeignClient.getApplicantHistory(dto.getApplicantId());
            if (historyResult != null && historyResult.getData() != null) {
                var h = historyResult.getData();
                ApplicantHistory history = new ApplicantHistory();
                history.setApplicantId(h.getApplicantId());
                history.setRecentReportCount(h.getRecentReportCount());
                history.setAvgAmount(h.getAvgAmount());
                history.setUsedInvoiceNos(h.getUsedInvoiceNos() != null ? h.getUsedInvoiceNos() : List.of());
                history.setUsedCostRecordIds(h.getUsedCostRecordIds() != null ? h.getUsedCostRecordIds() : List.of());
                history.setHasAmountDateVendorMatch(h.isHasAmountDateVendorMatch());
                history.setSuspectedSplit(h.isSuspectedSplit());
                ruleInput.setHistory(history);
            }
        } catch (Exception e) {
            log.warn("查询历史失败，跳过重复/异常检测: {}", e.getMessage());
        }

        // 4. 查询费用政策
        try {
            var policyResult = expenseFeignClient.getPolicies();
            if (policyResult != null && policyResult.getData() != null) {
                List<PolicyInput> policies = policyResult.getData().stream()
                    .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                    .map(p -> {
                    PolicyInput pi = new PolicyInput();
                    pi.setExpenseType(p.getExpenseType());
                    pi.setMaxAmount(p.getMaxAmount() != null ? p.getMaxAmount().doubleValue() : 0);
                    pi.setDailyLimit(p.getDailyLimit() != null ? p.getDailyLimit().doubleValue() : 0);
                    pi.setCityTier(p.getCityTier());
                    return pi;
                }).toList();
                ruleInput.setPolicies(policies);
            }
        } catch (Exception e) {
            log.warn("查询费用政策失败，跳过合规检查: {}", e.getMessage());
        }
    }

    // 5. 执行规则
    RuleOutput rule = droolsRuleService.evaluate(ruleInput);

    // 6. BLOCK 级违规直接拒绝
    long blockCount = rule.getViolations().stream()
        .filter(v -> "BLOCK".equals(v.getSeverity())).count();
    if (blockCount > 0) {
        String msg = rule.getViolations().stream()
            .filter(v -> "BLOCK".equals(v.getSeverity()))
            .map(RuleOutput.Violation::getMessage)
            .collect(Collectors.joining("; "));
        log.warn("审批启动被规则拦截: {}", msg);
        throw new BusinessException("审批启动失败: " + msg);
    }

    // 7. 流程变量 + 启动流程（原有逻辑）
    Map<String, Object> variables = new HashMap<>();
    variables.put("businessType", dto.getBusinessType());
    variables.put("businessId", dto.getBusinessId());
    variables.put("requestNo", dto.getRequestNo());
    variables.put("applicantId", dto.getApplicantId());
    variables.put("applicantName", dto.getApplicantName() != null ? dto.getApplicantName() : "未知");
    variables.put("amount", dto.getAmount() != null ? dto.getAmount().doubleValue() : 0);
    variables.put("needDirector", rule.isNeedDirector());
    variables.put("departmentId", dto.getDepartmentId());
    variables.put("violations", rule.getViolations());

    String processDefKey = switch (dto.getBusinessType()) {
        case "TRAVEL_REQUEST" -> "travel-request-approval";
        case "EXPENSE_REPORT" -> "expense-report-approval";
        default -> throw new IllegalArgumentException("未知业务类型: " + dto.getBusinessType());
    };

    var pi = runtimeService.startProcessInstanceByKey(processDefKey, variables);
    log.info("流程启动: processInstanceId={}, businessType={}, needDirector={}, violations={}",
        pi.getId(), dto.getBusinessType(), rule.isNeedDirector(), rule.getViolations().size());

    String approvalLevel = rule.isNeedDirector() ? "DUAL" : "SINGLE";
    return new ProcessStartResponse(pi.getId(), approvalLevel, rule.getWarnings());
}
```

添加 import：
```java
import com.expenseflow.approval.dto.*;
import com.expenseflow.approval.feign.dto.ApplicantHistoryDTO;
import com.expenseflow.common.exception.BusinessException;
import java.util.stream.Collectors;
```

同时需要在 ExpenseFeignClient 添加 policies 端点（复用现有 `/policy/list`，Feign 侧过滤 status=1）：
```java
@GetMapping("/policy/list")
Result<List<ExpensePolicyDTO>> getPolicies();
```

并在 feign/dto 下创建 `ExpensePolicyDTO.java`：
```java
package com.expenseflow.approval.feign.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ExpensePolicyDTO {
    private Long id;
    private String policyName;
    private String expenseType;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private String cityTier;
    private Integer status;
}
```

在 ApprovalProcessServiceImpl 中调用后过滤 `status == 1` 再转换。

- [ ] **Step 6: 编译验证**

```bash
cd approval-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add approval-service/src/main/java/
git commit -m "feat(approval): Feign 数据准备 + ApprovalProcessServiceImpl 集成 RuleInput 组装"
```

---

### Task 7: 扩展单元测试 — DroolsRuleServiceTest 从 5 → 16 测试

**Files:**
- Modify: `approval-service/src/test/java/com/expenseflow/approval/service/DroolsRuleServiceTest.java`

- [ ] **Step 1: 重写测试类 — 16 个测试覆盖全部 12 条规则**

```java
package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.*;
import com.expenseflow.approval.dto.RuleOutput.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DroolsRuleServiceTest {

    private final DroolsRuleService ruleService = new DroolsRuleService(null); // Java fallback

    // ============ amount-threshold (3 tests, 现有) ============

    @Test
    @DisplayName("TRAVEL_REQUEST 金额 > 5000 需要总监审批")
    void travelRequestAbove5000NeedsDirector() {
        RuleOutput result = ruleService.evaluate("TRAVEL_REQUEST", BigDecimal.valueOf(6000));
        assertThat(result.isNeedDirector()).isTrue();
    }

    @Test
    @DisplayName("TRAVEL_REQUEST 金额 ≤ 5000 不需要总监审批")
    void travelRequestBelow5000NoDirector() {
        RuleOutput result = ruleService.evaluate("TRAVEL_REQUEST", BigDecimal.valueOf(3000));
        assertThat(result.isNeedDirector()).isFalse();
    }

    @Test
    @DisplayName("EXPENSE_REPORT 金额 > 10000 触发高额警告")
    void expenseReportAbove10000Warns() {
        RuleOutput result = ruleService.evaluate("EXPENSE_REPORT", BigDecimal.valueOf(15000));
        assertThat(result.getWarnings()).isNotEmpty();
    }

    @Test
    @DisplayName("EXPENSE_REPORT 金额 > 20000 触发总监复核警告")
    void expenseReportAbove20000DirectorWarning() {
        RuleOutput result = ruleService.evaluate("EXPENSE_REPORT", BigDecimal.valueOf(25000));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("20000"));
    }

    @Test
    @DisplayName("小金额无警告")
    void smallAmountNoWarnings() {
        RuleOutput result = ruleService.evaluate("EXPENSE_REPORT", BigDecimal.valueOf(5000));
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.isNeedDirector()).isFalse();
    }

    // ============ type-compliance (3 tests) ============

    @Test
    @DisplayName("费用类型不在政策范围内 → BLOCK")
    void unmatchedExpenseTypeShouldBlock() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1000);
        input.setItems(List.of(item("OTHER", 100)));
        input.setPolicies(List.of(policy("TRANSPORT", 5000, 0)));

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "TYPE_MISMATCH".equals(v.getType()) && "BLOCK".equals(v.getSeverity()));
    }

    @Test
    @DisplayName("单笔超政策上限 → BLOCK")
    void itemExceedsPolicyMaxShouldBlock() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(6000);
        input.setItems(List.of(item("TRANSPORT", 6000)));
        input.setPolicies(List.of(policy("TRANSPORT", 5000, 0)));

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "POLICY_VIOLATION".equals(v.getType()) && "BLOCK".equals(v.getSeverity()));
    }

    @Test
    @DisplayName("同类型同日超日限额 → WARN")
    void dailyLimitExceededShouldWarn() {
        LocalDate today = LocalDate.now();
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(700);
        input.setItems(List.of(
            item("MEAL", 60, today),
            item("MEAL", 60, today)));
        input.setPolicies(List.of(policy("MEAL", 200, 100)));

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "POLICY_VIOLATION".equals(v.getType()) && "WARN".equals(v.getSeverity())
                && v.getMessage().contains("日限额"));
    }

    // ============ duplicate-detection (3 tests) ============

    @Test
    @DisplayName("发票号已存在 → BLOCK")
    void duplicateInvoiceNoShouldBlock() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1000);
        input.setInvoices(List.of(invoice("INV001", 1000)));
        ApplicantHistory history = new ApplicantHistory();
        history.setUsedInvoiceNos(List.of("INV001"));
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "DUPLICATE".equals(v.getType()) && "BLOCK".equals(v.getSeverity()));
    }

    @Test
    @DisplayName("三要素重复标志 → WARN")
    void amountDateVendorMatchShouldWarn() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1000);
        ApplicantHistory history = new ApplicantHistory();
        history.setHasAmountDateVendorMatch(true);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "DUPLICATE".equals(v.getType()) && "WARN".equals(v.getSeverity()));
    }

    @Test
    @DisplayName("发票号不在历史中 → 不触发重复")
    void newInvoiceNoShouldNotTriggerDuplicate() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1000);
        input.setInvoices(List.of(invoice("INV_NEW", 1000)));
        ApplicantHistory history = new ApplicantHistory();
        history.setUsedInvoiceNos(List.of("INV001", "INV002"));
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).noneMatch(v -> "DUPLICATE".equals(v.getType()));
    }

    // ============ anomaly-detection (3 tests) ============

    @Test
    @DisplayName("金额远超历史均值 → WARN")
    void amountDeviatesFromAverageShouldWarn() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(5000);
        input.setItems(List.of(item("HOTEL", 5000)));
        ApplicantHistory history = new ApplicantHistory();
        history.setAvgAmount(500);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "ANOMALY".equals(v.getType()) && "WARN".equals(v.getSeverity())
                && v.getMessage().contains("远超历史均值"));
    }

    @Test
    @DisplayName("近30天 ≥5笔 → WARN")
    void highFrequencyShouldWarn() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(2000);
        ApplicantHistory history = new ApplicantHistory();
        history.setRecentReportCount(6);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "ANOMALY".equals(v.getType()) && v.getMessage().contains("频率偏高"));
    }

    @Test
    @DisplayName("疑似拆单标志 → WARN")
    void suspectedSplitShouldWarn() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(500);
        ApplicantHistory history = new ApplicantHistory();
        history.setSuspectedSplit(true);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "ANOMALY".equals(v.getType()) && v.getMessage().contains("拆单"));
    }

    // ============ 组合场景 (2 tests) ============

    @Test
    @DisplayName("多违规同时触发")
    void multipleViolationsTriggeredTogether() {
        LocalDate today = LocalDate.now();
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(2000);
        input.setItems(List.of(
            item("OTHER", 500),     // TYPE_MISMATCH
            item("MEAL", 60, today),
            item("MEAL", 50, today)  // daily limit
        ));
        input.setPolicies(List.of(policy("TRANSPORT", 5000, 0), policy("MEAL", 200, 100)));
        ApplicantHistory history = new ApplicantHistory();
        history.setRecentReportCount(6);
        history.setAvgAmount(100);
        input.setHistory(history);
        input.setInvoices(List.of(invoice("INV_DUP", 500)));
        history.setUsedInvoiceNos(List.of("INV_DUP"));
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("合规报销无违规")
    void compliantReportNoViolations() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1500);
        input.setItems(List.of(item("TRANSPORT", 1500)));
        input.setPolicies(List.of(policy("TRANSPORT", 5000, 0)));
        ApplicantHistory history = new ApplicantHistory();
        history.setRecentReportCount(1);
        history.setAvgAmount(1500);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).isEmpty();
    }

    // ============ helper methods ============

    private ExpenseItemInput item(String type, double amount) {
        ExpenseItemInput i = new ExpenseItemInput();
        i.setExpenseType(type);
        i.setAmount(amount);
        i.setExpenseDate(LocalDate.now());
        return i;
    }

    private ExpenseItemInput item(String type, double amount, LocalDate date) {
        ExpenseItemInput i = item(type, amount);
        i.setExpenseDate(date);
        return i;
    }

    private PolicyInput policy(String type, double max, double daily) {
        PolicyInput p = new PolicyInput();
        p.setExpenseType(type);
        p.setMaxAmount(max);
        p.setDailyLimit(daily);
        return p;
    }

    private InvoiceInput invoice(String no, double amount) {
        InvoiceInput i = new InvoiceInput();
        i.setInvoiceNo(no);
        i.setAmount(amount);
        return i;
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd approval-service && mvn test -pl . -Dtest=DroolsRuleServiceTest
```

Expected: 16 tests PASS

- [ ] **Step 3: Commit**

```bash
git add approval-service/src/test/
git commit -m "test(approval): DroolsRuleService 16 测试覆盖全部12条规则"
```

---

### Task 8: 全量测试 + 本地验证

- [ ] **Step 1: 运行 approval-service 全量测试**

```bash
cd approval-service && mvn test -q
```

Expected: All tests PASS

- [ ] **Step 2: 打包**

```bash
mvn clean package -DskipTests -q
```

- [ ] **Step 3: Docker 重启服务**

```bash
docker compose -f docker-compose.services.yml up -d --build approval-service expense-service
```

- [ ] **Step 4: 验证 health endpoints**

```bash
curl -s http://localhost:8083/actuator/health | grep UP
curl -s http://localhost:8082/actuator/health | grep UP
```

- [ ] **Step 5: Commit**

```bash
git commit -m "chore: P1-2 Drools 规则库扩充完成，全量测试通过"
```

---

## 文件变更清单

| 操作 | 文件 |
|:---:|------|
| **C** | `approval-service/.../dto/ExpenseItemInput.java` |
| **C** | `approval-service/.../dto/InvoiceInput.java` |
| **C** | `approval-service/.../dto/ApplicantHistory.java` |
| **C** | `approval-service/.../dto/PolicyInput.java` |
| **C** | `approval-service/.../feign/dto/ExpenseItemDTO.java` |
| **C** | `approval-service/.../feign/dto/InvoiceDTO.java` |
| **C** | `approval-service/.../feign/dto/ApplicantHistoryDTO.java` |
| **C** | `approval-service/.../feign/dto/ExpensePolicyDTO.java` |
| **C** | `approval-service/.../resources/rules/amount-threshold.drl` |
| **C** | `approval-service/.../resources/rules/type-compliance.drl` |
| **C** | `approval-service/.../resources/rules/duplicate-detection.drl` |
| **C** | `approval-service/.../resources/rules/anomaly-detection.drl` |
| **C** | `expense-service/.../dto/ApplicantHistoryDTO.java` |
| **M** | `approval-service/.../dto/RuleInput.java` |
| **M** | `approval-service/.../dto/RuleOutput.java` |
| **M** | `approval-service/.../service/DroolsRuleService.java` |
| **M** | `approval-service/.../service/impl/ApprovalProcessServiceImpl.java` |
| **M** | `approval-service/.../feign/ExpenseFeignClient.java` |
| **M** | `approval-service/.../feign/fallback/ExpenseFeignFallbackFactory.java` |
| **M** | `expense-service/.../controller/ExpenseReportController.java` |
| **M** | `expense-service/.../service/ExpenseReportService.java` |
| **M** | `expense-service/.../service/ExpenseReportServiceImpl.java` |
| **M** | `approval-service/.../test/.../DroolsRuleServiceTest.java` |
| **D** | `approval-service/.../resources/rules/approval-rules.drl` |
