<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>饮食分析</span>
          <el-radio-group v-model="days" size="small" @change="load">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-empty v-if="noData" :description="message || '暂无可分析的订单数据'">
        <div class="text-muted">{{ suggestion }}</div>
      </el-empty>

      <template v-else-if="report">
        <!-- 指标 -->
        <div class="cards">
          <div class="stat-card">
            <div class="stat-value">{{ report.orderCount ?? 0 }}</div>
            <div class="stat-label">已完成订单</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ report.dishCount ?? 0 }}</div>
            <div class="stat-label">涉及菜品</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ report.periodDays ?? days }}</div>
            <div class="stat-label">统计天数</div>
          </div>
        </div>

        <!-- 过敏原风险 -->
        <el-alert
          v-if="allergyRisk.length"
          type="error"
          :closable="false"
          show-icon
          class="block"
          :title="`过敏/忌口风险：近 ${report.periodDays} 天你点过含「${allergyRisk.join('、')}」的菜品`"
        />
        <el-alert
          v-else
          type="success"
          :closable="false"
          show-icon
          class="block"
          title="未发现与饮食档案忌口冲突的菜品"
        />

        <!-- 饮食结构 -->
        <div v-if="structureEntries.length" class="block">
          <div class="section-title">饮食结构</div>
          <div class="structure">
            <div v-for="[k, v] in structureEntries" :key="k" class="structure-item">
              <div class="structure-head">
                <span>{{ k }}</span>
                <span class="text-muted">{{ v }} 次</span>
              </div>
              <el-progress :percentage="pct(v)" :show-text="false" :stroke-width="10" />
            </div>
          </div>
        </div>

        <!-- 总结与建议 -->
        <div v-if="report.summary" class="block">
          <div class="section-title">分析结论</div>
          <div class="summary">{{ report.summary }}</div>
        </div>

        <div v-if="suggestions.length" class="block">
          <div class="section-title">健康建议</div>
          <ul class="suggestions">
            <li v-for="(s, i) in suggestions" :key="i">{{ s }}</li>
          </ul>
        </div>

        <div v-if="report.modelVersion" class="footer text-muted">
          模型版本：{{ report.modelVersion }}
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { dietReport } from '@/api/ai'

const days = ref(7)
const loading = ref(false)
const report = ref<Record<string, unknown> | null>(null)

const noData = computed(() => !!report.value?.message && !report.value?.summary)

const message = computed(() => (report.value?.message as string) || '')
const suggestion = computed(() => (report.value?.suggestion as string) || '')
const suggestions = computed(() => ((report.value?.suggestions as string[]) || []).filter(Boolean))
const allergyRisk = computed(() => ((report.value?.allergyRisk as string[]) || []).filter(Boolean))

/** structure 是后端返回的 Map，key 为分类、value 为次数；不写死 key 以免后端调整后失效 */
const structureEntries = computed<Array<[string, number]>>(() => {
  const s = report.value?.structure as Record<string, number> | undefined
  if (!s) return []
  return Object.entries(s).sort((a, b) => Number(b[1]) - Number(a[1]))
})

const maxCount = computed(() =>
  structureEntries.value.reduce((m, [, v]) => Math.max(m, Number(v)), 0) || 1
)

function pct(v: number | string) {
  return Math.round((Number(v) / maxCount.value) * 100)
}

async function load() {
  loading.value = true
  try {
    report.value = await dietReport(days.value)
  } catch {
    report.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 900px;
  margin: 0 auto;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.cards {
  display: flex;
  gap: 14px;
}
.stat-card {
  flex: 1;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--campus-primary);
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.block {
  margin-top: 18px;
}
.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}
.structure {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.structure-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 4px;
}
.summary {
  line-height: 1.8;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 14px;
  white-space: pre-wrap;
}
.suggestions {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
}
.footer {
  margin-top: 18px;
  font-size: 12px;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
