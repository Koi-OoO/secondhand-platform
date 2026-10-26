<template>
  <div class="detail-page">
    <template v-if="notFound">
      <div class="empty-state">
        <div class="empty-state__icon">🛒</div>
        <h2>商品不存在</h2>
        <p>该商品可能已下架或已删除。</p>
        <router-link to="/products" class="empty-state__btn">返回商品广场</router-link>
      </div>
    </template>

    <template v-else-if="product">
      <router-link to="/products" class="back-link">
        <el-icon><ArrowLeft /></el-icon>
        返回商品广场
      </router-link>

      <div class="detail-layout">
        <div class="gallery-panel">
          <div class="gallery-main">
            <img v-if="currentImage" :src="currentImage" :alt="product.title" />
            <div v-else class="placeholder">暂无图片</div>
          </div>
          <div v-if="images.length > 1" class="thumb-list">
            <button
              v-for="(image, index) in images"
              :key="image"
              class="thumb"
              :class="{ 'thumb--active': index === currentIndex }"
              @click="selectImage(index)"
            >
              <img :src="image" :alt="product.title" />
            </button>
          </div>
        </div>

        <div class="info-panel">
          <div class="info-card">
            <div class="price-line">
              <span class="price-current">
                <span class="price-symbol">¥</span>{{ formatPriceNum(product.price) }}
              </span>
              <span v-if="product.originalPrice" class="price-origin">原价 {{ formatPrice(product.originalPrice) }}</span>
            </div>

            <h1>{{ product.title }}</h1>

            <div class="meta-row">
              <el-tag size="small" :type="conditionType(product.conditionLevel)" effect="light" round>
                {{ conditionMap[product.conditionLevel] }}
              </el-tag>
              <span class="meta-item">库存 {{ product.stock ?? 0 }}</span>
              <span class="meta-dot">·</span>
              <span class="meta-item">{{ product.viewCount || 0 }} 浏览</span>
              <span class="meta-dot">·</span>
              <span class="meta-item">{{ relativeTime(product.createTime) }}</span>
            </div>

            <div v-if="product.tradeLocation" class="trade-location">
              <el-icon><Location /></el-icon>
              <span>{{ product.tradeLocation }}</span>
            </div>

            <div class="seller-box" @click="$router.push(`/seller/${product.sellerId}`)">
              <el-avatar :size="44" :src="product.sellerAvatar">
                {{ sellerDisplayName[0] }}
              </el-avatar>
              <div class="seller-box__content">
                <div class="seller-box__name">{{ sellerDisplayName }}</div>
                <div class="seller-box__sub">点击查看卖家主页</div>
              </div>
              <el-icon class="seller-box__arrow"><ArrowRight /></el-icon>
            </div>

            <div v-if="!isSeller && product.status === 1 && product.stock > 0" class="buy-box">
              <span class="buy-box__label">购买数量</span>
              <el-input-number
                v-model="purchaseQuantity"
                :min="1"
                :max="Math.max(1, product.stock || 1)"
                :precision="0"
              />
            </div>

            <div class="action-row" :class="{ 'action-row--buyer': !isSeller }">
              <template v-if="isSeller">
                <el-tag :type="statusTagType(product.status)" effect="light" round>{{ statusMap[product.status] }}</el-tag>
                <router-link v-if="product.status === 2" :to="`/product/${product.id}/edit`">
                  <el-button type="primary" plain>编辑商品</el-button>
                </router-link>
              </template>
              <template v-else>
                <el-button size="large" class="product-action product-action--favorite" :class="{ 'is-favorited': isFavorited }"
                  :aria-pressed="isFavorited" :aria-label="isFavorited ? '取消收藏' : '收藏商品'"
                  :title="isFavorited ? '已收藏，点击取消收藏' : '收藏这件商品'" :loading="favoriteLoading" @click="toggleFavorite">
                  <el-icon v-if="!favoriteLoading" class="btn-icon"><StarFilled v-if="isFavorited" /><Star v-else /></el-icon>
                  <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
                </el-button>
                <el-button size="large" class="product-action product-action--contact" :loading="contactLoading" @click="onContact">
                  <el-icon v-if="!contactLoading" class="btn-icon"><ChatDotRound /></el-icon>
                  <span>联系卖家</span>
                </el-button>
                <el-button type="primary" size="large" class="product-action product-action--buy" :disabled="product.status !== 1 || !(product.stock > 0)" :loading="wantLoading" @click="onWant">
                  <span>{{ product.status === 1 ? (product.stock > 0 ? '立即下单' : '暂时无库存') : statusMap[product.status] }}</span>
                  <el-icon v-if="!wantLoading && product.status === 1" class="buy-arrow"><ArrowRight /></el-icon>
                </el-button>
              </template>
            </div>
          </div>
        </div>
      </div>

      <section class="section" v-if="product.description">
        <h3 class="section__title">商品描述</h3>
        <p class="description">{{ product.description }}</p>
      </section>

      <section class="section" v-if="sellerProducts.length">
        <h3 class="section__title">卖家其他在售商品</h3>
        <div class="related-grid">
          <ProductCard v-for="item in sellerProducts" :key="item.id" :product="item" :show-seller="false" />
        </div>
      </section>

      <section class="section" v-if="relatedProducts.length">
        <h3 class="section__title">相关推荐</h3>
        <div class="related-grid">
          <ProductCard v-for="item in relatedProducts" :key="item.id" :product="item" />
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useProductAvailability } from '@/composables/useProductAvailability'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Star, StarFilled, ChatDotRound, Location } from '@element-plus/icons-vue'
import { addFavorite, getFavoriteStatus, getProductDetail, getProductPage, removeFavorite } from '@/api/product'
import { createOrder } from '@/api/order'
import { createConversation } from '@/api/message'
import ProductCard from '@/components/common/ProductCard.vue'
import { useUserStore } from '@/stores/user'
import { CONDITION, PRODUCT_STATUS, PRODUCT_STATUS_COLOR } from '@/utils/constant'
import { formatPrice, relativeTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const product = ref(null)
const notFound = ref(false)
const currentIndex = ref(0)
const currentImage = ref('')
const sellerProducts = ref([])
const relatedProducts = ref([])
const purchaseQuantity = ref(1)
const isFavorited = ref(false)
const favoriteLoading = ref(false)
const wantLoading = ref(false)
const contactLoading = ref(false)

const conditionMap = CONDITION
const statusMap = PRODUCT_STATUS

const images = computed(() => (product.value?.images || []).map(item => item.url))
const sellerDisplayName = computed(() => product.value?.sellerNickname || product.value?.sellerUsername || '用户')
const isSeller = computed(() => userStore.userInfo?.id === product.value?.sellerId)

const { refreshAvailability } = useProductAvailability(() => product.value ? [product.value] : [], rows => {
  const current = rows.find(row => Number(row.id) === Number(product.value?.id))
  if (!current) return
  product.value.stock = current.stock
  product.value.status = current.status
  purchaseQuantity.value = Math.min(purchaseQuantity.value, Math.max(1, current.stock))
})

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id) {
    notFound.value = true
    return
  }

  try {
    const data = await getProductDetail(id)
    product.value = data
    currentImage.value = images.value[0] || ''
    if (userStore.isLoggedIn) {
      loadFavoriteStatus(id)
    }
    await Promise.all([
      loadSellerProducts(data.sellerId, id),
      loadRelatedProducts(data.categoryId, id)
    ])
  } catch {
    notFound.value = true
  }
})

