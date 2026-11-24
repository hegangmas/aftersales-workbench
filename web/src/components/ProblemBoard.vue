<template>
  <el-card class="board-card">
    <template #header>
      <div class="board-head">
        <h2 id="board-heading">问题单总表</h2>
        <div class="board-entries">
          <button type="button" :aria-pressed="mode === 'all'" @click="showAll">问题单总表</button>
          <button type="button" :aria-pressed="mode === 'pool'" @click="showPool">待认领池</button>
        </div>
      </div>
    </template>
    <p v-if="mode === 'pool'" class="board-note">待认领池只看负责人为无主的问题单。历史无主单仍显示未认领。</p>
    <search>
      <form class="filters" aria-labelledby="board-heading" @submit.prevent="submit">
        <div class="field">
          <label for="board-q">搜索</label>
          <input id="board-q" v-model="q" name="q" type="search" placeholder="AS-12、桩编码或标题"
            autocomplete="off" autocorrect="off" autocapitalize="none" spellcheck="false" enterkeyhint="search"
            maxlength="80">
        </div>
        <div class="field">
          <label for="board-state">状态</label>
          <select id="board-state" v-model="state" name="state">
            <option v-for="item in states" :key="item.value || 'all'" :value="item.value">{{ item.label }}</option>
          </select>
        </div>
        <fieldset>
          <legend>负责人</legend>
          <label><input v-model="ownerMode" type="radio" name="owner-mode" value="all">全部</label>
          <label><input v-model="ownerMode" type="radio" name="owner-mode" value="early">无主待认领</label>
          <label><input v-model="ownerMode" type="radio" name="owner-mode" value="user">指定账号</label>
          <div v-if="ownerMode === 'user'" class="field">
            <label for="board-owner">账号</label>
            <input id="board-owner" v-model.trim="ownerName" name="owner" list="board-owners" required maxlength="20"
              autocomplete="off" autocorrect="off" autocapitalize="none" spellcheck="false">
            <datalist id="board-owners">
              <option v-for="item in assignees" :key="item.username" :value="item.username"></option>
            </datalist>
          </div>
        </fieldset>
        <fieldset>
          <legend>类型</legend>
          <label><input v-model="kind" type="radio" name="kind" value="">全部</label>
          <label><input v-model="kind" type="radio" name="kind" value="fault">故障</label>
          <label><input v-model="kind" type="radio" name="kind" value="consult">咨询</label>
          <label><input v-model="kind" type="radio" name="kind" value="service">服务</label>
        </fieldset>
        <div class="field">
          <label for="board-category">故障分类</label>
          <select id="board-category" v-model="category" name="category">
            <option value="">全部</option>
            <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>
        <button type="submit" :disabled="busy">查询</button>
      </form>
    </search>
    <p class="board-note">共 {{ total }} 条<span v-if="total">，第 {{ page + 1 }} 页</span></p>
    <p v-if="!items.length" class="board-note">没有符合条件的问题单</p>
    <div v-else class="table-wrap" tabindex="0" role="region" aria-labelledby="board-heading">
      <table>
        <caption class="visually-hidden">问题单总表</caption>
        <thead>
          <tr>
            <th>单号</th>
            <th>标题</th>
            <th>桩编码</th>
            <th>状态</th>
            <th>负责人</th>
            <th>下次回访</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id" :class="{ selected: selectedId === item.id }">
            <th scope="row" data-label="单号">
              <button type="button" :aria-current="selectedId === item.id ? 'true' : undefined" @click="$emit('open', item.id)">
                {{ orderNumber(item) }}
              </button>
            </th>
            <td data-label="标题">{{ item.title }}</td>
            <td data-label="桩编码">{{ item.device || '无' }}</td>
            <td data-label="状态"><span class="status-chip" :data-state="item.state">{{ statusLabel(item.state) }}</span></td>
            <td data-label="负责人">{{ ownerLabel(item.owner) }}</td>
            <td data-label="下次回访">{{ item.nextDueAt ? dateLabel(item.nextDueAt) : '未安排' }}</td>
            <td data-label="更新时间">{{ dateLabel(item.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pager">
      <button type="button" :disabled="busy || page === 0" @click="$emit('prev')">上一页</button>
      <button type="button" :disabled="busy || !hasNext" @click="$emit('next')">下一页</button>
    </div>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  busy: Boolean,
  items: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 0 },
  hasNext: Boolean,
  selectedId: { type: String, default: '' },
  categories: { type: Array, default: () => [] },
  assignees: { type: Array, default: () => [] }
})
const emit = defineEmits(['open', 'search', 'prev', 'next'])

