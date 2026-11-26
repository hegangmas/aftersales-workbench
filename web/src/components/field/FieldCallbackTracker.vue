<template>
  <!-- 底部右卡：回传状态 & 模拟智享乐装回传触发器 -->
  <section class="card-bottom" aria-label="智享乐装回传状态">
    <header class="bottom-head">
      <div>
        <h3>回传状态</h3>
        <p class="bottom-sub">
          <template v-if="latestOrder">
            乐装单号 <strong class="mono">{{ latestOrder.externalNo }}</strong>
            <span class="state-chip">{{ fieldStateLabel(latestOrder.state) }}</span>
            <span v-if="latestOrder.receipt"> · {{ latestOrder.receipt }}</span>
          </template>
          <template v-else>尚未派单，确认派单后将在此实时跟踪 4 节点回传进度</template>
        </p>
      </div>
      <span v-if="latestOrder && isTerminalFieldState(latestOrder.state)" class="closed-pill">
        {{ fieldStateLabel(latestOrder.state) }}
      </span>
    </header>

    <!-- 回退异常留痕醒目展示 -->
    <div v-if="latestOrder?.rollbackAnomalyNote" class="anomaly-alert" role="alert">
      <strong>回传异常留痕</strong>
      <p>{{ latestOrder.rollbackAnomalyNote }}</p>
    </div>

    <!-- 4 节点进度条：已接单 / 已上门 / 完工回传 / 待验收 -->
    <ol class="callback-track" aria-label="现场回传4节点进度">
      <li
        v-for="(node, idx) in callbackNodes"
        :key="node.id"
        class="track-node"
        :class="nodeStatusClass(idx)"
      >
        <div class="node-line-wrap">
          <span class="node-dot">{{ nodeIsDone(idx) ? '✓' : idx + 1 }}</span>
          <span v-if="idx < callbackNodes.length - 1" class="node-connector" :class="{ filled: nodeIsDone(idx) }"></span>
        </div>
        <div class="node-copy">
          <strong>{{ node.label }}</strong>
          <small class="node-time">{{ node.timeText }}</small>
        </div>
      </li>
    </ol>

    <!-- 完工回传结构化详情摘要 -->
    <div
      v-if="latestOrder && (latestOrder.replacedParts || latestOrder.oldPartNo || latestOrder.customerAcceptResult || latestOrder.feeOwner)"
      class="callback-summary"
    >
      <span v-if="latestOrder.replacedParts">更换件：<strong>{{ latestOrder.replacedParts }}</strong></span>
      <span v-if="latestOrder.oldPartNo">旧件编号：<strong class="mono">{{ latestOrder.oldPartNo }}</strong></span>
      <span v-if="latestOrder.customerAcceptResult">验收结果：<strong>{{ latestOrder.customerAcceptResult }}</strong></span>
      <span v-if="latestOrder.feeOwner">费用归属：<strong>{{ latestOrder.feeOwner }}</strong></span>
    </div>

    <!-- 模拟智享乐装回传触发器（V1.1 已派单且有权限） -->
    <div v-if="latestV11Order && canProcess && !isProblemTerminal" class="sim-callback-box">
      <div class="sim-head">
        <strong>模拟智享乐装回传触发器</strong>
        <span class="muted-note">一键模拟乐装事件回传，跑通「待回传 → 待验收」闭环</span>
      </div>

      <div class="sim-btn-row">
        <button
          class="sim-btn"
          type="button"
          :disabled="busy"
          @click="triggerSimCallback('accepted')"
        >已接单 accepted</button>
        <button
          class="sim-btn"
          type="button"
          :disabled="busy"
          @click="triggerSimCallback('arrived')"
        >已上门 arrived</button>
        <button
          class="sim-btn primary"
          type="button"
          :disabled="busy"
          @click="triggerSimCallback('completed')"
        >完工回传 completed</button>
      </div>

      <div class="sim-fields-grid">
        <label class="sim-field">
          <span>更换件</span>
          <input v-model="simForm.replacedParts" class="field-input compact" type="text" placeholder="如：交流接触器">
        </label>
        <label class="sim-field">
          <span>旧件编号</span>
          <input v-model="simForm.oldPartNo" class="field-input compact" type="text" placeholder="如：OP-20261125-01">
        </label>
        <label class="sim-field">
          <span>验收结果</span>
          <select v-model="simForm.customerAcceptResult" class="field-input compact">
            <option value="客户现场验收通过">客户现场验收通过</option>
            <option value="客户签字确认恢复">客户签字确认恢复</option>
            <option value="待二次复测">待二次复测</option>
          </select>
        </label>
        <label class="sim-field">
          <span>费用归属</span>
          <select v-model="simForm.feeOwner" class="field-input compact">
            <option value="厂家质保">厂家质保</option>
            <option value="客户自费">客户自费</option>
            <option value="服务商承担">服务商承担</option>
          </select>
        </label>
      </div>
      <label class="sim-field full">
        <span>回执说明（可选）</span>
        <input
          v-model="simForm.receipt"
          class="field-input compact"
          type="text"
          maxlength="300"
          placeholder="可选填写现场回执备注，不填则使用对应节点默认回执"
        >
      </label>
    </div>

    <!-- 兼容旧版现场单手工回执 -->
    <div v-else-if="legacyActiveOrder && canProcess" class="sim-callback-box">
      <div class="sim-head">
        <strong>快捷登记现场回执</strong>
        <span class="muted-note">现场单 {{ legacyActiveOrder.externalNo }} 进行中</span>
      </div>
      <form class="legacy-receipt-form" @submit.prevent="handleLegacyReceipt">
        <select v-model="legacyReceipt.state" class="field-input compact">
          <option value="in_progress" :disabled="legacyActiveOrder.state === 'in_progress'">已上门（处理中）</option>
          <option value="complete">完工回传</option>
          <option value="rejected">拒单</option>
          <option value="cancelled">取消</option>
        </select>
        <input
          v-model="legacyReceipt.receipt"
          class="field-input compact"
          type="text"
          maxlength="700"
          placeholder="填写现场处理结果或原因"
        >
        <button
          class="btn-primary compact-btn"
          type="submit"
          :disabled="busy || !legacyReceipt.receipt.trim() || legacyReceipt.state === legacyActiveOrder.state"
        >保存回执</button>
      </form>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { dateLabel } from '../../status'

