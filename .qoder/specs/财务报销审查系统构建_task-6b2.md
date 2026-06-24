# 数据模型模块（DModel）

## Context

在现有 RRS 系统基础上新增「数据模型」模块。数据模型代表从素材库中清洗出的结构化数据，一个模型对应一张动态数据表（SQLite，`dm_{code}` 前缀），绑定一个素材库。支持 AI 辅助或手动定义表结构（DDL），配置抽取规则（Excel字段映射 / 图片 OCR+LLM 提取），手动/定时/自动三种触发方式抽取数据。动态表通过 `JdbcTemplate` 原始 SQL 操作，不走 JPA。

## 技术决策

- **动态表存储**：同一 SQLite 数据库，`dm_` 前缀，通过 `JdbcTemplate` 操作
- **自动抽取**：素材上传后通过 Spring `ApplicationEvent` + `@Async` 事件触发
- **AI 辅助 DDL**：需要选择已配置 LLM 凭证的 Agent

## 后端

### Task 1: 基础实体 + 枚举 + 仓库

**新建 5 个枚举**：
- `entity/DataModelStatus.java`: `UNINITIALIZED, INITIALIZING, READY, ERROR`
- `entity/ExtractionRuleType.java`: `EXCEL_MAPPING, IMAGE_PROMPT`
- `entity/TriggerType.java`: `MANUAL, SCHEDULED, AUTO`
- `entity/ScopeType.java`: `FULL, INCREMENTAL`
- `entity/ExtractionStatus.java`: `RUNNING, SUCCESS, FAILED`

**新建 4 个实体**：
- `entity/DataModel.java` — 主实体：id, code(unique), name, description(TEXT), maintainer, libraryId(Long), status(DataModelStatus), tableName, ddl(TEXT), primaryKey, lastExtractedAt, createdAt, updatedAt
- `entity/ExtractionRule.java` — 抽取规则：id, modelId, version(Integer), ruleType, ruleContent(TEXT/JSON), active(Boolean), createdAt
- `entity/ExtractionLog.java` — 抽取日志：id, modelId, triggerType, scopeType, status, totalFiles, processedFiles, successRecords, failedRecords, logContent(TEXT), startedAt, finishedAt, createdAt
- `entity/DataModelSchedule.java` — 定时调度：id, modelId(unique), cronExpression, enabled(Boolean), scopeType, createdAt, updatedAt

**新建 4 个仓库**：
- `repository/DataModelRepository.java` — findByCode, findByLibraryId, findByLibraryIdAndStatus
- `repository/ExtractionRuleRepository.java` — findByModelIdAndActiveTrue, findByModelIdOrderByVersionDesc, countByModelId
- `repository/ExtractionLogRepository.java` — findByModelIdOrderByCreatedAtDesc(Pageable), findTopByModelIdOrderByCreatedAtDesc
- `repository/DataModelScheduleRepository.java` — findByModelId, findByEnabledTrue

**修改** `RrsApplication.java` — 添加 `@EnableAsync`

### Task 2: DTO + 模型 CRUD + 控制器基础

**新建 12 个 DTO**：
- `DataModelDTO`, `DataModelCreateDTO`(code/name/description/maintainer)
- `DdlGenerateRequest`(agentId, fileIds?), `DdlConfirmRequest`(ddl, primaryKey)
- `ExtractionRuleDTO`, `ExtractionRuleSaveRequest`(ruleType, ruleContent)
- `ExtractionRequest`(scopeType, fileIds?), `ExtractionLogDTO`
- `DynamicTablePageDTO`(columns, rows, total), `DynamicTableRowUpdateDTO`(data: Map)
- `DataModelScheduleDTO`(cronExpression, enabled, scopeType)

