<template>
  <section class="board">
    <div class="toolbar">
      <div class="segments" role="toolbar" aria-label="状态筛选">
        <button v-for="id in buckets" :key="id || 'all'" type="button" class="segment" :aria-pressed="filters.bucket === id" @click="chooseBucket(id)">
          {{ labels[id] }} <span>{{ count(id) }}</span>
        </button>
      </div>
      <form class="filters" @submit.prevent="emitFilters(filters.bucket)">
        <label class="control">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4.5 4.5" /></svg>
          <span class="visually-hidden">搜索</span>
          <input v-model="q" type="search" name="q" placeholder="搜索单号、桩编码或标题…" maxlength="80" autocomplete="off" enterkeyhint="search">
        </label>
        <label class="control">
          <span class="visually-hidden">类型</span>
          <select v-model="kind" name="kind" aria-label="类型" @change="emitFilters(filters.bucket)">
            <option value="">全部类型</option>
            <option value="fault">故障</option>
            <option value="consult">咨询</option>
            <option value="service">服务</option>
          </select>
        </label>
        <label class="control">
          <span class="visually-hidden">故障分类</span>
          <select v-model="category" name="category" aria-label="故障分类" @change="emitFilters(filters.bucket)">
            <option value="">全部分类</option>
            <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label class="control">
          <span class="visually-hidden">负责人</span>
          <select v-model="owner" name="owner" aria-label="负责人" @change="emitFilters(filters.bucket)">
            <option value="">全部负责人</option>
            <option v-for="item in assignees" :key="item.username" :value="item.username">{{ item.username }}</option>
          </select>
        </label>
        <button class="icon-button" type="button" aria-label="重置筛选" @click="reset">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10a9 9 0 1 1 2 9M3 4v6h6" /></svg>
        </button>
      </form>
    </div>
    <p v-if="narrowed" class="filter-note">当前筛选 {{ total }} 条。上方摘要和分段计数仍是全部问题单。</p>
    <p v-if="!items.length" class="empty-row">没有符合条件的问题单</p>
    <div v-else class="table-scroll">
      <table class="tickets">
        <caption class="visually-hidden">问题单总表</caption>
        <colgroup>
          <col style="width:8%"><col style="width:26%"><col style="width:14%"><col style="width:12%"><col style="width:10%"><col style="width:14%"><col style="width:8%"><col style="width:8%">
        </colgroup>
        <thead>
          <tr>
            <th>单号</th><th>问题标题</th><th>桩编码</th><th>状态</th><th>负责人</th><th>下次回访</th><th>更新时间</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id" :class="{ pool: unowned(item) && item.state !== 'resolved' }">
            <td><button type="button" class="order-link" @click="$emit('open', item.id)">{{ number(item) }}</button></td>
            <td>
              <div class="title-line">
                <span v-if="kindLabel(item.kind)" class="type-tag" :class="item.kind">{{ kindLabel(item.kind) }}</span>
                <button type="button" class="title-button clip" @click="$emit('open', item.id)">{{ item.title }}</button>
              </div>
              <div v-if="item.category" class="sub clip">{{ item.category }}<span v-if="item.channel"> · {{ channel(item.channel) }}</span></div>
              <div v-if="badges(item).length" class="meta-tags">
                <span v-for="tag in badges(item)" :key="tag.text" class="mini-tag" :class="tag.tone">{{ tag.text }}</span>
              </div>
            </td>
            <td><div class="device-code">{{ item.device || '无' }}</div></td>
            <td><span class="status" :class="status(item).tone"><i></i>{{ status(item).label }}</span></td>
            <td>
              <div class="owner" :class="{ unowned: unowned(item) }">
                <span class="avatar mini" :class="unowned(item) ? 'none' : tone(item.owner)">{{ unowned(item) ? '' : initial(item.owner) }}</span>
                <span class="clip">{{ unowned(item) ? '未认领' : item.owner }}</span>
              </div>
            </td>
            <td>
              <div class="callback" :class="due(item).tone"><i v-if="due(item).tone === 'overdue' || due(item).tone === 'today'"></i>{{ due(item).text }}</div>
              <div v-if="hint(item)" class="sub clip">{{ hint(item) }}</div>
            </td>
            <td>
              <div class="updated">{{ updated(item.updatedAt).text }}</div>
              <div class="sub">{{ updated(item.updatedAt).year }}</div>
            </td>
            <td><button type="button" :class="action(item).claim ? 'claim-btn' : 'text-action'" :disabled="busy" @click="runAction(item)">{{ action(item).label }}</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="items.length" class="cards">
      <article v-for="item in items" :key="'card-' + item.id" class="ticket-card" :class="{ pool: unowned(item) && item.state !== 'resolved' }">
        <div class="ticket-top">
          <button type="button" class="order-link" @click="$emit('open', item.id)">{{ number(item) }}</button>
          <span class="status" :class="status(item).tone"><i></i>{{ status(item).label }}</span>
        </div>
        <div class="title-line">
          <span v-if="kindLabel(item.kind)" class="type-tag" :class="item.kind">{{ kindLabel(item.kind) }}</span>
          <button type="button" class="title-button" @click="$emit('open', item.id)">{{ item.title }}</button>
        </div>
        <div class="ticket-meta">
          <div class="device-code">{{ item.device || '无' }}<span v-if="item.category"> · {{ item.category }}</span><span v-if="item.channel"> · {{ channel(item.channel) }}</span></div>
          <div v-if="badges(item).length" class="meta-tags">
            <span v-for="tag in badges(item)" :key="tag.text" class="mini-tag" :class="tag.tone">{{ tag.text }}</span>
          </div>
          <div class="owner" :class="{ unowned: unowned(item) }"><span>{{ unowned(item) ? '未认领' : item.owner }}</span></div>
          <div class="callback" :class="due(item).tone">{{ due(item).text }}</div>
        </div>
        <div class="card-action"><button type="button" :class="action(item).claim ? 'claim-btn' : 'text-action'" :disabled="busy" @click="runAction(item)">{{ action(item).label }}</button></div>
      </article>
    </div>
    <footer v-if="total || items.length" class="pagination">
      <span>{{ pageLabel }}</span>
      <div class="pages">
        <button type="button" class="page" :disabled="busy || page === 0" aria-label="上一页" @click="$emit('page', page - 1)">‹</button>
        <button v-for="index in pageButtons" :key="index" type="button" class="page" :aria-current="index === page ? 'page' : undefined" :disabled="busy" @click="$emit('page', index)">{{ index + 1 }}</button>
        <button type="button" class="page" :disabled="busy || !hasNext" aria-label="下一页" @click="$emit('page', page + 1)">›</button>
      </div>
    </footer>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { BUCKET_LABELS, avatarTone, channelLabel, displayStatus, disposalLabel, duePresentation, escalationLabel, initial, isUnowned, kindLabel, orderNumber, rowAction, slaPresentation, stateCount, suspendLabel, updatedPresentation, visibleBuckets } from '../status'

