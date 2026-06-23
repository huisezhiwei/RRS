import request from './request'

export interface MaterialLibrary {
  id?: number
  code: string
  name: string
  description?: string
  maintainer?: string
  libraryType: 'EXCEL' | 'IMAGE'
  materialCount?: number
  lastUploadedAt?: string
  createdAt?: string
  updatedAt?: string
}

export function createLibrary(data: MaterialLibrary): Promise<MaterialLibrary> {
  return request.post('/api/libraries', data)
}

export function getLibraries(params?: { type?: string; keyword?: string }): Promise<MaterialLibrary[]> {
  return request.get('/api/libraries', { params })
}

export function getLibraryById(id: number): Promise<MaterialLibrary> {
  return request.get(`/api/libraries/${id}`)
}

export function updateLibrary(id: number, data: Partial<MaterialLibrary>): Promise<MaterialLibrary> {
  return request.put(`/api/libraries/${id}`, data)
}

export function deleteLibrary(id: number): Promise<void> {
  return request.delete(`/api/libraries/${id}`)
}
