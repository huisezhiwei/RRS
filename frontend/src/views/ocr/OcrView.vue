<template>
  <div class="ocr-page">
    <div class="page-header">
      <el-button @click="router.push('/ai/agents')" :icon="ArrowLeft" circle size="small" />
      <div class="header-info">
        <h2>OCR 图片解析助手</h2>
        <p v-if="agent">{{ agent.description }}</p>
      </div>
    </div>

    <el-row :gutter="24">
      <!-- Left: Upload + Preview -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span>图片上传</span></template>
          <el-upload
            drag
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleFileChange"
            accept=".jpg,.jpeg,.png,.bmp,.tiff,.tif"
          >
            <el-icon :size="40" color="#c0c4cc"><UploadFilled /></el-icon>
            <div class="upload-text">拖拽图片到此处，或 <em>点击上传</em></div>
            <div class="upload-tip">支持 JPG / PNG / BMP / TIFF 格式</div>
          </el-upload>

          <div v-if="previewUrl" class="image-preview">
            <img :src="previewUrl" alt="预览" />
          </div>
        </el-card>
      </el-col>

      <!-- Right: Config + Result -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span>识别配置</span></template>
          <el-form label-width="100px">
            <el-form-item label="OCR 模式">
              <el-radio-group v-model="ocrMode">
                <el-radio value="LOCAL">本地 Tesseract</el-radio>
                <el-radio value="LLM" :disabled="!agent?.credentialId">LLM Vision</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                @click="handleProcess"
                :loading="processing"
                :disabled="!selectedFile"
              >
                开始识别
              </el-button>
              <el-button v-if="result" @click="handleDownload" type="success">
                <el-icon><Download /></el-icon> 下载 MD 文件
              </el-button>
            </el-form-item>
          </el-form>

          <div v-if="ocrMode === 'LLM' && !agent?.credentialId" class="llm-hint">
            <el-alert title="LLM 模式需要先为 Agent 配置 LLM 凭证，请返回 AI 助手页进行配置。" type="warning" :closable="false" />
          </div>
        </el-card>

        <el-card v-if="result" shadow="never" class="result-card">
          <template #header>
            <div class="result-header">
              <span>识别结果</span>
              <el-tag size="small">{{ result.fileName }}</el-tag>
            </div>
          </template>
          <div class="markdown-body" v-html="renderMarkdown(result.mdContent)"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, UploadFilled, Download } from '@element-plus/icons-vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { getAiAgent, type AiAgentDTO } from '@/api/aiAgent'
import { processOcr, type OcrResultDTO } from '@/api/ocr'

const route = useRoute()
const router = useRouter()
const agentId = computed(() => route.query.agentId ? Number(route.query.agentId) : undefined)

const agent = ref<AiAgentDTO | null>(null)
const ocrMode = ref<'LOCAL' | 'LLM'>('LOCAL')
const selectedFile = ref<File | null>(null)
const previewUrl = ref<string>('')
const processing = ref(false)
const result = ref<OcrResultDTO | null>(null)

marked.setOptions({
  highlight: (code: string, lang: string) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
})

function renderMarkdown(content: string): string {
  if (!content) return ''
  try {
    return marked.parse(content) as string
  } catch {
    return content
  }
}

function handleFileChange(file: any) {
  selectedFile.value = file.raw
  previewUrl.value = URL.createObjectURL(file.raw)
  result.value = null
}

async function handleProcess() {
  if (!selectedFile.value) return
  processing.value = true
  try {
    result.value = await processOcr(selectedFile.value, ocrMode.value, agentId.value)
    ElMessage.success('识别完成')
  } catch (e: any) {
    ElMessage.error(e.message || '识别失败')
  } finally {
    processing.value = false
  }
}

function handleDownload() {
  if (!result.value) return
  const blob = new Blob([result.value.mdContent], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const mdName = result.value.fileName.replace(/\.[^.]+$/, '') + '.md'
  a.href = url
  a.download = mdName
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('MD 文件已下载')
}

onMounted(async () => {
  if (agentId.value) {
    try {
      agent.value = await getAiAgent(agentId.value)
    } catch { /* ignore */ }
  }
})
</script>

<style scoped>
.ocr-page {
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.header-info h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.header-info p {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}
.upload-text {
  margin-top: 8px;
  color: #606266;
}
.upload-tip {
  margin-top: 4px;
  color: #c0c4cc;
  font-size: 12px;
}
.image-preview {
  margin-top: 16px;
  text-align: center;
}
.image-preview img {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}
.llm-hint {
  margin-top: 8px;
}
.result-card {
  margin-top: 16px;
}
.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.markdown-body {
  line-height: 1.6;
  font-size: 14px;
  white-space: pre-wrap;
}
.markdown-body :deep(pre) {
  background: #1e1e1e;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}
.markdown-body :deep(code) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
.markdown-body :deep(p) {
  margin: 4px 0;
}
.markdown-body :deep(ul), .markdown-body :deep(ol) {
  padding-left: 20px;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 6px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #f5f7fa;
}
</style>
