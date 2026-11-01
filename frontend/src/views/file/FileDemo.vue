<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>文件上传下载演示</span>
          <span class="text-muted">分片上传 · 断点续传 · 批量小文件 · 进度条 · 并行下载</span>
        </div>
      </template>

      <el-tabs v-model="tab">
        <!-- ① 大文件分片上传 -->
        <el-tab-pane label="① 大文件分片上传" name="big">
          <div class="row">
            <input ref="bigInputRef" type="file" class="hidden-input" @change="onBigFileChange" />
            <el-button type="primary" @click="bigInputRef?.click()">选择大文件</el-button>
            <span v-if="bigFile" class="text-muted">{{ bigFile.name }}（{{ fmtSize(bigFile.size) }}）</span>
            <el-switch v-model="simulateCorrupt" active-text="模拟分片损坏" />
          </div>

          <div class="row">
            <el-button type="success" :disabled="!bigFile || uploading" @click="startBigUpload">
              开始上传 / 继续
            </el-button>
            <el-button :disabled="!uploading" @click="paused = true">暂停</el-button>
          </div>

          <el-progress :percentage="bigProgress" :stroke-width="16" :status="bigProgress === 100 ? 'success' : undefined" />
          <div class="meta">
            状态：<b>{{ bigStatus }}</b>　分片：{{ uploadedChunks }} / {{ totalChunks }}　每片 {{ fmtSize(chunkSize) }}
            <span v-if="paused" class="warn">（已暂停：点「继续」会重新 init 并只传缺的分片）</span>
          </div>
          <div v-if="downloadUrl" class="row">
            <span class="text-muted">下载地址：</span>
            <el-input v-model="downloadUrl" readonly style="max-width: 520px" />
            <el-button @click="copyText(downloadUrl)">复制</el-button>
          </div>
        </el-tab-pane>

        <!-- ② 小文件批量上传 -->
        <el-tab-pane label="② 小文件批量上传" name="batch">
          <div class="row">
            <input ref="batchInputRef" type="file" multiple class="hidden-input" @change="onBatchFilesChange" />
            <el-button type="primary" @click="batchInputRef?.click()">选择多个小文件</el-button>
            <el-button type="success" :disabled="!batchFiles.length || batchUploading" @click="startBatchUpload">
              批量上传
            </el-button>
            <span class="text-muted">已选 {{ batchFiles.length }} 个</span>
          </div>
          <el-progress :percentage="batchProgress" :stroke-width="14" />
          <el-table v-if="batchResults.length" :data="batchResults" size="small" class="mt">
            <el-table-column prop="fileName" label="文件名" min-width="200" />
            <el-table-column label="大小" width="100">
              <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="160" />
          </el-table>
        </el-tab-pane>

        <!-- ③ 下载 / 断点续传 / 并行 -->
        <el-tab-pane label="③ 下载 · 断点续传 · 并行" name="list">
          <div class="row">
            <el-button @click="loadFiles">刷新列表</el-button>
            <span class="text-muted">普通下载支持暂停/继续；并行下载把文件切成 4 段同时拉取</span>
          </div>
          <el-table :data="files" size="small">
            <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
            <el-table-column label="大小" width="100">
              <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="分片" width="70">
              <template #default="{ row }">{{ row.chunkTotal }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                  {{ row.status === 1 ? '已完成' : '上传中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下载进度" width="150">
              <template #default="{ row }">
                <el-progress
                  v-if="row.status === 1"
                  :percentage="downloadState[row.id]?.progress ?? 0"
                  :stroke-width="10"
                />
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="330">
              <template #default="{ row }">
                <template v-if="row.status === 1">
                  <el-button size="small" link type="primary" @click="downloadFile(row)">下载</el-button>
                  <el-button
                    size="small"
                    link
                    type="warning"
                    :disabled="!downloadState[row.id]?.running"
                    @click="togglePause(row)"
                  >
                    {{ downloadState[row.id]?.paused ? '继续' : '暂停' }}
                  </el-button>
                  <el-button size="small" link type="success" @click="parallelDownload(row)">4 线程并行</el-button>
                  <el-button size="small" link type="danger" @click="removeFile(row)">删除</el-button>
                </template>
                <span v-else class="text-muted">等待合并</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-card shadow="never">
      <template #header>运行日志</template>
      <div class="log">
        <div v-for="(line, i) in logs" :key="i">{{ line }}</div>
        <div v-if="!logs.length" class="text-muted">暂无日志</div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SparkMD5 from 'spark-md5'

