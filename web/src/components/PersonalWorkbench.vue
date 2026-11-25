<template>
  <article class="personal">
    <header class="personal-head"><div><button class="link" @click="$emit('back')">← 返回列表</button><h1>售后个人工作台</h1><p class="muted">处理问题、排查与回访</p></div><div><button class="btn secondary" @click="$emit('intake')">查桩与建单</button> <button class="btn secondary" @click="$emit('mine')">我的待办</button></div></header>
    <div class="case-bar"><strong>{{ orderNumber(problem) }} · {{ problem.title }}</strong><span class="status" :class="status.tone"><i></i>{{ status.label }}</span><span>负责人：{{ problem.owner && problem.owner !== 'early' ? problem.owner : '未认领' }}</span><span v-if="problem.pendingFollowup" class="pending">待回访</span></div>
    <div class="personal-grid">
      <section class="panel"><h2>客户与设备</h2><p class="muted">客户、安装和出厂资料为模拟数据，尚未接入</p><dl class="personal-facts"><dt>桩编码</dt><dd>{{ problem.device || '未关联' }}</dd><dt>客户与地址</dt><dd>李先生 · 合肥市滨湖新区（模拟）</dd><dt>设备型号</dt><dd>7kW 交流桩（模拟）</dd><dt>出厂测试</dt><dd>测试通过（MES 模拟）</dd><dt>设备数据</dt><dd>{{ deviceLoading ? '查询中…' : deviceError || (device && device.source) || '暂无' }}</dd></dl><div v-if="device" class="personal-box"><strong>设备信息</strong><p>{{ device.fault || '暂无故障摘要' }}</p><small>{{ device.observedAt || '时间未知' }}</small></div><h3>历史问题单</h3><p v-if="historyLoading" class="muted">查询中…</p><p v-else-if="historyError" class="muted">{{ historyError }}</p><p v-else-if="!history.length" class="muted">暂无其他问题单</p><ul v-else class="personal-history"><li v-for="item in history" :key="item.id"><button class="link" @click="$emit('open', item.id)">{{ orderNumber(item) }} · {{ item.title }}</button><span>{{ displayStatus(item).label }}</span></li></ul></section>
      <section class="panel"><h2>当前问题与处理</h2><div class="personal-box"><strong>客户描述：</strong>{{ problem.description || '暂无描述' }}</div><div class="personal-data"><div class="personal-box"><strong>最近充电记录</strong><p>11-25 09:15 · 充电完成 · 12.6 kWh</p><p>11-24 19:30 · 充电中断 · 3.2 kWh</p><p class="muted">模拟数据 · 仅用于展示</p></div><div class="personal-box"><strong>最近故障告警</strong><p>11-25 09:40 · PE 断线告警</p><p>11-24 19:32 · 充电启动异常</p><p class="muted">模拟数据 · 仅用于展示</p></div></div><h3>处理时间线</h3><ol v-if="problem.activities?.length" class="timeline personal-timeline"><li v-for="(item, i) in problem.activities" :key="i"><time>{{ dateLabel(item.createdAt) }} <span v-if="item.actor">· {{ item.actor }}</span></time><div>{{ item.note }}</div></li></ol><p v-else class="muted">暂无处理记录</p><div v-if="problem.pendingFollowup" class="personal-box"><strong>下次回访</strong><p>{{ dateLabel(problem.pendingFollowup.dueAt) }} · {{ problem.pendingFollowup.owner }} · {{ problem.pendingFollowup.nextStep }}</p></div><p v-if="handlingDenied" class="permission-note">{{ handlingDenied }}</p><form v-if="canProcess" class="stack-form personal-note" @submit.prevent="$emit('save-note')"><label class="field-label" for="personal-note">跟进记录</label><textarea id="personal-note" class="text-area" :value="note" maxlength="1500" placeholder="记录沟通内容、排查情况及下一步安排" @input="$emit('update:note', $event.target.value)"></textarea><div class="personal-actions"><button class="btn primary" type="submit" :disabled="busy || !note.trim()">保存跟进</button><button v-if="problem.state !== 'field' && !fieldInProgress" class="btn secondary" type="button" @click="goTo('action-callback')">安排回访</button><button v-if="problem.state === 'field' && !fieldInProgress" class="btn secondary" type="button" @click="goTo('action-field')">关联现场单</button></div></form><button v-else-if="status.id === 'unclaimed'" class="btn primary" :disabled="busy" @click="$emit('claim')">认领问题单</button></section>
      <section class="panel"><h2>故障排查与知识</h2><p class="muted">知识库和 AI 排查尚未接入；以下为模拟展示</p><div class="personal-box"><strong>故障分类</strong><p>{{ problem.category || '无法充电（模拟）' }}</p></div><div class="personal-box"><strong>模拟排查提示</strong><p>核对设备在线状态及最近告警，收集故障指示照片，按已审核的售后规范排查。</p></div><h3>素材收集</h3><p class="muted">客户补充资料及发送收集请求尚未接入，可先在跟进记录中记下缺失资料和约定时间。</p><p class="muted">电话集成：未接入</p></section>
    </div><p class="personal-more"><a href="#problem-actions">查看完整处理流程与历史记录 ↓</a></p>
  </article>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { lookupDevice, findProblems } from '../api/workbench'
import { displayStatus, orderNumber } from '../status'
const props = defineProps({ problem: { type: Object, required: true }, busy: Boolean, canProcess: Boolean, fieldInProgress: Boolean, handlingDenied: { type: String, default: '' }, note: { type: String, default: '' } })
defineEmits(['back', 'intake', 'mine', 'open', 'claim', 'save-note', 'update:note'])
const status = computed(() => displayStatus(props.problem))
const device = ref(null), history = ref([]), deviceError = ref(''), historyError = ref(''), deviceLoading = ref(false), historyLoading = ref(false)
let requestId = 0
watch(() => [props.problem.id, props.problem.device], async ([id, code]) => {
  const current = ++requestId
  device.value = null; history.value = []; deviceError.value = ''; historyError.value = ''
  deviceLoading.value = Boolean(code); historyLoading.value = Boolean(code)
  if (!code) return
  await Promise.all([
    lookupDevice(code).then(data => { if (current === requestId) device.value = data }).catch(() => { if (current === requestId) deviceError.value = '设备信息暂不可用' }).finally(() => { if (current === requestId) deviceLoading.value = false }),
    findProblems(code).then(data => { if (current === requestId) history.value = (Array.isArray(data) ? data : []).filter(item => item.id !== id).slice(0, 5) }).catch(() => { if (current === requestId) historyError.value = '历史问题单暂不可用' }).finally(() => { if (current === requestId) historyLoading.value = false })
  ])
}, { immediate: true })
function dateLabel(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '时间未知' }
function goTo(id) { (document.getElementById(id) || document.getElementById('problem-actions'))?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }
</script>