**新建 `service/DataModelService.java`** — CRUD + 动态表操作：
- `create(DataModelCreateDTO)` — 校验 code 唯一/格式(`^[a-zA-Z][a-zA-Z0-9_]*$`)，设 tableName=`dm_{code}`
- `list(keyword)`, `getById(id)`, `delete(id)` — 删除时 `DROP TABLE IF EXISTS dm_{code}`
- `executeCreateTable(tableName, ddl, primaryKey)` — 解析 DDL，追加 created_at/updated_at/source_file 公共字段，`jdbcTemplate.execute()`
- `modifyDdl(id, newDdl, primaryKey)` — 备份旧表(`ALTER TABLE RENAME TO dm_xxx_bak_时间戳`)，创建新表
- `queryDynamicTable(id, page, size, keyword)` — 动态列+分页+全字段模糊搜索
- `updateDynamicRow(id, rowId, data)` — 动态 UPDATE，白名单字段名校验

**新建 `controller/DataModelController.java`** — REST 端点前缀 `/api/data-models`

### Task 3: AI 辅助 DDL 生成

**新建 `service/DdlGenerationService.java`**：
- `generateFromExcel(agentId, files)` — POI 读取表头+前3行，构建 Prompt 调用 LLM
- `generateFromImage(agentId, files)` — OcrService.processLlm() 获取 OCR 结果，构建 Prompt
- 内部通过 `LlmService` 模式（新增通用 `callLlm(agentId, messages)` 方法到 `LlmService`）调用 LLM

**Excel DDL Prompt**：
```
System: 你是数据库建模专家。根据 Excel 文件表头和示例数据，生成 SQLite CREATE TABLE DDL。
规则：表名用 {TABLE_NAME} 占位；字段类型只用 TEXT/INTEGER/REAL；金额用 REAL，日期用 TEXT；字段名英文 snake_case + -- 中文注释；不含 PRIMARY KEY 和公共字段。
User: 以下是最近 N 个文件的表头和前3行：...
```

**Image DDL Prompt**：类似，输入为 OCR 结果

**控制器端点**：
- `POST /{id}/init/generate-ddl` — AI 生成 DDL
- `POST /{id}/init/confirm-ddl` — 确认 DDL + 建表
- `GET /{id}/init/agents` — 获取已配置凭证的 Agent 列表

### Task 4: 抽取规则 + 版本管理

**DataModelService 规则方法**：
- `saveRule(modelId, request)` — 当前 active 版本设 active=false，新建 version+1，超 5 个版本删最旧
- `getActiveRule(modelId)` — 返回当前活跃规则

**Excel 规则 JSON**：
```json
{"sheetIndex":0,"headerRowIndex":0,"dataStartRowIndex":1,"mappings":[{"sourceColumn":"姓名","targetField":"name","formatter":null},{"sourceColumn":"金额","targetField":"amount","formatter":{"type":"NUMBER_TO_STRING","pattern":"#.00"}}]}
```

**Image 规则 JSON**：
```json
{"systemPrompt":"...","userPromptTemplate":"请从以下OCR文本中提取...\n{text}","outputFields":["name","amount"],"ocrMode":"LLM"}
```

**控制器端点**：
- `GET /{id}/init/rules`, `POST /{id}/init/rules`

### Task 5: 数据抽取引擎

**新建 `service/ExtractionService.java`**：
- `extract(modelId, request)` — 创建 ExtractionLog，执行抽取，更新状态
- `extractExcel(model, rule, files, log, emitter)` — POI 读取→映射→格式化→batch INSERT
- `extractImage(model, rule, files, log, emitter)` — OCR→LLM提取JSON→INSERT
- `getFilesForExtraction(model, scope)` — FULL=全部, INCREMENTAL=uploadedAt > lastExtractedAt
- SSE 进度推送：`ConcurrentHashMap<Long, SseEmitter>`

**数据格式化器**：NUMBER_TO_STRING, DATE_FORMAT, TRIM

**控制器端点**：
- `POST /{id}/extract` — 手动触发
- `GET /{id}/extract/progress` — SSE 进度
- `GET /{id}/extract/logs` — 日志列表
- `GET /{id}/extract/files` — 可抽取文件列表
- `GET /{id}/data` — 动态表分页
- `PUT /{id}/data/{rowId}` — 编辑单行
- `PUT /{id}/ddl` — 修改 DDL

### Task 6: 事件驱动 + 定时调度