async function loadSellerProducts(sellerId, excludeId) {
  const data = await getProductPage({ sellerId, page: 1, size: 4, sort: 'latest' })
  sellerProducts.value = (data.records || []).filter(item => item.id !== excludeId).slice(0, 4)
}

async function loadRelatedProducts(categoryId, excludeId) {
  const data = await getProductPage({ categoryId, page: 1, size: 4, sort: 'hottest' })
  relatedProducts.value = (data.records || []).filter(item => item.id !== excludeId).slice(0, 4)
}

async function loadFavoriteStatus(id) {
  try {
    isFavorited.value = await getFavoriteStatus(id)
  } catch { /* 未登录或网络异常不影响详情展示 */ }
}

function selectImage(index) {
  currentIndex.value = index
  currentImage.value = images.value[index]
}

function formatPriceNum(val) {
  if (val == null) return '0.00'
  return formatPrice(val).replace('¥', '')
}

function conditionType(level) {
  return { 1: 'success', 2: '', 3: 'warning', 4: 'danger' }[level] || ''
}

function statusTagType(status) {
  return PRODUCT_STATUS_COLOR[status] || 'info'
}

async function toggleFavorite() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  if (favoriteLoading.value || !product.value?.id) {
    return
  }

  favoriteLoading.value = true
  try {
    if (isFavorited.value) {
      await removeFavorite(product.value.id)
      isFavorited.value = false
      product.value.likeCount = Math.max(0, (product.value.likeCount || 1) - 1)
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(product.value.id)
      isFavorited.value = true
      product.value.likeCount = (product.value.likeCount || 0) + 1
      ElMessage.success('收藏成功')
    }
  } finally {
    favoriteLoading.value = false
  }
}