import {
  batchUpload,
  completeUpload,
  deleteFile,
  fileDownloadUrl,
  FileApiError,
  initChunkUpload,
  listFiles,
  uploadChunk,
  type BatchItem,
  type FileItem
} from '@/api/file'

const CHUNK_SIZE = 5 * 1024 * 1024
const DOWNLOAD_CHUNK = 1024 * 1024
const PARALLEL_THREADS = 4

const tab = ref('big')
const logs = ref<string[]>([])

function appendLog(message: string) {
  logs.value.unshift(`[${new Date().toLocaleTimeString()}] ${message}`)
  if (logs.value.length > 200) logs.value.pop()
}

function fmtSize(bytes?: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

// ==================== ① 大文件分片上传 ====================
const bigInputRef = ref<HTMLInputElement>()
const bigFile = ref<File | null>(null)
const bigProgress = ref(0)
const bigStatus = ref('待开始')
const uploadedChunks = ref(0)
const totalChunks = ref(0)
const chunkSize = ref(CHUNK_SIZE)
const uploading = ref(false)
const paused = ref(false)
const simulateCorrupt = ref(false)
const downloadUrl = ref('')

let currentFileId = ''
const uploadedSet = new Set<number>()

function onBigFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  bigFile.value = target.files?.[0] ?? null
  bigProgress.value = 0
  bigStatus.value = '待开始'
  downloadUrl.value = ''
  uploadedChunks.value = 0
  totalChunks.value = 0
}

/** 计算整个文件的 MD5（一次性读入，演示够用；超大文件可改成分块增量） */
async function fileMd5(file: File) {
  const buffer = await file.arrayBuffer()
  return SparkMD5.ArrayBuffer.hash(buffer)
}

/** 计算单个分片的 MD5 */
async function blobMd5(blob: Blob) {
  const buffer = await blob.arrayBuffer()
  return SparkMD5.ArrayBuffer.hash(buffer)
}

async function startBigUpload() {
  const file = bigFile.value
  if (!file) return
  uploading.value = true
  paused.value = false
  try {
    bigStatus.value = '计算 MD5…'
    const md5 = await fileMd5(file)
    appendLog(`开始上传：${file.name}，${fmtSize(file.size)}，MD5=${md5}`)

    const init = await initChunkUpload({
      fileName: file.name,
      fileSize: file.size,
      md5,
      chunkSize: CHUNK_SIZE
    })
    currentFileId = init.fileId
    chunkSize.value = init.chunkSize
    totalChunks.value = init.chunkTotal

    if (init.instant) {
      bigStatus.value = '秒传成功'
      bigProgress.value = 100
      downloadUrl.value = init.downloadUrl
      appendLog('命中秒传：服务端已有相同 MD5 的文件，无需传输')
      return
    }

    uploadedSet.clear()
    init.uploaded.forEach((index) => uploadedSet.add(index))
    uploadedChunks.value = uploadedSet.size
    bigProgress.value = Math.round((uploadedSet.size / totalChunks.value) * 100)
    if (uploadedSet.size > 0) {
      appendLog(`断点续传：服务端已有 ${uploadedSet.size}/${totalChunks.value} 片，只传缺失分片`)
    }

    bigStatus.value = '上传中…'
    const pending: number[] = []
    for (let i = 0; i < totalChunks.value; i += 1) {
      if (!uploadedSet.has(i)) pending.push(i)
    }
    appendLog(`待上传分片 ${pending.length} 个，并发 3`)

    await runPool(pending, 3, uploadOneChunk)

    if (paused.value) {
      bigStatus.value = `已暂停（已传 ${uploadedSet.size}/${totalChunks.value}）`
      appendLog('已暂停：点「继续」会重新 init，只补传缺的分片')
      return
    }

    bigStatus.value = '合并中…'
    const done = await completeUpload(currentFileId)
    downloadUrl.value = done.downloadUrl
    bigStatus.value = '上传完成'
    bigProgress.value = 100
    appendLog(`合并完成并通过整体 MD5 校验：${done.fileName}`)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    bigStatus.value = `失败：${message}`
    appendLog(`上传失败：${message}`)
  } finally {
    uploading.value = false
  }
}

