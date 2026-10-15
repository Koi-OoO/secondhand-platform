<template>
  <div class="product-list">
    <section class="market-hero">
      <div>
        <p class="market-hero__eyebrow">MARKET</p>
        <h1>商品广场</h1>
        <p>搜索、筛选和比较正在出售的闲置好物。</p>
      </div>
      <router-link to="/product/publish" class="market-hero__publish">
        <el-icon><Plus /></el-icon>
        发布商品
      </router-link>
    </section>

    <section class="filter-panel">
      <div class="toolbar-search">
        <div class="search-wrap">
          <el-input
            v-model="keyword"
            placeholder="搜索商品、品牌、型号..."
            :prefix-icon="Search"
            clearable
            size="large"
            class="main-search"
            @keyup.enter="applySearch"
            @clear="applySearch"
            @focus="showHistory = searchHistory.length > 0"
            @blur="hideHistoryDelayed"
          />
          <button type="button" class="search-button" @mousedown.prevent="applySearch">搜索</button>

          <Transition name="hist-fade">
            <div v-if="showHistory" class="search-history">
              <div class="hist-header">
                <span>最近搜索</span>
                <button class="hist-clear" type="button" @mousedown.prevent="clearHistory">清空</button>
              </div>
              <div class="hist-list">
                <span
                  v-for="(h, idx) in searchHistory"
                  :key="idx"
                  class="hist-item"
                  @mousedown.prevent="fillFromHistory(h)"
                >
                  <span class="hist-text">{{ h }}</span>
                  <button class="hist-del" type="button" @mousedown.prevent.stop="removeHistory(idx)">×</button>
                </span>
              </div>
            </div>
          </Transition>
        </div>
      </div>

      <div class="filter-block">
        <span class="filter-label">分类</span>
        <div class="filter-chips">
          <button
            type="button"
            class="chip"
            :class="{ 'chip--active': currentCategory === 0 }"
            @click="selectCategory(0)"
          >全部</button>
          <button
            v-for="cat in categories"
            :key="cat.id"
            type="button"
            class="chip"
            :class="{ 'chip--active': currentCategory === cat.id }"
            @click="selectCategory(cat.id)"
          >{{ cat.name }}</button>
        </div>
      </div>

      <div class="filter-grid">
        <div class="filter-block">
          <span class="filter-label">成色</span>
          <div class="filter-chips">
            <button
              type="button"
              class="chip chip--sm"
              :class="{ 'chip--active': conditionFilter === 0 }"
              @click="toggleCondition(0)"
            >不限</button>
            <button
              v-for="(label, val) in CONDITION"
              :key="val"
              type="button"
              class="chip chip--sm"
              :class="{ 'chip--active': conditionFilter === Number(val) }"
              @click="toggleCondition(Number(val))"
            >{{ label }}</button>
          </div>
        </div>

        <div class="filter-block filter-block--price">
          <span class="filter-label">价格</span>
          <div class="price-range">
            <input
              v-model.number="minPrice"
              type="number"
              placeholder="最低"
              class="price-input"
              min="0"
            />
            <span class="price-sep">-</span>
            <input
              v-model.number="maxPrice"
              type="number"
              placeholder="最高"
              class="price-input"
              min="0"
            />
            <button class="price-apply" type="button" @click="applyPriceFilter">确定</button>
            <button v-if="(minPrice !== null && minPrice !== '') || (maxPrice !== null && maxPrice !== '')" class="price-reset" type="button" @click="resetPrice">重置</button>
          </div>
        </div>
      </div>
    </section>

    <section class="result-bar">
      <div>
        <strong>{{ total }}</strong>
        <span>件商品</span>
      </div>
      <div class="sort-btns">
        <button
          v-for="s in sortOptions"
          :key="s.value"
          type="button"
          class="sort-btn"
          :class="{ 'sort-btn--active': sort === s.value }"
          @click="changeSort(s.value)"
        >{{ s.label }}</button>
      </div>
    </section>

    <div v-if="activeFilters.length" class="active-filters">
      <span
        v-for="f in activeFilters"
        :key="f.label"
        class="active-tag"
      >
        {{ f.label }}
        <button type="button" @click="f.remove">×</button>
      </span>
    </div>

    <div v-if="loading" class="loading-wrap">
      <div class="skeleton-grid">
        <div v-for="n in 12" :key="n" class="skeleton-card">
          <div class="skeleton-img"></div>
          <div class="skeleton-body">
            <div class="skeleton-line skeleton-line--title"></div>
            <div class="skeleton-line skeleton-line--price"></div>
            <div class="skeleton-line skeleton-line--seller"></div>
          </div>
        </div>
      </div>
    </div>

    <template v-else>
      <div v-if="products.length" class="product-grid">
        <ProductCard
          v-for="item in products"
          :key="item.id"
          :product="item"
          :show-seller="true"
        />
      </div>

      <div v-else class="empty-state">
        <div class="empty-icon">🔎</div>
        <p class="empty-text">没有找到相关商品</p>
        <p class="empty-hint">换个关键词、分类或价格区间试试</p>
      </div>
    </template>

    <div v-if="total > size" class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Plus } from '@element-plus/icons-vue'
