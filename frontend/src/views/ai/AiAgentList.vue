<template>
  <div class="ai-agent-page">
    <div class="page-header">
      <h2>AI 助手</h2>
    </div>

    <el-row :gutter="24" v-loading="loading">
      <el-col :span="8" v-for="agent in agents" :key="agent.id">
        <el-card class="agent-card" shadow="hover" @click="openConfig(agent)">
          <div class="agent-icon">
            <el-icon :size="48" color="#409eff"><ChatDotRound /></el-icon>
          </div>
          <h3>{{ agent.name }}</h3>
          <p class="agent-desc">{{ agent.description || '暂无描述' }}</p>
          <div class="agent-status">
            <el-tag v-if="agent.credentialId" type="success" size="small">已配置</el-tag>
            <el-tag v-else type="warning" size="small">未配置</el-tag>
            <span v-if="agent.modelName" class="model-name">{{ agent.modelName }}</span>
          </div>
          <div class="agent-actions">
            <el-button type="primary" size="small" @click.stop="openChat(agent)" :disabled="!agent.credentialId">
              对话
            </el-button>
            <el-button size="small" @click.stop="openConfig(agent)">配置</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Config Dialog -->
    <el-dialog v-model="configVisible" title="配置 AI 助手" width="600px" destroy-on-close>
      <el-form :model="configForm" label-width="140px" v-if="selectedAgent">
        <el-form-item label="凭证 (LLM)">
          <el-select v-model="configForm.credentialId" placeholder="选择 LLM 凭证" style="width: 100%" @change="onCredentialChange">
            <el-option v-for="cred in llmCredentials" :key="cred.id" :label="cred.name" :value="cred.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="configForm.modelName" filterable allow-create placeholder="选择或输入模型名称" style="width: 100%" :loading="modelsLoading">
            <el-option v-for="m in modelList" :key="m" :label="m" :value="m" />
          </el-select>
          <el-button size="small" style="margin-top: 8px" @click="fetchModels" :loading="modelsLoading" :disabled="!configForm.credentialId">
            获取模型列表
          </el-button>
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="configForm.systemPrompt" type="textarea" :rows="4" placeholder="You are a helpful assistant..." />
        </el-form-item>
        <el-form-item label="温度">
          <el-slider v-model="configForm.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="最大Token数">
          <el-input-number v-model="configForm.maxTokens" :min="100" :max="128000" :step="100" />
        </el-form-item>
        <el-form-item label="Top P">
          <el-slider v-model="configForm.topP" :min="0" :max="1" :step="0.05" show-input />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveConfig" :loading="saveLoading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAiAgents, configureAiAgent, type AiAgentDTO, type AiAgentConfigDTO } from '@/api/aiAgent'
import { getCredentials, fetchLlmModels, type CredentialDTO } from '@/api/credential'

const router = useRouter()
const loading = ref(false)
const agents = ref<AiAgentDTO[]>([])
const configVisible = ref(false)
const selectedAgent = ref<AiAgentDTO | null>(null)
const llmCredentials = ref<CredentialDTO[]>([])
const modelList = ref<string[]>([])
const modelsLoading = ref(false)
const saveLoading = ref(false)

const configForm = ref<AiAgentConfigDTO>({
  credentialId: undefined,
  modelName: '',
  systemPrompt: '',
  temperature: 0.7,
  maxTokens: 4096,
  topP: 1.0,
})

async function loadAgents() {
  loading.value = true
  try {
    agents.value = await getAiAgents()
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadCredentials() {
  try {
    llmCredentials.value = await getCredentials('LLM')
  } catch { /* ignore */ }
}

function openConfig(agent: AiAgentDTO) {
  selectedAgent.value = agent
  configForm.value = {
    credentialId: agent.credentialId || undefined,
    modelName: agent.modelName || '',
    systemPrompt: agent.systemPrompt || '',
    temperature: agent.temperature ?? 0.7,
    maxTokens: agent.maxTokens ?? 4096,
    topP: agent.topP ?? 1.0,
  }
  modelList.value = []
  configVisible.value = true
  loadCredentials()
}

function openChat(agent: AiAgentDTO) {
  router.push(`/ai/chat/${agent.id}`)
}

async function onCredentialChange(credId: number) {
  if (credId) {
    await fetchModels()
  }
}

async function fetchModels() {
  if (!configForm.value.credentialId) return
  modelsLoading.value = true
  try {
    modelList.value = await fetchLlmModels(configForm.value.credentialId)
    ElMessage.success(`找到 ${modelList.value.length} 个模型`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    modelsLoading.value = false
  }
}

async function handleSaveConfig() {
  if (!selectedAgent.value) return
  saveLoading.value = true
  try {
    await configureAiAgent(selectedAgent.value.id, configForm.value)
    ElMessage.success('配置已保存')
    configVisible.value = false
    await loadAgents()
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    saveLoading.value = false
  }
}

onMounted(loadAgents)
</script>

<style scoped>
.ai-agent-page {
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.agent-card {
  text-align: center;
  cursor: pointer;
  transition: transform 0.2s;
  margin-bottom: 24px;
}
.agent-card:hover {
  transform: translateY(-4px);
}
.agent-icon {
  margin-bottom: 12px;
}
.agent-card h3 {
  margin: 8px 0;
  font-size: 18px;
  color: #303133;
}
.agent-desc {
  color: #909399;
  font-size: 13px;
  min-height: 40px;
  margin-bottom: 12px;
}
.agent-status {
  margin-bottom: 12px;
}
.model-name {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.agent-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}
</style>
