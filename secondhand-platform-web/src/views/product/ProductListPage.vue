<template>
  <div class="product-list">
    <section class="market-hero">
      <div>
        <p class="market-hero__eyebrow">Market</p>
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
            <button v-if="minPrice || maxPrice" class="price-reset" type="button" @click="resetPrice">重置</button>
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

const activeFilters = computed(() => {
  const r = []
  if (conditionFilter.value > 0) {
    r.push({
      label: CONDITION[conditionFilter.value],
      remove: () => { conditionFilter.value = 0; fetchProducts() }
    })
  }
  if (minPrice.value || maxPrice.value) {
    const lo = minPrice.value ? `¥${minPrice.value}` : '¥0'
    const hi = maxPrice.value ? `¥${maxPrice.value}` : ''
    r.push({
      label: `${lo}${hi ? ' - ' + hi : '以上'}`,
      remove: () => { minPrice.value = null; maxPrice.value = null; fetchProducts() }
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
  () => ({ ...route.query }),
  () => {
    syncFromQuery()
    fetchProducts()
  }
)

function syncFromQuery() {
  currentCategory.value = route.query.categoryId ? Number(route.query.categoryId) : 0
  keyword.value = route.query.keyword || ''
  sort.value = route.query.sort || 'latest'
  page.value = route.query.page ? Number(route.query.page) : 1
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
  currentCategory.value = id
  page.value = 1
  syncQueryToUrl()
}

function toggleCondition(val) {
  conditionFilter.value = val === conditionFilter.value ? 0 : val
  page.value = 1
  fetchProducts()
}

function applySearch() {
  if (keyword.value) saveHistory(keyword.value)
  page.value = 1
  syncQueryToUrl()
}

function applyPriceFilter() {
  page.value = 1
  fetchProducts()
}

function syncQueryToUrl() {
  const q = {}
  if (currentCategory.value > 0) q.categoryId = currentCategory.value
  if (keyword.value) q.keyword = keyword.value
  if (sort.value !== 'latest') q.sort = sort.value
  if (page.value > 1) q.page = page.value
  router.replace({ query: q })
}

function resetPrice() {
  minPrice.value = null
  maxPrice.value = null
  page.value = 1
  fetchProducts()
}

function changeSort(val) {
  sort.value = val
  page.value = 1
  syncQueryToUrl()
}

function handlePageChange(val) {
  page.value = val
  syncQueryToUrl()
  fetchProducts()
}

async function fetchCategories() {
  try {
    categories.value = await getCategories()
  } catch { /* ignore */ }
}

async function fetchProducts() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      sort: sort.value
    }
    if (currentCategory.value > 0) params.categoryId = currentCategory.value
    if (keyword.value) params.keyword = keyword.value
    if (conditionFilter.value > 0) params.conditionLevel = conditionFilter.value
    if (minPrice.value) params.minPrice = minPrice.value
    if (maxPrice.value) params.maxPrice = maxPrice.value
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
  padding-top: 18px;
  padding-bottom: $space-2xl;
}

.market-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
  padding: 30px 32px;
  overflow: hidden;
  border: 1px solid rgba($color-primary, 0.14);
  border-radius: $radius-xl;
  background:
    radial-gradient(120% 130% at 100% 0%, rgba($color-primary-lighter, 0.9), transparent 48%),
    linear-gradient(135deg, $color-bg-warm, #fff);
  box-shadow: 0 14px 38px rgba(224, 122, 95, 0.08);

  &::after {
    content: '';
    position: absolute;
    top: -90px;
    right: -60px;
    width: 280px;
    height: 280px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba($color-primary-light, 0.22), transparent 70%);
    filter: blur(16px);
    pointer-events: none;
  }

  > div,
  .market-hero__publish {
    position: relative;
    z-index: 1;
  }

  h1 {
    color: $color-text;
    font-size: 31px;
    font-weight: 800;
    line-height: 1.2;
    letter-spacing: -0.4px;
  }

  p {
    color: $color-text-secondary;
  }
}

.market-hero__eyebrow {
  display: inline-flex;
  align-items: center;
  margin-bottom: 8px;
  color: $color-primary-dark;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1.5px;
  text-transform: uppercase;

  &::before {
    content: '';
    width: 20px;
    height: 2px;
    margin-right: 8px;
    border-radius: 2px;
    background: $color-primary;
  }
}

.market-hero__publish {
  display: inline-flex;
  height: 42px;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  padding: 0 22px;
  color: #fff;
  background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  border-radius: $radius-full;
  font-size: 14px;
  font-weight: 800;
  box-shadow: $shadow-button;
  transition: transform $transition-fast, box-shadow $transition-fast, filter $transition-fast;

  &:hover {
    transform: translateY(-2px);
    filter: brightness(1.04);
    box-shadow: 0 12px 26px rgba($color-primary, 0.34);
  }
}

.filter-panel {
  position: relative;
  z-index: 10;
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  background: #fff;
  box-shadow: $shadow-card;
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
    min-height: 44px;
    border-radius: 999px;
    box-shadow: 0 0 0 1px $color-border inset;
    padding: 3px 16px;
  }

  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 2px rgba(224, 122, 95, 0.24) inset;
  }
}

