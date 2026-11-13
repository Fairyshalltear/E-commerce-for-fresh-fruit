<template>
  <section><div class="section-heading"><div><h2>我的收藏</h2><p>留住喜欢的水果，下次更容易找到。</p></div><RouterLink class="text-action" to="/products">继续挑选 →</RouterLink></div>
    <PageStatus :loading="loading" :error="error" :empty="!items.length" empty-text="还没有收藏，去挑选喜欢的水果吧。" @retry="load" />
    <div v-if="!loading && !error" class="product-grid favorites-grid"><template v-for="item in items" :key="item.productId">
      <ProductCard v-if="item.available && item.product" :product="item.product"><div class="favorite-card-actions"><button class="text-action" :disabled="busyId === item.productId" @click="remove(item.productId)">取消收藏</button></div></ProductCard>
      <article v-else class="product-card unavailable-card"><div class="product-picture"><img v-if="item.mainImageSnapshot" :src="imageUrl(item.mainImageSnapshot)" :alt="item.nameSnapshot"><span class="sold-out-badge">商品不可用</span></div><div class="product-card-body"><h3>{{ item.nameSnapshot }}</h3><p class="muted">{{ reason(item.unavailableReason) }}</p></div><div class="favorite-card-actions"><button class="text-action" :disabled="busyId === item.productId" @click="remove(item.productId)">取消收藏</button></div></article>
    </template></div><PagePagination :total="total" :page="page" :busy="loading" @change="changePage" />
  </section>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ProductCard from '../components/ProductCard.vue'
import PageStatus from '../components/PageStatus.vue'
import PagePagination from '../components/PagePagination.vue'
import { listFavorites, removeFavorite, type Favorite } from '../api/favorites'
import { imageUrl, errorMessage } from '../api/catalog'
const items = ref<Favorite[]>([]), page = ref(1), total = ref(0), loading = ref(false), error = ref(''), busyId = ref<number | null>(null)
async function load() { loading.value = true; error.value = ''; try { const result = await listFavorites(page.value); items.value = result.items; total.value = result.total; if (!items.value.length && page.value > 1) { page.value--; await load() } } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } }
async function remove(id: number) { busyId.value = id; try { await removeFavorite(id); await load() } catch (e) { error.value = errorMessage(e) } finally { busyId.value = null } }
function changePage(next: number) { page.value = next; void load() }
function reason(value: string | null) { return value === 'DELETED' ? '该商品已被删除' : value === 'UNLISTED' ? '该商品已下架' : '所属分类暂不可用' }
onMounted(load)
</script>
