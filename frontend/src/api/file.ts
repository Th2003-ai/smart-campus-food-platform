import axios, { type AxiosProgressEvent, type AxiosResponse } from 'axios'

/**
 * 文件模块接口。
 *
 * 这里刻意**不复用全局 request.ts 的 axios 实例**：
 * 全局拦截器会把所有非 200 的 code 统一弹 ElMessage，
 * 而分片上传需要自己识别 `code=4001`（分片损坏）并静默重传，
 * 用独立实例才能拿到业务码、避免刷屏。
 */
const BASE = import.meta.env.VITE_API_BASE_URL || '/api'

const http = axios.create({ baseURL: BASE, timeout: 180000 })

interface Result<T> {
  code: number
  message: string
  data: T
}

/** 带业务码的错误，前端据此判断是否需要重传该分片 */
export class FileApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

async function unwrap<T>(promise: Promise<AxiosResponse<Result<T>>>): Promise<T> {
  const response = await promise
  const body = response.data
  if (body.code !== 200) {
    throw new FileApiError(body.code, body.message || '请求失败')
  }
  return body.data
}

/** 分片 MD5 不一致时后端返回的业务码 */
export const CODE_CHUNK_MD5_MISMATCH = 4001

export interface ChunkInitRequest {
  fileName: string
  fileSize: number
  md5: string
  chunkSize?: number
}

export interface ChunkInitResponse {
  fileId: string
  fileName: string
  fileSize: number
  chunkSize: number
  chunkTotal: number
  /** 已上传分片序号（断点续传依据） */
  uploaded: number[]
  /** true 表示秒传 */
  instant: boolean
  downloadUrl: string
}

export interface FileItem {
  id: string
  fileName: string
  fileSize: number
  md5: string
  chunkTotal: number
  status: number
  downloadUrl: string
  createTime?: string
}

export interface BatchItem {
  fileName: string
  fileSize: number
  success: boolean
  fileId?: string
  downloadUrl?: string
  message?: string
}

export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  list: T[]
}

/** 1) 初始化：秒传 / 续传 / 新建 */
export function initChunkUpload(data: ChunkInitRequest) {
  return unwrap<ChunkInitResponse>(http.post('/files/init', data))
}

/** 2) 上传单个分片 */
export function uploadChunk(form: FormData, onProgress?: (percent: number) => void) {
  return unwrap<void>(
    http.post('/files/upload/chunk', form, {
      onUploadProgress: (e: AxiosProgressEvent) => {
        if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
      }
    })
  )
}

/** 3) 合并分片 */
export function completeUpload(fileId: string) {
  return unwrap<FileItem>(http.post(`/files/complete?fileId=${encodeURIComponent(fileId)}`))
}

/** 4) 小文件批量上传 */
export function batchUpload(files: File[], onProgress?: (percent: number) => void) {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  return unwrap<BatchItem[]>(
    http.post('/files/upload/batch', form, {
      onUploadProgress: (e: AxiosProgressEvent) => {
        if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
      }
    })
  )
}

/** 5) 文件列表 */
export function listFiles(pageNum = 1, pageSize = 20) {
  return unwrap<PageResult<FileItem>>(http.get('/files', { params: { pageNum, pageSize } }))
}

/** 6) 删除文件 */
export function deleteFile(id: string) {
  return unwrap<void>(http.delete(`/files/${id}`))
}

/** 下载地址（与 axios 实例同源，供 fetch 做 Range 下载用） */
export function fileDownloadUrl(id: string) {
  return `${BASE}/files/${id}/download`
}
