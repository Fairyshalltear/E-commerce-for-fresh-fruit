<template>
  <main class="page-shell cart-page">
    <StoreHeader />
    <div class="account-heading"><div><p class="eyebrow">买家账户</p><h1>购物车</h1></div><RouterLink class="text-action" to="/products">继续选购</RouterLink></div>
    <p v-if="notice" class="form-message-success" role="status">{{ notice }}</p>
    <PageStatus :loading="loading" :error="error" :empty="!cart?.items.length" empty-text="购物车还是空的，去挑选一些新鲜水果吧。" @retry="load" />
    <template v-if="cart?.items.length && !loading && !error">
      <div class="cart-toolbar"><label class="cart-select-all"><input type="checkbox" :checked="allSelected" :indeterminate="someSelected && !allSelected" @change="selectAll(($event.target as HTMLInputElement).checked)">全选</label><span>{{ cart.items.length }} 件商品</span></div>
      <div class="cart-list">
        <article v-for="item in cart.items" :key="item.id" class="cart-row" :class="{ 'cart-row-unavailable': !item.purchasable }">
          <input type="checkbox" :checked="item.selected" :disabled="busyId === item.id" :aria-label="'选择' + item.productName" @change="selectItem(item, ($event.target as HTMLInputElement).checked)">
          <RouterLink class="cart-image" :to="item.purchasable ? '/products' : '/cart'"><img v-if="item.imageUrl" :src="imageUrl(item.imageUrl)" :alt="item.productName"><span v-else>鲜果</span></RouterLink>
          <div class="cart-item-info"><h2>{{ item.productName }}</h2><p>{{ item.specification }}</p><p v-if="!item.purchasable" class="form-message-error">{{ unavailableText(item) }} · 可售 {{ item.availableStock }} 份</p><p v-else-if="item.priceChanged" class="cart-price-warning">价格已更新，请确认新价格后再结算。</p><p v-else class="muted">可售 {{ item.availableStock }} 份</p></div>
          <div class="cart-price">¥{{ price(item.currentUnitPrice) }}<small v-if="item.priceChanged">原接受价 ¥{{ price(item.acceptedUnitPrice) }}</small></div>
          <label class="cart-quantity"><span class="sr-only">购买数量</span><input type="number" min="1" :max="item.availableStock || 1" :value="item.quantity" :disabled="!item.purchasable || busyId === item.id" @change="changeQuantity(item, ($event.target as HTMLInputElement).value)"></label>
          <strong class="cart-line-total">¥{{ price(item.lineAmount) }}</strong>
          <div class="cart-item-actions"><button v-if="item.priceChanged && item.purchasable" class="text-action" :disabled="busyId === item.id" @click="acceptPrice(item)">接受新价</button><button class="text-action danger-text" :disabled="busyId === item.id" @click="remove(item)">删除</button></div>
        </article>
      </div>
      <div class="cart-summary"><p v-if="checkoutMessage" :class="checkoutValid ? 'form-message-success' : 'form-message-error'" role="status">{{ checkoutMessage }}</p><div><span>已选 {{ cart.selectedCount }} 件商品</span><strong>商品合计 <b>¥{{ price(cart.selectedSubtotal) }}</b></strong></div><p class="muted">运费 ¥{{ price(cart.shippingFee) }}</p><button class="primary-button" :disabled="checkoutBusy || !cart.selectedCount" @click="validateCheckout">{{ checkoutBusy ? '校验中…' : '校验结算商品' }}</button></div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StoreHeader from '../components/StoreHeader.vue'
import PageStatus from '../components/PageStatus.vue'
import { errorMessage, imageUrl, price } from '../api/catalog'
import { acceptCartPrice, getCart, removeCartItem, selectAllCartItems, selectCartItem, updateCartQuantity, validateCartCheckout, type Cart, type CartItem } from '../api/cart'

const cart = ref<Cart | null>(null), loading = ref(false), error = ref(''), notice = ref(''), busyId = ref<number | null>(null)
const checkoutBusy = ref(false), checkoutMessage = ref(''), checkoutValid = ref(false)
const allSelected = computed(() => Boolean(cart.value?.items.length) && cart.value!.items.every(item => item.selected))
const someSelected = computed(() => Boolean(cart.value?.items.some(item => item.selected)))

async function load() { loading.value = true; error.value = ''; try { cart.value = await getCart() } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } }
async function runItem(item: CartItem, action: () => Promise<Cart>) { busyId.value = item.id; notice.value = ''; checkoutMessage.value = ''; try { cart.value = await action() } catch (e) { notice.value = errorMessage(e); await load() } finally { busyId.value = null } }
function selectItem(item: CartItem, selected: boolean) { return runItem(item, () => selectCartItem(item.id, selected)) }
async function selectAll(selected: boolean) { notice.value = ''; try { cart.value = await selectAllCartItems(selected) } catch (e) { notice.value = errorMessage(e) } }
function changeQuantity(item: CartItem, value: string) { const quantity = Number(value); if (!Number.isInteger(quantity) || quantity < 1) { notice.value = '数量必须为正整数'; return } return runItem(item, () => updateCartQuantity(item.id, quantity)) }
function remove(item: CartItem) { return runItem(item, () => removeCartItem(item.id)) }
function acceptPrice(item: CartItem) { return runItem(item, () => acceptCartPrice(item.id, item.currentUnitPrice)) }
async function validateCheckout() { checkoutBusy.value = true; checkoutMessage.value = ''; try { const result = await validateCartCheckout(); cart.value = result.cart; checkoutValid.value = result.valid; checkoutMessage.value = result.message } catch (e) { checkoutValid.value = false; checkoutMessage.value = errorMessage(e) } finally { checkoutBusy.value = false } }
function unavailableText(item: CartItem) { return item.unavailableReason === 'OUT_OF_STOCK' ? '库存不足，暂不可购买' : '商品或规格当前不可购买' }
onMounted(load)
</script>
