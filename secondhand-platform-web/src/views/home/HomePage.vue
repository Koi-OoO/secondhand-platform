<template>
  <div class="home-page">
    <section class="home-hero">
      <div class="home-hero__glow" aria-hidden="true"></div>

      <div class="home-hero__content">
        <p class="home-hero__eyebrow">校园与同城闲置交易</p>
        <h1>把用不上的好物，流转给<span class="home-hero__hl">正需要的人</span></h1>
        <p class="home-hero__desc">
          搜数码、教材、通勤装备和生活小物，快速发现附近正在出售的闲置好物。
        </p>

        <form class="hero-search" @submit.prevent="handleHeroSearch">
          <el-icon class="hero-search__icon"><Search /></el-icon>
          <input
            v-model="heroKeyword"
            class="hero-search__input"
            type="search"
            placeholder="搜索手机、电脑、教材、自行车..."
            aria-label="搜索商品"
          />
          <button class="hero-search__button" type="submit">搜索</button>
        </form>

        <div class="hot-keywords">
          <span class="hot-keywords__label">热门</span>
          <button
            v-for="word in hotKeywords"
            :key="word"
            type="button"
            @click.stop="$router.push(`/products?keyword=${encodeURIComponent(word)}`)"
          >
            {{ word }}
          </button>
        </div>
      </div>

      <div class="home-hero__panel">
        <div class="publish-card" @click="$router.push('/product/publish')">
          <span class="publish-card__label">快速发布</span>
          <strong>闲置换现金</strong>
          <p class="publish-card__hint">三步上架，轻松出闲置</p>
          <span class="publish-card__cta">发布商品 →</span>
        </div>
        <div class="hero-stat">
          <span>{{ latestProducts.length || 0 }}</span>
          <p>今日新上</p>
        </div>
        <div class="hero-stat">
          <span>{{ categories.length || 0 }}</span>
          <p>交易分类</p>
        </div>
      </div>
    </section>

    <section class="section category-section">
      <div class="section__header">
        <div>
          <p class="section__label">Category</p>
          <h2>按分类逛一逛</h2>
        </div>
        <router-link to="/products" class="see-all">查看全部</router-link>
      </div>

      <div class="category-grid">
        <button
          v-for="cat in categories"
          :key="cat.id"
          type="button"
          class="category-item"
          @click="$router.push(`/products?categoryId=${cat.id}`)"
        >
          <span class="category-icon">{{ cat.icon }}</span>
          <span class="category-name">{{ cat.name }}</span>
        </button>
        <button type="button" class="category-item category-item--all" @click="$router.push('/products')">
          <span class="category-icon">+</span>
          <span class="category-name">全部商品</span>
        </button>
      </div>
    </section>

    <section class="section channels-section">
      <div class="channel-card channel-card--primary" @click="$router.push('/products?sort=latest')">
        <span>最新发布</span>
        <strong>刚刚上架的好物</strong>
        <p>抢先发现低价闲置</p>
        <span class="channel-card__arrow" aria-hidden="true">→</span>
      </div>
      <div class="channel-card" @click="$router.push('/products?sort=hottest')">
        <span>大家想要</span>
        <strong>高人气商品</strong>
        <p>看看别人都在关注什么</p>
        <span class="channel-card__arrow" aria-hidden="true">→</span>
      </div>
      <div class="channel-card" @click="$router.push('/products?sort=price_asc')">
        <span>低价淘</span>
        <strong>预算友好</strong>
        <p>从便宜实用开始逛</p>
        <span class="channel-card__arrow" aria-hidden="true">→</span>
      </div>
    </section>

    <section class="section">
      <div class="section__header">
        <div>
          <p class="section__label">New arrivals</p>
          <h2>最新发布</h2>
        </div>
        <router-link to="/products?sort=latest" class="see-all">更多</router-link>
      </div>
      <div class="scroll-row">
        <article
          v-for="item in latestProducts"
          :key="item.id"
          class="scroll-card"
          @click="$router.push(`/product/${item.id}`)"
        >
          <div class="scroll-card__img">
            <img v-if="getFirstImage(item)" :src="getFirstImage(item)" :alt="item.title" loading="lazy" />
            <div v-else class="image-fallback">暂无图片</div>
          </div>
          <span class="scroll-card__price">¥{{ formatPriceNum(item.price) }}</span>
          <span class="scroll-card__title">{{ item.title }}</span>
        </article>
        <div v-if="!latestProducts.length" class="empty-inline">暂无商品</div>
      </div>
    </section>

    <section class="section">
      <div class="section__header">
        <div>
          <p class="section__label">For you</p>
          <h2>猜你喜欢</h2>
        </div>
        <router-link to="/products?sort=hottest" class="see-all">进入商品广场</router-link>
      </div>
      <div class="waterfall">
        <ProductCard
          v-for="item in recommendProducts"
          :key="item.id"
          :product="item"
          :show-seller="true"
        />
      </div>
      <div v-if="!recommendProducts.length" class="empty-hint">
        <p>还没有商品，成为第一个发布的人吧！</p>
        <router-link to="/product/publish">去发布</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { getCategories } from '@/api/category'
