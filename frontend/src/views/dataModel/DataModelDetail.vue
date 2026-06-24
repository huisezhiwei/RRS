<template>
  <div class="model-detail-page">
    <div class="page-header">
      <div class="header-left">
        <el-button text @click="router.push('/data-models')">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h2>{{ model?.name || '加载中...' }}</h2>
        <el-tag :type="statusTagType(model?.status)" v-if="model">{{ statusLabel(model.status) }}</el-tag>
      </div>
      <div class="header-right">
        <span class="model-code">{{ model?.code }}</span>
        <span class="model-info" v-if="model?.libraryName">素材库: {{ model.libraryName }}</span>
        <span class="model-info">数据量: {{ model?.dataCount ?? 0 }} 条</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" v-if="model">
      <!-- Tab 1: Data Browsing -->
      <el-tab-pane label="数据浏览" name="data">
        <div class="tab-toolbar">
          <el-input v-model="dataSearch" placeholder="搜索数据" clearable style="width: 240px" @keyup.enter="fetchData" @clear="fetchData">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="fetchData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
        <el-table :data="tableData.rows" border stripe v-loading="loadingData" style="width: 100%" max-height="600">
          <el-table-column v-for="col in tableData.columns" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="80" fixed="right" align="center">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openEditDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="tableData.total > 0"
          class="data-pagination"
          layout="total, prev, pager, next"
          :total="tableData.total"
          :page-size="pageSize"
          :current-page="currentPage"
          @current-change="handlePageChange"
        />
      </el-tab-pane>

      <!-- Tab 2: Extraction Management -->
      <el-tab-pane label="抽取管理" name="extraction">
        <div class="tab-toolbar">
          <el-select v-model="extractScope" style="width: 120px">
            <el-option label="全量" value="FULL" />
            <el-option label="增量" value="INCREMENTAL" />
          </el-select>
          <el-button type="primary" :loading="extracting" @click="handleExtract">
            <el-icon><VideoPlay /></el-icon>
            手动抽取
          </el-button>
        </div>
        <ExtractionProgress
          ref="progressRef"
          :currentFile="progress.currentFile"
          :processedFiles="progress.processedFiles"
          :totalFiles="progress.totalFiles"
          :successRecords="progress.successRecords"
          :failedRecords="progress.failedRecords"
          :status="progress.status"
        />
        <div class="log-section" v-if="extractionLogs.length > 0">
          <h4>抽取历史</h4>
          <el-timeline>
            <el-timeline-item
              v-for="log in extractionLogs"
              :key="log.id"
              :type="log.status === 'SUCCESS' ? 'success' : log.status === 'FAILED' ? 'danger' : 'primary'"
              :timestamp="log.startedAt"
              placement="top"
            >
              <el-card shadow="never" class="log-card">
                <div class="log-summary">
                  <el-tag :type="log.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ log.status }}</el-tag>
                  <span>{{ log.triggerType }} / {{ log.scopeType }}</span>
                  <span>{{ log.successRecords }} 成功, {{ log.failedRecords }} 失败</span>
                </div>
                <pre v-if="log.logContent" class="log-detail">{{ log.logContent }}</pre>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-tab-pane>

      <!-- Tab 3: Rule Config -->
      <el-tab-pane label="规则配置" name="rules">
        <RuleConfigExcel
          v-if="libraryType === 'EXCEL'"
          v-model:ruleContent="ruleContent"
        />
        <RuleConfigImage
          v-else
          v-model:ruleContent="ruleContent"
          :agents="agents"
        />
        <div style="margin-top: 16px">
          <el-button type="primary" :loading="savingRule" @click="handleSaveRule">保存规则</el-button>
        </div>
      </el-tab-pane>

      <!-- Tab 4: Schedule -->
      <el-tab-pane label="定时调度" name="schedule">
        <ScheduleConfigPanel :modelId="modelId" :schedule="scheduleConfig" @updated="fetchSchedule" />
      </el-tab-pane>

      <!-- Tab 5: DDL Maintenance -->
      <el-tab-pane label="DDL 维护" name="ddl">
        <DdlEditor
          v-model:ddl="ddlContent"
          v-model:primaryKey="ddlPrimaryKey"
          @selectPrimaryKey="(name) => ddlPrimaryKey = name"
        />
        <div style="margin-top: 16px">
          <el-popconfirm title="修改 DDL 将备份旧表并创建新表，确认继续？" @confirm="handleModifyDdl">
            <template #reference>
              <el-button type="warning" :loading="modifyingDdl">应用 DDL 修改</el-button>
            </template>
          </el-popconfirm>
        </div>
      </el-tab-pane>
    </el-tabs>

    <RowEditDialog
      v-model="showEditDialog"
      :row="editingRow"
      :columns="tableData.columns"
      :modelId="modelId"
      @saved="fetchData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getDataModelById,
  getDynamicData,
  getExtractionLogs,
  getActiveRule,
  saveRule,
  getAvailableAgents,
  triggerExtraction,
  streamProgress,
  getSchedule,
  modifyDdl,
  type DataModel,
  type DynamicTablePage,
  type ExtractionLog,
  type AiAgent,
  type ScheduleConfig,
} from '@/api/dataModel'
import { getLibraries } from '@/api/materialLibrary'
import RuleConfigExcel from './components/RuleConfigExcel.vue'
import RuleConfigImage from './components/RuleConfigImage.vue'
import ExtractionProgress from './components/ExtractionProgress.vue'
import ScheduleConfigPanel from './components/ScheduleConfig.vue'
import DdlEditor from './components/DdlEditor.vue'
import RowEditDialog from './components/RowEditDialog.vue'

