<template>
  <canvas ref="canvasRef" class="captcha-image" title="看不清？点击换一张" @click="refresh" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { createCaptchaCode, drawCaptcha } from '@/utils/captcha'

const canvasRef = ref<HTMLCanvasElement>()
const code = ref('')

/** 换一张：重新随机并重绘 */
function refresh() {
  code.value = createCaptchaCode()
  if (canvasRef.value) {
    drawCaptcha(canvasRef.value, code.value)
  }
}

onMounted(refresh)

defineExpose({
  /** 当前验证码原文，仅供本地校验使用（不写进 DOM / 存储） */
  getCode: () => code.value,
  refresh
})
</script>

<style scoped>
.captcha-image {
  display: block;
  flex: 0 0 auto;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  overflow: hidden;
}
.captcha-image:hover {
  border-color: var(--campus-primary);
}
</style>
