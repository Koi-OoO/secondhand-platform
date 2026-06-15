<template>
  <main class="seller-page">
    <section class="seller-hero">
      <div class="seller-hero__identity">
        <el-avatar :size="72" :src="seller.avatar" class="seller-hero__avatar">
          {{ displayName.charAt(0) }}
        </el-avatar>
        <div class="seller-hero__text">
          <h1>{{ displayName }}</h1>
          <p>@{{ seller.username || 'user' }}</p>
        </div>
      </div>
      <div class="seller-hero__stats">
        <div>
          <strong>{{ seller.creditScore ?? 100 }}</strong>
          <span>信用分</span>
        </div>
        <div>
          <strong>{{ productTotal }}</strong>
          <span>发布商品</span>
        </div>
        <div>
          <strong>{{ evaluationTotal }}</strong>
          <span>收到评价</span>
        </div>
      </div>
    </section>

    <section class="seller-section">
      <div class="section-head">
        <h2>买家评价</h2>
        <span>{{ evaluationTotal }} 条</span>
      </div>
      <div v-if="evaluations.length" class="evaluation-list">
        <article v-for="item in evaluations" :key="item.id" class="evaluation-item">
          <router-link
            v-if="item.productId"
            :to="`/product/${item.productId}`"
            class="evaluation-item__image-link"
          >
            <img
              v-if="item.productImage"
              :src="item.productImage"
              :alt="item.productTitle || '商品图片'"
              class="evaluation-item__image"
            />
            <div v-else class="evaluation-item__image evaluation-item__image--empty">无图</div>
          </router-link>
          <div v-else class="evaluation-item__image evaluation-item__image--empty">无图</div>

          <div class="evaluation-item__body">
            <div class="evaluation-item__top">
              <div class="reviewer">
                <el-avatar :size="28" :src="item.fromUserAvatar">
                  {{ reviewerName(item).charAt(0) }}
                </el-avatar>
                <span>{{ reviewerName(item) }}</span>
              </div>
              <span class="review-time">{{ formatDate(item.createTime) }}</span>
            </div>
            <div class="rating" :aria-label="`${item.rating} 星评价`">
              <span v-for="n in 5" :key="n" :class="{ active: n <= item.rating }">★</span>
            </div>
            <p class="evaluation-item__content">{{ item.content || '买家没有填写文字评价' }}</p>
            <router-link v-if="item.productTitle && item.productId" :to="`/product/${item.productId}`" class="evaluation-item__product">
              {{ item.productTitle }}
            </router-link>
            <span v-else class="evaluation-item__product evaluation-item__product--muted">
              商品信息暂不可用
            </span>
          </div>
        </article>
      </div>
      <div v-else class="empty-row">暂无评价</div>
    </section>

    <section class="seller-section">
      <div class="section-head">
        <h2>商品</h2>
        <span>{{ productTotal }} 件</span>
      </div>
      <div v-if="products.length" class="product-grid">
        <ProductCard
          v-for="item in products"
          :key="item.id"
          :product="item"
          :show-seller="false"
        />
      </div>
      <div v-else class="empty-row">暂无商品</div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ProductCard from '@/components/common/ProductCard.vue'
import { getUserEvaluations } from '@/api/evaluation'
import { getSellerProducts } from '@/api/product'
import { getPublicUserProfile } from '@/api/user'
import { formatDate } from '@/utils/format'

const route = useRoute()
const seller = ref({})
const products = ref([])
const evaluations = ref([])
const productTotal = ref(0)
const evaluationTotal = ref(0)
const loading = ref(false)

const sellerId = computed(() => Number(route.params.id))
const displayName = computed(() => seller.value.nickname || seller.value.username || '用户')

onMounted(loadAll)
watch(() => route.params.id, loadAll)

async function loadAll() {
  if (!sellerId.value || loading.value) return
  loading.value = true
  try {
    const [profile, productPage, evaluationPage] = await Promise.all([
      getPublicUserProfile(sellerId.value),
      getSellerProducts(sellerId.value, { page: 1, size: 8 }),
      getUserEvaluations(sellerId.value, { page: 1, size: 8 })
    ])
    seller.value = profile || {}
    products.value = productPage?.records || []
    productTotal.value = productPage?.total || 0
    evaluations.value = evaluationPage?.records || []
    evaluationTotal.value = evaluationPage?.total || 0
  } finally {
    loading.value = false
  }
}