async function onWant() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  const address = product.value.tradeLocation?.trim()
  if (!address) {
    ElMessage.warning('卖家尚未设置面交地点，请联系卖家补充后再下单')
    return
  }
  const confirmed = await ElMessageBox.confirm(
    `面交地点：${address}。下单后按此地点交易；如需调整，请通过站内聊天协商。`,
    '确认下单',
    { confirmButtonText: '确认下单', cancelButtonText: '取消', type: 'info' }
  ).then(() => true).catch(() => false)
  if (!confirmed || wantLoading.value) return

  wantLoading.value = true
  try {
    await createOrder(product.value.id, purchaseQuantity.value, address.trim())
    ElMessage.success('下单成功')
  } finally {
    wantLoading.value = false
    refreshAvailability()
  }
}

async function onContact() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  if (!product.value?.sellerId) {
    ElMessage.warning('卖家信息不存在')
    return
  }
  if (contactLoading.value) {
    return
  }

  contactLoading.value = true
  try {
    await createConversation(product.value.sellerId, product.value.id, { skipErrorMessage: true })
  } catch {
    ElMessage.warning('会话暂时未持久化，请确认后端数据库已更新')
  } finally {
    contactLoading.value = false
  }

  router.push({
    path: '/messages',
    query: {
      peerId: product.value.sellerId,
      productId: product.value.id
    }
  })
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.detail-page {
  padding: 18px 0 $space-2xl;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 14px;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  font-weight: 600;
  transition: color $transition-fast;

  &:hover {
    color: $color-primary;
  }
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(320px, 500px) 1fr;
  gap: $space-xl;
  align-items: start;
}

// === 画廊 ===
.gallery-main {
  aspect-ratio: 1;
  border-radius: $radius-lg;
  overflow: hidden;
  background: #f3eee8;
  border: 1px solid $color-border-light;
  box-shadow: $shadow-card;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}

.placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-text-muted;
  font-size: $font-size-sm;
}

