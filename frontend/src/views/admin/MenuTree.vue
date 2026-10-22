<template>
  <div class="page menu-tree-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">菜单权限树（树形数据结构可视化）</span>
          <div class="toolbar">
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button label="tree">树形视图</el-radio-button>
              <el-radio-button label="flat">平铺列表</el-radio-button>
            </el-radio-group>
            <el-button size="small" @click="expandAll">全部展开</el-button>
            <el-button size="small" @click="collapseAll">全部收起</el-button>
            <el-button size="small" type="primary" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 说明 -->
      <el-alert type="info" :closable="false" class="tip"
        title="数据库 sys_menu 表通过 parent_id 自引用存储菜单，后端把平铺数据组装成「目录 → 菜单 → 按钮」多叉树，前端用 el-tree 渲染。" />

      <!-- 树的结构指标 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <el-statistic title="根节点（四端）" :value="roots" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <el-statistic title="节点总数" :value="total" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <el-statistic title="树的最大深度" :value="maxDepth" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <el-statistic title="按钮（叶子）数" :value="buttonCount" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 树形视图 -->
      <el-tree
        v-if="viewMode === 'tree'"
        ref="treeRef"
        v-loading="loading"
        :data="treeData"
        node-key="id"
        :props="treeProps"
        default-expand-all
        :expand-on-click-node="false"
        highlight-current
        class="menu-tree"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <el-icon class="node-icon"><component :is="data.icon || 'Menu'" /></el-icon>
            <span class="node-label">{{ data.label }}</span>
            <el-tag :type="typeTagType(data.menuType)" size="small" effect="plain">{{ data.typeText }}</el-tag>
            <span v-if="data.path" class="node-path">{{ data.path }}</span>
            <code v-if="data.perms" class="node-perms">{{ data.perms }}</code>
          </span>
        </template>
      </el-tree>

      <!-- 平铺列表（原始数据，与树形对照） -->
      <el-table v-else v-loading="loading" :data="flatData" border size="small" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="parentId" label="父节点ID" width="90" />
        <el-table-column prop="menuName" label="菜单名称" min-width="140" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.menuType)" size="small" effect="plain">{{ typeText(row.menuType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由地址" min-width="160" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElTree, ElMessage } from 'element-plus'
import { menuList, menuTree, type MenuFlat, type MenuNode } from '@/api/admin'

const treeRef = ref<InstanceType<typeof ElTree>>()
const treeData = ref<MenuNode[]>([])
const flatData = ref<MenuFlat[]>([])
const loading = ref(false)
const viewMode = ref<'tree' | 'flat'>('tree')
const treeProps = { label: 'label', children: 'children' }

/** 根节点数量（四端目录） */
const roots = computed(() => treeData.value.length)
/** 节点总数（平铺列表长度即所有节点） */
const total = computed(() => flatData.value.length)
/** 叶子按钮数量 */
const buttonCount = computed(() => flatData.value.filter((m) => m.menuType === 2).length)
/** 树的最大深度（递归计算） */
const maxDepth = computed(() => {
  let depth = 0
  const walk = (nodes: MenuNode[], level: number) => {
    if (nodes.length) depth = Math.max(depth, level)
    nodes.forEach((n) => n.children?.length && walk(n.children, level + 1))
  }
  walk(treeData.value, 1)
  return depth
})

async function load() {
  loading.value = true
  try {
    const [tree, flat] = await Promise.all([menuTree(), menuList()])
    treeData.value = tree
    flatData.value = flat
    ElMessage.success('菜单树加载成功')
  } finally {
    loading.value = false
  }
}

function walkIds(nodes: MenuNode[], cb: (id: string) => void) {
  nodes.forEach((n) => {
    cb(n.id)
    if (n.children?.length) walkIds(n.children, cb)
  })
}

function expandAll() {
  walkIds(treeData.value, (id) => {
    const node = treeRef.value?.getNode(id)
    if (node) node.expanded = true
  })
}

function collapseAll() {
  walkIds(treeData.value, (id) => {
    const node = treeRef.value?.getNode(id)
    if (node) node.expanded = false
  })
}

function typeTagType(t: number): 'primary' | 'success' | 'info' {
  return t === 0 ? 'primary' : t === 1 ? 'success' : 'info'
}

function typeText(t: number): string {
  return t === 0 ? '目录' : t === 1 ? '菜单' : '按钮'
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.title {
  font-weight: 600;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tip {
  margin-bottom: 16px;
}
.stat-row {
  margin-bottom: 16px;
}
.stat-card :deep(.el-statistic__head) {
  font-size: 13px;
  color: #909399;
}
.menu-tree {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
}
.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  padding-right: 8px;
}
.node-icon {
  color: var(--el-color-primary);
}
.node-label {
  font-weight: 500;
}
.node-path {
  font-size: 12px;
  color: #909399;
}
.node-perms {
  font-size: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  border-radius: 4px;
  padding: 0 6px;
}
</style>
