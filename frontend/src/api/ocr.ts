import axios from 'axios'

export interface OcrResultDTO {
  mdContent: string
  fileName: string
}

// Separate axios instance with longer timeout for OCR processing
const ocrRequest = axios.create({ baseURL: '', timeout: 180000 })

ocrRequest.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code === 200) return data.data
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  }
)

export function processOcr(file: File, mode: string, agentId?: number): Promise<OcrResultDTO> {
  const formData = new FormData()
  formData.append('file', file)
  const params = new URLSearchParams()
  params.append('mode', mode)
  if (agentId) params.append('agentId', String(agentId))
  return ocrRequest.post(`/api/ocr/process?${params.toString()}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
