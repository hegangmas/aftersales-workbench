<template>
  <section class="dispatch-shell" aria-label="派单至智享乐装">
    <!-- 1. 顶部步骤条 -->
    <header class="dispatch-top">
      <div class="top-left">
        <button class="back-btn" type="button" @click="$emit('back')">
          <span aria-hidden="true">←</span>
          <span>返回工作台</span>
        </button>
        <div class="top-title-group">
          <div class="top-title-row">
            <h2>派单至智享乐装</h2>
            <span class="badge-soft">V1.1 现场派单</span>
          </div>
          <p class="top-subtitle">核对受理信息、补全派单字段并跟踪智享乐装回传闭环</p>
        </div>
      </div>

      <ol class="step-bar" aria-label="派单流程步骤">
        <li
          v-for="(step, idx) in dispatchSteps"
          :key="step.id"
          class="step-item"
          :class="{ done: isStepDone(idx), active: isStepActive(idx) }"
        >
          <span class="step-num">{{ isStepDone(idx) ? '✓' : idx + 1 }}</span>
          <span class="step-text">{{ step.label }}</span>
          <span v-if="idx < dispatchSteps.length - 1" class="step-arrow" aria-hidden="true">→</span>
        </li>
      </ol>
    </header>

    <!-- 主体三栏 -->
    <div class="dispatch-grid">
      <FieldOrderSummary
        :problem="problem"
        :device="device"
        :busy="busy"
        :refresh-tip="refreshTip"
        @refresh="refreshView"
      />

      <FieldDispatchForm
        ref="dispatchFormRef"
        :problem="problem"
        :device="device"
        :is-device-pending="isDevicePending"
        :needs-claim-before-dispatch="needsClaimBeforeDispatch"
        :fields-locked="fieldsLocked"
        :confirm-disabled="confirmDisabled"
        :latest-v11-order="latestV11Order"
        :field-state-label="fieldStateLabel"
        @dispatch-v11="$emit('dispatch-v11', $event)"
        @form-state="onFormState"
      />
    </div>

    <!-- 5. 底部左右分栏 -->
    <div class="bottom-split">
      <!-- 底部左卡：3 个操作按钮 -->
      <section class="card-bottom left-action-card" aria-label="派单操作">
        <header class="bottom-card-head">
          <div>
            <h3>STEP 3 · 确认与流转</h3>
            <p class="bottom-sub">核对补填字段后确认派单，系统将自动生成智享乐装工单号</p>
          </div>
        </header>

        <div class="left-btn-group">
          <button
            class="btn-secondary"
            type="button"
            :disabled="busy"
            @click="onAuxAction('followup')"
          >创建跟进任务</button>

          <button
            class="btn-secondary"
            type="button"
            :disabled="busy"
            @click="onAuxAction('engineer')"
          >转工程师</button>

          <button
            class="btn-primary"
            type="submit"
            form="dispatch-v11-form"
            :disabled="confirmDisabled"
          >
            {{ busy ? '派单提交中…' : (latestV11Order ? '已派单至乐装' : '确认派单') }}
          </button>
        </div>

        <p v-if="isDevicePending" class="block-tip" role="alert">
          设备信息待补，禁止派单（请先返回工作台补齐桩 SN）
        </p>
        <p v-else-if="!canProcess" class="perm-tip">
          当前账号无处理权限，仅可查看派单详情
        </p>
        <p v-else-if="auxTip" class="aux-tip" role="status">
          {{ auxTip }}
        </p>
      </section>

      <FieldCallbackTracker
        :problem="problem"
        :latest-order="latestOrder"
        :latest-v11-order="latestV11Order"
        :legacy-active-order="legacyActiveOrder"
        :busy="busy"
        :can-process="canProcess"
        :is-problem-terminal="isProblemTerminal"
        :first-photo-name="firstPhotoName"
        :field-state-label="fieldStateLabel"
        @lz-callback="$emit('lz-callback', $event)"
        @field-result="$emit('field-result', $event)"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import FieldCallbackTracker from './field/FieldCallbackTracker.vue'
