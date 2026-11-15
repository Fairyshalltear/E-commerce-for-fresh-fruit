<template>
  <main class="page-shell checkout-page"><StoreHeader />
    <div class="account-heading"><div><p class="eyebrow">安全结算</p><h1>确认订单</h1></div><RouterLink class="text-action" to="/cart">返回购物车</RouterLink></div>
    <PageStatus :loading="loading" :error="error" @retry="load" />
    <template v-if="!loading && !error">
      <section class="checkout-section"><h2>收货地址</h2><div v-if="!addresses.length" class="empty-state">请先添加收货地址。<RouterLink to="/account/addresses">管理地址</RouterLink></div>
        <label v-for="address in addresses" :key="address.id" class="checkout-address" :class="{ active: address.id === addressId }"><input v-model.number="addressId" type="radio" :value="address.id"><span><strong>{{ address.recipientName }}</strong> {{ address.phone }}<small>{{ address.province }}{{ address.city }}{{ address.district }}{{ address.detailAddress }}{{ address.defaultAddress ? ' · 默认' : '' }}</small></span></label>
      </section>
      <section class="checkout-section"><h2>商品信息</h2><div v-if="preview" class="checkout-lines"><article v-for="item in preview.items" :key="item.skuId" class="checkout-line"><img v-if="item.imageUrl" :src="imageUrl(item.imageUrl)" :alt="item.productName"><div><strong>{{ item.productName }}</strong><small>{{ item.specification }} · {{ item.quantity }} 件</small><p v-if="!item.purchasable || item.priceChanged" class="form-message-error">{{ item.priceChanged ? '价格已变化，请返回购物车确认新价格' : '商品暂不可购买或库存不足' }}</p></div><span>¥{{ price(item.unitPrice) }} × {{ item.quantity }}</span><strong>¥{{ price(item.lineAmount) }}</strong></article></div><p v-else class="muted">正在校验商品价格和库存…</p></section>
      <section v-if="preview" class="checkout-total"><p>收货至：{{ preview.formattedAddress }}</p><div><span>商品金额</span><strong>¥{{ price(preview.subtotal) }}</strong></div><div><span>运费</span><strong>¥{{ price(preview.shippingFee) }}</strong></div><div class="checkout-grand-total"><span>应付金额</span><strong>¥{{ price(preview.total) }}</strong></div><p v-if="message" :class="preview.valid ? 'form-message-success' : 'form-message-error'" role="status">{{ message }}</p><button class="primary-button" :disabled="busy || !preview.valid || !addressId" @click="placeOrder">{{ busy ? '正在提交…' : '提交订单' }}</button></section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StoreHeader from '../components/StoreHeader.vue'
import PageStatus from '../components/PageStatus.vue'
import { errorMessage, imageUrl, price } from '../api/catalog'
import { listDeliveryAddresses, type DeliveryAddress } from '../api/buyer'
import { checkoutPreview, submitOrder, type CheckoutPreview, type CheckoutRequest } from '../api/orders'

const route = useRoute(), router = useRouter()
const addresses = ref<DeliveryAddress[]>([]), addressId = ref(0), preview = ref<CheckoutPreview | null>(null)
const loading = ref(true), busy = ref(false), error = ref(''), message = ref('')
const requestKey = crypto.randomUUID()
function payload(): CheckoutRequest { const skuId = Number(route.query.skuId), quantity = Number(route.query.quantity); return { addressId: addressId.value, requestKey, ...(skuId > 0 ? { items: [{ skuId, quantity: Number.isInteger(quantity) && quantity > 0 ? quantity : 1 }] } : {}) } }
async function load() { loading.value = true; error.value = ''; try { addresses.value = await listDeliveryAddresses(); addressId.value = addresses.value.find(a => a.defaultAddress)?.id ?? addresses.value[0]?.id ?? 0; if (addressId.value) await refreshPreview() } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } }
async function refreshPreview() { preview.value = null; message.value = ''; if (!addressId.value) return; try { preview.value = await checkoutPreview(payload()); message.value = preview.value.message } catch (e) { message.value = errorMessage(e) } }
async function placeOrder() { if (!preview.value?.valid || busy.value) return; busy.value = true; message.value = ''; try { const order = await submitOrder(payload()); await router.replace({ name: 'account-orders', query: { orderId: String(order.id) } }) } catch (e) { message.value = errorMessage(e); await refreshPreview() } finally { busy.value = false } }
watch(addressId, () => { if (!loading.value) void refreshPreview() })
onMounted(load)
</script>
