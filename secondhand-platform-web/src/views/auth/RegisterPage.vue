<template>
  <div class="auth-page">
    <!-- 左侧装饰 -->
    <div class="auth-visual">
      <div class="auth-visual__inner">
        <div class="visual-badge">闲物</div>
        <h1>发现你的<br>第一件好物</h1>
        <p>注册闲物，开启闲置物品的循环之旅</p>
        <div class="visual-features">
          <div class="feat-item">
            <el-icon><CircleCheck /></el-icon>
            <span>安全交易保障</span>
          </div>
          <div class="feat-item">
            <el-icon><CircleCheck /></el-icon>
            <span>实名信誉体系</span>
          </div>
          <div class="feat-item">
            <el-icon><CircleCheck /></el-icon>
            <span>免费发布商品</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单 -->
    <div class="auth-form-area">
      <div class="auth-form-card">
        <div class="auth-form__header">
          <h2>创建账号</h2>
          <p>填写信息，加入闲物社区</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @keyup.enter="handleRegister"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请设置用户名"
              :prefix-icon="User"
              size="large"
              maxlength="50"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请设置密码（至少6位）"
              :prefix-icon="Lock"
              show-password
              size="large"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
              size="large"
            />
          </el-form-item>

          <el-form-item label="昵称（选填）" prop="nickname">
            <el-input
              v-model="form.nickname"
              placeholder="给自己取个昵称"
              :prefix-icon="EditPen"
              size="large"
              maxlength="50"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="btn-submit"
              :loading="userStore.loading"
              @click="handleRegister"
            >
              注册
            </el-button>
          </el-form-item>
        </el-form>

        <div class="auth-form__footer">
          已有账号？
          <router-link to="/login">立即登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, EditPen, CircleCheck } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})

const validateConfirmPass = (rule, value, callback) => {
  if (!value) callback(new Error('请再次输入密码'))
  else if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度在 3 到 50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPass, trigger: 'blur' }
  ]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const { confirmPassword, ...userData } = form
  const ok = await userStore.register(userData)
  if (ok) {
    // 注册成功后自动登录
    const loginOk = await userStore.login(form.username, form.password)
    if (loginOk) {
      router.push('/')
    } else {
      router.push('/login')
    }
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
  min-height: 620px;
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
    radial-gradient(80% 80% at 70% 18%, rgba(255, 255, 255, 0.16), transparent 60%),
    linear-gradient(160deg, #C05A3F 0%, #E07A5F 50%, #F2A68D 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-2xl;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    width: 350px;
    height: 350px;
    border-radius: 50%;
    background: rgba(255,255,255,0.06);
    bottom: -80px;
    left: -80px;
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

.visual-features {
  margin-top: $space-xl;
  display: flex;
  flex-direction: column;
  gap: $space-md;

  .feat-item {
    display: flex;
    align-items: center;
    gap: $space-sm;
    font-size: $font-size-md;
    .el-icon { font-size: 18px; opacity: 0.9; }
  }
}

// === 右侧表单 ===
.auth-form-area {
  flex: 0 0 440px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-xl $space-2xl;
}

.auth-form-card {
  width: 100%;
  max-width: 360px;
}

.auth-form__header {
  margin-bottom: $space-lg;
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

.btn-submit {
  width: 100%;
  height: 46px;
  font-size: $font-size-md;
  border-radius: $radius-md;
  margin-top: $space-sm;
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