function reviewerName(item) {
  return item.fromUserNickname || '买家'
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.seller-page {
  width: 100%;
  max-width: 1120px;
  min-height: calc(100vh - 180px);
  margin: 0 auto 56px;
  padding-bottom: 40px;
}

.seller-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 12px;
  padding: 30px 32px;
  overflow: hidden;
  border: 1px solid rgba($color-primary, 0.14);
  border-radius: $radius-xl;
  background:
    radial-gradient(120% 130% at 100% 0%, rgba($color-primary-lighter, 0.9), transparent 50%),
    linear-gradient(135deg, $color-bg-warm, #fff);
  box-shadow: 0 14px 38px rgba(224, 122, 95, 0.08);

  &::after {
    content: '';
    position: absolute;
    top: -90px;
    right: -60px;
    width: 260px;
    height: 260px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba($color-primary-light, 0.20), transparent 70%);
    filter: blur(16px);
    pointer-events: none;
  }

  > * {
    position: relative;
    z-index: 1;
  }
}

.seller-hero__identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 16px;
}

.seller-hero__avatar {
  flex-shrink: 0;
}

.seller-hero__text {
  min-width: 0;

  h1 {
    margin: 0 0 4px;
    color: $color-text;
    font-size: 28px;
    font-weight: 800;
  }

  p {
    margin: 0;
    color: $color-text-muted;
    font-size: 14px;
  }
}

.seller-hero__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(84px, 1fr));
  gap: 12px;

  div {
    min-width: 84px;
    padding: 10px 12px;
    border: 1px solid $color-border-light;
    border-radius: 8px;
    background: #fff;
  }

  strong,
  span {
    display: block;
  }

  strong {
    color: $color-text;
    font-size: 22px;
    font-weight: 850;
  }

  span {
    margin-top: 2px;
    color: $color-text-muted;
    font-size: 12px;
  }
}

.seller-section {
  margin-top: 30px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;

  h2 {
    margin: 0;
    color: $color-text;
    font-size: 20px;
    font-weight: 800;
  }

  span {
    color: $color-text-muted;
    font-size: 13px;
  }
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.evaluation-list {
  display: grid;
  gap: 12px;
}

.evaluation-item {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  border: 1px solid $color-border-light;
  border-radius: 8px;
  background: #fff;
}

.evaluation-item__image-link {
  display: block;
}

.evaluation-item__image {
  width: 92px;
  height: 92px;
  border-radius: 8px;
  object-fit: cover;
  background: $color-bg-warm;

  &--empty {
    display: flex;
    align-items: center;
    justify-content: center;
    color: $color-text-muted;
    font-size: 12px;
  }
}

.evaluation-item__body {
  min-width: 0;
}

.evaluation-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.reviewer {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: $color-text;
  font-size: 14px;
  font-weight: 700;
}

.review-time {
  flex-shrink: 0;
  color: $color-text-muted;
  font-size: 12px;
}

.rating {
  margin-top: 8px;
  color: #d4d0cc;
  font-size: 14px;

  .active {
    color: #f6a400;
  }
}

.evaluation-item__content {
  margin: 7px 0 8px;
  color: $color-text-secondary;
  font-size: 14px;
  line-height: 1.7;
}

.evaluation-item__product {
  color: $color-primary;
  font-size: 13px;
  font-weight: 700;

  &--muted {
    color: $color-text-muted;
  }
}

.empty-row {
  padding: 28px 0;
  color: $color-text-muted;
  text-align: center;
}

@media (max-width: 860px) {
  .seller-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .seller-hero__stats,
  .product-grid {
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .seller-page {
    max-width: none;
    min-height: calc(100vh - 140px);
  }

  .seller-hero__stats,
  .product-grid {
    grid-template-columns: 1fr;
  }

  .evaluation-item {
    grid-template-columns: 72px minmax(0, 1fr);
    padding: 12px;
  }

  .evaluation-item__image {
    width: 72px;
    height: 72px;
  }

  .evaluation-item__top {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
