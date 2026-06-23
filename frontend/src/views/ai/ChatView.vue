<template>
  <div class="chat-view">
    <!-- Sidebar: sessions -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" size="small" @click="handleNewSession" style="width: 100%">
          <el-icon><Plus /></el-icon> 新建对话
        </el-button>
      </div>
      <div class="session-list">
        <div
          v-for="s in sessions" :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="selectSession(s.id)"
        >
          <span class="session-title">{{ s.title }}</span>
          <el-button size="small" text type="danger" @click.stop="handleDeleteSession(s.id)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- Main chat area -->
    <div class="chat-main">
      <div class="chat-messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="empty-hint">
          开始对话...
        </div>
        <div v-for="msg in messages" :key="msg.id || msg._tempId"
             class="message-row" :class="msg.role.toLowerCase()">
          <div class="message-avatar">
            <el-icon v-if="msg.role === 'USER'" :size="20"><User /></el-icon>
            <el-icon v-else :size="20"><Monitor /></el-icon>
          </div>
          <div class="message-content">
            <div v-if="msg.fileName" class="file-tag">
              <el-icon><Document /></el-icon> {{ msg.fileName }}
            </div>
            <div v-if="msg.role === 'ASSISTANT'" class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
            <div v-else class="plain-text">{{ msg.content }}</div>
            <div v-if="msg._streaming" class="typing-indicator">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- Input area -->
      <div class="chat-input">
        <div v-if="attachedFile" class="attached-file">
          <el-tag closable @close="attachedFile = null">
            <el-icon><Document /></el-icon> {{ attachedFile.name }}
          </el-tag>
        </div>
        <div class="input-row">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleFileChange"
            accept=".txt,.md,.json,.csv,.xml,.yaml,.yml,.log,.java,.py,.js,.ts,.html,.css,.sql"
          >
            <el-button :icon="Paperclip" circle size="small" />
          </el-upload>
          <el-input
            v-model="inputText"
            placeholder="输入消息..."
            :disabled="streaming"
            @keyup.enter="handleSend"
            autofocus
          />
          <el-button type="primary" @click="handleSend" :loading="streaming" :disabled="!inputText.trim() && !attachedFile">
            发送
          </el-button>
          <el-button v-if="streaming" type="danger" @click="handleStop">停止</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Paperclip } from '@element-plus/icons-vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import {
  getChatSessions, getChatSession, createChatSession, deleteChatSession,
  saveAssistantResponse, streamChatFetch,
  type ChatSessionDTO, type ChatMessageDTO, type ChatRequest
} from '@/api/chat'

const route = useRoute()
const router = useRouter()
const agentId = Number(route.params.agentId)

const sessions = ref<ChatSessionDTO[]>([])
const currentSessionId = ref<number | null>(null)
const messages = ref<(ChatMessageDTO & { _tempId?: string; _streaming?: boolean })[]>([])
const inputText = ref('')
const streaming = ref(false)
const attachedFile = ref<{ name: string; content: string } | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
let streamController: { abort: () => void } | null = null

// Configure marked with highlight.js
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

async function loadSessions() {
  try {
    sessions.value = await getChatSessions(agentId)
  } catch { /* ignore */ }
}

