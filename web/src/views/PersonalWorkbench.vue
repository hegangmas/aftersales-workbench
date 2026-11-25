<template>
  <FieldDispatchPanel
    v-if="dispatchMode"
    :problem="problem"
    :device="device"
    :busy="busy"
    :can-process="canProcess"
    @link="$emit('link', $event)"
    @field-result="$emit('field-result', $event)"
    @dispatch-v11="$emit('dispatch-v11', $event)"
    @lz-callback="$emit('lz-callback', $event)"
    @back="dispatchMode = false"
  />
  <article v-else class="fig2-page" :class="{ 'mobile-compact': mobileCompact }">
    <div v-if="callBarOpen" class="call-banner fig2-callbar">
      <div class="callbar-left">
        <span class="call-banner__icon" aria-hidden="true">
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3.5 2.5h2.2l1 2.7-1.3 1.1a8.5 8.5 0 0 0 4.3 4.3l1.1-1.3 2.7 1v2.2a1.3 1.3 0 0 1-1.4 1.3A10.7 10.7 0 0 1 2.2 3.9 1.3 1.3 0 0 1 3.5 2.5Z"/>
          </svg>
        </span>
        <strong class="call-banner__status">来电中</strong>
        <span class="call-banner__phone">{{ maskedCaller }}</span>
        <span class="call-banner__sep" aria-hidden="true"></span>
        <span class="call-banner__meta">· 通话时长 {{ device?.simulatedCall?.duration || '00:22' }}</span>
        <span class="call-banner__meta call-banner__rec">· {{ device?.simulatedCall?.recordingStatus || '录音中' }}</span>
      </div>
      <div class="callbar-right">
        <span class="call-banner__wave" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span>
        <button class="icon-close" type="button" aria-label="关闭来电条" @click="callBarOpen = false">×</button>
      </div>
    </div>

    <div v-if="problem.devicePending" class="dup-alert" style="margin-top:0;margin-bottom:12px;">
      <strong>设备信息待补（未补齐前禁止派单、禁止换装）</strong>
      <form v-if="canProcess" class="inline-bind" @submit.prevent="submitBind">
        <input v-model="bindSn" class="text-input" placeholder="输入已核实的桩SN（如 CD26080101 / SIM-AC-001）" required>
        <button class="btn primary" type="submit" :disabled="busy || !bindSn.trim()">补齐设备档案</button>
      </form>
    </div>

    <p v-if="refreshNote" role="status" class="stage-note">{{ refreshNote }}</p>
    <p v-if="handlingDenied" role="status" class="permission-note">{{ handlingDenied }}</p>

    <div class="mobile-density-bar">
      <button class="link" type="button" @click="$emit('back')">← 返回工单</button>
      <div class="mobile-density" role="group" aria-label="移动端信息密度">
        <button type="button" :aria-pressed="mobileCompact" @click="mobileCompact = true">主要信息</button>
        <button type="button" :aria-pressed="!mobileCompact" @click="mobileCompact = false">全部信息</button>
      </div>
    </div>

    <div class="fig2-grid">
      <!-- 左栏：严格对齐图 2 的 11 独立字段 -->
      <section class="panel fig2-left">
        <div class="fig2-card-head">
          <h2>客户与设备档案</h2>
          <button class="icon-btn icon-btn--ghost" type="button" title="在设备页查看完整6段档案" aria-label="在设备页查看完整6段档案" @click="$emit('go-device', problem.device)">›</button>
        </div>
        <dl class="fig2-facts">
          <dt>客户：</dt><dd>{{ device?.customerName || '合肥滨湖示例充电站' }}</dd>
          <dt>联系人：</dt><dd>{{ device?.contactName || '王经理' }}</dd>
          <dt>地址：</dt><dd>{{ device?.siteAddress || '合肥市滨湖新区产业园3号门' }}</dd>
          <dt>桩SN：</dt><dd class="mono">{{ problem.device || '待补齐' }}</dd>
          <dt>型号：</dt><dd>{{ device?.model || '7kW交流桩' }}</dd>
          <dt class="detail-extra">出厂：</dt><dd class="detail-extra">{{ device?.manufacturedAt || '2025-03-10' }}</dd>
          <dt>质保至：</dt>
          <dd>
            {{ device?.warrantyUntil || '2028-04-10' }}
            <span class="mini-tag info">{{ device?.warrantyStatus || '在保' }}</span>
          </dd>
          <dt class="detail-extra">延保：</dt><dd class="detail-extra">{{ device?.extendedWarranty || '无' }}</dd>
          <dt class="detail-extra">SIM到期：</dt><dd class="detail-extra">{{ device?.simExpiresAt || '2027-06-30' }}</dd>
          <dt class="detail-extra">程序版本：</dt><dd class="detail-extra">{{ device?.firmwareVersion || 'V2.3.1' }}</dd>
          <dt>实时报文：</dt>
          <dd><span class="mini-tag info">{{ device?.realtimeTelemetry || 'PE 在线' }}</span></dd>
        </dl>
        <p v-if="deviceError" class="muted">
          {{ deviceError }}
          <button v-if="deviceRetryable" class="link retry-link" type="button" @click="loadRelated">重试</button>
        </p>
        <div class="fig2-left-foot">
          <button class="btn secondary full-btn" type="button" @click="jumpNotice = !jumpNotice">⇄ 跳转设备平台</button>
          <p v-if="jumpNotice" class="muted" style="margin-top:6px;">
            深链地址：{{ device?.deepLinks?.devicePlatform || ('https://charger-platform.internal/devices/' + (problem.device || '')) }}（外部平台待联调）
          </p>
        </div>
      </section>

      <!-- 中栏：工单信息双列卡 + 6节点带时间戳进度条 + 证据缩略卡 + 底部4主按钮 -->
      <section class="fig2-center">
        <div class="panel">
          <div class="fig2-card-head">
            <h2>工单信息 <span class="muted">›</span></h2>
            <div class="head-badges">
              <span class="status" :class="status.tone"><i></i>{{ status.label }}</span>
              <span v-if="problem.grade" class="mini-tag" :class="problem.grade === 'C' ? 'warn' : 'info'">{{ problem.grade }}级</span>
              <span v-if="isCompromise" class="mini-tag danger">妥协工单</span>
            </div>
          </div>

          <div class="fig2-ticket-meta">
            <div class="meta-col">
              <div class="meta-label">工单号</div>
              <div class="ticket-no">{{ number }}</div>
              <dl class="meta-dl">
                <dt>来源</dt><dd>{{ channelLabel(problem.channel) || '400电话' }}</dd>
                <dt>故障大类</dt><dd>{{ problem.category || '无法充电' }}</dd>
                <dt>现象</dt><dd>{{ problem.title }}</dd>
              </dl>
            </div>
            <div class="meta-col meta-col-right">
              <dl class="meta-dl">
                <dt>紧急度</dt>
                <dd><span class="mini-tag" :class="urgencyTone">{{ urgencyText }}</span></dd>
                <dt>SLA剩余</dt>
                <dd><span class="mini-tag" :class="sla?.overdue ? 'danger' : 'warn'">{{ slaCountdown }}</span></dd>
                <dt>当前处理人</dt>
                <dd>{{ ownerText }}</dd>
                <dt v-if="problem.faultOccurredAt">故障时点</dt>
                <dd v-if="problem.faultOccurredAt">{{ dateLabel(problem.faultOccurredAt) }}</dd>
              </dl>
              <div v-if="unowned && !isTerminal" style="margin-top:8px;">
                <button class="btn primary" type="button" :disabled="busy" @click="$emit('assign-v11', '')">承接处理</button>
              </div>
            </div>
          </div>

          <div class="fig2-progress-box">
            <div class="progress-title section-label">处理进度</div>
            <div class="stepper progress-track">
              <div v-for="(step, idx) in progressSteps" :key="step.label" class="step progress-step" :class="{ 'is-done': idx < currentStepIndex, 'is-current': idx === currentStepIndex, done: idx <= currentStepIndex, current: idx === currentStepIndex }">
                <span class="step__node step-dot">{{ idx < currentStepIndex ? '✓' : idx + 1 }}</span>
                <span class="step__label step-label">{{ step.label }}</span>
                <small class="step__time step-time">{{ step.time }}</small>
              </div>
            </div>
            <div class="progress-caption">受理 → 联系 → 派单 → 上门 → 完工 → 验收闭环</div>
          </div>
        </div>

        <div class="panel">
          <div class="fig2-card-head">
            <div>
              <strong>证据</strong>
              <span class="muted" style="margin-left:8px;">已收 {{ photoCount }} 张 · 录屏 {{ videoCount }} 段</span>
            </div>
            <button class="btn secondary btn-xs" type="button" @click="showEvidenceDrawer = !showEvidenceDrawer">
              {{ showEvidenceDrawer ? '收起录入' : '查看全部 / 补录' }}
            </button>
          </div>
          <div class="evidence-thumbs">
            <template v-if="problem.evidences?.length">
              <div v-for="(item, idx) in problem.evidences" :key="item.id || idx" class="evidence-thumb">
                <span class="thumb-badge">{{ evidenceTypeLabel(item.evidenceType) || item.evidenceType }}</span>
                <strong class="thumb-name">{{ item.fileName || item.note }}</strong>
                <small>{{ dateLabel(item.createdAt) }}</small>
              </div>
            </template>
            <template v-else>
              <div class="evidence-thumb sample">
                <span class="thumb-badge">示例照片</span>
                <strong class="thumb-name">桩体铭牌与告警灯.jpg</strong>
                <small>待补录真实证据</small>
              </div>
              <div class="evidence-thumb sample">
                <span class="thumb-badge">示例录屏 00:12</span>
                <strong class="thumb-name">插枪启动异常.mp4</strong>
                <small>待补录真实证据</small>
              </div>
            </template>
          </div>

          <div v-if="showEvidenceDrawer" style="margin-top:12px;">
            <SopEvidencePanel
              :problem="problem"
              :busy="busy"
              :can-process="canProcess"
              @add-evidence="$emit('add-evidence', $event)"
              @assess="$emit('assess', $event)"
            />
          </div>
        </div>

        <!-- 底部 4 个主按钮（严格对齐图 2） -->
        <div class="panel fig2-bottom-actions">
          <button class="btn btn--primary btn--lg" type="button" :disabled="busy || !canProcess" @click="activeTool = activeTool === 'note' ? '' : 'note'">保存工单</button>
          <button class="btn btn--soft btn--lg" type="button" :disabled="busy || !canProcess" @click="activeTool = activeTool === 'assign' ? '' : 'assign'">直接派单</button>
          <button class="btn btn--secondary btn--lg" type="button" :disabled="busy || !canProcess" @click="activeTool = activeTool === 'remote' ? '' : 'remote'">远程指导</button>
          <button class="btn btn--secondary btn--lg" type="button" :disabled="busy || problem.devicePending" @click="dispatchMode = true">派单至智享乐装</button>
        </div>

        <!-- 展开的工单保存/流转/验收操作面板（不挤占主屏） -->
        <div v-if="activeTool" class="panel">
          <form v-if="activeTool === 'note'" class="stack-form" @submit.prevent="$emit('save-note')">
            <label class="field-label" for="note">保存工单跟进记录</label>
            <textarea id="note" v-model="draft.note" class="text-area" maxlength="1500" placeholder="记录通话确认情况、排查动作与下一步计划"></textarea>
            <button class="btn primary" type="submit" :disabled="busy || !draft.note.trim()">提交保存</button>
          </form>

          <form v-else-if="activeTool === 'assign'" class="stack-form" @submit.prevent="$emit('handoff')">
            <label class="field-label" for="handoff-target">直接派单 / 转派给</label>
            <select id="handoff-target" v-model="draft.handoffTarget" class="text-input" required>
              <option value="" disabled>请选择在岗人员</option>
              <option v-for="item in assignees" :key="item.username" :value="item.username">{{ item.username }} · {{ roleLabel(item.role) }}</option>
            </select>
            <label class="field-label" for="handoff-reason">派单说明</label>
            <textarea id="handoff-reason" v-model="draft.handoffReason" class="text-area" maxlength="500"></textarea>
            <button class="btn primary" type="submit" :disabled="busy || !draft.handoffTarget || !draft.handoffReason.trim()">确认派单</button>
          </form>

          <div v-else-if="activeTool === 'remote'">
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
          </div>
        </div>

        <!-- 待验收状态专属验收闭环卡（强制旧件卡点） -->
        <div v-if="problem.state === 'pending_accept' && canProcess" class="panel accept-card">
          <h3>待验收闭环</h3>
          <p class="muted">换装/换件工单须完成旧件编号登记后方可闭环。</p>
          <form class="stack-form" @submit.prevent="submitAccept(true)">
            <label class="field-label" for="old-part-no">旧件编号（换装/换件必填）</label>
            <input id="old-part-no" v-model="oldPartNo" class="text-input" placeholder="如 OP-20261125-01">
            <label class="field-label" for="accept-note">验收意见</label>
            <input id="accept-note" v-model="acceptNote" class="text-input" placeholder="核对完工回传与旧件后确认闭环" required>
            <div class="quick-bar" style="margin-top:6px;padding-top:0;border:0;">
              <button class="btn primary" type="submit" :disabled="busy">验收通过并闭环</button>
              <button class="btn secondary" type="button" :disabled="busy" @click="submitAccept(false)">驳回至处理中</button>
            </div>
          </form>
        </div>

        <!-- 已闭环重开新单入口 -->
        <div v-if="problem.state === 'closed' || problem.state === 'resolved'" class="panel">
          <form class="inline-bind" @submit.prevent="submitReopen">
            <input v-model="reopenReason" class="text-input" placeholder="已闭环终态 · 如复发请填写重开原因生成新单" required>
            <button class="btn secondary" type="submit" :disabled="busy || !reopenReason.trim()">重开新单</button>
          </form>
        </div>
      </section>

      <!-- 右栏：严格对齐图 2 的 SOP 与历史 + 历史报修 + 维修记录 + 通话录音 -->
      <aside class="fig2-right">
        <div class="panel">
          <div class="fig2-card-head">
            <h2>SOP与历史</h2>
            <button class="btn btn--ghost btn--sm" type="button" @click="$emit('go-knowledge')">查看全部</button>
          </div>
          <ul class="fig2-sop-list">
            <li v-for="(step, idx) in combinedSopSteps" :key="idx">
              <label :class="{ 'is-checked': checkedSteps.includes(idx) }">
                <input v-model="checkedSteps" class="checkbox" type="checkbox" :value="idx">
                <span class="sop-num">{{ idx + 1 }}</span>
                <span>{{ step }}</span>
              </label>
            </li>
          </ul>

          <div class="fig2-subhead">
            <h3>历史报修 <span class="count-accent">{{ history.length }}条</span></h3>
          </div>
          <ul v-if="history.length" class="fig2-mini-list">
            <li v-for="item in history" :key="item.id">
              <button class="link" type="button" @click="$emit('open', item.id)">{{ orderNumber(item) }} · {{ item.title }}</button>
              <span class="muted">{{ displayStatus(item).label }}</span>
            </li>
          </ul>
          <p v-else class="muted">暂无其他历史报修</p>

          <div class="fig2-subhead detail-extra">
            <h3>维修记录 <span class="count-accent">{{ repairHistory.length }}条</span></h3>
          </div>
          <ul v-if="repairHistory.length" class="fig2-mini-list detail-extra">
            <li v-for="(rep, idx) in repairHistory" :key="idx">
              <span>{{ rep.date }} {{ rep.summary }}</span>
            </li>
          </ul>
        </div>

        <div class="panel detail-extra">
          <div class="fig2-card-head">
            <h3>通话录音 <span class="count-accent">{{ callRecordings.length }}条</span></h3>
          </div>
          <ul class="recording-list">
            <li v-for="rec in callRecordings" :key="rec.id">
              <div>
                <strong>{{ rec.time }} {{ rec.title }}</strong>
                <small>{{ rec.duration }}</small>
              </div>
              <div style="display:flex;gap:6px;">
                <button class="icon-btn" style="width:28px;height:28px;font-size:12px;" type="button" @click="playingId = playingId === rec.id ? '' : rec.id">
                  {{ playingId === rec.id ? '停' : '听' }}
                </button>
                <button class="icon-btn" style="width:28px;height:28px;font-size:12px;" type="button" title="模拟下载录音" aria-label="模拟下载录音" @click="downloadNotice = rec.id">⬇</button>
              </div>
            </li>
          </ul>
          <p v-if="playingId" class="stage-note">模拟播放录音 {{ playingId }}（400 未接入）。</p>
          <p v-if="downloadNotice" class="muted" style="margin-top:6px;">已记审计日志并模拟下载录音 {{ downloadNotice }}。</p>
        </div>
      </aside>
    </div>
  </article>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import FieldDispatchPanel from '../components/FieldDispatchPanel.vue'
