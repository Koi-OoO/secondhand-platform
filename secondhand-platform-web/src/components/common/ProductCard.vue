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
      <div v-else class="image-fallback">暂无图片</div>

      <span v-if="imageCount > 1" class="img-badge img-badge--count">
        {{ imageCount }}图
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
        <el-avatar :size="22" :src="product.sellerAvatar" class="seller-avatar">
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
  background: $color-bg-card;
  border: 1px solid $color-border;
  border-radius: $radius-sm;
  overflow: hidden;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    border-color: rgba($color-primary, 0.18);
    box-shadow: 0 10px 24px rgba(45, 36, 32, 0.09);
    transform: translateY(-2px);
  }

  &:active {
    transform: translateY(0);
  }

  &.is-disabled {
    cursor: default;
    opacity: 0.72;

    &:hover {
      border-color: $color-border;
      box-shadow: none;
      transform: none;
    }
  }

  &.is-sold .product-card__img img {
    filter: grayscale(0.3);
  }
}

.product-card__img {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  background: #f3eee8;

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
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: $color-text-muted;
  font-size: 13px;
}

.img-badge {
  position: absolute;
  z-index: 2;
  max-width: calc(100% - 16px);
  padding: 3px 8px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.42);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  backdrop-filter: blur(8px);
}

.img-badge--condition {
  left: 8px;
  top: 8px;
}

.img-badge--count {
  right: 8px;
  top: 8px;
}

.img-overlay {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.42);

  span {
    padding: 7px 18px;
    border: 2px solid rgba(255, 255, 255, 0.72);
    border-radius: 999px;
    color: #fff;
    font-size: 16px;
    font-weight: 800;
  }
}

.product-card__body {
  padding: 10px 11px 12px;
}

.product-card__title {
  display: -webkit-box;
  min-height: 42px;
  margin-bottom: 8px;
  color: $color-text;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-card__meta-row {
  display: flex;
  min-height: 26px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.product-card__price {
  display: inline-flex;
  align-items: baseline;
  color: $color-price;
  font-weight: 900;
  line-height: 1;

  &--muted {
    color: $color-text-muted;
  }
}

.price-symbol {
  margin-right: 1px;
  font-size: 13px;
}

.price-num {
  font-size: 21px;
}

.want-count {
  flex-shrink: 0;
  color: $color-text-muted;
  font-size: 11px;
}

.original-price {
  margin-top: 2px;
  color: $color-text-muted;
  font-size: 12px;
  text-decoration: line-through;
}

.product-card__seller {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px solid $color-border-light;
  cursor: pointer;

  &:hover .seller-name {
    color: $color-primary;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seller-time {
  flex-shrink: 0;
  color: $color-text-muted;
  font-size: 11px;
}
</style>
