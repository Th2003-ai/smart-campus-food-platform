<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>商户审核</span>
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

      <el-table v-loading="loading" :data="rows" empty-text="暂无商户数据">
        <el-table-column prop="merchantName" label="商户名称" min-width="150" />
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="licenseNo" label="经营许可编号" min-width="150" />
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="auditTagType(row.auditStatus)">{{ auditText(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.accountStatus === 1 ? 'success' : 'info'">
              {{ row.accountStatus === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditRemark" label="审核意见" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 0">
              <el-button size="small" type="success" @click="onAudit(row, true)">通过</el-button>
              <el-button size="small" type="danger" @click="onAudit(row, false)">驳回</el-button>
            </template>
            <span v-else class="muted">已审核</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { auditMerchant, listMerchantsForAudit, type MerchantAuditItem } from '@/api/admin'

const loading = ref(false)
const rows = ref<MerchantAuditItem[]>([])
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
    rows.value = await listMerchantsForAudit(auditStatus.value)
  } finally {
    loading.value = false
  }
}

async function onAudit(row: MerchantAuditItem, pass: boolean) {
  // 驳回必须填写原因：与后端校验保持一致，前端先拦一次，避免白跑一趟请求
  let remark: string | undefined
  if (pass) {
    try {
      await ElMessageBox.confirm(`确认通过「${row.merchantName}」的资质审核？`, '审核确认', {
        type: 'warning'
      })
    } catch {
      return
    }
  } else {
    try {
      const result = await ElMessageBox.prompt(
        `请填写驳回「${row.merchantName}」的原因`,
        '驳回商户',
        {
          inputPlaceholder: '例如：营业执照编号与名称不一致',
          inputValidator: (value: string) => (value && value.trim() ? true : '驳回原因不能为空'),
          type: 'warning'
        }
      )
      remark = result.value.trim()
    } catch {
      return
    }
  }

  await auditMerchant(row.id, pass, remark)
  ElMessage.success(pass ? '已通过审核' : '已驳回')
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
.muted {
  color: #909399;
  font-size: 13px;
}
</style>
