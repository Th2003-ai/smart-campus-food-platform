<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>抢单大厅</span>
          <div class="header-right">
            <el-tag :type="accepting ? 'success' : 'info'" size="small">
              {{ accepting ? '接单中' : '休息中' }}
            </el-tag>
            <el-switch
              v-model="accepting"
              active-text="接单"
              inactive-text="休息"
              @change="onToggleWork"
            />
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="auditStatus !== undefined && auditStatus !== 1"
        type="warning"
        :closable="false"
        show-icon
        class="mb"
        :title="auditStatus === 0 ? '你的骑手资质正在审核中，审核通过后才能抢单' : '你的骑手资质未通过，请联系管理员'"
      />
      <el-alert
        v-else-if="!accepting"
        type="info"
        :closable="false"
        show-icon
        class="mb"
        title="当前为休息中，切换为「接单」后才能抢单"
      />

      <div class="tip text-muted">大厅每 5 秒自动刷新；新单也会通过 WebSocket 实时提醒。</div>

      <el-empty v-if="!loading && !orders.length" description="暂无可抢订单（商家出餐后订单才会出现在这里）" />

      <div v-else v-loading="loading" class="list">
        <div v-for="o in orders" :key="o.id" class="card">
          <div class="card-head">
            <span class="order-no">{{ o.orderNo }}</span>
            <span class="text-muted">{{ o.createTime }}</span>
          </div>
          <div class="card-body">
            <div class="info">
              <div class="line"><span class="label">收货人</span>{{ o.receiver }} {{ o.receiverPhone }}</div>
              <div class="line"><span class="label">地址</span>{{ o.address }}</div>
              <div class="line">
                <span class="label">取餐码</span>
                <b class="pickup">{{ o.pickupCode || '-' }}</b>
              </div>
              <div v-if="o.remark" class="line"><span class="label">备注</span>{{ o.remark }}</div>
            </div>
            <div class="amount">
              <div class="fee">配送费 ¥{{ money(o.deliveryFee) }}</div>
              <div class="text-muted">订单 ¥{{ money(o.totalAmount) }}</div>
            </div>
          </div>
          <div class="card-foot">
            <el-button
              type="primary"
              size="small"
              :loading="actingId === o.id"
              :disabled="!accepting || (auditStatus !== undefined && auditStatus !== 1)"
              @click="onGrab(o.id)"
            >
              抢单
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import type { Order } from '@/api/order'
import { getRiderProfile, grabOrder, listGrabHall, updateWorkStatus } from '@/api/rider'
import { money } from '@/utils/dict'

const orders = ref<Order[]>([])
const loading = ref(false)
const actingId = ref('')
const accepting = ref(false)
const auditStatus = ref<number | undefined>(undefined)

let timer: ReturnType<typeof setInterval> | null = null

async function loadProfile() {
  try {
    const r = await getRiderProfile()
    auditStatus.value = r.auditStatus
    accepting.value = r.workStatus === 1
  } catch {
    // 拦截器提示
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listGrabHall({ pageNum: 1, pageSize: 20 })
    orders.value = res.list
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

async function onGrab(orderId: string) {
  actingId.value = orderId
  try {
    await grabOrder(orderId)
    ElMessage.success('抢单成功，请尽快取餐')
    await load()
  } catch {
    // 「手慢了」等业务错误由拦截器提示
    await load()
  } finally {
    actingId.value = ''
  }
}

onMounted(() => {
  loadProfile()
  load()
  timer = setInterval(load, 5000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
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
  gap: 12px;
}
.mb {
  margin-bottom: 12px;
}
.tip {
  font-size: 12px;
  margin-bottom: 12px;
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
  gap: 10px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.order-no {
  font-weight: 600;
}
.card-body {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}
.info {
  flex: 1;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.line {
  line-height: 1.5;
}
.label {
  color: #909399;
  display: inline-block;
  width: 52px;
}
.pickup {
  color: #f56c6c;
  letter-spacing: 1px;
}
.amount {
  text-align: right;
  font-size: 13px;
}
.fee {
  font-weight: 600;
  color: #f56c6c;
}
.card-foot {
  display: flex;
  justify-content: flex-end;
  border-top: 1px dashed #ebeef5;
  padding-top: 10px;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
