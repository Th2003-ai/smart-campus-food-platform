<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>订单处理</span>
          <div class="header-right">
            <el-radio-group v-model="status" size="small" @change="reload">
              <el-radio-button :value="0">待接单</el-radio-button>
              <el-radio-button :value="1">备餐中</el-radio-button>
              <el-radio-button :value="2">待取餐</el-radio-button>
              <el-radio-button :value="4">已完成</el-radio-button>
              <el-radio-button :value="undefined">全部</el-radio-button>
            </el-radio-group>
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="tip">
        <el-icon><Bell /></el-icon>
        页面每 5 秒自动刷新；新订单也会通过 WebSocket 弹出提醒。学生下单后无需手动操作即可看到。
      </div>

      <el-empty v-if="!loading && !orders.length" description="当前没有订单" />

      <div v-else v-loading="loading" class="order-list">
        <div v-for="vo in orders" :key="vo.order.id" class="order-card">
          <div class="order-head">
            <div class="head-left">
              <span class="order-no">{{ vo.order.orderNo }}</span>
              <el-tag :type="orderStatusTagType(vo.order.orderStatus)" size="small">
                {{ vo.statusDesc || orderStatusText(vo.order.orderStatus) }}
              </el-tag>
              <span class="text-muted">{{ vo.order.createTime }}</span>
            </div>
            <div class="head-right">
              <el-tag v-if="vo.order.pickupCode" size="small" type="warning">
                取餐码 {{ vo.order.pickupCode }}
              </el-tag>
              <span class="total">¥{{ money(vo.order.totalAmount) }}</span>
            </div>
          </div>

          <div class="order-body">
            <div class="items">
              <div v-for="item in vo.items" :key="item.id" class="item-row">
                <div class="item-thumb">
                  <el-image
                    :src="dishCover({ dishId: item.dishId, dishImage: item.dishImage })"
                    fit="cover"
                    class="thumb-img"
                  >
                    <template #error><div class="thumb-placeholder">🍽</div></template>
                  </el-image>
                </div>
                <span class="item-name">{{ item.dishName }}</span>
                <span class="text-muted">× {{ item.quantity }}</span>
                <span class="item-amount">¥{{ money(item.amount) }}</span>
              </div>
            </div>

            <div class="receiver">
              <div class="receiver-line"><b>{{ vo.order.receiver }}</b> {{ vo.order.receiverPhone }}</div>
              <div class="receiver-line text-muted">{{ vo.order.address }}</div>
              <div v-if="vo.order.remark" class="remark">备注：{{ vo.order.remark }}</div>
              <div class="receiver-line text-muted">
                {{ deliveryTypeText(vo.order.deliveryType) }} · {{ payStatusText(vo.order.payStatus) }}
              </div>
            </div>
          </div>

          <div class="order-foot">
            <el-button
              v-if="vo.order.orderStatus === 0"
              type="primary"
              size="small"
              :loading="actingId === vo.order.id"
              @click="onAccept(vo.order.id)"
            >
              接单
            </el-button>
            <el-button
              v-if="vo.order.orderStatus === 0"
              type="danger"
              size="small"
              :loading="actingId === vo.order.id"
              @click="onReject(vo.order.id)"
            >
              拒单
            </el-button>
            <el-button
              v-if="vo.order.orderStatus === 1"
              type="success"
              size="small"
              :loading="actingId === vo.order.id"
              @click="onReady(vo.order.id)"
            >
              出餐完成
            </el-button>
            <el-button
              v-if="canConfirmPickup(vo.order.deliveryType, vo.order.orderStatus)"
              type="success"
              size="small"
              :loading="actingId === vo.order.id"
              @click="onConfirmPickup(vo.order)"
            >
              确认交付（自取）
            </el-button>
            <span
              v-if="vo.order.orderStatus === 2 && vo.order.deliveryType !== 2"
              class="text-muted"
            >
              已于出餐时生成取餐码 {{ vo.order.pickupCode }}，等待骑手接单
            </span>
            <span
              v-if="vo.order.orderStatus === 2 && vo.order.deliveryType === 2"
              class="text-muted"
            >
              自取订单，等待学生到店报取餐码 {{ vo.order.pickupCode }} 取餐
            </span>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'

