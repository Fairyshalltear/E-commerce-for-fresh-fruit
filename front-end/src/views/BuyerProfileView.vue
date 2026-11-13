<template>
  <div class="account-section">
    <div class="section-heading">
      <div>
        <h2>个人资料</h2>
        <p>维护用于联系和识别你的账户信息。</p>
      </div>
    </div>

    <div v-if="loading" class="account-state" role="status">正在加载个人资料…</div>
    <form v-else class="account-form" @submit.prevent="save">
      <div class="profile-summary">
        <div class="profile-avatar">
          <img v-if="avatarDisplayUrl" :src="avatarDisplayUrl" alt="个人头像">
          <span v-else aria-hidden="true">{{ initials }}</span>
        </div>
        <div>
          <strong>{{ profile?.nickname || profile?.username }}</strong>
          <p>买家账户</p>
        </div>
        <div class="avatar-actions">
          <input ref="fileInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp"
            @change="selectAvatar">
          <button class="secondary-button" type="button" :disabled="avatarBusy" @click="openFilePicker">
            选择头像
          </button>
          <button v-if="selectedAvatar" class="primary-button" type="button" :disabled="avatarBusy" @click="uploadAvatar">
            {{ avatarBusy ? '正在上传…' : '上传头像' }}
          </button>
        </div>
      </div>
      <p v-if="avatarError" class="form-message form-message-error" role="alert">{{ avatarError }}</p>
      <p v-if="avatarSuccess" class="form-message form-message-success" role="status">{{ avatarSuccess }}</p>

      <label class="form-field">
        用户名
        <input :value="profile?.username ?? ''" autocomplete="username" disabled>
        <span class="field-hint">用户名不可修改</span>
      </label>
      <label class="form-field">
        昵称
        <input v-model.trim="form.nickname" autocomplete="nickname" maxlength="50" required>
      </label>
      <label class="form-field">
        手机号
        <input v-model.trim="form.phone" autocomplete="tel" inputmode="tel" maxlength="11"
          pattern="1[3-9][0-9]{9}" required>
        <span class="field-hint">修改手机号无需短信验证</span>
      </label>

      <p v-if="errorMessage" class="form-message form-message-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="form-message form-message-success" role="status">{{ successMessage }}</p>
      <div class="form-actions">
        <button class="primary-button" type="submit" :disabled="busy">{{ busy ? '正在保存…' : '保存资料' }}</button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { getBuyerProfile, updateBuyerProfile, uploadBuyerAvatar, type BuyerProfile } from '../api/buyer'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const profile = ref<BuyerProfile | null>(null)
const form = reactive({ nickname: '', phone: '' })
const loading = ref(true)
const busy = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const avatarError = ref('')
const avatarSuccess = ref('')
const avatarBusy = ref(false)
const selectedAvatar = ref<File | null>(null)
const previewUrl = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const initials = computed(() => (profile.value?.nickname || profile.value?.username || '买').slice(0, 1))
const avatarDisplayUrl = computed(() => previewUrl.value || profile.value?.avatarUrl || '')

onMounted(load)
onUnmounted(clearPreview)

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    profile.value = await getBuyerProfile()
    form.nickname = profile.value.nickname || ''
    form.phone = profile.value.phone
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '暂时无法加载个人资料。')
  } finally {
    loading.value = false
  }
}

async function save() {
  busy.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    profile.value = await updateBuyerProfile({ nickname: form.nickname, phone: form.phone })
    form.nickname = profile.value.nickname || ''
    auth.updateDisplayName(profile.value.nickname || profile.value.username)
    successMessage.value = '资料已保存。'
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '资料保存失败，请稍后重试。')
  } finally {
    busy.value = false
  }
}

function openFilePicker() {
  avatarError.value = ''
  fileInput.value?.click()
}

function selectAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  avatarSuccess.value = ''
  avatarError.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    avatarError.value = '仅支持 JPEG、PNG 或 WebP 图片。'
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    avatarError.value = '头像图片不能超过 5 MiB。'
    return
  }
  clearPreview()
  selectedAvatar.value = file
  previewUrl.value = URL.createObjectURL(file)
}

async function uploadAvatar() {
  if (!selectedAvatar.value) return
  avatarBusy.value = true
  avatarError.value = ''
  avatarSuccess.value = ''
  try {
    const result = await uploadBuyerAvatar(selectedAvatar.value)
    if (profile.value) profile.value.avatarUrl = result.avatarUrl
    selectedAvatar.value = null
    clearPreview()
    avatarSuccess.value = '头像已更新。'
  } catch (error) {
    avatarError.value = getErrorMessage(error, '头像上传失败，请稍后重试。')
  } finally {
    avatarBusy.value = false
  }
}

function clearPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

function getErrorMessage(error: unknown, fallback: string) {
  return axios.isAxiosError<{ message?: string }>(error)
    ? error.response?.data?.message ?? fallback
    : fallback
}
</script>
