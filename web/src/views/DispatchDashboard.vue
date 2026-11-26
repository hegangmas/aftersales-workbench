<template>
  <section
    class="dispatch-dashboard"
    :class="{ 'is-fullscreen': isFullscreen }"
    aria-label="统一工单池调度大屏"
  >
    <header class="dash-topbar">
      <div class="topbar-left">
        <div class="topbar-title-row">
          <span class="live-indicator" aria-hidden="true"></span>
          <h2 class="topbar-title">统一工单池调度大屏</h2>
          <span v-if="isFullscreen" class="mode-badge">值守全屏</span>
        </div>
        <div class="topbar-meta">
          <span class="live-clock" aria-label="当前时间">{{ liveClock }}</span>
          <span class="meta-sep" aria-hidden="true">·</span>
          <span class="duty-stat">在岗 {{ onDutyCount }} 人 · 待分配 {{ unclaimedCountDisplay }}</span>
        </div>
      </div>

      <div class="topbar-actions">
        <span v-if="isFullscreen" class="esc-hint">按 ESC 退出全屏</span>
        <button
          type="button"
          class="btn btn--secondary btn--sm fullscreen-btn"
          :class="{ active: isFullscreen }"
          :aria-pressed="isFullscreen"
          @click="toggleFullscreen"
        >
          <svg v-if="!isFullscreen" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" />
          </svg>
          <svg v-else viewBox="0 0 24 24" aria-hidden="true">
            <path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5" />
          </svg>
          <span>{{ isFullscreen ? '退出全屏' : '全屏值守模式' }}</span>
        </button>
      </div>
    </header>

    <DispatchKpiGrid
      :has-summary-total="hasSummaryTotal"
      :today-total-display="todayTotalDisplay"
      :has-unclaimed-summary="hasUnclaimedSummary"
      :unclaimed-count-display="unclaimedCountDisplay"
      :has-sla-data="hasSlaData"
      :overdue-count-display="overdueCountDisplay"
      :has-compromise-data="hasCompromiseData"
      :compromise-count-display="compromiseCountDisplay"
      @open-unclaimed="$emit('open-board', 'unclaimed')"
    />

    <div class="mid-grid">
      <section class="dash-card pool-card" aria-label="工单池列表">
        <header class="card-header pool-header">
          <div class="pool-heading">
            <h2>工单池列表</h2>
            <p class="card-sub">优先高亮超时与待分配工单，支持多维筛选与快速认领</p>
          </div>

          <div class="pool-toolbar">
            <select v-model="filterChannel" class="pool-filter-select" aria-label="来源筛选">
              <option value="">全部来源</option>
              <option v-for="opt in CHANNEL_FILTER_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>

            <select v-model="filterStatus" class="pool-filter-select" aria-label="状态筛选">
              <option value="">全部状态</option>
              <option v-for="opt in STATUS_FILTER_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>

            <select v-model="filterPriority" class="pool-filter-select" aria-label="优先级筛选">
              <option value="">全部优先级</option>
              <option v-for="opt in PRIORITY_FILTER_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>

            <input
              v-model.trim="filterKeyword"
              type="search"
              class="pool-filter-input"
              placeholder="搜索工单号/故障内容"
              aria-label="搜索工单号/故障内容"
              maxlength="60"
            />

            <button type="button" class="link-btn" @click="$emit('open-board', '')">查看完整总表 →</button>
          </div>
        </header>

        <p v-if="!pagedRows.length" class="empty-state">
          {{ hasActivePoolFilter ? '没有符合当前筛选条件的工单，可清空筛选条件后重试。' : '当前工单池暂无可展示工单，可前往完整总表查看历史记录。' }}
        </p>

        <template v-else>
          <div class="table-wrap">
            <table class="pool-table">
              <colgroup>
                <col class="col-order" />
                <col class="col-channel" />
                <col class="col-title" />
                <col class="col-sla" />
                <col class="col-status" />
                <col class="col-owner" />
              </colgroup>
              <thead>
                <tr>
                  <th>工单号</th>
                  <th>来源</th>
                  <th>故障</th>
                  <th>SLA剩余</th>
                  <th>状态</th>
                  <th>处理人</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedRows" :key="row.item.id" :class="rowClass(row)">
                  <td>
                    <button type="button" class="order-btn" @click="$emit('open', row.item.id)">{{ row.number }}</button>
                  </td>
                  <td><span class="channel-pill">{{ row.channel }}</span></td>
                  <td>
                    <div class="title-cell">
                      <span v-if="row.priority === 'high'" class="priority-tag high">高优</span>
                      <span v-else-if="row.kind" class="kind-tag" :class="row.item.kind">{{ row.kind }}</span>
                      <button type="button" class="title-btn" :title="row.item.title" @click="$emit('open', row.item.id)">
                        {{ row.item.title }}
                      </button>
                      <span v-if="row.compromise" class="compromise-tag">妥协</span>
                    </div>
                  </td>
                  <td>
                    <span class="sla-text" :class="row.sla.tone">
                      <i v-if="row.sla.tone === 'overdue' || row.sla.tone === 'soon'"></i>
                      {{ row.sla.label }}
                    </span>
                  </td>
                  <td>
                    <span class="status-pill" :class="row.status.tone"><i></i>{{ row.status.label }}</span>
                  </td>
                  <td>
                    <div class="owner-cell">
                      <template v-if="row.unowned">
                        <span class="unowned-label">待分配</span>
                        <button v-if="!row.isTerminal" type="button" class="claim-mini" :disabled="busy" @click="$emit('claim', row.item.id)">认领</button>
                      </template>
                      <template v-else>
                        <span class="avatar-dot" :class="row.avatarTone">{{ row.initial }}</span>
                        <span class="owner-name">{{ row.item.owner }}</span>
                      </template>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="mobile-pool-list">
            <article v-for="row in pagedRows" :key="'m-' + row.item.id" class="mobile-pool-item" :class="rowClass(row)">
              <div class="mobile-row-top">
                <button type="button" class="order-btn" @click="$emit('open', row.item.id)">{{ row.number }}</button>
                <span class="status-pill" :class="row.status.tone"><i></i>{{ row.status.label }}</span>
              </div>
              <button type="button" class="title-btn mobile-title" @click="$emit('open', row.item.id)">{{ row.item.title }}</button>
              <div class="mobile-row-meta">
                <span class="channel-pill">{{ row.channel }}</span>
                <span class="sla-text" :class="row.sla.tone">{{ row.sla.label }}</span>
                <span v-if="row.unowned" class="unowned-label">待分配</span>
                <span v-else class="owner-name">{{ row.item.owner }}</span>
              </div>
            </article>
          </div>
        </template>

        <footer class="pool-pagination">
          <span class="pool-total">共 {{ filteredRows.length }} 条</span>
          <div class="page-controls" role="navigation" aria-label="工单池分页">
            <button type="button" class="page-btn" :disabled="currentPage <= 1" aria-label="上一页" @click="currentPage -= 1">‹</button>
            <button
              v-for="p in pageNumbers"
              :key="p"
              type="button"
              class="page-btn"
              :class="{ active: p === currentPage }"
              :aria-current="p === currentPage ? 'page' : undefined"
              @click="currentPage = p"
            >{{ p }}</button>
            <button type="button" class="page-btn" :disabled="currentPage >= totalPages" aria-label="下一页" @click="currentPage += 1">›</button>
          </div>
        </footer>
      </section>

      <DispatchAgentPanel
        :has-real-assignees="hasRealAssignees"
        :on-duty-count="onDutyCount"
        :agent-rows="agentRows"
        :busy="busy"
        :updating-agent="updatingAgent"
        :duty-error="dutyError"
        :assign-notice="assignNotice"
        @cycle-duty="cycleAgentDuty"
        @force-distribute="handleForceDistribute"
      />
    </div>

    <DispatchAnalyticsRow :items="items" :assignees="assignees" :now-ms="nowMs" />
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import DispatchKpiGrid from '../components/dispatch/DispatchKpiGrid.vue'
import DispatchAgentPanel from '../components/dispatch/DispatchAgentPanel.vue'
import DispatchAnalyticsRow from '../components/dispatch/DispatchAnalyticsRow.vue'
import { updateUserDuty } from '../api/workbench'
import {
  avatarTone, channelLabel, displayStatus, initial, isUnowned, kindLabel,
  orderNumber, roleLabel, shanghaiStamp, slaPresentation, stateCount
} from '../status'