const route = useRoute()
const router = useRouter()
const modelId = Number(route.params.id)

const model = ref<DataModel>()
const activeTab = ref('data')
const libraryType = ref('')
const agents = ref<AiAgent[]>([])

// Data tab
const tableData = ref<DynamicTablePage>({ columns: [], rows: [], total: 0 })
const loadingData = ref(false)
const dataSearch = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

// Extraction tab
const extractScope = ref('INCREMENTAL')
const extracting = ref(false)
const extractionLogs = ref<ExtractionLog[]>([])
const progressRef = ref<InstanceType<typeof ExtractionProgress>>()
let eventSource: EventSource | null = null
const progress = ref({ currentFile: '', processedFiles: 0, totalFiles: 0, successRecords: 0, failedRecords: 0, status: '' })

// Rule tab
const ruleContent = ref('')
const savingRule = ref(false)

// Schedule tab
const scheduleConfig = ref<ScheduleConfig | null>(null)

// DDL tab
const ddlContent = ref('')
const ddlPrimaryKey = ref('')
const modifyingDdl = ref(false)

// Edit dialog
const showEditDialog = ref(false)
const editingRow = ref<Record<string, any>>()

onMounted(async () => {
  try {
    model.value = await getDataModelById(modelId)
    ddlContent.value = model.value.ddl || ''
    ddlPrimaryKey.value = model.value.primaryKey || ''

    // Determine library type
    if (model.value.libraryId) {
      const libs = await getLibraries()
      const lib = libs.find(l => l.id === model.value!.libraryId)
      libraryType.value = lib?.libraryType || ''
    }

    agents.value = await getAvailableAgents(modelId)
    fetchData()
    fetchLogs()
    fetchRule()
    fetchSchedule()
  } catch (error: any) {
    ElMessage.error(error.message || '加载模型数据失败')
  }
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})

const fetchData = async () => {
  loadingData.value = true
  try {
    tableData.value = await getDynamicData(modelId, {
      page: currentPage.value - 1,
      size: pageSize.value,
      keyword: dataSearch.value || undefined,
    })
  } catch (error: any) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    loadingData.value = false
  }
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

