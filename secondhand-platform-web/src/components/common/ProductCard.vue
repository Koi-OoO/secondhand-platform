<template>
  <article
    class="product-card"
    :class="{ 'is-disabled': !isActive, 'is-sold': isSold }"
    @click="isActive && $router.push(`/product/${product.id}`)"
  >
    <div class="product-card__img">
      <img
        v-if="firstImage"
        :src="firstImage"
        :alt="product.title"
        loading="lazy"
      />
      <div v-else class="image-fallback">
        <span>NO PHOTO</span>
        <b>暂无图片</b>
      </div>

      <span v-if="imageCount > 1" class="img-badge img-badge--count">
        {{ imageCount }}P
      </span>
      <span class="img-badge img-badge--condition">
        {{ conditionMap[product.conditionLevel] || '闲置' }}
      </span>

      <div v-if="!isActive" class="img-overlay">
        <span>{{ statusText }}</span>
      </div>
    </div>

    <div class="product-card__body">
      <h4 class="product-card__title">{{ product.title }}</h4>

      <div class="product-card__meta-row">
        <div class="product-card__price" :class="{ 'product-card__price--muted': !isActive }">
          <span class="price-symbol">¥</span>
          <span class="price-num">{{ formatPriceNum(product.price) }}</span>
        </div>
        <span v-if="product.likeCount && isActive" class="want-count">
          {{ product.likeCount }}人想要
        </span>
      </div>

      <div v-if="product.originalPrice" class="original-price">
        原价 ¥{{ formatPriceNum(product.originalPrice) }}
      </div>

      <div
        v-if="showSeller"
        class="product-card__seller"
        @click.stop="goSeller"
      >
        <el-avatar :size="24" :src="product.sellerAvatar" class="seller-avatar">
          {{ (product.sellerNickname || product.sellerUsername || '?')[0] }}
        </el-avatar>
        <span class="seller-name">{{ product.sellerNickname || product.sellerUsername || '用户' }}</span>
        <span class="seller-time">{{ relativeTime(product.createTime) }}</span>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { CONDITION, PRODUCT_STATUS } from '@/utils/constant'
import { formatPrice, relativeTime } from '@/utils/format'

const props = defineProps({
  product: { type: Object, required: true },
  showSeller: { type: Boolean, default: true }
})

const conditionMap = CONDITION
const statusMap = PRODUCT_STATUS
const router = useRouter()

const isActive = computed(() => props.product.status === 1)
const isSold = computed(() => props.product.status === 3)

const statusText = computed(() => {
  return statusMap[props.product.status] || ''
})

const firstImage = computed(() => {
  if (props.product.images?.length && props.product.images[0].url) {
    return props.product.images[0].url
  }
  return ''
})

const imageCount = computed(() => {
  return props.product.images?.length || 0
})

function formatPriceNum(val) {
  if (val == null) return '0.00'
  return formatPrice(val).replace('¥', '')
}
function goSeller() {
  if (props.product.sellerId) {
    router.push(`/seller/${props.product.sellerId}`)
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.product-card {
  position: relative;
  background: $color-bg-card;
  border: $border-ink;
  border-radius: $radius-sm;
  box-shadow: $shadow-hard;
  cursor: pointer;
  overflow: hidden;
  transition: transform $transition-fast, box-shadow $transition-fast, opacity $transition-fast;

  &::before {
    content: '';
    position: absolute;
    top: 9px;
    right: 9px;
    z-index: 4;
    width: 18px;
    height: 18px;
    border-top: 2px solid rgba($color-ink, 0.25);
    border-right: 2px solid rgba($color-ink, 0.25);
    pointer-events: none;
  }

  &:hover {
    box-shadow: $shadow-hover;
    transform: translate(-3px, -3px);
  }

  &:active {
    box-shadow: 1px 1px 0 $color-ink;
    transform: translate(2px, 2px);
  }

  &.is-disabled {
    cursor: default;
    opacity: 0.78;

    &:hover {
      box-shadow: $shadow-hard;
      transform: none;
    }
  }

  &.is-sold .product-card__img img {
    filter: grayscale(0.45);
  }
}

.product-card__img {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  border-bottom: $border-ink;
  background:
    linear-gradient(135deg, rgba($color-yellow, 0.28), transparent 45%),
    $color-paper-3;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background: linear-gradient(118deg, rgba(255, 255, 255, 0.42) 0%, transparent 34%);
    opacity: 0.72;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform $transition-normal;
  }

  .product-card:not(.is-disabled):hover & img {
    transform: scale(1.045);
  }
}

.image-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  align-content: center;
  gap: 8px;
  background:
    repeating-linear-gradient(135deg, rgba($color-ink, 0.055) 0 2px, transparent 2px 10px),
    $color-paper-3;
  color: $color-text-secondary;
  text-align: center;

  span {
    padding: 4px 10px;
    border: 2px solid rgba($color-ink, 0.5);
    border-radius: $radius-xs;
    color: $color-red-deep;
    font-family: $font-family-mono;
    font-size: 11px;
    font-weight: $font-weight-heavy;
    letter-spacing: 1px;
    transform: rotate(-4deg);
  }

  b {
    font-size: $font-size-sm;
    font-weight: $font-weight-bold;
  }
}

