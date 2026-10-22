<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>我的收入</span>
          <div class="header-right">
            <el-tag :type="income?.online ? 'success' : 'info'" size="small">
              {{ income?.online ? '在线' : '离线' }}
            </el-tag>
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-statistic title="今日收入（元）" :value="num(income?.today_income)" :precision="2" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="本月收入（元）" :value="num(income?.month_income)" :precision="2" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="累计收入（元）" :value="num(income?.totalIncome)" :precision="2" />
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt">
        <el-col :span="8">
          <el-statistic title="今日单量" :value="num(income?.today_count)" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="本周收入（元）" :value="num(income?.week_income)" :precision="2" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="本月单量" :value="num(income?.month_count)" />
        </el-col>
      </el-row>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mt"
        title="收入来自已完成的配送单；在线状态由 60 秒内的心跳维持"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getIncome, type RiderIncome } from '@/api/rider'

const income = ref<RiderIncome | null>(null)
const loading = ref(false)

/** 后端金额是 BigDecimal，序列化为数字或字符串都可能，这里统一兜底为数字 */
function num(v: number | string | undefined | null): number {
  if (v === null || v === undefined || v === '') return 0
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

async function load() {
  loading.value = true
  try {
    income.value = await getIncome()
  } catch {
    // 拦截器提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mt {
  margin-top: 24px;
}
</style>
