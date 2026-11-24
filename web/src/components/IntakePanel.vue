<template>
  <div class="split">
    <section class="panel">
      <h2>查桩</h2>
      <p class="lede">按桩编码或手机号查询设备，并查看这台桩的历史问题单。</p>
      <form class="stack-form" @submit.prevent="$emit('search')">
        <label class="field-label" for="query">桩编码或手机号</label>
        <input id="query" class="text-input" :value="query" placeholder="SIM-AC-001 或 19900000001" autocomplete="off" @input="$emit('update:query', $event.target.value)">
        <button class="btn primary" type="submit" :disabled="busy || !query.trim()">查询</button>
      </form>
      <section v-if="device" class="device-card">
        <h3>{{ device.device }}</h3>
        <p>{{ device.fault }}</p>
        <p class="muted">{{ device.source }} · {{ device.observedAt }}</p>
      </section>
      <h3 v-if="orders.length">历史问题单</h3>
      <div v-if="orders.length">
        <div v-for="item in orders" :key="item.id" class="history-row">
          <button type="button" @click="$emit('open', item.id)">{{ number(item) }} · {{ item.title }}</button>
          <span class="status" :class="status(item).tone"><i></i>{{ status(item).label }}</span>
        </div>
      </div>
      <p v-else-if="device" class="muted">这台桩还没有历史问题单</p>
    </section>
    <section class="panel">
      <h2>新建问题单</h2>
      <p v-if="!device" class="lede">先查询到桩编码，表单会带入该设备。</p>
      <form class="stack-form" @submit.prevent="$emit('create')">
        <label class="field-label" for="device-code">桩编码</label>
        <input id="device-code" class="text-input" :value="device?.device || ''" readonly>
        <label class="field-label" for="title">问题标题</label>
        <input id="title" class="text-input" :value="title" maxlength="150" :disabled="!device" required @input="$emit('update:title', $event.target.value)">
        <label class="field-label" for="description">现象描述</label>
        <textarea id="description" class="text-area" :value="description" maxlength="1000" :disabled="!device" required @input="$emit('update:description', $event.target.value)"></textarea>
        <button class="btn primary" type="submit" :disabled="busy || !device || !title.trim() || !description.trim()">创建问题单</button>
      </form>
    </section>
  </div>
</template>

<script setup>
import { displayStatus, orderNumber } from '../status'

defineProps({
  query: { type: String, default: '' },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  device: { type: Object, default: null },
  orders: { type: Array, default: () => [] },
  busy: Boolean
})
defineEmits(['search', 'create', 'open', 'update:query', 'update:title', 'update:description'])

function number(item) { return orderNumber(item) }
function status(item) { return displayStatus(item) }
</script>
