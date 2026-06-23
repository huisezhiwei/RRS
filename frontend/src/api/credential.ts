import request from './request'

export interface CredentialDTO {
  id: number
  name: string
  type: 'LLM' | 'DATABASE'
  params: Record<string, any>
  createdAt: string
  updatedAt: string
}

export interface CredentialCreateDTO {
  name: string
  type: string
  params: Record<string, any>
}

export function getCredentials(type?: string): Promise<CredentialDTO[]> {
  return request.get('/api/credentials', { params: { type } })
}

export function getCredential(id: number): Promise<CredentialDTO> {
  return request.get(`/api/credentials/${id}`)
}

export function createCredential(data: CredentialCreateDTO): Promise<CredentialDTO> {
  return request.post('/api/credentials', data)
}

export function updateCredential(id: number, data: CredentialCreateDTO): Promise<CredentialDTO> {
  return request.put(`/api/credentials/${id}`, data)
}

export function deleteCredential(id: number): Promise<void> {
  return request.delete(`/api/credentials/${id}`)
}

export function fetchLlmModels(id: number): Promise<string[]> {
  return request.get(`/api/credentials/${id}/llm/models`)
}

export function testCredential(id: number): Promise<string> {
  return request.post(`/api/credentials/${id}/test`)
}

export function testCredentialDirect(type: string, params: Record<string, any>): Promise<string> {
  return request.post('/api/credentials/test', { type, params })
}
