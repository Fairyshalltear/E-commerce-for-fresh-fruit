<template>
  <main class="page-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/" aria-label="鲜果集首页">鲜果集</RouterLink>
      <nav class="topbar-actions" aria-label="账户导航">
        <RouterLink v-if="!auth.session" to="/login">买家登录 / 注册</RouterLink>
        <RouterLink v-if="!auth.session" to="/login?role=admin">管理后台</RouterLink>
        <RouterLink v-if="auth.session" :to="auth.session.account.role === 'ADMIN' ? '/admin' : '/account'">
          {{ auth.session.account.displayName }}
        </RouterLink>
        <button v-if="auth.session" class="text-button" type="button" @click="signOut">退出</button>
      </nav>
    </header>
    <section class="welcome">
      <p class="eyebrow">鲜果集 · 生鲜水果电商平台</p>
      <h1>基础服务已就绪</h1>
      <p class="welcome-copy">账户注册、买家登录与管理员登录已开放。</p>
      <div class="welcome-actions">
        <RouterLink class="primary-link" to="/login">进入买家端</RouterLink>
        <RouterLink class="secondary-link" to="/login?role=admin">进入管理端</RouterLink>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

function signOut() {
  auth.logout()
  void router.push('/')
}
</script>