import { getCategories } from '@/api/category'
import { getProductPage } from '@/api/product'
import { CONDITION } from '@/utils/constant'
import { readProductListQuery, buildProductListQuery, buildProductPageParams } from '@/utils/productFilters'
import ProductCard from '@/components/common/ProductCard.vue'

const route = useRoute()
const router = useRouter()

const categories = ref([])
const products = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(16)
const total = ref(0)
const keyword = ref('')
const sort = ref('latest')
const currentCategory = ref(0)
const conditionFilter = ref(0)
const minPrice = ref(null)
const maxPrice = ref(null)
const appliedFilters = computed(() => readProductListQuery(route.query))

const searchHistory = ref([])
const showHistory = ref(false)
const HISTORY_KEY = 'search_history'
const MAX_HISTORY = 8

const sortOptions = [
  { label: '最新', value: 'latest' },
  { label: '最热', value: 'hottest' },
  { label: '低价', value: 'price_asc' },
  { label: '高价', value: 'price_desc' }
]

const currentCategoryName = computed(() => {
  if (currentCategory.value <= 0) return ''
  return categories.value.find(cat => cat.id === currentCategory.value)?.name || ''
})

const activeFilters = computed(() => {
  const filters = appliedFilters.value
  const r = []
  if (filters.keyword) {
    r.push({
      label: `搜索：${filters.keyword}`,
      remove: () => { keyword.value = ''; applySearch() }
    })
  }
  if (currentCategoryName.value) {
    r.push({
      label: `分类：${currentCategoryName.value}`,
      remove: () => selectCategory(0)
    })
  }
  if (filters.conditionLevel > 0) {
    r.push({
      label: CONDITION[filters.conditionLevel],
      remove: () => toggleCondition(0)
    })
  }
  if (filters.minPrice !== null || filters.maxPrice !== null) {
    const lo = filters.minPrice !== null ? `¥${filters.minPrice}` : '¥0'
    const hi = filters.maxPrice !== null ? `¥${filters.maxPrice}` : ''
    r.push({
      label: `${lo}${hi ? ' - ' + hi : '以上'}`,
      remove: resetPrice
    })
  }
  return r
})

onMounted(() => {
  syncFromQuery()
  loadHistory()
  fetchCategories()
  fetchProducts()
})

watch(
  () => route.query,
  () => {
    syncFromQuery()
    fetchProducts()
  }
)

function syncFromQuery() {
  // URL 是已生效筛选的唯一来源，价格与成色也在刷新及历史导航时恢复。
  const filters = appliedFilters.value
  currentCategory.value = filters.categoryId
  keyword.value = filters.keyword
  sort.value = filters.sort
  page.value = filters.page
  minPrice.value = filters.minPrice
  maxPrice.value = filters.maxPrice
  conditionFilter.value = filters.conditionLevel
}

function loadHistory() {
  try {
    searchHistory.value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
  } catch { searchHistory.value = [] }
}

function saveHistory(kw) {
  if (!kw || !kw.trim()) return
  const q = kw.trim()
  searchHistory.value = [q, ...searchHistory.value.filter(h => h !== q)].slice(0, MAX_HISTORY)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(searchHistory.value))
}

function fillFromHistory(kw) {
  keyword.value = kw
  showHistory.value = false
  applySearch()
}

function removeHistory(idx) {
  searchHistory.value.splice(idx, 1)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(searchHistory.value))
  if (!searchHistory.value.length) showHistory.value = false
}

