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

    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-label">今日工单</span>
          <span class="mock-badge">{{ hasSummaryTotal ? '实时统计' : '模拟' }}</span>
        </div>
        <div class="kpi-value" :class="{ 'is-zero': todayTotalDisplay === 0 }">{{ todayTotalDisplay }}</div>
        <p class="kpi-sub">
          {{ hasSummaryTotal ? '工作台全量累计工单池' : '未接入实时总量，展示演示基准' }}
        </p>
      </div>

      <button
        type="button"
        class="kpi-card kpi-interactive"
        :class="{ warn: unclaimedCountDisplay > 0 }"
        @click="$emit('open-board', 'unclaimed')"
      >
        <div class="kpi-head">
          <span class="kpi-label">待分配</span>
          <span class="mock-badge" :class="{ warn: unclaimedCountDisplay > 0 }">
            {{ hasUnclaimedSummary ? '待认领池' : '模拟' }}
          </span>
        </div>
        <div class="kpi-value" :class="{ warn: unclaimedCountDisplay > 0, 'is-zero': unclaimedCountDisplay === 0 }">
          {{ unclaimedCountDisplay }}
        </div>
        <p class="kpi-sub kpi-link">点击进入待认领池筛选 <span>→</span></p>
      </button>

      <div class="kpi-card" :class="{ danger: overdueCountDisplay > 0 }">
        <div class="kpi-head">
          <span class="kpi-label">处理中超时</span>
          <span class="mock-badge" :class="{ danger: overdueCountDisplay > 0 }">
            {{ hasSlaData ? 'SLA 实时' : '模拟' }}
          </span>
        </div>
        <div class="kpi-value" :class="{ danger: overdueCountDisplay > 0, 'is-zero': overdueCountDisplay === 0 }">
          {{ overdueCountDisplay }}
        </div>
        <p class="kpi-sub">
          {{ hasSlaData ? '依据当前工单列表 SLA 时限统计' : '未加载 SLA 时限，展示模拟值' }}
        </p>
      </div>

      <div class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-label">妥协工单</span>
          <span class="mock-badge" :class="{ warn: compromiseCountDisplay > 0 }">
            {{ hasCompromiseData ? '实时统计' : '模拟' }}
          </span>
        </div>
        <div class="kpi-value" :class="{ 'is-zero': compromiseCountDisplay === 0 }">{{ compromiseCountDisplay }}</div>
        <p class="kpi-sub">
          {{ hasCompromiseData ? '标记为妥协/特殊处理的工单数' : '暂无妥协标记数据，展示模拟值' }}
        </p>
      </div>
    </div>

    <div class="mid-grid">
      <section class="dash-card pool-card" aria-label="工单池列表">
        <header class="card-header pool-header">
          <div class="pool-heading">
            <h2>工单池列表</h2>
            <p class="card-sub">优先高亮超时与待分配工单，支持多维筛选与快速认领</p>
          </div>

          <div class="pool-toolbar">
            <select
              v-model="filterChannel"
              class="pool-filter-select"
              aria-label="来源筛选"
            >
              <option value="">全部来源</option>
              <option v-for="opt in CHANNEL_FILTER_OPTIONS" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>

            <select
              v-model="filterStatus"
              class="pool-filter-select"
              aria-label="状态筛选"
            >
              <option value="">全部状态</option>
              <option v-for="opt in STATUS_FILTER_OPTIONS" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>

            <select
              v-model="filterPriority"
              class="pool-filter-select"
              aria-label="优先级筛选"
            >
              <option value="">全部优先级</option>
              <option v-for="opt in PRIORITY_FILTER_OPTIONS" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>

            <input
              v-model.trim="filterKeyword"
              type="search"
              class="pool-filter-input"
              placeholder="搜索工单号/故障内容"
              aria-label="搜索工单号/故障内容"
              maxlength="60"
            />

            <button
              type="button"
              class="link-btn"
              @click="$emit('open-board', '')"
            >
              查看完整总表 →
            </button>
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
                <tr
                  v-for="row in pagedRows"
                  :key="row.item.id"
                  :class="rowClass(row)"
                >
                  <td>
                    <button
                      type="button"
                      class="order-btn"
                      @click="$emit('open', row.item.id)"
                    >
                      {{ row.number }}
                    </button>
                  </td>
                  <td>
                    <span class="channel-pill">{{ row.channel }}</span>
                  </td>
                  <td>
                    <div class="title-cell">
                      <span v-if="row.priority === 'high'" class="priority-tag high">高优</span>
                      <span v-else-if="row.kind" class="kind-tag" :class="row.item.kind">{{ row.kind }}</span>
                      <button
                        type="button"
                        class="title-btn"
                        :title="row.item.title"
                        @click="$emit('open', row.item.id)"
                      >
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
                    <span class="status-pill" :class="row.status.tone">
                      <i></i>{{ row.status.label }}
                    </span>
                  </td>
                  <td>
                    <div class="owner-cell">
                      <template v-if="row.unowned">
                        <span class="unowned-label">待分配</span>
                        <button
                          v-if="!row.isTerminal"
                          type="button"
                          class="claim-mini"
                          :disabled="busy"
                          @click="$emit('claim', row.item.id)"
                        >
                          认领
                        </button>
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
            <article
              v-for="row in pagedRows"
              :key="'m-' + row.item.id"
              class="mobile-pool-item"
              :class="rowClass(row)"
            >
              <div class="mobile-row-top">
                <button
                  type="button"
                  class="order-btn"
                  @click="$emit('open', row.item.id)"
                >
                  {{ row.number }}
                </button>
                <span class="status-pill" :class="row.status.tone">
                  <i></i>{{ row.status.label }}
                </span>
              </div>
              <button
                type="button"
                class="title-btn mobile-title"
                @click="$emit('open', row.item.id)"
              >
                {{ row.item.title }}
              </button>
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
            <button
              type="button"
              class="page-btn"
              :disabled="currentPage <= 1"
              aria-label="上一页"
              @click="currentPage -= 1"
            >
              ‹
            </button>
            <button
              v-for="p in pageNumbers"
              :key="p"
              type="button"
              class="page-btn"
              :class="{ active: p === currentPage }"
              :aria-current="p === currentPage ? 'page' : undefined"
              @click="currentPage = p"
            >
              {{ p }}
            </button>
            <button
              type="button"
              class="page-btn"
              :disabled="currentPage >= totalPages"
              aria-label="下一页"
              @click="currentPage += 1"
            >
              ›
            </button>
          </div>
        </footer>
      </section>

      <section class="dash-card agent-card" aria-label="坐席在岗状态">
        <header class="card-header">
          <div>
            <h2>坐席在岗状态</h2>
            <p class="card-sub">
              {{ hasRealAssignees ? '点击右侧胶囊切换状态（就绪 → 休息 → 离岗）' : '未加载账号列表，展示模拟坐席（可点击切换演示）' }}
            </p>
          </div>
          <span class="duty-ratio-badge">{{ onDutyCount }} / {{ agentRows.length }} 在岗</span>
        </header>

        <ul class="agent-list">
          <li v-for="agent in agentRows" :key="agent.username" class="agent-row">
            <div class="agent-main">
              <span class="agent-avatar" :class="agent.tone">{{ agent.initial }}</span>
              <div class="agent-info">
                <div class="agent-name-line">
                  <strong>{{ agent.username }}</strong>
                  <span class="role-tag">{{ agent.roleLabel }}</span>
                </div>
                <span class="agent-duration">{{ agent.durationText }}</span>
              </div>
            </div>
            <button
              type="button"
              class="presence-pill"
              :class="agent.status"
              :disabled="busy || updatingAgent === agent.username"
              :title="'点击切换在岗状态（当前：' + agent.statusLabel + '）'"
              @click="cycleAgentDuty(agent)"
            >
              <i></i>{{ agent.statusLabel }}
            </button>
          </li>
        </ul>

        <p v-if="dutyError" role="alert" class="duty-error">{{ dutyError }}</p>

        <div class="agent-footer">
          <button
            type="button"
            class="assign-btn"
            :disabled="busy"
            @click="handleForceDistribute"
          >
            一键强制平均分配
          </button>
          <p v-if="assignNotice" role="status" class="assign-notice">
            {{ assignNotice }}
          </p>
        </div>
      </section>
    </div>

    <div class="bottom-grid">
      <section class="dash-card" aria-label="渠道分布">
        <header class="card-header trend-header">
          <div>
            <h2>渠道分布</h2>
            <p class="card-sub">各受理渠道工单数与占比</p>
          </div>
          <div class="channel-header-right">
            <div class="range-switch" role="group" aria-label="渠道统计周期">
              <button
                v-for="opt in channelRanges"
                :key="opt.id"
                type="button"
                :aria-pressed="channelRange === opt.id"
                @click="channelRange = opt.id"
              >
                {{ opt.label }}
              </button>
            </div>
            <span class="mock-badge">{{ channelUsingLiveData ? '实时' : '模拟' }}</span>
          </div>
        </header>
        <ul class="channel-bars">
          <li v-for="ch in channelDistribution" :key="ch.name" class="channel-bar-row">
            <div class="channel-bar-meta">
              <span class="channel-name">{{ ch.name }}</span>
              <span class="channel-values">
                <span class="channel-count">{{ ch.count }} 单</span>
                <strong class="channel-pct">{{ ch.percent }}%</strong>
              </span>
            </div>
            <div class="bar-track" aria-hidden="true">
              <div
                class="bar-fill"
                :style="{ width: ch.percent + '%', background: ch.color }"
              ></div>
            </div>
          </li>
        </ul>
      </section>

      <section class="dash-card" aria-label="升级与超时趋势">
        <header class="card-header trend-header">
          <div>
            <h2>升级与超时趋势</h2>
            <p class="card-sub">升级工单与 SLA 超时对比（模拟）</p>
          </div>
          <div class="range-switch" role="group" aria-label="趋势时间范围">
            <button
              v-for="opt in trendRanges"
              :key="opt.id"
              type="button"
              :aria-pressed="trendRange === opt.id"
              @click="trendRange = opt.id"
            >
              {{ opt.label }}
            </button>
          </div>
        </header>

        <div class="trend-legend">
          <span class="legend-item"><i class="dot-pine"></i>升级工单数</span>
          <span class="legend-item"><i class="dot-danger"></i>超时工单数</span>
          <span class="mock-badge">模拟</span>
        </div>

        <div class="svg-wrap">
          <svg
            viewBox="0 0 360 156"
            class="trend-svg"
            role="img"
            :aria-label="trendRangeLabel + '升级与超时工单趋势折线图'"
          >
            <g class="grid-lines">
              <line
                v-for="tick in chartTicks"
                :key="tick.value"
                x1="28"
                :y1="tick.y"
                x2="346"
                :y2="tick.y"
              />
              <text
                v-for="tick in chartTicks"
                :key="'t-' + tick.value"
                x="22"
                :y="tick.y + 3"
                text-anchor="end"
              >
                {{ tick.value }}
              </text>
            </g>

            <polyline
              fill="none"
              stroke="#0f766e"
              stroke-width="2.2"
              stroke-linecap="round"
              stroke-linejoin="round"
              :points="escalatedPoints"
            />
            <polyline
              fill="none"
              stroke="#c0362c"
              stroke-width="2.2"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-dasharray="4 3"
              :points="overduePoints"
            />

            <g v-for="pt in chartPoints" :key="pt.label">
              <circle :cx="pt.x" :cy="pt.yEsc" r="3.2" fill="#fff" stroke="#0f766e" stroke-width="2">
                <title>{{ pt.label }} 升级工单：{{ pt.escalated }}</title>
              </circle>
              <circle :cx="pt.x" :cy="pt.yOver" r="3.2" fill="#fff" stroke="#c0362c" stroke-width="2">
                <title>{{ pt.label }} 超时工单：{{ pt.overdue }}</title>
              </circle>
              <text :x="pt.x" y="146" text-anchor="middle" class="x-label">{{ pt.label }}</text>
            </g>
          </svg>
        </div>
      </section>

      <section class="dash-card" aria-label="本月排班">
        <header class="card-header trend-header">
          <div>
            <h2>本月排班</h2>
            <p class="card-sub">早班 / 晚班坐席轮值安排</p>
          </div>
          <div class="schedule-controls">
            <select
              v-model="selectedMonth"
              class="month-select"
              aria-label="选择排班月份"
            >
              <option v-for="m in monthOptions" :key="m.value" :value="m.value">
                {{ m.label }}
              </option>
            </select>
            <span class="mock-badge">模拟</span>
          </div>
        </header>

        <div class="schedule-wrap">
          <table class="schedule-table">
            <colgroup>
              <col class="col-shift" />
              <col v-for="day in weekDays" :key="'col-' + day" />
            </colgroup>
            <thead>
              <tr>
                <th>班次</th>
                <th v-for="(day, dIdx) in weekDays" :key="day" :class="{ 'is-today': dIdx === todayWeekdayIndex }">{{ day }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="shift in shiftRows" :key="shift.name">
                <th scope="row" class="shift-col">
                  <strong>{{ shift.name }}</strong>
                  <span>{{ shift.hours }}</span>
                </th>
                <td v-for="(member, idx) in shift.members" :key="idx" :class="{ 'is-today': idx === todayWeekdayIndex }">
                  <span class="shift-chip" :class="shift.tone" :title="member">{{ member }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="schedule-summary">
          {{ scheduleSummaryText }}
        </footer>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { updateUserDuty } from '../api/workbench'
import {
  avatarTone,
  channelLabel,
  displayStatus,
  initial,
  isUnowned,
  kindLabel,
  orderNumber,
  roleLabel,
  shanghaiStamp,
  slaPresentation,
  stateCount
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
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(new Date(ms))
  const read = type => parts.find(part => part.type === type).value
  return `${read('year')}-${read('month')}-${read('day')} ${read('hour')}:${read('minute')}:${read('second')}`
}

const liveClock = computed(() => formatShanghaiClock(nowMs.value))

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

function onKeyDown(event) {
  if (event.key === 'Escape' && isFullscreen.value) {
    isFullscreen.value = false
  }
}

onMounted(() => {
  clockTimer = setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
  window.removeEventListener('keydown', onKeyDown)
})

const assignNotice = ref('')
const dutyError = ref('')
const updatingAgent = ref('')
const trendRange = ref('30d')
const channelRange = ref('month')

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
  if (props.items.length) {
    return props.items.filter(item => isUnowned(item) && !TERMINAL_STATES.has(item.state)).length
  }
  return 6
})

