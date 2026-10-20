<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>个人中心</template>

      <el-tabs v-model="tab">
        <!-- 基本资料 -->
        <el-tab-pane label="基本资料" name="profile">
          <el-descriptions v-if="profile" :column="2" border>
            <el-descriptions-item label="昵称">{{ profile.nickname || '-' }}</el-descriptions-item>
            <el-descriptions-item label="学号">{{ profile.studentNo || profile.username }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ profile.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ profile.role || '-' }}</el-descriptions-item>
            <el-descriptions-item label="学院">{{ profile.college || '-' }}</el-descriptions-item>
            <el-descriptions-item label="年级">{{ profile.grade || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-button class="mt" type="danger" plain @click="onLogout">退出登录</el-button>
        </el-tab-pane>

        <!-- 饮食档案 -->
        <el-tab-pane label="饮食档案" name="diet">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="饮食档案是 AI 点餐与个性化推荐的数据基础：忌口与过敏原会被强制过滤"
          />
          <el-form :model="diet" label-width="110px" class="mt">
            <el-form-item label="饮食目标">
              <el-radio-group v-model="diet.dietGoal">
                <el-radio :value="0">普通</el-radio>
                <el-radio :value="1">减脂</el-radio>
                <el-radio :value="2">增肌</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="口味偏好">
              <el-input v-model="diet.tastePreference" placeholder="逗号分隔，如 清淡,微辣" />
            </el-form-item>
            <el-form-item label="忌口/过敏原">
              <el-input
                v-model="diet.allergyFoods"
                type="textarea"
                :rows="2"
                placeholder="逗号分隔，如 香菜,花生（AI 点餐会自动剔除含这些食材的菜品）"
              />
            </el-form-item>
            <el-form-item label="不喜欢">
              <el-input v-model="diet.dislikeFoods" type="textarea" :rows="2" placeholder="逗号分隔" />
            </el-form-item>
            <el-form-item label="身高(cm)">
              <el-input-number v-model="diet.heightCm" :min="100" :max="250" />
            </el-form-item>
            <el-form-item label="体重(kg)">
              <el-input-number v-model="diet.weightKg" :min="30" :max="200" :precision="1" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="onSaveDiet">保存档案</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 收货地址 -->
        <el-tab-pane label="收货地址" name="address">
          <div class="addr-head">
            <span class="text-muted">下单时需要选择这里维护的地址</span>
            <el-button type="primary" size="small" @click="openAddress()">新增地址</el-button>
          </div>

          <el-empty v-if="!addresses.length" description="还没有收货地址" />
          <div v-else class="addr-list">
            <div v-for="a in addresses" :key="a.id" class="addr-card">
              <div class="addr-main">
                <div class="addr-line">
                  <b>{{ a.receiver }}</b>
                  <span class="text-muted">{{ a.phone }}</span>
                  <el-tag v-if="a.isDefault" size="small" type="success">默认</el-tag>
                </div>
                <div class="text-muted">{{ joinAddress(a) }}</div>
              </div>
              <div class="addr-actions">
                <el-button text type="primary" @click="openAddress(a)">编辑</el-button>
                <el-button text type="danger" @click="onRemoveAddress(a)">删除</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 我的工单 -->
        <el-tab-pane label="我的工单" name="ticket">
          <el-empty v-if="!tickets.length" description="暂无客服工单" />
          <div v-else class="ticket-list">
            <div v-for="t in tickets" :key="t.id" class="ticket-card">
              <div class="ticket-head">
                <span class="ticket-no">{{ t.ticketNo }}</span>
                <el-tag size="small" :type="ticketTagType(t.status)">{{ ticketStatusText(t.status) }}</el-tag>
                <span class="text-muted">{{ t.createTime }}</span>
              </div>
              <div class="ticket-q">问题：{{ t.question }}</div>
              <div v-if="t.reply" class="ticket-a">客服回复：{{ t.reply }}</div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 地址弹窗 -->
    <el-dialog v-model="addrVisible" :title="addrForm.id ? '编辑地址' : '新增地址'" width="480px">
      <el-form ref="addrFormRef" :model="addrForm" :rules="addrRules" label-width="90px">
        <el-form-item label="收货人" prop="receiver">
          <el-input v-model="addrForm.receiver" maxlength="50" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addrForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="校区" prop="campusArea">
          <el-input v-model="addrForm.campusArea" placeholder="如 主校区" />
        </el-form-item>
        <el-form-item label="楼栋" prop="building">
          <el-input v-model="addrForm.building" placeholder="如 8号宿舍楼" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input v-model="addrForm.detail" placeholder="如 302室" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="addrForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addrVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveAddress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import { listMyTickets, type Ticket } from '@/api/admin'
import {
  addAddress,
  getDietProfile,
  getProfile,
  listAddress,
  removeAddress,
  saveDietProfile,
  type DietProfile,
  type UserAddress,
  type UserProfile
} from '@/api/user'
import { useUserStore } from '@/store/user'
import { joinAddress } from '@/utils/dict'

const router = useRouter()
const userStore = useUserStore()

const tab = ref('profile')
const loading = ref(false)
const saving = ref(false)

const profile = ref<UserProfile | null>(null)
const addresses = ref<UserAddress[]>([])
const tickets = ref<Ticket[]>([])

const diet = reactive<DietProfile>({
  dietGoal: 0,
  tastePreference: '',
  allergyFoods: '',
  dislikeFoods: '',
  heightCm: undefined,
  weightKg: undefined
})

const addrVisible = ref(false)
const addrFormRef = ref<FormInstance>()
const addrForm = reactive<UserAddress>({
  id: undefined,
  receiver: '',
  phone: '',
  campusArea: '',
  building: '',
  detail: '',
  isDefault: false
})

const addrRules: FormRules = {
  receiver: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

function ticketStatusText(status?: number) {
  if (status === 1) return '处理中'
  if (status === 2) return '已关闭'
  return '待处理'
}
function ticketTagType(status?: number) {
  if (status === 2) return 'success'
  if (status === 1) return 'warning'
  return 'danger'
}

async function loadAll() {
  loading.value = true
  try {
    const [p, d, a, t] = await Promise.all([
      getProfile(),
      getDietProfile(),
      listAddress(),
      listMyTickets()
    ])
    profile.value = p
    Object.assign(diet, d)
    addresses.value = a
    tickets.value = t
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onSaveDiet() {
  saving.value = true
  try {
    await saveDietProfile({ ...diet })
    ElMessage.success('饮食档案已保存，AI 推荐会立即生效')
  } catch {
    // 拦截器提示
  } finally {
    saving.value = false
  }
}

function openAddress(a?: UserAddress) {
  if (a) {
    Object.assign(addrForm, a)
  } else {
    Object.assign(addrForm, {
      id: undefined,
      receiver: '',
      phone: '',
      campusArea: '',
      building: '',
      detail: '',
      isDefault: false
    })
  }
  addrFormRef.value?.clearValidate()
  addrVisible.value = true
}

async function onSaveAddress() {
  const valid = await addrFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await addAddress({
      id: addrForm.id,
      receiver: addrForm.receiver.trim(),
      phone: addrForm.phone.trim(),
      campusArea: addrForm.campusArea?.trim() || undefined,
      building: addrForm.building?.trim() || undefined,
      detail: addrForm.detail.trim(),
      isDefault: addrForm.isDefault
    })
    ElMessage.success('地址已保存')
    addrVisible.value = false
    addresses.value = await listAddress()
  } catch {
    // 拦截器提示
  } finally {
    saving.value = false
  }
}

async function onRemoveAddress(a: UserAddress) {
  try {
    await ElMessageBox.confirm(`确定删除「${a.receiver}」的地址？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await removeAddress(a.id as string)
    ElMessage.success('已删除')
    addresses.value = await listAddress()
  } catch {
    // 拦截器提示
  }
}

async function onLogout() {
  await userStore.logout()
  router.push('/login')
}

onMounted(loadAll)
</script>

<style scoped>
.mt {
  margin-top: 16px;
}
.addr-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.addr-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.addr-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
}
.addr-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ticket-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ticket-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}
.ticket-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ticket-no {
  font-weight: 600;
}
.ticket-a {
  color: #67c23a;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
