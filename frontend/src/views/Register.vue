<template>
  <div class="register-page">
    <el-card class="register-card" shadow="never">
      <h2 class="title">账号注册</h2>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <!-- 角色选择 -->
        <el-form-item label="注册角色" prop="role">
          <el-radio-group v-model="form.role" @change="onRoleChange">
            <el-radio-button value="STUDENT">学生</el-radio-button>
            <el-radio-button value="MERCHANT">商户</el-radio-button>
            <el-radio-button value="RIDER">骑手</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-alert
          v-if="form.role !== 'STUDENT'"
          type="info"
          :closable="false"
          show-icon
          class="tip"
          title="商户 / 骑手注册后为「待审核」状态，可登录但需管理员审核通过后才能使用相应功能"
        />

        <!-- ============ 学生 ============ -->
        <template v-if="form.role === 'STUDENT'">
          <el-form-item label="学号" prop="studentNo">
            <el-input v-model="form.studentNo" maxlength="20" placeholder="注册后即为登录账号" />
          </el-form-item>
          <el-form-item label="姓名" prop="realName">
            <el-input v-model="form.realName" maxlength="50" placeholder="真实姓名，将作为默认昵称" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" />
          </el-form-item>
          <el-form-item label="学院" prop="college">
            <el-input v-model="form.college" maxlength="100" placeholder="选填，如 计算机学院" />
          </el-form-item>
          <el-form-item label="年级" prop="grade">
            <el-input v-model="form.grade" maxlength="20" placeholder="选填，如 2021级" />
          </el-form-item>
        </template>

        <!-- ============ 商户 ============ -->
        <template v-else-if="form.role === 'MERCHANT'">
          <el-form-item label="商户名称" prop="merchantName">
            <el-input v-model="form.merchantName" maxlength="100" placeholder="如 第八食堂·三楼档口" />
          </el-form-item>
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="form.contactName" maxlength="50" placeholder="选填" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="同时作为登录账号" />
          </el-form-item>
          <el-form-item label="经营许可证" prop="licenseNo">
            <el-input v-model="form.licenseNo" maxlength="64" placeholder="选填，营业执照/校园经营许可编号" />
          </el-form-item>
        </template>

        <!-- ============ 骑手 ============ -->
        <template v-else>
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="form.realName" maxlength="50" placeholder="用于身份审核" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="同时作为登录账号" />
          </el-form-item>
          <el-form-item label="学生证/工号" prop="studentCardNo">
            <el-input v-model="form.studentCardNo" maxlength="32" placeholder="选填" />
          </el-form-item>
          <el-form-item label="配送车辆" prop="vehicleType">
            <el-select v-model="form.vehicleType" placeholder="选填" clearable style="width: 100%">
              <el-option label="步行" value="步行" />
              <el-option label="自行车" value="自行车" />
              <el-option label="电动车" value="电动车" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 公共字段 -->
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-20 位" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="onSubmit">注册</el-button>
        </el-form-item>
      </el-form>

      <div class="footer">
        <el-button text type="primary" @click="router.push('/login')">返回登录</el-button>
        <span class="text-muted">管理员账号不开放注册</span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'

import { register, type RegisterParams, type RegisterRole } from '@/api/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  role: 'STUDENT' as RegisterRole,
  password: '',
  phone: '',
  studentNo: '',
  realName: '',
  college: '',
  grade: '',
  merchantName: '',
  contactName: '',
  licenseNo: '',
  studentCardNo: '',
  vehicleType: ''
})

/**
 * 校验规则按角色动态切换：
 * Element Plus 的 :rules 支持传入计算属性，角色一变规则立即生效。
 */
const rules = computed<FormRules>(() => {
  const base: FormRules = {
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, max: 20, message: '密码长度需在 6-20 位之间', trigger: 'blur' }
    ],
    phone: [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
    ]
  }

  if (form.role === 'STUDENT') {
    return {
      ...base,
      studentNo: [
        { required: true, message: '请输入学号', trigger: 'blur' },
        { max: 20, message: '学号长度不能超过 20 位', trigger: 'blur' }
      ],
      realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
    }
  }

  if (form.role === 'MERCHANT') {
    return {
      ...base,
      merchantName: [{ required: true, message: '请输入商户名称', trigger: 'blur' }]
    }
  }

  return {
    ...base,
    realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }]
  }
})

/** 切换角色后清掉校验残留的红字 */
function onRoleChange() {
  formRef.value?.clearValidate()
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  // 只提交当前角色需要的字段，避免把无关字段传到后端
  const payload: RegisterParams =
    form.role === 'STUDENT'
      ? {
          role: form.role,
          password: form.password,
          phone: form.phone.trim(),
          studentNo: form.studentNo.trim(),
          realName: form.realName.trim(),
          college: form.college.trim() || undefined,
          grade: form.grade.trim() || undefined
        }
      : form.role === 'MERCHANT'
        ? {
            role: form.role,
            password: form.password,
            phone: form.phone.trim(),
            merchantName: form.merchantName.trim(),
            contactName: form.contactName.trim() || undefined,
            licenseNo: form.licenseNo.trim() || undefined
          }
        : {
            role: form.role,
            password: form.password,
            phone: form.phone.trim(),
            realName: form.realName.trim(),
            studentCardNo: form.studentCardNo.trim() || undefined,
            vehicleType: form.vehicleType || undefined
          }

  loading.value = true
  try {
    await register(payload)
    const tips: Record<RegisterRole, string> = {
      STUDENT: '注册成功，请使用学号登录',
      MERCHANT: '注册成功，请等待管理员审核通过后使用商户功能',
      RIDER: '注册成功，请等待管理员审核通过后开始接单'
    }
    ElMessage.success(tips[form.role])
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #eef3ff 0%, #f7f9fc 100%);
}
.register-card {
  width: 480px;
  padding: 8px 12px;
}
.title {
  margin: 0 0 16px;
  font-size: 18px;
  text-align: center;
  color: var(--campus-primary);
}
.tip {
  margin-bottom: 16px;
}
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.text-muted {
  font-size: 12px;
  color: #909399;
}
</style>
