<template>
  <div class="page" v-loading="cartStore.loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>购物车</span>
          <div class="header-right">
            <span v-if="shopName" class="text-muted">{{ shopName }}</span>
            <el-button v-if="cartStore.items.length" text type="danger" @click="onClear">清空</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!cartStore.items.length" description="购物车是空的，去首页逛逛吧">
        <el-button type="primary" @click="router.push('/student/home')">去首页</el-button>
      </el-empty>

      <template v-else>
        <div class="cart-list">
          <div v-for="item in cartStore.items" :key="item.id" class="cart-row">
            <el-checkbox
              :model-value="item.selected === 1"
              @change="(val: boolean) => onSelected(item, val)"
            />

            <div class="thumb">
              <el-image :src="dishCover({ dishId: item.dishId, dishImage: item.dishImage })" fit="cover" class="thumb-img">
                <template #error><div class="thumb-placeholder">🍽</div></template>
              </el-image>
            </div>

            <div class="main">
              <div class="name">{{ item.dishName }}</div>
              <div class="meta">
                <span class="price">¥{{ money(item.price) }}</span>
                <el-tag v-if="item.dishStatus !== 1" type="danger" size="small">已下架</el-tag>
                <el-tag
                  v-else-if="item.stock !== undefined && item.quantity > item.stock"
                  type="warning"
                  size="small"
                >
                  库存不足（{{ item.stock }}）
                </el-tag>
              </div>
            </div>

            <el-input-number
              :model-value="item.quantity"
              :min="1"
              :max="item.stock && item.stock > 0 ? item.stock : 99"
              size="small"
              @change="(val: number | undefined) => onQuantity(item, val)"
            />

            <div class="amount">¥{{ money(item.amount) }}</div>

            <el-button text type="danger" @click="onRemove(item)">删除</el-button>
          </div>
        </div>

        <!-- 结算栏 -->
        <div class="checkout-bar">
          <div class="summary">
            <span>已选 <b>{{ cartStore.selectedCount }}</b> 件</span>
            <span class="total">合计 ¥{{ money(cartStore.selectedAmount) }}</span>
          </div>
          <div class="checkout-right">
            <span v-if="minPriceGap > 0" class="gap">
              还差 ¥{{ money(minPriceGap) }} 起送
            </span>
            <el-button
              type="primary"
              :disabled="!canCheckout"
              @click="openCheckout"
            >
              去结算
            </el-button>
          </div>
        </div>
      </template>
    </el-card>

    <!-- 结算弹窗 -->
    <el-dialog v-model="dialogVisible" title="确认订单" width="560px">
      <el-form label-width="88px">
        <el-form-item label="收货地址">
          <el-radio-group v-if="addresses.length" v-model="selectedAddressId" class="addr-group">
            <el-radio v-for="a in addresses" :key="a.id" :value="a.id as string" class="addr-item">
              <div class="addr-line">
                <b>{{ a.receiver }}</b> {{ a.phone }}
                <el-tag v-if="a.isDefault" size="small" type="success">默认</el-tag>
              </div>
              <div class="addr-detail text-muted">{{ joinAddress(a) }}</div>
            </el-radio>
          </el-radio-group>
          <div v-else class="no-addr">
            还没有收货地址，请先到
            <el-button text type="primary" @click="router.push('/student/profile')">个人中心</el-button>
            添加
          </div>
        </el-form-item>

        <el-form-item label="配送方式">
          <el-radio-group v-model="deliveryType">
            <el-radio :value="1">骑手配送</el-radio>
            <el-radio :value="2">宿舍楼下自取</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="remark" maxlength="255" placeholder="选填，如少放辣、放门口" />
        </el-form-item>

        <el-form-item label="费用明细">
          <div class="fee-box">
            <div>商品金额 ¥{{ money(cartStore.selectedAmount) }}</div>
            <div>配送费 ¥{{ money(deliveryFee) }}</div>
            <div class="total-line">应付 ¥{{ money(payableAmount) }}</div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  clearCart,
  removeCartItem,
  updateQuantity,
  updateSelected,
  type CartItem
} from '@/api/cart'
import { createOrder } from '@/api/order'
import { getShopDetail, type Shop } from '@/api/shop'
import { listAddress, type UserAddress } from '@/api/user'
import { useCartStore } from '@/store/cart'
import { joinAddress, money } from '@/utils/dict'
import { dishCover } from '@/utils/image'

