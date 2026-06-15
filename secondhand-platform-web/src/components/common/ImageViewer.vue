<template>
  <Teleport to="body">
    <Transition name="viewer-fade">
      <div
        v-if="visible"
        class="image-viewer"
        @click.self="close"
        @touchstart.passive="onTouchStart"
        @touchend="onTouchEnd"
      >
        <!-- 关闭按钮 -->
        <button class="viewer__close" @click="close">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>

        <!-- 计数器 -->
        <div class="viewer__counter" v-if="images.length > 1">
          {{ currentIndex + 1 }} / {{ images.length }}
        </div>

        <!-- 左箭头 -->
        <button
          v-if="images.length > 1"
          class="viewer__arrow viewer__arrow--left"
          @click.stop="prev"
        >
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <polyline points="15 18 9 12 15 6"></polyline>
          </svg>
        </button>

        <!-- 图片 -->
        <div class="viewer__stage">
          <img
            :src="currentSrc"
            :alt="`图片 ${currentIndex + 1}`"
            class="viewer__img"
            :style="imgStyle"
            @touchstart.prevent="onImgTouchStart"
            @touchmove.prevent="onImgTouchMove"
            @touchend="onImgTouchEnd"
          />
        </div>

        <!-- 右箭头 -->
        <button
          v-if="images.length > 1"
          class="viewer__arrow viewer__arrow--right"
          @click.stop="next"
        >
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <polyline points="9 18 15 12 9 6"></polyline>
          </svg>
        </button>

        <!-- Dots -->
        <div class="viewer__dots" v-if="images.length > 1">
          <span
            v-for="(_, idx) in images"
            :key="idx"
            class="viewer__dot"
            :class="{ 'viewer__dot--active': idx === currentIndex }"
            @click.stop="goTo(idx)"
          ></span>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  images: { type: Array, default: () => [] },
  modelValue: { type: Boolean, default: false },
  initialIndex: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed(() => props.modelValue)
const currentIndex = ref(props.initialIndex)
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)

const currentSrc = computed(() => props.images[currentIndex.value] || '')

const imgStyle = computed(() => ({
  transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`,
  transition: isZooming.value ? 'none' : 'transform 0.3s ease'
}))

let touchStartX = 0
let touchStartY = 0
let isZooming = ref(false)
let lastPinchDist = 0
let baseScale = 1

// 键盘导航
function onKeydown(e) {
  if (!visible.value) return
  if (e.key === 'Escape') close()
  if (e.key === 'ArrowLeft') prev()
  if (e.key === 'ArrowRight') next()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

watch(() => props.initialIndex, (v) => { currentIndex.value = v })

function close() {
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  emit('update:modelValue', false)
  document.body.style.overflow = ''
}

// 打开时锁 body
watch(visible, (v) => {
  if (v) {
    currentIndex.value = props.initialIndex
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
})

function prev() {
  if (props.images.length <= 1) return
  resetZoom()
  currentIndex.value = (currentIndex.value - 1 + props.images.length) % props.images.length
}

function next() {
  if (props.images.length <= 1) return
  resetZoom()
  currentIndex.value = (currentIndex.value + 1) % props.images.length
}

function goTo(idx) {
  resetZoom()
  currentIndex.value = idx
}

function resetZoom() {
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
}

// 左右滑动手势
function onTouchStart(e) {
  touchStartX = e.changedTouches[0].clientX
  touchStartY = e.changedTouches[0].clientY
}

function onTouchEnd(e) {
  if (scale.value > 1) return // 缩放时不切换
  const dx = e.changedTouches[0].clientX - touchStartX
  const dy = e.changedTouches[0].clientY - touchStartY
  if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 60) {
    dx > 0 ? prev() : next()
  }
}

// 双指缩放
function getPinchDist(e) {
  if (e.touches.length < 2) return 0
  const dx = e.touches[0].clientX - e.touches[1].clientX
  const dy = e.touches[0].clientY - e.touches[1].clientY
  return Math.sqrt(dx * dx + dy * dy)
}

function onImgTouchStart(e) {
  if (e.touches.length === 2) {
    lastPinchDist = getPinchDist(e)
    baseScale = scale.value
    isZooming.value = true
  }
}

function onImgTouchMove(e) {
  if (e.touches.length === 2) {
    const dist = getPinchDist(e)
    const newScale = Math.max(1, Math.min(4, baseScale * (dist / lastPinchDist)))
    scale.value = newScale
  }
}

function onImgTouchEnd() {
  isZooming.value = false
  if (scale.value < 1.1) resetZoom()
}
</script>

<style lang="scss" scoped>
.image-viewer {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  -webkit-user-select: none;
}

.viewer {
  &__close {
    position: fixed;
    top: 16px;
    right: 16px;
    z-index: 10;
    width: 44px;
    height: 44px;
    border: none;
    border-radius: 50%;
    background: rgba(255,255,255,0.15);
    color: #fff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s;

    &:hover { background: rgba(255,255,255,0.25); }

    svg { width: 24px; height: 24px; }
  }

  &__counter {
    position: fixed;
    top: 20px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    padding: 4px 14px;
    border-radius: 14px;
    background: rgba(0,0,0,0.5);
    color: #fff;
    font-size: 13px;
    font-weight: 500;
  }

  &__stage {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    padding: 80px 60px;
  }

  &__img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
    cursor: grab;
    transition: transform 0.3s ease;
  }

  &__arrow {
    position: fixed;
    top: 50%;
    transform: translateY(-50%);
    z-index: 10;
    width: 48px;
    height: 48px;
    border: none;
    border-radius: 50%;
    background: rgba(255,255,255,0.12);
    color: #fff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s;

    &:hover { background: rgba(255,255,255,0.25); }

    &--left { left: 16px; }
    &--right { right: 16px; }

    svg { width: 28px; height: 28px; }
  }

  &__dots {
    position: fixed;
    bottom: 24px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    gap: 8px;
    z-index: 10;
  }

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: rgba(255,255,255,0.3);
    cursor: pointer;
    transition: all 0.2s;

    &--active {
      width: 24px;
      border-radius: 4px;
      background: #fff;
    }
  }
}

// 过渡动画
.viewer-fade-enter-active,
.viewer-fade-leave-active {
  transition: opacity 0.3s ease;
}

.viewer-fade-enter-from,
.viewer-fade-leave-to {
  opacity: 0;
}
</style>
