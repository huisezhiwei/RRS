import request from './request'
import axios from 'axios'

export interface ChatMessageDTO {
  id: number
  sessionId: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  fileName?: string
  createdAt: string
}

export interface ChatSessionDTO {
  id: number
  agentId: number
  title: string
  messages?: ChatMessageDTO[]
  createdAt: string
  updatedAt: string
}

export interface ChatRequest {
  sessionId: number
  message: string
  fileName?: string
  fileContent?: string
}

export function getChatSessions(agentId: number): Promise<ChatSessionDTO[]> {
  return request.get('/api/chat/sessions', { params: { agentId } })
}

export function getChatSession(sessionId: number): Promise<ChatSessionDTO> {
  return request.get(`/api/chat/sessions/${sessionId}`)
}

export function createChatSession(agentId: number): Promise<ChatSessionDTO> {
  return request.post('/api/chat/sessions', null, { params: { agentId } })
}

export function deleteChatSession(sessionId: number): Promise<void> {
  return request.delete(`/api/chat/sessions/${sessionId}`)
}

export function saveAssistantResponse(sessionId: number, content: string): Promise<ChatMessageDTO> {
  return request.post('/api/chat/save-response', { sessionId, message: content })
}

/**
 * Stream chat via SSE. Returns an EventSource-like controller.
 */
export function streamChat(
  chatRequest: ChatRequest,
  onMessage: (content: string) => void,
  onDone: () => void,
  onError: (error: string) => void
): { abort: () => void } {
  const controller = new AbortController()

  axios.post('/api/chat/stream', chatRequest, {
    responseType: 'text',
    headers: { 'Accept': 'text/event-stream' },
    signal: controller.signal,
    // Use transformResponse to handle SSE stream
    transformResponse: (data: string) => data,
  }).then(response => {
    const text = response.data as string
    const lines = text.split('\n')

    for (const line of lines) {
      if (line.startsWith('event:message')) {
        // next line is data:
        continue
      }
      if (line.startsWith('data:')) {
        const data = line.substring(5).trim()
        if (data === '[DONE]') {
          onDone()
          return
        }
        onMessage(data)
      }
      if (line.startsWith('event:error')) {
        continue
      }
      if (line.startsWith('event:done')) {
        onDone()
        return
      }
    }
    onDone()
  }).catch(err => {
    if (err.name !== 'CanceledError' && err.name !== 'AbortError') {
      onError(err.message || 'Stream failed')
    }
  })

  return { abort: () => controller.abort() }
}

/**
 * Stream chat using fetch + ReadableStream for true streaming.
 */
export function streamChatFetch(
  chatRequest: ChatRequest,
  onMessage: (content: string) => void,
  onDone: () => void,
  onError: (error: string) => void
): { abort: () => void } {
  const controller = new AbortController()

  fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
    body: JSON.stringify(chatRequest),
    signal: controller.signal,
  }).then(async response => {
    if (!response.ok) {
      onError(`HTTP ${response.status}`)
      return
    }
    const reader = response.body?.getReader()
    if (!reader) {
      onError('No response body')
      return
    }
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      let currentEvent = 'message'
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          const data = line.substring(5).trim()
          if (currentEvent === 'error') {
            try {
              const errObj = JSON.parse(data)
              onError(errObj.error || data)
            } catch {
              onError(data)
            }
            return
          }
          if (data === '[DONE]') {
            onDone()
            return
          }
          onMessage(data)
        }
      }
    }
    onDone()
  }).catch(err => {
    if (err.name !== 'AbortError') {
      onError(err.message || 'Stream failed')
    }
  })

  return { abort: () => controller.abort() }
}
