<template>
  <div class="wizard-page">
    <div class="wizard-header">
      <el-button text @click="router.push('/data-models')">
        <el-icon><ArrowLeft /></el-icon>
        返回列表
      </el-button>
      <h2>模型初始化向导</h2>
    </div>

    <el-steps :active="currentStep" finish-status="success" align-center class="wizard-steps">
      <el-step title="选择素材库" />
      <el-step title="确认表结构" />
      <el-step title="配置抽取规则" />
      <el-step title="试抽取" />
      <el-step title="执行抽取" />
      <el-step title="完成" />
    </el-steps>

    <div class="wizard-content">
      <!-- Step 1: Select Library + Init Method -->
      <div v-show="currentStep === 0" class="step-content">
        <el-form :model="step1" label-width="120px" style="max-width: 600px">
          <el-form-item label="素材库" required>
            <el-select v-model="step1.libraryId" placeholder="选择素材库" style="width: 100%" @change="onLibraryChange">
              <el-option v-for="lib in libraries" :key="lib.id" :label="`${lib.name} (${lib.libraryType === 'EXCEL' ? 'Excel' : '图片'})`" :value="lib.id!" />
            </el-select>
          </el-form-item>
          <el-form-item label="初始化方式" v-if="step1.libraryId">
            <el-radio-group v-model="step1.initMethod">
              <el-radio value="ai">AI 辅助生成</el-radio>
              <el-radio value="manual">手动编写</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="AI Agent" v-if="step1.initMethod === 'ai' && step1.libraryId">
            <el-select v-model="step1.agentId" placeholder="选择 Agent" style="width: 100%" v-loading="loadingAgents">
              <el-option v-for="a in agents" :key="a.id" :label="`${a.name} (${a.modelName || ''})` " :value="a.id!" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: DDL -->
      <div v-show="currentStep === 1" class="step-content">
        <div class="step-actions" v-if="step1.initMethod === 'ai'">
          <el-button type="primary" :loading="generating" @click="handleGenerateDdl">
            <el-icon><MagicStick /></el-icon>
            AI 生成 DDL
          </el-button>
        </div>
        <DdlEditor
          v-model:ddl="step2.ddl"
          v-model:primaryKey="step2.primaryKey"
          @selectPrimaryKey="(name) => step2.primaryKey = name"
        />
        <div class="step-footer">
          <el-button type="primary" :loading="confirming" @click="handleConfirmDdl" :disabled="!step2.ddl">
            确认建表
          </el-button>
        </div>
      </div>

      <!-- Step 3: Extraction Rules -->
      <div v-show="currentStep === 2" class="step-content">
        <RuleConfigExcel
          v-if="libraryType === 'EXCEL'"
          v-model:ruleContent="step3.ruleContent"
        />
        <RuleConfigImage
          v-else
          v-model:ruleContent="step3.ruleContent"
          :agents="agents"
        />
        <div class="step-footer">
          <el-button type="primary" :loading="savingRule" @click="handleSaveRule" :disabled="!step3.ruleContent">
            保存规则
          </el-button>
        </div>
      </div>

      <!-- Step 4: Trial Extraction Choice -->
      <div v-show="currentStep === 3" class="step-content">
        <div class="trial-choice">
          <h3>是否进行试抽取？</h3>
          <p class="trial-hint">试抽取可以帮助您验证规则是否正确</p>
          <div class="trial-buttons">
            <el-card shadow="hover" class="trial-card" @click="startTrialExtraction">
              <el-icon :size="40" color="#409eff"><VideoPlay /></el-icon>
              <span>立即试抽取</span>
            </el-card>
            <el-card shadow="hover" class="trial-card" @click="currentStep = 5">
              <el-icon :size="40" color="#909399"><DArrowRight /></el-icon>
              <span>跳过，直接完成</span>
            </el-card>
          </div>
        </div>
      </div>

      <!-- Step 5: Execute Extraction -->
      <div v-show="currentStep === 4" class="step-content">
        <div class="file-selection" v-if="!extractionStarted">
          <div class="file-header">
            <span>选择要抽取的文件</span>
            <el-radio-group v-model="step5.scopeType" size="small">
              <el-radio-button value="FULL">全量</el-radio-button>
              <el-radio-button value="INCREMENTAL">增量</el-radio-button>
            </el-radio-group>
          </div>
          <el-checkbox-group v-model="step5.selectedFileIds">
            <el-checkbox v-for="f in extractFiles" :key="f.id" :value="f.id!" class="file-checkbox">
              {{ f.fileName }}
            </el-checkbox>
          </el-checkbox-group>
          <el-button type="primary" @click="startExtraction" :disabled="step5.selectedFileIds.length === 0" style="margin-top: 12px">
            开始抽取
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
      </div>

      <!-- Step 6: Complete -->
      <div v-show="currentStep === 5" class="step-content">
        <el-result icon="success" title="初始化完成" :sub-title="`模型 ${model?.name} 已成功初始化`">
          <template #extra>
            <el-button type="primary" @click="goToDetail">查看模型详情</el-button>
            <el-button @click="router.push('/data-models')">返回列表</el-button>
          </template>
        </el-result>
      </div>
    </div>

    <div class="wizard-footer" v-if="currentStep < 5">
      <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
      <el-button
        v-if="currentStep === 0"
        type="primary"
        :disabled="!canProceedStep0"
        @click="currentStep++"
      >
        下一步
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getDataModelById,
  getAvailableAgents,
  generateDdl,
  confirmDdl,
  saveRule,
  triggerExtraction,
  streamProgress,
  getExtractableFiles,
  type DataModel,
  type AiAgent,
  type MaterialFile,
} from '@/api/dataModel'
import { getLibraries, type MaterialLibrary } from '@/api/materialLibrary'
import DdlEditor from './components/DdlEditor.vue'
import RuleConfigExcel from './components/RuleConfigExcel.vue'
import RuleConfigImage from './components/RuleConfigImage.vue'
import ExtractionProgress from './components/ExtractionProgress.vue'

