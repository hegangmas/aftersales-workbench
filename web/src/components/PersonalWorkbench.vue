<template>
  <FieldDispatchPanel
    v-if="dispatchMode"
    :problem="problem"
    :device="device"
    :busy="busy"
    :can-process="canProcess"
    @link="$emit('link', $event)"
    @field-result="$emit('field-result', $event)"
    @back="dispatchMode = false"
  />
  <article v-else class="personal" :class="{ 'mobile-compact': mobileCompact }">
    <div v-if="device && device.simulatedCall" class="call-strip">
      <span><strong>400 来电弹屏（模拟）</strong> · {{ device.simulatedCall.caller }}</span>
      <span>通话 {{ device.simulatedCall.duration }} · {{ device.simulatedCall.recordingStatus }}</span>
    </div>

    <header class="personal-head">
      <div>
        <button class="link" type="button" @click="$emit('back')">← 返回列表</button>
        <h1>售后个人工作台</h1>
        <p class="muted">集中查看设备档案、SOP 证据判定，并完成跟进、挂起回池、快通道、派单与转交</p>
      </div>
      <div class="personal-head-actions">
        <div class="mobile-density" role="group" aria-label="移动端信息密度">
          <button type="button" :aria-pressed="mobileCompact" @click="mobileCompact = true">主要信息</button>
          <button type="button" :aria-pressed="!mobileCompact" @click="mobileCompact = false">全部信息</button>
        </div>
        <button class="btn secondary" type="button" @click="$emit('intake')">查桩与建单</button>
        <button class="btn secondary" type="button" @click="$emit('mine')">我的待办</button>
      </div>
    </header>

    <div class="case-bar">
      <strong>{{ number }} · {{ problem.title }}</strong>
      <span class="status" :class="status.tone"><i></i>{{ status.label }}</span>
      <span v-if="problem.channel" class="mini-tag info">{{ channelLabel(problem.channel) }}</span>
      <span v-if="problem.grade" class="mini-tag" :class="problem.grade === 'C' ? 'warn' : 'info'">
        {{ problem.grade }}级<template v-if="problem.disposalType"> · {{ disposalLabel(problem.disposalType) }}</template>
      </span>
      <span v-if="problem.suspendReason && problem.state === 'waiting'" class="mini-tag warn">{{ suspendLabel(problem.suspendReason) }}</span>
      <span v-if="problem.escalationLevel && problem.escalationLevel !== 'none'" class="mini-tag danger">{{ escalationLabel(problem.escalationLevel) }}</span>
      <span v-if="isCompromise" class="mini-tag danger">妥协工单</span>
      <span v-if="sla" class="mini-tag" :class="sla.overdue ? 'danger' : 'muted'">{{ sla.text }}</span>
      <span>负责人：{{ ownerText }}</span>
      <span>下一步：{{ nextAction }}</span>
      <span v-if="problem.pendingFollowup" class="pending">{{ problem.pendingFollowup.overdue ? '回访已到期' : '待回访' }}</span>
    </div>
    <p v-if="refreshNote" role="status" class="stage-note">{{ refreshNote }}</p>

    <div class="personal-grid">
      <section class="panel">
        <h2>客户与设备档案</h2>
        <p class="muted">客户、MES、质保与 400 为模拟数据（外部系统未接入）</p>
        <dl class="personal-facts">
          <dt>桩编码</dt><dd>{{ problem.device || '未关联' }}</dd>
          <dt>问题分类</dt><dd>{{ kindText }}<span v-if="problem.category"> · {{ problem.category }}</span></dd>
          <dt>客户与地址</dt><dd>{{ device?.customerName || '李先生（模拟）' }} · {{ device?.siteAddress || '合肥市滨湖新区（模拟）' }}</dd>
          <dt>设备型号</dt><dd>{{ device?.model || '7kW 交流桩（模拟）' }}</dd>
          <dt>在线 / 状态</dt><dd>{{ device?.onlineStatus || '在线（模拟）' }} · {{ device?.currentStatus || '告警中（模拟）' }}</dd>
          <dt class="detail-extra">出厂 / 质保</dt>
          <dd class="detail-extra">{{ device?.manufacturedAt || '2025-06-18（MES 模拟）' }} · {{ device?.warrantyStatus || '在保（模拟）' }}</dd>
          <dt class="detail-extra">程序 / SIM</dt>
          <dd class="detail-extra">{{ device?.firmwareVersion || 'v2.4.18（模拟）' }} · {{ device?.simExpiresAt || '2027-05-31（模拟）' }}</dd>
          <dt>设备数据</dt>
          <dd>
            <template v-if="deviceLoading">查询中…</template>
            <template v-else-if="deviceError">
              <span>{{ deviceError }}</span>
              <button v-if="deviceRetryable" class="link retry-link" type="button" @click="loadRelated">重试</button>
            </template>
            <template v-else>{{ (device && device.source) || '暂无' }}</template>
          </dd>
        </dl>
        <div v-if="device" class="personal-box">
          <strong>当前故障摘要</strong>
          <p>{{ device.fault || '暂无故障摘要' }}</p>
          <small>{{ device.observedAt || '时间未知' }}</small>
        </div>

        <h3>历史问题单</h3>
        <p v-if="historyLoading" class="muted">查询中…</p>
        <p v-else-if="historyError" class="muted">
          {{ historyError }}
          <button v-if="historyRetryable" class="link retry-link" type="button" @click="loadRelated">重试</button>
        </p>
        <p v-else-if="!history.length" class="muted">暂无其他问题单</p>
        <ul v-else class="personal-history">
          <li v-for="item in history" :key="item.id">
            <button class="link" type="button" @click="$emit('open', item.id)">{{ orderNumber(item) }} · {{ item.title }}</button>
            <span>{{ displayStatus(item).label }}</span>
          </li>
        </ul>

        <div class="detail-extra">
          <h3>维修记录（模拟）</h3>
          <ul v-if="repairHistory.length" class="record-list">
            <li v-for="(rep, idx) in repairHistory" :key="idx">
              <time>{{ rep.date }} · {{ rep.source }}</time>
              <div>{{ rep.summary }}</div>
            </li>
          </ul>
          <p v-else class="muted">暂无历史维修记录</p>

          <h3>通话录音（模拟 · {{ callRecordings.length }} 条）</h3>
          <ul v-if="callRecordings.length" class="recording-list">
            <li v-for="rec in callRecordings" :key="rec.id">
              <div>
                <strong>{{ rec.title }}</strong>
                <small>{{ rec.time }} · 时长 {{ rec.duration }}</small>
              </div>
              <button class="btn secondary btn-xs" type="button" @click="playingId = playingId === rec.id ? '' : rec.id">
                {{ playingId === rec.id ? '停止' : '试听(模拟)' }}
              </button>
            </li>
          </ul>
          <p v-if="playingId" class="stage-note">正在模拟播放录音 {{ playingId }}（400 呼叫中心接入后将直连真实录音流）。</p>
        </div>

        <div class="platform-jump">
          <button class="btn secondary" type="button" @click="jumpNotice = !jumpNotice">⇄ 跳转设备平台</button>
          <p v-if="jumpNotice" class="muted">充电桩管理平台直链待 REQ-002 联调确认后开通（当前桩号：{{ problem.device || '未关联' }}）。</p>
        </div>
      </section>

      <section class="panel">
        <h2>当前问题、SOP 与证据</h2>
        <div class="progress-track" aria-label="工单处理进度">
          <div v-for="(step, idx) in progressSteps" :key="step.label" class="progress-step" :class="{ done: idx <= currentStepIndex, current: idx === currentStepIndex }">
            <span class="step-dot">{{ idx < currentStepIndex ? '✓' : idx + 1 }}</span>
            <span class="step-label">{{ step.label }}</span>
          </div>
        </div>
        <div class="personal-box">
          <strong>客户描述：</strong>{{ problem.description || '暂无描述' }}
          <p v-if="problem.faultOccurredAt" class="muted">故障发生时点：{{ dateLabel(problem.faultOccurredAt) }}</p>
          <p v-if="problem.escalationReason" class="muted">升级/妥协说明：{{ problem.escalationReason }}</p>
        </div>

        <div class="personal-data detail-extra">
          <div class="personal-box">
            <strong>最近充电记录（模拟）</strong>
            <template v-if="device?.chargingRecords?.length">
              <p v-for="rec in device.chargingRecords" :key="rec.orderNo">
                {{ rec.startedAt }} · {{ rec.status }} · {{ rec.energy }}（{{ rec.stopReason }}）
              </p>
            </template>
            <p v-else class="muted">暂无模拟充电记录</p>
          </div>
          <div class="personal-box">
            <strong>最近故障告警（模拟）</strong>
            <template v-if="device?.recentAlerts?.length">
              <p v-for="al in device.recentAlerts" :key="al.code + al.reportedAt">
                {{ al.reportedAt }} · {{ al.name }}（{{ al.status }}）
              </p>
            </template>
            <p v-else class="muted">暂无模拟故障告警</p>
          </div>
        </div>

        <SopEvidencePanel
          :problem="problem"
          :busy="busy"
          :can-process="canProcess"
          @add-evidence="$emit('add-evidence', $event)"
          @assess="$emit('assess', $event)"
        />

        <h3>处理时间线</h3>
        <ol v-if="problem.activities?.length" class="timeline personal-timeline">
          <li v-for="(item, index) in problem.activities" :key="index">
            <time>{{ dateLabel(item.createdAt) }}<span v-if="item.actor"> · {{ item.actor }}</span></time>
            <div>{{ item.note }}</div>
          </li>
        </ol>
        <p v-else class="muted">暂无处理记录</p>

        <div class="detail-extra">
          <template v-if="problem.callbackResults?.length">
            <h3>实际回访</h3>
            <ul class="record-list">
              <li v-for="(item, index) in problem.callbackResults" :key="index">
                <time>{{ dateLabel(item.contactedAt) }} · {{ item.actor }}</time>
                <div>{{ item.note }}</div>
                <div v-if="item.evidence" class="muted">{{ evidenceLabels[item.evidenceSource] }} · {{ item.evidence }}</div>
              </li>
            </ul>
          </template>

          <h3>已关联现场单与回执</h3>
          <p v-if="!problem.fieldOrders?.length" class="muted">尚未关联现场单</p>
          <ul v-else class="record-list">
            <li v-for="item in problem.fieldOrders" :key="item.externalNo">
              {{ item.externalNo }} · {{ fieldLabels[item.state] || item.state }}<span v-if="item.receipt"> · {{ item.receipt }}</span>
            </li>
          </ul>

          <h3>认领与转交</h3>
          <p v-if="!problem.ownershipEvents?.length" class="muted">还没有认领或转交记录</p>
          <ul v-else class="record-list">
            <li v-for="(item, index) in problem.ownershipEvents" :key="index">
              <time>{{ dateLabel(item.createdAt) }} · {{ item.actor }}</time>
              {{ item.action === 'claim' ? '认领' : '由 ' + ownerName(item.fromOwner) + ' 交给 ' + ownerName(item.toOwner) }}<span v-if="item.reason"> · {{ item.reason }}</span>
            </li>
          </ul>
        </div>
        <div v-if="canProcess" class="quick-bar">
          <button class="btn primary" type="button" @click="focusEl('action-note')">记录跟进</button>
          <button class="btn secondary" type="button" @click="focusEl('action-callback')">安排回访</button>
          <button class="btn secondary" type="button" @click="dispatchMode = true">派单至智享乐装</button>
          <button class="btn secondary" type="button" @click="focusEl('problem-actions')">全部操作</button>
        </div>
      </section>

      <aside id="problem-actions" class="panel">
        <h2>下一步操作</h2>
        <p v-if="handlingDenied" role="status" class="permission-note">{{ handlingDenied }}</p>
        <p v-if="unowned && problem.state !== 'resolved'">
          <button class="btn primary" type="button" :disabled="busy" @click="$emit('claim')">认领问题单</button>
        </p>

        <form v-if="canProcess" id="action-note" class="stack-form" @submit.prevent="$emit('save-note')">
          <label class="field-label" for="note">跟进记录</label>
          <textarea id="note" v-model="draft.note" class="text-area" maxlength="1500" placeholder="记录沟通内容、排查情况及下一步安排"></textarea>
          <button class="btn primary" type="submit" :disabled="busy || !draft.note.trim()">保存跟进</button>
        </form>

        <WorkflowExtras
          :problem="problem"
          :busy="busy"
          :can-process="canProcess"
          :field-in-progress="fieldInProgress"
          :evidence-labels="evidenceLabels"
          @suspend="$emit('suspend', $event)"
          @quick-close="$emit('quick-close', $event)"
          @escalate="$emit('escalate', $event)"
        />

        <FieldOrders
          :editable="canProcess"
          :problem="problem"
          :busy="busy"
          v-model:number="draft.fieldNumber"
          v-model:receipt="draft.fieldReceipt"
          v-model:state="draft.fieldState"
          @link="$emit('link', $event)"
          @result="$emit('field-result', $event)"
        />
        <p v-if="canProcess && problem.state === 'field'" class="stage-note">
          待现场派单后，回访先停住。尚未关联现场单时，可以填写原因撤回并返回排查；已关联且未结束的现场单必须先登记结果。
        </p>
        <form v-if="canProcess && problem.state === 'field' && !fieldInProgress" class="stack-form" @submit.prevent="$emit('withdraw')">
          <label class="field-label" for="withdraw-reason">撤回原因</label>
          <textarea id="withdraw-reason" v-model="draft.withdrawReason" class="text-area" maxlength="500"></textarea>
          <button class="btn secondary" type="submit" :disabled="busy || !draft.withdrawReason.trim()">撤回待派单，返回排查</button>
        </form>

        <section v-if="canProcess && problem.state !== 'field' && !fieldInProgress" id="action-callback" class="action-block">
          <h3>回访安排</h3>
          <p v-if="problem.state === 'verify' || problem.resumeState === 'verify'" class="stage-note">
            待验证问题可以安排回访。选择继续跟进后，问题仍回到待验证。
          </p>
          <p v-if="problem.pendingFollowup" class="stage-note">
            {{ problem.pendingFollowup.overdue ? '已到期' : '待回访' }} ·
            {{ dateLabel(problem.pendingFollowup.dueAt) }} · {{ problem.pendingFollowup.owner }} ·
            {{ problem.pendingFollowup.nextStep }}
          </p>
          <form class="stack-form" @submit.prevent="$emit('save-schedule')">
            <label class="field-label" for="owner">负责人</label>
            <input id="owner" v-model="draft.owner" class="text-input" maxlength="20">
            <label class="field-label" for="due">回访时间（北京时间）</label>
            <input id="due" v-model="draft.dueAt" class="text-input" type="datetime-local" required>
            <label class="field-label" for="next">待确认事项</label>
            <input id="next" v-model="draft.nextStep" class="text-input" maxlength="500">
            <button class="btn secondary" type="submit" :disabled="busy || !draft.owner.trim() || !draft.dueAt || !draft.nextStep.trim()">
              {{ problem.pendingFollowup ? '更新安排' : '设回访' }}
            </button>
          </form>
          <button class="btn secondary" type="button" :disabled="busy" @click="$emit('begin-callback')">现在回访</button>
          <form v-if="draft.showCallback" class="stack-form" @submit.prevent="$emit('save-outcome')">
            <label class="field-label" for="contacted-at">实际联系时间（北京时间）</label>
            <input id="contacted-at" v-model="draft.contactedAt" class="text-input" type="datetime-local" required>
            <label class="field-label" for="outcome">回访结果</label>
            <select id="outcome" v-model="draft.outcome" class="text-input">
              <option value="continue">继续跟进</option>
              <option value="field">待现场派单</option>
              <option value="resolved">确认恢复并结案</option>
            </select>
            <label class="field-label" for="result">回访说明</label>
            <textarea id="result" v-model="draft.result" class="text-area" maxlength="1000"></textarea>
            <template v-if="draft.outcome === 'resolved'">
              <label class="field-label" for="evidence-source">恢复核验来源</label>
              <select id="evidence-source" v-model="draft.evidenceSource" class="text-input" required>
                <option value="" disabled>请选择</option>
                <option v-for="(label, value) in evidenceLabels" :key="value" :value="value">{{ label }}</option>
              </select>
              <label class="field-label" for="evidence">具体恢复依据</label>
              <input id="evidence" v-model="draft.evidence" class="text-input" maxlength="1000" placeholder="例如：车主确认已正常完成一次充电">
            </template>
            <button
              class="btn primary"
              type="submit"
              :disabled="busy || !draft.result.trim() || !draft.contactedAt || (draft.outcome === 'resolved' && (!draft.evidenceSource || !draft.evidence.trim()))"
            >
              提交回访结果
            </button>
          </form>
        </section>

        <form v-if="canHandoff" class="stack-form action-block" @submit.prevent="$emit('handoff')">
          <h3>转交</h3>
          <label class="field-label" for="handoff-target">转交给</label>
          <select id="handoff-target" v-model="draft.handoffTarget" class="text-input" required>
            <option value="" disabled>请选择</option>
            <option v-for="item in assignees" :key="item.username" :value="item.username">
              {{ item.username }} · {{ item.role === 'admin' ? '管理员' : '坐席' }}
            </option>
          </select>
          <label class="field-label" for="handoff-reason">转交原因</label>
          <textarea id="handoff-reason" v-model="draft.handoffReason" class="text-area" maxlength="500"></textarea>
          <button class="btn secondary" type="submit" :disabled="busy || !draft.handoffTarget || !draft.handoffReason.trim()">转交</button>
        </form>
      </aside>
    </div>
  </article>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import FieldDispatchPanel from './FieldDispatchPanel.vue'