import {
  acceptOrder,
  confirmSelfPickup,
  listShopOrders,
  readyOrder,
  rejectOrder,
  type OrderVO
} from '@/api/order'
import {
  canConfirmPickup,
  deliveryTypeText,
  money,
  orderStatusTagType,
  orderStatusText,
  payStatusText
} from '@/utils/dict'
import { dishCover } from '@/utils/image'

const status = ref<number | undefined>(0)
const orders = ref<OrderVO[]>([])
const loading = ref(false)
const actingId = ref('')
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  try {
    const res = await listShopOrders({
      status: status.value,
      pageNum: page.pageNum,
      pageSize: page.pageSize
    })
    orders.value = res.list
    page.total = res.total
  } catch {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function reload() {
  page.pageNum = 1
  load()
}

/**
 * 自取订单确认交付（2 待取餐 → 4 已完成）。
 *
 * 走的是学生端同一个接口：后端按登录角色判断，商户只能确认本店订单。
 * 商户忙没来得及点时，学生也可以在自己的订单详情页自助确认，避免订单卡死。
 */
async function onConfirmPickup(order: OrderVO['order']) {
  try {
    await ElMessageBox.confirm(
      `确认已把餐品交给学生？（取餐码 ${order.pickupCode || '-'}）确认后订单直接完成。`,
      '确认交付',
      { type: 'warning', confirmButtonText: '确认交付', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actingId.value = order.id
  try {
    await confirmSelfPickup(order.id)
    ElMessage.success('已确认交付，订单完成')
    await load()
  } catch {
    // 状态已变更等业务错误由拦截器提示
  } finally {
    actingId.value = ''
  }
}

async function onAccept(orderId: string) {
  actingId.value = orderId
  try {
    await acceptOrder(orderId)
    ElMessage.success('已接单，请尽快备餐')
    await load()
  } catch {
    // 状态已变更等业务错误由拦截器提示
  } finally {
    actingId.value = ''
  }
}

async function onReady(orderId: string) {
  actingId.value = orderId
  try {
    const order = await readyOrder(orderId)
    ElMessage.success(`已出餐，取餐码 ${order.pickupCode || ''}`)
    await load()
  } catch {
    // 拦截器提示
  } finally {
    actingId.value = ''
  }
}

async function onReject(orderId: string) {
  // 后端 reject 的 reason 是必填的 @RequestParam，这里必须拦住空值
  let reason: string
  try {
    const res = await ElMessageBox.prompt('请填写拒单原因', '拒单', {
      inputPlaceholder: '如：食材售罄 / 店铺即将打烊',
      inputValidator: (v: string) => (v && v.trim() ? true : '拒单原因不能为空'),
      confirmButtonText: '确定拒单',
      cancelButtonText: '取消'
    })
    reason = res.value.trim()
  } catch {
    return
  }
  actingId.value = orderId
  try {
    await rejectOrder(orderId, reason)
    ElMessage.success('已拒单，库存已回补')
    await load()
  } catch {
    // 拦截器提示
  } finally {
    actingId.value = ''
  }
}

onMounted(() => {
  load()
  // 轮询兜底：即使 WebSocket 断开，学生下单后 5 秒内也能看到
  timer = setInterval(load, 5000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
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
.tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 12px;
}
.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 60px;
}
.order-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.order-head {
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
  font-size: 13px;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.order-no {
  font-weight: 600;
}
.total {
  font-weight: 600;
  color: #f56c6c;
}
.order-body {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.items {
  flex: 1;
  min-width: 260px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.item-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.item-thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  overflow: hidden;
  background: #f5f7fa;
  flex-shrink: 0;
}
.thumb-img {
  width: 100%;
  height: 100%;
}
.thumb-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.item-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-amount {
  width: 70px;
  text-align: right;
}
.receiver {
  width: 300px;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-left: 1px dashed #ebeef5;
  padding-left: 16px;
}
.receiver-line {
  line-height: 1.5;
}
.remark {
  color: #e6a23c;
}
.order-foot {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}
.text-muted {
  color: #909399;
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
</style>