function clearHistory() {
  searchHistory.value = []
  localStorage.removeItem(HISTORY_KEY)
  showHistory.value = false
}

function hideHistoryDelayed() {
  setTimeout(() => { showHistory.value = false }, 200)
}

function selectCategory(id) {
  syncQueryToUrl({ categoryId: id, page: 1 })
}

function toggleCondition(val) {
  const conditionLevel = val === appliedFilters.value.conditionLevel ? 0 : val
  syncQueryToUrl({ conditionLevel, page: 1 })
}

function applySearch() {
  keyword.value = keyword.value.trim()
  if (keyword.value) saveHistory(keyword.value)
  syncQueryToUrl({ keyword: keyword.value, page: 1 })
}

function applyPriceFilter() {
  syncQueryToUrl({ minPrice: minPrice.value, maxPrice: maxPrice.value, page: 1 })
}

function syncQueryToUrl(changes) {
  // 修改一个条件时保留其他已生效条件；未确认的输入不会悄悄影响翻页。
  const query = buildProductListQuery({ ...appliedFilters.value, ...changes })
  router.replace({ name: 'Products', query })
}

function resetPrice() {
  minPrice.value = null
  maxPrice.value = null
  syncQueryToUrl({ minPrice: null, maxPrice: null, page: 1 })
}

function changeSort(val) {
  syncQueryToUrl({ sort: val, page: 1 })
}

function handlePageChange(val) {
  syncQueryToUrl({ page: val })
}

async function fetchCategories() {
  try {
    categories.value = await getCategories()
  } catch { /* ignore */ }
}

async function fetchProducts() {
  loading.value = true
  try {
    const params = buildProductPageParams(route.query, size.value)
    const data = await getProductPage(params)
    products.value = data.records || []
    total.value = data.total || 0
  } catch {
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.product-list {
  width: 100%;
  max-width: $max-width;
  margin: 0 auto;
  padding: $space-xl $space-lg $space-2xl;
}

.market-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-lg;
  margin-bottom: $space-lg;
  padding: 30px 32px;
  overflow: hidden;
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-bg-card;
  box-shadow: $shadow-hard;

  &::after {
    content: '';
    position: absolute;
    right: -24px;
    top: -26px;
    width: 150px;
    height: 150px;
    border-radius: 50%;
    background-image: radial-gradient(rgba($color-ink, 0.17) 1.5px, transparent 1.6px);
    background-size: 10px 10px;
  }

  > div,
  .market-hero__publish {
    position: relative;
    z-index: 1;
  }

  h1 {
    color: $color-text;
    font-family: $font-family-display;
    font-size: 36px;
    font-weight: $font-weight-heavy;
    line-height: 1.2;
    letter-spacing: 0;
  }

  p {
    color: $color-text-secondary;
  }
}

.market-hero__eyebrow {
  display: inline-flex;
  align-items: center;
  margin-bottom: 8px;
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

.market-hero__publish {
  display: inline-flex;
  height: 44px;
  flex-shrink: 0;
  align-items: center;
  gap: 7px;
  padding: 0 22px;
  border: $border-ink;
  border-radius: $radius-full;
  background: $color-red;
  box-shadow: $shadow-button;
  color: #fff;
  font-size: $font-size-sm;
  font-weight: $font-weight-heavy;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    background: $color-red-deep;
    box-shadow: $shadow-hover;
    transform: translate(-2px, -2px);
  }
}

.filter-panel {
  position: relative;
  z-index: 10;
  margin-bottom: $space-lg;
  padding: 18px;
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-bg-card;
  box-shadow: $shadow-hard;

  &::before {
    content: '';
    position: absolute;
    top: -13px;
    left: 34px;
    width: 118px;
    height: 24px;
    border: 1.5px solid rgba($color-ink, 0.42);
    background: rgba($color-yellow, 0.78);
    clip-path: polygon(2% 0, 98% 8%, 100% 92%, 0 100%);
    transform: rotate(-2deg);
  }
}

.toolbar-search {
  margin-bottom: 14px;
}

.search-wrap {
  position: relative;
  display: flex;
  gap: 10px;
}

.main-search {
  flex: 1;

  :deep(.el-input__wrapper) {
    min-height: 46px;
    border-radius: $radius-full;
    padding: 3px 16px;
  }
}

.search-button {
  width: 96px;
  border: $border-ink;
  border-radius: $radius-full;
  background: $color-red;
  box-shadow: $shadow-button;
  color: #fff;
  font-weight: $font-weight-heavy;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    background: $color-red-deep;
    box-shadow: $shadow-hover;
    transform: translate(-2px, -2px);
  }

  &:active {
    box-shadow: 1px 1px 0 $color-ink;
    transform: translate(2px, 2px);
  }
}

