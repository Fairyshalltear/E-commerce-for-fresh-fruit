<template>
  <section class="account-section">
    <div class="section-heading">
      <div>
        <h2>收货地址</h2>
        <p>最多保存 5 个地址，默认地址可在下单时优先使用。</p>
      </div>
      <div class="address-heading-actions">
        <span class="address-count">{{ addresses.length }}/5</span>
        <button class="primary-button" type="button" :disabled="busy || addresses.length >= 5" @click="startCreate">
          <Plus class="button-icon" aria-hidden="true" />
          新增地址
        </button>
      </div>
    </div>

    <p v-if="errorMessage" class="form-message form-message-error" role="alert">{{ errorMessage }}</p>
    <div v-if="loading" class="account-state" role="status">正在加载收货地址…</div>

    <form v-if="editing" class="address-editor" @submit.prevent="saveAddress">
      <div class="address-editor-heading">
        <h3>{{ editingId === null ? '新增收货地址' : '编辑收货地址' }}</h3>
        <button class="icon-button" type="button" aria-label="取消编辑" title="取消编辑" @click="cancelEdit">
          <Close class="button-icon" aria-hidden="true" />
        </button>
      </div>
      <div class="address-fields">
        <label class="form-field">
          收货人
          <input v-model.trim="form.recipientName" maxlength="50" required>
        </label>
        <label class="form-field">
          联系电话
          <input v-model.trim="form.phone" inputmode="tel" autocomplete="tel" maxlength="11"
            pattern="1[3-9][0-9]{9}" required>
        </label>
        <label class="form-field">
          省份
          <input v-model.trim="form.province" maxlength="50" required>
        </label>
        <label class="form-field">
          城市
          <input v-model.trim="form.city" maxlength="50" required>
        </label>
        <label class="form-field">
          区县
          <input v-model.trim="form.district" maxlength="50" required>
        </label>
        <label class="form-field address-field-wide">
          详细地址
          <input v-model.trim="form.detailAddress" maxlength="200" required>
        </label>
        <fieldset class="address-tag-field">
          <legend>地址标签</legend>
          <label class="tag-option">
            <input v-model="form.addressTag" type="radio" value="HOME">
            家
          </label>
          <label class="tag-option">
            <input v-model="form.addressTag" type="radio" value="COMPANY">
            公司
          </label>
        </fieldset>
        <label v-if="editingId === null" class="checkbox-field">
          <input v-model="form.defaultAddress" type="checkbox">
          设为默认地址
        </label>
      </div>
      <div class="form-actions">
        <button class="primary-button" type="submit" :disabled="busy">
          {{ busy ? '正在保存…' : '保存地址' }}
        </button>
        <button class="secondary-button" type="button" :disabled="busy" @click="cancelEdit">取消</button>
      </div>
    </form>

    <div v-if="!loading && addresses.length === 0" class="account-state address-empty">
      还没有收货地址。添加地址后可用于后续购物。
    </div>
    <div v-else-if="!loading" class="address-list">
      <article v-for="address in addresses" :key="address.id" class="address-item">
        <div class="address-main">
          <div class="address-recipient">
            <strong>{{ address.recipientName }}</strong>
            <span class="address-phone">{{ address.phone }}</span>
            <span class="address-tag">{{ address.addressTag === 'HOME' ? '家' : '公司' }}</span>
            <span v-if="address.defaultAddress" class="default-tag">默认</span>
          </div>
          <p class="address-location">
            {{ address.province }}{{ address.city }}{{ address.district }}{{ address.detailAddress }}
          </p>
        </div>
        <div class="address-actions">
          <button v-if="!address.defaultAddress" class="text-action" type="button" @click="makeDefault(address)">
            <Star class="button-icon" aria-hidden="true" />
            设为默认
          </button>
          <button class="icon-button" type="button" :aria-label="`编辑${address.recipientName}的地址`"
            title="编辑地址" @click="startEdit(address)">
            <Edit class="button-icon" aria-hidden="true" />
          </button>
          <button class="icon-button icon-button-danger" type="button" :aria-label="`删除${address.recipientName}的地址`"
            title="删除地址" @click="removeAddress(address)">
            <Delete class="button-icon" aria-hidden="true" />
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, reactive, ref } from 'vue'
import { Close, Delete, Edit, Plus, Star } from '@element-plus/icons-vue'
import {
  createDeliveryAddress,
  deleteDeliveryAddress,
  listDeliveryAddresses,
  setDefaultDeliveryAddress,
  updateDeliveryAddress,
  type DeliveryAddress,
  type DeliveryAddressPayload,
} from '../api/buyer'

const addresses = ref<DeliveryAddress[]>([])
const loading = ref(true)
const busy = ref(false)
const editing = ref(false)
const editingId = ref<number | null>(null)
const errorMessage = ref('')
const blankForm = () => ({
  recipientName: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  addressTag: 'HOME' as 'HOME' | 'COMPANY',
  defaultAddress: false,
})
const form = reactive(blankForm())
const canAdd = computed(() => addresses.value.length < 5)

onMounted(loadAddresses)

async function loadAddresses() {
  loading.value = true
  errorMessage.value = ''
  try {
    addresses.value = await listDeliveryAddresses()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '暂时无法加载收货地址。')
  } finally {
    loading.value = false
  }
}

function startCreate() {
  if (!canAdd.value) return
  Object.assign(form, blankForm())
  editingId.value = null
  editing.value = true
  errorMessage.value = ''
}

function startEdit(address: DeliveryAddress) {
  Object.assign(form, {
    recipientName: address.recipientName,
    phone: address.phone,
    province: address.province,
    city: address.city,
    district: address.district,
    detailAddress: address.detailAddress,
    addressTag: address.addressTag,
    defaultAddress: false,
  })
  editingId.value = address.id
  editing.value = true
  errorMessage.value = ''
}

function cancelEdit() {
  editing.value = false
  editingId.value = null
  Object.assign(form, blankForm())
}

async function saveAddress() {
  busy.value = true
  errorMessage.value = ''
  const payload: DeliveryAddressPayload = {
    recipientName: form.recipientName,
    phone: form.phone,
    province: form.province,
    city: form.city,
    district: form.district,
    detailAddress: form.detailAddress,
    addressTag: form.addressTag,
  }
  try {
    if (editingId.value === null) {
      await createDeliveryAddress({ ...payload, defaultAddress: form.defaultAddress })
    } else {
      await updateDeliveryAddress(editingId.value, payload)
    }
    cancelEdit()
    await loadAddresses()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '地址保存失败，请稍后重试。')
  } finally {
    busy.value = false
  }
}

async function makeDefault(address: DeliveryAddress) {
  errorMessage.value = ''
  try {
    await setDefaultDeliveryAddress(address.id)
    await loadAddresses()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '默认地址设置失败，请稍后重试。')
  }
}

async function removeAddress(address: DeliveryAddress) {
  if (!window.confirm(`确定删除 ${address.recipientName} 的收货地址吗？`)) return
  errorMessage.value = ''
  try {
    await deleteDeliveryAddress(address.id)
    if (editingId.value === address.id) cancelEdit()
    await loadAddresses()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '地址删除失败，请稍后重试。')
  }
}

function getErrorMessage(error: unknown, fallback: string) {
  return axios.isAxiosError<{ message?: string }>(error)
    ? error.response?.data?.message ?? fallback
    : fallback
}
</script>
