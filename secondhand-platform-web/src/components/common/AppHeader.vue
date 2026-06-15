<template>
  <header class="app-header" :class="{ 'app-header--scrolled': scrolled }">
    <div class="app-header__inner container">
      <router-link to="/" class="app-header__logo" aria-label="闲物首页">
        <span class="logo-icon">闲</span>
        <span class="logo-text">闲物</span>
      </router-link>

      <nav class="app-header__nav" aria-label="主导航">
        <router-link to="/" class="nav-link" active-class="active">首页</router-link>
        <router-link to="/products" class="nav-link" active-class="active">商品广场</router-link>
      </nav>

      <button type="button" class="header-search" @click="router.push('/products')">
        <el-icon><Search /></el-icon>
        <span>搜索闲置好物</span>
      </button>

      <div class="app-header__actions">
        <template v-if="userStore.isLoggedIn">
          <router-link v-if="pendingCount.toShip > 0" to="/user/orders?tab=sold" class="order-alert">
            待发货 {{ displayCount(pendingCount.toShip) }}
          </router-link>

          <router-link v-if="pendingCount.toConfirm > 0" to="/user/orders" class="order-alert order-alert--confirm">
            待收货 {{ displayCount(pendingCount.toConfirm) }}
          </router-link>

          <router-link to="/product/publish" class="btn-publish">
            <el-icon><Plus /></el-icon>
            <span>发布</span>
          </router-link>

          <el-dropdown trigger="click" @command="handleCommand">
            <button type="button" class="user-trigger">
              <el-avatar :size="34" :src="userStore.avatar" class="user-trigger__avatar">
                {{ userStore.nickname.charAt(0) }}
              </el-avatar>
              <span class="user-trigger__name">{{ userStore.nickname }}</span>
              <el-icon class="user-trigger__arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="home">
                  <el-icon><Shop /></el-icon>我的主页
                </el-dropdown-item>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="favorites">
                  <el-icon><Star /></el-icon>我的收藏
                </el-dropdown-item>
                <el-dropdown-item command="orders">
                  <el-icon><Document /></el-icon>
                  我的订单
                  <span v-if="orderBadge" class="dropdown-badge">{{ orderBadge }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <template v-else>
          <router-link to="/login" class="btn-text">登录</router-link>
          <router-link to="/register" class="btn-primary-sm">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { Search, Plus, ArrowDown, User, Star, Document, SwitchButton, Shop } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPendingCount } from '@/api/order'

const router = useRouter()
const userStore = useUserStore()
const scrolled = ref(false)
const pendingCount = ref({ toShip: 0, toConfirm: 0 })
const lastToShip = ref(null)
const lastToConfirm = ref(null)
let pendingTimer = null

const orderBadge = computed(() => {
  const total = Number(pendingCount.value.toShip || 0) + Number(pendingCount.value.toConfirm || 0)
  return total > 0 ? displayCount(total) : ''
})

function onScroll() {
  scrolled.value = window.scrollY > 10
}
onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  pollPendingCount()
  pendingTimer = setInterval(pollPendingCount, 30_000)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  clearInterval(pendingTimer)
})

function handleCommand(cmd) {
  switch (cmd) {
    case 'home': if (userStore.userInfo?.id) router.push(`/seller/${userStore.userInfo.id}`); break
    case 'profile': router.push('/user/profile'); break
    case 'favorites': router.push('/user/favorites'); break
    case 'orders': router.push('/user/orders'); break
    case 'logout': userStore.logout(); break
  }
}

function displayCount(count) {
  return count > 99 ? '99+' : String(count)
}