.search-history {
  position: absolute;
  top: 54px;
  left: 0;
  right: 106px;
  z-index: 100;
  padding: 12px 14px;
  border: $border-ink;
  border-radius: $radius-md;
  background: $color-bg-card;
  box-shadow: $shadow-hover;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.45), transparent 42%);
  }
}

.hist-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
}

.hist-clear,
.hist-del {
  border: 0;
  background: none;
  color: $color-text-muted;
  cursor: pointer;

  &:hover {
    color: $color-red;
  }
}

.hist-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hist-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 160px;
  padding: 5px 10px;
  border: 1.5px dashed rgba($color-ink, 0.5);
  border-radius: $radius-full;
  background: $color-paper;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    border-style: solid;
    background: $color-yellow;
    box-shadow: 2px 2px 0 $color-ink;
    transform: translate(-1px, -1px);
  }
}

.hist-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hist-fade-enter-active,
.hist-fade-leave-active {
  transition: opacity $transition-fast, transform $transition-fast;
}

.hist-fade-enter-from,
.hist-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.filter-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
}

.filter-block {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding-top: 12px;
  border-top: 2px dashed rgba($color-ink, 0.22);
}

.filter-block--price {
  min-width: 330px;
}

.filter-label {
  width: 36px;
  flex-shrink: 0;
  padding-top: 6px;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  font-weight: $font-weight-heavy;
}

.filter-chips {
  display: flex;
  min-width: 0;
  flex: 1;
  gap: 8px;
  overflow-x: auto;
  padding: 0 2px 5px;
  -webkit-overflow-scrolling: touch;

  &::-webkit-scrollbar {
    display: none;
  }
}

.chip {
  flex-shrink: 0;
  padding: 7px 14px;
  border: 2px solid transparent;
  border-radius: $radius-full;
  background: $color-paper;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast, color $transition-fast;

  &:hover {
    border-color: $color-ink;
    background: $color-bg-card;
    color: $color-text;
    box-shadow: 2px 2px 0 $color-ink;
  }

  &--active {
    position: relative;
    border-color: $color-ink;
    background: $color-yellow;
    box-shadow: 2px 2px 0 $color-ink;
    color: $color-text;
    font-weight: $font-weight-heavy;

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      bottom: -6px;
      width: 18px;
      height: 3px;
      border-radius: $radius-full;
      background: $color-red;
      transform: translateX(-50%);
    }
  }

  &--sm {
    padding: 6px 12px;
    font-size: 12px;
  }
}

.price-range {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.price-input {
  width: 78px;
  height: 34px;
  padding: 0 10px;
  border: 2px solid $color-ink;
  border-radius: $radius-sm;
  outline: none;
  background: $color-paper;
  color: $color-text;
  font-size: $font-size-sm;
  font-weight: $font-weight-semibold;

  &:focus {
    background: $color-bg-card;
    box-shadow: 2px 2px 0 $color-ink;
  }

  &::placeholder {
    color: $color-text-muted;
  }

  &::-webkit-inner-spin-button,
  &::-webkit-outer-spin-button {
    margin: 0;
    -webkit-appearance: none;
  }

  -moz-appearance: textfield;
}

.price-sep {
  color: $color-text-secondary;
  font-weight: $font-weight-heavy;
}

.price-apply,
.price-reset {
  height: 34px;
  padding: 0 12px;
  border: 2px solid $color-ink;
  border-radius: $radius-full;
  font-size: 12px;
  font-weight: $font-weight-heavy;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast, color $transition-fast;

  &:hover {
    box-shadow: 2px 2px 0 $color-ink;
    transform: translate(-1px, -1px);
  }
}

.price-apply {
  background: $color-yellow;
  color: $color-text;

  &:hover {
    background: $color-red;
    color: #fff;
  }
}

.price-reset {
  background: $color-bg-card;
  color: $color-text-secondary;
}

.result-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: $color-text-secondary;
  padding: 0 2px;

  strong {
    margin-right: 4px;
    color: $color-red-deep;
    font-family: $font-family-mono;
    font-size: 24px;
  }
}

