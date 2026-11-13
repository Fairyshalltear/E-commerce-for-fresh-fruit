<template>
  <section class="account-section">
    <div class="section-heading">
      <div>
        <h2>账户安全</h2>
        <p>更新密码后需要重新登录。</p>
      </div>
    </div>
    <form class="account-form" @submit.prevent="submit">
      <label class="form-field">
        当前密码
        <input v-model="form.currentPassword" type="password" autocomplete="current-password" required>
      </label>
      <label class="form-field">
        新密码
        <input v-model="form.newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required>
        <span class="field-hint">密码长度为 8 至 72 位</span>
      </label>
      <label class="form-field">
        确认新密码
        <input v-model="form.confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required>
      </label>
      <p v-if="errorMessage" class="form-message form-message-error" role="alert">{{ errorMessage }}</p>
      <div class="form-actions">
        <button class="primary-button" type="submit" :disabled="busy">
          {{ busy ? '正在更新…' : '更新密码' }}
        </button>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import axios from 'axios'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changeBuyerPassword } from '../api/buyer'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const busy = ref(false)
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''
  if (form.newPassword !== form.confirmPassword) {
    errorMessage.value = '两次输入的新密码不一致。'
    return
  }
  busy.value = true
  try {
    await changeBuyerPassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
    auth.logout()
    await router.replace({ name: 'login' })
  } catch (error) {
    errorMessage.value = axios.isAxiosError<{ message?: string }>(error)
      ? error.response?.data?.message ?? '密码更新失败，请稍后重试。'
      : '密码更新失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}
</script>
