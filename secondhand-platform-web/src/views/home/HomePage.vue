<template>
  <div class="home-page">
    <section class="home-hero">
      <div class="home-hero__inner">
        <div class="home-hero__content">
          <span class="home-hero__eyebrow"><i></i>校园与同城 · 闲置流转中</span>
          <h1>
            好物不打烊，<br>
            闲置<span class="home-hero__mark">换个主人</span><span class="home-hero__red">继续发光</span>
          </h1>
          <svg class="scribble" viewBox="0 0 240 14" aria-hidden="true">
            <path d="M3 9 C 30 3, 55 12, 82 7 S 135 3, 162 8 S 215 11, 237 5" />
          </svg>
          <p class="home-hero__desc">
            搜数码、教材、通勤装备和生活小物，发现附近同学正在出售的宝贝。
            <b>三步上架</b>，让用不上的东西流向<b>正需要的人</b>。
          </p>

          <form class="hero-search" @submit.prevent="handleHeroSearch">
            <el-icon class="hero-search__icon"><Search /></el-icon>
            <input
              v-model="heroKeyword"
              class="hero-search__input"
              type="search"
              placeholder="搜索 iPhone、考研教材、自行车..."
              aria-label="搜索商品"
            />
            <button class="hero-search__button" type="submit">开逛</button>
          </form>

          <div class="hot-keywords">
            <span class="hot-keywords__label">HOT</span>
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

        <aside class="home-hero__panel">
          <div class="stamp">省钱<br>又环保</div>
          <div class="publish-poster" @click="$router.push('/product/publish')">
            <span class="tape" aria-hidden="true"></span>
            <span class="publish-poster__tag">QUICK SELL · 快速出</span>
            <strong>闲置换现金</strong>
            <p>拍照、定价、上架，三步搞定</p>
            <span class="publish-poster__cta">发布商品 <i>→</i></span>
            <div class="barcode" aria-hidden="true"></div>
          </div>
          <div class="ticket-row">
            <div class="ticket">
              <b>{{ latestProducts.length || 0 }}</b>
              <span>今日新上</span>
            </div>
            <div class="ticket">
              <b>{{ categories.length || 0 }}</b>
              <span>交易分类</span>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <section class="section category-section">
      <div class="section__header">
        <div>
          <p class="section__label">CATEGORY</p>
          <h2>按摊位逛一逛</h2>
        </div>
        <router-link to="/products" class="see-all">查看全部 <i>→</i></router-link>
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
          <span class="category-icon">✦</span>
          <span class="category-name">全部商品</span>
        </button>
      </div>
    </section>

    <section class="channels-section">
      <button class="channel-card channel-card--ink" type="button" @click="$router.push('/products?sort=latest')">
        <span>FRESH · 上新</span>
        <strong>刚刚上架的好物</strong>
        <p>手快有，手慢无，抢先发现低价闲置</p>
        <i aria-hidden="true">→</i>
      </button>
      <button class="channel-card channel-card--yellow" type="button" @click="$router.push('/products?sort=hottest')">
        <span>POPULAR · 人气</span>
        <strong>大家都想要</strong>
        <p>看看同学们都在蹲什么</p>
        <i aria-hidden="true">→</i>
      </button>
      <button class="channel-card" type="button" @click="$router.push('/products?sort=price_asc')">
        <span>BUDGET · 捡漏</span>
        <strong>预算友好区</strong>
        <p>从便宜实用开始逛起</p>
        <i aria-hidden="true">→</i>
      </button>
    </section>

    <section class="section arrivals-section">
      <div class="section__header">
        <div>
          <p class="section__label">NEW ARRIVALS</p>
          <h2>刚刚出摊</h2>
        </div>
        <router-link to="/products?sort=latest" class="see-all">更多上新 <i>→</i></router-link>
      </div>

      <div class="scroll-row">
        <article
          v-for="item in latestProducts"
          :key="item.id"
          class="polaroid"
          @click="$router.push(`/product/${item.id}`)"
        >
          <div class="polaroid__img">
            <img v-if="getFirstImage(item)" :src="getFirstImage(item)" :alt="item.title" loading="lazy" />
            <div v-else class="image-fallback">暂无图片</div>
          </div>
          <p class="polaroid__title">{{ item.title }}</p>
          <span class="price-ticket"><i>¥</i><b>{{ formatPriceNum(item.price) }}</b></span>
        </article>
        <div v-if="!latestProducts.length" class="empty-inline">暂无商品</div>
      </div>
    </section>

    <section class="section recommend-section">
      <div class="section__header">
        <div>
          <p class="section__label">FOR YOU</p>
          <h2>猜你想蹲</h2>
        </div>
        <router-link to="/products?sort=hottest" class="see-all">进入商品广场 <i>→</i></router-link>
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

