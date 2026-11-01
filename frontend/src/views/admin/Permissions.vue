<template>
  <div class="page perm-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">角色权限矩阵（四个主体）</span>
          <el-button type="primary" size="small" :loading="saving" @click="saveRole">保存该角色授权</el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" class="tip"
        title="左侧切换主体，勾选该主体拥有的权限码，并设定数据范围（ALL 全部 / SHOP 本店 / SELF 本人）。保存后立即写回 sys_role_permission。" />

      <el-radio-group v-model="currentRole" class="role-switch" @change="loadRoleRows">
        <el-radio-button v-for="r in roles" :key="r" :label="r">{{ roleText(r) }}</el-radio-button>
      </el-radio-group>

      <el-table :data="permRows" border size="small" stripe v-loading="loading">
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="permission" label="权限码" min-width="170">
          <template #default="{ row }"><code class="code">{{ row.permission }}</code></template>
        </el-table-column>
        <el-table-column prop="desc" label="含义" min-width="200" />
        <el-table-column label="授权" width="90" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.checked" :disabled="currentRole === 'ADMIN'" />
          </template>
        </el-table-column>
        <el-table-column label="数据范围" width="150">
          <template #default="{ row }">
            <el-select v-model="row.dataScope" size="small" :disabled="!row.checked">
              <el-option v-for="s in scopes" :key="s" :label="s" :value="s" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="user-card">
      <template #header>
        <span class="title">成员角色分配（sys_user.role）</span>
      </template>
      <el-table :data="users" border size="small" stripe v-loading="userLoading">
        <el-table-column prop="username" label="账号" width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="主体（角色）" width="160">
          <template #default="{ row }">
            <el-select v-model="row.role" size="small" @change="(v: string) => changeRole(row, v)">
              <el-option v-for="r in roles" :key="r" :label="roleText(r)" :value="r" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最近登录" min-width="170" />
      </el-table>
      <div class="hint">改完立即生效：该成员下次请求就会按新主体校验接口权限。</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  permMatrix,
  permUsers,
  saveRolePerms,
  updateUserRole,
  type PermMatrix,
  type RolePermItem,
  type SysUserRow
} from '@/api/permission'

/** 权限码 → 中文说明（未列出的按权限码原样展示） */
const PERM_DESC: Record<string, string> = {
  'shop:read': '查看店铺与菜品',
  'order:write': '下单、取消订单',
  'order:read': '查看订单',
  'review:write': '提交评价',
  'ai:use': '使用 AI 点餐/客服',
  'dish:write': '菜品增删改、上下架',
  'dish:read': '查看本店菜品',
  'order:transit': '接单、出餐',
  'review:reply': '回复评价',
  'ai:business': 'AI 经营助手',
  'rider:hall': '查看抢单大厅',
  'rider:grab': '抢单',
  'delivery:transit': '取餐、送达',
  'admin:all': '管理端全部权限'
}

const ROLE_TEXT: Record<string, string> = {
  STUDENT: '学生',
  MERCHANT: '商户',
  RIDER: '骑手',
  ADMIN: '管理员'
}

interface PermRow {
  permission: string
  module: string
  desc: string
  checked: boolean
  dataScope: string
}

const matrix = ref<PermMatrix>({ roles: [], scopes: [], perms: [], granted: {} })
const roles = ref<string[]>([])
const scopes = ref<string[]>([])
const currentRole = ref('STUDENT')
const permRows = ref<PermRow[]>([])
const users = ref<SysUserRow[]>([])
const loading = ref(false)
const userLoading = ref(false)
const saving = ref(false)

function roleText(role: string) {
  return ROLE_TEXT[role] ?? role
}

/** 用矩阵数据重建当前角色的可编辑行 */
function loadRoleRows() {
  const granted = matrix.value.granted[currentRole.value] ?? {}
  permRows.value = matrix.value.perms.map((perm) => ({
    permission: perm,
    module: perm.split(':')[0],
    desc: PERM_DESC[perm] ?? '',
    checked: perm in granted,
    dataScope: granted[perm] ?? 'SELF'
  }))
}

async function loadMatrix() {
  loading.value = true
  try {
    const data = await permMatrix()
    matrix.value = data
    roles.value = data.roles
    scopes.value = data.scopes
    loadRoleRows()
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  userLoading.value = true
  try {
    users.value = await permUsers()
  } finally {
    userLoading.value = false
  }
}

async function saveRole() {
  const items: RolePermItem[] = permRows.value
    .filter((r) => r.checked)
    .map((r) => ({ permission: r.permission, dataScope: r.dataScope }))
  saving.value = true
  try {
    await saveRolePerms(currentRole.value, items)
    ElMessage.success(`${roleText(currentRole.value)} 授权已保存（${items.length} 项）`)
    await loadMatrix()
  } finally {
    saving.value = false
  }
}

async function changeRole(row: SysUserRow, role: string) {
  await updateUserRole(row.id, role)
  ElMessage.success(`${row.username} 已设为 ${roleText(role)}`)
  await loadUsers()
}

onMounted(async () => {
  await Promise.all([loadMatrix(), loadUsers()])
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.title {
  font-weight: 600;
}
.tip {
  margin-bottom: 12px;
}
.role-switch {
  margin-bottom: 14px;
}
.code {
  color: #e6a23c;
  background: #fdf6ec;
  border-radius: 4px;
  padding: 0 6px;
  font-size: 12px;
}
.user-card {
  margin-top: 16px;
}
.hint {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
