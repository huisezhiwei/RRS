import request from './request'
import axios from 'axios'

export interface Material {
  id?: number
  libraryId?: number
  fileName?: string
  storedName?: string
  fileSize?: number
  mimeType?: string
  uploadedAt?: string
  createdAt?: string
  tags?: Tag[]
}

export interface Tag {
  id?: number
  name: string
  color?: string
  createdAt?: string
}

export interface PageData<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function uploadMaterial(libraryId: number, file: File): Promise<Material> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/libraries/${libraryId}/materials`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export function getMaterials(
  libraryId: number,
  params?: { page?: number; size?: number; tag?: string }
): Promise<PageData<Material>> {
  return request.get(`/api/libraries/${libraryId}/materials`, { params })
}

export async function downloadMaterial(id: number, fileName: string): Promise<void> {
  try {
    const response = await axios.get(`/api/materials/${id}/download`, {
      responseType: 'blob',
      timeout: 60000,
    })

    // Check if the response is actually a blob (not an error JSON)
    const contentType = response.headers['content-type'] || ''
    if (contentType.includes('application/json')) {
      // Backend returned an error as JSON
      const text = await response.data.text()
      const errorData = JSON.parse(text)
      throw new Error(errorData.message || '下载失败')
    }

    const blob = new Blob([response.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (error: any) {
    if (error.response && error.response.data instanceof Blob) {
      // Try to read error from blob
      try {
        const text = await error.response.data.text()
        const errorData = JSON.parse(text)
        throw new Error(errorData.message || '下载失败')
      } catch {
        throw new Error('下载失败')
      }
    }
    throw error
  }
}

export function deleteMaterial(id: number): Promise<void> {
  return request.delete(`/api/materials/${id}`)
}

export function setMaterialTags(materialId: number, tagIds: number[]): Promise<Material> {
  return request.put(`/api/materials/${materialId}/tags`, { tagIds })
}

export function getTags(): Promise<Tag[]> {
  return request.get('/api/tags')
}

export function createTag(data: { name: string; color?: string }): Promise<Tag> {
  return request.post('/api/tags', data)
}

export function deleteTag(id: number): Promise<void> {
  return request.delete(`/api/tags/${id}`)
}