const props = defineProps({
  problem: { type: Object, required: true },
  latestOrder: { type: Object, default: null },
  latestV11Order: { type: Object, default: null },
  legacyActiveOrder: { type: Object, default: null },
  busy: Boolean,
  canProcess: Boolean,
  isProblemTerminal: Boolean,
  firstPhotoName: { type: String, default: '' },
  fieldStateLabel: { type: Function, required: true }
})

const emit = defineEmits(['lz-callback', 'field-result'])

const simForm = reactive({
  replacedParts: '交流接触器模块',
  oldPartNo: 'OP-20261125-01',
  customerAcceptResult: '客户现场验收通过',
  feeOwner: '厂家质保',
  receipt: ''
})

const legacyReceipt = reactive({
  state: 'complete',
  receipt: ''
})

// 4 节点进度：已接单 / 已上门 / 完工回传 / 待验收
const callbackNodes = computed(() => {
  const order = props.latestOrder
  const acceptTime = order?.acceptedAt
    ? dateLabel(order.acceptedAt)
    : (order?.state === 'dispatched' ? '待乐装接单' : '--')
  const arriveTime = order?.arrivedAt ? dateLabel(order.arrivedAt) : '--'
  const completeTime = order?.completedAt ? dateLabel(order.completedAt) : '--'
  const verifyTime = (props.problem?.state === 'closed' || props.problem?.state === 'resolved')
    ? '已验收闭环'
    : (order?.state === 'completed' || order?.state === 'complete' || props.problem?.state === 'pending_accept' ? '待客服验收' : '--')

  return [
    { id: 'accepted', label: '已接单', timeText: acceptTime },
    { id: 'arrived', label: '已上门', timeText: arriveTime },
    { id: 'completed', label: '完工回传', timeText: completeTime },
    { id: 'verify', label: '待验收', timeText: verifyTime }
  ]
})