const router = useRouter()
const cartStore = useCartStore()

const shop = ref<Shop | null>(null)
const addresses = ref<UserAddress[]>([])

const dialogVisible = ref(false)
const submitting = ref(false)
const selectedAddressId = ref<string>('')
const deliveryType = ref(1)
const remark = ref('')

const shopName = computed(() => cartStore.items[0]?.shopName || shop.value?.shopName || '')
const minPrice = computed(() => shop.value?.minPrice ?? 0)
const minPriceGap = computed(() => Math.max(0, minPrice.value - cartStore.selectedAmount))

/** 自取不收配送费，与后端 createOrder 的逻辑保持一致 */
const deliveryFee = computed(() => (deliveryType.value === 2 ? 0 : shop.value?.deliveryFee ?? 0))
const payableAmount = computed(() => cartStore.selectedAmount + deliveryFee.value)

const canCheckout = computed(
  () => cartStore.selectedCount > 0 && minPriceGap.value <= 0
)

async function loadShop() {
  const shopId = cartStore.shopId
  if (!shopId) {
    shop.value = null
    return
  }
  try {
    shop.value = await getShopDetail(shopId)
  } catch {
    shop.value = null
  }
}

onMounted(async () => {
  await cartStore.refresh()
  await loadShop()
})

async function onSelected(item: CartItem, checked: boolean) {
  try {
    await updateSelected(item.id, checked ? 1 : 0)
    await cartStore.refresh()
  } catch {
    // 拦截器已提示
  }
}

async function onQuantity(item: CartItem, val: number | undefined) {
  const quantity = Number(val)
  if (!quantity || quantity === item.quantity) return
  try {
    await updateQuantity(item.id, quantity)
    await cartStore.refresh()
  } catch {
    await cartStore.refresh()
  }
}

async function onRemove(item: CartItem) {
  try {
    await removeCartItem(item.id)
    await cartStore.refresh()
    await loadShop()
  } catch {
    // 拦截器已提示
  }
}

async function onClear() {
  try {
    await ElMessageBox.confirm('确定清空购物车？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearCart()
    await cartStore.refresh()
  } catch {
    // 拦截器已提示
  }
}

async function openCheckout() {
  deliveryType.value = shop.value?.deliveryFee ? 1 : 2
  remark.value = ''
  try {
    addresses.value = await listAddress()
    const preferred = addresses.value.find((a) => a.isDefault) || addresses.value[0]
    selectedAddressId.value = (preferred?.id as string) || ''
  } catch {
    addresses.value = []
  }
  dialogVisible.value = true
}

async function onSubmit() {
  const address = addresses.value.find((a) => a.id === selectedAddressId.value)
  if (!address) {
    ElMessage.warning('请选择收货地址')
    return
  }
  submitting.value = true
  try {
    await createOrder({
      deliveryType: deliveryType.value,
      // 后端 createOrder 直接置为已支付，payType 只做记录
      payType: 1,
      receiver: address.receiver,
      receiverPhone: address.phone,
      address: joinAddress(address),
      remark: remark.value.trim() || undefined
    })
    ElMessage.success('下单成功')
    dialogVisible.value = false
    await cartStore.refresh()
    router.push('/student/orders')
  } catch {
    // 库存不足等业务错误由拦截器提示
  } finally {
    submitting.value = false
  }
}
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
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.cart-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cart-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.thumb {
  width: 60px;
  height: 60px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
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
  font-size: 24px;
}
.main {
  flex: 1;
  min-width: 0;
}
.name {
  font-weight: 600;
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
}
.price {
  color: #f56c6c;
  font-weight: 600;
}
.amount {
  width: 90px;
  text-align: right;
  font-weight: 600;
  color: #f56c6c;
}
.checkout-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.summary {
  display: flex;
  gap: 16px;
  align-items: baseline;
}
.total {
  font-size: 16px;
  font-weight: 600;
  color: #f56c6c;
}
.checkout-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.gap {
  font-size: 12px;
  color: #e6a23c;
}
.addr-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.addr-item {
  height: auto;
  align-items: flex-start;
}
.addr-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.addr-detail {
  font-size: 12px;
}
.no-addr {
  font-size: 13px;
  color: #909399;
}
.fee-box {
  font-size: 13px;
  line-height: 1.8;
}
.total-line {
  font-weight: 600;
  color: #f56c6c;
}
.text-muted {
  color: #909399;
}
</style>
