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
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { listMyDishes, type Dish } from '@/api/dish'
import { getShopStats } from '@/api/order'
import { getMyShop, updateBusinessStatus, type Shop } from '@/api/shop'
import { money } from '@/utils/dict'
import { dishCover } from '@/utils/image'

const router = useRouter()

const loading = ref(false)
const shop = ref<Shop | null>(null)
const dishes = ref<Dish[]>([])
const opening = ref(false)

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
