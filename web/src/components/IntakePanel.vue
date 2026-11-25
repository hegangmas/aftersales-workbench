<template>
  <div class="split">
    <section class="panel">
      <h2>查桩与防重检查</h2>
      <p class="lede">按桩编码或手机号查询设备，自动核查同桩未闭环工单，避免重复建单。</p>
      <form class="stack-form" @submit.prevent="$emit('search')">
        <label class="field-label" for="query">桩编码或手机号</label>
        <input id="query" class="text-input" :value="query" placeholder="SIM-AC-001 / 19900000001 或 SIM-DC-002 / 19900000002" autocomplete="off" @input="$emit('update:query', $event.target.value)">
        <button class="btn primary" type="submit" :disabled="busy || !query.trim()">查询</button>
      </form>
      <section v-if="device" class="device-card">
        <h3>{{ device.device }} · {{ device.model || '模拟设备' }}</h3>
        <p>{{ device.fault }}</p>
        <p class="muted">{{ device.customerName }} · {{ device.siteAddress }}</p>
        <p class="muted">{{ device.onlineStatus }} · {{ device.warrantyStatus }} · {{ device.source }} · {{ device.observedAt }}</p>
      </section>
      <section v-if="openOrders.length" class="dup-alert" role="status">
        <strong>发现 {{ openOrders.length }} 条同桩未闭环工单（建议优先进入原单续办，避免重复建单）</strong>
        <div v-for="item in openOrders" :key="item.id" class="history-row">
          <button type="button" @click="$emit('open', item.id)">{{ number(item) }} · {{ item.title }}</button>
          <span class="status" :class="status(item).tone"><i></i>{{ status(item).label }}</span>
        </div>
      </section>
      <h3 v-if="orders.length">全部历史问题单</h3>
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
      <form class="stack-form" @submit.prevent="submitCreate">
        <label class="field-label" for="device-code">桩编码</label>
        <input id="device-code" class="text-input" :value="device?.device || ''" readonly>
        <div class="inline-fields">
          <div>
            <label class="field-label" for="channel">报修渠道</label>
            <select id="channel" v-model="channel" class="text-input" :disabled="!device">
              <option value="400">400电话</option>
              <option value="miniprogram">客户小程序</option>
              <option value="provider">服务商扫码</option>
              <option value="wecom">企微群</option>
              <option value="phone">电话受理</option>
            </select>
          </div>
          <div>
            <label class="field-label" for="category">故障分类（匹配 SOP）</label>
            <select id="category" v-model="category" class="text-input" :disabled="!device">
              <option value="充电故障">充电故障</option>
              <option value="离线与通信异常">离线与通信异常</option>
              <option value="硬件外观与枪头异常">硬件外观与枪头异常</option>
            </select>
          </div>
        </div>
        <label class="field-label" for="fault-at">故障发生时点（北京时间，可选）</label>
        <input id="fault-at" v-model="faultOccurredAt" class="text-input" type="datetime-local" :disabled="!device">
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
import { computed, ref } from 'vue'
import { displayStatus, orderNumber } from '../status'

const props = defineProps({
  query: { type: String, default: '' },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  device: { type: Object, default: null },
  orders: { type: Array, default: () => [] },
  busy: Boolean
})
const emit = defineEmits(['search', 'create', 'open', 'update:query', 'update:title', 'update:description'])

const channel = ref('400')
const category = ref('充电故障')
const faultOccurredAt = ref('')
const openOrders = computed(() => (props.orders || []).filter(item => item.state !== 'resolved'))

function number(item) { return orderNumber(item) }
function status(item) { return displayStatus(item) }
function submitCreate() {
  emit('create', {
    channel: channel.value,
    category: category.value,
    faultOccurredAt: faultOccurredAt.value || null
  })
}
</script>