/** 上传一个分片；4001 时自动重传（最多 3 次） */
async function uploadOneChunk(index: number) {
  const file = bigFile.value
  if (!file) return
  const start = index * chunkSize.value
  const blob = file.slice(start, Math.min(start + chunkSize.value, file.size))
  const md5 = await blobMd5(blob)

  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const form = new FormData()
    form.append('fileId', currentFileId)
    form.append('chunkIndex', String(index))
    // 演示用：第 1 片第一次故意传错 MD5，后端会返回 4001 要求重传
    const corrupt = simulateCorrupt.value && index === 0 && attempt === 1
    form.append('chunkMd5', corrupt ? '0123456789abcdef0123456789abcdef' : md5)
    form.append('file', blob)
    try {
      await uploadChunk(form)
      uploadedSet.add(index)
      uploadedChunks.value = uploadedSet.size
      bigProgress.value = Math.round((uploadedSet.size / totalChunks.value) * 100)
      if (corrupt) {
        appendLog(`分片 ${index} 首次携带错误 MD5 竟被接受？`)
      }
      return
    } catch (error) {
      const code = error instanceof FileApiError ? error.code : 0
      const message = error instanceof Error ? error.message : String(error)
      appendLog(
        `分片 ${index} 第 ${attempt} 次失败（code=${code}）${corrupt ? ' ← 模拟损坏' : ''}：${message}`
      )
      if (attempt === 3) throw error
      appendLog(`分片 ${index} 自动重传（第 ${attempt + 1} 次）`)
    }
  }
}

/** 并发池：限制同时上传的分片数 */
async function runPool(indexes: number[], concurrency: number, worker: (index: number) => Promise<void>) {
  let cursor = 0
  const size = Math.max(1, Math.min(concurrency, indexes.length))
  const runners = Array.from({ length: size }, async () => {
    while (cursor < indexes.length) {
      if (paused.value) return
      const index = indexes[cursor]
      cursor += 1
      await worker(index)
    }
  })
  await Promise.all(runners)
}

// ==================== ② 小文件批量上传 ====================
const batchInputRef = ref<HTMLInputElement>()
const batchFiles = ref<File[]>([])
const batchUploading = ref(false)
const batchProgress = ref(0)
const batchResults = ref<BatchItem[]>([])

function onBatchFilesChange(event: Event) {
  const target = event.target as HTMLInputElement
  batchFiles.value = Array.from(target.files ?? [])
  batchResults.value = []
  batchProgress.value = 0
}

async function startBatchUpload() {
  if (!batchFiles.value.length) return
  batchUploading.value = true
  batchProgress.value = 0
  try {
    appendLog(`批量上传 ${batchFiles.value.length} 个小文件（单个请求，multipart 数组）`)
    const results = await batchUpload(batchFiles.value, (percent) => {
      batchProgress.value = percent
    })
    batchResults.value = results
    const ok = results.filter((item) => item.success).length
    appendLog(`批量上传完成：成功 ${ok}/${results.length}`)
    ElMessage.success(`成功 ${ok}/${results.length}`)
    await loadFiles()
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    appendLog(`批量上传失败：${message}`)
  } finally {
    batchUploading.value = false
  }
}

// ==================== ③ 下载 / 续传 / 并行 ====================
interface DownloadState {
  progress: number
  offset: number
  total: number
  paused: boolean
  running: boolean
}

const files = ref<FileItem[]>([])
const downloadState = reactive<Record<string, DownloadState>>({})
const downloadParts = new Map<string, Uint8Array[]>()

async function loadFiles() {
  try {
    const page = await listFiles(1, 50)
    files.value = page.list
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    appendLog(`加载文件列表失败：${message}`)
  }
}