import { getProductPage } from '@/api/product'
import { formatPrice } from '@/utils/format'
import ProductCard from '@/components/common/ProductCard.vue'

const router = useRouter()
const heroKeyword = ref('')
const latestProducts = ref([])
const recommendProducts = ref([])
const categories = ref([])
const hotKeywords = ['iPhone', '平板', '考研教材', '自行车', '耳机']

onMounted(async () => {
  try {
    const data = await getCategories()
    categories.value = (data || []).map(cat => ({
      ...cat,
      icon: categoryIcons[cat.name] || '📦'
    }))
  } catch { /* ignore */ }

  try {
    const data = await getProductPage({ page: 1, size: 10, sort: 'latest' })
    latestProducts.value = data.records || []
  } catch { /* ignore */ }

  try {
    const data = await getProductPage({ page: 1, size: 12, sort: 'hottest' })
    recommendProducts.value = data.records || []
  } catch { /* ignore */ }
})

function getFirstImage(item) {
  return item.images?.length ? item.images[0].url : ''
}

function formatPriceNum(val) {
  return formatPrice(val).replace('¥', '')
}

function handleHeroSearch() {
  const keyword = heroKeyword.value.trim()
  if (!keyword) {
    router.push('/products')
    return
  }
  router.push({ path: '/products', query: { keyword } })
}

const categoryIcons = {
  '手机数码': '📱',
  '电脑办公': '💻',
  '家用电器': '🔌',
  '服饰鞋包': '👟',
  '图书教材': '📚',
  '运动户外': '🏀',
  '其他': '📦'
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.home-page {
  padding-bottom: $space-2xl;
}

// === 入场动画：自上而下错落浮现 ===
@keyframes riseIn {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}

.home-page > section {
  animation: riseIn 0.6s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.home-page > section:nth-child(1) { animation-delay: 0.04s; }
.home-page > section:nth-child(2) { animation-delay: 0.12s; }
.home-page > section:nth-child(3) { animation-delay: 0.20s; }
.home-page > section:nth-child(4) { animation-delay: 0.28s; }
.home-page > section:nth-child(5) { animation-delay: 0.36s; }

// === Hero ===
.home-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 312px;
  gap: 28px;
  margin: 8px 0 36px;
  padding: 44px 40px;
  overflow: hidden;
  border-radius: $radius-xl;
  border: 1px solid rgba($color-primary, 0.14);
  background:
    radial-gradient(130% 120% at 0% 0%, rgba($color-primary-light, 0.20), transparent 52%),
    radial-gradient(120% 130% at 100% 8%, rgba($color-primary-lighter, 0.9), transparent 46%),
    linear-gradient(180deg, $color-bg-warm 0%, #ffffff 100%);
  box-shadow: 0 18px 48px rgba(224, 122, 95, 0.10);
}

.home-hero__glow {
  position: absolute;
  top: -120px;
  right: -80px;
  width: 360px;
  height: 360px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba($color-primary-light, 0.30), transparent 70%);
  filter: blur(20px);
  pointer-events: none;
}

.home-hero__content {
  position: relative;
  z-index: 1;
}

.home-hero__eyebrow,
.section__label {
  display: inline-flex;
  align-items: center;
  margin-bottom: 12px;
  color: $color-primary-dark;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

.home-hero__eyebrow::before {
  content: '';
  width: 22px;
  height: 2px;
  margin-right: 9px;
  border-radius: 2px;
  background: $color-primary;
}

.home-hero h1 {
  max-width: 660px;
  margin: 0 0 14px;
  color: $color-text;
  font-size: 40px;
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: -0.5px;
}

.home-hero__hl {
  background: linear-gradient(transparent 60%, rgba($color-primary-light, 0.55) 0);
  padding: 0 2px;
  color: $color-primary-dark;
}

.home-hero__desc {
  max-width: 580px;
  color: $color-text-secondary;
  font-size: 16px;
  line-height: 1.7;
}

.hero-search {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 600px;
  height: 56px;
  margin-top: 26px;
  padding: 0 8px 0 20px;
  background: #fff;
  border: 1.5px solid rgba($color-primary, 0.35);
  border-radius: $radius-full;
  box-shadow: 0 12px 30px rgba(224, 122, 95, 0.14);
  transition: border-color $transition-fast, box-shadow $transition-fast, transform $transition-fast;

  &:focus-within {
    border-color: $color-primary;
    box-shadow: 0 16px 38px rgba(224, 122, 95, 0.22);
    transform: translateY(-1px);
  }
}

.hero-search__icon {
  color: $color-primary;
  font-size: 22px;
}

.hero-search__input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: $color-text;
  font-size: 15px;

  &::placeholder {
    color: $color-text-muted;
  }

  &::-webkit-search-cancel-button {
    cursor: pointer;
  }
}

.hero-search__button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 0 26px;
  border: 0;
  color: #fff;
  background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  border-radius: $radius-full;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast, filter $transition-fast;
  box-shadow: $shadow-button;

  &:hover {
    filter: brightness(1.04);
    box-shadow: 0 6px 18px rgba(224, 122, 95, 0.42);
  }

  &:active {
    transform: scale(0.97);
  }
}

