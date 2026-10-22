<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>个人中心</span>
          <el-button text type="danger" @click="onLogout">退出登录</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && !rider" description="当前账号未绑定骑手信息，请联系管理员" />

      <template v-else-if="rider">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="姓名">{{ rider.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="auditTagType(rider.auditStatus)" size="small">
              {{ auditText(rider.auditStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="学生证/工号">{{ rider.studentCardNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="配送车辆">{{ rider.vehicleType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="配送中订单">{{ rider.deliveringCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="同时接单上限">{{ rider.maxDelivering ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="累计收入">¥{{ money(rider.totalIncome) }}</el-descriptions-item>
          <el-descriptions-item label="平均配送时长">
            {{ rider.avgDeliveryTime ? rider.avgDeliveryTime + ' 分钟' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="rider.auditRemark" label="审核意见" :span="2">
            {{ rider.auditRemark }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="rider.auditStatus !== 1"
          type="warning"
          :closable="false"
          show-icon
          class="mt"
          title="资质未通过审核，暂不能抢单。请联系管理员在后台审核。"
        />

        <div class="work-row mt">
          <span class="label">工作状态</span>
          <el-switch
            v-model="accepting"
            active-text="接单中"
            inactive-text="休息中"
            :disabled="rider.auditStatus !== 1"
            @change="onToggleWork"
          />
          <span class="text-muted">切换为接单中才能在大厅抢单</span>
        </div>

        <div class="work-row mt">
          <span class="label">在线心跳</span>
          <el-button size="small" :loading="beating" @click="onBeat">发送心跳</el-button>
          <el-tag :type="online ? 'success' : 'info'" size="small">{{ online ? '在线' : '离线' }}</el-tag>
          <span class="text-muted">在线状态存放在 Redis，60 秒无心跳自动判定离线</span>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getIncome, getRiderProfile, sendHeartbeat, updateWorkStatus, type Rider } from '@/api/rider'
import { useUserStore } from '@/store/user'
import { money } from '@/utils/dict'

const router = useRouter()
const userStore = useUserStore()

const rider = ref<Rider | null>(null)
const loading = ref(false)
const beating = ref(false)
const accepting = ref(false)
const online = ref(false)

function auditText(s?: number) {
  if (s === 1) return '已通过'
  if (s === 2) return '已驳回'
  return '待审核'
}
function auditTagType(s?: number) {
  if (s === 1) return 'success'
  if (s === 2) return 'danger'
  return 'warning'
}

async function load() {
  loading.value = true
  try {
    rider.value = await getRiderProfile()
    accepting.value = rider.value.workStatus === 1
    const inc = await getIncome()
    online.value = !!inc.online
  } catch {
    // 拦截器提示
  } finally {
    loading.value = false
  }
}

async function onToggleWork(val: boolean) {
  try {
    await updateWorkStatus(val ? 1 : 0)
    ElMessage.success(val ? '已切换为接单中' : '已切换为休息中')
  } catch {
    accepting.value = !val
  }
}

async function onBeat() {
  beating.value = true
  try {
    const res = await sendHeartbeat()
    online.value = !!res.online
    ElMessage.success('心跳已发送，在线状态已续期 60 秒')
  } catch {
    // 拦截器提示
  } finally {
    beating.value = false
  }
}

async function onLogout() {
  await userStore.logout()
  router.push('/login')
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mt {
  margin-top: 18px;
}
.work-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.label {
  width: 76px;
  color: #909399;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
