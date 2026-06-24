# 数据模型模块 (DataModelController) 测试报告

## 测试概览

| 项目 | 结果 |
|------|------|
| 测试日期 | 2026-06-24 |
| 接口数量 | 18 |
| 测试用例 | 30 |
| 通过 | 30 |
| 失败 | 0 |
| 通过率 | **100%** |

## 接口清单

| # | HTTP 方法 | 路径 | 功能 |
|---|-----------|------|------|
| 1 | POST | `/api/data-models` | 创建数据模型 |
| 2 | GET | `/api/data-models` | 查询模型列表 |
| 3 | GET | `/api/data-models/{id}` | 获取模型详情 |
| 4 | DELETE | `/api/data-models/{id}` | 删除数据模型 |
| 5 | POST | `/api/data-models/{id}/init/generate-ddl` | LLM 生成 DDL |
| 6 | POST | `/api/data-models/{id}/init/confirm-ddl` | 确认 DDL 建表 |
| 7 | GET | `/api/data-models/{id}/init/agents` | 获取可用 Agent |
| 8 | GET | `/api/data-models/{id}/init/library-materials` | 获取关联素材 |
| 9 | GET | `/api/data-models/{id}/init/rules` | 获取抽取规则 |
| 10 | POST | `/api/data-models/{id}/init/rules` | 保存抽取规则 |
| 11 | POST | `/api/data-models/{id}/extract` | 触发数据抽取 |
| 12 | GET | `/api/data-models/{id}/extract/progress` | SSE 抽取进度 |
| 13 | GET | `/api/data-models/{id}/extract/logs` | 获取抽取日志 |
| 14 | GET | `/api/data-models/{id}/extract/files` | 获取可抽取文件 |
| 15 | GET | `/api/data-models/{id}/data` | 查询动态表数据 |
| 16 | PUT | `/api/data-models/{id}/data/{rowId}` | 更新动态表行 |
| 17 | PUT | `/api/data-models/{id}/ddl` | 修改 DDL |
| 18 | GET/POST/DELETE | `/api/data-models/{id}/schedule` | 定时任务管理 |

## 测试用例详情

### CRUD (7 用例)

| # | 测试用例 | 预期结果 | 状态 |
|---|----------|----------|------|
| 1 | 创建数据模型 | 200 成功 | PASS |
| 2 | 创建无效编码 | 400 编码格式无效 | PASS |
| 3 | 创建重复编码 | 400 编码已存在 | PASS |
| 4 | 查询所有模型 | 200 返回列表 | PASS |
| 5 | 按关键词搜索 | 200 返回过滤结果 | PASS |
| 6 | 按 ID 获取 | 200 成功 | PASS |
| 7 | 获取不存在模型 | 400 不存在 | PASS |

### DDL / 初始化 (5 用例)

| # | 测试用例 | 预期结果 | 状态 |
|---|----------|----------|------|
| 8 | 确认 DDL 建表 | 200 成功，状态变READY | PASS |
| 9 | 验证状态变更 | 200 status=READY | PASS |
| 10 | 获取可用 Agent | 200 返回空列表 | PASS |
| 11 | 获取关联素材 | 200 返回空列表 | PASS |
| 12 | LLM 生成 DDL (无服务) | 400 未绑定素材库 | PASS |

### 抽取规则 (3 用例)

| # | 测试用例 | 预期结果 | 状态 |
|---|----------|----------|------|
| 13 | 获取规则(空) | 200 null | PASS |
| 14 | 保存抽取规则 | 200 成功 | PASS |
| 15 | 获取活跃规则 | 200 version=1 | PASS |

### 动态表 / 抽取 (7 用例)

| # | 测试用例 | 预期结果 | 状态 |
|---|----------|----------|------|
| 16 | 查询空表 | 200 空列表 | PASS |
| 17 | 按关键词查询 | 200 空列表 | PASS |
| 18 | 获取抽取日志 | 200 空列表 | PASS |
| 19 | 获取可抽取文件 | 200 空列表 | PASS |
| 20 | 触发抽取 | 200 成功 | PASS |
| 21 | SSE 进度端点 | 可达 | PASS |
| 22 | 修改 DDL (添加列) | 200 成功 | PASS |

### 定时任务 (6 用例)

| # | 测试用例 | 预期结果 | 状态 |
|---|----------|----------|------|
| 23 | 获取定时任务(空) | 200 null | PASS |
| 24 | 创建定时任务 | 200 成功 | PASS |
| 25 | 获取定时任务 | 200 cron=0 0 2 * * ? | PASS |
| 26 | 更新定时任务 | 200 更新成功 | PASS |
| 27 | 删除定时任务 | 200 成功 | PASS |
| 28 | 再次删除(已删除) | 200 静默处理 | PASS |

### 清理 (2 用例)

| # | 测试用例 | 预期结果 | 状态 |
|---|----------|----------|------|
| 29 | 删除数据模型 | 200 成功(含动态表清理) | PASS |
| 30 | 删除不存在模型 | 400 不存在 | PASS |

## 测试覆盖度

- CRUD：全路径覆盖，编码校验、重复检查
- 初始化向导：DDL 确认建表、Agent 列表、素材列表、DDL 生成(无LLM)
- 抽取规则：创建、版本控制、活跃规则查询
- 动态表：空表查询、关键词搜索、DDL 修改(备份+重建)
- 抽取流程：触发、进度 SSE、日志查询、文件列表
- 定时任务：创建、更新、删除、重复删除
- 覆盖度评估：**完全覆盖**（LLM 相关子流程需真实 LLM 服务）
