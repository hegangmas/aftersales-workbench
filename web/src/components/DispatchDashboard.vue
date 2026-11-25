<template>
  <section class="dispatch-dashboard" aria-label="统一工单池调度大屏">
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-label">今日工单</span>
          <span class="mock-badge">{{ hasSummaryTotal ? '实时统计' : '模拟' }}</span>
        </div>
        <div class="kpi-value">{{ todayTotalDisplay }}</div>
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
        <div class="kpi-value" :class="{ warn: unclaimedCountDisplay > 0 }">
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
        <div class="kpi-value" :class="{ danger: overdueCountDisplay > 0 }">
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
        <div class="kpi-value">{{ compromiseCountDisplay }}</div>
        <p class="kpi-sub">
          {{ hasCompromiseData ? '标记为妥协/特殊处理的工单数' : '暂无妥协标记数据，展示模拟值' }}
        </p>
      </div>
    </div>

    <div class="mid-grid">
      <section class="dash-card pool-card" aria-label="工单池列表">
        <header class="card-header">
          <div>
            <h2>工单池列表</h2>
            <p class="card-sub">展示当前池内前 {{ poolRows.length }} 条工单，优先高亮超时与待分配工单</p>
          </div>
          <button
            type="button"
            class="link-btn"
            @click="$emit('open-board', '')"
          >
            查看完整总表 →
          </button>
        </header>

        <p v-if="!poolRows.length" class="empty-state">
          当前工单池暂无可展示工单，可前往完整总表查看历史记录。
        </p>

        <template v-else>
          <div class="table-wrap">
            <table class="pool-table">
              <thead>
                <tr>
                  <th>单号</th>
                  <th>来源渠道</th>
                  <th>标题</th>
                  <th>SLA 剩余</th>
                  <th>状态</th>
                  <th>处理人</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in poolRows"
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
                      <span v-if="row.kind" class="kind-tag" :class="row.item.kind">{{ row.kind }}</span>
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
                          v-if="row.item.state !== 'resolved'"
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
              v-for="row in poolRows"
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
      </section>

      <section class="dash-card agent-card" aria-label="坐席在岗状态">
        <header class="card-header">
          <div>
            <h2>坐席在岗状态</h2>
            <p class="card-sub">
              {{ hasRealAssignees ? '名单取自系统账号，在岗状态与时长为模拟' : '未加载账号列表，展示模拟坐席' }}
            </p>
          </div>
          <span class="mock-badge">模拟</span>
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
                <span class="agent-duration">在岗时长（模拟）：{{ agent.duration }}</span>
              </div>
            </div>
            <span class="presence-pill" :class="agent.status">
              <i></i>{{ agent.statusLabel }}
            </span>
          </li>
        </ul>

        <div class="agent-footer">
          <button
            type="button"
            class="assign-btn"
            :disabled="busy"
            @click="simulateEvenAssign"
          >
            一键强制平均分配（模拟）
          </button>
          <p v-if="assignNotice" role="status" class="assign-notice">
            {{ assignNotice }}
          </p>
        </div>
      </section>
    </div>

    <div class="bottom-grid">
      <section class="dash-card" aria-label="渠道分布">
        <header class="card-header">
          <div>
            <h2>渠道分布</h2>
            <p class="card-sub">各受理渠道工单占比</p>
          </div>
          <span class="mock-badge">模拟</span>
        </header>
        <ul class="channel-bars">
          <li v-for="ch in channelDistribution" :key="ch.name" class="channel-bar-row">
            <div class="channel-bar-meta">
              <span class="channel-name">{{ ch.name }}</span>
              <strong class="channel-pct">{{ ch.percent }}%</strong>
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

      <section class="dash-card" aria-label="本月排班表">
        <header class="card-header">
          <div>
            <h2>本月排班表</h2>
            <p class="card-sub">早班 / 晚班坐席轮值安排</p>
          </div>
          <span class="mock-badge">模拟</span>
        </header>

        <div class="schedule-wrap">
          <table class="schedule-table">
            <thead>
              <tr>
                <th>班次</th>
                <th v-for="day in weekDays" :key="day">{{ day }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="shift in shiftRows" :key="shift.name">
                <th scope="row" class="shift-col">
                  <strong>{{ shift.name }}</strong>
                  <span>{{ shift.hours }}</span>
                </th>
                <td v-for="(member, idx) in shift.members" :key="idx">
                  <span class="shift-chip" :class="shift.tone">{{ member }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  avatarTone,
  channelLabel,
  displayStatus,
  initial,
  isUnowned,
  kindLabel,
  orderNumber,
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

defineEmits(['open', 'open-board', 'claim'])

const assignNotice = ref('')
const trendRange = ref('7d')

const hasSummaryTotal = computed(() => props.summary !== null && props.summary.total !== undefined && props.summary.total !== null)
const todayTotalDisplay = computed(() => (hasSummaryTotal.value ? Number(props.summary.total) : 32))

const hasUnclaimedSummary = computed(() => Boolean(props.summary && props.summary.states && props.summary.states.unclaimed !== undefined))
const unclaimedCountDisplay = computed(() => {
  if (hasUnclaimedSummary.value) return stateCount(props.summary, 'unclaimed')
  if (props.items.length) return props.items.filter(item => isUnowned(item) && item.state !== 'resolved').length
  return 6
})

const hasSlaData = computed(() => props.items.some(item => item && item.slaDueAt))
const overdueCountDisplay = computed(() => {
  if (hasSlaData.value) {
    return props.items.filter(item => slaPresentation(item.slaDueAt, item.state)?.overdue).length
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
  if (item.state === 'resolved') return { label: '已结案', tone: 'closed', overdue: false }
  if (!item.slaDueAt) return { label: '未设置', tone: 'none', overdue: false }

  const dueEpoch = parseShanghaiEpoch(item.slaDueAt)
  const nowEpoch = parseShanghaiEpoch(shanghaiStamp())
  if (dueEpoch === null || nowEpoch === null) {
    const fallback = slaPresentation(item.slaDueAt, item.state)
    return fallback ? { label: fallback.text, tone: fallback.overdue ? 'overdue' : 'normal', overdue: fallback.overdue } : { label: '—', tone: 'none', overdue: false }
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

const poolRows = computed(() => {
  return props.items.slice(0, 8).map(item => {
    const unowned = isUnowned(item) && item.state !== 'resolved'
    const sla = formatSlaRemaining(item)
    return {
      item,
      number: orderNumber(item),
      channel: channelLabel(item.channel) || item.channel || '未标注',
      kind: kindLabel(item.kind),
      status: displayStatus(item),
      unowned,
      sla,
      compromise: Number(item.compromiseFlag || 0) === 1 || item.compromiseFlag === true,
      initial: initial(item.owner),
      avatarTone: avatarTone(item.owner)
    }
  })
})

function rowClass(row) {
  if (row.sla.overdue) return 'row-overdue'
  if (row.unowned) return 'row-unassigned'
  return ''
}

const MOCK_AGENTS = [
  { username: '张三', roleLabel: '一线坐席', status: 'ready', statusLabel: '就绪', duration: '04h 18m' },
  { username: '李四', roleLabel: '一线坐席', status: 'ready', statusLabel: '就绪', duration: '03h 52m' },
  { username: '赵六', roleLabel: '二线专家', status: 'break', statusLabel: '休息', duration: '02h 40m' },
  { username: '陈七', roleLabel: '值班组长', status: 'away', statusLabel: '离岗', duration: '01h 15m' }
]

const STATUS_CYCLE = [
  { status: 'ready', statusLabel: '就绪', duration: '04h 12m' },
  { status: 'ready', statusLabel: '就绪', duration: '03h 36m' },
  { status: 'break', statusLabel: '休息', duration: '02h 25m' },
  { status: 'away', statusLabel: '离岗', duration: '01h 08m' }
]

const hasRealAssignees = computed(() => props.assignees && props.assignees.length > 0)

const agentRows = computed(() => {
  if (!hasRealAssignees.value) {
    return MOCK_AGENTS.map(item => ({
      ...item,
      initial: initial(item.username),
      tone: avatarTone(item.username)
    }))
  }
  return props.assignees.map((item, idx) => {
    const preset = STATUS_CYCLE[idx % STATUS_CYCLE.length]
    return {
      username: item.username,
      roleLabel: item.role === 'admin' ? '管理员' : '坐席',
      status: preset.status,
      statusLabel: preset.statusLabel,
      duration: preset.duration,
      initial: initial(item.username),
      tone: avatarTone(item.username)
    }
  })
})

function simulateEvenAssign() {
  assignNotice.value = '已按在岗就绪坐席模拟均分待分配工单（演示）'
}

const channelDistribution = [
  { name: '400热线', percent: 40, color: '#0f766e' },
  { name: '服务商', percent: 22, color: '#0d9488' },
  { name: '小程序', percent: 20, color: '#2dd4bf' },
  { name: '企微群', percent: 18, color: '#64748b' }
]

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

const trendRangeLabel = computed(() => trendRanges.find(r => r.id === trendRange.value)?.label || '近7天')

const activeSeries = computed(() => TREND_DATA[trendRange.value] || TREND_DATA['7d'])

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

const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const shiftRows = computed(() => {
  const names = hasRealAssignees.value && props.assignees.length >= 2
    ? props.assignees.map(a => a.username)
    : ['张三', '李四', '赵六', '陈七']
  const pick = idx => names[idx % names.length]
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
</script>

<style scoped>
.dispatch-dashboard {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 8px #132c4906;
  text-align: left;
  transition: border-color .15s ease, box-shadow .15s ease, transform .15s ease;
}

.kpi-interactive {
  cursor: pointer;
}

.kpi-interactive:hover {
  border-color: var(--pine-bright);
  box-shadow: 0 4px 14px #0f766e14;
  transform: translateY(-1px);
}

.kpi-card.warn {
  border-color: #f3dfb6;
  background: linear-gradient(180deg, #fffdf7 0%, #fff 100%);
}

.kpi-card.danger {
  border-color: #f3c6c2;
  background: linear-gradient(180deg, #fff8f7 0%, #fff 100%);
}

.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.kpi-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.kpi-value {
  color: var(--heading);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.kpi-value.warn { color: var(--warn); }
.kpi-value.danger { color: var(--danger); }

.kpi-sub {
  margin: 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.4;
}

.kpi-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--pine);
  font-weight: 600;
}

.mock-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.mock-badge.warn {
  border-color: #f3e2b8;
  background: #fff8e8;
  color: var(--warn);
}

.mock-badge.danger {
  border-color: #f5c2be;
  background: #fdecec;
  color: var(--danger);
}

.mid-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(290px, 0.95fr);
  gap: 14px;
  align-items: stretch;
}

.bottom-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  align-items: stretch;
}

.dash-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 10px #132c4906;
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
  color: var(--heading);
  font-size: 15px;
  font-weight: 700;
}

.card-sub {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 11px;
}

.link-btn {
  flex: none;
  padding: 4px 10px;
  border: 1px solid #d5ebe5;
  border-radius: 6px;
  background: var(--pine-soft);
  color: var(--pine);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.link-btn:hover {
  border-color: var(--pine-bright);
  background: #dbf0ec;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--line);
  border-radius: 9px;
}

.pool-table {
  width: 100%;
  min-width: 640px;
  border-collapse: separate;
  border-spacing: 0;
  text-align: left;
}

.pool-table th {
  padding: 9px 10px;
  border-bottom: 1px solid var(--line);
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.pool-table td {
  padding: 9px 10px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
  font-size: 12px;
}

.pool-table tbody tr:last-child td {
  border-bottom: 0;
}

.pool-table tr.row-unassigned td {
  background: #fffbf2;
}

.pool-table tr.row-unassigned td:first-child {
  box-shadow: inset 3px 0 0 #eab65f;
}

.pool-table tr.row-overdue td {
  background: #fdf4f3;
}

.pool-table tr.row-overdue td:first-child {
  box-shadow: inset 3px 0 0 var(--danger);
}

.order-btn {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--pine);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.order-btn:hover {
  text-decoration: underline;
}

.channel-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  border-radius: 5px;
  background: var(--pine-soft);
  color: var(--pine);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 220px;
}

.title-btn {
  min-width: 0;
  overflow: hidden;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  font-weight: 500;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title-btn:hover {
  color: var(--pine);
}

.kind-tag, .compromise-tag {
  flex: none;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 10px;
  line-height: 1.4;
}

.kind-tag.fault { background: #fbefed; color: #ba7d73; }
.kind-tag.consult { background: #edf5fc; color: #6d9ac4; }
.kind-tag.service { background: #eaf6f1; color: #5a9f8c; }
.compromise-tag { background: #fdecec; color: var(--danger); font-weight: 600; }

.sla-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.sla-text i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.sla-text.overdue { color: var(--danger); font-weight: 600; }
.sla-text.soon { color: var(--warn); font-weight: 600; }
.sla-text.normal { color: var(--pine); }

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 7px;
  border-radius: 5px;
  font-size: 11px;
  white-space: nowrap;
}

.status-pill i { width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
.status-pill.processing { background: #e6f7f5; color: #0f766e; }
.status-pill.waiting { background: #fef3c7; color: #b45309; }
.status-pill.dispatch { background: #eef2ff; color: #4338ca; }
.status-pill.onsite { background: #e0f2fe; color: #0369a1; }
.status-pill.verify { background: #f3e8ff; color: #7e22ce; }
.status-pill.closed { background: #f1f5f9; color: #475569; }
.status-pill.triage { background: #fce7f3; color: #9d174d; }
.status-pill.follow { background: #ecfccb; color: #3f6212; }
.status-pill.answered { background: #f5f5f4; color: #57534e; }
.status-pill.referred { background: #f4f4f5; color: #3f3f46; }

.owner-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.avatar-dot, .agent-avatar {
  display: inline-grid;
  place-items: center;
  flex: none;
  border-radius: 50%;
  font-weight: 600;
}

.avatar-dot { width: 20px; height: 20px; font-size: 10px; }
.agent-avatar { width: 32px; height: 32px; font-size: 13px; }

.avatar-dot.teal, .agent-avatar.teal { background: #e2f2ef; color: #0f766e; }
.avatar-dot.blue, .agent-avatar.blue { background: #e9f1fb; color: #4a76a8; }
.avatar-dot.purple, .agent-avatar.purple { background: #f0ecfa; color: #7e63b0; }
.avatar-dot.pink, .agent-avatar.pink { background: #f8eaf0; color: #a96787; }

.owner-name {
  color: var(--ink);
  font-size: 12px;
}

.unowned-label {
  color: var(--warn);
  font-size: 12px;
  font-weight: 600;
}

.claim-mini {
  padding: 2px 7px;
  border: 1px solid #a4d2c9;
  border-radius: 5px;
  background: #fffefa;
  color: var(--pine);
  font-size: 11px;
  font-weight: 600;
}

.claim-mini:hover:not(:disabled) {
  background: var(--pine-soft);
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

.agent-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.agent-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fbfcfd;
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
  color: var(--heading);
  font-size: 13px;
}

.role-tag {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--pine-soft);
  color: var(--pine);
  font-size: 10px;
  font-weight: 600;
}

.agent-duration {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: 11px;
}

.presence-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.presence-pill i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.presence-pill.ready {
  background: var(--pine-soft);
  color: var(--pine);
}

.presence-pill.break {
  background: var(--warn-bg);
  color: var(--warn);
}

.presence-pill.away {
  background: #f1f5f9;
  color: #64748b;
}

.agent-footer {
  margin-top: auto;
  padding-top: 12px;
}

.assign-btn {
  width: 100%;
  min-height: 38px;
  padding: 8px 12px;
  border: 1px solid var(--pine);
  border-radius: 8px;
  background: var(--pine);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  transition: background .15s ease;
}

.assign-btn:hover:not(:disabled) {
  background: var(--pine-bright);
  border-color: var(--pine-bright);
}

.assign-btn:disabled {
  opacity: .6;
  cursor: not-allowed;
}

.assign-notice {
  margin: 8px 0 0;
  padding: 8px 10px;
  border: 1px solid #bce2dc;
  border-radius: 8px;
  background: var(--pine-soft);
  color: var(--pine);
  font-size: 12px;
  line-height: 1.5;
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
  font-size: 12px;
}

.channel-name {
  color: var(--ink);
  font-weight: 500;
}

.channel-pct {
  color: var(--heading);
  font-weight: 700;
}

.bar-track {
  height: 9px;
  border-radius: 999px;
  background: var(--canvas);
  border: 1px solid var(--line);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
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
  background: #f1f5f9;
}

.range-switch button {
  padding: 3px 8px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #64748b;
  font-size: 11px;
  font-weight: 500;
}

.range-switch button[aria-pressed="true"] {
  background: #fff;
  color: var(--pine);
  font-weight: 700;
  box-shadow: 0 1px 3px #0f172a12;
}

.trend-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 11px;
  color: #64748b;
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

.dot-pine { background: var(--pine); }
.dot-danger { background: var(--danger); }

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
  stroke: var(--line);
  stroke-width: 1;
}

.grid-lines text,
.x-label {
  fill: #8a96a5;
  font-size: 10px;
}

.schedule-wrap {
  overflow-x: auto;
  margin-top: 4px;
  border: 1px solid var(--line);
  border-radius: 9px;
}

.schedule-table {
  width: 100%;
  min-width: 320px;
  border-collapse: collapse;
  text-align: center;
}

.schedule-table th,
.schedule-table td {
  padding: 8px 4px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 11px;
}

.schedule-table thead th {
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
}

.schedule-table tbody tr:last-child th,
.schedule-table tbody tr:last-child td {
  border-bottom: 0;
}

.shift-col {
  text-align: left;
  padding-left: 8px;
  white-space: nowrap;
}

.shift-col strong {
  display: block;
  color: var(--heading);
  font-size: 12px;
}

.shift-col span {
  color: var(--muted);
  font-size: 10px;
  font-weight: 400;
}

.shift-chip {
  display: inline-block;
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 11px;
  white-space: nowrap;
}

.shift-chip.early {
  background: var(--pine-soft);
  color: var(--pine);
}

.shift-chip.late {
  background: #f1f5f9;
  color: var(--ink);
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .mid-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
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
    font-size: 11px;
  }
}
</style>
