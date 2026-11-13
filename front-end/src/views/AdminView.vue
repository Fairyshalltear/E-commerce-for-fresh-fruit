<template>
  <main class="admin-page"><header class="admin-topbar"><RouterLink class="brand" to="/">鲜果集<span class="brand-note">管理后台</span></RouterLink><div class="topbar-actions"><RouterLink to="/">查看商城</RouterLink><span>{{ auth.session?.account.displayName }}</span><button class="text-button" @click="logout">退出登录</button></div></header>
    <div class="admin-layout"><aside class="admin-sidebar"><p class="eyebrow">运营管理</p><RouterLink to="/admin/products" :class="{ active: route.path.startsWith('/admin/products') }">商品管理</RouterLink><RouterLink to="/admin/categories" active-class="active">分类管理</RouterLink><RouterLink to="/admin/buyers" :class="{ active: route.path.startsWith('/admin/buyers') }">买家管理</RouterLink><p class="admin-sidebar-note">商品与买家账户管理</p></aside><section class="admin-content"><RouterView /></section></div>
  </main>
</template>
<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const router = useRouter(), route = useRoute(), auth = useAuthStore()
function logout() { auth.logout(); void router.replace('/') }
</script>
