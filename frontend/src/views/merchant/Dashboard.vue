<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>经营概览</span>
          <div class="header-right">
            <el-tag :type="shop?.businessStatus === 1 ? 'success' : 'info'" size="small">
              {{ shop?.businessStatus === 1 ? '营业中' : '休息中' }}
            </el-tag>
            <el-switch
              v-model="opening"
              active-text="营业"
              inactive-text="休息"
              @change="onToggleBusiness"
            />
            <el-button size="small" :loading="loading" @click="loadAll">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="shop && shop.banStatus === 1"
        type="error"
        :closable="false"
        show-icon
        class="mb"
        title="店铺已被平台封禁，学生端不会展示，请先联系管理员解封"
      />
      <el-alert
        v-else-if="shop && shop.businessStatus !== 1"
        type="warning"
        :closable="false"
        show-icon
        class="mb"
        title="店铺当前为休息中，学生端可见但无法下单；打开「营业」开关即可接单"
      />

      <el-row :gutter="16">
        <el-col :span="6"><el-statistic title="今日订单" :value="s.todayOrders" /></el-col>
        <el-col :span="6">
          <el-statistic title="今日营业额（元）" :value="s.todaySales" :precision="2" />
        </el-col>
        <el-col :span="6"><el-statistic title="待接单" :value="s.pendingCount" /></el-col>
        <el-col :span="6">
          <el-statistic title="店铺评分" :value="num(shop?.score)" :precision="2" />
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt">
        <el-col :span="6"><el-statistic title="备餐中" :value="s.preparingCount" /></el-col>
        <el-col :span="6"><el-statistic title="待取餐" :value="s.pickingCount" /></el-col>
        <el-col :span="6"><el-statistic title="配送中" :value="s.deliveringCount" /></el-col>
        <el-col :span="6"><el-statistic title="累计销售额（元）" :value="s.totalSales" :precision="2" /></el-col>
      </el-row>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>近 7 天经营趋势</span>
          <el-button size="small" text type="primary" @click="loadAll">刷新</el-button>
        </div>
      </template>
      <div class="trend">
        <div v-for="d in trend" :key="d.date" class="trend-col">
          <div class="trend-val">{{ num(d.sales) > 0 ? '¥' + money(d.sales) : '—' }}</div>
          <div class="trend-bar-wrap">
            <div class="trend-bar" :style="{ height: barHeight(d.sales) }" />
          </div>
          <div class="trend-label">{{ d.date.slice(5) }}</div>
          <div class="trend-orders">{{ num(d.orders) }} 单</div>
        </div>
      </div>

      <el-divider content-position="left">
        店内热销 Top{{ hot.length || 5 }}（近 30 天成交件数）
      </el-divider>
      <el-empty v-if="!hot.length" description="还没有成交数据" :image-size="60" />
      <div v-else class="hot-list">
        <div v-for="item in hot" :key="item.dishId" class="hot-item">
          <span class="hot-rank">{{ item.rank }}</span>
          <span class="hot-name">{{ item.dishName }}</span>
          <span class="hot-qty">{{ item.qty }} 件</span>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>菜品销售概览</span>
          <el-button size="small" text type="primary" @click="router.push('/merchant/ai-report')">
            查看 AI 经营简报
          </el-button>
        </div>
      </template>

      <el-empty v-if="!dishes.length" description="还没有菜品" />
      <el-table v-else :data="dishes" stripe>
        <el-table-column label="菜品" min-width="200">
          <template #default="{ row }">
            <div class="dish-cell">
              <div class="thumb">
                <el-image :src="dishCover(row)" fit="cover" class="thumb-img">
                  <template #error><div class="thumb-placeholder">🍽</div></template>
                </el-image>
              </div>
              <span>{{ row.dishName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="monthlySales" label="月售" width="90" />
        <el-table-column label="价格" width="100" align="right">
          <template #default="{ row }">¥{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getShopTopDishes, listMyDishes, type Dish } from '@/api/dish'
import { getShopStats, getShopTrend } from '@/api/order'
import { getMyShop, updateBusinessStatus, type Shop } from '@/api/shop'
import { money } from '@/utils/dict'
import { dishCover } from '@/utils/image'

const router = useRouter()

const loading = ref(false)
const shop = ref<Shop | null>(null)
const dishes = ref<Dish[]>([])
const opening = ref(false)

/** 近 7 天趋势（无单日后端已补 0） */
const trend = ref<Array<{ date: string; orders: number; sales: number }>>([])
/** 店内热销榜（后端 Redis ZSet 缓存 5 分钟） */
const hot = ref<Array<{ rank: number; dishId: string; dishName: string; qty: number }>>([])

const maxSales = computed(() => Math.max(...trend.value.map((d) => num(d.sales)), 1))
function barHeight(sales: unknown): string {
  return `${Math.max(4, (num(sales) / maxSales.value) * 100)}%`
}

const s = reactive({
  todayOrders: 0,
  todaySales: 0,
  pendingCount: 0,
  preparingCount: 0,
  pickingCount: 0,
  deliveringCount: 0,
  totalSales: 0
})

function num(v: unknown): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

async function loadAll() {
  loading.value = true
  try {
    const [shopInfo, stats, dishList] = await Promise.all([
      getMyShop(),
      getShopStats(),
      listMyDishes()
    ])
    shop.value = shopInfo
    opening.value = shopInfo.businessStatus === 1
    s.todayOrders = num(stats.todayOrders)
    s.todaySales = num(stats.todaySales)
    s.pendingCount = num(stats.pendingCount)
    s.preparingCount = num(stats.preparingCount)
    s.pickingCount = num(stats.pickingCount)
    s.deliveringCount = num(stats.deliveringCount)
    s.totalSales = num(stats.totalSales)
    dishes.value = dishList
    // 两个新增指标单独加载：旧后端没有这两个接口时，主看板照常显示
    trend.value = await getShopTrend(7).catch(() => [])
    hot.value = await getShopTopDishes(30, 5).catch(() => [])
  } catch {
    // 拦截器提示（例如商户未通过审核）
  } finally {
    loading.value = false
  }
}

async function onToggleBusiness(val: boolean) {
  try {
    await updateBusinessStatus(val ? 1 : 0)
    ElMessage.success(val ? '已开始营业' : '已切换为休息中')
  } catch {
    opening.value = !val
  }
}

onMounted(loadAll)
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
  gap: 12px;
}
.mb {
  margin-bottom: 16px;
}
.mt {
  margin-top: 20px;
}
.trend {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 170px;
  padding: 0 4px;
}
.trend-col {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}
.trend-val {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.trend-bar-wrap {
  width: 100%;
  flex: 1;
  display: flex;
  align-items: flex-end;
}
.trend-bar {
  width: 100%;
  min-height: 4px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, #409eff, #a0cfff);
}
.trend-label {
  font-size: 12px;
  color: #606266;
  margin-top: 6px;
}
.trend-orders {
  font-size: 12px;
  color: #909399;
}
.hot-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hot-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: 6px;
  background: #fafafa;
}
.hot-rank {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.hot-name {
  flex: 1;
}
.hot-qty {
  color: #e6a23c;
  font-weight: 600;
}
.dish-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.thumb {
  width: 32px;
  height: 32px;
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
</style>
