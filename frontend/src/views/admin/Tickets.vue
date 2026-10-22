<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>客服工单</span>
          <div class="header-right">
            <el-radio-group v-model="status" size="small" @change="reload">
              <el-radio-button :value="undefined">全部</el-radio-button>
              <el-radio-button :value="0">待处理</el-radio-button>
              <el-radio-button :value="1">处理中</el-radio-button>
              <el-radio-button :value="2">已关闭</el-radio-button>
            </el-radio-group>
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && !tickets.length" description="暂无工单（AI 客服无法回答时会自动转人工）" />

      <div v-else class="list">
        <div v-for="t in tickets" :key="t.id" class="card">
          <div class="card-head">
            <div class="head-left">
              <span class="ticket-no">{{ t.ticketNo }}</span>
              <el-tag size="small" :type="tagType(t.status)">{{ statusText(t.status) }}</el-tag>
              <el-tag v-if="t.category" size="small" type="info">{{ t.category }}</el-tag>
            </div>
            <span class="text-muted">{{ t.createTime }}</span>
          </div>

          <div class="line"><span class="label">问题</span>{{ t.question }}</div>
          <div v-if="t.orderId" class="line"><span class="label">关联订单</span>{{ t.orderId }}</div>
          <div v-if="t.sessionId" class="line text-muted">
            <span class="label">会话</span>{{ t.sessionId }}
          </div>

          <div v-if="t.reply" class="reply">
            <span class="text-muted">已回复：</span>{{ t.reply }}
            <span v-if="t.closeTime" class="text-muted">（{{ t.closeTime }} 关闭）</span>
          </div>

          <div class="card-foot">
            <el-button
              v-if="t.status !== 2"
              size="small"
              type="primary"
              @click="openHandle(t)"
            >
              处理工单
            </el-button>
            <span v-else class="text-muted">已关闭</span>
          </div>
        </div>
      </div>

      <el-pagination
        v-if="page.total > page.pageSize"
        v-model:current-page="page.pageNum"
        :page-size="page.pageSize"
        :total="page.total"
        layout="prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="handleVisible" title="处理工单" width="520px">
      <div v-if="current" class="dialog-question">
        <div class="text-muted">用户问题</div>
        <div>{{ current.question }}</div>
      </div>
      <el-input
        v-model="reply"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="填写回复内容，学生会看到这条回复"
      />
      <el-checkbox v-model="closeAfter" class="mt">回复后直接关闭工单</el-checkbox>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { handleTicket, listTickets, type Ticket } from '@/api/admin'

const loading = ref(false)
const tickets = ref<Ticket[]>([])
const status = ref<number | undefined>(undefined)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const handleVisible = ref(false)
const submitting = ref(false)
const reply = ref('')
const closeAfter = ref(true)
const current = ref<Ticket | null>(null)

function statusText(s?: number) {
  if (s === 1) return '处理中'
  if (s === 2) return '已关闭'
  return '待处理'
}
function tagType(s?: number) {
  if (s === 2) return 'success'
  if (s === 1) return 'warning'
  return 'danger'
}

async function load() {
  loading.value = true
  try {
    const res = await listTickets({
      status: status.value,
      pageNum: page.pageNum,
      pageSize: page.pageSize
    })
    tickets.value = res.list
    page.total = res.total
  } catch {
    // 拦截器提示
  } finally {
    loading.value = false
  }
}

function reload() {
  page.pageNum = 1
  load()
}

function openHandle(t: Ticket) {
  current.value = t
  reply.value = ''
  closeAfter.value = true
  handleVisible.value = true
}

async function onSubmit() {
  const content = reply.value.trim()
  if (!content) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  submitting.value = true
  try {
    await handleTicket(current.value!.id, content, closeAfter.value)
    ElMessage.success(closeAfter.value ? '已回复并关闭工单' : '已回复，工单进入处理中')
    handleVisible.value = false
    await load()
  } catch {
    // 拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 60px;
}
.card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ticket-no {
  font-weight: 600;
}
.line {
  line-height: 1.6;
}
.label {
  color: #909399;
  display: inline-block;
  width: 64px;
}
.reply {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 8px 10px;
}
.card-foot {
  display: flex;
  justify-content: flex-end;
  border-top: 1px dashed #ebeef5;
  padding-top: 8px;
}
.dialog-question {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 12px;
  line-height: 1.6;
}
.mt {
  margin-top: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
