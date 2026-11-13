<template>
  <ElDialog
    :model-value="open"
    class="product-status-dialog"
    :class="{ 'product-status-dialog--unlist': targetStatus === 0 }"
    modal-class="product-status-overlay"
    width="460px"
    align-center
    append-to-body
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :before-close="cancel"
    @open-auto-focus="focusCancel"
    @opened="focusCancel"
    @closed="$emit('closed')"
  >
    <template #header="{ titleId }">
      <div class="product-status-heading">
        <span class="product-status-intent" aria-hidden="true"><ElIcon><Upload v-if="targetStatus === 1" /><Download v-else /></ElIcon></span>
        <h2 :id="titleId">{{ targetStatus === 1 ? '上架商品' : '下架商品' }}</h2>
      </div>
      <button type="button" class="product-status-close" :disabled="busy" aria-label="关闭确认弹窗" @click="cancel"><ElIcon><Close /></ElIcon></button>
    </template>

    <template v-if="product">
      <div class="product-status-summary">
        <img v-if="product.product.mainImage && !imageFailed" :src="imageUrl(product.product.mainImage)" alt="" @error="imageFailed = true">
        <span v-else class="product-status-placeholder" aria-label="暂无商品图片"><ElIcon aria-hidden="true"><Picture /></ElIcon></span>
        <div><p class="product-status-name">{{ product.product.name }}</p><p class="product-status-category">{{ product.product.categoryName }}</p></div>
      </div>
      <div class="product-status-transition" aria-label="商品状态变更">
        <span>{{ product.status === 1 ? '已上架' : '已下架' }}</span>
        <ElIcon aria-hidden="true"><Right /></ElIcon>
        <span class="product-status-target">{{ targetStatus === 1 ? '已上架' : '已下架' }}</span>
      </div>
      <p class="product-status-explanation">{{ targetStatus === 1 ? '上架后，商品将在商城展示；库存为 0 时显示售罄。' : '下架后，商品将从商城推荐、列表和详情中隐藏，已有收藏会显示商品不可用。' }}</p>
      <p v-if="error" class="product-status-error" role="alert">{{ error }}</p>
      <p v-if="busy" class="product-status-progress" role="status">{{ reading ? '正在重新读取商品信息…' : targetStatus === 1 ? '正在上架商品…' : '正在下架商品…' }}</p>
    </template>

    <template #footer>
      <div class="product-status-footer">
        <ElButton ref="cancelButton" :disabled="busy" @click="cancel">取消</ElButton>
        <ElButton v-if="reloadRequired" class="product-status-confirm" :loading="reading" :disabled="busy && !reading" @click="$emit('reload')">{{ reading ? '读取中…' : '重新读取' }}</ElButton>
        <ElButton v-else class="product-status-confirm" :loading="busy" :disabled="confirmDisabled || busy" @click="$emit('confirm')">{{ busy ? (targetStatus === 1 ? '上架中…' : '下架中…') : (targetStatus === 1 ? '确认上架' : '确认下架') }}</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElButton, ElDialog, ElIcon, type ButtonInstance } from 'element-plus'
import { Close, Download, Picture, Right, Upload } from '@element-plus/icons-vue'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/icon/style/css'
import type { AdminCard } from '../api/admin-catalog'
import { imageUrl } from '../api/catalog'

const props = defineProps<{
  open: boolean
  product: AdminCard | null
  targetStatus: 0 | 1
  busy: boolean
  reading: boolean
  error: string
  reloadRequired: boolean
  confirmDisabled: boolean
}>()
const emit = defineEmits<{
  cancel: []
  confirm: []
  reload: []
  closed: []
}>()
const cancelButton = ref<ButtonInstance>()
const imageFailed = ref(false)
watch(() => [props.open, props.product?.product.mainImage], () => { imageFailed.value = false })
function cancel() { if (!props.busy) emit('cancel') }
async function focusCancel() { await nextTick(); cancelButton.value?.ref?.focus() }
</script>
