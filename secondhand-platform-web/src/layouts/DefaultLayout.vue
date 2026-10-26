<template>
  <div class="default-layout" :class="{ 'default-layout--chat': isChatPage }">
    <AppHeader />
    <main class="default-layout__main">
      <slot />
    </main>
    <AppFooter v-if="!isChatPage" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/common/AppHeader.vue'
import AppFooter from '@/components/common/AppFooter.vue'

const route = useRoute()
const isChatPage = computed(() => route.name === 'Messages' && route.query.tab !== 'notifications')
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.default-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;

  // 私聊占满可视区域，导航按实际高度占位，其余空间交给聊天区域。
  &--chat {
    height: 100vh;
    height: 100dvh;
    min-height: 0;
    overflow: hidden;

    :deep(.app-header) { flex-shrink: 0; }

    .default-layout__main {
      display: flex;
      min-height: 0;
      flex-direction: column;
    }
  }

  &__main {
    flex: 1;
    width: 100%;
    min-width: 0;
    margin: 0 auto;
  }
}
</style>
