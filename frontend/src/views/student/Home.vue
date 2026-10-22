<template>
  <div class="page">
    <!-- 搜索栏 -->
    <el-card shadow="never">
      <div class="search-row">
        <el-input
          v-model="keyword"
          placeholder="搜索店铺名称，回车确认"
          clearable
          class="search-input"
          @keyup.enter="onSearch"
        >
          <template #append>
            <el-button @click="onSearch">搜索</el-button>
          </template>
        </el-input>
      </div>
    </el-card>

    <!-- 个性化推荐 -->
    <el-card v-if="recommendDishes.length" shadow="never">
      <template #header>
        <div class="section-header">
          <span>为你推荐</span>
          <span class="hint">{{ recommendReason || '基于你的饮食档案' }}</span>
        </div>
      </template>
      <div class="dish-grid">
        <div
          v-for="dish in recommendDishes"
          :key="dish.id"
          class="dish-card"
          @click="router.push(`/student/dish/${dish.id}`)"
        >
          <div class="dish-thumb">
            <el-image :src="dishCover(dish)" fit="cover" class="thumb-img">
              <template #error><div class="thumb-placeholder">🍽</div></template>
            </el-image>
          </div>
          <div class="dish-body">
            <div class="dish-name">{{ dish.dishName }}</div>
            <div class="dish-meta">
              <span class="price">¥{{ money(dish.price) }}</span>
              <span class="text-muted">月售 {{ dish.monthlySales ?? 0 }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 店铺列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="section-header">
          <span>全部店铺</span>
          <el-select v-model="sortBy" style="width: 130px" @change="reload">
            <el-option label="按销量" value="sales" />
            <el-option label="按评分" value="score" />
          </el-select>
        </div>
      </template>

      <el-empty v-if="!loading && !shops.length" description="暂无营业中的店铺" />
      <div v-else v-loading="loading" class="shop-grid">
        <div
          v-for="shop in shops"
          :key="shop.id"
          class="shop-card"
          @click="router.push(`/student/shop/${shop.id}`)"
        >
          <div class="shop-thumb">
            <el-image v-if="shop.logo" :src="shop.logo" fit="cover" class="thumb-img" />
            <div v-else class="thumb-placeholder">🏪</div>
          </div>
          <div class="shop-body">
            <div class="shop-title-row">
              <span class="shop-name">{{ shop.shopName }}</span>
              <el-tag v-if="shop.businessStatus !== 1" type="info" size="small">休息中</el-tag>
            </div>
            <div class="shop-meta">
              <span class="score">★ {{ shop.score ?? '-' }}</span>
              <span class="text-muted">月售 {{ shop.monthlySales ?? 0 }}</span>
            </div>
            <div class="shop-meta text-muted">
              起送 ¥{{ money(shop.minPrice) }} · 配送 ¥{{ money(shop.deliveryFee) }}
              <span v-if="shop.deliveryTime"> · 约 {{ shop.deliveryTime }} 分钟</span>
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
        @current-change="loadShops"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { recommend } from '@/api/ai'
import type { Dish } from '@/api/dish'
import { listShops, type Shop } from '@/api/shop'
import { money } from '@/utils/dict'
import { dishCover } from '@/utils/image'

const router = useRouter()

const keyword = ref('')
const sortBy = ref('sales')
const shops = ref<Shop[]>([])
const loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 8, total: 0 })

const recommendDishes = ref<Dish[]>([])
const recommendReason = ref('')

async function loadShops() {
  loading.value = true
  try {
    const res = await listShops({
      keyword: keyword.value.trim() || undefined,
      pageNum: page.pageNum,
      pageSize: page.pageSize
    })
    shops.value = res.list
    page.total = res.total
  } catch {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

/** AI 推荐位属于可降级内容：失败就不展示，不影响首页其它部分 */
async function loadRecommend() {
  try {
    // 后端返回 Map：{ dishes, reason, coldStart, scene, modelVersion }
    const res = await recommend('HOME', 6)
    recommendDishes.value = (res.dishes as Dish[]) || []
    recommendReason.value = (res.reason as string) || ''
  } catch {
    recommendDishes.value = []
  }
}

function reload() {
  page.pageNum = 1
  loadShops()
}

function onSearch() {
  page.pageNum = 1
  loadShops()
}

onMounted(() => {
  loadShops()
  loadRecommend()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-row {
  display: flex;
  justify-content: center;
}
.search-input {
  max-width: 520px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hint {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}
.shop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  min-height: 80px;
}
.shop-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.shop-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.shop-thumb,
.dish-thumb {
  width: 64px;
  height: 64px;
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
  font-size: 26px;
}
.shop-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.shop-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.shop-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-meta {
  display: flex;
  gap: 10px;
  font-size: 12px;
}
.score {
  color: #e6a23c;
}
.dish-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}
.dish-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}
.dish-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.dish-card .dish-thumb {
  width: 100%;
  height: 90px;
  border-radius: 0;
}
.dish-body {
  padding: 8px 10px;
}
.dish-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
}
.price {
  color: #f56c6c;
  font-weight: 600;
}
.text-muted {
  color: #909399;
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
</style>
