<template>
  <div class="extraction-progress">
    <div class="progress-bar-section" v-if="totalFiles > 0">
      <div class="progress-info">
        <span>{{ currentFile || '准备中...' }}</span>
        <span>{{ processedFiles }} / {{ totalFiles }} 文件</span>
      </div>
      <el-progress
        :percentage="totalFiles > 0 ? Math.round((processedFiles / totalFiles) * 100) : 0"
        :status="status === 'complete' ? 'success' : status === 'error' ? 'exception' : ''"
      />
      <div class="progress-stats">
        <el-tag type="success" size="small">成功: {{ successRecords }} 条</el-tag>
        <el-tag type="danger" size="small" v-if="failedRecords > 0">失败: {{ failedRecords }} 条</el-tag>
      </div>
    </div>
    <div class="log-section" v-if="logs.length > 0">
      <div class="log-header">
        <span>抽取日志</span>
        <el-button size="small" text @click="scrollToBottom">滚动到底部</el-button>
      </div>
      <div class="log-content" ref="logContainer">
        <div v-for="(log, i) in logs" :key="i" class="log-line" :class="{ 'log-error': log.includes('失败') }">
          {{ log }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

defineProps<{
  currentFile?: string
  processedFiles: number
  totalFiles: number
  successRecords: number
  failedRecords: number
  status?: string
}>()

const logs = ref<string[]>([])
const logContainer = ref<HTMLElement>()

const addLog = (msg: string) => {
  logs.value.push(msg)
  nextTick(() => scrollToBottom())
}

const scrollToBottom = () => {
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
}

const clearLogs = () => {
  logs.value = []
}

defineExpose({ addLog, clearLogs })
</script>

<style scoped>
.extraction-progress {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.progress-stats {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 14px;
}

.log-content {
  max-height: 300px;
  overflow-y: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.log-line {
  white-space: pre-wrap;
  word-break: break-all;
}

.log-error {
  color: #f56c6c;
}
</style>
