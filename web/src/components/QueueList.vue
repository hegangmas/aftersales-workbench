<template>
  <section class="panel">
    <p v-if="!items.length" class="lede">{{ empty }}</p>
    <ul v-else class="queue">
      <li v-for="item in items" :key="item.problemId || item.id">
        <button type="button" @click="$emit('open', item.problemId || item.id)">
          <span class="order">{{ number(item) }}</span>
          <span>{{ item.title }}</span>
          <span class="meta">{{ meta(item) }}</span>
        </button>
      </li>
    </ul>
    <p class="hint">共 {{ total }} 条<span v-if="items.length < total">，已显示前 {{ items.length }} 条</span></p>
    <button v-if="hasMore" class="btn secondary" type="button" :disabled="busy" @click="$emit('more')">加载更多</button>
  </section>
</template>

<script setup>
import { displayStatus, orderNumber } from '../status'

defineProps({
  items: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  empty: { type: String, default: '' },
  mode: { type: String, default: 'mine' },
  busy: Boolean,
  hasMore: Boolean
})
defineEmits(['open', 'more'])

function number(item) {
  return orderNumber(item)
}
function meta(item) {
  if (item.dueAt) return (item.owner || '') + ' · ' + String(item.dueAt).replace('T', ' ').slice(0, 16) + ' · ' + (item.nextStep || '')
  return (item.device || '无桩编码') + ' · ' + displayStatus(item).label
}
</script>
