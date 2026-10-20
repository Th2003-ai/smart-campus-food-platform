<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>评价管理</template>

      <!-- 评分概览 -->
      <el-row :gutter="16">
        <el-col :span="6">
          <el-statistic title="评价总数" :value="num(overview.reviewCount)" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="口味均分" :value="num(overview.avgTasteScore)" :precision="2" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="服务均分" :value="num(overview.avgServiceScore)" :precision="2" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="好评率(%)" :value="num(overview.goodRate)" :precision="1" />
        </el-col>
      </el-row>

      <!-- 标签云 -->
      <div v-if="tags.length" class="block">
        <div class="section-title">AI 标签</div>
        <div class="tag-cloud">
          <el-tag
            v-for="t in tags"
            :key="t.tagName + '_' + t.sentiment"
            size="small"
            :type="tagType(t.sentiment)"
          >
            {{ t.tagName }}（{{ t.tagCount }}）
          </el-tag>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>评价列表</span>
          <el-radio-group v-model="sentiment" size="small" @change="reload">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button :value="2">好评</el-radio-button>
            <el-radio-button :value="1">中评</el-radio-button>
            <el-radio-button :value="0">差评</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-empty v-if="!loading && !reviews.length" description="暂无评价" />

      <div v-else class="list">
        <div v-for="r in reviews" :key="r.id" class="card">
          <div class="card-head">
            <div class="rates">
              <span class="label">口味</span><el-rate :model-value="r.tasteScore" disabled size="small" />
              <span class="label">服务</span><el-rate :model-value="r.serviceScore" disabled size="small" />
              <span class="label">配送</span><el-rate :model-value="r.deliveryScore" disabled size="small" />
            </div>
            <div class="head-right">
              <el-tag v-if="r.sentiment !== undefined" size="small" :type="tagType(r.sentiment)">
                {{ sentimentText(r.sentiment) }}
              </el-tag>
              <el-tag v-if="r.isAnonymous === 1" size="small" type="info">匿名</el-tag>
              <span class="text-muted">{{ r.createTime }}</span>
            </div>
          </div>

          <div class="content">{{ r.content || '（用户未填写文字评价）' }}</div>

          <div v-if="r.aiTags" class="ai-tags">
            <span class="text-muted">AI 标签：</span>
            <el-tag v-for="t in splitCsv(r.aiTags)" :key="t" size="small" type="info">{{ t }}</el-tag>
          </div>

          <div v-if="r.merchantReply" class="reply">
            <span class="text-muted">我的回复：</span>{{ r.merchantReply }}
          </div>

          <div class="card-foot">
            <span v-if="r.riderId" class="text-muted">含配送评价</span>
            <el-button size="small" @click="openReply(r)">
              {{ r.merchantReply ? '修改回复' : '回复' }}
            </el-button>
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
        @current-change="loadReviews"
      />
    </el-card>

    <el-dialog v-model="replyVisible" title="回复评价" width="480px">
      <el-input
        v-model="replyContent"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        placeholder="回复会展示在该评价下方"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="onReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  listMyShopReviews,
  replyReview,
  reviewOverview,
  reviewTags,
  type Review,
  type ReviewTag
} from '@/api/review'
import { splitCsv } from '@/utils/dict'

const loading = ref(false)
const reviews = ref<Review[]>([])
const overview = ref<Record<string, unknown>>({})
const tags = ref<ReviewTag[]>([])
const sentiment = ref<number | undefined>(undefined)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const replyVisible = ref(false)
const replying = ref(false)
const replyContent = ref('')
const replyTarget = ref<Review | null>(null)

function num(v: unknown): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}
function tagType(s?: number) {
  if (s === 2) return 'success'
  if (s === 0) return 'danger'
  return 'warning'
}
function sentimentText(s?: number) {
  if (s === 2) return '好评'
  if (s === 0) return '差评'
  return '中评'
}

async function loadReviews() {
  loading.value = true
  try {
    const res = await listMyShopReviews({
      sentiment: sentiment.value,
      pageNum: page.pageNum,
      pageSize: page.pageSize
    })
    reviews.value = res.list
    page.total = res.total
  } catch {
    // 拦截器提示
  } finally {
    loading.value = false
  }
}

function reload() {
  page.pageNum = 1
  loadReviews()
}

async function loadStats() {
  try {
    const [o, t] = await Promise.all([reviewOverview(), reviewTags(20)])
    overview.value = o
    tags.value = t
  } catch {
    // 概览失败不影响列表
  }
}

function openReply(r: Review) {
  replyTarget.value = r
  replyContent.value = r.merchantReply || ''
  replyVisible.value = true
}

async function onReply() {
  const content = replyContent.value.trim()
  if (!content) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  replying.value = true
  try {
    await replyReview(replyTarget.value!.id, content)
    ElMessage.success('回复成功')
    replyVisible.value = false
    await loadReviews()
  } catch {
    // 拦截器提示
  } finally {
    replying.value = false
  }
}

onMounted(() => {
  loadReviews()
  loadStats()
})
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.block {
  margin-top: 20px;
}
.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}
.tag-cloud {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 60px;
}
.card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.rates {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.label {
  color: #909399;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.content {
  line-height: 1.7;
}
.ai-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.reply {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
}
.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed #ebeef5;
  padding-top: 8px;
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