const route = useRoute()
const router = useRouter()
const modelId = Number(route.params.id)

const model = ref<DataModel>()
const libraries = ref<MaterialLibrary[]>([])
const agents = ref<AiAgent[]>([])
const extractFiles = ref<MaterialFile[]>([])
const loadingAgents = ref(false)
const libraryType = ref<string>('')
const generating = ref(false)
const confirming = ref(false)
const savingRule = ref(false)
const extractionStarted = ref(false)
const currentStep = ref(0)
const progressRef = ref<InstanceType<typeof ExtractionProgress>>()
let eventSource: EventSource | null = null

const step1 = ref({
  libraryId: null as number | null,
  initMethod: 'ai' as string,
  agentId: null as number | null,
})

const step2 = ref({
  ddl: '',
  primaryKey: '',
})

const step3 = ref({
  ruleContent: '',
})

const step5 = ref({
  scopeType: 'FULL',
  selectedFileIds: [] as number[],
})

const progress = ref({
  currentFile: '',
  processedFiles: 0,
  totalFiles: 0,
  successRecords: 0,
  failedRecords: 0,
  status: '',
})

const canProceedStep0 = computed(() => {
  if (!step1.value.libraryId) return false
  if (step1.value.initMethod === 'ai' && !step1.value.agentId) return false
  return true
})

onMounted(async () => {
  try {
    model.value = await getDataModelById(modelId)
    if (model.value.libraryId) {
      step1.value.libraryId = model.value.libraryId
    }
    libraries.value = await getLibraries()
    agents.value = await getAvailableAgents(modelId)
  } catch (error: any) {
    ElMessage.error(error.message || '加载数据失败')
  }
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})

