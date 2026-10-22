<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>AI 经营助手</span>
          <div class="header-right">
            <el-button size="small" :loading="loading" @click="loadCurrent">重新生成</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 经营简报 -->
        <el-tab-pane label="经营简报" name="business">
          <template v-if="business">
            <el-alert
              v-if="business.message"
              type="warning"
              :closable="false"
              show-icon
              class="mb"
              :title="String(business.message)"
            />

            <el-row :gutter="16">
              <el-col :span="8">
                <el-statistic title="菜品数" :value="Number(business.dishCount ?? 0)" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="热销菜品" :value="hotDishes.length" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="滞销菜品" :value="slowDishes.length" />
              </el-col>
            </el-row>

            <div v-if="business.summary" class="block">
              <div class="section-title">经营结论</div>
              <div class="summary">{{ business.summary }}</div>
            </div>

            <div class="block two-col">
              <div>
                <div class="section-title">热销菜品</div>
                <div v-if="hotDishes.length" class="chips">
                  <el-tag v-for="d in hotDishes" :key="d" type="success">{{ d }}</el-tag>
                </div>
                <div v-else class="text-muted">暂无数据</div>
              </div>
              <div>
                <div class="section-title">滞销菜品</div>
                <div v-if="slowDishes.length" class="chips">
                  <el-tag v-for="d in slowDishes" :key="d" type="warning">{{ d }}</el-tag>
                </div>
                <div v-else class="text-muted">暂无数据</div>
              </div>
            </div>

            <div v-if="businessSuggestions.length" class="block">
              <div class="section-title">经营建议</div>
              <ul class="suggestions">
                <li v-for="(x, i) in businessSuggestions" :key="i">{{ x }}</li>
              </ul>
            </div>

            <div v-if="business.modelVersion" class="footer text-muted">
              模型版本：{{ business.modelVersion }}
            </div>
          </template>
          <el-empty v-else description="点击「重新生成」获取经营简报" />
        </el-tab-pane>

        <!-- 评价分析报告 -->
        <el-tab-pane label="评价分析" name="review">
          <template v-if="review">
            <el-alert
              v-if="review.message"
              type="warning"
              :closable="false"
              show-icon
              class="mb"
              :title="String(review.message)"
            />
            <el-alert
              v-if="review.suggestion"
              type="info"
              :closable="false"
              show-icon
              class="mb"
              :title="String(review.suggestion)"
            />

            <el-row v-if="reviewOverview" :gutter="16">
              <el-col :span="6">
                <el-statistic title="评价数" :value="Number(reviewOverview.reviewCount ?? 0)" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="口味均分" :value="Number(reviewOverview.avgTasteScore ?? 0)" :precision="2" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="服务均分" :value="Number(reviewOverview.avgServiceScore ?? 0)" :precision="2" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="好评率(%)" :value="Number(reviewOverview.goodRate ?? 0)" :precision="1" />
              </el-col>
            </el-row>

            <div v-if="review.summary" class="block">
              <div class="section-title">口碑结论</div>
              <div class="summary">{{ review.summary }}</div>
            </div>

            <div class="block two-col">
              <div>
                <div class="section-title">好评点</div>
                <div v-if="highlights.length" class="chips">
                  <el-tag v-for="h in highlights" :key="h" type="success">{{ h }}</el-tag>
                </div>
                <div v-else class="text-muted">暂无</div>
              </div>
              <div>
                <div class="section-title">吐槽点</div>
                <div v-if="complaints.length" class="chips">
                  <el-tag v-for="c in complaints" :key="c" type="danger">{{ c }}</el-tag>
                </div>
                <div v-else class="text-muted">暂无</div>
              </div>
            </div>

            <div v-if="reviewSuggestions.length" class="block">
              <div class="section-title">改进建议</div>
              <ul class="suggestions">
                <li v-for="(x, i) in reviewSuggestions" :key="i">{{ x }}</li>
              </ul>
            </div>

            <div v-if="review.modelVersion" class="footer text-muted">
              模型版本：{{ review.modelVersion }}
            </div>
          </template>
          <el-empty v-else description="点击「重新生成」获取评价分析" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { businessReport, reviewReport } from '@/api/ai'

const tab = ref('business')
const loading = ref(false)

const business = ref<Record<string, unknown> | null>(null)
const review = ref<Record<string, unknown> | null>(null)

const hotDishes = computed(() => ((business.value?.hotDishes as string[]) || []).filter(Boolean))
const slowDishes = computed(() => ((business.value?.slowDishes as string[]) || []).filter(Boolean))
const businessSuggestions = computed(
  () => ((business.value?.suggestions as string[]) || []).filter(Boolean)
)

const reviewOverview = computed(
  () => (review.value?.overview as Record<string, unknown>) || null
)
const highlights = computed(() => ((review.value?.highlights as string[]) || []).filter(Boolean))
const complaints = computed(() => ((review.value?.complaints as string[]) || []).filter(Boolean))
const reviewSuggestions = computed(
  () => ((review.value?.suggestions as string[]) || []).filter(Boolean)
)

async function loadBusiness() {
  loading.value = true
  try {
    business.value = await businessReport()
  } catch {
    business.value = null
  } finally {
    loading.value = false
  }
}

async function loadReview() {
  loading.value = true
  try {
    review.value = await reviewReport()
  } catch {
    review.value = null
  } finally {
    loading.value = false
  }
}

function loadCurrent() {
  return tab.value === 'business' ? loadBusiness() : loadReview()
}

/** 切到某个 tab 且还没有数据时才请求，避免重复调用大模型 */
function onTabChange() {
  if (tab.value === 'business' && !business.value) loadBusiness()
  if (tab.value === 'review' && !review.value) loadReview()
}

onMounted(loadBusiness)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right {
  display: flex;
  gap: 8px;
}
.mb {
  margin-bottom: 16px;
}
.block {
  margin-top: 20px;
}
.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}
.summary {
  line-height: 1.8;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 14px;
  white-space: pre-wrap;
}
.two-col {
  display: flex;
  gap: 32px;
}
.two-col > div {
  flex: 1;
}
.chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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
