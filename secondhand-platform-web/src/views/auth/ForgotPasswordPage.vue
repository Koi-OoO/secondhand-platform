<template>
  <div class="reset-page">
    <div class="reset-card">
      <router-link to="/login" class="back-link">返回登录</router-link>
      <div class="reset-heading">
        <span class="reset-mark"><Key /></span>
        <p class="eyebrow">ACCOUNT RECOVERY</p>
        <h1>找回密码</h1>
        <p>使用绑定邮箱验证身份，重新设置登录密码。</p>
      </div>

      <el-steps :active="step - 1" simple class="reset-steps">
        <el-step title="验证账号" />
        <el-step title="输入验证码" />
        <el-step title="设置密码" />
      </el-steps>

      <el-form v-if="step === 1" ref="accountFormRef" :model="accountForm" :rules="accountRules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="accountForm.username" size="large" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="绑定邮箱" prop="email">
          <el-input v-model="accountForm.email" size="large" placeholder="请输入注册时绑定的邮箱" :prefix-icon="Message" />
        </el-form-item>
        <el-button class="submit-button" type="primary" size="large" :loading="loading" @click="sendCode">
          发送验证码
        </el-button>
      </el-form>

      <el-form v-else-if="step === 2" ref="codeFormRef" :model="codeForm" :rules="codeRules" label-position="top">
        <p class="step-hint">验证码已发送至 {{ maskedEmail }}</p>
        <el-form-item label="邮箱验证码" prop="code">
          <el-input v-model="codeForm.code" size="large" maxlength="6" placeholder="请输入 6 位验证码" :prefix-icon="Message" />
        </el-form-item>
        <div class="code-actions">
          <el-button class="submit-button" type="primary" size="large" :loading="loading" @click="verifyCode">
            验证并继续
          </el-button>
          <el-button link :disabled="countdown > 0" @click="sendCode">
            {{ countdown > 0 ? `${countdown}s 后重新发送` : '重新发送验证码' }}
          </el-button>
        </div>
      </el-form>

      <el-form v-else ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password size="large" placeholder="请输入 6-64 位新密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password size="large" placeholder="请再次输入新密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-button class="submit-button" type="primary" size="large" :loading="loading" @click="confirmReset">
          完成重置
        </el-button>
      </el-form>

      <p v-if="step === 1" class="reset-note">账号未绑定邮箱？请联系平台管理员处理。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message, User } from '@element-plus/icons-vue'
import { requestPasswordResetCode, resetPassword, verifyPasswordResetCode } from '@/api/user'

const router = useRouter()
const step = ref(1)
const loading = ref(false)
const countdown = ref(0)
const resetToken = ref('')
let timer

const accountFormRef = ref(null)
const codeFormRef = ref(null)
const passwordFormRef = ref(null)
const accountForm = reactive({ username: '', email: '' })
const codeForm = reactive({ code: '' })
const passwordForm = reactive({ newPassword: '', confirmPassword: '' })

const accountRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入绑定邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}
const codeRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位数字验证码', trigger: 'blur' }
  ]
}
const passwordRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需为 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [{
    validator: (_rule, value, callback) => value === passwordForm.newPassword ? callback() : callback(new Error('两次输入的密码不一致')),
    trigger: 'blur'
  }]
}

const maskedEmail = computed(() => {
  const [name, domain] = accountForm.email.split('@')
  if (!name || !domain) return accountForm.email
  return `${name.slice(0, 2)}***@${domain}`
})

async function sendCode() {
  const valid = await accountFormRef.value?.validate().catch(() => false)
  if (!valid || countdown.value > 0) return
  loading.value = true
  try {
    await requestPasswordResetCode(accountForm.username, accountForm.email)
    step.value = 2
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) window.clearInterval(timer)
    }, 1000)
    ElMessage.success('验证码已发送，请查收邮箱')
  } finally {
    loading.value = false
  }
}

async function verifyCode() {
  const valid = await codeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    resetToken.value = await verifyPasswordResetCode(accountForm.username, accountForm.email, codeForm.code)
    step.value = 3
  } finally {
    loading.value = false
  }
}

async function confirmReset() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await resetPassword(resetToken.value, passwordForm.newPassword)
    ElMessage.success('密码已重置，请使用新密码登录')
    router.replace('/login')
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.reset-page {
  width: min(520px, 100%);
  padding: $space-xl;
}

.reset-card {
  position: relative;
  padding: 34px 34px 28px;
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-bg-card;
  box-shadow: 8px 8px 0 $color-ink;
}

.back-link {
  color: $color-text-muted;
  font-size: $font-size-sm;
}

.reset-heading {
  margin: 24px 0;
  text-align: center;

  .reset-mark {
    display: grid;
    width: 46px;
    height: 46px;
    margin: 0 auto 12px;
    place-items: center;
    border: $border-ink;
    border-radius: $radius-md;
    background: $color-yellow;
    box-shadow: 4px 4px 0 $color-ink;
  }

  .eyebrow {
    margin: 0 0 4px;
    color: $color-red-deep;
    font-family: $font-family-mono;
    font-size: 11px;
    font-weight: $font-weight-heavy;
    letter-spacing: 1px;
  }

  h1 {
    margin: 0;
    font-family: $font-family-display;
    font-size: 30px;
  }

  > p:last-child {
    margin: 8px 0 0;
    color: $color-text-muted;
    font-size: $font-size-sm;
  }
}

.reset-steps {
  margin-bottom: 26px;
}

.step-hint,
.reset-note {
  color: $color-text-muted;
  font-size: $font-size-sm;
  line-height: 1.6;
}

.code-actions {
  display: grid;
  gap: 8px;
}

.submit-button {
  width: 100%;
}

.reset-note {
  margin: 18px 0 0;
  text-align: center;
}

@media (max-width: 560px) {
  .reset-page { padding: $space-md; }
  .reset-card { padding: 28px 20px 22px; box-shadow: 5px 5px 0 $color-ink; }
}
</style>
