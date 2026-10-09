<template>
  <div class="security-form-page">
    <router-link to="/user/security" class="back-link">
      <el-icon><ArrowLeft /></el-icon>
      返回账号安全
    </router-link>

    <div class="page-heading">
      <div>
        <h2 class="page-title">修改登录密码</h2>
        <p>使用当前密码验证身份后设置新密码。</p>
      </div>
      <el-icon class="heading-icon"><Lock /></el-icon>
    </div>

    <div class="security-card card">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6-64 位" :prefix-icon="Key" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" :prefix-icon="Key" />
        </el-form-item>
        <div class="form-actions">
          <router-link to="/user/security" class="cancel-link">取消</router-link>
          <el-button type="primary" :loading="saving" @click="savePassword">保存新密码</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Key, Lock } from '@element-plus/icons-vue'
import { changePassword } from '@/api/user'

const passwordFormRef = ref(null)
const saving = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需为 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [{
    validator: (_rule, value, callback) => value === passwordForm.newPassword ? callback() : callback(new Error('两次输入的密码不一致')),
    trigger: 'blur'
  }]
}

async function savePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordFormRef.value?.clearValidate()
    ElMessage.success('密码修改成功')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.security-form-page { display: flex; flex-direction: column; gap: $space-lg; max-width: 760px; }
.back-link { display: inline-flex; align-items: center; gap: 4px; width: fit-content; color: $color-text-muted; font-size: $font-size-sm; }
.back-link:hover { color: $color-primary; }
.page-heading { display: flex; align-items: center; justify-content: space-between; }
.page-heading p { margin: 4px 0 0; color: $color-text-muted; font-size: $font-size-sm; }
.heading-icon { color: $color-primary; font-size: 28px; }
.security-card { padding: $space-xl; }
.form-actions { display: flex; align-items: center; justify-content: flex-end; gap: $space-md; margin-top: $space-sm; }
.cancel-link { color: $color-text-secondary; font-size: $font-size-sm; }
.cancel-link:hover { color: $color-primary; }

@media (max-width: 600px) {
  .security-card { padding: $space-lg; }
}
</style>
