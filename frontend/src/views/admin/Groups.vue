<template>
  <div class="groups-page" v-loading="loading">
    <el-row :gutter="16">
      <!-- 左：用户组 -->
      <el-col :span="6">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-head">
              <span>用户组</span>
              <el-button size="small" type="primary" plain @click="onCreate">新建</el-button>
            </div>
          </template>
          <el-menu :default-active="activeGroupId" @select="onSelect">
            <el-menu-item v-for="g in matrix.groups" :key="g.id" :index="g.id">
              <span>{{ g.name }}</span>
              <el-tag v-if="g.builtin" size="small" type="info" class="ml">内置</el-tag>
              <span class="count">{{ (matrix.granted[g.id] || []).length }}</span>
            </el-menu-item>
          </el-menu>
          <div class="hint">
            一人可属多个组，权限取并集；ADMIN 绕过全部检查。
          </div>
        </el-card>
      </el-col>

      <!-- 右：权限 / 成员 -->
      <el-col :span="18">
        <el-card shadow="never" class="panel">
          <el-tabs v-model="tab">
            <el-tab-pane label="接口权限" name="priv">
              <div class="toolbar">
                <span class="cur">{{ activeGroup ? activeGroup.name : '未选择' }}</span>
                <span class="tip">共 {{ endpointTree.total }} 个接口，已勾 {{ checkedKeys.length }} 个</span>
                <div class="spacer" />
                <el-input v-model="keyword" placeholder="搜索模块/方法" size="small" clearable style="width: 200px" />
                <el-button size="small" @click="toggleAll(true)">全选</el-button>
                <el-button size="small" @click="toggleAll(false)">全不选</el-button>
                <el-button size="small" type="primary" :loading="saving" @click="onSave">保存</el-button>
              </div>

              <el-collapse v-model="openModules">
                <el-collapse-item v-for="mod in filteredModules" :key="mod.module" :name="mod.module">
                  <template #title>
                    <div class="mod-title">
                      <el-checkbox
                        :model-value="moduleState(mod)"
                        :indeterminate="moduleIndeterminate(mod)"
                        @change="(v: boolean) => toggleModule(mod, v)"
                        @click.stop
                      />
                      <strong>{{ mod.module }}</strong>
                      <span class="tip">{{ mod.methods.length }} 个接口</span>
                    </div>
                  </template>
                  <div class="methods">
                    <div v-for="m in mod.methods" :key="m.key" class="method-row">
                      <el-checkbox
                        :model-value="checkedKeys.includes(m.key)"
                        @change="(v: boolean) => toggleKey(m.key, v)"
                      >
                        <code>{{ m.method }}</code>
                      </el-checkbox>
                      <span class="path">{{ (m.paths || []).join(' / ') }}</span>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </el-tab-pane>

            <el-tab-pane label="组员" name="user">
              <el-table :data="users" size="small" border>
                <el-table-column prop="username" label="账号" width="140" />
                <el-table-column prop="nickname" label="昵称" width="140" />
                <el-table-column prop="role" label="角色" width="110" />
                <el-table-column label="所属用户组">
                  <template #default="{ row }">
                    <el-select
                      :model-value="row.groupIds"
                      multiple
                      size="small"
                      style="width: 100%"
                      @change="(v: string[]) => onSetGroups(row.id, v)"
                    >
                      <el-option v-for="g in matrix.groups" :key="g.id" :label="g.name" :value="g.id" />
                    </el-select>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createGroup,
  groupEndpoints,
  groupMatrix,
  groupUsers,
  saveGroupPrivs,
  setUserGroups,
  type EndpointTree,
  type GroupMatrix,
  type GroupRow,
  type GroupUserRow
} from '@/api/group'

const loading = ref(false)
const saving = ref(false)
const matrix = ref<GroupMatrix>({ groups: [], granted: {}, scopes: {}, scopesAllowed: [] })
const endpointTree = ref<EndpointTree>({ modules: [], total: 0 })
const users = ref<GroupUserRow[]>([])
const activeGroupId = ref('')
const tab = ref('priv')
const keyword = ref('')
const checkedKeys = ref<string[]>([])
const openModules = ref<string[]>([])

const activeGroup = computed<GroupRow | undefined>(() =>
  matrix.value.groups.find((g) => g.id === activeGroupId.value)
)

const filteredModules = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return endpointTree.value.modules
  return endpointTree.value.modules
    .map((m) => ({
      ...m,
      methods: m.methods.filter(
        (x) => x.method.toLowerCase().includes(kw) || m.module.toLowerCase().includes(kw)
      )
    }))
    .filter((m) => m.methods.length > 0)
})

function moduleState(mod: { methods: Array<{ key: string }> }) {
  return mod.methods.every((m) => checkedKeys.value.includes(m.key))
}

function moduleIndeterminate(mod: { methods: Array<{ key: string }> }) {
  const n = mod.methods.filter((m) => checkedKeys.value.includes(m.key)).length
  return n > 0 && n < mod.methods.length
}

function toggleModule(mod: { methods: Array<{ key: string }> }, on: boolean) {
  const keys = mod.methods.map((m) => m.key)
  const rest = checkedKeys.value.filter((k) => !keys.includes(k))
  checkedKeys.value = on ? [...rest, ...keys] : rest
}

function toggleKey(key: string, on: boolean) {
  const set = new Set(checkedKeys.value)
  on ? set.add(key) : set.delete(key)
  checkedKeys.value = [...set]
}

function toggleAll(on: boolean) {
  checkedKeys.value = on
    ? filteredModules.value.flatMap((m) => m.methods.map((x) => x.key))
    : []
}

function onSelect(id: string) {
  activeGroupId.value = id
  checkedKeys.value = [...(matrix.value.granted[id] || [])]
}

async function load() {
  loading.value = true
  try {
    const [m, ep, us] = await Promise.all([groupMatrix(), groupEndpoints(), groupUsers()])
    matrix.value = m
    endpointTree.value = ep
    users.value = us
    if (!activeGroupId.value && m.groups.length) {
      onSelect(m.groups[0].id)
    } else if (activeGroupId.value) {
      checkedKeys.value = [...(m.granted[activeGroupId.value] || [])]
    }
  } finally {
    loading.value = false
  }
}

async function onSave() {
  if (!activeGroupId.value) return
  saving.value = true
  try {
    const items = checkedKeys.value.map((k) => {
      const [module, method] = k.split('.')
      return {
        module,
        method,
        dataScope: matrix.value.scopes[activeGroupId.value]?.[k] || 'ALL'
      }
    })
    await saveGroupPrivs(activeGroupId.value, items)
    ElMessage.success(`已保存 ${items.length} 个接口权限`)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onCreate() {
  const { value } = await ElMessageBox.prompt('输入用户组名称', '新建用户组', {
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空'
  })
  try {
    const g = await createGroup({ name: value })
    ElMessage.success('已创建')
    await load()
    onSelect(g.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  }
}

async function onSetGroups(userId: string, groupIds: string[]) {
  try {
    await setUserGroups(userId, groupIds)
    ElMessage.success('已更新该用户的用户组')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}

onMounted(load)
</script>

<style scoped>
.groups-page {
  padding: 4px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ml {
  margin-left: 8px;
}
.count {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}
.hint {
  margin-top: 12px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.toolbar .cur {
  font-weight: 600;
}
.toolbar .spacer {
  flex: 1;
}
.tip {
  color: #909399;
  font-size: 12px;
}
.mod-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.methods {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
}
.method-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.method-row .path {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