const callbackStage = computed(() => {
  const order = props.latestOrder
  if (!order) return { completedUpTo: -1, currentIdx: -1 }
  if (order.state === 'dispatched') return { completedUpTo: -1, currentIdx: 0 }
  if (order.state === 'accepted' || order.state === 'linked') return { completedUpTo: 0, currentIdx: 1 }
  if (order.state === 'arrived' || order.state === 'in_progress') return { completedUpTo: 1, currentIdx: 2 }
  if (order.state === 'completed' || order.state === 'complete') {
    if (props.problem?.state === 'closed' || props.problem?.state === 'resolved') {
      return { completedUpTo: 3, currentIdx: -1 }
    }
    return { completedUpTo: 2, currentIdx: 3 }
  }
  return { completedUpTo: -1, currentIdx: -1 }
})

function nodeIsDone(idx) {
  return idx <= callbackStage.value.completedUpTo
}

function nodeStatusClass(idx) {
  const { completedUpTo, currentIdx } = callbackStage.value
  return {
    done: idx <= completedUpTo && idx !== currentIdx,
    current: idx === currentIdx
  }
}

function isTerminalFieldState(state) {
  return state === 'rejected' || state === 'cancelled'
}

function triggerSimCallback(eventState) {
  if (!props.latestV11Order || !props.canProcess || props.busy || props.isProblemTerminal) return
  const externalNo = props.latestV11Order.externalNo
  const customReceipt = simForm.receipt.trim()
  if (eventState === 'accepted') {
    emit('lz-callback', {
      externalNo,
      state: 'accepted',
      receipt: customReceipt || '智享乐装已接单，已分配现场服务工程师'
    })
    return
  }
  if (eventState === 'arrived') {
    emit('lz-callback', {
      externalNo,
      state: 'arrived',
      receipt: customReceipt || '工程师已到达现场并核验故障桩体'
    })
    return
  }
  emit('lz-callback', {
    externalNo,
    state: 'completed',
    receipt: customReceipt || '现场更换备件并通电复测正常，已完工回传',
    replacedParts: simForm.replacedParts.trim() || '交流接触器模块',
    oldPartNo: simForm.oldPartNo.trim() || 'OP-20261125-01',
    oldPartPhoto: props.firstPhotoName || '旧件铭牌照.jpg',
    customerAcceptResult: simForm.customerAcceptResult || '客户现场验收通过',
    feeOwner: simForm.feeOwner || '厂家质保'
  })
}

function handleLegacyReceipt() {
  const receipt = legacyReceipt.receipt.trim()
  if (!props.legacyActiveOrder || !props.canProcess || props.busy || !receipt) return
  if (legacyReceipt.state === props.legacyActiveOrder.state) return
  emit('field-result', {
    externalNo: props.legacyActiveOrder.externalNo,
    state: legacyReceipt.state,
    receipt
  })
  legacyReceipt.receipt = ''
}
</script>

