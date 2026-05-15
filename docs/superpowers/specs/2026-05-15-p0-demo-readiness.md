# P0 演示可用性提升 — 设计文档

> 版本：v1.0 | 日期：2026-05-15 | 状态：OCR 已确认，审批+前端待设计

## 背景

M8-M10 完成后核心链路代码就绪，但以下 3 项在演示时可能卡住：
1. OCR 发票识别是 Mock 实现，不是真实 AI
2. 审批全流程未端到端验证
3. 前端未启动验证过页面渲染和交互

---

## P0-1：OCR 接入真实阿里云 API

### 当前状态

`OcrServiceImpl.doOcr()` 用 `Thread.sleep(800)` 模拟识别，返回写死的 Mock 数据（金额 100.00、发票号 MOCK-INV-xxx）。

`OcrConfig` 已配置阿里云 OCR API 端点 + AppCode 认证字段，但 `doOcr()` 未使用。

### 改动范围

| 文件 | 操作 | 说明 |
|------|------|------|
| `ai-service/.../impl/OcrServiceImpl.java` | 修改 | 注入 OcrConfig，mock=false 时发 HTTP 请求 |
| `ai-service/.../service/OcrServiceImplTest.java` | 新建 | 测试真实 API 调用 + 降级 |

### 技术方案

```
doOcr(AiOcrResult):
  if (ocrConfig.isMock()):
    mockRecognize(result)   // 保持现有 Mock 逻辑
  else:
    try:
      httpClient.send(postRequest)       // POST 阿里云 OCR API
        Header: Authorization: APPCODE {appCode}
        Body: {url: imageUrl} or {img: base64}
      parseResponse(responseBody)        // 提取字段
      result.status = "SUCCESS"
    catch (Exception):
      mockRecognize(result)              // 降级
      result.confidence = 0
      result.errorMessage = ex.message
```

### 解析映射

| OCR API 返回字段 | AiOcrResult 字段 |
|-----------------|-----------------|
| `data.invoiceNum` | parsedInvoiceNo |
| `data.totalAmount` | parsedAmount |
| `data.invoiceDate` | parsedInvoiceDate |
| `data.sellerName` | parsedSellerName |
| `data.sellerTaxNo` | parsedSellerTaxNo |
| 整体 JSON | rawResponse |

### 测试

```java
class OcrServiceImplTest {
    // 测试1: mock=true → 走 Mock 逻辑，返回固定数据
    // 测试2: mock=false + API正常 → 解析真实响应
    // 测试3: mock=false + API异常 → 降级为 Mock，confidence=0
}
```

---

## P0-2：审批全流程端到端验证（待设计）

简要：curl 走通出差申请提交→经理审批→总监审批→回调更新→通知发送的完整链路，修复中途断裂点。

## P0-3：前端冒烟测试（待设计）

简要：Vite dev server 启动，验证登录、出差列表、报销创建、审批工作台、AI 助手、通知中心 6 个核心页面无报错。

---

## 不在此次范围

- OcrConfig 中 accessKeyId/accessKeySecret 字段当前未使用（使用 AppCode 认证），不做清理
- 发票图片上传→OCR 触发→识别的 RabbitMQ 链路不做改动，只改 doOcr 内部实现
