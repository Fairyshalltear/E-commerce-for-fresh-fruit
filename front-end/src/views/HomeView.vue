<template>
  <main class="page-shell"><StoreHeader />
    <section class="store-hero"><div><p class="eyebrow">FRESH FRUIT · 鲜果集</p><h1>把一季的新鲜，<br>带进每一天。</h1><p class="hero-copy">挑选喜欢的水果，让生活多一份自然的甜。</p>
      <form class="hero-search" @submit.prevent="search"><label class="visually-hidden" for="home-keyword">搜索水果</label><input id="home-keyword" v-model.trim="keyword" placeholder="搜索水果名称，例如苹果、橙子"><button class="primary-button">搜索水果</button></form>
    </div><div v-if="carousels.length" class="hero-art has-carousel"><a :href="activeCarousel.targetUrl || '/products'" :target="external(activeCarousel.targetUrl)?'_blank':undefined" rel="noopener"><img :src="imageUrl(activeCarousel.imageUrl)" :alt="activeCarousel.title || '鲜果集首页轮播图'"><span v-if="activeCarousel.title" class="carousel-caption">{{ activeCarousel.title }}</span></a><button class="carousel-arrow previous" type="button" aria-label="上一张" title="上一张" @click="moveCarousel(-1)">‹</button><button class="carousel-arrow next" type="button" aria-label="下一张" title="下一张" @click="moveCarousel(1)">›</button><div class="carousel-dots"><button v-for="(item,index) in carousels" :key="item.id" type="button" :aria-label="`第 ${index+1} 张`" :aria-pressed="index===carouselIndex" @click="carouselIndex=index"></button></div></div><div v-else class="hero-art" aria-hidden="true"><span class="fruit-orbit"></span><span class="fruit-shape fruit-orange"></span><span class="fruit-shape fruit-green"></span><span class="hero-art-note">自然好味 · 新鲜之选</span></div></section>
    <section v-if="announcements.length" class="home-announcements"><div class="catalog-heading"><div><p class="eyebrow">商城公告</p><h2>店铺动态</h2></div></div><ul><li v-for="item in announcements" :key="item.id"><button type="button" @click="openAnnouncement(item.id)"><span>{{ item.pinned ? '置顶' : '公告' }}</span><strong>{{ item.title }}</strong><small>{{ item.summary }}</small><time>{{ new Date(item.publishedAt).toLocaleDateString('zh-CN') }}</time></button></li></ul></section>
    <dialog ref="announcementDialog" class="announcement-dialog"><button class="dialog-close" type="button" aria-label="关闭公告" @click="announcementDialog?.close()">×</button><p class="eyebrow">商城公告</p><h2>{{ announcementDetail?.title }}</h2><time>{{ announcementDetail?.startAt ? new Date(announcementDetail.startAt).toLocaleString('zh-CN') : '' }}</time><p class="announcement-body">{{ announcementDetail?.body }}</p></dialog>
    <section class="category-strip" aria-label="商品分类"><RouterLink to="/products">全部水果</RouterLink><RouterLink v-for="category in tree" :key="category.id" :to="{ path: '/products', query: { categoryId: category.id } }">{{ category.name }}</RouterLink></section>
    <section class="catalog-section"><div class="catalog-heading"><div><p class="eyebrow">为你精选</p><h2>今日鲜果推荐</h2></div><RouterLink class="text-action" to="/products">浏览全部水果 →</RouterLink></div>
      <PageStatus :loading="loading" :error="error" :empty="!items.length" empty-text="新鲜水果正在准备中，敬请期待。" @retry="load" />
      <div v-if="!loading && !error" class="product-grid"><ProductCard v-for="item in items" :key="item.id" :product="item" /></div>
    </section><footer class="store-footer">鲜果集 · 愿你的每一天，都有新鲜好味。</footer>
  </main>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import StoreHeader from '../components/StoreHeader.vue'
import ProductCard from '../components/ProductCard.vue'
import PageStatus from '../components/PageStatus.vue'
import { categories, recommendations, errorMessage, imageUrl, type Category, type ProductCard as Card } from '../api/catalog'
import http,{type ApiResponse} from '../api/http'
const router = useRouter(), keyword = ref(''), tree = ref<Category[]>([]), items = ref<Card[]>([]), loading = ref(true), error = ref('')
interface Carousel { id:number; imageUrl:string; title:string|null; targetUrl:string|null }
interface AnnouncementSummary { id:number; title:string; summary:string; pinned:boolean; publishedAt:string }
const carousels=ref<Carousel[]>([]),announcements=ref<AnnouncementSummary[]>([]),carouselIndex=ref(0),announcementDialog=ref<HTMLDialogElement>(),announcementDetail=ref<{title:string;body:string;startAt:string}|null>(null)
const activeCarousel=computed(()=>carousels.value[carouselIndex.value]??{id:0,imageUrl:'',title:'',targetUrl:null})
async function load() { loading.value = true; error.value = ''; try { [tree.value, items.value] = await Promise.all([categories(), recommendations()]) } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } }
function search() { void router.push({ path: '/products', query: keyword.value ? { keyword: keyword.value } : {} }) }
function external(target:string|null){return Boolean(target&&/^https?:\/\//i.test(target))}
function moveCarousel(direction:number){carouselIndex.value=(carouselIndex.value+direction+carousels.value.length)%carousels.value.length}
async function openAnnouncement(id:number){try{announcementDetail.value=(await http.get<ApiResponse<{title:string;body:string;startAt:string}>>(`/api/v1/public/content/announcements/${id}`)).data.data;announcementDialog.value?.showModal()}catch{error.value='公告内容暂时无法读取。'}}
let carouselTimer=0
onMounted(async()=>{await load();try{const [c,a]=await Promise.all([http.get<ApiResponse<Carousel[]>>('/api/v1/public/content/carousels'),http.get<ApiResponse<AnnouncementSummary[]>>('/api/v1/public/content/announcements')]);carousels.value=c.data.data;announcements.value=a.data.data;if(carousels.value.length>1)carouselTimer=window.setInterval(()=>moveCarousel(1),6000)}catch{/* Published content is optional; keep the storefront usable. */}})
onBeforeUnmount(()=>window.clearInterval(carouselTimer))
</script>