const props = defineProps({
  items: { type: Array, default: () => [] },
  summary: { type: Object, default: null },
  assignees: { type: Array, default: () => [] },
  busy: Boolean
})

const emit = defineEmits(['open', 'open-board', 'claim', 'force-distribute'])

const TERMINAL_STATES = new Set(['resolved', 'closed', 'cancelled', 'answered', 'referred'])
const POOL_PAGE_SIZE = 8
const DUTY_ORDER = ['ready', 'break', 'away']
const DUTY_LABELS = { ready: '就绪', break: '休息', away: '离岗' }

const isFullscreen = ref(false)
const nowMs = ref(Date.now())
let clockTimer = null

function formatShanghaiClock(ms) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
  }).formatToParts(new Date(ms))
  const read = type => parts.find(part => part.type === type).value
  return `${read('year')}-${read('month')}-${read('day')} ${read('hour')}:${read('minute')}:${read('second')}`
}

const liveClock = computed(() => formatShanghaiClock(nowMs.value))
function toggleFullscreen() { isFullscreen.value = !isFullscreen.value }
function onKeyDown(event) { if (event.key === 'Escape' && isFullscreen.value) isFullscreen.value = false }

onMounted(() => {
  clockTimer = setInterval(() => { nowMs.value = Date.now() }, 1000)
  window.addEventListener('keydown', onKeyDown)
})
onUnmounted(() => {
  if (clockTimer) { clearInterval(clockTimer); clockTimer = null }
  window.removeEventListener('keydown', onKeyDown)
})