async function pollPendingCount() {
  if (!userStore.isLoggedIn) {
    pendingCount.value = { toShip: 0, toConfirm: 0 }
    lastToShip.value = null
    lastToConfirm.value = null
    return
  }

  try {
    const data = await getPendingCount()
    const toShip = Number(data?.toShip || 0)
    const toConfirm = Number(data?.toConfirm || 0)
    pendingCount.value = {
      toShip,
      toConfirm
    }

    const notifyKey = `ship_notice_seen_${userStore.userInfo?.id || userStore.username}_${toShip}`
    const shouldNotifyInitial = lastToShip.value === null && toShip > 0 && !sessionStorage.getItem(notifyKey)
    const shouldNotifyIncrease = lastToShip.value !== null && toShip > lastToShip.value

    if (shouldNotifyInitial || shouldNotifyIncrease) {
      ElMessage.warning(`你有 ${toShip} 个订单等待发货`)
      sessionStorage.setItem(notifyKey, '1')
    }
    lastToShip.value = toShip

    const confirmNotifyKey = `confirm_notice_seen_${userStore.userInfo?.id || userStore.username}_${toConfirm}`
    const shouldConfirmInitial = lastToConfirm.value === null && toConfirm > 0 && !sessionStorage.getItem(confirmNotifyKey)
    const shouldConfirmIncrease = lastToConfirm.value !== null && toConfirm > lastToConfirm.value

    if (shouldConfirmInitial || shouldConfirmIncrease) {
      ElMessage.info(`你有 ${toConfirm} 个订单等待确认收货`)
      sessionStorage.setItem(confirmNotifyKey, '1')
    }
    lastToConfirm.value = toConfirm
  } catch {
    // 静默失败，避免影响页面主流程
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 68px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(18px) saturate(1.4);
  border-bottom: 1px solid rgba(237, 228, 220, 0.75);
  transition: box-shadow $transition-normal, background $transition-normal;

  &--scrolled {
    background: rgba(255, 255, 255, 0.96);
    box-shadow: 0 8px 24px rgba(45, 36, 32, 0.07);
  }

  &__inner {
    height: 100%;
    display: flex;
    align-items: center;
    gap: 22px;
  }

  &__logo {
    display: flex;
    align-items: center;
    gap: 9px;
    flex-shrink: 0;
  }

  &__nav {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: $space-sm;
    flex-shrink: 0;
  }
}

.logo-icon {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-primary;
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  border-radius: 8px;
  box-shadow: 0 8px 18px rgba($color-primary, 0.25);
}

.logo-text {
  color: $color-text;
  font-size: 22px;
  font-weight: 800;
}

.nav-link {
  position: relative;
  padding: 8px 12px;
  color: $color-text-secondary;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  transition: color $transition-fast, background $transition-fast;

  &:hover {
    color: $color-primary;
    background: $color-bg-hover;
  }

  &.active {
    color: $color-text;

    &::after {
      content: '';
      position: absolute;
      left: 14px;
      right: 14px;
      bottom: 2px;
      height: 3px;
      background: $color-primary;
      border-radius: 999px;
    }
  }
}

.header-search {
  display: flex;
  min-width: 220px;
  max-width: 420px;
  height: 38px;
  flex: 1;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border: 1px solid $color-border;
  border-radius: 999px;
  background: $color-bg-warm;
  color: $color-text-muted;
  cursor: pointer;
  transition: border-color $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    border-color: rgba(224, 122, 95, 0.45);
    background: #fff;
    box-shadow: 0 6px 18px rgba(224, 122, 95, 0.12);
  }
}

.btn-publish {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 18px;
  background: $color-primary;
  color: #fff;
  font-size: $font-size-sm;
  font-weight: 800;
  border-radius: 999px;
  transition: transform $transition-fast, box-shadow $transition-fast;
  box-shadow: 0 8px 18px rgba($color-primary, 0.24);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 10px 22px rgba($color-primary, 0.32);
  }
}

.order-alert {
  display: inline-flex;
  height: 34px;
  align-items: center;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba($color-error, 0.1);
  color: $color-error;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 0 0 1px rgba($color-error, 0.12) inset;

  &:hover {
    background: rgba($color-error, 0.15);
  }

  &--confirm {
    background: rgba($color-info, 0.1);
    color: $color-info;
    box-shadow: 0 0 0 1px rgba($color-info, 0.12) inset;

    &:hover {
      background: rgba($color-info, 0.15);
    }
  }
}

.dropdown-badge {
  display: inline-flex;
  min-width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  margin-left: 8px;
  padding: 0 5px;
  border-radius: 999px;
  background: $color-error;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: $space-sm;
  padding: 3px 10px 3px 3px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: $color-bg-hover;
  }

  &__avatar {
    flex-shrink: 0;
  }

  &__name {
    max-width: 82px;
    color: $color-text;
    font-size: $font-size-sm;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__arrow {
    color: $color-text-muted;
    font-size: 12px;
  }
}

.btn-text,
.btn-primary-sm {
  display: inline-flex;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  border-radius: 999px;
  font-size: $font-size-sm;
  font-weight: 700;
}

.btn-text {
  color: $color-text-secondary;

  &:hover {
    color: $color-primary;
    background: $color-bg-hover;
  }
}

.btn-primary-sm {
  background: $color-text;
  color: #fff;

  &:hover {
    background: $color-primary;
  }
}

@media (max-width: 760px) {
  .app-header {
    height: auto;
  }

  .app-header__inner {
    flex-wrap: wrap;
    gap: 10px;
    padding-top: 10px;
    padding-bottom: 10px;
  }

  .app-header__nav {
    order: 3;
    width: 100%;
  }

  .header-search {
    order: 4;
    min-width: 100%;
  }

  .app-header__actions {
    margin-left: auto;
  }

  .logo-text,
  .user-trigger__name {
    display: none;
  }
}
</style>