.sort-btns {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
}

.sort-btn {
  min-width: 54px;
  height: 34px;
  padding: 0 12px;
  border: 2px solid $color-ink;
  border-radius: $radius-full;
  background: $color-bg-card;
  color: $color-text-secondary;
  font-size: 12px;
  font-weight: $font-weight-bold;
  box-shadow: 2px 2px 0 $color-ink;

  &:hover {
    background: $color-yellow;
    color: $color-text;
  }

  &--active {
    background: $color-ink;
    color: $color-paper;
    font-weight: $font-weight-heavy;
  }
}

.active-filters {
  display: flex;
  gap: 8px;
  margin: -2px 0 16px;
  flex-wrap: wrap;
}

.active-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 2px solid $color-ink;
  border-radius: $radius-full;
  background: $color-yellow;
  box-shadow: 2px 2px 0 $color-ink;
  color: $color-text;
  font-size: 12px;
  font-weight: $font-weight-heavy;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:hover {
    background: $color-primary-lighter;
    transform: translate(-1px, -1px);
  }

  button {
    display: inline-grid;
    width: 18px;
    height: 18px;
    place-items: center;
    border: 0;
    border-radius: 50%;
    background: none;
    color: $color-text;
    cursor: pointer;
    font-weight: $font-weight-heavy;
    line-height: 1;

    &:hover {
      background: $color-ink;
      color: $color-paper;
    }
  }
}

.product-grid,
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.skeleton-card {
  overflow: hidden;
  border: $border-ink;
  border-radius: $radius-sm;
  background: $color-bg-card;
  box-shadow: $shadow-hard;
}

.skeleton-img {
  aspect-ratio: 1;
  border-bottom: $border-ink;
  background: linear-gradient(90deg, $color-paper-3 25%, $color-bg-card 50%, $color-paper-3 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-body {
  padding: 12px;
}

.skeleton-line {
  height: 14px;
  margin-bottom: 8px;
  border-radius: $radius-full;
  background: linear-gradient(90deg, $color-paper-3 25%, $color-bg-card 50%, $color-paper-3 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;

  &--title {
    width: 90%;
  }

  &--price {
    width: 50%;
    height: 18px;
  }

  &--seller {
    width: 65%;
    height: 12px;
  }
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.empty-state {
  position: relative;
  padding: 72px 20px;
  border: $border-ink;
  border-radius: $radius-lg;
  background: $color-bg-card;
  box-shadow: $shadow-hard;
  text-align: center;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 18px;
    left: 50%;
    width: 104px;
    height: 22px;
    border: 1.5px solid rgba($color-ink, 0.42);
    background: rgba($color-yellow, 0.76);
    clip-path: polygon(2% 0, 98% 8%, 100% 92%, 0 100%);
    transform: translateX(-50%) rotate(-2deg);
  }

  .empty-icon {
    margin-bottom: 12px;
    font-size: 48px;
  }

  .empty-text {
    margin-bottom: 4px;
    color: $color-text;
    font-size: 16px;
    font-weight: $font-weight-heavy;
  }

  .empty-hint {
    color: $color-text-muted;
    font-size: $font-size-sm;
  }
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: $space-xl 0;
}

.loading-wrap {
  padding-top: 2px;
}

@media (max-width: 1160px) {
  .product-grid,
  .skeleton-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: $bp-lg) {
  .filter-grid {
    grid-template-columns: 1fr;
  }

  .filter-block--price {
    min-width: 0;
  }

  .product-grid,
  .skeleton-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: $bp-sm) {
  .product-list {
    padding: $space-lg $space-md $space-2xl;
  }

  .market-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 22px;

    h1 {
      font-size: 30px;
    }
  }

  .search-wrap {
    flex-direction: column;
  }

  .search-button {
    width: 100%;
    height: 42px;
  }

  .search-history {
    right: 0;
  }

  .filter-block {
    flex-direction: column;
    gap: 8px;
    padding-top: 14px;
  }

  .filter-label {
    width: auto;
    padding-top: 0;
  }

  .result-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .product-grid,
  .skeleton-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  .sort-btns,
  .active-filters {
    width: 100%;
  }

  .sort-btn {
    flex: 1;
  }
}
</style>
