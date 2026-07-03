<template>
  <header class="app-header" :class="{ 'app-header--scrolled': scrolled }">
    <div class="market-ticker" aria-hidden="true">
      <div class="market-ticker__track">
        <div v-for="copy in 2" :key="copy" class="market-ticker__group">
          <span v-for="item in tickerItems" :key="`${copy}-${item}`" class="market-ticker__item">
            {{ item }}
          </span>
        </div>
      </div>
    </div>

    <div class="app-header__inner container">
      <router-link to="/" class="app-header__logo" aria-label="校园闲物首页">
        <span class="logo-icon">闲</span>
        <span class="logo-text">
          <b>校园闲物</b>
          <small>CAMPUS FLEA MARKET</small>
        </span>
      </router-link>

      <nav class="app-header__nav" aria-label="主导航">
        <router-link to="/" class="nav-link" active-class="active">首页</router-link>
        <router-link to="/products" class="nav-link" active-class="active">商品广场</router-link>
      </nav>

      <form class="header-search" role="search" @submit.prevent="handleHeaderSearch">
        <el-icon class="header-search__icon"><Search /></el-icon>
        <input
          v-model="headerKeyword"
          type="search"
          placeholder="搜索 iPhone、教材、自行车..."
          aria-label="搜索商品"
        />
        <button
          v-if="headerKeyword"
          type="button"
          class="header-search__clear"
          aria-label="清空搜索"
          @click="clearHeaderSearch"
        >
          ×
        </button>
        <button type="submit" class="header-search__submit" aria-label="提交搜索">
          <el-icon><Search /></el-icon>
        </button>
      </form>

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
            <span>发布闲置</span>
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
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { Search, Plus, ArrowDown, User, Star, Document, SwitchButton, Shop } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPendingCount } from '@/api/order'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const scrolled = ref(false)
const headerKeyword = ref('')
const pendingCount = ref({ toShip: 0, toConfirm: 0 })
const lastToShip = ref(null)
const lastToConfirm = ref(null)
let pendingTimer = null

const tickerItems = [
  '今日新上 128 件好物',
  '数码专区最低 3 折起',
  '面交请选择公共区域',
  '闲置换现金',
  '支持担保交易',
  '让好物继续流转',
  '本周成交 356 单'
]

const orderBadge = computed(() => {
  const total = Number(pendingCount.value.toShip || 0) + Number(pendingCount.value.toConfirm || 0)
  return total > 0 ? displayCount(total) : ''
})

watch(
  () => route.query.keyword,
  keyword => {
    headerKeyword.value = typeof keyword === 'string' ? keyword : ''
  },
  { immediate: true }
)

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

function handleHeaderSearch() {
  const keyword = headerKeyword.value.trim()
  if (!keyword) {
    router.push('/products')
    return
  }
  router.push({ path: '/products', query: { keyword } })
}