.img-badge {
  position: absolute;
  z-index: 2;
  max-width: calc(100% - 16px);
  border: 2px solid $color-ink;
  color: $color-ink;
  font-weight: $font-weight-heavy;
  line-height: 1.35;
}

.img-badge--condition {
  left: 8px;
  top: 8px;
  padding: 3px 9px;
  border-radius: $radius-full;
  background: $color-bg-card;
  box-shadow: 2px 2px 0 $color-ink;
  transform: rotate(-3deg);
  font-size: 11px;
}

.img-badge--count {
  right: 8px;
  top: 8px;
  padding: 2px 7px;
  border-radius: $radius-xs;
  background: $color-ink;
  color: $color-paper;
  font-family: $font-family-mono;
  font-size: 11px;
}

.img-overlay {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba($color-ink, 0.58);

  span {
    padding: 7px 18px;
    border: 2px solid $color-paper;
    border-radius: $radius-full;
    color: $color-paper;
    font-size: $font-size-md;
    font-weight: $font-weight-heavy;
  }
}

.product-card__body {
  padding: 13px 12px 14px;
}

.product-card__title {
  display: -webkit-box;
  min-height: 44px;
  margin-bottom: 11px;
  color: $color-text;
  font-size: 14px;
  font-weight: $font-weight-bold;
  line-height: 1.55;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-card__meta-row {
  display: flex;
  min-height: 30px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.product-card__price {
  position: relative;
  display: inline-flex;
  max-width: 100%;
  align-items: baseline;
  padding: 3px 10px 4px 16px;
  border: 2px solid $color-ink;
  border-radius: 4px 8px 8px 4px;
  background: $color-yellow;
  color: $color-ink;
  font-family: $font-family-mono;
  font-weight: $font-weight-heavy;
  line-height: 1;
  box-shadow: 2px 2px 0 $color-ink;
  overflow: hidden;

  &--muted {
    background: $color-paper-3;
    color: $color-text-muted;
  }

  &::before {
    content: '';
    position: absolute;
    left: 6px;
    top: 50%;
    width: 5px;
    height: 5px;
    border: 1.5px solid $color-ink;
    border-radius: 50%;
    background: $color-bg-card;
    transform: translateY(-50%);
  }
}

.price-symbol {
  margin-right: 2px;
  font-size: 13px;
}

.price-num {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 21px;
}

.want-count {
  flex-shrink: 0;
  max-width: 72px;
  color: $color-text-secondary;
  font-size: 11px;
  font-weight: $font-weight-bold;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.original-price {
  margin-top: 6px;
  color: $color-text-muted;
  font-size: 12px;
  text-decoration: line-through;
}

.product-card__seller {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 11px;
  padding-top: 10px;
  border-top: 2px dashed rgba($color-ink, 0.22);
  cursor: pointer;
  transition: border-color $transition-fast;

  &:hover .seller-name {
    color: $color-red-deep;
  }

  &:hover {
    border-top-color: rgba($color-red, 0.45);
  }
}

.seller-avatar {
  flex-shrink: 0;
}

.seller-name {
  min-width: 0;
  flex: 1;
  color: $color-text-secondary;
  font-size: 12px;
  font-weight: $font-weight-semibold;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seller-time {
  flex-shrink: 0;
  color: $color-text-muted;
  font-size: 11px;
}

@media (max-width: $bp-sm) {
  .product-card {
    box-shadow: 3px 3px 0 $color-ink;
  }

  .product-card__body {
    padding: 10px;
  }

  .product-card__title {
    min-height: 40px;
    font-size: 13px;
    line-height: 1.5;
  }

  .price-num {
    font-size: 18px;
  }

  .want-count {
    display: none;
  }
}
</style>
