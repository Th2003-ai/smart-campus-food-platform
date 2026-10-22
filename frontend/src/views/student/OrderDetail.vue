<template>
  <div class="page" v-loading="loading">
    <el-card v-if="vo" shadow="never">
      <template #header>
        <div class="header">
          <span>订单详情</span>
          <div class="head-right">
            <el-tag :type="orderStatusTagType(vo.order.orderStatus)">
              {{ vo.statusDesc || orderStatusText(vo.order.orderStatus) }}
            </el-tag>
            <el-button size="small" @click="router.push('/student/orders')">返回列表</el-button>
          </div>
        </div>
      </template>

      <!-- 状态进度：当前没有 order_status_log 的查询接口，
           这里按 orderStatus 推导当前节点，已取消/申诉单独提示。
           自取单没有"配送中"环节，步骤条会少一步。 -->
      <el-steps v-if="!isAbnormal" :active="activeStep" align-center finish-status="success" class="steps">
        <el-step v-for="(title, i) in stepTitles" :key="i" :title="title" />
      </el-steps>
      <el-alert
        v-else
        :title="vo.order.orderStatus === 5 ? '订单已取消' : '订单申诉中'"
        :type="vo.order.orderStatus === 5 ? 'info' : 'warning'"
        :closable="false"
        show-icon
        class="abnormal"
      />

      <el-alert
        v-if="selfPickup && vo.order.orderStatus === 2"
        type="success"
        :closable="false"
        show-icon
        class="abnormal"
        :title="`自取订单：请到店向商家报取餐码 ${vo.order.pickupCode || ''}，拿到餐后点「确认取餐」`"
      />

      <el-descriptions :column="2" border class="block">
        <el-descriptions-item label="订单号">{{ vo.order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ vo.order.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="配送方式">{{ deliveryTypeText(vo.order.deliveryType) }}</el-descriptions-item>
        <el-descriptions-item label="支付状态">{{ payStatusText(vo.order.payStatus) }}</el-descriptions-item>
        <el-descriptions-item label="收货人">{{ vo.order.receiver || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ vo.order.receiverPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收货地址" :span="2">{{ vo.order.address || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="vo.order.remark" label="备注" :span="2">
          {{ vo.order.remark }}
        </el-descriptions-item>
        <el-descriptions-item v-if="vo.order.pickupCode" label="取餐码">
          <span class="pickup-code">{{ vo.order.pickupCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="vo.order.cancelReason" label="取消原因">
          {{ vo.order.cancelReason }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="section-title">商品明细</div>
      <div class="item-list">
        <div v-for="item in vo.items" :key="item.id" class="item-row">
          <div class="item-thumb">
            <el-image :src="dishCover({ dishId: item.dishId, dishImage: item.dishImage })" fit="cover" class="thumb-img">
              <template #error><div class="thumb-placeholder">🍽</div></template>
            </el-image>
          </div>
          <span class="item-name">{{ item.dishName }}</span>
          <span class="text-muted">¥{{ money(item.price) }} × {{ item.quantity }}</span>
          <span class="item-amount">¥{{ money(item.amount) }}</span>
        </div>
      </div>

      <div class="fee-box">
        <div class="fee-row"><span>商品金额</span><span>¥{{ money(vo.order.goodsAmount) }}</span></div>
        <div class="fee-row"><span>配送费</span><span>¥{{ money(vo.order.deliveryFee) }}</span></div>
        <div class="fee-row total"><span>实付金额</span><span>¥{{ money(vo.order.totalAmount) }}</span></div>
      </div>

      <div class="actions">
        <el-button
          v-if="canCancelOrder(vo.order.orderStatus)"
          @click="onCancel"
        >
          取消订单
        </el-button>
        <el-button
          v-if="showConfirmPickup"
          type="success"
          :loading="confirming"
          @click="onConfirmPickup"
        >
          确认取餐
        </el-button>
        <el-button
          v-if="canReviewOrder(vo.order.orderStatus)"
          type="primary"
          @click="router.push('/student/orders')"
        >
          去评价
        </el-button>
      </div>
    </el-card>

    <el-empty v-else-if="!loading" description="订单不存在或无权查看" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { cancelOrder, confirmSelfPickup, getOrderDetail, type OrderVO } from '@/api/order'
import {
  canCancelOrder,
  canConfirmPickup,
  canReviewOrder,
  deliveryTypeText,
  isSelfPickup,
  money,
  orderStatusTagType,
  orderStatusText,
  payStatusText
} from '@/utils/dict'
import { dishCover } from '@/utils/image'

const route = useRoute()
const router = useRouter()

const orderId = route.params.orderId as string
const vo = ref<OrderVO | null>(null)
const loading = ref(false)
const confirming = ref(false)

/** 是否处于非正常流程（已取消 / 申诉中），这两种不走步骤条 */
const isAbnormal = computed(() => {
  const s = vo.value?.order.orderStatus
  return s === 5 || s === 6
})

const selfPickup = computed(() => isSelfPickup(vo.value?.order.deliveryType))

/** 自取单没有"配送中"，所以步骤条少一步 */
const stepTitles = computed(() =>
  selfPickup.value
    ? ['已下单', '备餐中', '待取餐', '已完成']
    : ['已下单', '备餐中', '待取餐', '配送中', '已完成']
)

/**
 * el-steps 的 active 从 0 开始，表示"当前进行到第几步"。
 * 配送单：状态 0~4 与步骤下标一一对应，已完成时用 5 让全部点亮。
 * 自取单：只有 4 步，状态 4（已完成）对应 active=4。
 */
const activeStep = computed(() => {
  const s = vo.value?.order.orderStatus ?? 0
  if (selfPickup.value) {
    // 自取单理论上不会出现 3（配送中），若出现按"待取餐"处理
    return s === 4 ? 4 : Math.min(s, 2)
  }
  return s === 4 ? 5 : s
})

/** 待取餐且是自取单时，学生可以自己点"确认取餐"（商户也可以在他的订单页点） */
const showConfirmPickup = computed(() =>
  canConfirmPickup(vo.value?.order.deliveryType, vo.value?.order.orderStatus)
)

async function load() {
  loading.value = true
  try {
    vo.value = await getOrderDetail(orderId)
  } catch {
    // 越权或不存在由拦截器提示
  } finally {
    loading.value = false
  }
}

async function onConfirmPickup() {
  try {
    await ElMessageBox.confirm(
      '确认已经拿到餐品？确认后订单将直接完成，且不能再取消。',
      '确认取餐',
      { type: 'warning', confirmButtonText: '已取餐', cancelButtonText: '还没拿到' }
    )
  } catch {
    return
  }
  confirming.value = true
  try {
    await confirmSelfPickup(orderId)
    ElMessage.success('已确认取餐，订单完成，可以去评价了')
    await load()
  } catch {
    // 状态已变更等业务错误由拦截器提示
  } finally {
    confirming.value = false
  }
}

async function onCancel() {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt('请填写取消原因（选填）', '取消订单', {
      inputPlaceholder: '如：不想要了',
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想'
    })
    reason = (res.value || '').trim()
  } catch {
    return
  }
  try {
    await cancelOrder(orderId, reason || undefined)
    ElMessage.success('订单已取消')
    await load()
  } catch {
    // 已被接单等业务错误由拦截器提示
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 980px;
  margin: 0 auto;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.steps {
  margin: 8px 0 20px;
}
.abnormal {
  margin: 8px 0 20px;
}
.block {
  margin-bottom: 20px;
}
.pickup-code {
  font-family: Consolas, monospace;
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
  letter-spacing: 2px;
}
.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}
.item-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 18px;
}
.item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.item-thumb {
  width: 40px;
  height: 40px;
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
  width: 80px;
  text-align: right;
  font-weight: 600;
}
.fee-box {
  margin-left: auto;
  width: 260px;
  font-size: 13px;
  border-top: 1px dashed #ebeef5;
  padding-top: 12px;
}
.fee-row {
  display: flex;
  justify-content: space-between;
  line-height: 2;
}
.fee-row.total {
  font-size: 15px;
  font-weight: 600;
  color: #f56c6c;
  border-top: 1px solid #ebeef5;
  margin-top: 6px;
  padding-top: 6px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
.text-muted {
  color: #909399;
}
</style>