import FieldDispatchForm from './field/FieldDispatchForm.vue'
import FieldOrderSummary from './field/FieldOrderSummary.vue'

const props = defineProps({
  problem: { type: Object, required: true },
  device: { type: Object, default: null },
  busy: Boolean,
  canProcess: Boolean
})

defineEmits(['link', 'field-result', 'dispatch-v11', 'lz-callback', 'back'])

const dispatchSteps = [
  { id: 'check', label: '核对信息' },
  { id: 'fill', label: '补全字段' },
  { id: 'confirm', label: '确认派单' }
]

const fieldStateTextMap = {
  dispatched: '已派单（待接单）',
  accepted: '已接单',
  arrived: '已上门',
  completed: '完工回传',
  linked: '已接单（已关联）',
  in_progress: '已上门（处理中）',
  complete: '完工回传',
  rejected: '已拒单',
  cancelled: '已取消'
}

const V11_ORDER_STATES = ['dispatched', 'accepted', 'arrived', 'completed']

const dispatchFormRef = ref(null)
const refreshTip = ref('')
const auxTip = ref('')
const isFormComplete = ref(false)
const firstPhotoName = ref('')

const isDevicePending = computed(() =>
  Number(props.problem?.devicePending || 0) === 1 || props.problem?.devicePending === true
)

const isProblemTerminal = computed(() =>
  ['closed', 'cancelled', 'resolved'].includes(props.problem?.state)
)

const fieldOrdersList = computed(() =>
  Array.isArray(props.problem?.fieldOrders) ? props.problem.fieldOrders : []
)

const latestV11Order = computed(() =>
  fieldOrdersList.value.find(item => item && V11_ORDER_STATES.includes(item.state)) || null
)

const legacyActiveOrder = computed(() =>
  fieldOrdersList.value.find(item => item && (item.state === 'linked' || item.state === 'in_progress')) || null
)

const latestOrder = computed(() =>
  latestV11Order.value || legacyActiveOrder.value || fieldOrdersList.value[0] || null
)

const hasDispatched = computed(() =>
  Boolean(latestV11Order.value || ['pending_callback', 'pending_accept', 'closed'].includes(props.problem?.state))
)

const needsClaimBeforeDispatch = computed(() =>
  props.problem?.state === 'pending_assign'
)

const fieldsLocked = computed(() =>
  Boolean(latestV11Order.value) || isDevicePending.value || isProblemTerminal.value
)

const confirmDisabled = computed(() =>
  props.busy ||
  !props.canProcess ||
  isDevicePending.value ||
  needsClaimBeforeDispatch.value ||
  hasDispatched.value ||
  isProblemTerminal.value
)

// 顶部步骤条高亮：0=核对信息, 1=补全字段, 2=确认派单
const activeStepIndex = computed(() => {
  if (hasDispatched.value) return 2
  if (isFormComplete.value && !isDevicePending.value) return 2
  return 1
})

function isStepDone(idx) {
  if (hasDispatched.value) return idx < 2 || (idx === 2 && ['pending_accept', 'closed'].includes(props.problem?.state))
  return idx < activeStepIndex.value
}

function isStepActive(idx) {
  if (hasDispatched.value && ['pending_accept', 'closed'].includes(props.problem?.state)) return false
  return idx === activeStepIndex.value
}

function fieldStateLabel(state) {
  return fieldStateTextMap[state] || state || '未知'
}

function onFormState(payload) {
  isFormComplete.value = Boolean(payload?.isComplete)
  firstPhotoName.value = payload?.firstPhotoName || ''
}

function refreshView() {
  dispatchFormRef.value?.syncFromProps()
  const now = new Date()
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  const ss = String(now.getSeconds()).padStart(2, '0')
  refreshTip.value = `已同步最新工单与设备字段（${hh}:${mm}:${ss}）`
}

function onAuxAction(type) {
  if (type === 'followup') {
    auxTip.value = '请点击左上角「← 返回工作台」，在「保存工单 / 远程指导」中创建跟进任务。'
  } else {
    auxTip.value = '请点击左上角「← 返回工作台」，使用「直接派单」将工单转派给工程师。'
  }
}

