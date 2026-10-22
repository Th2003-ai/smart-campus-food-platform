<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>店铺管理</span>
          <div class="header-actions">
            <el-select v-model="banStatus" placeholder="全部状态" clearable style="width: 140px" @change="load">
              <el-option label="正常" :value="0" />
              <el-option label="已封禁" :value="1" />
            </el-select>
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 说明：学生端店铺列表会过滤掉已封禁店铺，因此管理端必须走本页，
           否则店铺一旦封禁就再也找不到、无法解封。 -->
      <el-table v-loading="loading" :data="rows" empty-text="暂无店铺数据">
        <el-table-column prop="shopName" label="店铺名称" min-width="150" />
        <el-table-column label="营业状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.businessStatus === 1 ? 'success' : 'info'">
              {{ row.businessStatus === 1 ? '营业中' : '休息中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="封禁状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.banStatus === 1 ? 'danger' : 'success'">
              {{ row.banStatus === 1 ? '已封禁' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="monthlySales" label="月销量" width="90" />
        <el-table-column prop="score" label="评分" width="80" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.banStatus !== 1"
              size="small"
              type="danger"
              @click="onBan(row, true)"
            >
              封禁
            </el-button>
            <el-button v-else size="small" @click="onBan(row, false)">解封</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { banShop, listShopsForAudit, type ShopAuditItem } from '@/api/admin'

const loading = ref(false)
const rows = ref<ShopAuditItem[]>([])
const banStatus = ref<number | undefined>(undefined)

async function load() {
  loading.value = true
  try {
    rows.value = await listShopsForAudit(banStatus.value)
  } finally {
    loading.value = false
  }
}

async function onBan(row: ShopAuditItem, ban: boolean) {
  try {
    await ElMessageBox.confirm(
      ban
        ? `封禁后「${row.shopName}」将不再出现在学生端列表，并自动置为休息中，确认封禁？`
        : `确认解封「${row.shopName}」？解封后需商户自行重新开启营业。`,
      ban ? '封禁店铺' : '解封店铺',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await banShop(row.id, ban)
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
