<template>
  <div class="page" v-loading="loading">
    <!-- 核心指标 -->
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>数据看板</span>
          <el-button size="small" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="4"><el-statistic title="订单总数" :value="n('orderCount')" /></el-col>
        <el-col :span="4">
          <el-statistic title="成交金额（元）" :value="n('salesAmount')" :precision="2" />
        </el-col>
        <el-col :span="4"><el-statistic title="学生用户" :value="n('studentCount')" /></el-col>
        <el-col :span="4"><el-statistic title="入驻店铺" :value="n('shopCount')" /></el-col>
        <el-col :span="4"><el-statistic title="在岗骑手" :value="n('riderCount')" /></el-col>
        <el-col :span="4"><el-statistic title="待处理工单" :value="n('ticketWaiting')" /></el-col>
      </el-row>
      <el-row :gutter="16" class="mt">
        <el-col :span="6">
          <el-statistic title="待审核商户" :value="n('pendingMerchantAudit')" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="待审核骑手" :value="n('pendingRiderAudit')" />
        </el-col>
        <el-col :span="6"><el-statistic title="评价总数" :value="n('reviewCount')" /></el-col>
        <el-col :span="6">
          <el-button text type="primary" @click="router.push('/admin/merchants')">
            去审核商户 →
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 趋势 -->
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>订单量与销售额趋势</span>
          <el-radio-group v-model="days" size="small" @change="loadTrend">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="15">近 15 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="trendRef" class="chart" />
    </el-card>

    <!-- 占比 + 时段 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>品类订单占比</template>
          <div ref="pieRef" class="chart" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>下单时段分布</template>
          <div ref="hourRef" class="chart" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 排行 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>店铺销量排行</template>
          <el-table :data="shopRanking" size="small" stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="shopName" label="店铺" min-width="140" />
            <el-table-column prop="monthlySales" label="月售" width="90" />
            <el-table-column prop="score" label="评分" width="80" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>菜品销量排行</template>
          <el-table :data="dishRanking" size="small" stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="dishName" label="菜品" min-width="120" />
            <el-table-column prop="shopName" label="所属店铺" min-width="130" />
            <el-table-column prop="monthlySales" label="月售" width="90" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 热词 -->
    <el-card shadow="never">
      <template #header>用户反馈热词（AI 提取的评价标签）</template>
      <el-empty v-if="!tagCloud.length" description="还没有评价标签数据" />
      <div v-else class="tags">
        <el-tag v-for="t in tagCloud" :key="String(t.tagName)" size="small" type="info">
          {{ t.tagName }}（{{ t.tagCount }}）
        </el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'

import {
  dashboardCategoryShare,
  dashboardDishRanking,
  dashboardHourDistribution,
  dashboardOverview,
  dashboardShopRanking,
  dashboardTagCloud,
  dashboardTrend
} from '@/api/admin'

const router = useRouter()

const loading = ref(false)
const days = ref(7)
const overview = ref<Record<string, unknown>>({})
const shopRanking = ref<Array<Record<string, unknown>>>([])
const dishRanking = ref<Array<Record<string, unknown>>>([])
const tagCloud = ref<Array<Record<string, unknown>>>([])

const trendRef = ref<HTMLElement>()
const pieRef = ref<HTMLElement>()
const hourRef = ref<HTMLElement>()

let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
let hourChart: echarts.ECharts | null = null

function n(key: string): number {
  const v = Number(overview.value[key])
  return Number.isFinite(v) ? v : 0
}

async function loadOverview() {
  try {
    const [o, sr, dr, tc] = await Promise.all([
      dashboardOverview(),
      dashboardShopRanking(10),
      dashboardDishRanking(10),
      dashboardTagCloud(30)
    ])
    overview.value = o
    shopRanking.value = sr
    dishRanking.value = dr
    tagCloud.value = tc
  } catch {
    // 拦截器提示
  }
}

async function loadTrend() {
  try {
    const rows = await dashboardTrend(days.value)
    const labels = rows.map((r) => String(r.stat_date ?? ''))
    const orders = rows.map((r) => Number(r.order_count ?? 0))
    const sales = rows.map((r) => Number(r.sales_amount ?? 0))

    await nextTick()
    if (!trendRef.value) return
    if (!trendChart) trendChart = echarts.init(trendRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['订单量', '销售额'] },
      grid: { left: 50, right: 50, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: labels },
      yAxis: [
        { type: 'value', name: '订单量' },
        { type: 'value', name: '销售额' }
      ],
      series: [
        { name: '订单量', type: 'line', smooth: true, data: orders, areaStyle: { opacity: 0.12 } },
        { name: '销售额', type: 'bar', yAxisIndex: 1, data: sales, barMaxWidth: 22 }
      ]
    })
  } catch {
    // 拦截器提示
  }
}

async function loadCharts() {
  try {
    const [cats, hours] = await Promise.all([dashboardCategoryShare(), dashboardHourDistribution()])
    await nextTick()

    if (pieRef.value) {
      if (!pieChart) pieChart = echarts.init(pieRef.value)
      pieChart.setOption({
        tooltip: { trigger: 'item' },
        series: [
          {
            type: 'pie',
            radius: ['45%', '70%'],
            data: cats.map((c) => ({
              name: String(c.categoryName ?? '未分类'),
              value: Number(c.orderCount ?? 0)
            }))
          }
        ]
      })
    }

    if (hourRef.value) {
      if (!hourChart) hourChart = echarts.init(hourRef.value)
      hourChart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 45, right: 20, top: 20, bottom: 30 },
        xAxis: {
          type: 'category',
          data: hours.map((h) => `${h.hourOfDay ?? 0}时`)
        },
        yAxis: { type: 'value' },
        series: [
          {
            type: 'bar',
            data: hours.map((h) => Number(h.orderCount ?? 0)),
            barMaxWidth: 22,
            itemStyle: { color: '#244b73' }
          }
        ]
      })
    }
  } catch {
    // 拦截器提示
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadTrend(), loadCharts()])
  } finally {
    loading.value = false
  }
}

function onResize() {
  trendChart?.resize()
  pieChart?.resize()
  hourChart?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  pieChart?.dispose()
  hourChart?.dispose()
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
}
.mt {
  margin-top: 18px;
}
.chart {
  width: 100%;
  height: 260px;
}
.tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
