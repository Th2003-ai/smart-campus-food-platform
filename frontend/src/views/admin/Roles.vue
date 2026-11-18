<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <div>
            <h3>角色管理</h3>
            <p class="desc">
              角色是数据（<code>sys_role</code>），新增/改名/停用都不需要改代码；停用的角色无法登录。
              新增角色后到「角色权限」页给它勾权限码，再在用户管理里分配给成员。
            </p>
          </div>
          <el-button type="primary" @click="openCreate">新增角色</el-button>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" size="default">
        <el-table-column prop="code" label="角色编码" width="150">
          <template #default="{ row }">
            <code>{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="角色名称" width="120" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="内置" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.builtin" size="small" type="warning">内置</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可注册" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.allowRegister === 1 ? 'primary' : 'info'">
              {{ row.allowRegister === 1 ? '允许' : '禁止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属端" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.portal" size="small">{{ portalText(row.portal) }}</el-tag>
            <el-tag v-else size="small" type="danger">未配置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="homePath" label="首页" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <code v-if="row.homePath">{{ row.homePath }}</code>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="用户数" width="80" />
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-tooltip
              :disabled="!row.builtin && row.userCount === 0"
              :content="row.builtin ? '内置角色不能删除' : '仍有用户使用该角色，不能删除'"
            >
              <span>
                <el-button
                  link
                  type="danger"
                  size="small"
                  :disabled="row.builtin || row.userCount > 0"
                  @click="onDelete(row)"
                >
                  删除
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新增角色'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="editing"
            placeholder="大写字母开头，如 REVIEWER"
            maxlength="32"
          />
          <div class="tip">创建后不可修改；会写进 sys_user.role 与 sys_role_permission.role</div>
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="如：审核员" maxlength="50" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          <span class="tip">停用后该角色的用户无法登录</span>
        </el-form-item>
        <el-form-item label="允许注册">
          <el-switch v-model="form.allowRegister" :active-value="1" :inactive-value="0" />
          <span class="tip">打开后注册页会出现该角色</span>
        </el-form-item>
        <el-form-item label="所属端" prop="portal">
          <el-select v-model="form.portal" placeholder="不挂靠任何端" clearable style="width: 200px" @change="onPortalChange">
            <el-option label="学生端" value="STUDENT" />
            <el-option label="商户端" value="MERCHANT" />
            <el-option label="骑手端" value="RIDER" />
            <el-option label="管理端" value="ADMIN" />
          </el-select>
          <div class="tip">决定这个角色能进哪套页面/接口；不选则登录后只看到「暂无工作台」</div>
        </el-form-item>
        <el-form-item label="首页路径">
          <el-input v-model="form.homePath" placeholder="/admin/dashboard" maxlength="200" />
          <div class="tip">登录后跳转的路由；留空则用该端的默认首页</div>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import { createRole, deleteRole, listRoles, updateRole, type RoleItem } from '@/api/role'

const rows = ref<RoleItem[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  code: '',
  name: '',
  description: '',
  sort: 0,
  status: 1,
  allowRegister: 0,
  portal: '' as string,
  homePath: ''
})

/** 端 → 默认首页，切换端时自动带出，省得手填 */
const PORTAL_HOME: Record<string, string> = {
  STUDENT: '/student/home',
  MERCHANT: '/merchant/dashboard',
  RIDER: '/rider/hall',
  ADMIN: '/admin/dashboard'
}

const PORTAL_TEXT: Record<string, string> = {
  STUDENT: '学生端',
  MERCHANT: '商户端',
  RIDER: '骑手端',
  ADMIN: '管理端'
}

function portalText(portal?: string | null) {
  return portal ? PORTAL_TEXT[portal] ?? portal : '-'
}

function onPortalChange(value: string) {
  if (value && !form.homePath) {
    form.homePath = PORTAL_HOME[value] ?? ''
  }
}

const rules: FormRules = {
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: /^[A-Z][A-Z0-9_]{1,31}$/,
      message: '大写字母开头，只能包含大写字母/数字/下划线，长度 2~32',
      trigger: 'blur'
    }
  ],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    rows.value = await listRoles()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.code = ''
  form.name = ''
  form.description = ''
  form.sort = 0
  form.status = 1
  form.allowRegister = 0
  form.portal = ''
  form.homePath = ''
  formRef.value?.clearValidate()
}

function openCreate() {
  editing.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: RoleItem) {
  editing.value = true
  form.code = row.code
  form.name = row.name
  form.description = row.description ?? ''
  form.sort = row.sort ?? 0
  form.status = row.status
  form.allowRegister = row.allowRegister
  form.portal = row.portal ?? ''
  form.homePath = row.homePath ?? ''
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      sort: form.sort,
      status: form.status,
      allowRegister: form.allowRegister,
      // 传空串表示清空（后端按 null 存）
      portal: form.portal || '',
      homePath: form.homePath.trim() || ''
    }
    if (editing.value) {
      await updateRole(form.code, payload)
      ElMessage.success('角色已更新')
    } else {
      await createRole({ ...payload, code: form.code.trim().toUpperCase() })
      ElMessage.success('角色已创建，请到「角色权限」页分配权限')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onDelete(row: RoleItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${row.name}（${row.code}）」？该角色的授权会被一并清除。`,
      '删除角色',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteRole(row.code)
    ElMessage.success('角色已删除')
    await load()
  } catch {
    // 拦截器已提示（内置角色 / 仍有用户）
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  padding: 4px;
}
.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.header h3 {
  margin: 0 0 4px;
  font-size: 16px;
}
.desc {
  margin: 0;
  max-width: 820px;
  font-size: 12px;
  line-height: 1.7;
  color: #909399;
}
.muted {
  color: #c0c4cc;
}
.tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