const fetchLogs = async () => {
  try {
    extractionLogs.value = await getExtractionLogs(modelId, { page: 0, size: 10 })
  } catch { /* ignore */ }
}

const fetchRule = async () => {
  try {
    const rule = await getActiveRule(modelId)
    if (rule) ruleContent.value = rule.ruleContent || ''
  } catch { /* ignore */ }
}

const fetchSchedule = async () => {
  try {
    scheduleConfig.value = await getSchedule(modelId)
  } catch { /* ignore */ }
}

const openEditDialog = (row: Record<string, any>) => {
  editingRow.value = { ...row }
  showEditDialog.value = true
}

const handleExtract = () => {
  extracting.value = true
  progressRef.value?.clearLogs()
  progress.value = { currentFile: '', processedFiles: 0, totalFiles: 0, successRecords: 0, failedRecords: 0, status: '' }

  eventSource = streamProgress(modelId)
  eventSource.addEventListener('progress', (e: MessageEvent) => {
    try {
      const data = JSON.parse(e.data)
      progress.value.currentFile = data.currentFile || ''
      progress.value.processedFiles = data.processedFiles || 0
      progress.value.totalFiles = data.totalFiles || 0
      progress.value.successRecords = data.successRecords || 0
      progress.value.failedRecords = data.failedRecords || 0
      if (data.log) progressRef.value?.addLog(data.log)
    } catch { /* ignore */ }
  })
  eventSource.addEventListener('complete', (e: MessageEvent) => {
    try {
      const data = JSON.parse(e.data)
      progress.value.status = 'complete'
      progress.value.successRecords = data.successRecords || progress.value.successRecords
      progress.value.failedRecords = data.failedRecords || progress.value.failedRecords
    } catch { /* ignore */ }
    eventSource?.close()
    eventSource = null
    extracting.value = false
    fetchData()
    fetchLogs()
  })
  eventSource.onerror = () => {
    extracting.value = false
    eventSource?.close()
    eventSource = null
  }

  triggerExtraction(modelId, { scopeType: extractScope.value })
    .catch((error: any) => {
      ElMessage.error(error.message || '抽取失败')
      extracting.value = false
    })
}

const handleSaveRule = async () => {
  savingRule.value = true
  try {
    const ruleType = libraryType.value === 'EXCEL' ? 'EXCEL_MAPPING' : 'IMAGE_PROMPT'
    await saveRule(modelId, { ruleType, ruleContent: ruleContent.value })
    ElMessage.success('规则已保存')
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    savingRule.value = false
  }
}

const handleModifyDdl = async () => {
  modifyingDdl.value = true
  try {
    await modifyDdl(modelId, { ddl: ddlContent.value, primaryKey: ddlPrimaryKey.value || undefined })
    ElMessage.success('DDL 已修改，旧表已备份')
    model.value = await getDataModelById(modelId)
    fetchData()
  } catch (error: any) {
    ElMessage.error(error.message || 'DDL 修改失败')
  } finally {
    modifyingDdl.value = false
  }
}

const statusTagType = (status?: string) => {
  switch (status) {
    case 'READY': return 'success'
    case 'INITIALIZING': return 'warning'
    case 'ERROR': return 'danger'
    default: return 'info'
  }
}

const statusLabel = (status?: string) => {
  switch (status) {
    case 'UNINITIALIZED': return '未初始化'
    case 'INITIALIZING': return '初始化中'
    case 'READY': return '就绪'
    case 'ERROR': return '异常'
    default: return status || '-'
  }
}
</script>

<style scoped>
.model-detail-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  font-size: 20px;
  color: #303133;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: #909399;
}

.model-code {
  font-family: monospace;
  font-weight: 600;
  color: #606266;
}

.tab-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

.data-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.log-section {
  margin-top: 24px;
}

.log-section h4 {
  margin-bottom: 16px;
}

.log-card {
  padding: 0;
}

.log-summary {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}

.log-detail {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  white-space: pre-wrap;
  max-height: 150px;
  overflow-y: auto;
}
</style>
