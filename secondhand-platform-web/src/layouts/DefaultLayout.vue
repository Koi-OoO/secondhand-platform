<template>
  <div class="default-layout">
    <AppHeader />
    <main class="default-layout__main">
      <slot />
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/common/AppHeader.vue'
import AppFooter from '@/components/common/AppFooter.vue'
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.default-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;

  &__main {
    flex: 1;
    width: 100%;
    min-width: 0;
    margin: 0 auto;
  }
}
</style>
