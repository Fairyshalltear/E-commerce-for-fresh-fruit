<template>
  <main class="page-shell"><StoreHeader />
    <section class="store-hero"><div><p class="eyebrow">FRESH FRUIT · 鲜果集</p><h1>把一季的新鲜，<br>带进每一天。</h1><p class="hero-copy">挑选喜欢的水果，让生活多一份自然的甜。</p>
      <form class="hero-search" @submit.prevent="search"><label class="visually-hidden" for="home-keyword">搜索水果</label><input id="home-keyword" v-model.trim="keyword" placeholder="搜索水果名称，例如苹果、橙子"><button class="primary-button">搜索水果</button></form>
    </div><div class="hero-art" aria-hidden="true"><span class="fruit-orbit"></span><span class="fruit-shape fruit-orange"></span><span class="fruit-shape fruit-green"></span><span class="hero-art-note">自然好味 · 新鲜之选</span></div></section>
    <section class="category-strip" aria-label="商品分类"><RouterLink to="/products">全部水果</RouterLink><RouterLink v-for="category in tree" :key="category.id" :to="{ path: '/products', query: { categoryId: category.id } }">{{ category.name }}</RouterLink></section>
    <section class="catalog-section"><div class="catalog-heading"><div><p class="eyebrow">为你精选</p><h2>今日鲜果推荐</h2></div><RouterLink class="text-action" to="/products">浏览全部水果 →</RouterLink></div>
      <PageStatus :loading="loading" :error="error" :empty="!items.length" empty-text="新鲜水果正在准备中，敬请期待。" @retry="load" />
      <div v-if="!loading && !error" class="product-grid"><ProductCard v-for="item in items" :key="item.id" :product="item" /></div>
    </section><footer class="store-footer">鲜果集 · 愿你的每一天，都有新鲜好味。</footer>
  </main>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import StoreHeader from '../components/StoreHeader.vue'
import ProductCard from '../components/ProductCard.vue'
import PageStatus from '../components/PageStatus.vue'
import { categories, recommendations, errorMessage, type Category, type ProductCard as Card } from '../api/catalog'
const router = useRouter(), keyword = ref(''), tree = ref<Category[]>([]), items = ref<Card[]>([]), loading = ref(true), error = ref('')
async function load() { loading.value = true; error.value = ''; try { [tree.value, items.value] = await Promise.all([categories(), recommendations()]) } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } }
function search() { void router.push({ path: '/products', query: keyword.value ? { keyword: keyword.value } : {} }) }
onMounted(load)
</script>