function clearHeaderSearch() {
  headerKeyword.value = ''
  if (route.path === '/products' && route.query.keyword) {
    const { keyword, page, ...restQuery } = route.query
    router.replace({ path: '/products', query: restQuery })
  }
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
  background: $color-paper;
  border-bottom: $border-ink;
  backdrop-filter: blur(10px);
  transition: box-shadow $transition-normal, background $transition-normal;

  &--scrolled {
    background: rgba($color-paper, 0.96);
    box-shadow: 0 5px 0 rgba($color-ink, 0.12);
  }

  &__inner {
    min-height: 68px;
    display: flex;
    align-items: center;
    gap: 24px;
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

.market-ticker {
  position: relative;
  overflow: hidden;
  border-bottom: $border-ink;
  background: $color-ink;
  color: $color-paper;
  font-family: $font-family-mono;
  font-size: 12.5px;
  font-weight: $font-weight-heavy;
  letter-spacing: 0.5px;
  line-height: 1;
  box-shadow: inset 0 -1px 0 rgba($color-paper, 0.12);

  &::before,
  &::after {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    z-index: 2;
    width: 48px;
    pointer-events: none;
  }

  &::before {
    left: 0;
    background: linear-gradient(90deg, $color-ink, rgba($color-ink, 0));
  }

  &::after {
    right: 0;
    background: linear-gradient(270deg, $color-ink, rgba($color-ink, 0));
  }
}

.market-ticker__track {
  display: flex;
  width: max-content;
  will-change: transform;
  animation: tickerMove 13.5s linear infinite;
  transform: translate3d(0, 0, 0);
}

.market-ticker__group {
  display: flex;
  min-width: max(100vw, 980px);
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-around;
}

.market-ticker__item {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 7px 18px 8px;
  white-space: nowrap;
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.18);

  &::after {
    content: '';
    width: 7px;
    height: 7px;
    margin-left: 28px;
    border: 1.5px solid $color-ink;
    border-radius: 50%;
    background: $color-yellow;
    color: $color-yellow;
    box-shadow: 0 0 0 1px rgba($color-paper, 0.34);
  }
}

@keyframes tickerMove {
  to {
    transform: translate3d(-50%, 0, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .market-ticker__track {
    animation-duration: 0.01ms;
    animation-iteration-count: 1;
  }
}

.logo-icon {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: $border-ink;
  border-radius: $radius-md;
  background: $color-red;
  color: #fff;
  box-shadow: 3px 3px 0 $color-ink;
  font-family: $font-family-display;
  font-size: 24px;
  font-weight: $font-weight-heavy;
  transform: rotate(-6deg);
  transition: transform $transition-fast;
}

.app-header__logo:hover .logo-icon {
  transform: rotate(4deg) scale(1.04);
}

.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;

  b {
    color: $color-text;
    font-family: $font-family-display;
    font-size: 22px;
    font-weight: $font-weight-heavy;
  }

  small {
    color: $color-text-secondary;
    font-family: $font-family-mono;
    font-size: 9px;
    font-weight: 700;
    letter-spacing: 1.6px;
  }
}

.nav-link {
  position: relative;
  padding: 7px 14px;
  border: 2px solid transparent;
  border-radius: $radius-full;
  color: $color-text-secondary;
  font-size: 14px;
  font-weight: $font-weight-bold;
  transition: color $transition-fast, background $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    color: $color-text;
    background: rgba($color-ink, 0.06);
  }

  &.active {
    color: $color-text;
    border-color: $color-ink;
    background: $color-yellow;
    box-shadow: 2px 2px 0 $color-ink;
  }
}

.header-search {
  position: relative;
  display: flex;
  min-width: 300px;
  max-width: 470px;
  height: 42px;
  flex: 1;
  align-items: center;
  gap: 9px;
  padding: 0 7px 0 16px;
  border: $border-ink;
  border-radius: 999px;
  background: $color-bg-card;
  color: $color-text-muted;
  font-weight: $font-weight-semibold;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;
  isolation: isolate;

  &:hover {
    background: $color-paper-2;
    box-shadow: $shadow-hard;
    transform: translate(-1px, -1px);
  }

  &::after {
    content: '';
    position: absolute;
    inset: 3px 44px 3px 45px;
    z-index: 0;
    border-radius: inherit;
    background: linear-gradient(90deg, transparent, rgba($color-yellow, 0.18), transparent);
    opacity: 0;
    pointer-events: none;
    transition: opacity $transition-fast;
  }

  &:focus-within::after {
    opacity: 1;
  }

  &:focus-within {
    background: $color-paper-2;
    box-shadow: $shadow-hard;
    transform: translate(-1px, -1px);
  }

  &__icon {
    flex-shrink: 0;
    color: $color-red;
    font-size: 17px;
  }

  input {
    min-width: 0;
    flex: 1;
    border: 0;
    outline: 0;
    background: transparent;
    color: $color-text;
    font-size: $font-size-sm;
    font-weight: $font-weight-semibold;
    overflow: hidden;
    text-overflow: ellipsis;
    appearance: none;
    line-height: 1.3;

    &::-webkit-search-cancel-button {
      display: none;
    }

    &::placeholder {
      color: $color-text-muted;
    }
  }
}

.header-search__clear {
  display: inline-flex;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: 1.5px dashed rgba($color-ink, 0.42);
  border-radius: 50%;
  background: $color-paper;
  color: $color-text-secondary;
  font-size: 16px;
  font-weight: $font-weight-heavy;
  line-height: 1;
  transition: background $transition-fast, color $transition-fast, border-color $transition-fast;

  &:hover {
    border-style: solid;
    border-color: $color-ink;
    background: $color-yellow;
    color: $color-text;
  }
}

.header-search__submit {
  display: inline-flex;
  width: 31px;
  height: 31px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: 2px solid $color-ink;
  border-radius: 50%;
  background: $color-red;
  box-shadow: 2px 2px 0 $color-ink;
  color: #fff;
  font-size: 14px;
  font-weight: $font-weight-heavy;
  line-height: 1;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    background: $color-red-deep;
    box-shadow: 3px 3px 0 $color-ink;
    transform: translate(-1px, -1px);
  }

  &:active {
    box-shadow: 1px 1px 0 $color-ink;
    transform: translate(1px, 1px);
  }
}

