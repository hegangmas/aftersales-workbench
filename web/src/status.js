const CORE_BUCKETS = ['', 'unclaimed', 'open', 'fieldPending', 'fieldActive', 'verify', 'resolved']
const EXTRA_BUCKETS = ['triage', 'waiting', 'answered', 'referred']

export const BUCKET_LABELS = {
  '': '全部',
  unclaimed: '待认领',
  open: '处理中',
  triage: '待排查',
  waiting: '待回访',
  fieldPending: '待派单',
  fieldActive: '现场处理中',
  verify: '待验证',
  resolved: '已结案',
  answered: '已答复',
  referred: '已转出'
}

export function visibleBuckets(summary, current) {
  const extra = EXTRA_BUCKETS.filter(id => stateCount(summary, id) > 0 || current === id)
  return CORE_BUCKETS.concat(extra)
}

export function stateCount(summary, key) {
  if (!summary) return 0
  if (key === '' || key === 'total') return Number(summary.total || 0)
  return Number(summary.states && summary.states[key] || 0)
}

export function isUnowned(item) {
  return !item || !item.owner || item.owner === 'early'
}

export function displayStatus(item) {
  if (!item) return { id: '', label: '', tone: 'closed' }
  if (isUnowned(item) && item.state !== 'resolved') {
    return { id: 'unclaimed', label: '待认领', tone: 'waiting' }
  }
  const active = Number(item.activeFieldOrders || 0) > 0
    || (item.fieldOrders || []).some(order => order && (order.state === 'linked' || order.state === 'in_progress'))
  if (item.state === 'field' && !active) return { id: 'fieldPending', label: '待派单', tone: 'dispatch' }
  if (item.state === 'field' && active) return { id: 'fieldActive', label: '现场处理中', tone: 'onsite' }
  const map = {
    open: ['open', '处理中', 'processing'],
    triage: ['triage', '待排查', 'triage'],
    waiting: ['waiting', '待回访', 'follow'],
    verify: ['verify', '待验证', 'verify'],
    resolved: ['resolved', '已结案', 'closed'],
    answered: ['answered', '已答复', 'answered'],
    referred: ['referred', '已转出', 'referred']
  }
  const found = map[item.state] || [item.state, item.state || '未知', 'closed']
  return { id: found[0], label: found[1], tone: found[2] }
}

export function rowAction(item) {
  const status = displayStatus(item)
  if (status.id === 'unclaimed') return { id: 'claim', label: '认领', claim: true }
  if (status.id === 'fieldPending') return { id: 'field', label: '关联现场单' }
  if (status.id === 'fieldActive') return { id: 'receipt', label: '记录回执' }
  if (status.id === 'verify') return { id: 'callback', label: '回访确认' }
  if (status.id === 'resolved' || status.id === 'answered' || status.id === 'referred') return { id: 'view', label: '查看' }
  return { id: 'note', label: '跟进' }
}

export function kindLabel(kind) {
  return { fault: '故障', consult: '咨询', service: '服务' }[kind] || ''
}

export const CHANNEL_LABELS = {
  phone: '电话',
  '400': '400电话',
  miniprogram: '客户小程序',
  provider: '服务商扫码',
  wecom: '企微群'
}

export const SUSPEND_LABELS = {
  customer_reboot: '待客户重启',
  customer_evidence: '待传证据',
  parts_wait: '待备件',
  field_wait: '待乐装回传'
}

export const DISPOSAL_LABELS = {
  remote: '远程处置',
  part: '换件',
  replace: '换装',
  onsite: '上门排查'
}

export const EVIDENCE_TYPE_LABELS = {
  photo: '现场照片',
  video: '短视频/录屏',
  timestamp_log: '故障时点/日志',
  site_compare: '同站对照',
  swap_test: '替换测试',
  platform_data: '平台报文/充电记录'
}

export const ESCALATION_LABELS = {
  none: '未升级',
  l2: '已升级二线',
  expert: '已升级专家'
}

export function channelLabel(channel) {
  return CHANNEL_LABELS[channel] || ''
}

export function suspendLabel(reason) {
  return SUSPEND_LABELS[reason] || ''
}

export const suspendReasonLabel = suspendLabel

export function disposalLabel(type) {
  return DISPOSAL_LABELS[type] || ''
}

export function evidenceTypeLabel(type) {
  return EVIDENCE_TYPE_LABELS[type] || ''
}

export function escalationLabel(level) {
  return ESCALATION_LABELS[level] || ''
}

export function slaPresentation(slaDueAt, state) {
  if (state === 'resolved' || !slaDueAt) return null
  const stamp = String(slaDueAt).replace('T', ' ').replace(/\.\d+/, '').replace(/Z|[+-]\d\d:\d\d$/, '').slice(0, 16)
  if (stamp <= shanghaiStamp()) return { text: 'SLA 已超时', overdue: true }
  return { text: 'SLA ' + stamp.slice(5, 16), overdue: false }
}

export function avatarTone(name) {
  const tones = ['teal', 'blue', 'purple', 'pink']
  const text = String(name || '')
  let total = 0
  for (let i = 0; i < text.length; i += 1) total += text.charCodeAt(i)
  return tones[total % tones.length]
}

export function initial(name) {
  const text = String(name || '').trim()
  if (!text) return '·'
  return text.slice(0, 1).toUpperCase()
}

export function shanghaiStamp(date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(date)
  const read = type => parts.find(part => part.type === type).value
  return read('year') + '-' + read('month') + '-' + read('day') + ' ' + read('hour') + ':' + read('minute')
}

export function duePresentation(value) {
  if (!value) return { text: '未安排', tone: 'none' }
  const stamp = String(value).replace('T', ' ').replace(/\.\d+/, '').replace(/Z|[+-]\d\d:\d\d$/, '').slice(0, 16)
  const now = shanghaiStamp()
  const overdue = stamp <= now
  const sameDay = stamp.slice(0, 10) === now.slice(0, 10)
  const time = stamp.slice(11, 16)
  const month = stamp.slice(5, 7)
  const day = stamp.slice(8, 10)
  if (overdue) return { text: month + '-' + day + ' ' + time + ' · 已到期', tone: 'overdue' }
  if (sameDay) return { text: '今天 ' + time, tone: 'today' }
  return { text: month + '-' + day + ' ' + time, tone: 'later' }
}

export function updatedPresentation(value) {
  const stamp = String(value || '').replace('T', ' ').replace(/\.\d+/, '').slice(0, 16)
  if (stamp.length < 16) return { text: stamp, year: '' }
  return { text: stamp.slice(5, 16), year: stamp.slice(0, 4) + ' 年' }
}

export function orderNumber(item) {
  if (!item) return ''
  return item.displayNumber ? 'AS-' + item.displayNumber : item.id
}

export function summaryScope(summary) {
  if (!summary || !summary.states) return ''
  const order = ['open', 'triage', 'waiting', 'fieldPending', 'fieldActive', 'verify', 'resolved', 'answered', 'referred', 'unclaimed']
  return order
    .filter(key => Number(summary.states[key] || 0) > 0)
    .map(key => BUCKET_LABELS[key] + ' ' + summary.states[key])
    .join(' · ')
}