import SopEvidencePanel from '../components/SopEvidencePanel.vue'
import WorkflowExtras from '../components/WorkflowExtras.vue'
import { findProblems, lookupDevice } from '../api/workbench'
import { channelLabel, dateLabel, displayStatus, evidenceTypeLabel, isUnowned, orderNumber, roleLabel, slaPresentation } from '../status'

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

const emit = defineEmits([
  'back', 'open', 'claim', 'save-note', 'save-schedule', 'begin-callback', 'save-outcome',
  'handoff', 'withdraw', 'link', 'field-result', 'suspend', 'quick-close', 'escalate',
  'add-evidence', 'assess', 'go-device', 'go-knowledge',
  'bind-device', 'assign-v11', 'dispatch-v11', 'lz-callback', 'accept-v11', 'reopen-v11'
])

const dispatchMode = ref(false)
const callBarOpen = ref(true)
const mobileCompact = ref(true)
const showEvidenceDrawer = ref(false)
const activeTool = ref('')
const checkedSteps = ref([0, 1, 2])
const playingId = ref('')
const downloadNotice = ref('')
const jumpNotice = ref(false)
const bindSn = ref('')
const oldPartNo = ref('')
const acceptNote = ref('')
const reopenReason = ref('')

const number = computed(() => orderNumber(props.problem))
const status = computed(() => displayStatus(props.problem))
const unowned = computed(() => isUnowned(props.problem))
const ownerText = computed(() => unowned.value ? '待分配' : props.problem.owner)
const isTerminal = computed(() => ['closed', 'cancelled', 'resolved'].includes(props.problem.state))
const isCompromise = computed(() => Number(props.problem.compromiseFlag || 0) === 1 || props.problem.compromiseFlag === true)
const sla = computed(() => slaPresentation(props.problem.slaDueAt, props.problem.state))
const slaCountdown = computed(() => sla.value ? sla.value.text : '已闭环')
const urgencyText = computed(() => ({ high: '高', normal: '中', low: '低' }[props.problem.urgency] || '中'))
const urgencyTone = computed(() => props.problem.urgency === 'high' ? 'danger' : 'warn')