const hasSlaData = computed(() => props.items.some(item => item && item.slaDueAt))
const overdueCountDisplay = computed(() => {
  if (hasSlaData.value) {
    return props.items.filter(item => {
      if (!item || TERMINAL_STATES.has(item.state)) return false
      return Boolean(slaPresentation(item.slaDueAt, item.state)?.overdue)
    }).length
  }
  return 2
})

const hasCompromiseData = computed(() => props.items.some(item => item && Object.prototype.hasOwnProperty.call(item, 'compromiseFlag')))
const compromiseCountDisplay = computed(() => {
  if (hasCompromiseData.value) {
    return props.items.filter(item => Number(item.compromiseFlag || 0) === 1 || item.compromiseFlag === true).length
  }
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
    return fallback
      ? { label: fallback.text, tone: fallback.overdue ? 'overdue' : 'normal', overdue: fallback.overdue }
      : { label: '—', tone: 'none', overdue: false }
  }

  const diffMins = Math.round((dueEpoch - nowEpoch) / 60000)
  if (diffMins <= 0) {
    const over = Math.abs(diffMins)
    if (over >= 60) {
      const h = Math.floor(over / 60)
      const m = over % 60
      return { label: m ? `已超时 ${h}h${m}m` : `已超时 ${h}h`, tone: 'overdue', overdue: true }
    }
    return { label: over === 0 ? '已到期' : `已超时 ${over}m`, tone: 'overdue', overdue: true }
  }
  if (diffMins < 60) {
    return { label: `剩余 ${diffMins}m`, tone: diffMins <= 30 ? 'soon' : 'normal', overdue: false }
  }
  const h = Math.floor(diffMins / 60)
  const m = diffMins % 60
  return {
    label: m ? `剩余 ${h}h${m}m` : `剩余 ${h}h`,
    tone: diffMins <= 60 ? 'soon' : 'normal',
    overdue: false
  }
}

