<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>搜索</template>
      <div class="search-row">
        <el-input
          v-model="keyword"
          placeholder="输入菜品或店铺名称，回车搜索"
          clearable
          @keyup.enter="onSearch"
        >
          <template #append>
            <el-button @click="onSearch">搜索</el-button>
          </template>
        </el-input>
        <el-button type="primary" plain :loading="semanticLoading" @click="onSemantic">
          AI 语义搜索
        </el-button>
      </div>
      <div class="hint text-muted">
        普通搜索按名称/标签/食材匹配；AI 语义搜索能理解「想吃点暖和的、不辣的」这类描述（大模型不可用时自动降级为关键词检索）
      </div>
    </el-card>

    <!-- 语义搜索说明 -->
    <el-card v-if="semanticReply" shadow="never">
      <template #header>
        <div class="header">
          <span>AI 理解结果</span>
          <el-tag v-if="semanticFallback" type="warning" size="small">已降级为关键词检索</el-tag>
        </div>
      </template>
      <div class="reply">{{ semanticReply }}</div>
      <div v-if="conditions.length" class="conditions">
        <el-tag v-for="c in conditions" :key="c" size="small" type="info">{{ c }}</el-tag>
      </div>
    </el-card>

    <!-- 结果 -->
    <el-card shadow="never">
      <template #header>
        <el-tabs v-model="tab">
          <el-tab-pane label="菜品" name="dish" />
          <el-tab-pane label="店铺" name="shop" />
        </el-tabs>
      </template>

      <template v-if="tab === 'dish'">
        <el-empty v-if="!loading && !dishes.length" description="没有找到匹配的菜品" />
        <div v-else v-loading="loading" class="dish-list">
          <div
            v-for="d in dishes"
            :key="d.id"
            class="dish-row"
            @click="router.push(`/student/dish/${d.id}`)"
          >
            <div class="thumb">
              <el-image :src="dishCover(d)" fit="cover" class="thumb-img">
                <template #error><div class="thumb-placeholder">🍽</div></template>
              </el-image>
            </div>
            <div class="main">
              <div class="name">{{ d.dishName }}</div>
              <div class="tags">
                <el-tag v-for="t in splitCsv(d.tags)" :key="t" size="small" type="info">{{ t }}</el-tag>
              </div>
            </div>
            <div class="right">
              <span class="price">¥{{ money(d.price) }}</span>
              <span class="text-muted">月售 {{ d.monthlySales ?? 0 }}</span>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <el-empty v-if="!loading && !shops.length" description="没有找到匹配的店铺" />
        <div v-else v-loading="loading" class="shop-list">
          <div
            v-for="s in shops"
            :key="s.id"
            class="shop-row"
            @click="router.push(`/student/shop/${s.id}`)"
          >
            <span class="name">{{ s.shopName }}</span>
            <el-tag v-if="s.businessStatus !== 1" size="small" type="info">休息中</el-tag>
            <span class="text-muted">★ {{ s.score ?? '-' }} · 月售 {{ s.monthlySales ?? 0 }}</span>
            <span class="text-muted">起送 ¥{{ money(s.minPrice) }}</span>
          </div>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { semanticSearch } from '@/api/ai'
import { searchDishes, type Dish } from '@/api/dish'
import { listShops, type Shop } from '@/api/shop'
import { money, splitCsv } from '@/utils/dict'
import { dishCover } from '@/utils/image'

const route = useRoute()
const router = useRouter()

const keyword = ref((route.query.keyword as string) || '')
const tab = ref<'dish' | 'shop'>('dish')
const loading = ref(false)
const semanticLoading = ref(false)

const dishes = ref<Dish[]>([])
const shops = ref<Shop[]>([])

const semanticReply = ref('')
const semanticFallback = ref(false)
const conditions = ref<string[]>([])

async function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) return
  loading.value = true
  semanticReply.value = ''
  try {
    const [d, s] = await Promise.all([
      searchDishes({ keyword: kw, limit: 30 }),
      listShops({ keyword: kw, pageNum: 1, pageSize: 20 })
    ])
    dishes.value = d
    shops.value = s.list
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/** AI 语义搜索：返回 reply + dishes，fallback=true 表示已降级 */
async function onSemantic() {
  const msg = keyword.value.trim()
  if (!msg) return
  semanticLoading.value = true
  try {
    const res = await semanticSearch({ message: msg, limit: 12 })
    semanticReply.value = res.reply || ''
    semanticFallback.value = !!res.fallback
    conditions.value = Object.entries(res.conditions || {}).map(([k, v]) => `${k}：${v}`)
    dishes.value = (res.dishes as Dish[]) || []
    tab.value = 'dish'
  } catch {
    // 拦截器已提示
  } finally {
    semanticLoading.value = false
  }
}

onMounted(() => {
  if (keyword.value) onSearch()
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
  gap: 10px;
}
.hint {
  margin-top: 8px;
  font-size: 12px;
}
.header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.reply {
  margin-bottom: 8px;
  line-height: 1.6;
}
.conditions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.dish-list,
.shop-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 60px;
}
.dish-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
}
.dish-row:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
}
.thumb {
  width: 56px;
  height: 56px;
  border-radius: 6px;
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
  font-size: 22px;
}
.main {
  flex: 1;
  min-width: 0;
}
.name {
  font-weight: 600;
}
.tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 4px;
}
.right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.price {
  color: #f56c6c;
  font-weight: 600;
}
.shop-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
}
.shop-row .name {
  flex: 1;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
