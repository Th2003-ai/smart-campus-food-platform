<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>缓存演示（PPT 新增页对照）</span>
          <span class="text-muted">
            第 13 页 三兄弟 · 第 15 页 热 key · 第 21 页 静态字典
          </span>
        </div>
      </template>

      <!-- 一、静态字典 -->
      <el-divider content-position="left">第 21 页 · 静态字典数据（启动预热 + 写时刷新）</el-divider>
      <div class="row">
        <el-tag type="success">
          已预热 {{ status?.dicts.tableCount ?? 0 }} 张表 / 共 {{ status?.dicts.totalRows ?? 0 }} 行
        </el-tag>
        <span class="text-muted">加载时间：{{ loadedAtText }}</span>
        <el-button size="small" type="primary" @click="onRefreshDicts">刷新字典</el-button>
        <el-select v-model="pickedTable" size="small" style="width: 180px" @change="onLoadDictDetail">
          <el-option v-for="t in status?.dictTables ?? []" :key="t" :label="t" :value="t" />
        </el-select>
        <el-button size="small" @click="onCompare">缓存 vs 直查库</el-button>
      </div>

      <el-table :data="dictRows" size="small" max-height="220" class="mt">
        <el-table-column
          v-for="col in dictColumns"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>

      <el-alert
        v-if="compare"
        class="mt"
        type="info"
        :closable="false"
        :title="`${compare.table}：缓存 ${compare.cacheCostMs} ms vs 直查库 ${compare.dbCostMs} ms（各查 ${compare.rounds} 次）`"
        :description="compare.note"
      />

      <!-- 二、热 key -->
      <el-divider content-position="left">第 15 页 · 热 key 探测（客户端埋点 + TopN）</el-divider>
      <div class="row">
        <el-input v-model="dishId" size="small" style="width: 340px" placeholder="菜品 ID" />
        <el-button size="small" type="primary" @click="onBurst">并发打 50 次</el-button>
        <el-button size="small" @click="onLoadHotKeys">刷新 TopN</el-button>
        <el-button size="small" @click="onResetHotKeys">重置统计</el-button>
        <span class="text-muted">总访问 {{ status?.hotKeyTotal ?? 0 }} 次</span>
      </div>
      <el-table :data="status?.hotKeys ?? []" size="small" max-height="220">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="key" label="缓存 key" min-width="320" show-overflow-tooltip />
        <el-table-column prop="count" label="访问次数" width="120" />
      </el-table>

      <!-- 三、三兄弟 -->
      <el-divider content-position="left">第 13 页 · 缓存三兄弟（对策 + 现场开关）</el-divider>
      <el-table :data="threeBrothers" size="small">
        <el-table-column prop="name" label="问题" width="90" />
        <el-table-column prop="desc" label="触发条件" min-width="200" />
        <el-table-column prop="fix" label="本项目对策" min-width="260" />
      </el-table>

      <div class="row mt">
        <span class="text-muted">防穿透开关（关闭后可复现「每个请求都打库」）：</span>
        <el-switch
          :model-value="status?.nullValueCacheEnabled ?? true"
          active-text="开启"
          inactive-text="关闭"
          @change="onToggleNullCache"
        />
        <span class="text-muted">
          三组对比数字请在终端跑 <code>python docs/demo_three.py</code>
        </span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  burstDishDetail,
  compareDict,
  getCacheStatus,
  getDictDetail,
  refreshDicts,
  resetHotKeys,
  switchNullCache,
  type CacheStatus,
  type DictCompare
} from '@/api/cache'

const status = ref<CacheStatus | null>(null)
const pickedTable = ref('dish_category')
const dictRows = ref<Record<string, unknown>[]>([])
const compare = ref<DictCompare | null>(null)
const dishId = ref('DI0000000000000000000000000002')

const threeBrothers = [
  {
    name: '穿透',
    desc: '查的 key 在库里也不存在，缓存永远不命中',
    fix: '空值缓存 60 秒（默认开，可切换）'
  },
  {
    name: '击穿',
    desc: '一个热点 key 刚好过期，N 个请求同时回源',
    fix: 'SETNX 互斥重建（5 秒）+ 双重检查 + 等待重读'
  },
  {
    name: '雪崩',
    desc: '大批 key 同一秒过期，或整台 Redis 不可用',
    fix: 'TTL ±20% 抖动 + Redis 故障降级直查库'
  }
]

const loadedAtText = computed(() => {
  const ts = status.value?.dicts.loadedAt ?? 0
  return ts ? new Date(ts).toLocaleTimeString() : '未加载'
})

const dictColumns = computed(() => {
  const first = dictRows.value[0]
  return first ? Object.keys(first).slice(0, 6) : []
})

async function load() {
  try {
    status.value = await getCacheStatus()
    if (!status.value.dictTables.includes(pickedTable.value)) {
      pickedTable.value = status.value.dictTables[0] ?? 'dish_category'
    }
    await onLoadDictDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败，请确认已用 admin 登录')
  }
}

async function onLoadDictDetail() {
  try {
    dictRows.value = await getDictDetail(pickedTable.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载字典失败')
  }
}

async function onRefreshDicts() {
  try {
    const result = await refreshDicts()
    ElMessage.success(`字典已刷新，耗时 ${result.costMs} ms`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新失败')
  }
}

async function onCompare() {
  try {
    compare.value = await compareDict(pickedTable.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '对比失败')
  }
}

async function onLoadHotKeys() {
  await load()
}

async function onResetHotKeys() {
  try {
    await resetHotKeys()
    ElMessage.success('热 key 统计已清零')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重置失败')
  }
}

async function onBurst() {
  try {
    await burstDishDetail(dishId.value, 50)
    ElMessage.success('已并发请求 50 次，看下方 TopN 的计数')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '并发请求失败')
  }
}

async function onToggleNullCache(enabled: boolean | string | number) {
  try {
    await switchNullCache(Boolean(enabled))
    ElMessage.success(`防穿透已${enabled ? '开启' : '关闭'}`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '切换失败')
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 16px;
}
.header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.mt {
  margin-top: 12px;
}
.text-muted {
  color: #909399;
  font-size: 13px;
}
</style>
