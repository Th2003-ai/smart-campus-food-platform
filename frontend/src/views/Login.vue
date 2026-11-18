<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <h2 class="title">智能 AI 校园外卖平台</h2>
      <p class="text-muted">登录后按角色进入对应工作台</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px" @keyup.enter="onSubmit">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="学号 / 手机号 / admin" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="演示账号密码见 database/README.md"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="onSubmit">登录</el-button>
        </el-form-item>
      </el-form>

      <div class="footer">
        <span class="text-muted">演示账号见 database/README.md</span>
        <el-button text type="primary" @click="router.push('/register')">注册账号</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { ROLE_HOME, useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

/**
 * 登录只填账号密码：角色由账号在 sys_role 里的绑定决定，前端不再让用户选。
 * 这样新增角色不需要改登录页，也不会出现「选错入口」的问题。
 */
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    const result = await userStore.login({ username: form.username, password: form.password })
    // 首页由角色配置（sys_role.home_path）决定，内置四端有默认值
    const home = userStore.home || ROLE_HOME[result.role] || '/no-workspace'
    const redirect = route.query.redirect as string | undefined
    router.push(redirect || home)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #eef3ff 0%, #f7f9fc 100%);
}
.login-card {
  width: 420px;
  padding: 8px 12px;
}
.title {
  margin: 0 0 4px;
  font-size: 20px;
  text-align: center;
  color: var(--campus-primary);
}
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
</style>
