<template>
  <img
    class="soongo-logo"
    :src="variant === 'white' ? logoWhite : logoTeal"
    :width="variant === 'white' ? 570 : 715"
    :height="variant === 'white' ? 203 : 254"
    :alt="alt"
    :fetchpriority="priority || undefined"
    :style="width ? { width } : undefined"
  >
</template>

<script setup>
import logoTeal from '../assets/logo-soongo-teal.png'
import logoWhite from '../assets/logo-soongo-white.png'

defineProps({
  variant: { type: String, default: 'teal' },
  alt: { type: String, default: '' },
  priority: { type: String, default: '' },
  width: { type: String, default: '' }
})
</script>

<style scoped>
.soongo-logo {
  display: block;
  height: auto;
  max-width: 100%;
  object-fit: contain;
  background: transparent;
  flex: none;
}
</style>