**新建事件**：
- `event/MaterialUploadedEvent.java` — 携带 libraryId, materialId
- `event/DataModelAutoExtractListener.java` — `@Async @EventListener`，查找关联 READY 模型，增量抽取

**修改 `MaterialService.upload()`** — 末尾发布 `MaterialUploadedEvent`

**新建 `service/DataModelScheduleService.java`** — 使用 `TaskScheduler`（非 `@Scheduled`，动态任务）：
- `@PostConstruct` 加载所有已启用调度
- `saveSchedule(modelId, dto)` — 创建/更新 cron 任务
- `removeSchedule(modelId)` — 取消调度

**新建 `config/ScheduleConfig.java`** — 声明 `TaskScheduler` Bean

**控制器端点**：
- `GET/POST/DELETE /{id}/schedule`

## 前端

### Task 7: API + 列表页 + 导航

**新建 `api/dataModel.ts`** — 全部接口函数（createDataModel, getDataModels, generateDdl, confirmDdl, saveRule, triggerExtraction, streamProgress, getDynamicData, updateRow, modifyDdl, schedule CRUD 等）

**新建 `views/dataModel/DataModelList.vue`** — 卡片网格展示，状态标签颜色区分，搜索+新建按钮

**修改 `router/index.ts`** — 添加 3 条路由：`/data-models`, `/data-models/:id`, `/data-models/:id/init`

**修改 `AppLayout.vue`** — 导航菜单添加「数据模型」（Grid 图标），activeMenu 逻辑

### Task 8: 初始化向导（6步）

**新建 `views/dataModel/DataModelWizard.vue`** — `el-steps` 6步向导：

1. **选择素材库+初始化方式**：下拉选素材库，根据 libraryType 显示 AI/手动选项，AI 需选 Agent
2. **确认表结构+主键**：DDL textarea（等宽字体），下拉选主键字段，确认建表
3. **配置抽取规则**：Excel=映射表格(动态增删行)，Image=Prompt textarea
4. **是否试抽取**：两个按钮卡片（立即试/跳过）
5. **选择文件+执行**：文件 checkbox + SSE 进度条 + 滚动日志
6. **完成**：结果展示 + 跳转详情

**新建子组件**：
- `components/DdlEditor.vue` — DDL 编辑器（textarea + 字段解析）
- `components/RuleConfigExcel.vue` — 映射表格
- `components/RuleConfigImage.vue` — Prompt 编辑器
- `components/ExtractionProgress.vue` — 进度展示

### Task 9: 模型详情页

**新建 `views/dataModel/DataModelDetail.vue`** — Tab 页布局：
- **数据浏览**：el-table 动态列+分页，行内编辑弹窗
- **抽取管理**：手动抽取按钮 + 抽取范围选择 + 日志时间线
- **规则配置**：复用 RuleConfig 组件
- **定时调度**：cron 输入 + 预设选择 + 启停开关
- **DDL 维护**：编辑器 + 应用修改（确认弹窗提示备份）

**新建子组件**：
- `components/ScheduleConfig.vue` — 定时调度配置
- `components/RowEditDialog.vue` — 单行编辑弹窗

## 文件变更总览

| 类型 | 数量 | 说明 |
|------|------|------|
| 后端新建 | ~33 个 | 4实体 + 5枚举 + 4仓库 + 12DTO + 4Service + 1Controller + 2Event + 1Config |
| 后端修改 | 3 个 | RrsApplication(@EnableAsync), MaterialService(发布事件), LlmService(通用chat方法) |
| 前端新建 | ~10 个 | 1API + 3页面 + 6子组件 |
| 前端修改 | 2 个 | router/index.ts, AppLayout.vue |

## 验证

1. 后端编译：`cd backend; .\gradlew build -x test`
2. 启动后端，确认新表已创建（data_model, extraction_rule, extraction_log, data_model_schedule）
3. 创建数据模型 → 选择素材库 → AI/手动生成 DDL → 确认建表
4. 配置抽取规则 → 试抽取 → 检查动态表数据
5. 模型详情页数据浏览/编辑/抽取管理/定时调度
6. 修改 DDL → 确认旧表备份+新表创建
7. 上传新素材 → 观察自动增量抽取
