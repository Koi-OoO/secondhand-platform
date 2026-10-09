<template>
  <div class="auth-page auth-page--login">
    <div class="auth-visual">
      <div class="auth-visual__inner">
        <router-link to="/" class="auth-brand" aria-label="返回首页">
          <span>闲</span>
          <b>校园闲物</b>
        </router-link>
        <div class="visual-badge">LOGIN PASS · 市集通行证</div>
        <h1>回到你的<br>校园摊位</h1>
        <p>查看收藏、订单和正在出售的闲置，让好物继续流转。</p>
        <div class="visual-stats">
          <div class="stat-item">
            <span class="stat-num">128</span>
            <span class="stat-label">今日新上</span>
          </div>
          <div class="stat-item">
            <span class="stat-num">356</span>
            <span class="stat-label">本周成交</span>
          </div>
        </div>
        <div class="poster-barcode" aria-hidden="true"></div>
      </div>
    </div>

    <div class="auth-form-area">
      <div class="auth-form-card">
        <span class="form-tape" aria-hidden="true"></span>
        <div class="auth-form__header">
          <p class="form-kicker">WELCOME BACK</p>
          <h2>欢迎回来</h2>
          <p>登录你的校园闲物账号</p>
        </div>

        <div v-if="userStore.loginError" class="auth-error">{{ userStore.loginError }}</div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              size="large"
            />
          </el-form-item>

          <div class="forgot-link-row">
            <router-link to="/forgot-password">忘记密码？</router-link>
          </div>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="btn-submit"
              :loading="userStore.loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="auth-form__footer">
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref(null)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const ok = await userStore.login(form.username, form.password)
  if (ok) {
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

@keyframes authIn {
  from { opacity: 0; transform: translateY(16px) scale(0.99); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.auth-page {
  position: relative;
  width: 100%;
  max-width: 980px;
  min-height: 610px;
  display: flex;
  border: $border-ink;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: 10px 10px 0 $color-ink;
  background: $color-bg-card;
  animation: authIn 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;

  &::after {
    content: '';
    position: absolute;
    right: 0;
    bottom: 0;
    border-width: 0 0 28px 28px;
    border-style: solid;
    border-color: transparent transparent $color-paper-3 transparent;
    box-shadow: -2px -2px 0 rgba($color-ink, 0.22);
    pointer-events: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-page { animation: none; }
}

.auth-visual {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-2xl;
  overflow: hidden;
  border-right: $border-ink;
  background:
    radial-gradient(rgba($color-paper, 0.34) 1.5px, transparent 1.6px) 0 0 / 12px 12px,
    linear-gradient(150deg, $color-red 0%, $color-red-deep 55%, $color-ink 100%);
  color: $color-paper;

  &::before,
  &::after {
    content: '';
    position: absolute;
    border: 2px solid rgba($color-paper, 0.35);
    transform: rotate(-8deg);
  }

  &::before {
    width: 210px;
    height: 150px;
    right: -52px;
    top: 46px;
    border-radius: $radius-lg;
  }

  &::after {
    width: 160px;
    height: 110px;
    left: -44px;
    bottom: 58px;
    border-radius: $radius-md;
    transform: rotate(10deg);
  }

  &__inner {
    position: relative;
    z-index: 1;
    max-width: 360px;
  }
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: $color-paper;

  span {
    display: grid;
    width: 42px;
    height: 42px;
    place-items: center;
    border: 2px solid $color-paper;
    border-radius: $radius-md;
    background: $color-yellow;
    box-shadow: 4px 4px 0 $color-ink;
    color: $color-ink;
    font-family: $font-family-display;
    font-size: 24px;
    font-weight: $font-weight-heavy;
    transform: rotate(-6deg);
  }

  b {
    font-family: $font-family-display;
    font-size: 24px;
    font-weight: $font-weight-heavy;
    letter-spacing: 1px;
  }
}

.auth-visual h1 {
  margin: $space-lg 0 $space-md;
  font-family: $font-family-display;
  font-size: 44px;
  font-weight: $font-weight-heavy;
  line-height: 1.18;
  letter-spacing: 0;
}

.auth-visual p {
  color: rgba($color-paper, 0.82);
  font-size: $font-size-md;
  line-height: $line-height-relaxed;
}

.visual-badge {
  display: inline-flex;
  margin-top: $space-xl;
  padding: 6px 13px;
  border: 1.5px solid $color-paper;
  border-radius: $radius-xs;
  background: rgba($color-ink, 0.18);
  color: $color-yellow;
  font-family: $font-family-mono;
  font-size: 11px;
  font-weight: $font-weight-heavy;
  letter-spacing: 1.4px;
  transform: rotate(-1.5deg);
}

.visual-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $space-md;
  margin-top: $space-xl;
}

.stat-item {
  position: relative;
  padding: 14px 16px 12px 22px;
  border: 2px solid $color-paper;
  border-radius: $radius-md;
  background: $color-bg-card;
  box-shadow: 4px 4px 0 $color-ink;
  color: $color-ink;

  &::before {
    content: '';
    position: absolute;
    top: 8px;
    bottom: 8px;
    left: 11px;
    border-left: 2px dashed rgba($color-ink, 0.28);
  }
}

.stat-num {
  display: block;
  color: $color-red-deep;
  font-family: $font-family-mono;
  font-size: 28px;
  font-weight: $font-weight-heavy;
  line-height: 1;
}

.stat-label {
  display: block;
  margin-top: 5px;
  color: $color-text-secondary;
  font-size: 12px;
  font-weight: $font-weight-bold;
}

.poster-barcode {
  height: 28px;
  margin-top: $space-xl;
  opacity: 0.78;
  background:
    repeating-linear-gradient(
      90deg,
      $color-paper 0 2px,
      transparent 2px 5px,
      $color-paper 5px 6px,
      transparent 6px 12px,
      $color-paper 12px 15px,
      transparent 15px 18px,
      $color-paper 18px 19px,
      transparent 19px 26px
    );
}

.auth-form-area {
  flex: 0 0 440px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-2xl;
  background:
    linear-gradient(118deg, rgba(255, 255, 255, 0.48) 0%, transparent 34%),
    $color-paper;
}

.auth-form-card {
  position: relative;
  width: 100%;
  max-width: 360px;
  padding: 34px 30px 28px;
  border: $border-ink;
  border-radius: $radius-lg;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.48), transparent 38%),
    $color-bg-card;
  box-shadow: $shadow-hover;
}

.form-tape {
  position: absolute;
  top: -13px;
  left: 50%;
  width: 104px;
  height: 25px;
  border: 1.5px solid rgba($color-ink, 0.55);
  background: rgba($color-yellow, 0.88);
  clip-path: polygon(3% 0, 97% 6%, 100% 94%, 0 100%);
  transform: translateX(-50%) rotate(-3deg);
}

.auth-form__header {
  margin-bottom: $space-lg;

  h2 {
    color: $color-text;
    font-family: $font-family-display;
    font-size: 34px;
    font-weight: $font-weight-heavy;
    line-height: 1.2;
  }

  p {
    margin-top: 4px;
    color: $color-text-muted;
    font-size: $font-size-sm;
  }
}

.form-kicker {
  display: inline-flex;
  margin: 0 0 8px;
  padding: 3px 9px;
  border: 1.5px solid $color-red-deep;
  border-radius: $radius-xs;
  color: $color-red-deep !important;
  font-family: $font-family-mono;
  font-size: 11px !important;
  font-weight: $font-weight-heavy;
  letter-spacing: 1.8px;
  transform: rotate(-1.5deg);
}

:deep(.el-form-item__label) {
  color: $color-text;
  font-size: $font-size-sm;
  font-weight: $font-weight-heavy;
}

:deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: $radius-md;

  &:hover {
    background: $color-paper-2;
  }
}

:deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-error {
  margin-bottom: $space-md;
  padding: 10px 14px;
  border: 2px solid $color-ink;
  border-radius: $radius-md;
  background: $color-primary-lighter;
  box-shadow: 3px 3px 0 $color-ink;
  color: $color-red-deep;
  font-size: $font-size-sm;
  font-weight: $font-weight-heavy;
}

.btn-submit {
  width: 100%;
  height: 46px;
  border: $border-ink;
  border-radius: $radius-full;
  background: $color-red;
  box-shadow: $shadow-button;
  color: #fff;
  font-size: $font-size-md;
  font-weight: $font-weight-heavy;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    border-color: $color-ink;
    background: $color-red-deep;
    box-shadow: $shadow-hover;
    transform: translate(-2px, -2px);
  }
}

.auth-form__footer {
  margin-top: 8px;
  padding-top: 14px;
  border-top: 2px dashed rgba($color-ink, 0.18);
  color: $color-text-muted;
  font-size: $font-size-sm;
  text-align: center;

  a {
    color: $color-red-deep;
    font-weight: $font-weight-heavy;

    &:hover {
      background: linear-gradient(transparent 58%, rgba($color-yellow, 0.7) 58%);
    }
  }
}

.forgot-link-row {
  margin: -8px 0 16px;
  text-align: right;

  a {
    color: $color-red-deep;
    font-size: $font-size-sm;
    font-weight: $font-weight-heavy;
  }
}

@media (max-width: 860px) {
  .auth-page {
    max-width: 520px;
    min-height: auto;
    flex-direction: column;
    box-shadow: 6px 6px 0 $color-ink;
  }

  .auth-visual {
    min-height: 270px;
    border-right: 0;
    border-bottom: $border-ink;
    padding: $space-xl;
  }

  .auth-visual h1 {
    font-size: 34px;
  }

  .visual-stats,
  .poster-barcode {
    display: none;
  }

  .auth-form-area {
    flex: 1;
    padding: $space-xl $space-lg;
  }

  .auth-form-card {
    max-width: 100%;
    padding: 32px 22px 24px;
    box-shadow: $shadow-hard;
  }
}
</style>