async function selectSession(sessionId: number) {
  currentSessionId.value = sessionId
  try {
    const session = await getChatSession(sessionId)
    messages.value = session.messages || []
    await nextTick()
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function handleNewSession() {
  try {
    const session = await createChatSession(agentId)
    sessions.value.unshift(session)
    await selectSession(session.id)
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function handleDeleteSession(sessionId: number) {
  try {
    await ElMessageBox.confirm('确认删除此对话？', '确认', { type: 'warning' })
    await deleteChatSession(sessionId)
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
    }
    ElMessage.success('已删除')
  } catch { /* cancelled */ }
}

function handleFileChange(file: any) {
  const rawFile = file.raw
  const reader = new FileReader()
  reader.onload = (e) => {
    attachedFile.value = {
      name: rawFile.name,
      content: e.target?.result as string
    }
  }
  reader.readAsText(rawFile)
}

async function handleSend() {
  if (!inputText.value.trim() && !attachedFile.value) return
  if (!currentSessionId.value) {
    // Auto-create session
    const session = await createChatSession(agentId)
    sessions.value.unshift(session)
    currentSessionId.value = session.id
  }

  const userContent = inputText.value.trim()
  const fileName = attachedFile.value?.name
  const fileContent = attachedFile.value?.content

  // Add user message to UI immediately
  const userMsg: ChatMessageDTO & { _tempId?: string } = {
    id: 0,
    sessionId: currentSessionId.value,
    role: 'USER',
    content: userContent,
    fileName,
    createdAt: new Date().toISOString(),
    _tempId: 'temp-' + Date.now(),
  }
  messages.value.push(userMsg)
  inputText.value = ''
  attachedFile.value = null

  // Add placeholder for assistant response
  const assistantMsg: ChatMessageDTO & { _tempId?: string; _streaming?: boolean } = {
    id: 0,
    sessionId: currentSessionId.value,
    role: 'ASSISTANT',
    content: '',
    createdAt: new Date().toISOString(),
    _tempId: 'temp-assistant-' + Date.now(),
    _streaming: true,
  }
  messages.value.push(assistantMsg)
  await nextTick()
  scrollToBottom()

  // Stream
  streaming.value = true
  const request: ChatRequest = {
    sessionId: currentSessionId.value,
    message: userContent,
    fileName,
    fileContent,
  }

  const assistantIdx = messages.value.length - 1

  streamController = streamChatFetch(
    request,
    (content) => {
      messages.value[assistantIdx].content += content
      nextTick(() => scrollToBottom())
    },
    async () => {
      messages.value[assistantIdx]._streaming = false
      streaming.value = false
      // Save assistant response to DB
      try {
        await saveAssistantResponse(currentSessionId.value!, messages.value[assistantIdx].content)
        await loadSessions() // refresh session list (title may have changed)
      } catch { /* ignore */ }
    },
    (error) => {
      messages.value[assistantIdx].content += `\n\n[错误: ${error}]`
      messages.value[assistantIdx]._streaming = false
      streaming.value = false
    }
  )
}

function handleStop() {
  if (streamController) {
    streamController.abort()
    streamController = null
  }
  streaming.value = false
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg && lastMsg._streaming) {
    lastMsg._streaming = false
  }
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

onMounted(async () => {
  await loadSessions()
  if (sessions.value.length > 0) {
    await selectSession(sessions.value[0].id)
  }
})
</script>

<style scoped>
.chat-view {
  display: flex;
  height: calc(100vh - 120px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
}

/* Sidebar */
.chat-sidebar {
  width: 260px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fafafa;
}
.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}
.session-item:hover {
  background: #ecf5ff;
}
.session-item.active {
  background: #409eff;
  color: #fff;
}
.session-item.active .el-button {
  color: #fff;
}
.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

/* Main chat */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.empty-hint {
  text-align: center;
  color: #c0c4cc;
  margin-top: 100px;
  font-size: 16px;
}

.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 80%;
}
.message-row.user {
  margin-left: auto;
  flex-direction: row-reverse;
}
.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.message-row.user .message-avatar {
  background: #409eff;
  color: #fff;
}
.message-row.assistant .message-avatar {
  background: #67c23a;
  color: #fff;
}

.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  max-width: 100%;
  word-break: break-word;
}
.message-row.user .message-content {
  background: #ecf5ff;
  border-top-right-radius: 2px;
}
.message-row.assistant .message-content {
  background: #f5f7fa;
  border-top-left-radius: 2px;
}

.file-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.plain-text {
  white-space: pre-wrap;
  line-height: 1.6;
}

.markdown-body {
  line-height: 1.6;
  font-size: 14px;
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

.typing-indicator {
  display: inline-flex;
  gap: 4px;
  margin-top: 4px;
}
.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #909399;
  animation: typing 1.4s infinite;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; }
  30% { opacity: 1; }
}

/* Input area */
.chat-input {
  border-top: 1px solid #e4e7ed;
  padding: 12px 16px;
  background: #fff;
}
.attached-file {
  margin-bottom: 8px;
}
.input-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.input-row .el-input {
  flex: 1;
}
</style>
