<template>
  <div class="not-found">
    <div class="not-found__code">404</div>
    <h1>页面未找到</h1>
    <p>你访问的页面不存在或已被移除</p>
    <router-link to="/" class="btn-home">返回首页</router-link>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.not-found {
  text-align: center;
  padding: 80px $space-lg;

  &__code {
    font-size: 100px;
    font-weight: 800;
    background: linear-gradient(135deg, $color-primary, $color-primary-light);
    background-clip: text;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    line-height: 1.2;
  }

  h1 {
    font-size: $font-size-xl;
    margin: $space-md 0 $space-sm;
  }

  p {
    color: $color-text-muted;
    margin-bottom: $space-xl;
  }
}

.btn-home {
  display: inline-block;
  padding: 10px 28px;
  background: $color-primary;
  color: #fff;
  border-radius: $radius-full;
  font-weight: 500;
  transition: all $transition-normal;
  &:hover {
    background: $color-primary-dark;
    box-shadow: $shadow-button;
  }
}
</style>
