<template>
  <div class="security-page">
    <div class="page-heading">
      <div>
        <h2 class="page-title">账号安全</h2>
        <p>查看账号安全状态，选择需要处理的安全项。</p>
      </div>
      <el-icon class="heading-icon"><Lock /></el-icon>
    </div>

    <div v-loading="loading" class="security-card card">
      <h3 class="section-title">安全状态</h3>

      <div class="security-row">
        <div class="row-icon"><User /></div>
        <div class="row-content">
          <strong>登录账号</strong>
          <span>{{ security.username || userStore.username }}</span>
        </div>
        <el-tag type="success">已设置</el-tag>
      </div>

      <div class="security-row">
        <div class="row-icon"><Lock /></div>
        <div class="row-content">
          <strong>登录密码</strong>
          <span>建议定期修改密码，保护账号安全</span>
        </div>
        <div class="row-action">
          <el-tag type="success">已设置</el-tag>
          <router-link to="/user/security/password" class="security-action">
            修改密码
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
      </div>

      <div class="security-row">
        <div class="row-icon"><Message /></div>
        <div class="row-content">
          <strong>安全邮箱</strong>
          <span>{{ security.email || '尚未绑定邮箱' }}</span>
        </div>
        <div class="row-action">
          <el-tag :type="emailVerified ? 'success' : 'warning'">
            {{ emailStatusLabel }}
          </el-tag>
          <router-link to="/user/security/email" class="security-action">
            {{ emailActionLabel }}
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
      </div>
    </div>

    <div v-if="!emailVerified" class="security-tip">
      <el-icon><InfoFilled /></el-icon>
      <span>验证邮箱后，忘记密码时可以通过邮箱验证码找回。</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowRight, InfoFilled, Lock, Message, User } from '@element-plus/icons-vue'
import { getSecurityInfo } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const security = reactive({ username: '', email: '', emailVerified: 0 })
const emailVerified = computed(() => security.emailVerified === 1)
const emailStatusLabel = computed(() => {
  if (emailVerified.value) return '已验证'
  return security.email ? '待验证' : '未绑定'
})
const emailActionLabel = computed(() => {
  if (emailVerified.value) return '更换邮箱'
  return security.email ? '验证邮箱' : '绑定邮箱'
})

async function loadSecurity() {
  loading.value = true
  try {
    Object.assign(security, await getSecurityInfo())
  } finally {
    loading.value = false
  }
}

onMounted(loadSecurity)
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.security-page { display: flex; flex-direction: column; gap: $space-lg; max-width: 760px; }

.page-heading { display: flex; align-items: center; justify-content: space-between; }
.page-heading p { margin: 4px 0 0; color: $color-text-muted; font-size: $font-size-sm; }
.heading-icon { color: $color-primary; font-size: 28px; }

.security-card { padding: $space-xl; }
.section-title { margin: 0 0 $space-lg; font-size: $font-size-lg; }

.security-row {
  display: flex;
  align-items: center;
  gap: $space-md;
  padding: $space-md 0;
  border-bottom: 1px solid $color-border-light;
}

.security-row:last-child { border-bottom: 0; padding-bottom: 0; }
.row-icon { display: grid; width: 38px; height: 38px; place-items: center; border-radius: $radius-md; background: $color-bg-warm; color: $color-primary; }
.row-content { flex: 1; min-width: 0; }
.row-content strong, .row-content span { display: block; }
.row-content span { margin-top: 4px; color: $color-text-muted; font-size: $font-size-sm; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.row-action { display: flex; align-items: center; gap: $space-md; flex-shrink: 0; }
.security-action { display: inline-flex; align-items: center; gap: 4px; color: $color-primary; font-size: $font-size-sm; font-weight: 600; white-space: nowrap; }
.security-action:hover { color: $color-primary-dark; }

.security-tip { display: flex; align-items: center; gap: $space-sm; color: $color-text-secondary; font-size: $font-size-sm; }
.security-tip .el-icon { color: $color-warning; }

@media (max-width: 600px) {
  .security-card { padding: $space-lg; }
  .security-row { align-items: flex-start; }
  .row-action { flex-direction: column; align-items: flex-end; gap: 6px; }
}
</style>
