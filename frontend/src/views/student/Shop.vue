<template>
  <div class="page" v-loading="loading">
    <!-- 店铺头部 -->
    <el-card v-if="shop" shadow="never">
      <div class="shop-header">
        <div class="logo-box">
          <el-image v-if="shop.logo" :src="shop.logo" fit="cover" class="logo-img" />
          <div v-else class="logo-placeholder">🏪</div>
        </div>
        <div class="shop-info">
          <div class="title-row">
            <h2 class="name">{{ shop.shopName }}</h2>
            <el-tag :type="shop.businessStatus === 1 ? 'success' : 'info'" size="small">
              {{ shop.businessStatus === 1 ? '营业中' : '休息中' }}
            </el-tag>
          </div>
          <div class="meta">
            <span class="score">★ {{ shop.score ?? '-' }}</span>
            <span>月售 {{ shop.monthlySales ?? 0 }}</span>
            <span>起送 ¥{{ money(shop.minPrice) }}</span>
            <span>配送 ¥{{ money(shop.deliveryFee) }}</span>
            <span v-if="shop.deliveryTime">约 {{ shop.deliveryTime }} 分钟</span>
          </div>
          <div v-if="shop.notice" class="notice">公告：{{ shop.notice }}</div>
          <div class="meta text-muted">
            <span v-if="shop.address">{{ shop.address }}</span>
            <span v-if="shop.openTime">营业时间 {{ shop.openTime }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 菜品列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="section-header">
          <span>菜品（{{ dishes.length }}）</span>
          <el-button text type="primary" @click="router.push('/student/cart')">
            去购物车（{{ cartStore.items.length }}）
          </el-button>
        </div>
      </template>

      <el-empty v-if="!loading && !dishes.length" description="该店铺暂无可售菜品" />
      <div v-else class="dish-list">
        <div v-for="dish in dishes" :key="dish.id" class="dish-row">
          <div class="dish-thumb" @click="router.push(`/student/dish/${dish.id}`)">
            <el-image :src="dishCover(dish)" fit="cover" class="thumb-img">
              <template #error><div class="thumb-placeholder">🍽</div></template>
            </el-image>
          </div>
          <div class="dish-main" @click="router.push(`/student/dish/${dish.id}`)">
            <div class="dish-name">{{ dish.dishName }}</div>
            <div v-if="dish.description" class="dish-desc">{{ dish.description }}</div>
            <div class="dish-tags">
              <el-tag v-for="t in splitCsv(dish.tags)" :key="t" size="small" type="info">{{ t }}</el-tag>
            </div>
            <div class="dish-meta">
              <span class="price">¥{{ money(dish.price) }}</span>
              <span class="text-muted">月售 {{ dish.monthlySales ?? 0 }}</span>
              <span class="text-muted">库存 {{ dish.stock ?? 0 }}</span>
            </div>
          </div>
          <div class="dish-action">
            <el-button
              type="primary"
              size="small"
              :disabled="(dish.stock ?? 0) <= 0"
              :loading="addingId === dish.id"
              @click="onAdd(dish)"
            >
              {{ (dish.stock ?? 0) <= 0 ? '售罄' : '加入购物车' }}
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { addToCart } from '@/api/cart'
import { getShopDetail, listShopDishes, type Shop } from '@/api/shop'
import type { Dish } from '@/api/dish'
import { useCartStore } from '@/store/cart'
import { money, splitCsv } from '@/utils/dict'
import { dishCover } from '@/utils/image'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const shopId = route.params.shopId as string
const shop = ref<Shop | null>(null)
const dishes = ref<Dish[]>([])
const loading = ref(false)
const addingId = ref('')

async function load() {
  loading.value = true
  try {
    const [s, d] = await Promise.all([getShopDetail(shopId), listShopDishes(shopId)])
    shop.value = s
    dishes.value = d
  } catch {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

async function onAdd(dish: Dish) {
  addingId.value = dish.id
  try {
    await addToCart({ dishId: dish.id, quantity: 1 })
    ElMessage.success(`已加入购物车：${dish.dishName}`)
    await cartStore.refresh()
  } catch {
    // 同店约束等业务错误由拦截器提示
  } finally {
    addingId.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.shop-header {
  display: flex;
  gap: 16px;
}
.logo-box {
  width: 88px;
  height: 88px;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f7fa;
  flex-shrink: 0;
}
.logo-img {
  width: 100%;
  height: 100%;
}
.logo-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
}
.shop-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.name {
  margin: 0;
  font-size: 18px;
}
.meta {
  display: flex;
  gap: 14px;
  font-size: 13px;
  flex-wrap: wrap;
}
.notice {
  font-size: 13px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 6px 10px;
  border-radius: 4px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dish-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dish-row {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.dish-thumb {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
  cursor: pointer;
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
  font-size: 28px;
}
.dish-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
}
.dish-name {
  font-weight: 600;
}
.dish-desc {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.dish-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 12px;
}
.price {
  color: #f56c6c;
  font-weight: 600;
  font-size: 14px;
}
.text-muted {
  color: #909399;
}
.dish-action {
  display: flex;
  align-items: center;
}
</style>