function ensureState(item: FileItem) {
  if (!downloadState[item.id]) {
    downloadState[item.id] = {
      progress: 0,
      offset: 0,
      total: item.fileSize,
      paused: false,
      running: false
    }
    downloadParts.set(item.id, [])
  }
  return downloadState[item.id]
}

/** 分块 Range 下载：每 1MB 一个请求，可暂停后继续（断点续传） */
async function downloadFile(item: FileItem) {
  const state = ensureState(item)
  if (state.running) return
  state.running = true
  state.paused = false
  const parts = downloadParts.get(item.id) ?? []
  downloadParts.set(item.id, parts)
  const url = fileDownloadUrl(item.id)
  appendLog(`开始下载：${item.fileName}，从 ${fmtSize(state.offset)} 处继续`)

  try {
    while (state.offset < state.total) {
      if (state.paused) {
        appendLog(`下载已暂停：${item.fileName} @ ${fmtSize(state.offset)}`)
        return
      }
      const start = state.offset
      const end = Math.min(start + DOWNLOAD_CHUNK - 1, state.total - 1)
      const response = await fetch(url, { headers: { Range: `bytes=${start}-${end}` } })
      if (response.status !== 206 && response.status !== 200) {
        throw new Error(`HTTP ${response.status}`)
      }
      const buffer = new Uint8Array(await response.arrayBuffer())
      parts.push(buffer)
      state.offset += buffer.length
      state.progress = Math.round((state.offset / state.total) * 100)
    }
    saveBlob(parts, item.fileName)
    appendLog(`下载完成：${item.fileName}`)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    appendLog(`下载失败：${message}`)
  } finally {
    state.running = false
  }
}

function togglePause(item: FileItem) {
  const state = ensureState(item)
  if (state.paused) {
    state.paused = false
    void downloadFile(item)
  } else {
    state.paused = true
    appendLog('已请求暂停，当前分块完成后停止')
  }
}

/** 高并发下载：把文件切成 4 段，同时发起 4 个 Range 请求 */
async function parallelDownload(item: FileItem) {
  const threads = PARALLEL_THREADS
  const size = Math.ceil(item.fileSize / threads)
  const url = fileDownloadUrl(item.id)
  const startedAt = performance.now()
  appendLog(`${threads} 线程并行下载：${item.fileName}`)
  try {
    const tasks = Array.from({ length: threads }, (_, index) => {
      const start = index * size
      const end = Math.min(start + size - 1, item.fileSize - 1)
      return fetch(url, { headers: { Range: `bytes=${start}-${end}` } }).then((response) => {
        if (response.status !== 206 && response.status !== 200) {
          throw new Error(`HTTP ${response.status}`)
        }
        return response.arrayBuffer()
      })
    })
    const buffers = await Promise.all(tasks)
    saveBlobBuffers(buffers, item.fileName)
    const cost = Math.round(performance.now() - startedAt)
    appendLog(`${threads} 段并行下载完成，耗时 ${cost} ms`)
    ElMessage.success(`并行下载完成，耗时 ${cost} ms`)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    appendLog(`并行下载失败：${message}`)
  }
}

function saveBlob(parts: Uint8Array[], fileName: string) {
  const blob = new Blob(parts as BlobPart[])
  triggerDownload(blob, fileName)
}

function saveBlobBuffers(buffers: ArrayBuffer[], fileName: string) {
  const blob = new Blob(buffers as BlobPart[])
  triggerDownload(blob, fileName)
}

function triggerDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

async function removeFile(item: FileItem) {
  try {
    await ElMessageBox.confirm(`确定删除「${item.fileName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteFile(item.id)
    appendLog(`已删除：${item.fileName}`)
    downloadParts.delete(item.id)
    delete downloadState[item.id]
    await loadFiles()
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    appendLog(`删除失败：${message}`)
  }
}

onMounted(loadFiles)
</script>

<style scoped>
.page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.meta {
  margin: 10px 0 14px;
  font-size: 13px;
  color: #606266;
}
.warn {
  color: #e6a23c;
}
.hidden-input {
  display: none;
}
.text-muted {
  color: #909399;
  font-size: 13px;
}
.mt {
  margin-top: 12px;
}
.log {
  max-height: 260px;
  overflow-y: auto;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.7;
  color: #303133;
}
</style>