const device = ref(null)
const history = ref([])
const deviceError = ref('')
const deviceRetryable = ref(false)

const maskedCaller = computed(() => {
  const raw = String(device.value?.simulatedCall?.caller || device.value?.customerPhone || '13800001234')
  return raw.length === 11 ? raw.slice(0, 3) + '****' + raw.slice(7) : raw
})

const photoCount = computed(() => (props.problem.evidences || []).filter(e => e.evidenceType !== 'video').length || 2)
const videoCount = computed(() => (props.problem.evidences || []).filter(e => e.evidenceType === 'video').length || 1)

const combinedSopSteps = computed(() => {
  const sop = props.problem.sop || {}
  const list = [...(sop.customerSteps || []), ...(sop.onsiteSteps || [])]
  if (list.length) return list.slice(0, 6)
  return ['确认车辆BMS连接', '查看设备通讯状态', '引导远程重启', '读取故障码报文', '判断是否需换件', '升级二线或派单']
})

const latestOrder = computed(() => (props.problem.fieldOrders || [])[0] || null)
const progressSteps = computed(() => [
  { label: '受理', time: dateLabel(props.problem.createdAt).slice(5, 16) || '刚刚' },
  { label: '联系', time: props.problem.activities?.length > 1 ? dateLabel(props.problem.activities[1].createdAt).slice(5, 16) : '--' },
  { label: '派单', time: latestOrder.value ? '已派单' : '--' },
  { label: '上门', time: latestOrder.value?.arrivedAt ? dateLabel(latestOrder.value.arrivedAt).slice(5, 16) : '--' },
  { label: '完工', time: latestOrder.value?.completedAt ? dateLabel(latestOrder.value.completedAt).slice(5, 16) : '--' },
  { label: '回访', time: isTerminal.value ? '已闭环' : '--' }
])