.btn-publish {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 18px;
  border: $border-ink;
  border-radius: $radius-full;
  background: $color-primary;
  color: #fff;
  font-size: $font-size-sm;
  font-weight: $font-weight-heavy;
  transition: transform $transition-fast, box-shadow $transition-fast;
  box-shadow: $shadow-button;
  white-space: nowrap;

  &:hover {
    transform: translate(-2px, -2px);
    box-shadow: $shadow-hover;
  }
}

.order-alert {
  display: inline-flex;
  height: 34px;
  align-items: center;
  padding: 0 12px;
  border: 2px solid $color-ink;
  border-radius: 999px;
  background: $color-primary-lighter;
  color: $color-red-deep;
  font-size: 12px;
  font-weight: $font-weight-heavy;
  box-shadow: 2px 2px 0 $color-ink;
  white-space: nowrap;

  &:hover {
    background: $color-yellow;
  }

  &--confirm {
    background: #D8F0EA;
    color: $color-teal;

    &:hover {
      background: $color-yellow;
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
  border: 1.5px solid $color-ink;
  background: $color-yellow;
  color: $color-ink;
  font-size: 11px;
  font-weight: $font-weight-heavy;
  line-height: 1;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: $space-sm;
  padding: 3px 10px 3px 3px;
  border: $border-ink;
  border-radius: 999px;
  background: $color-bg-card;
  cursor: pointer;
  box-shadow: 3px 3px 0 $color-ink;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    background: $color-yellow;
    transform: translate(-1px, -1px);
    box-shadow: $shadow-hard;
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
  border: 2px solid transparent;
  border-radius: 999px;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
}

.btn-text {
  color: $color-text-secondary;

  &:hover {
    color: $color-text;
    background: $color-bg-hover;
  }
}

.btn-primary-sm {
  border-color: $color-ink;
  background: $color-yellow;
  color: $color-text;
  box-shadow: 3px 3px 0 $color-ink;

  &:hover {
    background: $color-primary;
    color: #fff;
  }
}

@media (max-width: 760px) {
  .market-ticker {
    font-size: 11px;

    &::before,
    &::after {
      width: 24px;
    }
  }

  .market-ticker__track {
    animation-duration: 13s;
  }

  .market-ticker__group {
    min-width: max(100vw, 720px);
    justify-content: flex-start;
  }

  .market-ticker__item {
    padding-right: 12px;
    padding-left: 12px;

    &::after {
      margin-left: 18px;
    }
  }

  .app-header__inner {
    flex-wrap: wrap;
    gap: 10px;
    padding-top: 10px;
    padding-bottom: 10px;
  }

  .app-header__nav {
    order: 3;
    width: calc(100% + 6px);
    margin: 0 -3px;
    overflow-x: auto;
    padding-bottom: 2px;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .header-search {
    order: 4;
    min-width: 100%;
    height: 44px;
  }

  .app-header__actions {
    margin-left: auto;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .logo-text,
  .user-trigger__name {
    display: none;
  }

  .btn-publish {
    padding: 0 14px;
  }
}
</style>
