<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <div class="filters">
            <el-select
              v-model="query.categoryId"
              placeholder="全部分类"
              clearable
              style="width: 140px"
              @change="load"
            >
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>

            <el-select
              v-model="query.status"
              placeholder="全部状态"
              clearable
              style="width: 130px"
              @change="load"
            >
              <el-option label="上架中" :value="1" />
              <el-option label="已下架" :value="0" />
            </el-select>

            <el-button :disabled="loading" @click="load">刷新</el-button>
          </div>

          <el-button type="primary" @click="onCreate">新增菜品</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="dishes" stripe>
        <el-table-column prop="dishName" label="菜品名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="分类" width="100">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="价格" width="100">
          <template #default="{ row }">￥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="库存" width="150">
          <template #default="{ row }">
            <el-input-number
              :model-value="row.stock"
              :min="0"
              :max="9999"
              size="small"
              style="width: 110px"
              @change="(v: number | undefined) => onChangeStock(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="monthlySales" label="月销量" width="90" />
        <el-table-column label="评分" width="80">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="(v: string | number | boolean) => onChangeStatus(row, v)"
            />
            <span class="text-muted switch-label">{{ row.status === 1 ? '上架' : '下架' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onEdit(row)">编辑</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="本店还没有菜品，点右上角「新增菜品」" />
        </template>
      </el-table>

      <p class="text-muted hint">
        提示：库存改成 0 会自动下架；「标签」和「主要食材」两栏直接影响学生端的 AI 点餐、语义搜索和忌口过滤效果，建议每条菜都填。
      </p>
    </el-card>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑菜品' : '新增菜品'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="菜品名称" prop="dishName">
          <el-input v-model="form.dishName" maxlength="30" show-word-limit placeholder="例如：清炒时蔬盖饭" />
        </el-form-item>

        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :max="999" :precision="2" :step="1" />
          <span class="unit">元</span>
        </el-form-item>

        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" :max="9999" />
          <span class="unit">份</span>
        </el-form-item>

        <el-form-item label="菜品简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item label="标签" prop="tags">
          <el-input v-model="form.tags" placeholder="逗号分隔，例如：清淡,低脂,不辣" />
          <div class="tip">AI 语义搜索、个性化推荐按这栏匹配，别写太长，3～5 个词就够。</div>
        </el-form-item>

        <el-form-item label="主要食材" prop="ingredients">
          <el-input v-model="form.ingredients" placeholder="逗号分隔，例如：青菜,米饭,胡萝卜" />
          <div class="tip">过敏原过滤依据：学生端设了忌口/过敏后，AI 会按这栏排除菜品。</div>
        </el-form-item>

        <el-form-item label="图片链接" prop="image">
          <el-input v-model="form.image" placeholder="选填，图片 URL" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 菜品管理。主责：成员2（商户端）。
 *
 * 对应后端接口（都要求商户登录）：
 *   GET  /merchant/dishes              本店菜品列表（返回 List，不分页）
 *   POST /merchant/dishes              新增或修改（带 id 是修改）
 *   PUT  /merchant/dishes/{id}/status  上下架（query 参数 status）
 *   PUT  /merchant/dishes/{id}/stock   调整库存（query 参数 stock，改成 0 自动下架）
 *   GET  /merchant/dish-categories     分类列表（必须传 shopId）
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import {
  listDishCategories,
  listMyDishes,
  saveDish,
  updateDishStatus,
  updateDishStock,
  type Dish,
  type DishCategory
} from '@/api/dish'
import { getMyShop } from '@/api/shop'

const categories = ref<DishCategory[]>([])
const dishes = ref<Dish[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const query = reactive<{ categoryId?: string; status?: number }>({
  categoryId: undefined,
  status: undefined
})

/** 表单只带后端允许改的字段；编辑时不带 status，避免顺手把上下架状态也改掉 */
const form = reactive<Partial<Dish>>({
  id: undefined,
  dishName: '',
  categoryId: undefined,
  price: 10,
  stock: 0,
  description: '',
  tags: '',
  ingredients: '',
  image: ''
})

const rules: FormRules = {
  dishName: [{ required: true, message: '请填写菜品名称', trigger: 'blur' }],
  price: [{ required: true, message: '请填写价格', trigger: 'blur' }]
}

function categoryName(categoryId?: string) {
  if (!categoryId) {
    return '未分类'
  }
  return categories.value.find((c) => c.id === categoryId)?.name || '未分类'
}

/** 加载本店菜品 */
async function load() {
  loading.value = true
  try {
    dishes.value = await listMyDishes({ categoryId: query.categoryId, status: query.status })
  } catch {
    // utils/request 的拦截器已经弹过提示
  } finally {
    loading.value = false
  }
}

/** 先拿店铺 ID（分类接口必须传），再取分类和菜品 */
async function init() {
  const shop = await getMyShop().catch(() => null)
  if (!shop) {
    ElMessage.warning('当前账号还没有绑定店铺')
    return
  }
  categories.value = await listDishCategories(shop.id).catch(() => [])
  await load()
}

/** 上下架开关 */
async function onChangeStatus(row: Dish, value: string | number | boolean) {
  const next = Number(value)
  try {
    await updateDishStatus(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? `「${row.dishName}」已上架` : `「${row.dishName}」已下架`)
  } catch {
    // 失败时开关保持原样（模板绑的是 model-value，不会自己翻过去）
  }
}

/** 表格里直接改库存 */
async function onChangeStock(row: Dish, value: number | undefined) {
  if (value == null || value === row.stock) {
    return
  }
  try {
    await updateDishStock(row.id, value)
    row.stock = value
    if (value === 0) {
      row.status = 0
      ElMessage.warning('库存改成 0，菜品已自动下架')
    } else {
      ElMessage.success('库存已更新')
    }
  } catch {
    // 同上
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    dishName: '',
    categoryId: undefined,
    price: 10,
    stock: 0,
    description: '',
    tags: '',
    ingredients: '',
    image: ''
  })
}

function onCreate() {
  resetForm()
  dialogVisible.value = true
}

function onEdit(row: Dish) {
  Object.assign(form, {
    id: row.id,
    dishName: row.dishName,
    categoryId: row.categoryId,
    price: Number(row.price),
    stock: row.stock ?? 0,
    description: row.description ?? '',
    tags: row.tags ?? '',
    ingredients: row.ingredients ?? '',
    image: row.image ?? ''
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    await saveDish({ ...form })
    ElMessage.success(form.id ? '菜品已更新' : '菜品已新增')
    dialogVisible.value = false
    await load()
  } catch {
    // 同上
  } finally {
    saving.value = false
  }
}

onMounted(init)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.switch-label {
  margin-left: 6px;
}
.unit {
  margin-left: 8px;
  color: #8a8f99;
  font-size: 13px;
}
.tip {
  font-size: 12px;
  line-height: 1.5;
  color: #8a8f99;
}
.hint {
  margin: 12px 0 0;
  line-height: 1.6;
}
</style>
