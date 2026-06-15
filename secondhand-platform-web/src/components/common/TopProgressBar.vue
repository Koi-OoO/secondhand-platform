<template>
  <div class="top-progress" :class="{ 'is-loading': loading }">
    <div class="top-progress__bar" :style="{ width: percent + '%', opacity: loading ? 1 : 0 }"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const percent = ref(0)

let timer = null
let hideTimer = null

function start() {
  loading.value = true
  percent.value = 0
  clearInterval(timer)
  clearTimeout(hideTimer)

  // 模拟进度：快速到 60%，然后缓慢增长
  timer = setInterval(() => {
    if (percent.value < 60) {
      percent.value += 15
    } else if (percent.value < 85) {
      percent.value += 3
    } else if (percent.value < 92) {
      percent.value += 1
    }
  }, 200)
}

function done() {
  percent.value = 100
  clearInterval(timer)
  hideTimer = setTimeout(() => {
    loading.value = false
    percent.value = 0
  }, 300)
}

onMounted(() => {
  router.beforeEach(() => { start() })
  router.afterEach(() => { done() })
  router.onError(() => { done() })
})

onBeforeUnmount(() => {
  clearInterval(timer)
  clearTimeout(hideTimer)
})
</script>

<style lang="scss" scoped>
.top-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  height: 3px;
  pointer-events: none;

  &__bar {
    height: 100%;
    background: linear-gradient(90deg, $color-primary, $color-primary-dark);
    border-radius: 0 2px 2px 0;
    transition: opacity 0.3s ease;
    box-shadow: 0 0 6px rgba($color-primary, 0.4);
  }
}
</style>