.thumb-list {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.thumb {
  width: 64px;
  height: 64px;
  border: 2px solid $color-border;
  border-radius: $radius-md;
  padding: 0;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
  transition: border-color $transition-fast, transform $transition-fast;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &:hover {
    border-color: $color-primary-light;
  }

  &--active {
    border-color: $color-primary;
    box-shadow: 0 0 0 3px rgba($color-primary, 0.12);
  }
}

// === 信息面板 ===
.info-panel {
  position: sticky;
  top: 84px;
}

.info-card {
  padding: $space-xl;
  background: $color-bg-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  box-shadow: $shadow-md;
}

.info-panel h1 {
  margin: 12px 0;
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  line-height: 1.4;
  color: $color-text;
}

.price-line {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.price-current {
  display: inline-flex;
  align-items: baseline;
  font-size: 34px;
  color: $color-price;
  font-weight: 900;
  line-height: 1.1;
}

.price-symbol {
  margin-right: 1px;
  font-size: 20px;
}

.price-origin {
  color: $color-text-muted;
  text-decoration: line-through;
  font-size: $font-size-sm;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: $color-text-secondary;
  font-size: $font-size-sm;
}

.meta-dot {
  color: $color-text-muted;
}

.seller-box {
  display: flex;
  align-items: center;
  gap: 12px;
  background: $color-bg-warm;
  border: 1px solid $color-border-light;
  padding: 12px 14px;
  border-radius: $radius-md;
  margin: $space-lg 0;
  cursor: pointer;
  transition: border-color $transition-fast, background $transition-fast;

  &:hover {
    border-color: rgba($color-primary, 0.25);
    background: $color-bg-hover;

    .seller-box__arrow {
      color: $color-primary;
      transform: translateX(2px);
    }
  }

  &__content {
    flex: 1;
    min-width: 0;
  }

  &__name {
    font-weight: $font-weight-semibold;
    color: $color-text;
  }

  &__sub {
    color: $color-text-muted;
    font-size: $font-size-xs;
    margin-top: 2px;
  }

  &__arrow {
    color: $color-text-muted;
    transition: color $transition-fast, transform $transition-fast;
  }
}

.buy-box {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: $space-lg;

  &__label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
}

.action-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;

  &--buyer {
    display: grid;
    grid-template-columns: minmax(0, .72fr) minmax(0, 1fr) minmax(0, 1.3fr);
    align-items: stretch;
  }
}

.action-row--buyer .product-action {
  width: 100%;
  min-width: 0;
  height: 52px;
  margin: 0;
  padding: 0 14px;
  border-radius: $radius-md;
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  white-space: nowrap;
  transform: none;
  transition: background $transition-fast, border-color $transition-fast, box-shadow $transition-fast, transform $transition-fast;

  &:focus-visible {
    outline: 3px solid $color-ink;
    outline-offset: 4px;
  }
}

.action-row--buyer .product-action--favorite {
  color: $color-text-secondary;
  background: transparent;
  border: 1px solid $color-border-light;
  box-shadow: none;

  &:hover:not(:disabled) {
    color: $color-ink;
    background: $color-paper-3;
    border-color: $color-ink-2;
  }

  &.is-favorited {
    color: #795415;
    background: #fff3d3;
    border-color: #e3c784;
  }
}

.action-row--buyer .product-action--contact {
  color: $color-ink;
  background: $color-paper-2;
  border: 2px solid $color-ink;
  box-shadow: none;

  &:hover:not(:disabled) {
    background: $color-paper-3;
    border-color: $color-ink;
  }
}

.action-row--buyer .product-action--buy {
  color: #fff;
  background: $color-primary;
  border: 2px solid $color-ink;
  box-shadow: 2px 3px 0 $color-ink;

  &:hover:not(:disabled) {
    background: $color-primary-dark;
    border-color: $color-ink;
    transform: translateY(-1px);
    box-shadow: 2px 4px 0 $color-ink;
  }

  &:active:not(:disabled) {
    transform: translateY(2px);
    box-shadow: none;
  }

  &:disabled {
    color: $color-text-muted;
    background: $color-paper-3;
    border-color: $color-border-light;
    box-shadow: none;
  }
}

.btn-icon { margin-right: 6px; font-size: 18px; }
.buy-arrow { margin-left: 8px; font-size: 16px; }

@media (max-width: 1120px) {
  .action-row--buyer { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .product-action--buy { grid-column: 1 / -1; }
}

@media (prefers-reduced-motion: reduce) {
  .action-row--buyer .product-action { transition: none; }
  .action-row--buyer .product-action--buy:hover:not(:disabled),
  .action-row--buyer .product-action--buy:active:not(:disabled) { transform: none; }
}

// === 区块 ===
.section {
  margin-top: $space-xl;
  padding: $space-lg;
  background: $color-bg-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
}

.section__title {
  position: relative;
  margin-bottom: 14px;
  padding-left: 12px;
  font-size: $font-size-lg;
  font-weight: $font-weight-bold;
  color: $color-text;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 4px;
    height: 16px;
    border-radius: 999px;
    background: $color-primary;
  }
}

.description {
  margin: 0;
  white-space: pre-wrap;
  line-height: $line-height-relaxed;
  color: $color-text-secondary;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

// === 空状态 ===
.empty-state {
  text-align: center;
  padding: 90px 20px;

  &__icon {
    font-size: 52px;
    margin-bottom: 10px;
  }

  h2 {
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    color: $color-text;
    margin-bottom: 6px;
  }

  p {
    color: $color-text-muted;
    margin-bottom: $space-lg;
  }

  &__btn {
    display: inline-flex;
    padding: 10px 24px;
    background: $color-primary;
    color: #fff;
    border-radius: $radius-full;
    font-weight: $font-weight-semibold;
    box-shadow: 0 8px 18px rgba($color-primary, 0.24);
    transition: transform $transition-fast;

    &:hover {
      transform: translateY(-1px);
    }
  }
}

@media (max-width: 900px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .info-panel {
    position: static;
  }

  .related-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .info-card { padding: 20px; }
  .action-row--buyer .product-action { height: 48px; padding: 0 10px; }
}
</style>
