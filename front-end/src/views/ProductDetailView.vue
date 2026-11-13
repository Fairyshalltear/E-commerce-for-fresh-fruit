<template>
  <main class="page-shell"><StoreHeader /><nav class="breadcrumbs"><RouterLink to="/products">全部水果</RouterLink><template v-for="category in detail?.categoryPath" :key="category.id"><span>/</span><RouterLink :to="{ path: '/products', query: { categoryId: category.id } }">{{ category.name }}</RouterLink></template></nav>
    <PageStatus :loading="loading" :error="error" @retry="load" />
    <template v-if="detail && !loading && !error">
      <section class="product-detail-layout"><div class="product-gallery"><div class="detail-image"><img v-if="mainImage" :src="imageUrl(mainImage)" :alt="detail.name"><span v-else>暂无图片</span></div>
        <div class="image-thumbnails"><button v-for="image in detail.images" :key="image.id" :class="{ active: mainImage === image.url }" :aria-label="'查看商品图片 ' + image.id" @click="mainImage = image.url"><img :src="imageUrl(image.url)" :alt="detail.name"></button></div>
      </div><div class="product-info"><p class="eyebrow">{{ detail.categoryPath.at(-1)?.name }}</p><h1>{{ detail.name }}</h1><p class="product-origin" v-if="detail.origin">产地 · {{ detail.origin }}</p>
        <div class="detail-price"><small>¥</small>{{ price(selected?.price ?? null) }}<span class="availability-tag" :class="{ 'is-sold-out': !selected?.availableStock }">{{ selected?.availableStock ? '有货' : '售罄' }}</span></div>
        <div class="specification-section"><h2>选择规格</h2><div class="specification-options"><button v-for="sku in detail.skus" :key="sku.id" :class="{ active: selectedId === sku.id }" :aria-pressed="selectedId === sku.id" @click="selectedId = sku.id">{{ sku.specification }}<small v-if="!sku.availableStock"> · 售罄</small></button></div></div>
        <p class="muted">当前规格可售库存：{{ selected?.availableStock ?? 0 }} 份</p>
        <div v-if="auth.session?.account.role !== 'ADMIN'" class="purchase-actions">
          <label class="form-field">购买数量<input v-model.number="quantity" type="number" min="1" :max="selected?.availableStock ?? 1" step="1" :disabled="!selected?.availableStock"></label>
          <button class="primary-button" :disabled="cartBusy || !selected?.availableStock" @click="addToCart">{{ cartBusy ? '正在加入…' : '加入购物车' }}</button>
        </div>
        <p v-if="cartMessage" class="form-message-success" role="status">{{ cartMessage }}</p>
        <p v-if="cartError" class="form-message-error" role="alert">{{ cartError }}</p>
        <button v-if="auth.session?.account.role !== 'ADMIN'" class="favorite-button" :class="{ 'is-favorited': favorited }" :disabled="favoriteBusy" @click="toggleFavorite">{{ favoriteBusy ? '正在更新…' : favorited ? '♥ 已收藏 · 取消收藏' : '♡ 收藏商品' }}</button>
        <p v-if="favoriteError" class="form-message-error" role="alert">{{ favoriteError }}</p>
        <div class="detail-facts"><div v-if="detail.taste"><span>口感特点</span><p>{{ detail.taste }}</p></div><div v-if="detail.storageMethod"><span>储存方法</span><p>{{ detail.storageMethod }}</p></div></div>
      </div></section>
      <section class="detail-description"><div class="catalog-heading"><h2>商品介绍</h2><span class="muted">认识这份新鲜</span></div><p class="plain-text">{{ detail.description || '暂无详细描述' }}</p></section>
      <section class="detail-reviews"><div class="catalog-heading"><h2>买家评价 <small>({{ detail.reviewSummary.count }})</small></h2><span class="rating-summary">{{ detail.reviewSummary.averageRating === null ? '暂无评分' : '★ ' + detail.reviewSummary.averageRating.toFixed(1) + ' / 5' }}</span></div>
        <PageStatus :loading="reviewsLoading" :error="reviewsError" :empty="!reviews.length" empty-text="暂无评价" @retry="loadReviews" />
        <article v-for="review in reviews" :key="review.id" class="review-item"><div><strong>{{ review.nickname }}</strong><span class="rating-stars">{{ '★'.repeat(review.rating) }}{{ '☆'.repeat(5 - review.rating) }}</span><time>{{ review.createdAt.replace('T', ' ').slice(0, 16) }}</time></div><p class="plain-text">{{ review.content || '买家未填写文字评价' }}</p><small class="muted">规格：{{ review.specification }}</small></article>
        <PagePagination :total="reviewTotal" :page="reviewPage" :busy="reviewsLoading" @change="changeReviewPage" />
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import StoreHeader from '../components/StoreHeader.vue'
import PageStatus from '../components/PageStatus.vue'
import PagePagination from '../components/PagePagination.vue'
import { productDetail, productReviews, price, imageUrl, errorMessage, type ProductDetail, type Review } from '../api/catalog'
import { favoriteState, addFavorite, removeFavorite } from '../api/favorites'
import { addCartItem } from '../api/cart'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const detail = ref<ProductDetail | null>(null), loading = ref(false), error = ref(''), mainImage = ref(''), selectedId = ref(0)
const reviews = ref<Review[]>([]), reviewPage = ref(1), reviewTotal = ref(0), reviewsLoading = ref(false), reviewsError = ref('')
const favorited = ref(false), favoriteBusy = ref(false), favoriteError = ref('')
const quantity = ref(1), cartBusy = ref(false), cartMessage = ref(''), cartError = ref('')
const selected = computed(() => detail.value?.skus.find(s => s.id === selectedId.value))
let generation = 0, reviewGeneration = 0
async function load() {
  const own = ++generation; loading.value = true; error.value = ''; detail.value = null; reviewPage.value = 1
  try { const data = await productDetail(Number(route.params.productId)); if (own !== generation) return; detail.value = data; mainImage.value = data.images[0]?.url ?? ''; selectedId.value = data.skus.find(s => s.availableStock > 0)?.id ?? data.skus[0]?.id ?? 0; await Promise.all([loadReviews(), loadFavorite()]) } catch (e) { if (own === generation) error.value = errorMessage(e) } finally { if (own === generation) loading.value = false }
}
async function loadReviews() { const own = ++reviewGeneration, id = Number(route.params.productId); reviewsLoading.value = true; reviewsError.value = ''; try { const data = await productReviews(id, reviewPage.value); if (own === reviewGeneration && id === Number(route.params.productId)) { reviews.value = data.items; reviewTotal.value = data.total } } catch (e) { if (own === reviewGeneration) reviewsError.value = errorMessage(e) } finally { if (own === reviewGeneration) reviewsLoading.value = false } }
async function loadFavorite() { favorited.value = false; favoriteError.value = ''; if (auth.session?.account.role !== 'BUYER') return; const id = Number(route.params.productId); try { const state = await favoriteState(id); if (id === Number(route.params.productId)) favorited.value = state.favorited } catch (e) { favoriteError.value = errorMessage(e) } }
async function toggleFavorite() { if (!auth.session) { await router.push({ path: '/login', query: { redirect: route.fullPath } }); return } if (auth.session.account.role !== 'BUYER') return; favoriteBusy.value = true; favoriteError.value = ''; try { if (favorited.value) await removeFavorite(Number(route.params.productId)); else await addFavorite(Number(route.params.productId)); favorited.value = !favorited.value } catch (e) { favoriteError.value = errorMessage(e) } finally { favoriteBusy.value = false } }
async function addToCart() {
  if (!auth.session) { await router.push({ path: '/login', query: { redirect: route.fullPath } }); return }
  if (auth.session.account.role !== 'BUYER' || !selected.value) return
  if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > selected.value.availableStock) { cartError.value = '购买数量需为正整数且不能超过可售库存'; return }
  cartBusy.value = true; cartError.value = ''; cartMessage.value = ''
  try { await addCartItem(selected.value.id, quantity.value); cartMessage.value = '已加入购物车' }
  catch (e) { cartError.value = errorMessage(e) }
  finally { cartBusy.value = false }
}
function changeReviewPage(page: number) { reviewPage.value = page; void loadReviews() }
watch(() => route.params.productId, load, { immediate: true })
watch(selectedId, () => { quantity.value = 1; cartError.value = ''; cartMessage.value = '' })
watch(() => auth.session?.accessToken, loadFavorite)
</script>