function normalizeStatusFilterKey(row) {
  const state = row.item?.state || ''
  const sid = row.status?.id || ''
  if (row.unowned || state === 'pending_assign' || sid === 'unclaimed') return 'pending_assign'
  if (state === 'processing' || state === 'open' || sid === 'processing' || sid === 'open') return 'processing'
  if (state === 'suspended' || state === 'waiting' || sid === 'suspended' || sid === 'waiting') return 'suspended'
  if (state === 'pending_callback' || sid === 'pending_callback' || sid === 'fieldPending' || sid === 'fieldActive') return 'pending_callback'
  if (state === 'pending_accept' || sid === 'pending_accept' || sid === 'verify') return 'pending_accept'
  if (state === 'cancelled' || sid === 'cancelled') return 'cancelled'
  if (TERMINAL_STATES.has(state) || TERMINAL_STATES.has(sid)) return 'closed'
  return sid || state
}

const allPoolRows = computed(() => {
  return props.items.map(item => {
    const isTerminal = TERMINAL_STATES.has(item.state)
    const unowned = isUnowned(item) && !isTerminal
    const sla = formatSlaRemaining(item)
    const priority = String(item.urgency || item.priority || 'normal').toLowerCase()
    return {
      item,
      number: orderNumber(item),
      channelKey: item.channel || '',
      channel: channelLabel(item.channel) || item.channel || '未标注',
      kind: kindLabel(item.kind),
      priority,
      status: displayStatus(item),
      isTerminal,
      unowned,
      sla,
      compromise: Number(item.compromiseFlag || 0) === 1 || item.compromiseFlag === true,
      initial: initial(item.owner),
      avatarTone: avatarTone(item.owner)
    }
  })
})

