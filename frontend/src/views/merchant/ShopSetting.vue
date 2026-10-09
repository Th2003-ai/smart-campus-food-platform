<template>
  <div class="page">
    <el-row :gutter="16">
      <!-- 左：店铺资料表单 -->
      <el-col :xs="24" :lg="16">
        <el-card v-loading="loading" shadow="never">
          <template #header>
            <div class="card-head">
              <span>店铺资料</span>
              <span class="text-muted">改完点“保存”，否则不会写到后端</span>
            </div>
          </template>

          <el-alert
            v-if="!loading && !shop"
            class="mb"
            type="warning"
            :closable="false"
            show-icon
            title="当前账号还没有绑定店铺，请联系管理员在后台创建后再来做设置"
          />

          <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="96px">
            <el-form-item label="店铺名称" prop="shopName">
              <el-input v-model="form.shopName" maxlength="30" show-word-limit placeholder="例如：川师大·轻食厨房" />
            </el-form-item>

            <el-form-item label="店铺简介" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                maxlength="200"
                show-word-limit
                placeholder="一句话介绍你的店"
              />
            </el-form-item>

            <el-form-item label="店铺公告" prop="notice">
              <el-input v-model="form.notice" maxlength="100" show-word-limit placeholder="例如：高峰期出餐约 15 分钟" />
            </el-form-item>

            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" maxlength="20" placeholder="顾客联系用的手机号" />
            </el-form-item>

            <el-form-item label="店铺地址" prop="address">
              <el-input v-model="form.address" maxlength="60" placeholder="例如：学生公寓 3 号楼一层" />
            </el-form-item>

            <el-form-item label="营业时间" prop="openTime">
              <el-input v-model="form.openTime" maxlength="30" placeholder="例如：09:00-21:30" />
            </el-form-item>

            <el-form-item label="起送价" prop="minPrice">
              <el-input-number v-model="form.minPrice" :min="0" :max="200" :precision="2" :step="1" />
              <span class="unit">元</span>
            </el-form-item>

            <el-form-item label="配送费" prop="deliveryFee">
              <el-input-number v-model="form.deliveryFee" :min="0" :max="50" :precision="2" :step="0.5" />
              <span class="unit">元</span>
            </el-form-item>

            <el-form-item label="预计送达" prop="deliveryTime">
              <el-input-number v-model="form.deliveryTime" :min="5" :max="120" :step="5" />
              <span class="unit">分钟</span>
            </el-form-item>

            <el-form-item label="Logo 图片" prop="logo">
              <el-input v-model="form.logo" placeholder="图片链接，留空用默认图" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
              <el-button :disabled="loading" @click="load">放弃修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右：营业状态 + 只读信息 -->
      <el-col :xs="24" :lg="8">
        <el-card shadow="never">
          <template #header>营业状态</template>
          <div class="status-box">
            <el-tag :type="isOpen ? 'success' : 'info'" size="large">
              {{ isOpen ? '营业中' : '休息中' }}
            </el-tag>
            <p class="text-muted">
              休息中时学生端仍能搜到你的店，但不能下单；切回营业中后立即可下单。
            </p>
            <el-button
              :type="isOpen ? 'warning' : 'success'"
              :loading="switching"
              :disabled="!shop"
              @click="onToggleStatus"
            >
              {{ isOpen ? '暂停营业' : '开始营业' }}
            </el-button>
          </div>
        </el-card>

        <el-card class="mt" shadow="never">
          <template #header>店铺信息（只读）</template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="店铺 ID">{{ shop?.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="店铺评分">{{ shop?.score ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="月销量">{{ shop?.monthlySales ?? 0 }}</el-descriptions-item>
          </el-descriptions>
          <p class="text-muted mt8">这几个字段由系统统计，不能在这里改。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * 店铺设置。主责：成员2（商户端）。
 *
 * 对应后端三个接口：
 *   GET /merchant/shop          —— 读取我的店铺
 *   PUT /merchant/shop          —— 保存店铺资料
 *   PUT /merchant/shop/status   —— 切换营业状态
 *
 * 注意：后端 ShopService.updateMyShop 只允许改下面这 10 个字段，
 *      商户归属（merchantId）、封禁状态（banStatus）等一概不接受前端传值 —— 这是行级隔离。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { getMyShop, updateBusinessStatus, updateMyShop, type Shop } from '@/api/shop'

interface ShopForm {
  shopName: string
  description: string
  notice: string
  phone: string
  address: string
  openTime: string
  minPrice: number
  deliveryFee: number
  deliveryTime: number
  logo: string
}

const formRef = ref<FormInstance>()
const shop = ref<Shop | null>(null)
const loading = ref(false)
const saving = ref(false)
const switching = ref(false)

const form = reactive<ShopForm>({
  shopName: '',
  description: '',
  notice: '',
  phone: '',
  address: '',
  openTime: '',
  minPrice: 0,
  deliveryFee: 0,
  deliveryTime: 30,
  logo: ''
})

const rules: FormRules = {
  shopName: [{ required: true, message: '请填写店铺名称', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  minPrice: [{ required: true, message: '请填写起送价', trigger: 'blur' }],
  deliveryFee: [{ required: true, message: '请填写配送费', trigger: 'blur' }]
}

/** 1 营业中 / 0 休息中 */
const isOpen = computed(() => shop.value?.businessStatus === 1)

/** 加载我的店铺并填进表单 */
async function load() {
  loading.value = true
  try {
    const data = await getMyShop()
    shop.value = data
    if (!data) {
      return
    }
    form.shopName = data.shopName ?? ''
    form.description = data.description ?? ''
    form.notice = data.notice ?? ''
    form.phone = data.phone ?? ''
    form.address = data.address ?? ''
    form.openTime = data.openTime ?? ''
    form.minPrice = data.minPrice ?? 0
    form.deliveryFee = data.deliveryFee ?? 0
    form.deliveryTime = data.deliveryTime ?? 30
    form.logo = data.logo ?? ''
  } catch {
    // utils/request 的拦截器已经弹过错误提示，这里不重复弹
  } finally {
    loading.value = false
  }
}

/** 保存店铺资料 */
async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    await updateMyShop({ ...form })
    ElMessage.success('店铺资料已保存')
    await load() // 重新拉一次，保证页面显示的和数据库里的一致
  } catch {
    // 同上
  } finally {
    saving.value = false
  }
}

/** 切换营业状态 */
async function onToggleStatus() {
  if (!shop.value) {
    return
  }
  const next = isOpen.value ? 0 : 1
  switching.value = true
  try {
    await updateBusinessStatus(next)
    shop.value.businessStatus = next
    ElMessage.success(next === 1 ? '已开始营业' : '已暂停营业')
  } catch {
    // 同上
  } finally {
    switching.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mb {
  margin-bottom: 12px;
}
.mt {
  margin-top: 16px;
}
.mt8 {
  margin-top: 8px;
}
.unit {
  margin-left: 8px;
  color: #8a8f99;
  font-size: 13px;
}
.status-box {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.status-box p {
  margin: 0;
  line-height: 1.6;
}
</style>
