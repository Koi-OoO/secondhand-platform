<template>
  <footer class="app-footer">
    <div class="app-footer__inner container">
      <div class="app-footer__brand">
        <span class="footer-logo">闲物</span>
        <p>让闲置物品找到新的主人</p>
      </div>
      <div class="app-footer__links">
        <span>关于我们</span>
        <span>帮助中心</span>
        <span>用户协议</span>
        <span>隐私政策</span>
      </div>
      <div class="app-footer__copy">
        © 2026 闲物二手交易平台
      </div>
    </div>
  </footer>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.app-footer {
  border-top: 1px solid $color-border-light;
  background: $color-bg-card;
  padding: $space-2xl 0;

  &__inner {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $space-md;
  }

  &__brand {
    text-align: center;
    p {
      margin-top: 4px;
      font-size: $font-size-sm;
      color: $color-text-muted;
    }
  }

  &__links {
    display: flex;
    gap: $space-lg;
    font-size: $font-size-sm;
    color: $color-text-secondary;

    span {
      cursor: pointer;
      transition: color $transition-fast;
      &:hover { color: $color-primary; }
    }
  }

  &__copy {
    font-size: $font-size-xs;
    color: $color-text-muted;
  }
}

.footer-logo {
  font-size: $font-size-lg;
  font-weight: 700;
  color: $color-primary;
  letter-spacing: 1px;
}
</style>
