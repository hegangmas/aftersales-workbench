<template>
  <section v-if="canProcess" class="workflow-extras">
    <details v-if="nonFieldStage" class="action-block extra-panel">
      <summary class="extra-summary">挂起与回池</summary>
      <form class="stack-form extra-body" @submit.prevent="submitSuspend">
        <label class="field-label" for="suspend-reason">挂起原因</label>
        <select id="suspend-reason" v-model="suspendReason" class="text-input" required>
          <option v-for="item in suspendOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>

        <label class="extra-check">
          <input v-model="returnToPool" type="checkbox">
          <span>挂起后回池共享</span>
        </label>

        <label class="field-label" for="suspend-due">下次提醒时间（可选，北京时间）</label>
        <input id="suspend-due" v-model="dueAt" class="text-input" type="datetime-local">

        <label class="field-label" for="suspend-next">待确认事项（可选）</label>
        <input id="suspend-next" v-model="nextStep" class="text-input" maxlength="500" placeholder="例如：等待客户断电重启后反馈">

        <button class="btn secondary" type="submit" :disabled="busy || !suspendReason">确认挂起</button>
      </form>
    </details>

    <details v-if="canQuickClose" class="action-block extra-panel">
      <summary class="extra-summary">简单工单快通道</summary>
      <form class="stack-form extra-body" @submit.prevent="submitQuickClose">
        <p class="stage-note">适用于远程重启/指导恢复且无需换件、换装或上门的工单。</p>

        <label class="field-label" for="qc-note">完结说明</label>
        <textarea id="qc-note" v-model="qcNote" class="text-area" maxlength="1000" placeholder="简述远程处理方式与恢复情况" required></textarea>

        <label class="field-label" for="qc-source">核验来源</label>
        <select id="qc-source" v-model="qcSource" class="text-input" required>
          <option value="" disabled>请选择</option>
          <option v-for="(label, value) in evidenceLabels" :key="value" :value="value">{{ label }}</option>
        </select>

        <label class="field-label" for="qc-evidence">完结依据</label>
        <input id="qc-evidence" v-model="qcEvidence" class="text-input" maxlength="1000" placeholder="例如：平台显示已正常开始充电" required>

        <button class="btn primary" type="submit" :disabled="busy || !qcNote.trim() || !qcSource || !qcEvidence.trim()">
          快通道直接结案
        </button>
      </form>
    </details>

    <details class="action-block extra-panel">
      <summary class="extra-summary">SLA 升级与妥协工单旁路</summary>
      <form class="stack-form extra-body" @submit.prevent="submitEscalate">
        <p v-if="currentEscalateText" class="stage-note">当前标记：{{ currentEscalateText }}</p>

        <label class="field-label" for="escalate-level">升级级别</label>
        <select id="escalate-level" v-model="escalateLevel" class="text-input" required>
          <option value="none">解除 / {{ escalationLabel('none') }}</option>
          <option value="l2">二线（{{ escalationLabel('l2') }}）</option>
          <option value="expert">专家（{{ escalationLabel('expert') }}）</option>
        </select>

        <label class="extra-check">
          <input v-model="compromise" type="checkbox">
          <span>标记为妥协工单（客户不配合排查）</span>
        </label>

        <label class="field-label" for="escalate-reason">升级或妥协原因</label>
        <textarea id="escalate-reason" v-model="escalateReason" class="text-area" maxlength="500" placeholder="说明超时升级原因或客户不配合情况" required></textarea>

        <button class="btn secondary" type="submit" :disabled="busy || !escalateReason.trim()">保存升级/妥协标记</button>
      </form>
    </details>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { escalationLabel, suspendLabel } from '../status'

const props = defineProps({
  problem: { type: Object, required: true },
  canProcess: Boolean,
  busy: Boolean,
  fieldInProgress: Boolean,
  evidenceLabels: {
    type: Object,
    default: () => ({ customer: '车主确认', platform: '平台充电记录', field: '现场复测' })
  }
})

const emit = defineEmits(['suspend', 'quick-close', 'escalate'])

const suspendOptions = [
  'customer_reboot',
  'customer_evidence',
  'parts_wait',
  'field_wait'
].map(value => ({ value, label: suspendLabel(value) }))

const suspendReason = ref('customer_reboot')
const returnToPool = ref(false)
const dueAt = ref('')
const nextStep = ref('')

function defaultSource(labels) {
  const keys = Object.keys(labels || {})
  if (keys.includes('platform')) return 'platform'
  return keys[0] || ''
}

const qcNote = ref('')
const qcSource = ref(defaultSource(props.evidenceLabels))
const qcEvidence = ref('')

const escalateLevel = ref(props.problem.escalationLevel || 'none')
const compromise = ref(Boolean(props.problem.compromiseFlag))
const escalateReason = ref('')

const nonFieldStage = computed(() => props.problem.state !== 'field' && !props.fieldInProgress)
const canQuickClose = computed(() => nonFieldStage.value
  && !['replace', 'part', 'onsite'].includes(props.problem.disposalType))

const currentEscalateText = computed(() => {
  const parts = []
  const level = props.problem.escalationLevel
  if (level && level !== 'none') parts.push(escalationLabel(level) || level)
  if (props.problem.compromiseFlag) parts.push('妥协工单')
  if (props.problem.escalationReason) parts.push(props.problem.escalationReason)
  return parts.join(' · ')
})

watch(() => props.problem.id, () => {
  suspendReason.value = 'customer_reboot'
  returnToPool.value = false
  dueAt.value = ''
  nextStep.value = ''
  qcNote.value = ''
  qcSource.value = defaultSource(props.evidenceLabels)
  qcEvidence.value = ''
  escalateLevel.value = props.problem.escalationLevel || 'none'
  compromise.value = Boolean(props.problem.compromiseFlag)
  escalateReason.value = ''
})

watch(() => [props.problem.escalationLevel, props.problem.compromiseFlag], ([level, flag]) => {
  escalateLevel.value = level || 'none'
  compromise.value = Boolean(flag)
})

function submitSuspend() {
  if (!suspendReason.value) return
  emit('suspend', {
    reason: suspendReason.value,
    returnToPool: Boolean(returnToPool.value),
    nextStep: nextStep.value.trim(),
    dueAt: dueAt.value || null
  })
  returnToPool.value = false
  dueAt.value = ''
  nextStep.value = ''
}

function submitQuickClose() {
  if (!qcNote.value.trim() || !qcSource.value || !qcEvidence.value.trim()) return
  emit('quick-close', {
    note: qcNote.value.trim(),
    evidenceSource: qcSource.value,
    evidence: qcEvidence.value.trim()
  })
  qcNote.value = ''
  qcEvidence.value = ''
}

function submitEscalate() {
  if (!escalateReason.value.trim()) return
  emit('escalate', {
    level: escalateLevel.value || 'none',
    reason: escalateReason.value.trim(),
    compromise: Boolean(compromise.value)
  })
  escalateReason.value = ''
}
</script>

<style scoped>
.workflow-extras { display: grid; gap: 10px; margin-top: 16px; }
.extra-panel { border: 1px solid var(--line); border-radius: 8px; background: #fff; padding: 10px 12px; }
.extra-panel.action-block + .extra-panel.action-block { margin-top: 0; padding-top: 10px; border-top: 1px solid var(--line); }
.extra-summary { cursor: pointer; font-size: 13px; font-weight: 600; color: var(--pine-ink); list-style-position: inside; }
.extra-body { margin-top: 10px; }
.extra-check { display: flex; align-items: center; gap: 8px; cursor: pointer; font-weight: 400; }
.extra-check input { width: auto; min-height: 0; }
</style>