const hasActivePoolFilter = computed(() =>
  Boolean(filterChannel.value || filterStatus.value || filterPriority.value || filterKeyword.value)
)

const filteredRows = computed(() => {
  const kw = filterKeyword.value.toLowerCase()
  return allPoolRows.value.filter(row => {
    if (filterChannel.value && row.channelKey !== filterChannel.value) return false
    if (filterStatus.value && normalizeStatusFilterKey(row) !== filterStatus.value) return false
    if (filterPriority.value && row.priority !== filterPriority.value) return false
    if (kw) {
      const hay = [
        row.number,
        row.item.title,
        row.item.description,
        row.item.device,
        row.item.category
      ].filter(Boolean).join(' ').toLowerCase()
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
  const total = totalPages.value
  const curr = Math.min(Math.max(1, currentPage.value), total)
  const start = Math.max(1, Math.min(curr - 2, total - 4))
  const end = Math.min(total, start + 4)
  const pages = []
  for (let i = start; i <= end; i += 1) pages.push(i)
  return pages
})

watch([filterChannel, filterStatus, filterPriority, filterKeyword, () => props.items.length], () => {
  currentPage.value = 1
})

watch(totalPages, nextTotal => {
  if (currentPage.value > nextTotal) currentPage.value = nextTotal
})

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

watch(
  () => props.assignees,
  list => {
    const names = new Set((list || []).map(a => a && a.username).filter(Boolean))
    Object.keys(dutyOverrides).forEach(key => {
      if (!names.has(key)) delete dutyOverrides[key]
    })
  },
  { deep: true }
)

const hasRealAssignees = computed(() => Array.isArray(props.assignees) && props.assignees.length > 0)

function formatElapsedFromStamp(value) {
  if (!value) return ''
  let epoch = null
  if (Array.isArray(value) && value.length >= 5) {
    epoch = Date.UTC(value[0], value[1] - 1, value[2], value[3], value[4])
  } else {
    epoch = parseShanghaiEpoch(value)
  }
  if (epoch === null) return ''
  const nowEpoch = parseShanghaiEpoch(shanghaiStamp(new Date(nowMs.value)))
  if (nowEpoch === null) return ''
  const diffMins = Math.max(0, Math.round((nowEpoch - epoch) / 60000))
  const h = Math.floor(diffMins / 60)
  const m = diffMins % 60
  if (h > 0) return `${String(h).padStart(2, '0')}h ${String(m).padStart(2, '0')}m`
  return `${m}m`
}

const agentRows = computed(() => {
  if (!hasRealAssignees.value) {
    return MOCK_AGENTS.map(item => {
      const status = mockDutyStates[item.username] || item.status
      return {
        username: item.username,
        roleLabel: item.roleLabel,
        status,
        statusLabel: DUTY_LABELS[status] || '就绪',
        durationText: `状态时长（模拟）：${item.duration}`,
        initial: initial(item.username),
        tone: avatarTone(item.username)
      }
    })
  }
  return props.assignees.map(item => {
    const override = dutyOverrides[item.username]
    const rawStatus = override?.status || item.dutyStatus || 'ready'
    const status = DUTY_ORDER.includes(rawStatus) ? rawStatus : 'ready'
    const updatedAt = override?.updatedAt || item.dutyUpdatedAt
    const elapsed = formatElapsedFromStamp(updatedAt)
    return {
      username: item.username,
      roleLabel: roleLabel(item.role),
      status,
      statusLabel: DUTY_LABELS[status] || '就绪',
      durationText: elapsed ? `持续时长：${elapsed}` : '状态已同步',
      initial: initial(item.username),
      tone: avatarTone(item.username)
    }
  })
})

const onDutyCount = computed(() => agentRows.value.filter(agent => agent.status !== 'away').length)

async function cycleAgentDuty(agent) {
  if (updatingAgent.value === agent.username) return
  const idx = DUTY_ORDER.indexOf(agent.status)
  const nextStatus = DUTY_ORDER[(idx + 1) % DUTY_ORDER.length]
  dutyError.value = ''

  if (!hasRealAssignees.value) {
    mockDutyStates[agent.username] = nextStatus
    return
  }

  const prev = dutyOverrides[agent.username] ? { ...dutyOverrides[agent.username] } : null
  dutyOverrides[agent.username] = {
    status: nextStatus,
    updatedAt: shanghaiStamp(new Date(nowMs.value))
  }
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

const channelRanges = [
  { id: 'today', label: '今日' },
  { id: '7d', label: '近7天' },
  { id: 'month', label: '本月' }
]

const CHANNEL_MOCK = {
  today: [
    { name: '400电话', count: 14, percent: 44, color: '#0f766e' },
    { name: '客户小程序', count: 8, percent: 25, color: '#2dd4bf' },
    { name: '服务商扫码', count: 6, percent: 19, color: '#0d9488' },
    { name: '企微群', count: 4, percent: 12, color: '#64748b' }
  ],
  '7d': [
    { name: '400电话', count: 52, percent: 41, color: '#0f766e' },
    { name: '服务商扫码', count: 29, percent: 23, color: '#0d9488' },
    { name: '客户小程序', count: 25, percent: 20, color: '#2dd4bf' },
    { name: '企微群', count: 20, percent: 16, color: '#64748b' }
  ],
  month: [
    { name: '400电话', count: 148, percent: 40, color: '#0f766e' },
    { name: '服务商扫码', count: 82, percent: 22, color: '#0d9488' },
    { name: '客户小程序', count: 74, percent: 20, color: '#2dd4bf' },
    { name: '企微群', count: 66, percent: 18, color: '#64748b' }
  ]
}

const CHANNEL_COLOR_MAP = {
  '400': '#0f766e',
  provider: '#0d9488',
  miniprogram: '#2dd4bf',
  wecom: '#64748b',
  phone: '#14b8a6'
}

const channelLiveItems = computed(() => {
  if (!props.items.length) return []
  const nowStamp = shanghaiStamp(new Date(nowMs.value))
  const todayStr = nowStamp.slice(0, 10)
  const monthStr = nowStamp.slice(0, 7)
  const nowEpoch = parseShanghaiEpoch(nowStamp)

  return props.items.filter(item => {
    if (!item || !item.updatedAt) return channelRange.value === 'month'
    const stamp = String(item.updatedAt).replace('T', ' ').slice(0, 16)
    if (channelRange.value === 'today') return stamp.slice(0, 10) === todayStr
    if (channelRange.value === 'month') return stamp.slice(0, 7) === monthStr
    const epoch = parseShanghaiEpoch(stamp)
    if (epoch === null || nowEpoch === null) return true
    return (nowEpoch - epoch) <= 7 * 24 * 60 * 60 * 1000
  })
})

const channelUsingLiveData = computed(() => channelLiveItems.value.length > 0)

const channelDistribution = computed(() => {
  const matched = channelLiveItems.value
  if (!matched.length) {
    return CHANNEL_MOCK[channelRange.value] || CHANNEL_MOCK.month
  }
  const counts = {}
  matched.forEach(item => {
    const key = item.channel || 'other'
    counts[key] = (counts[key] || 0) + 1
  })
  const total = matched.length
  return Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .map(([key, count]) => ({
      name: channelLabel(key) || (key === 'other' ? '未标注' : key),
      count,
      percent: Math.round((count / total) * 100),
      color: CHANNEL_COLOR_MAP[key] || '#64748b'
    }))
})

const trendRanges = [
  { id: '7d', label: '近7天' },
  { id: '30d', label: '近30天' },
  { id: 'month', label: '本月' }
]

const TREND_DATA = {
  '7d': [
    { label: '周一', escalated: 4, overdue: 2 },
    { label: '周二', escalated: 6, overdue: 3 },
    { label: '周三', escalated: 5, overdue: 1 },
    { label: '周四', escalated: 8, overdue: 4 },
    { label: '周五', escalated: 7, overdue: 2 },
    { label: '周六', escalated: 4, overdue: 2 },
    { label: '周日', escalated: 5, overdue: 1 }
  ],
  '30d': [
    { label: '第1周', escalated: 14, overdue: 7 },
    { label: '第2周', escalated: 18, overdue: 9 },
    { label: '第3周', escalated: 15, overdue: 5 },
    { label: '第4周', escalated: 19, overdue: 6 },
    { label: '近3日', escalated: 11, overdue: 3 }
  ],
  month: [
    { label: '1日', escalated: 3, overdue: 1 },
    { label: '6日', escalated: 7, overdue: 3 },
    { label: '11日', escalated: 6, overdue: 4 },
    { label: '16日', escalated: 9, overdue: 2 },
    { label: '21日', escalated: 8, overdue: 3 },
    { label: '26日', escalated: 5, overdue: 2 }
  ]
}

const trendRangeLabel = computed(() => trendRanges.find(r => r.id === trendRange.value)?.label || '近30天')

const activeSeries = computed(() => TREND_DATA[trendRange.value] || TREND_DATA['30d'])

const chartMax = computed(() => {
  const maxVal = Math.max(4, ...activeSeries.value.flatMap(d => [d.escalated, d.overdue]))
  return Math.ceil(maxVal / 5) * 5
})

const chartTicks = computed(() => {
  const max = chartMax.value
  const top = 16
  const bottom = 124
  return [0, Math.round(max / 2), max].map(val => ({
    value: val,
    y: bottom - ((val / max) * (bottom - top))
  }))
})

const chartPoints = computed(() => {
  const list = activeSeries.value
  const left = 42
  const right = 336
  const top = 16
  const bottom = 124
  const max = chartMax.value
  const step = list.length > 1 ? (right - left) / (list.length - 1) : 0
  return list.map((item, idx) => ({
    ...item,
    x: Math.round((left + step * idx) * 10) / 10,
    yEsc: Math.round((bottom - (item.escalated / max) * (bottom - top)) * 10) / 10,
    yOver: Math.round((bottom - (item.overdue / max) * (bottom - top)) * 10) / 10
  }))
})

const escalatedPoints = computed(() => chartPoints.value.map(p => `${p.x},${p.yEsc}`).join(' '))
const overduePoints = computed(() => chartPoints.value.map(p => `${p.x},${p.yOver}`).join(' '))

function buildMonthOptions() {
  const stamp = shanghaiStamp()
  const year = Number(stamp.slice(0, 4))
  const month = Number(stamp.slice(5, 7))
  const offsets = [-1, 0, 1, 2]
  return offsets.map(offset => {
    const d = new Date(Date.UTC(year, month - 1 + offset, 1))
    const y = d.getUTCFullYear()
    const m = d.getUTCMonth() + 1
    const val = `${y}-${String(m).padStart(2, '0')}`
    return { value: val, label: `${y}年${m}月` }
  })
}

const monthOptions = buildMonthOptions()
const selectedMonth = ref(shanghaiStamp().slice(0, 7))

const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const todayWeekdayIndex = computed(() => {
  const day = new Date(nowMs.value + 8 * 3600 * 1000).getUTCDay()
  return day === 0 ? 6 : day - 1
})
const shiftRows = computed(() => {
  const names = hasRealAssignees.value && props.assignees.length >= 2
    ? props.assignees.map(a => a.username)
    : ['张三', '李四', '赵六', '陈七']
  const monthIdx = Math.max(0, monthOptions.findIndex(m => m.value === selectedMonth.value))
  const pick = idx => names[(idx + monthIdx) % names.length]
  return [
    {
      name: '早班',
      hours: '09:00-18:00',
      tone: 'early',
      members: [pick(0), pick(1), pick(0), pick(2), pick(1), pick(0), pick(3)]
    },
    {
      name: '晚班',
      hours: '14:00-23:00',
      tone: 'late',
      members: [pick(2), pick(3), pick(1), pick(3), pick(2), pick(1), pick(0)]
    }
  ]
})

const scheduleSummaryText = computed(() => {
  const activeMonth = monthOptions.find(m => m.value === selectedMonth.value)?.label || selectedMonth.value
  const earlyCount = shiftRows.value[0]?.members.length || 7
  const lateCount = shiftRows.value[1]?.members.length || 7
  return `${activeMonth}排班汇总：早班（09:00-18:00）${earlyCount} 班次 · 晚班（14:00-23:00）${lateCount} 班次 · 交接重合时段 14:00-18:00`
})
</script>

<style scoped>
.dispatch-dashboard {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}

.dispatch-dashboard.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 100;
  overflow: auto;
  background: #f5f7fa;
  padding: 20px;
  margin-top: 0;
}

.dash-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  min-height: 56px;
  padding: 10px var(--card-px, 20px);
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  background: var(--bg-surface, #ffffff);
  box-shadow: var(--elev-1);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  min-width: 0;
}

.topbar-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.live-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success-solid, #1f8f5c);
  box-shadow: 0 0 0 3px rgba(31, 143, 92, 0.18);
  flex: none;
}

.topbar-title {
  margin: 0;
  color: var(--text-primary, #152020);
  font-size: 16px;
  font-weight: 600;
}

.mode-badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
}

.topbar-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 4px 10px;
  border-radius: var(--radius-sm, 6px);
  background: var(--bg-subtle, #f8fafa);
  border: 0;
  font-size: 12px;
}

.live-clock {
  color: var(--text-primary, #152020);
  font-family: var(--font-mono, monospace);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.meta-sep {
  color: var(--text-tertiary, #7a8686);
}

.duty-stat {
  color: var(--brand-700, #0f766e);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.esc-hint {
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
}

.fullscreen-btn svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.fullscreen-btn.active {
  border-color: var(--brand-700, #0f766e);
  background: var(--brand-700, #0f766e);
  color: #fff;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--gutter, 16px);
}

.kpi-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 112px;
  gap: 4px;
  padding: 16px 20px;
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  background: var(--bg-surface, #ffffff);
  box-shadow: var(--elev-1);
  text-align: left;
  transition: border-color .15s ease, box-shadow .15s ease;
}

.kpi-interactive {
  cursor: pointer;
}

.kpi-interactive:hover {
  border-color: var(--border-strong, #cfd7d7);
  box-shadow: var(--elev-2);
}

.kpi-card.warn {
  border-color: var(--warning-border, #f3d7a6);
  background: linear-gradient(180deg, #FFFAF1 0%, #FFFFFF 72%);
}

.kpi-card.danger {
  border-color: var(--danger-border, #f4c5c0);
  background: linear-gradient(180deg, #FFF8F7 0%, #FFFFFF 72%);
}

.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.kpi-label {
  color: var(--text-secondary, #4a5656);
  font-size: 13px;
  font-weight: 500;
}

.kpi-value {
  color: var(--text-primary, #152020);
  font-family: var(--font-num, sans-serif);
  font-size: 32px;
  line-height: 40px;
  font-weight: 600;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.kpi-value.is-zero { color: var(--text-disabled, #a8b2b2); }
.kpi-value.warn { color: var(--warning-fg, #a15c07); }
.kpi-value.danger { color: var(--danger-fg, #c0362c); }

.kpi-sub {
  margin: 0;
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  line-height: 1.45;
}

.kpi-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--brand-700, #0f766e);
  font-weight: 500;
}

.mock-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border: 0;
  border-radius: 4px;
  background: var(--neutral-bg, #f1f4f4);
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.mock-badge.warn {
  background: var(--warning-bg, #fef5e6);
  color: var(--warning-fg, #a15c07);
}

.mock-badge.danger {
  background: var(--danger-bg, #fdeeec);
  color: var(--danger-fg, #c0362c);
}

.duty-ratio-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border: 1px solid var(--brand-100, #ccede8);
  border-radius: 999px;
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.mid-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.75fr) minmax(290px, 0.9fr);
  gap: var(--gutter, 16px);
  align-items: stretch;
}

.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.1fr) minmax(0, 1.1fr);
  gap: var(--gutter, 16px);
  align-items: stretch;
}

.dash-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 20px;
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  background: var(--bg-surface, #ffffff);
  box-shadow: var(--elev-1);
}

.pool-card {
  padding: 0;
  overflow: hidden;
}

.pool-card .pool-header {
  padding: 16px 20px 12px;
  margin-bottom: 0;
}

.pool-card .pool-pagination {
  padding: 12px 20px;
  border-top: 1px solid var(--border-subtle, #edf0f0);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.card-header h2 {
  margin: 0;
  color: var(--text-primary, #152020);
  font-size: 15px;
  font-weight: 600;
}

.card-sub {
  margin: 2px 0 0;
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
}

.pool-header {
  flex-wrap: wrap;
  align-items: center;
}

.pool-heading {
  min-width: 150px;
}

.pool-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 100%;
}

.pool-filter-select,
.pool-filter-input,
.month-select {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--border-strong, #cfd7d7);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-surface, #ffffff);
  color: var(--text-primary, #152020);
  font-size: 13px;
  outline: none;
  transition: border-color .15s ease, box-shadow .15s ease;
}

.pool-filter-select:focus,
.pool-filter-input:focus,
.month-select:focus {
  border-color: var(--brand-600, #0d9488);
  box-shadow: var(--focus-ring);
}

.pool-filter-input {
  width: 168px;
  min-width: 120px;
}

.link-btn {
  flex: none;
  height: 32px;
  padding: 0 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--brand-700, #0f766e);
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.link-btn:hover {
  color: var(--brand-800, #115e59);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.table-wrap {
  overflow-x: auto;
  border: 0;
  border-top: 1px solid var(--border-subtle, #edf0f0);
  border-radius: 0;
}

.pool-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  text-align: left;
}

.pool-table .col-order { width: 158px; }
.pool-table .col-channel { width: 88px; }
.pool-table .col-title { width: auto; }
.pool-table .col-sla { width: 98px; }
.pool-table .col-status { width: 92px; }
.pool-table .col-owner { width: 112px; }

.pool-table th {
  height: 40px;
  padding: 0 10px;
  border-bottom: 1px solid var(--border-default, #e2e7e7);
  background: var(--bg-subtle, #f8fafa);
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.pool-table td {
  height: 48px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-subtle, #edf0f0);
  vertical-align: middle;
  font-size: 13px;
}

.pool-table th:first-child,
.pool-table td:first-child {
  padding-left: 20px;
}

.pool-table th:last-child,
.pool-table td:last-child {
  padding-right: 20px;
}

.pool-table tbody tr:hover td {
  background: var(--bg-hover, #f4f8f7);
}

.pool-table tbody tr:last-child td {
  border-bottom: 0;
}

.pool-table tr.row-unassigned td {
  background: #FFFBF2;
}

.pool-table tr.row-unassigned td:first-child {
  box-shadow: inset 3px 0 0 var(--warning-solid, #d97706);
}

.pool-table tr.row-overdue td {
  background: #FEF5F4;
}

.pool-table tr.row-overdue td:first-child {
  box-shadow: inset 3px 0 0 var(--danger-solid, #c0362c);
}

.order-btn {
  display: block;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--brand-700, #0f766e);
  font-family: var(--font-mono, monospace);
  font-size: 13px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.order-btn:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.channel-pill {
  display: inline-flex;
  align-items: center;
  height: 20px;
  max-width: 100%;
  overflow: hidden;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--neutral-bg, #f1f4f4);
  color: var(--text-secondary, #4a5656);
  font-size: 12px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.title-btn {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary, #152020);
  font-size: 13px;
  font-weight: 500;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title-btn:hover {
  color: var(--brand-700, #0f766e);
}

.kind-tag,
.compromise-tag,
.priority-tag {
  flex: none;
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1;
}

.kind-tag.fault { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); }
.kind-tag.consult { background: var(--info-bg, #edf3f9); color: var(--info-fg, #2f5f8a); }
.kind-tag.service { background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); }
.priority-tag.high { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); font-weight: 600; }
.compromise-tag { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); font-weight: 600; }

.sla-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  overflow: hidden;
  color: var(--text-secondary, #4a5656);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sla-text i {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.sla-text.overdue { color: var(--danger-fg, #c0362c); font-weight: 600; }
.sla-text.soon { color: var(--warning-fg, #a15c07); font-weight: 600; }
.sla-text.normal { color: var(--brand-700, #0f766e); }

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  max-width: 100%;
  overflow: hidden;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill i { flex: none; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.status-pill.processing { background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); }
.status-pill.waiting { background: var(--warning-bg, #fef5e6); color: var(--warning-fg, #a15c07); }
.status-pill.dispatch { background: var(--info-bg, #edf3f9); color: var(--info-fg, #2f5f8a); }
.status-pill.onsite { background: var(--info-bg, #edf3f9); color: var(--info-fg, #2f5f8a); }
.status-pill.verify { background: var(--warning-bg, #fef5e6); color: var(--warning-fg, #a15c07); }
.status-pill.closed { background: var(--neutral-bg, #f1f4f4); color: var(--neutral-fg, #4a5656); }
.status-pill.triage { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); }
.status-pill.follow { background: var(--success-bg, #eaf6ef); color: var(--success-fg, #18794e); }
.status-pill.answered { background: var(--neutral-bg, #f1f4f4); color: var(--neutral-fg, #4a5656); }
.status-pill.referred { background: var(--neutral-bg, #f1f4f4); color: var(--neutral-fg, #4a5656); }

.owner-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.avatar-dot, .agent-avatar {
  display: inline-grid;
  place-items: center;
  flex: none;
  border-radius: 50%;
  font-weight: 600;
}

.avatar-dot { width: 22px; height: 22px; font-size: 12px; }
.agent-avatar { width: 32px; height: 32px; font-size: 13px; }

.avatar-dot.teal, .agent-avatar.teal { background: var(--avatar-1-bg, #e8f6f4); color: var(--avatar-1-fg, #0f766e); }
.avatar-dot.blue, .agent-avatar.blue { background: var(--avatar-2-bg, #edf1f5); color: var(--avatar-2-fg, #45607a); }
.avatar-dot.purple, .agent-avatar.purple { background: var(--avatar-3-bg, #f5f1e8); color: var(--avatar-3-fg, #7a5c2e); }
.avatar-dot.pink, .agent-avatar.pink { background: var(--avatar-4-bg, #eef3ec); color: var(--avatar-4-fg, #4c6b45); }

.owner-name {
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary, #152020);
  font-size: 13px;
  text-overflow: ellipsis;
}

.unowned-label {
  color: var(--warning-fg, #a15c07);
  font-size: 12px;
  font-weight: 500;
}

.claim-mini {
  height: 24px;
  padding: 0 8px;
  border: 1px solid var(--brand-100, #ccede8);
  border-radius: 6px;
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 500;
}

.claim-mini:hover:not(:disabled) {
  background: #DAF0EC;
  border-color: var(--brand-200, #99dbd2);
}

.claim-mini:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.mobile-pool-list {
  display: none;
}

.empty-state {
  margin: 0;
  padding: 28px 16px;
  border: 1px dashed var(--line);
  border-radius: 9px;
  background: var(--canvas);
  color: var(--muted);
  text-align: center;
  font-size: 12px;
}

.pool-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 12px;
}

.pool-total {
  color: var(--muted);
  font-size: 12px;
}

.page-controls {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.page-btn {
  min-width: 26px;
  height: 26px;
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #fff;
  color: var(--ink);
  font-size: 12px;
  font-weight: 500;
  transition: border-color .15s ease, background .15s ease, color .15s ease;
}

.page-btn:hover:not(:disabled):not(.active) {
  border-color: var(--pine-bright);
  color: var(--pine);
}

.page-btn.active {
  border-color: var(--pine);
  background: var(--pine);
  color: #fff;
  font-weight: 700;
}

.page-btn:disabled {
  opacity: .45;
  cursor: not-allowed;
}

.agent-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.agent-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 56px;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid var(--border-subtle, #edf0f0);
  background: transparent;
}

.agent-row:last-child {
  border-bottom: 0;
}

.agent-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.agent-info {
  min-width: 0;
}

.agent-name-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.agent-name-line strong {
  color: var(--text-primary, #152020);
  font-size: 13px;
  font-weight: 600;
}

.role-tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--neutral-bg, #f1f4f4);
  color: var(--text-secondary, #4a5656);
  font-size: 12px;
  font-weight: 500;
}

.agent-duration {
  display: block;
  margin-top: 2px;
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.presence-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  height: 26px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color .15s ease, background .15s ease;
}

.presence-pill:hover:not(:disabled) {
  border-color: currentColor;
}

.presence-pill:disabled {
  opacity: .6;
  cursor: not-allowed;
}

.presence-pill i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.presence-pill.ready {
  background: var(--success-bg, #eaf6ef);
  border-color: var(--success-border, #bfe3ce);
  color: var(--success-fg, #18794e);
}

.presence-pill.break {
  background: var(--warning-bg, #fef5e6);
  border-color: var(--warning-border, #f3d7a6);
  color: var(--warning-fg, #a15c07);
}

.presence-pill.away {
  background: var(--neutral-bg, #f1f4f4);
  border-color: var(--neutral-border, #e2e7e7);
  color: var(--neutral-fg, #4a5656);
}

.duty-error {
  margin: 8px 0 0;
  padding: 8px 10px;
  border: 1px solid var(--danger-border, #f4c5c0);
  border-radius: 8px;
  background: var(--danger-bg, #fdeeec);
  color: var(--danger-fg, #c0362c);
  font-size: 12px;
}

.agent-footer {
  margin-top: auto;
  padding-top: 12px;
}

.assign-btn {
  width: 100%;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--border-strong, #cfd7d7);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-surface, #ffffff);
  color: var(--text-primary, #152020);
  font-size: 13px;
  font-weight: 500;
  box-shadow: var(--elev-1);
  transition: background .15s ease, border-color .15s ease;
}

.assign-btn:hover:not(:disabled) {
  background: var(--bg-subtle, #f8fafa);
  border-color: var(--border-hover, #b5c0c0);
}

.assign-btn:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.assign-notice {
  margin: 8px 0 0;
  padding: 8px 10px;
  border: 1px solid var(--brand-100, #ccede8);
  border-radius: 8px;
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  line-height: 1.5;
}

.channel-header-right,
.schedule-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.channel-bars {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: grid;
  gap: 14px;
}

.channel-bar-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 13px;
}

.channel-name {
  color: var(--text-primary, #152020);
  font-weight: 500;
}

.channel-values {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.channel-count {
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.channel-pct {
  color: var(--text-primary, #152020);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.bar-track {
  height: 8px;
  border-radius: 4px;
  background: var(--bg-muted, #f1f4f4);
  border: 0;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width .25s ease;
}

.trend-header {
  align-items: center;
  flex-wrap: wrap;
}

.range-switch {
  display: inline-flex;
  padding: 2px;
  border-radius: 7px;
  background: var(--bg-muted, #f1f4f4);
}

.range-switch button {
  height: 24px;
  padding: 0 10px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-secondary, #4a5656);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.range-switch button[aria-pressed="true"] {
  background: var(--bg-surface, #ffffff);
  color: var(--brand-700, #0f766e);
  font-weight: 600;
  box-shadow: var(--elev-seg);
}

.trend-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-secondary, #4a5656);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.dot-pine, .dot-danger {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-pine { background: var(--chart-1, #0d9488); }
.dot-danger { background: var(--chart-2, #c0362c); }

.svg-wrap {
  width: 100%;
  margin-top: auto;
}

.trend-svg {
  width: 100%;
  height: auto;
  overflow: visible;
}

.grid-lines line {
  stroke: var(--chart-grid, #eef1f1);
  stroke-width: 1;
  stroke-dasharray: 4 4;
}

.grid-lines text,
.x-label {
  fill: var(--chart-axis, #7a8686);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.schedule-wrap {
  overflow-x: auto;
  margin-top: 4px;
  border: 0;
  border-top: 1px solid var(--border-subtle, #edf0f0);
  border-radius: 0;
}

.schedule-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  text-align: center;
}

.schedule-table .col-shift {
  width: 86px;
}

.schedule-table th,
.schedule-table td {
  padding: 8px 3px;
  border-bottom: 1px solid var(--border-subtle, #edf0f0);
  font-size: 12px;
}

.schedule-table th.is-today,
.schedule-table td.is-today {
  background: var(--brand-50, #e8f6f4);
}

.schedule-table thead th {
  background: var(--bg-subtle, #f8fafa);
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.schedule-table tbody tr:last-child th,
.schedule-table tbody tr:last-child td {
  border-bottom: 0;
}

.schedule-table tbody th.shift-col {
  text-align: left;
  padding: 8px 6px 8px 2px;
  white-space: nowrap;
}

.shift-col strong {
  display: block;
  color: var(--text-primary, #152020);
  font-size: 13px;
  font-weight: 600;
}

.shift-col span {
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}

.shift-chip {
  display: inline-block;
  max-width: 100%;
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  vertical-align: middle;
}

.shift-chip.early {
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-weight: 500;
}

.shift-chip.late {
  background: var(--neutral-bg, #f1f4f4);
  color: var(--text-secondary, #4a5656);
}

.schedule-summary {
  margin-top: auto;
  padding-top: 10px;
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .mid-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
  .pool-toolbar {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
  .table-wrap {
    display: none;
  }
  .mobile-pool-list {
    display: grid;
    gap: 8px;
  }
  .mobile-pool-item {
    display: grid;
    gap: 6px;
    padding: 10px 12px;
    border: 1px solid var(--line);
    border-radius: 9px;
    background: #fff;
  }
  .mobile-pool-item.row-unassigned {
    background: #fffbf2;
    border-left: 3px solid #eab65f;
  }
  .mobile-pool-item.row-overdue {
    background: #fdf4f3;
    border-left: 3px solid var(--danger);
  }
  .mobile-row-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  .mobile-title {
    max-width: 100%;
    white-space: normal;
    line-height: 1.4;
  }
  .mobile-row-meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    font-size: 12px;
  }
}
</style>