const onLibraryChange = (libId: number) => {
  const lib = libraries.value.find(l => l.id === libId)
  libraryType.value = lib?.libraryType || ''
}

const handleGenerateDdl = async () => {
  if (!step1.value.agentId) {
    ElMessage.warning('请先选择 Agent')
    return
  }
  generating.value = true
  try {
    step2.value.ddl = await generateDdl(modelId, { agentId: step1.value.agentId })
    ElMessage.success('DDL 已生成，请检查并修改后确认建表')
  } catch (error: any) {
    ElMessage.error(error.message || 'DDL 生成失败')
  } finally {
    generating.value = false
  }
}

const handleConfirmDdl = async () => {
  confirming.value = true
  try {
    await confirmDdl(modelId, { ddl: step2.value.ddl, primaryKey: step2.value.primaryKey || undefined })
    ElMessage.success('建表成功')
    currentStep.value = 2
  } catch (error: any) {
    ElMessage.error(error.message || '建表失败')
  } finally {
    confirming.value = false
  }
}

const handleSaveRule = async () => {
  savingRule.value = true
  try {
    const ruleType = libraryType.value === 'EXCEL' ? 'EXCEL_MAPPING' : 'IMAGE_PROMPT'
    await saveRule(modelId, { ruleType, ruleContent: step3.value.ruleContent })
    ElMessage.success('规则已保存')
    currentStep.value = 3
  } catch (error: any) {
    ElMessage.error(error.message || '规则保存失败')
  } finally {
    savingRule.value = false
  }
}

const startTrialExtraction = async () => {
  currentStep.value = 4
  try {
    extractFiles.value = await getExtractableFiles(modelId)
  } catch { extractFiles.value = [] }
}

const startExtraction = () => {
  extractionStarted.value = true
  progressRef.value?.clearLogs()
  progress.value = { currentFile: '', processedFiles: 0, totalFiles: 0, successRecords: 0, failedRecords: 0, status: '' }

  // Connect SSE
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
      progressRef.value?.addLog(`抽取完成: 成功 ${data.successRecords} 条, 失败 ${data.failedRecords} 条`)
    } catch { /* ignore */ }
    eventSource?.close()
    eventSource = null
  })
  eventSource.onerror = () => {
    progress.value.status = 'error'
    progressRef.value?.addLog('连接断开')
    eventSource?.close()
    eventSource = null
  }

  // Trigger extraction
  triggerExtraction(modelId, {
    scopeType: step5.value.scopeType,
    fileIds: step5.value.selectedFileIds.length > 0 ? step5.value.selectedFileIds : undefined,
  }).catch((error: any) => {
    ElMessage.error(error.message || '抽取失败')
  })
}

const goToDetail = () => {
  router.push(`/data-models/${modelId}`)
}
</script>

<style scoped>
.wizard-page {
  max-width: 900px;
  margin: 0 auto;
}

.wizard-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.wizard-header h2 {
  font-size: 20px;
  color: #303133;
}

.wizard-steps {
  margin-bottom: 32px;
}

.wizard-content {
  min-height: 400px;
}

.step-content {
  padding: 20px 0;
}

.step-actions {
  margin-bottom: 16px;
}

.step-footer {
  margin-top: 20px;
}

.trial-choice {
  text-align: center;
}

.trial-choice h3 {
  font-size: 18px;
  margin-bottom: 8px;
}

.trial-hint {
  color: #909399;
  margin-bottom: 24px;
}

.trial-buttons {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.trial-card {
  width: 180px;
  cursor: pointer;
  text-align: center;
  transition: transform 0.2s;
}

.trial-card:hover {
  transform: translateY(-4px);
}

.trial-card span {
  display: block;
  margin-top: 12px;
  font-size: 14px;
}

.file-selection {
  margin-bottom: 20px;
}

.file-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}

.file-checkbox {
  display: block;
  margin-bottom: 4px;
}

.wizard-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e4e7ed;
}
</style>
