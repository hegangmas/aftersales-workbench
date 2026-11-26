<template>
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
            <div class="bar-fill" :style="{ width: ch.percent + '%', background: ch.color }"></div>
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
        <svg viewBox="0 0 360 156" class="trend-svg" role="img" :aria-label="trendRangeLabel + '升级与超时工单趋势折线图'">
          <g class="grid-lines">
            <line v-for="tick in chartTicks" :key="tick.value" x1="28" :y1="tick.y" x2="346" :y2="tick.y" />
            <text v-for="tick in chartTicks" :key="'t-' + tick.value" x="22" :y="tick.y + 3" text-anchor="end">
              {{ tick.value }}
            </text>
          </g>

          <polyline fill="none" stroke="#0f766e" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" :points="escalatedPoints" />
          <polyline fill="none" stroke="#c0362c" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="4 3" :points="overduePoints" />

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
          <select v-model="selectedMonth" class="month-select" aria-label="选择排班月份">
            <option v-for="m in monthOptions" :key="m.value" :value="m.value">{{ m.label }}</option>
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

      <footer class="schedule-summary">{{ scheduleSummaryText }}</footer>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { channelLabel, shanghaiStamp } from '../../status'

const props = defineProps({
  items: { type: Array, default: () => [] },
  assignees: { type: Array, default: () => [] },
  nowMs: { type: Number, required: true }
})

const channelRange = ref('month')
const trendRange = ref('30d')

function parseShanghaiEpoch(stamp) {
  const match = String(stamp || '').match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/)
  if (!match) return null
  return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5]))
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
  const nowStamp = shanghaiStamp(new Date(props.nowMs))
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
  if (!matched.length) return CHANNEL_MOCK[channelRange.value] || CHANNEL_MOCK.month
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
  return [-1, 0, 1, 2].map(offset => {
    const d = new Date(Date.UTC(year, month - 1 + offset, 1))
    const y = d.getUTCFullYear()
    const m = d.getUTCMonth() + 1
    return { value: `${y}-${String(m).padStart(2, '0')}`, label: `${y}年${m}月` }
  })
}

const monthOptions = buildMonthOptions()
const selectedMonth = ref(shanghaiStamp().slice(0, 7))
const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const todayWeekdayIndex = computed(() => {
  const day = new Date(props.nowMs + 8 * 3600 * 1000).getUTCDay()
  return day === 0 ? 6 : day - 1
})