<style scoped>
.card-bottom {
  min-width: 0;
  padding: 18px 20px;
  background: var(--bg-surface, #ffffff);
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  box-shadow: var(--elev-1);
}

.bottom-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.bottom-head h3 {
  margin: 0;
  color: var(--heading, #0f172a);
  font-size: 15px;
  font-weight: 700;
}

.bottom-sub {
  margin: 3px 0 0;
  color: var(--muted, #8a96a5);
  font-size: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.mono {
  font-family: var(--font-mono, monospace);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.state-chip {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
}

.closed-pill {
  padding: 3px 8px;
  border-radius: 6px;
  background: #fdecec;
  color: #c0362c;
  font-size: 12px;
  font-weight: 600;
}

.anomaly-alert {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #fca5a5;
  background: #fef2f2;
  color: #991b1b;
  font-size: 12px;
}

.anomaly-alert strong { display: block; margin-bottom: 2px; }
.anomaly-alert p { margin: 0; }

.callback-track {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  list-style: none;
  margin: 0;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 10px;
}

.track-node { display: grid; gap: 6px; }
.node-line-wrap { display: flex; align-items: center; gap: 6px; }

.node-dot {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: 999px;
  background: var(--bg-surface, #fff);
  border: 1.5px solid var(--border-strong, #cfd7d7);
  color: var(--text-tertiary, #7a8686);
  font-family: var(--font-num, sans-serif);
  font-size: 12px;
  font-weight: 600;
}

.track-node.done .node-dot {
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  border: 1px solid var(--pine-bright, #0d9488);
}

.track-node.current .node-dot {
  background: var(--pine, #0f766e);
  color: #fff;
  box-shadow: 0 0 0 3px #0d948824;
}

.node-connector { flex: 1; height: 2px; border-radius: 2px; background: #e2e8f0; }
.node-connector.filled { background: var(--pine-bright, #0d9488); }
.node-copy strong { display: block; color: #64748b; font-size: 12px; }

.track-node.done .node-copy strong,
.track-node.current .node-copy strong { color: var(--heading, #0f172a); }

.node-time { display: block; color: var(--text-tertiary, #7a8686); font-size: 12px; font-variant-numeric: tabular-nums; }

.callback-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine-ink, #102c2b);
  font-size: 12px;
}

.sim-callback-box {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #eaeef2);
  display: grid;
  gap: 8px;
}

.sim-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.sim-head strong { color: var(--heading, #0f172a); font-size: 13px; }
.muted-note { color: var(--text-tertiary, #7a8686); font-size: 12px; }
.sim-btn-row { display: flex; flex-wrap: wrap; gap: 8px; }

.sim-btn {
  padding: 6px 12px;
  border: 1px solid #cce7e2;
  border-radius: 7px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.sim-btn:hover:not(:disabled) { background: #d7eeeb; }
.sim-btn.primary { border-color: var(--pine, #0f766e); background: var(--pine, #0f766e); color: #fff; }
.sim-btn.primary:hover:not(:disabled) { background: var(--pine-bright, #0d9488); }
.sim-btn:disabled { opacity: 0.55; cursor: not-allowed; }

.sim-fields-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.sim-field { display: grid; gap: 3px; min-width: 0; }
.sim-field span { font-size: 12px; color: var(--text-secondary, #4a5656); font-weight: 600; }

.field-input {
  width: 100%;
  min-width: 0;
  min-height: 36px;
  padding: 6px 10px;
  border: 1px solid #dce3ec;
  border-radius: 8px;
  background: #fff;
  color: var(--ink, #334155);
  font-size: 12px;
}

.field-input.compact { min-height: 32px; padding: 4px 8px; }
.field-input:focus { outline: none; border-color: var(--pine-bright, #0d9488); box-shadow: 0 0 0 3px #0d94881f; }

.legacy-receipt-form {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--pine, #0f766e);
  background: var(--pine, #0f766e);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.btn-primary:hover:not(:disabled) { background: var(--pine-bright, #0d9488); border-color: var(--pine-bright, #0d9488); }
.btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }
.compact-btn { min-height: 32px; padding: 4px 12px; }

@media (max-width: 1120px) { .sim-fields-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 760px) {
  .callback-track, .sim-fields-grid, .legacy-receipt-form { grid-template-columns: 1fr; }
  .node-connector { display: none; }
}
</style>
