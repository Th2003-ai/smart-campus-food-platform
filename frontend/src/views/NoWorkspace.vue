<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <h2>当前角色还没有专属工作台</h2>
      <p class="muted">
        你的账号角色是
        <el-tag size="small">{{ roleLabel }}</el-tag>
        （<code>{{ userStore.role }}</code>）。
      </p>
      <p class="muted">
        这个角色是通过「角色管理」动态添加的，系统只给了它权限码，还没有配对应的页面。
        请让管理员在「角色权限」页确认授权，或改用内置四端账号登录。
      </p>
      <div class="actions">
        <el-button type="primary" @click="onLogout">退出登录</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listLoginRoles } from '@/api/role'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const roleNames = ref<Record<string, string>>({})

const roleLabel = computed(() => roleNames.value[userStore.role] ?? userStore.role)

onMounted(async () => {
  try {
    const rows = await listLoginRoles()
    roleNames.value = Object.fromEntries(rows.map((item) => [item.code, item.name]))
  } catch {
    // 取不到名称就显示编码
  }
})

async function onLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #eef3ff 0%, #f7f9fc 100%);
}
.card {
  width: 460px;
  padding: 8px 12px;
}
h2 {
  margin: 0 0 12px;
  font-size: 18px;
  color: var(--campus-primary);
}
.muted {
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
}
.actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
