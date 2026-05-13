# M3 设计规格：差旅报销核心服务

## 概述

M3 里程碑实现 expense-service 完整业务能力：出差申请、报销单、报销明细、发票管理（含阿里云 OCR）、消费记录、费用政策、打款流水。通过 OpenFeign 对接 system-service（用户/部门）和 approval-service（M3 Mock 审批流）。

## 设计决策

| 决策点 | 选择 |
|--------|------|
| OCR 调用方式 | expense-service 直接调阿里云 OCR SDK |
| 审批流集成 | M3 预留 Feign 接口 + fallback mock (直接返回模拟 processInstanceId + 状态变 APPROVED) |
| 关联数据获取 | 仅存 ID，VO 通过 Feign 实时查 system-service 组装姓名/部门名 |

---

## 一、模块结构

```
expense-service/
├── controller/      # 7 个 Controller
├── service/ + impl/
├── mapper/          # 7 个 MyBatis-Plus Mapper
├── entity/          # 7 个 PO (ex_travel_request ~ ex_expense_policy)
├── dto/ + vo/
├── feign/
│   ├── SystemFeignClient          # GET /system/user/{id}, GET /system/department/{id}
│   ├── ApprovalFeignClient        # POST /approval/process/start (M3 fallback mock)
│   └── fallback/
└── config/
    ├── MybatisPlusConfig          # 多租户 + 分页
    └── MetaObjectHandlerConfig    # 自动填充 create_time/update_time
```

## 二、Controller 路由

| Controller | 路径 | 核心接口 |
|---|---|---|
| TravelRequestController | /expense/travel | CRUD + POST `/{id}/submit` + `/{id}/withdraw` |
| ExpenseReportController | /expense/report | CRUD + POST `/{id}/submit` + `/{id}/withdraw` |
| ExpenseItemController | /expense/report/{reportId}/item | CRUD（依附报销单） |
| InvoiceController | /expense/invoice | POST `/upload` + GET `/page` + POST `/{id}/ocr` |
| CostRecordController | /expense/cost | CRUD |
| ExpensePolicyController | /expense/policy | CRUD |
| PaymentRecordController | /expense/payment | GET `/page` + POST `/{id}/pay` |

## 三、业务编号生成

| 单据 | 格式 | 示例 |
|------|------|------|
| 出差申请 | TR-YYYYMMDD-XXXX | TR-20260513-0001 |
| 报销单 | ER-YYYYMMDD-XXXX | ER-20260513-0001 |
| 打款流水 | PY-YYYYMMDD-XXXX | PY-20260513-0001 |

逻辑：查询当天最大序号 +1，不足 4 位补零。基于 `request_no`/`report_no` 唯一索引防并发重复。

## 四、状态机

### 出差申请

```
DRAFT → SUBMITTED → APPROVING → APPROVED
  │         │                      │
  └─ 删除   └─ 撤回 → WITHDRAWN   └→ REJECTED
                 └─ 重新编辑 → DRAFT
```

M3 Mock: SUBMITTED → 调用 ApprovalFeignClient.startProcess() → fallback 返回 mock processInstanceId → 直接变 APPROVED。

### 报销单

```
DRAFT → SUBMITTED → APPROVING → APPROVED → PAID
  │         │           │            │
  └─ 删除   └─ 撤回    └→ REJECTED  └→ REJECTED
```

- 提交前置：至少 1 条明细项 + totalAmount 自动汇总
- 可关联 APPROVED 状态的出差申请
- APPROVED 后手动打款 → PAID

## 五、发票上传 + OCR

```
POST /expense/invoice/upload (MultipartFile, ≤10MB, PNG/JPG/PDF)
  → 存储到 ./upload/invoice/{tenantId}/{uuid}.{ext}
  → INSERT ex_invoice (image_url, ocr_status=PENDING)
  → 异步执行阿里云 OCR SDK
  → 成功: 解析结果写入 ex_invoice (ocr_status=SUCCESS)
  → 失败: error_message, ocr_status=FAILED
```

同步返回发票 ID，OCR 结果通过 GET /{id} 轮询获取。

## 六、费用政策校验

提交报销单时逐条明细对比 `ex_expense_policy`：
- 费用类型匹配 → 检查 `max_amount`
- 有 `daily_limit` → 当天该类型累计是否超限
- 超限返回警告（不阻止提交）

## 七、Feign 对接

### SystemFeignClient

```java
@FeignClient(name = "system-service", path = "/system",
             fallbackFactory = SystemFeignFallback.class)
public interface SystemFeignClient {
    @GetMapping("/user/{id}")
    Result<UserVO> getUser(@PathVariable Long id);

    @GetMapping("/department/{id}")
    Result<DepartmentVO> getDepartment(@PathVariable Long id);
}
```

### ApprovalFeignClient

```java
@FeignClient(name = "approval-service", path = "/approval",
             fallbackFactory = ApprovalFeignFallback.class)
public interface ApprovalFeignClient {
    @PostMapping("/process/start")
    Result<String> startApproval(@RequestBody ApprovalStartDTO dto);
}
```

M3 Fallback: 直接返回 `Result.ok("mock-pi-" + UUID)` → expense-service 收到后更新 status=APPROVED。

## 八、VO 组装

VO 返回前通过 Feign 组装 system-service 数据：

| VO | 自身字段 | Feign 补充 |
|---|---|---|
| TravelRequestVO | requestNo, destination, dates, amount, status | applicantName, departmentName |
| ExpenseReportVO | reportNo, totalAmount, status, items[] | applicantName, departmentName, travelRequestNo |

## 九、依赖

expense-service pom.xml 新增：
```xml
spring-cloud-starter-openfeign       # Feign 声明式调用
spring-boot-starter-validation       # 参数校验
aliyun-ocr-sdk                       # 阿里云 OCR (M3 Mock 可用)
```

## 十、测试验证

1. `mvn clean package -DskipTests`
2. 启动全部 6 服务
3. 验证所有 health endpoint 200
4. 验证 Nacos 注册
5. 验证出差申请 CRUD + 提交 → 状态变化
6. 验证报销单 CRUD + 提交 → 明细汇总 + 政策校验
7. 验证发票上传 + OCR 异步
8. 验证 Feign 组装 VO（申请人姓名/部门名）
