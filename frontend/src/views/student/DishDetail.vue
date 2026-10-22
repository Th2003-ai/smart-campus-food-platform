<template>
  <div class="page" v-loading="loading">
    <el-card v-if="dish" shadow="never">
      <div class="detail">
        <div class="image-box">
          <el-image :src="dishCover(dish)" fit="cover" class="image">
            <template #error><div class="image-placeholder">🍽</div></template>
          </el-image>
        </div>

        <div class="info">
          <h2 class="name">{{ dish.dishName }}</h2>

          <div class="meta">
            <span class="score">★ {{ dish.score ?? '-' }}</span>
            <span class="text-muted">月售 {{ dish.monthlySales ?? 0 }}</span>
            <el-tag v-if="dish.status !== 1" type="info" size="small">已下架</el-tag>
            <el-tag v-else-if="(dish.stock ?? 0) <= 0" type="danger" size="small">售罄</el-tag>
          </div>

          <div class="price-row">
            <span class="price">¥{{ money(dish.price) }}</span>
            <span class="text-muted">库存 {{ dish.stock ?? 0 }}</span>
          </div>

          <div v-if="dish.description" class="block">
            <div class="label">简介</div>
            <div>{{ dish.description }}</div>
          </div>

          <div v-if="tags.length" class="block">
            <div class="label">标签</div>
            <div class="tags">
              <el-tag v-for="t in tags" :key="t" size="small" type="info">{{ t }}</el-tag>
            </div>
          </div>

          <div v-if="ingredients.length" class="block">
            <div class="label">主要食材</div>
            <div class="tags">
              <el-tag v-for="t in ingredients" :key="t" size="small">{{ t }}</el-tag>
            </div>
          </div>

          <el-alert
            v-if="allergyHints.length"
            type="warning"
            :closable="false"
            show-icon
            class="allergy"
            :title="`该菜品含你忌口的食材：${allergyHints.join('、')}`"
          />

          <div class="buy-row">
            <span class="label">数量</span>
            <el-input-number v-model="quantity" :min="1" :max="maxQuantity" :disabled="soldOut" />
            <el-button
              type="primary"
              :disabled="soldOut"
              :loading="adding"
              @click="onAdd"
            >
              {{ soldOut ? '该菜品暂不可购买' : '加入购物车' }}
            </el-button>
            <el-button
              v-if="dish.shopId"
              text
              type="primary"
              @click="router.push(`/student/shop/${dish.shopId}`)"
            >
              返回店铺
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-empty v-else-if="!loading" description="菜品不存在或已下架" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getDishDetail, type Dish } from '@/api/dish'
import { addToCart } from '@/api/cart'
import { getDietProfile } from '@/api/user'
import { useCartStore } from '@/store/cart'
import { money, splitCsv } from '@/utils/dict'
import { dishCover } from '@/utils/image'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const dishId = route.params.dishId as string
const dish = ref<Dish | null>(null)
const loading = ref(false)
const adding = ref(false)
const quantity = ref(1)
/** 用户忌口列表，用于在详情页提前提示（AI 点餐侧也有二次过滤） */
const banned = ref<string[]>([])

const tags = computed(() => splitCsv(dish.value?.tags))
const ingredients = computed(() => splitCsv(dish.value?.ingredients))

const soldOut = computed(() => {
  const d = dish.value
  if (!d) return true
  return d.status !== 1 || (d.stock ?? 0) <= 0
})

const maxQuantity = computed(() => Math.max(1, dish.value?.stock ?? 1))

/** 菜品的食材/标签里命中忌口的部分 */
const allergyHints = computed(() => {
  const d = dish.value
  if (!d || !banned.value.length) return []
  const haystack = [d.ingredients, d.tags, d.description].filter(Boolean).join(',')
  return banned.value.filter((b) => b && haystack.includes(b))
})

async function load() {
  loading.value = true
  try {
    dish.value = await getDishDetail(dishId)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/** 忌口提示属于增强项，失败忽略 */
async function loadBanned() {
  try {
    const profile = await getDietProfile()
    banned.value = [
      ...splitCsv(profile.allergyFoods),
      ...splitCsv(profile.dislikeFoods)
    ]
  } catch {
    banned.value = []
  }
}

async function onAdd() {
  if (!dish.value) return
  adding.value = true
  try {
    await addToCart({ dishId: dish.value.id, quantity: quantity.value })
    ElMessage.success('已加入购物车')
    await cartStore.refresh()
  } catch {
    // 同店约束等业务错误由拦截器提示
  } finally {
    adding.value = false
  }
}

onMounted(() => {
  load()
  loadBanned()
})
</script>

<style scoped>
.page {
  max-width: 980px;
  margin: 0 auto;
}
.detail {
  display: flex;
  gap: 24px;
}
.image-box {
  width: 320px;
  height: 240px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f7fa;
}
.image {
  width: 100%;
  height: 100%;
}
.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56px;
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.name {
  margin: 0;
  font-size: 20px;
}
.meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.score {
  color: #e6a23c;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.price {
  color: #f56c6c;
  font-size: 24px;
  font-weight: 600;
}
.block {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}
.label {
  width: 64px;
  flex-shrink: 0;
  color: #909399;
}
.tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.allergy {
  margin-top: 4px;
}
.buy-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.text-muted {
  color: #909399;
}
</style>
