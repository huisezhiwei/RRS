import request from './request'

export interface DataModel {
  id?: number
  code?: string
  name?: string
  description?: string
  maintainer?: string
  libraryId?: number
  libraryName?: string
  status?: string
  tableName?: string
  ddl?: string
  primaryKey?: string
  lastExtractedAt?: string
  createdAt?: string
  updatedAt?: string
  dataCount?: number
}

export interface ExtractionRule {
  id?: number
  modelId?: number
  version?: number
  ruleType?: string
  ruleContent?: string
  active?: boolean
  createdAt?: string
}

export interface ExtractionLog {
  id?: number
  modelId?: number
  triggerType?: string
  scopeType?: string
  status?: string
  totalFiles?: number
  processedFiles?: number
  successRecords?: number
  failedRecords?: number
  logContent?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
}

export interface DynamicTablePage {
  columns: string[]
  rows: Record<string, any>[]
  total: number
}

export interface ScheduleConfig {
  id?: number
  modelId?: number
  cronExpression?: string
  enabled?: boolean
  scopeType?: string
  createdAt?: string
  updatedAt?: string
}

export interface AiAgent {
  id?: number
  code?: string
  name?: string
  description?: string
  agentType?: string
  credentialId?: number
  modelName?: string
  systemPrompt?: string
  temperature?: number
  maxTokens?: number
  topP?: number
  createdAt?: string
  updatedAt?: string
}

export interface MaterialFile {
  id?: number
  libraryId?: number
  fileName?: string
  storedName?: string
  fileSize?: number
  mimeType?: string
  uploadedAt?: string
  createdAt?: string
  tags?: any[]
}

// ---- CRUD ----

export function createDataModel(data: { code: string; name: string; description?: string; maintainer?: string }): Promise<DataModel> {
  return request.post('/api/data-models', data)
}

export function getDataModels(params?: { keyword?: string }): Promise<DataModel[]> {
  return request.get('/api/data-models', { params })
}

export function getDataModelById(id: number): Promise<DataModel> {
  return request.get(`/api/data-models/${id}`)
}

export function deleteDataModel(id: number): Promise<void> {
  return request.delete(`/api/data-models/${id}`)
}

// ---- Init Wizard ----

export function getAvailableAgents(id: number): Promise<AiAgent[]> {
  return request.get(`/api/data-models/${id}/init/agents`)
}

export function generateDdl(id: number, data: { agentId: number; fileIds?: number[] }): Promise<string> {
  return request.post(`/api/data-models/${id}/init/generate-ddl`, data)
}

export function confirmDdl(id: number, data: { ddl: string; primaryKey?: string }): Promise<void> {
  return request.post(`/api/data-models/${id}/init/confirm-ddl`, data)
}

export function getLibraryMaterials(id: number): Promise<MaterialFile[]> {
  return request.get(`/api/data-models/${id}/init/library-materials`)
}

// ---- Extraction Rules ----

export function getActiveRule(id: number): Promise<ExtractionRule | null> {
  return request.get(`/api/data-models/${id}/init/rules`)
}

export function saveRule(id: number, data: { ruleType: string; ruleContent: string }): Promise<ExtractionRule> {
  return request.post(`/api/data-models/${id}/init/rules`, data)
}

// ---- Extraction ----

export function triggerExtraction(id: number, data: { scopeType: string; fileIds?: number[] }): Promise<ExtractionLog> {
  return request.post(`/api/data-models/${id}/extract`, data)
}

export function streamProgress(id: number): EventSource {
  return new EventSource(`/api/data-models/${id}/extract/progress`)
}

export function getExtractionLogs(id: number, params?: { page?: number; size?: number }): Promise<ExtractionLog[]> {
  return request.get(`/api/data-models/${id}/extract/logs`, { params })
}

export function getExtractableFiles(id: number): Promise<MaterialFile[]> {
  return request.get(`/api/data-models/${id}/extract/files`)
}

// ---- Dynamic Table Data ----

export function getDynamicData(id: number, params?: { page?: number; size?: number; keyword?: string }): Promise<DynamicTablePage> {
  return request.get(`/api/data-models/${id}/data`, { params })
}

export function updateRow(id: number, rowId: number, data: Record<string, any>): Promise<void> {
  return request.put(`/api/data-models/${id}/data/${rowId}`, { data })
}

// ---- DDL Maintenance ----

export function modifyDdl(id: number, data: { ddl: string; primaryKey?: string }): Promise<void> {
  return request.put(`/api/data-models/${id}/ddl`, data)
}

// ---- Schedule ----

export function getSchedule(id: number): Promise<ScheduleConfig | null> {
  return request.get(`/api/data-models/${id}/schedule`)
}

export function saveSchedule(id: number, data: { cronExpression: string; enabled: boolean; scopeType: string }): Promise<ScheduleConfig> {
  return request.post(`/api/data-models/${id}/schedule`, data)
}

export function deleteSchedule(id: number): Promise<void> {
  return request.delete(`/api/data-models/${id}/schedule`)
}
