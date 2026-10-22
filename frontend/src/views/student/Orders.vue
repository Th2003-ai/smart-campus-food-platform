<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>我的订单</span>
          <el-radio-group v-model="status" size="small" @change="reload">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button :value="0">待接单</el-radio-button>
            <el-radio-button :value="1">备餐中</el-radio-button>
            <el-radio-button :value="2">待取餐</el-radio-button>
            <el-radio-button :value="3">配送中</el-radio-button>
            <el-radio-button :value="4">已完成</el-radio-button>
            <el-radio-button :value="5">已取消</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-empty v-if="!loading && !orders.length" description="暂无订单" />

      <div v-else v-loading="loading" class="order-list">
        <div v-for="vo in orders" :key="vo.order.id" class="order-card">
          <div class="order-head">
            <div class="head-left">
              <span class="order-no">{{ vo.order.orderNo }}</span>
              <span class="text-muted">{{ vo.order.createTime }}</span>
            </div>
            <el-tag :type="orderStatusTagType(vo.order.orderStatus)" size="small">
              {{ vo.statusDesc || orderStatusText(vo.order.orderStatus) }}
            </el-tag>
          </div>

          <div class="order-items" @click="router.push(`/student/orders/${vo.order.id}`)">
            <div v-for="item in vo.items" :key="item.id" class="item-row">
              <div class="item-thumb">
                <el-image :src="dishCover({ dishId: item.dishId, dishImage: item.dishImage })" fit="cover" class="thumb-img">
                  <template #error><div class="thumb-placeholder">🍽</div></template>
                </el-image>
              </div>
              <span class="item-name">{{ item.dishName }}</span>
              <span class="text-muted">× {{ item.quantity }}</span>
              <span class="item-amount">¥{{ money(item.amount) }}</span>
            </div>
          </div>

          <div class="order-foot">
            <div class="foot-left text-muted">
              <span v-if="vo.order.pickupCode">取餐码 {{ vo.order.pickupCode }}</span>
              <span>{{ deliveryTypeText(vo.order.deliveryType) }}</span>
            </div>
            <div class="foot-right">
              <span class="total">合计 ¥{{ money(vo.order.totalAmount) }}</span>
              <el-button
                v-if="canCancelOrder(vo.order.orderStatus)"
                size="small"
                @click="onCancel(vo.order.id)"
              >
                取消订单
              </el-button>
              <el-button
                v-if="canReviewOrder(vo.order.orderStatus)"
                size="small"
                type="primary"
                @click="openReview(vo.order.id)"
              >
                评价
              </el-button>
              <el-button size="small" text type="primary" @click="router.push(`/student/orders/${vo.order.id}`)">
                详情
              </el-button>
            </div>
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

    <!-- 评价弹窗 -->
    <el-dialog v-model="reviewVisible" title="评价订单" width="480px">
      <el-form label-width="88px">
        <el-form-item label="口味评分" required>
          <el-rate v-model="reviewForm.tasteScore" :max="5" show-score />
        </el-form-item>
        <el-form-item label="服务评分">
          <el-rate v-model="reviewForm.serviceScore" :max="5" />
        </el-form-item>
        <el-form-item label="配送评分">
          <el-rate v-model="reviewForm.deliveryScore" :max="5" />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input
            v-model="reviewForm.content"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说说这次的用餐体验"
          />
        </el-form-item>
        <el-form-item label="匿名">
          <el-switch v-model="reviewForm.anonymous" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="onSubmitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { cancelOrder, listMyOrders, type OrderVO } from '@/api/order'
import { submitReview } from '@/api/review'
import {
  canCancelOrder,
  canReviewOrder,
  deliveryTypeText,
  money,
  orderStatusTagType,
  orderStatusText
} from '@/utils/dict'
import { dishCover } from '@/utils/image'

const router = useRouter()

const status = ref<number | undefined>(undefined)
const orders = ref<OrderVO[]>([])
const loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const reviewVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewOrderId = ref('')
const reviewForm = reactive({
  tasteScore: 5,
  serviceScore: 5,
  deliveryScore: 5,
  content: '',
  anonymous: false
})

async function load() {
  loading.value = true
  try {
    const res = await listMyOrders({
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

async function onCancel(orderId: string) {
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
    // 已被商家接单等业务错误由拦截器提示
  }
}

function openReview(orderId: string) {
  reviewOrderId.value = orderId
  reviewForm.tasteScore = 5
  reviewForm.serviceScore = 5
  reviewForm.deliveryScore = 5
  reviewForm.content = ''
  reviewForm.anonymous = false
  reviewVisible.value = true
}

async function onSubmitReview() {
  if (!reviewForm.tasteScore) {
    ElMessage.warning('请先给口味打分')
    return
  }
  reviewSubmitting.value = true
  try {
    await submitReview({
      orderId: reviewOrderId.value,
      tasteScore: reviewForm.tasteScore,
      serviceScore: reviewForm.serviceScore || undefined,
      deliveryScore: reviewForm.deliveryScore || undefined,
      content: reviewForm.content.trim() || undefined,
      anonymous: reviewForm.anonymous
    })
    ElMessage.success('评价成功，感谢反馈')
    reviewVisible.value = false
    await load()
  } catch {
    // 重复评价等业务错误由拦截器提示
  } finally {
    reviewSubmitting.value = false
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
  gap: 10px;
}
.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.head-left {
  display: flex;
  gap: 12px;
  align-items: baseline;
  font-size: 13px;
}
.order-no {
  font-weight: 600;
}
.order-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;
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
.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}
.foot-left {
  display: flex;
  gap: 14px;
  font-size: 12px;
}
.foot-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.total {
  font-weight: 600;
  color: #f56c6c;
}
.text-muted {
  color: #909399;
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
</style>
