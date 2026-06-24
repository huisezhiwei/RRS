# 单元测试报告 - 非接口功能模块

**测试日期**: 2026-06-18  
**测试框架**: JUnit 5 + Spring Boot Test  
**测试类**: `NonApiFeatureTest.java`  
**测试环境**: Spring Boot 3.5.3 / Java 17 / SQLite

---

## 测试概览

| 指标 | 数值 |
|------|------|
| 总测试用例数 | 12 |
| 通过 | 12 |
| 失败 | 0 |
| 成功率 | **100%** |

---

## 模块一: DataInitializer 数据初始化 (2 个用例)

验证系统启动时内置 AI Agent 的初始化逻辑及幂等性。

| # | 测试用例 | 描述 | 结果 |
|---|---------|------|------|
| 1 | testBuiltInAgentsInitialized | 启动后 chat_assistant 和 ocr_assistant 两个内置 Agent 已正确初始化，类型、名称、系统提示词均正确 | ✅ PASS |
| 2 | testInitBuiltInAgentsIdempotent | 重复调用 `initBuiltInAgents()` 不会产生重复记录，Agent 数量保持不变 | ✅ PASS |

**覆盖度**: 
- ✅ chat_assistant 初始化验证（AgentType、name、createdAt）
- ✅ ocr_assistant 初始化验证（AgentType、name、systemPrompt）
- ✅ 幂等性验证（重复初始化不产生重复数据）

---

## 模块二: DataModelScheduleService 定时任务管理 (5 个用例)

验证定时任务的 CRUD 操作、更新逻辑及异常 cron 表达式的处理。

| # | 测试用例 | 描述 | 结果 |
|---|---------|------|------|
| 3 | testSaveAndGetSchedule | 创建定时任务（cron=`0 0 3 * * ?`，FULL 模式），保存后可正确检索，ID、cron、scopeType 均匹配 | ✅ PASS |
| 4 | testRemoveSchedule | 创建定时任务后删除，再次查询返回 null | ✅ PASS |
| 5 | testGetScheduleReturnsNullWhenNone | 对没有定时任务的数据模型查询，返回 null | ✅ PASS |
| 6 | testUpdateSchedule | 对同一模型保存两次不同配置，第二次更新同一记录（ID 不变），cron 和 enabled 状态更新为新值 | ✅ PASS |
| 7 | testInvalidCronExpression | 使用无效 cron 表达式 `invalid_cron`，系统不抛出异常（静默处理） | ✅ PASS |

**覆盖度**:
- ✅ 定时任务创建（saveSchedule）
- ✅ 定时任务查询（getSchedule）
- ✅ 定时任务删除（removeSchedule）
- ✅ 定时任务更新（重复 saveSchedule 更新同一记录）
- ✅ 无定时任务时返回 null
- ✅ 无效 cron 表达式容错处理

---

## 模块三: DataModelAutoExtractListener 自动抽取事件监听 (2 个用例)

验证素材上传后自动触发数据抽取的事件监听机制。

| # | 测试用例 | 描述 | 结果 |
|---|---------|------|------|
| 8 | testMaterialUploadedEventPublish | 创建数据模型后发布 MaterialUploadedEvent，事件正常处理无异常 | ✅ PASS |
| 9 | testMaterialUploadedEventNoModels | 发布不存在的 libraryId/modelId 的事件，系统不报错（优雅忽略） | ✅ PASS |

**覆盖度**:
- ✅ 正常事件发布与处理（@Async @EventListener）
- ✅ 无匹配模型时的容错处理
- ✅ Spring ApplicationEventPublisher 集成

---

## 模块四: ExtractionRule 版本控制 (3 个用例)

验证抽取规则的版本递增、旧版本自动停用机制。

| # | 测试用例 | 描述 | 结果 |
|---|---------|------|------|
| 10 | testSaveFirstRule | 保存第一条规则，版本号为 1，状态为 active | ✅ PASS |
| 11 | testRuleVersioning | 保存第二条规则后版本号递增为 2，新规则为 active，getActiveRule 返回最新版本 | ✅ PASS |
| 12 | testGetActiveRuleEmpty | 无规则的数据模型调用 getActiveRule 返回 null | ✅ PASS |

**覆盖度**:
- ✅ 规则初始版本创建（version=1）
- ✅ 版本递增机制（version 自动 +1）
- ✅ 旧版本自动停用（active 切换）
- ✅ 获取当前活跃规则（getActiveRule）
- ✅ 无规则时返回 null

---

## 测试覆盖总结

| 功能模块 | 用例数 | 通过 | 覆盖功能点 |
|---------|--------|------|-----------|
| DataInitializer 数据初始化 | 2 | 2 | 内置 Agent 初始化 + 幂等性 |
| ScheduleService 定时任务 | 5 | 5 | CRUD + 更新 + 无效 cron 容错 |
| AutoExtractListener 事件监听 | 2 | 2 | 事件发布 + 无匹配容错 |
| ExtractionRule 版本控制 | 3 | 3 | 版本递增 + 活跃规则切换 |
| **合计** | **12** | **12** | **100% 通过率** |