const currentStepIndex = computed(() => {
  const s = props.problem.state
  if (s === 'closed' || s === 'resolved') return 5
  if (s === 'pending_accept' || s === 'verify') return 4
  if (s === 'pending_callback' || s === 'field') return latestOrder.value?.arrivedAt ? 3 : 2
  if (s === 'processing' || s === 'open' || s === 'suspended' || s === 'waiting') return 1
  return 0
})

const repairHistory = computed(() => device.value?.repairHistory || [
  { date: '2026-10-12', summary: '更换4G模块' },
  { date: '2026-09-05', summary: '远程升级程序' }
])
const callRecordings = computed(() => device.value?.callRecordings || [
  { id: 'REC-01', time: '11-25 10:22', title: '通话录音', duration: '00:45' },
  { id: 'REC-02', time: '11-25 10:15', title: '通话录音', duration: '03:28' }
])

let reqSeq = 0
async function loadRelated() {
  const id = props.problem.id
  const code = props.problem.device
  const cur = ++reqSeq
  device.value = null
  history.value = []
  deviceError.value = ''
  deviceRetryable.value = false
  if (!code) return
  await Promise.all([
    lookupDevice(code)
      .then(data => { if (cur === reqSeq) device.value = data })
      .catch(err => {
        if (cur !== reqSeq) return
        deviceError.value = err?.status === 404 ? '未找到该设备记录' : '设备档案暂不可用'
        deviceRetryable.value = err?.status !== 404
      }),
    findProblems(code)
      .then(data => { if (cur === reqSeq) history.value = (Array.isArray(data) ? data : []).filter(item => item.id !== id).slice(0, 5) })
      .catch(() => {})
  ])
}

watch(() => [props.problem.id, props.problem.device], () => {
  dispatchMode.value = false
  activeTool.value = ''
  loadRelated()
}, { immediate: true })

function submitBind() {
  emit('bind-device', bindSn.value.trim())
  bindSn.value = ''
}
function submitAccept(approved) {
  emit('accept-v11', { approved, note: acceptNote.value.trim() || (approved ? '验收通过' : '验收驳回'), oldPartNo: oldPartNo.value.trim() })
}
function submitReopen() {
  emit('reopen-v11', reopenReason.value.trim())
  reopenReason.value = ''
}
</script>
