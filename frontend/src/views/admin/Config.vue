<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>系统配置</span>
          <div class="header-right">
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
            <el-button type="primary" size="small" @click="openEdit()">新增配置</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="mb"
        title="密钥安全"
        description="大模型 API Key 等敏感项请通过环境变量或密钥管理注入，不要在这里明文保存，更不要提交到仓库。"
      />

      <el-empty v-if="!loading && !list.length" description="还没有配置项，点「新增配置」开始维护" />

      <el-table v-else :data="list" stripe>
        <el-table-column prop="configKey" label="配置键" min-width="200" />
        <el-table-column prop="configValue" label="配置值" min-width="220" show-overflow-tooltip />
        <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" :title="isNew ? '新增配置' : '编辑配置'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" :disabled="!isNew" placeholder="如 platform.notice" />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input v-model="form.configValue" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="说明" prop="remark">
          <el-input v-model="form.remark" maxlength="255" placeholder="选填，便于其他人理解这个配置的用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { listConfig, saveConfig, type ConfigItem } from '@/api/admin'

const loading = ref(false)
const saving = ref(false)
const list = ref<ConfigItem[]>([])

const editVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ConfigItem>({ configKey: '', configValue: '', remark: '' })

const isNew = computed(() => !list.value.some((c) => c.configKey === form.configKey))

const rules: FormRules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    list.value = await listConfig()
  } catch {
    // 拦截器提示
  } finally {
    loading.value = false
  }
}

function openEdit(row?: ConfigItem) {
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, { configKey: '', configValue: '', remark: '' })
  }
  formRef.value?.clearValidate()
  editVisible.value = true
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await saveConfig({
      configKey: form.configKey.trim(),
      configValue: form.configValue ?? '',
      remark: form.remark?.trim() || undefined
    })
    ElMessage.success('配置已保存')
    editVisible.value = false
    await load()
  } catch {
    // 拦截器提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mb {
  margin-bottom: 14px;
}
</style>