import FieldOrders from './FieldOrders.vue'
import SopEvidencePanel from './SopEvidencePanel.vue'
import WorkflowExtras from './WorkflowExtras.vue'
import { findProblems, lookupDevice } from '../api/workbench'
import { channelLabel, displayStatus, disposalLabel, escalationLabel, isUnowned, kindLabel, orderNumber, slaPresentation, suspendLabel } from '../status'

const props = defineProps({
  problem: { type: Object, required: true },
  draft: { type: Object, required: true },
  busy: Boolean,
  canProcess: Boolean,
  canHandoff: Boolean,
  fieldInProgress: Boolean,
  handlingDenied: { type: String, default: '' },
  nextAction: { type: String, default: '' },
  refreshNote: { type: String, default: '' },
  assignees: { type: Array, default: () => [] },
  evidenceLabels: { type: Object, required: true }
})

defineEmits([
  'back', 'intake', 'mine', 'open', 'claim', 'save-note',
  'save-schedule', 'begin-callback', 'save-outcome', 'handoff', 'withdraw', 'link', 'field-result',
  'suspend', 'quick-close', 'escalate', 'add-evidence', 'assess'
])

const fieldLabels = { linked: '已关联', in_progress: '处理中', complete: '完工', rejected: '拒单', cancelled: '取消' }
const dispatchMode = ref(false)
const mobileCompact = ref(true)
const playingId = ref('')
const jumpNotice = ref(false)
const number = computed(() => orderNumber(props.problem))
const status = computed(() => displayStatus(props.problem))
const unowned = computed(() => isUnowned(props.problem))
const ownerText = computed(() => unowned.value ? '无主' : props.problem.owner)
const kindText = computed(() => kindLabel(props.problem.kind) || '问题')
const isCompromise = computed(() => Number(props.problem.compromiseFlag || 0) === 1 || props.problem.compromiseFlag === true)
const sla = computed(() => slaPresentation(props.problem.slaDueAt, props.problem.state))
const progressSteps = [
  { label: '受理' },
  { label: '联系' },
  { label: '派单' },
  { label: '上门' },
  { label: '完工' },
  { label: '回访' }
]
const currentStepIndex = computed(() => {
  const s = props.problem.state
  if (s === 'resolved' || s === 'answered' || s === 'referred') return 5
  if (s === 'verify') return 4
  if (s === 'field') return props.fieldInProgress ? 3 : 2
  const hasContact = (props.problem.activities || []).some(a => ['followup', 'callback', 'schedule', 'suspend'].includes(a.action))
  return hasContact ? 1 : 0
})
const repairHistory = computed(() => device.value?.repairHistory || [
  { date: '2026-10-12', summary: '更换 4G 通信模块', source: '智享乐装（模拟）' },
  { date: '2026-09-05', summary: '远程升级主板程序', source: '远程（模拟）' }
])
const callRecordings = computed(() => device.value?.callRecordings || [
  { id: 'REC-SIM-01', time: '11-25 10:22', title: '首次来电确认故障现象', duration: '01:45' },
  { id: 'REC-SIM-02', time: '11-25 10:15', title: '400 转接接入录音', duration: '03:28' }
])
function focusEl(id) {
  (document.getElementById(id) || document.getElementById('problem-actions'))?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const device = ref(null)
const history = ref([])
const deviceError = ref('')
const deviceRetryable = ref(false)
const historyError = ref('')
const historyRetryable = ref(false)
const deviceLoading = ref(false)
const historyLoading = ref(false)
let requestId = 0

function classifyLookupError(failure, missingText, deniedText, unavailableText) {
  if (failure && failure.status === 404) return { text: missingText, retryable: false }
  if (failure && failure.status === 403) return { text: deniedText, retryable: false }
  return { text: unavailableText, retryable: true }
}

async function loadRelated() {
  const id = props.problem.id
  const code = props.problem.device
  const current = ++requestId
  device.value = null
  history.value = []
  deviceError.value = ''
  deviceRetryable.value = false
  historyError.value = ''
  historyRetryable.value = false
  deviceLoading.value = Boolean(code)
  historyLoading.value = Boolean(code)
  if (!code) return
  await Promise.all([
    lookupDevice(code)
      .then(data => { if (current === requestId) device.value = data })
      .catch(failure => {
        if (current !== requestId) return
        const info = classifyLookupError(failure, '未找到该设备记录', '无权查询设备信息', '设备信息暂不可用')
        deviceError.value = info.text
        deviceRetryable.value = info.retryable
      })
      .finally(() => { if (current === requestId) deviceLoading.value = false }),
    findProblems(code)
      .then(data => {
        if (current === requestId) history.value = (Array.isArray(data) ? data : []).filter(item => item.id !== id).slice(0, 5)
      })
      .catch(failure => {
        if (current !== requestId) return
        const info = classifyLookupError(failure, '未找到历史问题单', '无权查询历史问题单', '历史问题单暂不可用')
        historyError.value = info.text
        historyRetryable.value = info.retryable
      })
      .finally(() => { if (current === requestId) historyLoading.value = false })
  ])
}

watch(() => [props.problem.id, props.problem.device], () => {
  dispatchMode.value = false
  loadRelated()
}, { immediate: true })

function dateLabel(value) {
  return value ? String(value).replace('T', ' ').replace(/\.\d+/, '').slice(0, 16) : '时间未知'
}

function ownerName(value) {
  return !value || value === 'early' ? '无主' : value
}
</script>