@keyframes riseIn {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}

.home-page > section {
  animation: riseIn 0.62s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.home-page > section:nth-child(1) { animation-delay: 0.04s; }
.home-page > section:nth-child(2) { animation-delay: 0.12s; }
.home-page > section:nth-child(3) { animation-delay: 0.20s; }
.home-page > section:nth-child(4) { animation-delay: 0.28s; }
.home-page > section:nth-child(5) { animation-delay: 0.36s; }

.home-hero {
  position: relative;
  padding: 52px $space-lg 20px;

  &::before {
    content: '';
    position: absolute;
    left: max(18px, calc((100vw - #{$max-width}) / 2));
    right: max(18px, calc((100vw - #{$max-width}) / 2));
    bottom: 0;
    height: 2px;
    border-top: 2px dashed rgba($color-ink, 0.18);
    pointer-events: none;
  }
}

.home-hero__inner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 350px;
  gap: 56px;
  align-items: start;
  max-width: $max-width;
  margin: 0 auto;
}

.home-hero__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 26px;
  padding: 6px 15px;
  border: $border-ink;
  border-radius: $radius-full;
  background: $color-bg-card;
  box-shadow: 3px 3px 0 $color-ink;
  font-family: $font-family-mono;
  font-size: 11px;
  font-weight: $font-weight-heavy;
  letter-spacing: 1.4px;

  i {
    width: 8px;
    height: 8px;
    border: 1.5px solid $color-ink;
    border-radius: 50%;
    background: $color-green;
  }
}

.home-hero h1 {
  max-width: 720px;
  margin: 0;
  color: $color-text;
  font-family: $font-family-display;
  font-size: clamp(38px, 4.8vw, 58px);
  font-weight: $font-weight-heavy;
  line-height: 1.22;
  letter-spacing: 0;
}

.home-hero__mark {
  display: inline-block;
  margin: 0 5px;
  padding: 0 7px;
  border-radius: $radius-xs;
  background: linear-gradient(104deg, transparent 1%, $color-yellow 4%, $color-yellow 96%, transparent 99%);
}

.home-hero__red {
  color: $color-red;
}

.scribble {
  display: block;
  width: 240px;
  max-width: 70vw;
  margin-top: 6px;
  stroke: $color-red;
  stroke-width: 3.5;
  fill: none;
  stroke-linecap: round;
  stroke-dasharray: 320;
  stroke-dashoffset: 320;
  animation: drawLine 1s ease 0.45s forwards;
}

@keyframes drawLine {
  to {
    stroke-dashoffset: 0;
  }
}

.home-hero__desc {
  max-width: 560px;
  margin-top: 20px;
  color: $color-text-secondary;
  font-size: 16px;
  line-height: $line-height-relaxed;

  b {
    color: $color-text;
    background: linear-gradient(transparent 62%, rgba($color-yellow, 0.55) 62%);
    padding: 0 2px;
  }
}

.hero-search {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 560px;
  min-height: 60px;
  margin-top: 30px;
  padding: 0 8px 0 22px;
  border: $border-ink;
  border-radius: $radius-full;
  background: $color-bg-card;
  box-shadow: 5px 5px 0 $color-ink;
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover {
    box-shadow: 6px 6px 0 $color-ink;
    transform: translate(-1px, -1px);
  }

  &:focus-within {
    box-shadow: $shadow-hover;
    transform: translate(-2px, -2px);
  }
}

.hero-search__icon {
  flex-shrink: 0;
  color: $color-red;
  font-size: 21px;
}

.hero-search__input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: $color-text;
  font-size: 15px;

  &::placeholder {
    color: $color-text-muted;
  }
}

.hero-search__button {
  height: 44px;
  padding: 0 25px;
  border: $border-ink;
  border-radius: $radius-full;
  background: $color-red;
  color: #fff;
  font-weight: $font-weight-heavy;
  transition: background $transition-fast, box-shadow $transition-fast;

  &:hover {
    background: $color-red-deep;
    box-shadow: 2px 2px 0 $color-ink;
  }
}

.hot-keywords {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
  margin-top: 20px;
}

.hot-keywords__label {
  color: $color-text-secondary;
  font-family: $font-family-mono;
  font-size: 12px;
  font-weight: $font-weight-heavy;
}

.hot-keywords button {
  padding: 5px 13px;
  border: 1.5px dashed rgba($color-ink, 0.48);
  border-radius: $radius-full;
  background: transparent;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    border-style: solid;
    background: $color-bg-card;
    color: $color-text;
    box-shadow: 2px 2px 0 $color-ink;
    transform: translateY(-1px);
  }
}

.home-hero__panel {
  position: relative;
  display: grid;
  gap: $space-md;
  padding-top: 10px;

  &::before {
    content: '';
    position: absolute;
    inset: 22px -14px auto auto;
    width: 72px;
    height: 18px;
    border: 1.5px solid rgba($color-ink, 0.35);
    background: rgba($color-paper-2, 0.46);
    transform: rotate(8deg);
  }
}

.stamp {
  position: absolute;
  top: -34px;
  right: -18px;
  z-index: 3;
  display: grid;
  width: 92px;
  height: 92px;
  place-items: center;
  border: 2.5px solid $color-red;
  border-radius: 50%;
  background: $color-bg-card;
  box-shadow: 3px 3px 0 $color-ink;
  color: $color-red;
  font-family: $font-family-display;
  font-size: 15px;
  font-weight: $font-weight-heavy;
  line-height: 1.3;
  text-align: center;
  transform: rotate(12deg);

  &::before {
    content: '';
    position: absolute;
    inset: 5px;
    border: 1.5px dashed $color-red;
    border-radius: 50%;
  }
}

.publish-poster {
  position: relative;
  padding: 26px 24px 20px;
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-red;
  box-shadow: 6px 6px 0 $color-ink;
  color: #fff;
  cursor: pointer;
  transform: rotate(-1.2deg);
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover {
    box-shadow: $shadow-hover;
    transform: rotate(-1.2deg) translate(-2px, -2px);
  }
}

.tape {
  position: absolute;
  top: -12px;
  left: 50%;
  width: 96px;
  height: 24px;
  border: 1.5px solid rgba($color-ink, 0.55);
  background: rgba($color-yellow, 0.86);
  clip-path: polygon(3% 0, 97% 6%, 100% 94%, 0 100%);
  transform: translateX(-50%) rotate(-3deg);
}

.publish-poster__tag {
  font-family: $font-family-mono;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.6px;
}

.publish-poster strong {
  display: block;
  margin: 8px 0 4px;
  font-family: $font-family-display;
  font-size: 30px;
  font-weight: $font-weight-heavy;
}

.publish-poster p {
  font-size: $font-size-sm;
  opacity: 0.92;
}

.publish-poster__cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  padding: 8px 18px;
  border: 2px solid $color-ink;
  border-radius: $radius-full;
  background: $color-bg-card;
  box-shadow: 3px 3px 0 rgba($color-ink, 0.85);
  color: $color-red-deep;
  font-size: 13px;
  font-weight: $font-weight-heavy;

  i {
    font-style: normal;
    transition: transform $transition-fast;
  }
}

.publish-poster:hover .publish-poster__cta i {
  transform: translateX(4px);
}

.barcode {
  height: 26px;
  margin-top: 18px;
  opacity: 0.85;
  background:
    repeating-linear-gradient(
      90deg,
      #fff 0 2px,
      transparent 2px 5px,
      #fff 5px 6px,
      transparent 6px 12px,
      #fff 12px 15px,
      transparent 15px 18px,
      #fff 18px 19px,
      transparent 19px 26px
    );
}

.ticket-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $space-md;
}

.ticket {
  position: relative;
  padding: 16px 18px 14px 26px;
  border: $border-ink;
  border-radius: $radius-md;
  background: $color-bg-card;
  box-shadow: $shadow-hard;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 8px;
    bottom: 8px;
    left: 12px;
    border-left: 2px dashed rgba($color-ink, 0.3);
  }

  &::after {
    content: '';
    position: absolute;
    right: -10px;
    bottom: -10px;
    width: 42px;
    height: 42px;
    border: 2px solid rgba($color-ink, 0.12);
    border-radius: 50%;
  }

  b {
    display: block;
    color: $color-red-deep;
    font-family: $font-family-mono;
    font-size: 27px;
    line-height: 1.1;
  }

  span {
    color: $color-text-secondary;
    font-size: 12px;
    font-weight: $font-weight-semibold;
  }
}

