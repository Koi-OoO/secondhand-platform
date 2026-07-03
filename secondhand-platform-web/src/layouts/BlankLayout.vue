<template>
  <div class="blank-layout">
    <slot />
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.blank-layout {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    linear-gradient(90deg, rgba($color-ink, 0.035) 1px, transparent 1px) 0 0 / 34px 34px,
    linear-gradient(rgba($color-ink, 0.035) 1px, transparent 1px) 0 0 / 34px 34px,
    $color-bg;
  overflow-x: hidden;
}

.blank-layout::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.055;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='1'/%3E%3C/svg%3E");
}

.blank-layout :deep(> *) {
  position: relative;
  z-index: 1;
}

@media (max-width: $bp-sm) {
  .blank-layout {
    align-items: flex-start;
    padding: 16px;
  }
}
</style>
