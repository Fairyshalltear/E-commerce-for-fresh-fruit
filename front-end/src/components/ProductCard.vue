<template>
  <article class="product-card">
    <RouterLink :to="'/products/' + product.id" class="product-card-link">
      <div class="product-picture">
        <img v-if="product.mainImage" :src="imageUrl(product.mainImage)" :alt="product.name" loading="lazy">
        <span v-else class="picture-placeholder">暂无图片</span>
        <span v-if="product.soldOut" class="sold-out-badge">售罄</span>
      </div>
      <div class="product-card-body"><p class="product-category">{{ product.categoryName }}</p><h3>{{ product.name }}</h3>
        <div class="product-card-bottom"><span class="product-price"><small>¥</small>{{ price(product.minPrice) }}<small v-if="product.minPrice !== product.maxPrice">起</small></span><span class="product-stock">{{ product.soldOut ? '补货中' : '库存 ' + product.availableStock }}</span></div>
      </div>
    </RouterLink>
    <slot />
  </article>
</template>
<script setup lang="ts">
import { price, imageUrl, type ProductCard } from '../api/catalog'
defineProps<{ product: ProductCard }>()
</script>