const props = defineProps({
  items: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 0 },
  pageSize: { type: Number, default: 10 },
  hasNext: Boolean,
  busy: Boolean,
  filters: { type: Object, required: true },
  summary: { type: Object, default: null },
  categories: { type: Array, default: () => [] },
  assignees: { type: Array, default: () => [] }
})
const emit = defineEmits(['open', 'claim', 'search', 'page'])
const labels = BUCKET_LABELS
const q = ref('')
const kind = ref('')
const category = ref('')
const owner = ref('')

watch(() => props.filters, value => {
  q.value = value.q || ''
  kind.value = value.kind || ''
  category.value = value.category || ''
  owner.value = value.owner || ''
}, { immediate: true, deep: true })

const buckets = computed(() => visibleBuckets(props.summary, props.filters.bucket || ''))
const narrowed = computed(() => Boolean(q.value.trim() || kind.value || category.value || owner.value))
const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const pageButtons = computed(() => {
  const last = pageCount.value - 1
  const start = Math.max(0, Math.min(props.page - 2, last - 4))
  const end = Math.min(last, start + 4)
  const pages = []
  for (let index = Math.max(0, end - 4); index <= end; index += 1) pages.push(index)
  return pages
})
const pageLabel = computed(() => {
  if (!props.total) return '共 0 条问题单'
  const start = props.page * props.pageSize + 1
  const end = props.page * props.pageSize + props.items.length
  const prefix = narrowed.value ? '当前筛选 ' : '显示第 '
  return narrowed.value ? prefix + props.total + ' 条' : prefix + start + '–' + end + ' 条，共 ' + props.total + ' 条问题单'
})

function count(id) { return stateCount(props.summary, id) }
function number(item) { return orderNumber(item) }
function status(item) { return displayStatus(item) }
function action(item) { return rowAction(item) }
function unowned(item) { return isUnowned(item) }
function tone(name) { return avatarTone(name) }
function due(item) { return duePresentation(item.nextDueAt) }
function updated(value) { return updatedPresentation(value) }
function channel(value) { return channelLabel(value) }
function badges(item) {
  const list = []
  if (item.grade) {
    const disposal = item.disposalType ? '·' + disposalLabel(item.disposalType) : ''
    list.push({ text: item.grade + '级' + disposal, tone: item.grade === 'C' ? 'warn' : 'info' })
  }
  if (item.suspendReason && item.state === 'waiting') {
    list.push({ text: suspendLabel(item.suspendReason), tone: 'warn' })
  }
  if (item.escalationLevel && item.escalationLevel !== 'none') {
    list.push({ text: escalationLabel(item.escalationLevel), tone: 'danger' })
  }
  if (Number(item.compromiseFlag || 0) === 1 || item.compromiseFlag === true) {
    list.push({ text: '妥协工单', tone: 'danger' })
  }
  const sla = slaPresentation(item.slaDueAt, item.state)
  if (sla) {
    list.push({ text: sla.text, tone: sla.overdue ? 'danger' : 'muted' })
  }
  return list
}
function hint(item) {
  if (item.nextStep) return item.nextStep
  const id = displayStatus(item).id
  if (id === 'unclaimed') return '认领后安排回访'
  if (id === 'fieldPending') return '待关联智享乐装现场单'
  if (id === 'fieldActive') return '等待现场处理反馈'
  if (id === 'verify') return '回访确认后可结案'
  if (id === 'resolved') return '已结案'
  return ''
}
function emitFilters(bucket) {
  emit('search', { q: q.value.trim(), kind: kind.value, category: category.value, owner: owner.value, bucket: bucket || '', state: '' })
}
function chooseBucket(id) { emitFilters(id) }
function reset() {
  q.value = ''
  kind.value = ''
  category.value = ''
  owner.value = ''
  emitFilters('')
}
function runAction(item) {
  const next = rowAction(item)
  if (next.claim) emit('claim', item)
  else emit('open', item.id, next.id === 'view' ? '' : next.id)
}
</script>

<style scoped>
svg { width: 14px; height: 14px; fill: none; stroke: currentColor; stroke-width: 1.75; stroke-linecap: round; stroke-linejoin: round; }
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}
</style>