.search-button {
  width: 92px;
  border: 0;
  border-radius: $radius-full;
  background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  color: #fff;
  font-weight: 800;
  cursor: pointer;
  transition: filter $transition-fast, transform $transition-fast;

  &:hover {
    filter: brightness(1.05);
  }

  &:active {
    transform: scale(0.97);
  }
}

.search-history {
  position: absolute;
  top: 52px;
  left: 0;
  right: 98px;
  z-index: 100;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid $color-border-light;
  border-radius: $radius-md;
  box-shadow: 0 14px 34px rgba(45, 36, 32, 0.12);
}

.hist-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: $color-text-muted;
  font-size: 13px;
}

.hist-clear,
.hist-del {
  border: 0;
  background: none;
  color: $color-text-muted;
  cursor: pointer;

  &:hover {
    color: $color-primary;
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
  border-radius: 999px;
  background: $color-bg-warm;
  color: $color-text-secondary;
  font-size: 13px;
  cursor: pointer;
}

.hist-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hist-fade-enter-active,
.hist-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
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
  padding-top: 10px;
  border-top: 1px solid $color-border-light;
}

.filter-block--price {
  min-width: 330px;
}

.filter-label {
  width: 36px;
  flex-shrink: 0;
  padding-top: 6px;
  color: $color-text-muted;
  font-size: 13px;
  font-weight: 700;
}

.filter-chips {
  display: flex;
  min-width: 0;
  flex: 1;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 3px;
  -webkit-overflow-scrolling: touch;

  &::-webkit-scrollbar {
    display: none;
  }
}

.chip {
  flex-shrink: 0;
  padding: 7px 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: $color-bg-warm;
  color: $color-text-secondary;
  font-size: 13px;
  cursor: pointer;
  transition: color $transition-fast, background $transition-fast, border-color $transition-fast;

  &:hover {
    color: $color-primary;
    border-color: rgba(224, 122, 95, 0.2);
  }

  &--active {
    color: #fff;
    background: linear-gradient(135deg, $color-primary, $color-primary-dark);
    font-weight: 800;
    box-shadow: 0 4px 12px rgba($color-primary, 0.28);

    &:hover {
      color: #fff;
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
  gap: 6px;
  flex-wrap: wrap;
}

.price-input {
  width: 76px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid $color-border;
  border-radius: 8px;
  color: $color-text;
  font-size: 13px;
  outline: none;

  &:focus {
    border-color: $color-primary;
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
  color: $color-text-muted;
}

.price-apply,
.price-reset {
  height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
}

.price-apply {
  border: 0;
  background: $color-primary-lighter;
  color: $color-primary;
  font-weight: 800;

  &:hover {
    background: $color-primary;
    color: #fff;
  }
}

.price-reset {
  border: 1px solid $color-border;
  background: #fff;
  color: $color-text-muted;
}

.result-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: $color-text-secondary;

  strong {
    margin-right: 4px;
    color: $color-text;
    font-size: 20px;
  }
}

.sort-btns {
  display: flex;
  gap: 6px;
}

.sort-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid $color-border-light;
  border-radius: 999px;
  background: #fff;
  color: $color-text-secondary;
  font-size: 12px;
  cursor: pointer;

  &:hover {
    color: $color-primary;
  }

  &--active {
    color: #fff;
    background: $color-text;
    border-color: $color-text;
    font-weight: 800;
  }
}

.active-filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.active-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  background: $color-primary-lighter;
  color: $color-primary;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;

  button {
    border: 0;
    background: none;
    color: $color-primary;
    cursor: pointer;
  }
}

.product-grid,
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.skeleton-card {
  overflow: hidden;
  border-radius: $radius-md;
  background: #fff;
}

.skeleton-img {
  aspect-ratio: 1;
  background: linear-gradient(90deg, #f0f0f0 25%, #e7e2dc 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-body {
  padding: 12px;
}

.skeleton-line {
  height: 14px;
  margin-bottom: 8px;
  border-radius: 999px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e7e2dc 50%, #f0f0f0 75%);
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
  padding: 72px 20px;
  text-align: center;

  .empty-icon {
    margin-bottom: 12px;
    font-size: 48px;
  }

  .empty-text {
    margin-bottom: 4px;
    color: $color-text;
    font-size: 16px;
    font-weight: 700;
  }

  .empty-hint {
    color: $color-text-muted;
    font-size: 13px;
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

@media (max-width: 920px) {
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

@media (max-width: 640px) {
  .market-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 20px;

    h1 {
      font-size: 26px;
    }
  }

  .search-wrap {
    flex-direction: column;
  }

  .search-button {
    width: 100%;
    height: 40px;
  }

  .search-history {
    right: 0;
  }

  .filter-block {
    flex-direction: column;
    gap: 8px;
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
    gap: 12px;
  }
}
</style>