const assignNotice = ref('')
const dutyError = ref('')
const updatingAgent = ref('')
const filterChannel = ref('')
const filterStatus = ref('')
const filterPriority = ref('')
const filterKeyword = ref('')
const currentPage = ref(1)

const CHANNEL_FILTER_OPTIONS = [
  { value: '400', label: '400电话' },
  { value: 'miniprogram', label: '客户小程序' },
  { value: 'provider', label: '服务商扫码' },
  { value: 'wecom', label: '企微群' },
  { value: 'phone', label: '电话' }
]
const STATUS_FILTER_OPTIONS = [
  { value: 'pending_assign', label: '待分配' },
  { value: 'processing', label: '处理中' },
  { value: 'suspended', label: '挂起' },
  { value: 'pending_callback', label: '待回传' },
  { value: 'pending_accept', label: '待验收' },
  { value: 'closed', label: '已闭环' },
  { value: 'cancelled', label: '已取消' }
]
const PRIORITY_FILTER_OPTIONS = [
  { value: 'high', label: '高优先级' },
  { value: 'normal', label: '中优先级' },
  { value: 'low', label: '低优先级' }
]

const hasSummaryTotal = computed(() => props.summary !== null && props.summary.total !== undefined && props.summary.total !== null)
const todayTotalDisplay = computed(() => (hasSummaryTotal.value ? Number(props.summary.total) : 32))
const hasUnclaimedSummary = computed(() => Boolean(props.summary && props.summary.states && props.summary.states.unclaimed !== undefined))
const unclaimedCountDisplay = computed(() => {
  if (hasUnclaimedSummary.value) return stateCount(props.summary, 'unclaimed')
  if (props.items.length) return props.items.filter(item => isUnowned(item) && !TERMINAL_STATES.has(item.state)).length
  return 6
})
const hasSlaData = computed(() => props.items.some(item => item && item.slaDueAt))
const overdueCountDisplay = computed(() => {
  if (hasSlaData.value) {
    return props.items.filter(item => item && !TERMINAL_STATES.has(item.state) && Boolean(slaPresentation(item.slaDueAt, item.state)?.overdue)).length
  }
  return 2
})
const hasCompromiseData = computed(() => props.items.some(item => item && Object.prototype.hasOwnProperty.call(item, 'compromiseFlag')))
const compromiseCountDisplay = computed(() => {
  if (hasCompromiseData.value) return props.items.filter(item => Number(item.compromiseFlag || 0) === 1 || item.compromiseFlag === true).length
  return 3
})

