<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>知识库</span>
          <div class="header-right">
            <el-input
              v-model="category"
              placeholder="按分类筛选"
              clearable
              size="small"
              style="width: 160px"
              @change="load"
            />
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
            <el-button type="primary" size="small" @click="openEdit()">新增问答</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb"
        title="这里维护的问答对是智能客服的召回依据：问题、关键词、答案都会被检索；命中次数越高排序越前"
      />

      <el-empty v-if="!loading && !list.length" description="还没有知识条目，点「新增问答」开始维护" />

      <el-table v-else :data="list" stripe>
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="question" label="标准问题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="answer" label="标准答案" min-width="260" show-overflow-tooltip />
        <el-table-column prop="keywords" label="关键词" min-width="150" show-overflow-tooltip />
        <el-table-column prop="hitCount" label="命中" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small">
              {{ row.status === 0 ? '停用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑问答' : '新增问答'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="分类" prop="category">
          <el-input v-model="form.category" maxlength="50" placeholder="如 配送 / 退款 / 账号" />
        </el-form-item>
        <el-form-item label="标准问题" prop="question">
          <el-input v-model="form.question" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
        <el-form-item label="标准答案" prop="answer">
          <el-input v-model="form.answer" type="textarea" :rows="4" maxlength="1000" />
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input
            v-model="form.keywords"
            maxlength="255"
            placeholder="逗号分隔，留空则用标准问题兜底"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import { listKnowledge, removeKnowledge, saveKnowledge, type Knowledge } from '@/api/admin'

const loading = ref(false)
const saving = ref(false)
const list = ref<Knowledge[]>([])
const category = ref('')

const editVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Knowledge>({
  id: undefined,
  category: '',
  question: '',
  answer: '',
  keywords: '',
  status: 1
})

const rules: FormRules = {
  question: [{ required: true, message: '请输入标准问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入标准答案', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    list.value = await listKnowledge(category.value.trim() || undefined)
  } catch {
    // 拦截器提示
  } finally {
    loading.value = false
  }
}

function openEdit(row?: Knowledge) {
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, {
      id: undefined,
      category: '',
      question: '',
      answer: '',
      keywords: '',
      status: 1
    })
  }
  formRef.value?.clearValidate()
  editVisible.value = true
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await saveKnowledge({
      id: form.id,
      category: form.category?.trim() || undefined,
      question: form.question.trim(),
      answer: form.answer.trim(),
      keywords: form.keywords?.trim() || undefined,
      status: form.status
    })
    ElMessage.success('已保存，客服下次回答即生效')
    editVisible.value = false
    await load()
  } catch {
    // 拦截器提示
  } finally {
    saving.value = false
  }
}

async function onRemove(row: Knowledge) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.question}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await removeKnowledge(row.id as string)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 拦截器提示
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
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
