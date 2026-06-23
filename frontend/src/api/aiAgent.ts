import request from './request'

export interface AiAgentDTO {
  id: number
  code: string
  name: string
  description: string
  agentType: string
  credentialId: number | null
  credentialName: string | null
  modelName: string | null
  systemPrompt: string | null
  temperature: number | null
  maxTokens: number | null
  topP: number | null
  createdAt: string
  updatedAt: string
}

export interface AiAgentConfigDTO {
  credentialId?: number
  modelName?: string
  systemPrompt?: string
  temperature?: number
  maxTokens?: number
  topP?: number
}

export function getAiAgents(): Promise<AiAgentDTO[]> {
  return request.get('/api/ai-agents')
}

export function getAiAgent(id: number): Promise<AiAgentDTO> {
  return request.get(`/api/ai-agents/${id}`)
}

export function configureAiAgent(id: number, config: AiAgentConfigDTO): Promise<AiAgentDTO> {
  return request.put(`/api/ai-agents/${id}/config`, config)
}
