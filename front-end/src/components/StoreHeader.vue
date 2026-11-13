<template>
  <header class="topbar store-topbar">
    <RouterLink class="brand" to="/">鲜果集<span class="brand-note">新鲜，每一天</span></RouterLink>
    <nav class="topbar-actions" aria-label="商城导航">
      <RouterLink to="/products">全部水果</RouterLink>
      <RouterLink v-if="auth.session?.account.role === 'BUYER'" to="/cart">购物车</RouterLink>
      <RouterLink v-if="auth.session?.account.role === 'BUYER'" to="/account/favorites">我的收藏</RouterLink>
      <RouterLink v-if="auth.session" :to="auth.session.account.role === 'ADMIN' ? '/admin' : '/account'">{{ auth.session.account.displayName }}</RouterLink>
      <RouterLink v-else to="/login">登录 / 注册</RouterLink>
      <RouterLink v-if="!auth.session" to="/login?role=admin">管理后台</RouterLink>
      <button v-if="auth.session" class="text-button" @click="logout">退出</button>
    </nav>
  </header>
</template>
<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
function logout() { auth.logout(); void router.push('/') }
</script>
