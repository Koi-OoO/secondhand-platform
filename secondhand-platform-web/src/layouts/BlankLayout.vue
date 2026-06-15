<template>
  <div class="blank-layout">
    <slot />
  </div>
</template>

<style lang="scss" scoped>
.blank-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 20% 80%, rgba($color-primary, 0.08), transparent 50%),
    radial-gradient(circle at 80% 20%, rgba($color-primary, 0.05), transparent 50%),
    $color-bg;
}
</style>