.hot-keywords {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
  font-size: 13px;

  &__label {
    color: $color-text-muted;
    font-weight: 600;
  }

  button {
    border: 1px solid $color-border;
    background: rgba(255, 255, 255, 0.7);
    color: $color-text-secondary;
    border-radius: $radius-full;
    padding: 6px 13px;
    cursor: pointer;
    transition: color $transition-fast, border-color $transition-fast, background $transition-fast, transform $transition-fast;

    &:hover {
      color: $color-primary-dark;
      border-color: rgba($color-primary, 0.4);
      background: $color-primary-lighter;
      transform: translateY(-1px);
    }
  }
}

// === Hero 右侧面板 ===
.home-hero__panel {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  align-content: start;
}

.publish-card {
  grid-column: 1 / -1;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 22px;
  overflow: hidden;
  border-radius: $radius-lg;
  background: linear-gradient(140deg, $color-primary 0%, $color-primary-light 100%);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 12px 28px rgba(224, 122, 95, 0.28);
  transition: transform $transition-fast, box-shadow $transition-fast;

  &::after {
    content: '';
    position: absolute;
    right: -30px;
    bottom: -40px;
    width: 130px;
    height: 130px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.14);
  }

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 18px 38px rgba(224, 122, 95, 0.36);
  }

  strong {
    font-size: 23px;
    font-weight: 800;
  }
}

.publish-card__label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;
  opacity: 0.9;
}

.publish-card__hint {
  font-size: 13px;
  opacity: 0.92;
}

.publish-card__cta {
  width: fit-content;
  margin-top: 10px;
  padding: 7px 16px;
  background: rgba(255, 255, 255, 0.95);
  color: $color-primary-dark;
  border-radius: $radius-full;
  font-size: 13px;
  font-weight: 800;
}

.hero-stat {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  backdrop-filter: blur(6px);

  span {
    color: $color-primary-dark;
    font-size: 30px;
    font-weight: 850;
    line-height: 1.1;
  }

  p {
    margin-top: 2px;
    color: $color-text-secondary;
    font-size: 13px;
  }
}

// === 通用区块 ===
.section {
  margin-bottom: 38px;
}

.section__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  h2 {
    color: $color-text;
    font-size: 25px;
    font-weight: 800;
    line-height: 1.2;
    letter-spacing: -0.4px;
  }
}

.section__label::before {
  content: '';
  width: 18px;
  height: 2px;
  margin-right: 8px;
  border-radius: 2px;
  background: $color-primary;
}

.see-all {
  flex-shrink: 0;
  color: $color-text-secondary;
  font-size: 13px;
  font-weight: 600;
  transition: color $transition-fast;

  &:hover {
    color: $color-primary;
  }
}

// === 分类网格 ===
.category-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 12px;
}

.category-item {
  display: flex;
  min-height: 104px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid $color-border-light;
  border-radius: $radius-md;
  background: #fff;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    border-color: rgba($color-primary, 0.3);
    box-shadow: 0 10px 24px rgba(45, 36, 32, 0.09);
    transform: translateY(-3px);

    .category-icon {
      background: $color-primary-lighter;
      transform: scale(1.08);
    }
  }
}

