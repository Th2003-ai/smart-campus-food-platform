<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>骑手审核</span>
          <div class="header-actions">
            <el-select v-model="auditStatus" placeholder="全部状态" clearable style="width: 140px" @change="load">
              <el-option label="待审核" :value="0" />
              <el-option label="已通过" :value="1" />
              <el-option label="已驳回" :value="2" />
            </el-select>
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" empty-text="暂无骑手数据">
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="studentCardNo" label="学生证/工号" min-width="130" />
        <el-table-column prop="vehicleType" label="配送车辆" width="110" />
        <el-table-column label="工作状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.workStatus === 1 ? 'success' : 'info'">
              {{ row.workStatus === 1 ? '接单中' : '休息中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deliveringCount" label="配送中" width="80" />
        <el-table-column prop="totalIncome" label="累计收入" width="100" />
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="auditTagType(row.auditStatus)">{{ auditText(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.accountStatus === 1 ? 'success' : 'danger'">
              {{ row.accountStatus === 1 ? '正常' : '已封禁' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 0">
              <el-button size="small" type="success" @click="onAudit(row, true)">通过</el-button>
              <el-button size="small" type="danger" @click="onAudit(row, false)">驳回</el-button>
            </template>
            <template v-else>
              <el-button
                v-if="row.accountStatus === 1"
                size="small"
                type="danger"
                @click="onBan(row, true)"
              >
                封禁
              </el-button>
              <el-button v-else size="small" @click="onBan(row, false)">解封</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { auditRider, banRider, listRidersForAudit, type RiderAuditItem } from '@/api/admin'

const loading = ref(false)
const rows = ref<RiderAuditItem[]>([])
const auditStatus = ref<number | undefined>(undefined)

function auditText(status: number) {
  if (status === 1) return '已通过'
  if (status === 2) return '已驳回'
  return '待审核'
}

function auditTagType(status: number) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

async function load() {
  loading.value = true
  try {
    rows.value = await listRidersForAudit(auditStatus.value)
  } finally {
    loading.value = false
  }
}

async function onAudit(row: RiderAuditItem, pass: boolean) {
  let remark: string | undefined
  if (pass) {
    try {
      await ElMessageBox.confirm(`确认通过「${row.realName}」的骑手资质审核？`, '审核确认', {
        type: 'warning'
      })
    } catch {
      return
    }
  } else {
    try {
      const result = await ElMessageBox.prompt(`请填写驳回「${row.realName}」的原因`, '驳回骑手', {
        inputPlaceholder: '例如：学生证照片不清晰',
        inputValidator: (value: string) => (value && value.trim() ? true : '驳回原因不能为空'),
        type: 'warning'
      })
      remark = result.value.trim()
    } catch {
      return
    }
  }

  await auditRider(row.id, pass, remark)
  ElMessage.success(pass ? '已通过审核' : '已驳回（账号已同时禁用）')
  await load()
}

async function onBan(row: RiderAuditItem, ban: boolean) {
  try {
    await ElMessageBox.confirm(
      ban
        ? `封禁后「${row.realName}」将无法登录并停止接单，确认封禁？`
        : `确认解封「${row.realName}」的账号？`,
      ban ? '封禁骑手' : '解封骑手',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await banRider(row.id, ban)
  ElMessage.success(ban ? '已封禁' : '已解封')
  await load()
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-actions {
  display: flex;
  gap: 8px;
}
</style>