.section,
.channels-section {
  width: 100%;
  max-width: $max-width;
  margin: 0 auto;
  padding: 44px $space-lg 0;
}

.section__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $space-lg;
  margin-bottom: $space-lg;

  h2 {
    color: $color-text;
    font-family: $font-family-display;
    font-size: 32px;
    font-weight: $font-weight-heavy;
    line-height: 1.15;
  }
}

.section__label {
  display: inline-block;
  margin-bottom: 10px;
  padding: 3px 10px;
  border: 1.5px solid $color-red-deep;
  border-radius: $radius-xs;
  color: $color-red-deep;
  font-family: $font-family-mono;
  font-size: 11px;
  font-weight: $font-weight-heavy;
  letter-spacing: 2px;
  transform: rotate(-1.5deg);
}

.see-all {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;

  i {
    font-style: normal;
    transition: transform $transition-fast;
  }

  &:hover {
    color: $color-text;

    i {
      transform: translateX(4px);
    }
  }
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 14px;
}

.category-item {
  position: relative;
  display: flex;
  min-height: 110px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-bg-card;
  box-shadow: $shadow-hard;
  overflow: hidden;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &::after {
    content: '';
    position: absolute;
    inset: auto -18px -28px auto;
    width: 64px;
    height: 64px;
    border: 2px dashed rgba($color-ink, 0.12);
    border-radius: 50%;
    transition: transform $transition-fast;
  }

  &:hover {
    background: $color-paper-2;
    box-shadow: 6px 6px 0 $color-ink;
    transform: translate(-2px, -2px) rotate(-1deg);
  }

  &:hover::after {
    transform: translate(-6px, -6px);
  }

  &:nth-child(even):hover {
    transform: translate(-2px, -2px) rotate(1deg);
  }
}