.category-item--all {
  color: $color-primary-dark;
  border-color: rgba($color-primary, 0.25);
  background: linear-gradient(160deg, $color-primary-lighter, #fff);

  .category-icon {
    background: rgba(255, 255, 255, 0.7);
    color: $color-primary;
    font-weight: 700;
  }
}

.category-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  border-radius: $radius-full;
  background: $color-bg-warm;
  font-size: 26px;
  transition: transform $transition-fast, background $transition-fast;
}

.category-name {
  max-width: 100%;
  padding: 0 6px;
  color: $color-text;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// === 频道卡 ===
.channels-section {
  display: grid;
  grid-template-columns: 1.35fr 1fr 1fr;
  gap: 14px;
}

.channel-card {
  position: relative;
  min-height: 128px;
  padding: 20px 22px;
  overflow: hidden;
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  background: #fff;
  cursor: pointer;
  box-shadow: $shadow-sm;
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  span {
    color: $color-primary;
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.5px;
  }

  strong {
    display: block;
    margin: 8px 0 5px;
    color: $color-text;
    font-size: 21px;
    font-weight: 800;
  }

  p {
    color: $color-text-secondary;
    font-size: 13px;
  }

  &:hover {
    border-color: rgba($color-primary, 0.25);
    box-shadow: $shadow-lg;
    transform: translateY(-3px);

    .channel-card__arrow {
      transform: translateX(4px);
      opacity: 1;
    }
  }
}

.channel-card__arrow {
  position: absolute;
  right: 18px;
  bottom: 16px;
  color: $color-primary !important;
  font-size: 18px;
  font-weight: 800;
  opacity: 0.55;
  transition: transform $transition-fast, opacity $transition-fast;
}

.channel-card--primary {
  border-color: transparent;
  background: linear-gradient(140deg, $color-primary 0%, $color-primary-light 100%);
  box-shadow: 0 12px 28px rgba(224, 122, 95, 0.26);

  &::after {
    content: '';
    position: absolute;
    right: -34px;
    top: -34px;
    width: 120px;
    height: 120px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.14);
  }

  span,
  strong,
  p {
    color: #fff;
  }

  .channel-card__arrow {
    color: #fff !important;
    opacity: 0.85;
  }
}

// === 横向滚动：最新发布 ===
.scroll-row {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding: 4px 2px 12px;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;

  &::-webkit-scrollbar {
    display: none;
  }
}

.scroll-card {
  flex: 0 0 158px;
  scroll-snap-align: start;
  cursor: pointer;
}

.scroll-card__img {
  position: relative;
  width: 158px;
  height: 158px;
  overflow: hidden;
  background: #f2eee9;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform $transition-normal;
  }

  .scroll-card:hover & img {
    transform: scale(1.06);
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

.scroll-card__price {
  display: block;
  margin-top: 10px;
  color: $color-price;
  font-size: 18px;
  font-weight: 850;
}

.scroll-card__title {
  display: -webkit-box;
  margin-top: 2px;
  color: $color-text;
  font-size: 13px;
  line-height: 1.45;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

// === 瀑布流：猜你喜欢 ===
.waterfall {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.empty-inline,
.empty-hint {
  color: $color-text-muted;
  font-size: 14px;
}

.empty-hint {
  padding: 52px 20px;
  text-align: center;
  background: $color-bg-warm;
  border: 1px dashed $color-border;
  border-radius: $radius-lg;

  a {
    display: inline-flex;
    margin-top: 14px;
    padding: 9px 20px;
    color: #fff;
    background: linear-gradient(135deg, $color-primary, $color-primary-dark);
    border-radius: $radius-full;
    font-weight: 700;
    box-shadow: $shadow-button;
  }
}

@media (max-width: 960px) {
  .home-hero {
    grid-template-columns: 1fr;
    padding: 32px 26px;
  }

  .home-hero h1 {
    font-size: 31px;
  }

  .category-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .channels-section,
  .waterfall {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .home-hero {
    margin-top: 4px;
    padding: 24px 20px;
  }

  .home-hero h1 {
    font-size: 27px;
  }

  .hero-search {
    height: auto;
    min-height: 52px;
  }

  .hero-search__button {
    padding: 0 18px;
  }

  .home-hero__panel,
  .category-grid,
  .channels-section,
  .waterfall {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-page > section {
    animation: none;
  }
}
</style>
