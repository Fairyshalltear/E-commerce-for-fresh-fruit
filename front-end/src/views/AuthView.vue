<template>
  <main class="auth-page">
    <RouterLink class="auth-back" to="/">← 返回鲜果集</RouterLink>

    <section class="auth-panel" aria-labelledby="auth-title">
      <p class="eyebrow">鲜果集账户</p>
      <h1 id="auth-title">{{ isRegister ? '创建买家账户' : role === 'ADMIN' ? '管理员登录' : '买家登录' }}</h1>
      <p class="auth-intro">{{ role === 'ADMIN' ? '使用本地配置的管理员账户进入后台。' : '登录后管理地址、购物车和订单。' }}</p>

      <div class="role-switch" aria-label="账户类型">
        <button type="button" :aria-pressed="role === 'BUYER'" @click="setRole('BUYER')">买家</button>
        <button type="button" :aria-pressed="role === 'ADMIN'" @click="setRole('ADMIN')">管理员</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <template v-if="role === 'BUYER' && isRegister">
          <label class="form-field">
            用户名
            <input v-model.trim="username" autocomplete="username" minlength="3" maxlength="50" required>
          </label>
          <label class="form-field">
            手机号
            <input v-model.trim="phone" autocomplete="tel" inputmode="tel" required>
          </label>
          <label class="form-field">
            昵称（选填）
            <input v-model.trim="nickname" autocomplete="nickname" maxlength="50">
          </label>
        </template>
        <label v-else class="form-field">
          {{ role === 'ADMIN' ? '管理员用户名' : '用户名或手机号' }}
          <input
            v-model.trim="identity"
            :autocomplete="role === 'ADMIN' ? 'username' : 'username'"
            required
          >
        </label>
        <label class="form-field">
          密码
          <input
            v-model="password"
            type="password"
            :autocomplete="isRegister ? 'new-password' : 'current-password'"
            minlength="8"
            maxlength="72"
            required
          >
        </label>
        <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>
        <button class="auth-submit" type="submit" :disabled="busy">
          {{ busy ? '正在提交…' : isRegister ? '注册并登录' : '登录' }}
        </button>
      </form>

      <p v-if="role === 'BUYER'" class="auth-toggle">
        {{ isRegister ? '已有账户？' : '还没有账户？' }}
        <button type="button" @click="toggleMode">{{ isRegister ? '立即登录' : '注册买家账户' }}</button>
      </p>
    </section>
  </main>
</template>

<script setup lang="ts">
import axios from 'axios'
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
type LoginRole = 'BUYER' | 'ADMIN'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const role = ref<LoginRole>(route.query.role === 'admin' ? 'ADMIN' : 'BUYER')
const isRegister = ref(false)
const identity = ref('')
const username = ref('')
const phone = ref('')
const nickname = ref('')
const password = ref('')
const busy = ref(false)
const errorMessage = ref('')

watch(() => route.query.role, (value) => {
  setRole(value === 'admin' ? 'ADMIN' : 'BUYER')
})

function setRole(nextRole: LoginRole) {
  role.value = nextRole
  isRegister.value = false
  errorMessage.value = ''
  password.value = ''
}

function toggleMode() {
  isRegister.value = !isRegister.value
  errorMessage.value = ''
  password.value = ''
}

async function submit() {
  busy.value = true
  errorMessage.value = ''
  try {
    if (role.value === 'ADMIN') {
      await auth.loginAdmin({ username: identity.value, password: password.value })
    } else if (isRegister.value) {
      await auth.register({
        username: username.value,
        phone: phone.value,
        password: password.value,
        nickname: nickname.value || undefined,
      })
    } else {
      await auth.loginBuyer({ usernameOrPhone: identity.value, password: password.value })
    }

    const defaultRoute = auth.session?.account.role !== 'BUYER' ? '/admin' : '/account'
    const redirect = typeof route.query.redirect === 'string'
      && route.query.redirect.startsWith('/')
      && !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : defaultRoute
    await router.replace(redirect)
  } catch (error) {
    if (axios.isAxiosError<{ message?: string }>(error)) {
      errorMessage.value = error.response?.data?.message ?? '无法连接服务器，请稍后重试。'
    } else {
      errorMessage.value = '登录失败，请稍后重试。'
    }
  } finally {
    busy.value = false
  }
}
</script>