.category-item--all {
  background: $color-yellow;
}

.category-icon {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border: 2px solid $color-ink;
  border-radius: 50%;
  background: #FFD9C4;
  font-size: 26px;
}

.category-item:nth-child(8n+2) .category-icon { background: #CFE6F7; }
.category-item:nth-child(8n+3) .category-icon { background: #FFEBAE; }
.category-item:nth-child(8n+4) .category-icon { background: #F5CFDD; }
.category-item:nth-child(8n+5) .category-icon { background: #CDE8CF; }
.category-item:nth-child(8n+6) .category-icon { background: #E3D9F5; }
.category-item:nth-child(8n+7) .category-icon { background: #EBDFC5; }
.category-item:nth-child(8n+8) .category-icon { background: $color-yellow; }

.category-name {
  max-width: 100%;
  padding: 0 6px;
  color: $color-text;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.channels-section {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr;
  gap: 18px;
}

.channel-card {
  position: relative;
  min-height: 142px;
  padding: 24px 26px;
  overflow: hidden;
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-bg-card;
  box-shadow: $shadow-hard;
  color: $color-text;
  text-align: left;
  transition: transform $transition-fast, box-shadow $transition-fast;

  &::before {
    content: '';
    position: absolute;
    left: 18px;
    right: 66px;
    bottom: 18px;
    border-top: 2px dashed rgba($color-ink, 0.16);
  }

  &:hover {
    box-shadow: $shadow-hover;
    transform: translate(-3px, -3px);
  }

  span {
    color: $color-red-deep;
    font-family: $font-family-mono;
    font-size: 11px;
    font-weight: $font-weight-heavy;
    letter-spacing: 1.5px;
  }

  strong {
    display: block;
    margin: 9px 0 5px;
    font-family: $font-family-display;
    font-size: 25px;
    font-weight: $font-weight-heavy;
  }

  p {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }

  i {
    position: absolute;
    right: 20px;
    bottom: 16px;
    display: grid;
    width: 34px;
    height: 34px;
    place-items: center;
    border: 2px solid $color-ink;
    border-radius: 50%;
    background: $color-paper;
    font-style: normal;
    font-weight: $font-weight-heavy;
    transition: transform $transition-fast, background $transition-fast;
  }

  &:hover i {
    background: $color-yellow;
    transform: translateX(4px);
  }
}

.channel-card--ink {
  background: $color-ink;

  &::before {
    border-top-color: rgba($color-paper, 0.2);
  }

  &::after {
    content: '';
    position: absolute;
    top: -20px;
    right: -20px;
    width: 130px;
    height: 130px;
    border-radius: 50%;
    background-image: radial-gradient(rgba($color-paper, 0.5) 1.5px, transparent 1.6px);
    background-size: 10px 10px;
  }

  span {
    color: $color-yellow;
  }

  strong {
    color: $color-paper;
  }

  p {
    color: rgba($color-paper, 0.75);
  }

  i {
    border-color: $color-paper;
    background: $color-red;
    color: #fff;
  }
}

.channel-card--yellow {
  background: $color-yellow;

  span,
  p {
    color: $color-text;
  }
}

.scroll-row {
  position: relative;
  display: flex;
  gap: 20px;
  overflow-x: auto;
  margin: 0 -4px;
  padding: 14px 4px 24px;
  scroll-snap-type: x mandatory;
  scrollbar-color: $color-ink $color-paper-3;
}

.polaroid {
  flex: 0 0 180px;
  scroll-snap-align: start;
  padding: 10px 10px 14px;
  border: $border-ink;
  border-radius: 6px;
  background: $color-bg-card;
  box-shadow: $shadow-hard;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:nth-child(odd) {
    transform: rotate(-1.3deg);
  }

  &:nth-child(even) {
    transform: rotate(1.1deg);
  }

  &:hover {
    box-shadow: 6px 8px 0 $color-ink;
    transform: rotate(0) translateY(-6px);
  }
}

.polaroid__img {
  position: relative;
  display: grid;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  border: 1.5px solid rgba($color-ink, 0.2);
  border-radius: 3px;
  background: $color-paper-3;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(118deg, rgba(255, 255, 255, 0.5) 0%, transparent 34%);
    pointer-events: none;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.image-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  background:
    repeating-linear-gradient(135deg, rgba($color-ink, 0.055) 0 2px, transparent 2px 10px),
    $color-paper-3;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  font-weight: $font-weight-heavy;
}

.polaroid__title {
  display: -webkit-box;
  min-height: 40px;
  margin: 12px 0 9px;
  color: $color-text;
  font-size: 13px;
  font-weight: $font-weight-bold;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.price-ticket {
  position: relative;
  display: inline-flex;
  align-items: baseline;
  gap: 1px;
  padding: 3px 10px 3px 16px;
  border: 2px solid $color-ink;
  border-radius: 4px 8px 8px 4px;
  background: $color-yellow;
  box-shadow: 2px 2px 0 $color-ink;
  color: $color-ink;
  font-family: $font-family-mono;

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

  i {
    font-style: normal;
    font-size: 12px;
  }

  b {
    max-width: 92px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 19px;
  }
}

.waterfall {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.empty-inline,
.empty-hint {
  color: $color-text-secondary;
  font-size: $font-size-sm;
}

.empty-inline {
  min-width: 220px;
  padding: 28px;
  border: 2px dashed rgba($color-ink, 0.35);
  border-radius: $radius-md;
  background: rgba($color-bg-card, 0.68);
  text-align: center;
}

.empty-hint {
  padding: 52px 20px;
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-bg-card;
  box-shadow: $shadow-hard;
  text-align: center;

  a {
    display: inline-flex;
    margin-top: 14px;
    padding: 9px 20px;
    border: $border-ink;
    border-radius: $radius-full;
    background: $color-red;
    box-shadow: $shadow-button;
    color: #fff;
    font-weight: $font-weight-heavy;
  }
}

@media (max-width: $bp-lg) {
  .home-hero__inner {
    grid-template-columns: 1fr;
    gap: $space-xl;
  }

  .home-hero__panel {
    max-width: 520px;
  }

  .category-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .channels-section,
  .waterfall {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: $bp-sm) {
  .home-hero {
    padding: $space-xl $space-md 8px;
  }

  .home-hero h1 {
    font-size: 34px;
  }

  .hero-search {
    min-height: 54px;
    padding-left: 16px;
    gap: 8px;
  }

  .hero-search__button {
    height: 40px;
    padding: 0 16px;
  }

  .stamp {
    right: 4px;
  }

  .category-grid,
  .channels-section,
  .waterfall,
  .ticket-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .section,
  .channels-section {
    padding-right: $space-md;
    padding-left: $space-md;
  }

  .section__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
    margin-bottom: 16px;
  }

  .section__header h2 {
    font-size: 28px;
  }

  .category-item {
    min-height: 98px;
  }

  .channel-card {
    min-height: 132px;
    padding: 20px;
  }
}
</style>
