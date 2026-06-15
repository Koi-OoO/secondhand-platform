<template>
  <div class="auth-page">
    <!-- 左侧装饰区 -->
    <div class="auth-visual">
      <div class="auth-visual__inner">
        <div class="visual-badge">闲物</div>
        <h1>让好物<br>找到新主人</h1>
        <p>每一件闲置，都在等待它的下一段旅程</p>
        <div class="visual-stats">
          <div class="stat-item">
            <span class="stat-num">10,000+</span>
            <span class="stat-label">在线商品</span>
          </div>
          <div class="stat-item">
            <span class="stat-num">50,000+</span>
            <span class="stat-label">成交订单</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="auth-form-area">
      <div class="auth-form-card">
        <div class="auth-form__header">
          <h2>欢迎回来</h2>
          <p>登录你的闲物账号</p>
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
  width: 100%;
  max-width: 960px;
  min-height: 600px;
  display: flex;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-lg;
  background: #fff;
  animation: authIn 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@media (prefers-reduced-motion: reduce) {
  .auth-page { animation: none; }
}

// === 左侧装饰 ===
.auth-visual {
  flex: 1;
  background:
    radial-gradient(80% 80% at 70% 18%, rgba(255, 255, 255, 0.18), transparent 60%),
    linear-gradient(160deg, #E07A5F 0%, #F2A68D 40%, #FDE8E0 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-2xl;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    width: 300px;
    height: 300px;
    border-radius: 50%;
    background: rgba(255,255,255,0.1);
    top: -60px;
    right: -60px;
  }
  &::after {
    content: '';
    position: absolute;
    width: 200px;
    height: 200px;
    border-radius: 50%;
    background: rgba(255,255,255,0.08);
    bottom: -30px;
    left: -40px;
  }

  &__inner {
    position: relative;
    z-index: 1;
    color: #fff;
    h1 {
      font-size: 36px;
      font-weight: 700;
      line-height: 1.3;
      margin: $space-lg 0 $space-md;
      text-shadow: 0 2px 8px rgba(0,0,0,0.08);
    }
    p {
      font-size: $font-size-md;
      opacity: 0.9;
      line-height: 1.6;
    }
  }
}

.visual-badge {
  display: inline-block;
  padding: 6px 16px;
  background: rgba(255,255,255,0.2);
  backdrop-filter: blur(8px);
  border-radius: $radius-full;
  font-size: $font-size-sm;
  font-weight: 600;
  letter-spacing: 2px;
}

.visual-stats {
  display: flex;
  gap: $space-xl;
  margin-top: $space-xl;

  .stat-item {
    display: flex;
    flex-direction: column;
  }
  .stat-num {
    font-size: $font-size-2xl;
    font-weight: 700;
  }
  .stat-label {
    font-size: $font-size-sm;
    opacity: 0.8;
    margin-top: 2px;
  }
}

// === 右侧表单 ===
.auth-form-area {
  flex: 0 0 440px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-2xl;
}

.auth-form-card {
  width: 100%;
  max-width: 360px;
}

.auth-form__header {
  margin-bottom: $space-xl;
  h2 {
    font-size: $font-size-2xl;
    font-weight: 700;
    color: $color-text;
  }
  p {
    margin-top: 4px;
    color: $color-text-muted;
    font-size: $font-size-sm;
  }
}

.auth-error {
  margin-bottom: $space-md;
  padding: 10px 14px;
  border-radius: $radius-md;
  background: rgba($color-error, 0.1);
  border: 1px solid rgba($color-error, 0.25);
  color: $color-error;
  font-size: $font-size-sm;
  font-weight: 600;
}

.btn-submit {
  width: 100%;
  height: 46px;
  font-size: $font-size-md;
  border-radius: $radius-md;
  background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  border-color: transparent;
  box-shadow: $shadow-button;
  transition: transform $transition-fast, box-shadow $transition-fast, filter $transition-fast;

  &:hover {
    transform: translateY(-1px);
    filter: brightness(1.04);
    box-shadow: 0 10px 24px rgba($color-primary, 0.36);
  }
}

.auth-form__footer {
  text-align: center;
  font-size: $font-size-sm;
  color: $color-text-muted;

  a {
    color: $color-primary;
    font-weight: 500;
    &:hover { text-decoration: underline; }
  }
}

@media (max-width: 768px) {
  .auth-visual { display: none; }
  .auth-form-area { flex: 1; }
}
</style>