function parseShanghaiEpoch(stamp) {
  const match = String(stamp || '').match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/)
  if (!match) return null
  return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5]))
}

function formatSlaRemaining(item) {
  if (!item) return { label: '—', tone: 'none', overdue: false }
  if (item.state === 'closed') return { label: '已闭环', tone: 'closed', overdue: false }
  if (item.state === 'cancelled') return { label: '已取消', tone: 'closed', overdue: false }
  if (item.state === 'resolved') return { label: '已结案', tone: 'closed', overdue: false }
  if (!item.slaDueAt) return { label: '未设置', tone: 'none', overdue: false }

  const dueEpoch = parseShanghaiEpoch(item.slaDueAt)
  const nowEpoch = parseShanghaiEpoch(shanghaiStamp(new Date(nowMs.value)))
  if (dueEpoch === null || nowEpoch === null) {
    const fallback = slaPresentation(item.slaDueAt, item.state)
    return fallback ? { label: fallback.text, tone: fallback.overdue ? 'overdue' : 'normal', overdue: fallback.overdue } : { label: '—', tone: 'none', overdue: false }
  }
  const diffMins = Math.round((dueEpoch - nowEpoch) / 60000)
  if (diffMins <= 0) {
    const over = Math.abs(diffMins)
    if (over >= 60) {
      const h = Math.floor(over / 60), m = over % 60
      return { label: m ? `已超时 ${h}h${m}m` : `已超时 ${h}h`, tone: 'overdue', overdue: true }
    }
    return { label: over === 0 ? '已到期' : `已超时 ${over}m`, tone: 'overdue', overdue: true }
  }
  if (diffMins < 60) return { label: `剩余 ${diffMins}m`, tone: diffMins <= 30 ? 'soon' : 'normal', overdue: false }
  const h = Math.floor(diffMins / 60), m = diffMins % 60
  return { label: m ? `剩余 ${h}h${m}m` : `剩余 ${h}h`, tone: diffMins <= 60 ? 'soon' : 'normal', overdue: false }
}

function normalizeStatusFilterKey(row) {
  const state = row.item?.state || '', sid = row.status?.id || ''
  if (row.unowned || state === 'pending_assign' || sid === 'unclaimed') return 'pending_assign'
  if (state === 'processing' || state === 'open' || sid === 'processing' || sid === 'open') return 'processing'
  if (state === 'suspended' || state === 'waiting' || sid === 'suspended' || sid === 'waiting') return 'suspended'
  if (state === 'pending_callback' || sid === 'pending_callback' || sid === 'fieldPending' || sid === 'fieldActive') return 'pending_callback'
  if (state === 'pending_accept' || sid === 'pending_accept' || sid === 'verify') return 'pending_accept'
  if (state === 'cancelled' || sid === 'cancelled') return 'cancelled'
  if (TERMINAL_STATES.has(state) || TERMINAL_STATES.has(sid)) return 'closed'
  return sid || state
}

const allPoolRows = computed(() => props.items.map(item => {
  const isTerminal = TERMINAL_STATES.has(item.state)
  return {
    item,
    number: orderNumber(item),
    channelKey: item.channel || '',
    channel: channelLabel(item.channel) || item.channel || '未标注',
    kind: kindLabel(item.kind),
    priority: String(item.urgency || item.priority || 'normal').toLowerCase(),
    status: displayStatus(item),
    isTerminal,
    unowned: isUnowned(item) && !isTerminal,
    sla: formatSlaRemaining(item),
    compromise: Number(item.compromiseFlag || 0) === 1 || item.compromiseFlag === true,
    initial: initial(item.owner),
    avatarTone: avatarTone(item.owner)
  }
}))

const hasActivePoolFilter = computed(() => Boolean(filterChannel.value || filterStatus.value || filterPriority.value || filterKeyword.value))