const shiftRows = computed(() => {
  const names = Array.isArray(props.assignees) && props.assignees.length >= 2
    ? props.assignees.map(a => a.username)
    : ['张三', '李四', '赵六', '陈七']
  const monthIdx = Math.max(0, monthOptions.findIndex(m => m.value === selectedMonth.value))
  const pick = idx => names[(idx + monthIdx) % names.length]
  return [
    { name: '早班', hours: '09:00-18:00', tone: 'early', members: [pick(0), pick(1), pick(0), pick(2), pick(1), pick(0), pick(3)] },
    { name: '晚班', hours: '14:00-23:00', tone: 'late', members: [pick(2), pick(3), pick(1), pick(3), pick(2), pick(1), pick(0)] }
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
.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.72fr) minmax(0, 0.98fr) minmax(0, 1.38fr);
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

.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 12px; }
.trend-header { align-items: center; flex-wrap: wrap; }
.card-header h2 { margin: 0; color: var(--text-primary, #152020); font-size: 15px; font-weight: 600; }
.card-sub { margin: 2px 0 0; color: var(--text-tertiary, #7a8686); font-size: 12px; }

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

.channel-header-right, .schedule-controls { display: flex; align-items: center; gap: 6px; }
.month-select {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--border-strong, #cfd7d7);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-surface, #ffffff);
  color: var(--text-primary, #152020);
  font-size: 13px;
  outline: none;
}

.channel-bars { list-style: none; margin: 4px 0 0; padding: 0; display: grid; gap: 14px; }
.channel-bar-meta { display: flex; align-items: center; justify-content: space-between; margin-bottom: 5px; font-size: 13px; }
.channel-name { color: var(--text-primary, #152020); font-weight: 500; }
.channel-values { display: inline-flex; align-items: center; gap: 8px; }
.channel-count { color: var(--text-tertiary, #7a8686); font-size: 12px; font-variant-numeric: tabular-nums; }
.channel-pct { color: var(--text-primary, #152020); font-weight: 600; font-variant-numeric: tabular-nums; }
.bar-track { height: 8px; border-radius: 4px; background: var(--bg-muted, #f1f4f4); overflow: hidden; }
.bar-fill { height: 100%; border-radius: 4px; transition: width .25s ease; }

.range-switch { display: inline-flex; padding: 2px; border-radius: 7px; background: var(--bg-muted, #f1f4f4); }
.range-switch button {
  height: 24px; padding: 0 10px; border: 0; border-radius: 5px; background: transparent;
  color: var(--text-secondary, #4a5656); font-size: 12px; font-weight: 500; white-space: nowrap;
}
.range-switch button[aria-pressed="true"] {
  background: var(--bg-surface, #ffffff); color: var(--brand-700, #0f766e); font-weight: 600; box-shadow: var(--elev-seg);
}

.trend-legend { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; font-size: 12px; color: var(--text-secondary, #4a5656); }
.legend-item { display: inline-flex; align-items: center; gap: 5px; }
.dot-pine, .dot-danger { width: 8px; height: 8px; border-radius: 50%; }
.dot-pine { background: var(--chart-1, #0d9488); }
.dot-danger { background: var(--chart-2, #c0362c); }

.svg-wrap { width: 100%; margin-top: auto; }
.trend-svg { width: 100%; height: auto; overflow: visible; }
.grid-lines line { stroke: var(--chart-grid, #eef1f1); stroke-width: 1; stroke-dasharray: 4 4; }
.grid-lines text, .x-label { fill: var(--chart-axis, #7a8686); font-size: 12px; font-variant-numeric: tabular-nums; }

.schedule-wrap { overflow-x: auto; margin-top: 4px; border-top: 1px solid var(--border-subtle, #edf0f0); }
.schedule-table { width: 100%; min-width: 540px; table-layout: auto; border-collapse: collapse; text-align: center; }
.schedule-table .col-shift { width: 86px; }
.schedule-table th, .schedule-table td { padding: 8px 3px; border-bottom: 1px solid var(--border-subtle, #edf0f0); font-size: 12px; }
.schedule-table th.is-today, .schedule-table td.is-today { background: var(--brand-50, #e8f6f4); }
.schedule-table thead th { background: var(--bg-subtle, #f8fafa); color: var(--text-tertiary, #7a8686); font-size: 12px; font-weight: 500; white-space: nowrap; }
.schedule-table tbody tr:last-child th, .schedule-table tbody tr:last-child td { border-bottom: 0; }
.schedule-table tbody th.shift-col { text-align: left; padding: 8px 6px 8px 2px; white-space: nowrap; }
.shift-col strong { display: block; color: var(--text-primary, #152020); font-size: 13px; font-weight: 600; }
.shift-col span { color: var(--text-tertiary, #7a8686); font-size: 12px; font-weight: 400; font-variant-numeric: tabular-nums; }

.shift-chip {
  display: inline-block; max-width: none; padding: 2px 6px; border-radius: 4px;
  font-size: 12px; line-height: 1.4; white-space: nowrap; vertical-align: middle;
}
.shift-chip.early { background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); font-weight: 500; }
.shift-chip.late { background: var(--neutral-bg, #f1f4f4); color: var(--text-secondary, #4a5656); }

.schedule-summary { margin-top: auto; padding-top: 10px; color: var(--text-tertiary, #7a8686); font-size: 12px; line-height: 1.5; }

@media (max-width: 1100px) {
  .bottom-grid { grid-template-columns: 1fr; }
}
</style>
