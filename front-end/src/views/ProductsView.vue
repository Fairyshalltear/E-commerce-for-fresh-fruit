<template>
  <main class="page-shell"><StoreHeader /><div class="catalog-heading page-heading"><div><p class="eyebrow">挑选一份新鲜</p><h1>全部水果</h1></div><span class="muted">按你的喜好，找到好味道。</span></div>
    <div class="browse-layout"><aside class="category-sidebar"><h2>水果分类</h2><button :class="{ active: !categoryId }" @click="choose('')">全部分类</button>
      <div v-for="root in tree" :key="root.id" class="category-group"><button :class="{ active: categoryId === String(root.id) }" @click="choose(String(root.id))">{{ root.name }}</button><button v-for="child in root.children" :key="child.id" class="category-child" :class="{ active: categoryId === String(child.id) }" @click="choose(String(child.id))">{{ child.name }}</button></div>
    </aside><section class="browse-results">
      <form class="catalog-filters" @submit.prevent="submit"><label class="form-field filter-keyword">关键词<input v-model="keyword" maxlength="100" placeholder="搜索名称或描述"></label>
        <label class="form-field price-field">最低价格<input v-model="minPrice" type="number" min="0" max="99999999.99" step="0.01" placeholder="不限"></label><span class="price-separator">—</span>
        <label class="form-field price-field">最高价格<input v-model="maxPrice" type="number" min="0" max="99999999.99" step="0.01" placeholder="不限"></label>
        <button class="primary-button">筛选</button><button type="button" class="text-action" @click="reset">重置</button>
      </form>
      <div class="results-toolbar"><span class="muted">共 {{ total }} 件商品</span><label>排序 <select :value="sort" @change="sortChange"><option value="NEWEST">最新上架</option><option value="PRICE_ASC">价格从低到高</option><option value="PRICE_DESC">价格从高到低</option></select></label></div>
      <PageStatus :loading="loading" :error="error" :empty="!items.length" empty-text="没有找到符合条件的水果，试试调整筛选条件。" @retry="load" />
      <div v-if="!loading && !error" class="product-grid browse-grid"><ProductCard v-for="item in items" :key="item.id" :product="item" /></div>
      <PagePagination :total="total" :page="page" :busy="loading" @change="pageChange" />
    </section></div>
  </main>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StoreHeader from '../components/StoreHeader.vue'
import ProductCard from '../components/ProductCard.vue'
import PageStatus from '../components/PageStatus.vue'
import PagePagination from '../components/PagePagination.vue'
import { categories, searchProducts, errorMessage, type Category, type ProductCard as Card, type Sort } from '../api/catalog'
const route = useRoute(), router = useRouter()
const tree = ref<Category[]>([]), items = ref<Card[]>([]), loading = ref(false), error = ref(''), total = ref(0)
const keyword = ref(''), categoryId = ref(''), minPrice = ref(''), maxPrice = ref(''), sort = ref<Sort>('NEWEST'), page = ref(1)
let generation = 0
const text = (value: unknown) => typeof value === 'string' ? value : ''
async function load() {
  const own = ++generation; loading.value = true; error.value = ''
  keyword.value = text(route.query.keyword); categoryId.value = text(route.query.categoryId); minPrice.value = text(route.query.minPrice); maxPrice.value = text(route.query.maxPrice)
  sort.value = (text(route.query.sort) || 'NEWEST') as Sort; page.value = Number(text(route.query.page) || 1)
  try {
    const [cats, result] = await Promise.all([categories(), searchProducts({ keyword: keyword.value || undefined, categoryId: categoryId.value ? Number(categoryId.value) : undefined, minPrice: minPrice.value || undefined, maxPrice: maxPrice.value || undefined, sort: sort.value, page: page.value })])
    if (own !== generation) return
    tree.value = cats; items.value = result.items; total.value = result.total
  } catch (e) { if (own === generation) error.value = errorMessage(e) } finally { if (own === generation) loading.value = false }
}
function submit() {
  if (minPrice.value && maxPrice.value && Number(minPrice.value) > Number(maxPrice.value)) { error.value = '最低价不能大于最高价'; return }
  void router.push({ path: '/products', query: { ...(keyword.value ? { keyword: keyword.value.trim() } : {}), ...(categoryId.value ? { categoryId: categoryId.value } : {}), ...(minPrice.value ? { minPrice: minPrice.value } : {}), ...(maxPrice.value ? { maxPrice: maxPrice.value } : {}), sort: sort.value } }).then(() => { if (!loading.value) void load() })
}
function choose(id: string) { categoryId.value = id; submit() }
function reset() { keyword.value = ''; categoryId.value = ''; minPrice.value = ''; maxPrice.value = ''; sort.value = 'NEWEST'; submit() }
function sortChange(e: Event) { sort.value = (e.target as HTMLSelectElement).value as Sort; submit() }
function pageChange(next: number) { void router.push({ query: { ...route.query, page: next } }) }
watch(() => route.fullPath, load, { immediate: true })
</script>
