<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>我的配送</span>
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <el-tabs v-model="tab" @tab-change="load">
        <el-tab-pane label="待取餐" name="2" />
        <el-tab-pane label="配送中" name="3" />
      </el-tabs>

      <el-empty v-if="!loading && !orders.length" :description="tab === '2' ? '暂无待取餐订单' : '暂无配送中订单'" />

      <div v-else class="list">
        <div v-for="o in orders" :key="o.id" class="card">
          <div class="card-head">
            <span class="order-no">{{ o.orderNo }}</span>
            <el-tag size="small" :type="tab === '2' ? 'warning' : 'primary'">
              {{ tab === '2' ? '待取餐' : '配送中' }}
            </el-tag>
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
              <div class="text-muted">{{ o.createTime }}</div>
            </div>
          </div>
          <div class="card-foot">
            <el-button
              v-if="tab === '2'"
              type="primary"
              size="small"
              :loading="actingId === o.id"
              @click="onPickup(o.id)"
            >
              确认取餐
            </el-button>
            <el-button
              v-else
              type="success"
              size="small"
              :loading="actingId === o.id"
              @click="onDeliver(o.id)"
            >
              确认送达
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import type { Order } from '@/api/order'
import { deliverOrder, listRiderOrders, pickupOrder } from '@/api/rider'
import { money } from '@/utils/dict'

const tab = ref('2')
const orders = ref<Order[]>([])
const loading = ref(false)
const actingId = ref('')

let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  try {
    const res = await listRiderOrders({ status: Number(tab.value), pageNum: 1, pageSize: 20 })
    orders.value = res.list
  } catch {
    // 拦截器提示
  } finally {
    loading.value = false
  }
}

async function onPickup(orderId: string) {
  actingId.value = orderId
  try {
    await pickupOrder(orderId)
    ElMessage.success('已取餐，开始配送')
    // 取餐后订单从「待取餐」进入「配送中」，切到对应 tab 让骑手直接看到
    tab.value = '3'
    await load()
  } catch {
    // 拦截器提示
  } finally {
    actingId.value = ''
  }
}

async function onDeliver(orderId: string) {
  try {
    await ElMessageBox.confirm('确认已送达用户手中？', '确认送达', { type: 'warning' })
  } catch {
    return
  }
  actingId.value = orderId
  try {
    await deliverOrder(orderId)
    ElMessage.success('已送达，收入已计入')
    await load()
  } catch {
    // 拦截器提示
  } finally {
    actingId.value = ''
  }
}

onMounted(() => {
  load()
  timer = setInterval(load, 6000)
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
  align-items: center;
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
