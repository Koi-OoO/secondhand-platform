<template>
  <div class="security-form-page">
    <router-link to="/user/security" class="back-link">
      <el-icon><ArrowLeft /></el-icon>
      返回账号安全
    </router-link>

    <div class="page-heading">
      <div>
        <h2 class="page-title">{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <el-icon class="heading-icon"><Message /></el-icon>
    </div>

    <div v-loading="loading" class="security-card card">
      <div class="current-status">
        <span>当前邮箱</span>
        <strong>{{ security.email || '尚未绑定' }}</strong>
        <el-tag v-if="security.email" :type="emailVerified ? 'success' : 'warning'">
          {{ emailVerified ? '已验证' : '待验证' }}
        </el-tag>
      </div>
      <p class="card-hint">验证码将发送到你填写的邮箱，请使用本人常用邮箱。</p>

      <el-form ref="emailFormRef" :model="emailForm" :rules="emailRules" label-position="top">
        <el-form-item label="邮箱地址" prop="email">
          <el-input v-model="emailForm.email" size="large" placeholder="请输入常用邮箱" :prefix-icon="Message" />
        </el-form-item>
        <div class="code-field">
          <el-form-item label="验证码" prop="code">
            <el-input v-model="emailForm.code" size="large" maxlength="6" placeholder="请输入 6 位验证码" />
          </el-form-item>
          <el-button class="send-button" :disabled="countdown > 0" :loading="sending" @click="sendBindCode">
            {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
          </el-button>
        </div>
        <div class="form-actions">
          <el-button v-if="emailVerified" class="unbind-button" type="danger" plain @click="openUnbindDialog">
            解绑邮箱
          </el-button>
          <router-link to="/user/security" class="cancel-link">取消</router-link>
          <el-button type="primary" :loading="saving" @click="verifyBindCode">验证并保存</el-button>
        </div>
      </el-form>
    </div>

    <el-dialog v-model="unbindDialogVisible" title="解绑安全邮箱" width="420px" destroy-on-close>
      <p class="unbind-warning">解绑后将无法通过邮箱找回密码，请确认仍能通过其他方式保护账号。</p>
      <el-form ref="unbindFormRef" :model="unbindForm" :rules="unbindRules" label-position="top">
        <div class="code-field">
          <el-form-item label="邮箱验证码" prop="code">
            <el-input v-model="unbindForm.code" maxlength="6" placeholder="请输入 6 位验证码" />
          </el-form-item>
          <el-button class="send-button" :disabled="unbindCountdown > 0" :loading="unbindSending" @click="sendUnbindCode">
            {{ unbindCountdown > 0 ? `${unbindCountdown}s 后重发` : '发送验证码' }}
          </el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="closeUnbindDialog">取消</el-button>
        <el-button type="danger" :loading="unbinding" @click="confirmUnbindEmail">确认解绑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Message } from '@element-plus/icons-vue'
import {
  getSecurityInfo,
  requestEmailBindCode,
  requestEmailUnbindCode,
  unbindEmail as unbindEmailRequest,
  verifyEmailBindCode
} from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const sending = ref(false)
const saving = ref(false)
const countdown = ref(0)
const security = reactive({ email: '', emailVerified: 0 })
const emailForm = reactive({ email: '', code: '' })
const emailFormRef = ref(null)
const unbindForm = reactive({ code: '' })
const unbindFormRef = ref(null)
const unbindDialogVisible = ref(false)
const unbindSending = ref(false)
const unbinding = ref(false)
const unbindCountdown = ref(0)
let timer
let unbindTimer

const emailVerified = computed(() => security.emailVerified === 1)
const pageTitle = computed(() => emailVerified.value ? '更换安全邮箱' : '绑定安全邮箱')
const pageDescription = computed(() => emailVerified.value
  ? '更换后，新的邮箱将用于找回密码。'
  : '绑定并验证邮箱，用于找回密码和保护账号安全。')

const emailRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位数字验证码', trigger: 'blur' }
  ]
}
const unbindRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位数字验证码', trigger: 'blur' }
  ]
}

async function loadSecurity() {
  loading.value = true
  try {
    const data = await getSecurityInfo()
    Object.assign(security, data)
    if (!data.emailVerified && data.email) emailForm.email = data.email
  } finally {
    loading.value = false
  }
}

async function sendBindCode() {
  const valid = await emailFormRef.value?.validateField('email').catch(() => false)
  if (!valid || countdown.value > 0) return
  sending.value = true
  try {
    await requestEmailBindCode(emailForm.email)
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) window.clearInterval(timer)
    }, 1000)
    ElMessage.success('验证码已发送，请查收邮箱')
  } finally {
    sending.value = false
  }
}

async function verifyBindCode() {
  const valid = await emailFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await verifyEmailBindCode(emailForm.email, emailForm.code)
    await userStore.fetchProfile()
    ElMessage.success('邮箱保存成功')
    router.push('/user/security')
  } finally {
    saving.value = false
  }
}

function openUnbindDialog() {
  unbindDialogVisible.value = true
}

function closeUnbindDialog() {
  if (unbinding.value) return
  unbindDialogVisible.value = false
  unbindForm.code = ''
  unbindFormRef.value?.clearValidate()
}

async function sendUnbindCode() {
  if (unbindCountdown.value > 0) return
  unbindSending.value = true
  try {
    await requestEmailUnbindCode()
    unbindCountdown.value = 60
    unbindTimer = window.setInterval(() => {
      unbindCountdown.value -= 1
      if (unbindCountdown.value <= 0) window.clearInterval(unbindTimer)
    }, 1000)
    ElMessage.success('验证码已发送，请查收当前邮箱')
  } finally {
    unbindSending.value = false
  }
}

async function confirmUnbindEmail() {
  const valid = await unbindFormRef.value?.validate().catch(() => false)
  if (!valid) return
  unbinding.value = true
  try {
    await unbindEmailRequest(unbindForm.code)
    await userStore.fetchProfile()
    security.email = ''
    security.emailVerified = 0
    unbinding.value = false
    closeUnbindDialog()
    ElMessage.success('邮箱解绑成功')
    router.push('/user/security')
  } finally {
    unbinding.value = false
  }
}

onMounted(loadSecurity)
onBeforeUnmount(() => {
  window.clearInterval(timer)
  window.clearInterval(unbindTimer)
})
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
.current-status { display: flex; align-items: center; gap: $space-md; padding-bottom: $space-lg; border-bottom: 1px solid $color-border-light; }
.current-status span { color: $color-text-muted; font-size: $font-size-sm; }
.current-status strong { margin-right: auto; color: $color-text; }
.card-hint { margin: $space-lg 0; color: $color-text-muted; font-size: $font-size-sm; }
.code-field { display: grid; grid-template-columns: 1fr auto; align-items: end; gap: $space-md; }
.send-button { margin-bottom: 18px; }
.form-actions { display: flex; align-items: center; justify-content: flex-end; gap: $space-md; margin-top: $space-sm; }
.unbind-button { margin-right: auto; }
.cancel-link { color: $color-text-secondary; font-size: $font-size-sm; }
.cancel-link:hover { color: $color-primary; }
.unbind-warning { margin: 0 0 $space-lg; color: $color-text-secondary; line-height: 1.6; }

@media (max-width: 600px) {
  .security-card { padding: $space-lg; }
  .current-status { align-items: flex-start; flex-wrap: wrap; }
  .current-status strong { width: 100%; order: 3; }
  .code-field { grid-template-columns: 1fr; gap: 0; }
  .send-button { width: 100%; margin: -8px 0 $space-md; }
  .unbind-button { margin-right: 0; }
}
</style>