const states = [
  { value: '', label: '全部' },
  { value: 'open', label: '处理中' },
  { value: 'triage', label: '待排查' },
  { value: 'waiting', label: '待回访' },
  { value: 'field', label: '现场服务' },
  { value: 'verify', label: '待验证' },
  { value: 'resolved', label: '已结案' },
  { value: 'answered', label: '已答复' },
  { value: 'referred', label: '已转出' }
]
const q = ref('')
const state = ref('')
const ownerMode = ref('all')
const ownerName = ref('')
const kind = ref('')
const category = ref('')
const mode = ref('all')

function orderNumber(item) {
  return item.displayNumber ? 'AS-' + item.displayNumber : item.id
}

function dateLabel(value) {
  return String(value).replace('T', ' ').replace(/\.\d+/, '').replace(/Z|[+-]\d\d:\d\d$/, '').slice(0, 16)
}

function statusLabel(value) {
  return {
    open: '处理中', waiting: '待回访', field: '现场服务', verify: '待验证',
    triage: '待排查', resolved: '已结案', answered: '已答复', referred: '已转出'
  }[value] || value
}

function ownerLabel(owner) {
  return !owner || owner === 'early' ? '无主' : owner
}

function payload() {
  return {
    q: q.value.trim(),
    state: state.value,
    owner: ownerMode.value === 'user' ? ownerName.value : ownerMode.value === 'early' ? 'early' : '',
    kind: kind.value,
    category: category.value
  }
}

function submit() {
  mode.value = ownerMode.value === 'early' && !q.value.trim() && !state.value && !kind.value && !category.value ? 'pool' : 'all'
  emit('search', payload())
}

function showAll() {
  q.value = ''
  state.value = ''
  ownerMode.value = 'all'
  ownerName.value = ''
  kind.value = ''
  category.value = ''
  mode.value = 'all'
  emit('search', payload())
}

function showPool() {
  q.value = ''
  state.value = ''
  ownerMode.value = 'early'
  ownerName.value = ''
  kind.value = ''
  category.value = ''
  mode.value = 'pool'
  emit('search', payload())
}
</script>

<style scoped>
.board-head, .board-entries, .pager { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.board-head { justify-content: space-between; }
h2 { margin: 0; font-size: 16px; }
.board-entries button, .pager button {
  min-height: 44px;
  padding: 0 12px;
  color: var(--brand);
  background: var(--card);
  border: 1px solid var(--brand);
  border-radius: 6px;
  cursor: pointer;
}
.board-entries button[aria-pressed="true"] { background: var(--selected); }
.board-note { margin: 0 0 8px; color: var(--muted); font-size: 13px; }
.filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 8px 12px; align-items: start; }
.field { display: flex; flex-direction: column; gap: 4px; }
.filters label, legend { font-size: 13px; font-weight: 600; }
fieldset { margin: 0; padding: 8px; border: 1px solid var(--line); border-radius: 6px; display: grid; gap: 4px; }
fieldset label { display: flex; align-items: center; gap: 8px; min-height: 44px; font-weight: 500; }
input[type="radio"] { accent-color: #006072; inline-size: 1.1rem; block-size: 1.1rem; }
.filters button[type="submit"] {
  justify-self: start;
  align-self: end;
  min-height: 44px;
  padding: 0 12px;
  color: var(--brand);
  background: #fff;
  border: 1px solid var(--brand);
  border-radius: 6px;
  cursor: pointer;
}
.table-wrap {
  overflow: auto;
  max-inline-size: 100%;
  max-block-size: min(420px, 70vh);
  container: board / inline-size;
  border: 1px solid var(--line);
  border-radius: 8px;
}
table { border-collapse: separate; border-spacing: 0; width: 100%; }
caption.visually-hidden {
  position: absolute;
  clip-path: inset(50%);
  overflow: hidden;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  border: 0;
  white-space: nowrap;
}
thead th {
  position: sticky;
  inset-block-start: 0;
  z-index: 1;
  background: var(--card);
  text-align: start;
}
th, td { padding: 8px; border-bottom: 1px solid var(--line); vertical-align: top; text-align: start; }
tbody th { background: var(--card); }
tr.selected td, tr.selected th { background: var(--selected); }
th button {
  min-height: 44px;
  padding: 0;
  color: var(--brand);
  background: transparent;
  border: 0;
  font-weight: 700;
  cursor: pointer;
  text-align: start;
}
.pager { margin-top: 8px; }
.pager button:disabled, .filters button:disabled { cursor: not-allowed; opacity: 0.6; }
@container board (inline-size < 640px) {
  table, thead, tbody, tr, th, td { display: block; }
  thead { display: none; }
  tr { margin: 8px; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
  th, td { border-bottom: 1px solid var(--line); }
  th::before, td::before {
    content: attr(data-label) ": ";
    color: var(--muted);
    font-size: 12px;
    font-weight: 600;
  }
}
</style>
