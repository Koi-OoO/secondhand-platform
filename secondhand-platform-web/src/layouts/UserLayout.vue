<template>
  <div class="user-layout">
    <AppHeader />
    <div class="user-layout__body container">
      <!-- 侧边栏 -->
      <aside class="user-layout__sidebar">
        <div class="sidebar-card card">
          <!-- 用户信息卡 -->
          <div class="sidebar-user" @click="$router.push('/user/profile')">
            <el-avatar :size="56" :src="userStore.avatar">
              {{ userStore.nickname.charAt(0) }}
            </el-avatar>
            <div class="sidebar-user__info">
              <span class="sidebar-user__name">{{ userStore.nickname }}</span>
              <span class="sidebar-user__id">@{{ userStore.username }}</span>
            </div>
          </div>

          <!-- 菜单 -->
          <nav class="sidebar-nav">
            <router-link to="/user/profile" class="sidebar-nav__item" active-class="is-active">
              <el-icon><User /></el-icon>
              <span>个人资料</span>
            </router-link>
            <router-link to="/user/orders" class="sidebar-nav__item" active-class="is-active">
              <el-icon><Document /></el-icon>
              <span>我的订单</span>
              <span v-if="orderBadge" class="nav-badge">{{ orderBadge }}</span>
            </router-link>
            <router-link to="/user/products" class="sidebar-nav__item" active-class="is-active">
              <el-icon><Goods /></el-icon>
              <span>我的商品</span>
            </router-link>
            <router-link to="/user/favorites" class="sidebar-nav__item" active-class="is-active">
              <el-icon><Star /></el-icon>
              <span>我的收藏</span>
            </router-link>
          </nav>
        </div>
      </aside>

      <!-- 主内容 -->
      <main class="user-layout__content">
        <router-view />
      </main>
    </div>
    <AppFooter />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/stores/user'
import { getPendingCount } from '@/api/order'
import AppHeader from '@/components/common/AppHeader.vue'
import AppFooter from '@/components/common/AppFooter.vue'
import { User, Document, Goods, Star } from '@element-plus/icons-vue'

const userStore = useUserStore()

// 订单待处理计数轮询
const pendingCount = ref({ toShip: 0, toConfirm: 0 })
const orderBadge = computed(() => {
  const total = pendingCount.value.toShip + pendingCount.value.toConfirm
  return total > 0 ? (total > 99 ? '99+' : total) : 0
})

let pollTimer = null

async function pollPending() {
  if (!userStore.isLoggedIn) return
  try {
    pendingCount.value = await getPendingCount()
  } catch { /* 静默失败 */ }
}

onMounted(() => {
  pollPending()
  pollTimer = setInterval(pollPending, 30_000) // 每 30 秒刷新
})

onBeforeUnmount(() => {
  clearInterval(pollTimer)
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.user-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;

  &__body {
    flex: 1;
    display: flex;
    gap: $space-lg;
    padding-top: $space-xl;
    padding-bottom: $space-2xl;
  }

  &__sidebar {
    flex-shrink: 0;
    width: 240px;
  }

  &__content {
    flex: 1;
    min-width: 0;
  }
}

.sidebar-card {
  padding: $space-lg;
  position: sticky;
  top: 84px;
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: $space-md;
  padding-bottom: $space-lg;
  margin-bottom: $space-lg;
  border-bottom: 1px solid $color-border-light;
  cursor: pointer;

  &:hover .sidebar-user__name {
    color: $color-primary;
  }

  &__info {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  &__name {
    font-weight: 600;
    font-size: $font-size-md;
    color: $color-text;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    transition: color $transition-fast;
  }

  &__id {
    font-size: $font-size-sm;
    color: $color-text-muted;
  }
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;

  &__item {
    position: relative;
    display: flex;
    align-items: center;
    gap: $space-md;
    padding: 11px $space-md;
    border-radius: $radius-md;
    font-size: $font-size-md;
    color: $color-text-secondary;
    transition: all $transition-fast;

    .el-icon {
      font-size: 18px;
    }

    &:hover {
      background: $color-bg-hover;
      color: $color-primary;
    }

    &.is-active {
      background: $color-primary-lighter;
      color: $color-primary-dark;
      font-weight: 700;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 18px;
        border-radius: 2px;
        background: $color-primary;
      }
    }
  }
}

.nav-badge {
  margin-left: auto;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: $color-error;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

@media (max-width: 768px) {
  .user-layout__body {
    flex-direction: column;
  }
  .user-layout__sidebar {
    width: 100%;
  }
  .sidebar-card {
    position: static;
  }
  .sidebar-nav {
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