const filteredRows = computed(() => {
  const kw = filterKeyword.value.toLowerCase()
  return allPoolRows.value.filter(row => {
    if (filterChannel.value && row.channelKey !== filterChannel.value) return false
    if (filterStatus.value && normalizeStatusFilterKey(row) !== filterStatus.value) return false
    if (filterPriority.value && row.priority !== filterPriority.value) return false
    if (kw) {
      const hay = [row.number, row.item.title, row.item.description, row.item.device, row.item.category].filter(Boolean).join(' ').toLowerCase()
      if (!hay.includes(kw)) return false
    }
    return true
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / POOL_PAGE_SIZE)))
const pagedRows = computed(() => {
  const safePage = Math.min(Math.max(1, currentPage.value), totalPages.value)
  const start = (safePage - 1) * POOL_PAGE_SIZE
  return filteredRows.value.slice(start, start + POOL_PAGE_SIZE)
})
const pageNumbers = computed(() => {
  const total = totalPages.value, curr = Math.min(Math.max(1, currentPage.value), total)
  const start = Math.max(1, Math.min(curr - 2, total - 4)), end = Math.min(total, start + 4)
  const pages = []
  for (let i = start; i <= end; i += 1) pages.push(i)
  return pages
})

watch([filterChannel, filterStatus, filterPriority, filterKeyword, () => props.items.length], () => { currentPage.value = 1 })
watch(totalPages, nextTotal => { if (currentPage.value > nextTotal) currentPage.value = nextTotal })

function rowClass(row) {
  if (row.sla.overdue) return 'row-overdue'
  if (row.unowned) return 'row-unassigned'
  return ''
}

const MOCK_AGENTS = [
  { username: '张三', roleLabel: '一线坐席', status: 'ready', duration: '04h 18m' },
  { username: '李四', roleLabel: '一线坐席', status: 'ready', duration: '03h 52m' },
  { username: '赵六', roleLabel: '二线专家', status: 'break', duration: '02h 40m' },
  { username: '陈七', roleLabel: '值班组长', status: 'away', duration: '01h 15m' }
]
const dutyOverrides = reactive({})
const mockDutyStates = reactive({})

watch(() => props.assignees, list => {
  const names = new Set((list || []).map(a => a && a.username).filter(Boolean))
  Object.keys(dutyOverrides).forEach(key => { if (!names.has(key)) delete dutyOverrides[key] })
}, { deep: true })

const hasRealAssignees = computed(() => Array.isArray(props.assignees) && props.assignees.length > 0)

function formatElapsedFromStamp(value) {
  if (!value) return ''
  const epoch = Array.isArray(value) && value.length >= 5
    ? Date.UTC(value[0], value[1] - 1, value[2], value[3], value[4])
    : parseShanghaiEpoch(value)
  if (epoch === null) return ''
  const nowEpoch = parseShanghaiEpoch(shanghaiStamp(new Date(nowMs.value)))
  if (nowEpoch === null) return ''
  const diffMins = Math.max(0, Math.round((nowEpoch - epoch) / 60000))
  const h = Math.floor(diffMins / 60), m = diffMins % 60
  return h > 0 ? `${String(h).padStart(2, '0')}h ${String(m).padStart(2, '0')}m` : `${m}m`
}

const agentRows = computed(() => {
  if (!hasRealAssignees.value) {
    return MOCK_AGENTS.map(item => {
      const status = mockDutyStates[item.username] || item.status
      return {
        username: item.username, roleLabel: item.roleLabel, status,
        statusLabel: DUTY_LABELS[status] || '就绪',
        durationText: `状态时长（模拟）：${item.duration}`,
        initial: initial(item.username), tone: avatarTone(item.username)
      }
    })
  }
  return props.assignees.map(item => {
    const override = dutyOverrides[item.username]
    const rawStatus = override?.status || item.dutyStatus || 'ready'
    const status = DUTY_ORDER.includes(rawStatus) ? rawStatus : 'ready'
    const elapsed = formatElapsedFromStamp(override?.updatedAt || item.dutyUpdatedAt)
    return {
      username: item.username, roleLabel: roleLabel(item.role), status,
      statusLabel: DUTY_LABELS[status] || '就绪',
      durationText: elapsed ? `持续时长：${elapsed}` : '状态已同步',
      initial: initial(item.username), tone: avatarTone(item.username)
    }
  })
})

const onDutyCount = computed(() => agentRows.value.filter(agent => agent.status !== 'away').length)

async function cycleAgentDuty(agent) {
  if (updatingAgent.value === agent.username) return
  const nextStatus = DUTY_ORDER[(DUTY_ORDER.indexOf(agent.status) + 1) % DUTY_ORDER.length]
  dutyError.value = ''
  if (!hasRealAssignees.value) { mockDutyStates[agent.username] = nextStatus; return }
  const prev = dutyOverrides[agent.username] ? { ...dutyOverrides[agent.username] } : null
  dutyOverrides[agent.username] = { status: nextStatus, updatedAt: shanghaiStamp(new Date(nowMs.value)) }
  updatingAgent.value = agent.username
  try {
    await updateUserDuty(agent.username, nextStatus)
  } catch (err) {
    if (prev) dutyOverrides[agent.username] = prev
    else delete dutyOverrides[agent.username]
    dutyError.value = (err && err.message) || '更新在岗状态失败，请重试'
  } finally {
    updatingAgent.value = ''
  }
}

function handleForceDistribute() {
  assignNotice.value = '已触发一键强制平均分配（按就绪坐席均分待分配工单）'
  emit('force-distribute')
}
</script>

<style scoped>
.dispatch-dashboard { display: grid; gap: 16px; margin-top: 16px; }
.dispatch-dashboard.is-fullscreen {
  position: fixed; inset: 0; z-index: 100; overflow: auto; background: var(--bg-canvas, #f3f5f5); padding: 20px; margin-top: 0;
}
.dash-topbar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
  min-height: 56px; padding: 10px var(--card-px, 20px);
  border: 1px solid var(--border-default, #e2e7e7); border-radius: var(--radius-xl, 12px);
  background: var(--bg-surface, #ffffff); box-shadow: var(--elev-1);
}
.topbar-left { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; min-width: 0; }
.topbar-title-row { display: flex; align-items: center; gap: 8px; }
.live-indicator { width: 6px; height: 6px; border-radius: 50%; background: var(--success-solid, #1f8f5c); box-shadow: 0 0 0 3px rgba(31, 143, 92, 0.18); flex: none; }
.topbar-title { margin: 0; color: var(--text-primary, #152020); font-size: 16px; font-weight: 600; }
.mode-badge { padding: 2px 8px; border-radius: 999px; background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); font-size: 12px; font-weight: 600; }
.topbar-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 4px 10px; border-radius: var(--radius-sm, 6px); background: var(--bg-subtle, #f8fafa); font-size: 12px; }
.live-clock { color: var(--text-primary, #152020); font-family: var(--font-mono, monospace); font-weight: 500; font-variant-numeric: tabular-nums; }
.meta-sep { color: var(--text-tertiary, #7a8686); }
.duty-stat { color: var(--brand-700, #0f766e); font-weight: 600; font-variant-numeric: tabular-nums; }
.topbar-actions { display: flex; align-items: center; gap: 10px; }
.esc-hint { color: var(--text-tertiary, #7a8686); font-size: 12px; }
.fullscreen-btn svg { width: 14px; height: 14px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.fullscreen-btn.active { border-color: var(--brand-700, #0f766e); background: var(--brand-700, #0f766e); color: #fff; }

.mid-grid { display: grid; grid-template-columns: minmax(0, 1.75fr) minmax(290px, 0.9fr); gap: var(--gutter, 16px); align-items: stretch; }
.dash-card {
  display: flex; flex-direction: column; min-width: 0;
  border: 1px solid var(--border-default, #e2e7e7); border-radius: var(--radius-xl, 12px);
  background: var(--bg-surface, #ffffff); box-shadow: var(--elev-1);
}
.pool-card { padding: 0; overflow: hidden; }
.pool-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; padding: 16px 20px 12px; }
.pool-heading { min-width: 150px; }
.pool-heading h2 { margin: 0; color: var(--text-primary, #152020); font-size: 15px; font-weight: 600; }
.card-sub { margin: 2px 0 0; color: var(--text-tertiary, #7a8686); font-size: 12px; }
.pool-toolbar { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; max-width: 100%; }
.pool-filter-select, .pool-filter-input {
  height: 32px; padding: 0 10px; border: 1px solid var(--border-strong, #cfd7d7); border-radius: var(--radius-md, 8px);
  background: var(--bg-surface, #ffffff); color: var(--text-primary, #152020); font-size: 13px; outline: none;
}
.pool-filter-select:focus, .pool-filter-input:focus { border-color: var(--brand-600, #0d9488); box-shadow: var(--focus-ring); }
.pool-filter-input { width: 168px; min-width: 120px; }
.link-btn { flex: none; height: 32px; padding: 0 6px; border: 0; background: transparent; color: var(--brand-700, #0f766e); font-size: 13px; font-weight: 500; white-space: nowrap; }
.link-btn:hover { color: var(--brand-800, #115e59); text-decoration: underline; text-underline-offset: 3px; }

.table-wrap { overflow-x: auto; border-top: 1px solid var(--border-subtle, #edf0f0); }
.pool-table { width: 100%; table-layout: fixed; border-collapse: separate; border-spacing: 0; text-align: left; }
.pool-table .col-order { width: 158px; }
.pool-table .col-channel { width: 88px; }
.pool-table .col-title { width: auto; }
.pool-table .col-sla { width: 98px; }
.pool-table .col-status { width: 92px; }
.pool-table .col-owner { width: 112px; }
.pool-table th { height: 40px; padding: 0 10px; border-bottom: 1px solid var(--border-default, #e2e7e7); background: var(--bg-subtle, #f8fafa); color: var(--text-tertiary, #7a8686); font-size: 12px; font-weight: 500; white-space: nowrap; }
.pool-table td { height: 48px; padding: 8px 10px; border-bottom: 1px solid var(--border-subtle, #edf0f0); vertical-align: middle; font-size: 13px; }
.pool-table th:first-child, .pool-table td:first-child { padding-left: 20px; }
.pool-table th:last-child, .pool-table td:last-child { padding-right: 20px; }
.pool-table tbody tr:hover td { background: var(--bg-hover, #f4f8f7); }
.pool-table tbody tr:last-child td { border-bottom: 0; }
.pool-table tr.row-unassigned td { background: #FFFBF2; }
.pool-table tr.row-unassigned td:first-child { box-shadow: inset 3px 0 0 var(--warning-solid, #d97706); }
.pool-table tr.row-overdue td { background: #FEF5F4; }
.pool-table tr.row-overdue td:first-child { box-shadow: inset 3px 0 0 var(--danger-solid, #c0362c); }

.order-btn { display: block; max-width: 100%; padding: 0; border: 0; background: transparent; color: var(--brand-700, #0f766e); font-family: var(--font-mono, monospace); font-size: 13px; font-weight: 500; font-variant-numeric: tabular-nums; white-space: nowrap; }
.order-btn:hover { text-decoration: underline; text-underline-offset: 3px; }
.channel-pill { display: inline-flex; align-items: center; height: 20px; padding: 0 6px; border-radius: 4px; background: var(--neutral-bg, #f1f4f4); color: var(--text-secondary, #4a5656); font-size: 12px; font-weight: 500; white-space: nowrap; }
.title-cell { display: flex; align-items: center; gap: 6px; min-width: 0; overflow: hidden; }
.title-btn { flex: 1; min-width: 0; overflow: hidden; padding: 0; border: 0; background: transparent; color: var(--text-primary, #152020); font-size: 13px; font-weight: 500; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.title-btn:hover { color: var(--brand-700, #0f766e); }
.kind-tag, .compromise-tag, .priority-tag { flex: none; display: inline-flex; align-items: center; height: 20px; padding: 0 6px; border-radius: 4px; font-size: 12px; line-height: 1; }
.kind-tag.fault { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); }
.kind-tag.consult { background: var(--info-bg, #edf3f9); color: var(--info-fg, #2f5f8a); }
.kind-tag.service { background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); }
.priority-tag.high, .compromise-tag { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); font-weight: 600; }

.sla-text { display: inline-flex; align-items: center; gap: 5px; color: var(--text-secondary, #4a5656); font-size: 12px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.sla-text i { flex: none; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.sla-text.overdue { color: var(--danger-fg, #c0362c); font-weight: 600; }
.sla-text.soon { color: var(--warning-fg, #a15c07); font-weight: 600; }
.sla-text.normal { color: var(--brand-700, #0f766e); }

.status-pill { display: inline-flex; align-items: center; gap: 5px; height: 22px; padding: 0 8px; border-radius: 999px; font-size: 12px; font-weight: 500; white-space: nowrap; }
.status-pill i { flex: none; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.status-pill.processing { background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); }
.status-pill.waiting, .status-pill.verify { background: var(--warning-bg, #fef5e6); color: var(--warning-fg, #a15c07); }
.status-pill.dispatch, .status-pill.onsite { background: var(--info-bg, #edf3f9); color: var(--info-fg, #2f5f8a); }
.status-pill.closed, .status-pill.answered, .status-pill.referred { background: var(--neutral-bg, #f1f4f4); color: var(--neutral-fg, #4a5656); }
.status-pill.triage { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); }
.status-pill.follow { background: var(--success-bg, #eaf6ef); color: var(--success-fg, #18794e); }

.owner-cell { display: flex; align-items: center; gap: 6px; min-width: 0; overflow: hidden; white-space: nowrap; }
.avatar-dot { display: inline-grid; place-items: center; flex: none; width: 22px; height: 22px; border-radius: 50%; font-size: 12px; font-weight: 600; }
.avatar-dot.teal { background: var(--avatar-1-bg, #e8f6f4); color: var(--avatar-1-fg, #0f766e); }
.avatar-dot.blue { background: var(--avatar-2-bg, #edf1f5); color: var(--avatar-2-fg, #45607a); }
.avatar-dot.purple { background: var(--avatar-3-bg, #f5f1e8); color: var(--avatar-3-fg, #7a5c2e); }
.avatar-dot.pink { background: var(--avatar-4-bg, #eef3ec); color: var(--avatar-4-fg, #4c6b45); }
.owner-name { min-width: 0; overflow: hidden; color: var(--text-primary, #152020); font-size: 13px; text-overflow: ellipsis; }
.unowned-label { color: var(--warning-fg, #a15c07); font-size: 12px; font-weight: 500; }
.claim-mini { height: 24px; padding: 0 8px; border: 1px solid var(--brand-100, #ccede8); border-radius: 6px; background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); font-size: 12px; font-weight: 500; }
.claim-mini:hover:not(:disabled) { background: #DAF0EC; border-color: var(--brand-200, #99dbd2); }
.claim-mini:disabled { opacity: .55; cursor: not-allowed; }

.mobile-pool-list { display: none; }
.empty-state { margin: 16px 20px; padding: 28px 16px; border: 1px dashed var(--border-default, #e2e7e7); border-radius: 9px; background: var(--bg-subtle, #f8fafa); color: var(--text-tertiary, #7a8686); text-align: center; font-size: 13px; }
.pool-pagination { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: auto; padding: 12px 20px; border-top: 1px solid var(--border-subtle, #edf0f0); }
.pool-total { color: var(--text-tertiary, #7a8686); font-size: 12px; font-variant-numeric: tabular-nums; }
.page-controls { display: inline-flex; align-items: center; gap: 4px; }
.page-btn { min-width: 28px; height: 28px; padding: 0 6px; border: 1px solid var(--border-default, #e2e7e7); border-radius: 6px; background: #fff; color: var(--text-secondary, #4a5656); font-size: 12px; }
.page-btn.active { border-color: var(--brand-100, #ccede8); background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); font-weight: 600; }
.page-btn:disabled { opacity: .45; cursor: not-allowed; }

@media (max-width: 1100px) {
  .mid-grid { grid-template-columns: 1fr; }
  .pool-toolbar { justify-content: flex-start; }
}
@media (max-width: 720px) {
  .table-wrap { display: none; }
  .mobile-pool-list { display: grid; gap: 8px; padding: 0 16px 12px; }
  .mobile-pool-item { display: grid; gap: 6px; padding: 10px 12px; border: 1px solid var(--border-default, #e2e7e7); border-radius: 9px; background: #fff; }
  .mobile-pool-item.row-unassigned { background: #FFFBF2; border-left: 3px solid var(--warning-solid, #d97706); }
  .mobile-pool-item.row-overdue { background: #FEF5F4; border-left: 3px solid var(--danger-solid, #c0362c); }
  .mobile-row-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
  .mobile-title { white-space: normal; line-height: 1.4; }
  .mobile-row-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: 12px; }
}
</style>