watch(
  () => props.problem?.id,
  () => {
    refreshTip.value = ''
    auxTip.value = ''
  }
)
</script>

<style scoped>
.dispatch-shell {
  display: grid;
  gap: 14px;
  color: var(--ink, #334155);
}

.dispatch-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px 20px;
  padding: 14px 18px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 12px;
  box-shadow: 0 4px 14px #132c4908;
}

.top-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 6px 12px;
  border: 1px solid #cce7e2;
  border-radius: 8px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.back-btn:hover { background: #d7eeeb; }
.top-title-group { min-width: 0; }
.top-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.top-title-row h2 { margin: 0; color: var(--heading, #0f172a); font-size: 18px; font-weight: 700; }

.badge-soft {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
}

.top-subtitle { margin: 2px 0 0; color: var(--text-tertiary, #7a8686); font-size: 12px; }

.step-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  list-style: none;
  margin: 0;
  padding: 8px 14px;
  background: var(--bg-subtle, #f8fafa);
  border: 0;
  border-radius: 10px;
}

.step-item { display: inline-flex; align-items: center; gap: 7px; font-size: 13px; color: var(--text-tertiary, #7a8686); }

.step-num {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--bg-surface, #fff);
  border: 1.5px solid var(--border-strong, #cfd7d7);
  color: var(--text-tertiary, #7a8686);
  font-family: var(--font-num, sans-serif);
  font-size: 12px;
  font-weight: 600;
}

.step-item.done .step-num { background: var(--brand-700, #0f766e); color: #fff; border-color: var(--brand-700, #0f766e); }

.step-item.active .step-num {
  background: var(--bg-surface, #fff);
  border: 2px solid var(--brand-700, #0f766e);
  color: var(--brand-700, #0f766e);
  box-shadow: 0 0 0 4px rgba(13, 148, 136, 0.14);
}

.step-item.done .step-text,
.step-item.active .step-text { color: var(--text-primary, #152020); font-weight: 600; }
.step-arrow { margin-left: 2px; color: var(--text-disabled, #a8b2b2); }

.dispatch-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.95fr) minmax(260px, 0.95fr) minmax(320px, 1.15fr);
  gap: var(--gutter, 16px);
  align-items: stretch;
}

.bottom-split {
  display: grid;
  grid-template-columns: minmax(320px, 0.92fr) minmax(420px, 1.4fr);
  gap: 14px;
  align-items: start;
}

.card-bottom {
  min-width: 0;
  padding: 18px 20px;
  background: var(--bg-surface, #ffffff);
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  box-shadow: var(--elev-1);
}

.bottom-card-head h3 { margin: 0; color: var(--heading, #0f172a); font-size: 15px; font-weight: 700; }

.bottom-sub {
  margin: 3px 0 0;
  color: var(--muted, #8a96a5);
  font-size: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.left-btn-group {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.btn-primary { border: 1px solid var(--pine, #0f766e); background: var(--pine, #0f766e); color: #fff; }
.btn-primary:hover:not(:disabled) { background: var(--pine-bright, #0d9488); border-color: var(--pine-bright, #0d9488); }
.btn-secondary { border: 1px solid #dce3ec; background: #fff; color: var(--ink, #334155); }
.btn-secondary:hover:not(:disabled) { border-color: var(--pine, #0f766e); color: var(--pine, #0f766e); }
.btn-primary:disabled,
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }

.block-tip {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  font-size: 12px;
  font-weight: 600;
}

.perm-tip { margin: 10px 0 0; color: #b45309; font-size: 12px; }

.aux-tip {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 12px;
}

@media (max-width: 1120px) {
  .dispatch-grid { grid-template-columns: 1fr 1fr; }
  .dispatch-grid > :deep(:last-child) { grid-column: 1 / -1; }
  .bottom-split { grid-template-columns: 1fr; }
}

@media (max-width: 760px) {
  .dispatch-grid,
  .left-btn-group { grid-template-columns: 1fr; }
  .dispatch-grid > :deep(:last-child) { grid-column: auto; }
}
</style>
