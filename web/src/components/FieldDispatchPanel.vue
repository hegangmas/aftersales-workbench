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
      <!-- 2. 左栏：待派工单 -->
      <article class="card-col">
        <header class="col-head">
          <div>
            <span class="col-kicker">STEP 1 · 工单摘要</span>
            <h3>待派工单</h3>
          </div>
          <button
            class="refresh-btn"
            type="button"
            :disabled="busy"
            @click="refreshView"
          >
            <span aria-hidden="true">↻</span>
            <span>刷新</span>
          </button>
        </header>

        <p v-if="refreshTip" class="inline-feedback" role="status">{{ refreshTip }}</p>

        <div class="order-banner">
          <div class="order-banner-main">
            <span class="order-label">工单编号</span>
            <strong class="order-code">{{ orderCode }}</strong>
          </div>
          <span class="status-pill" :class="statusInfo.tone">
            <i aria-hidden="true"></i>{{ statusInfo.label }}
          </span>
        </div>

        <dl class="info-list">
          <div class="info-row">
            <dt>客户</dt>
            <dd>{{ resolvedCustomer }}</dd>
          </div>
          <div class="info-row">
            <dt>地址</dt>
            <dd>{{ resolvedAddress }}</dd>
          </div>
          <div class="info-row">
            <dt>联系人</dt>
            <dd class="contact-cell">
              <span>{{ resolvedContactName }}</span>
              <span class="phone-pill">{{ resolvedPhone }}</span>
            </dd>
          </div>
          <div class="info-row">
            <dt>桩SN</dt>
            <dd class="mono">{{ resolvedDeviceCode }}</dd>
          </div>
          <div class="info-row">
            <dt>故障</dt>
            <dd>
              <span>{{ problem.title || '未填写故障现象' }}</span>
              <span v-if="problem.category" class="mini-chip">{{ problem.category }}</span>
            </dd>
          </div>
          <div class="info-row">
            <dt>提交时间</dt>
            <dd class="mono">{{ submittedTimeText }}</dd>
          </div>
        </dl>

        <div class="remark-box">
          <div class="remark-head">
            <span>备注信息</span>
            <span v-if="disposalText" class="mini-chip pine">{{ disposalText }}</span>
          </div>
          <p class="remark-text">{{ problem.description || '暂无故障描述与补充备注' }}</p>
        </div>
      </article>

      <!-- 3. 中栏：自动带入（只读） ⓘ -->
      <article class="card-col">
        <header class="col-head">
          <div class="head-with-tip">
            <div>
              <span class="col-kicker">STEP 1 · 只读同步</span>
              <h3 class="title-inline">
                <span>自动带入（只读）</span>
                <span class="info-mark" title="以下字段由受理建单自动带入，只读锁定不可修改" aria-label="只读说明">ⓘ</span>
              </h3>
            </div>
          </div>
          <span class="lock-badge">🔒 已锁定</span>
        </header>

        <div class="readonly-stack">
          <div v-for="field in readonlyFields" :key="field.key" class="readonly-field">
            <label class="readonly-label" :for="'ro-' + field.key">{{ field.label }}</label>
            <div class="readonly-box">
              <input
                :id="'ro-' + field.key"
                class="readonly-input"
                type="text"
                :value="field.value"
                readonly
                tabindex="-1"
                :aria-label="field.label + '（只读）'"
              >
              <span class="box-lock" aria-hidden="true">🔒</span>
            </div>
          </div>
        </div>

        <p class="sync-tip" role="note">
          <span aria-hidden="true">ⓘ</span>
          <span>以上信息由受理建单自动带入</span>
        </p>
      </article>

      <!-- 4. 右栏：需补填字段 -->
      <article class="card-col">
        <header class="col-head">
          <div>
            <span class="col-kicker">STEP 2 · 补全字段</span>
            <h3>需补填字段</h3>
          </div>
          <span class="required-hint"><em class="req">*</em> 请完善以下信息后再提交</span>
        </header>

        <div v-if="isDevicePending" class="state-alert danger" role="alert">
          <strong>设备信息待补，禁止派单</strong>
          <p>请先返回工作台补齐设备桩 SN 档案后，再执行派单至智享乐装。</p>
        </div>
        <div v-else-if="latestV11Order" class="state-alert info" role="status">
          <strong>已派单至智享乐装（单号：{{ latestV11Order.externalNo }}）</strong>
          <p>工单当前状态为「{{ fieldStateLabel(latestV11Order.state) }}」，可在下方回传状态区模拟推进。</p>
        </div>
        <div v-else-if="needsClaimBeforeDispatch" class="state-alert" role="status">
          <strong>当前工单处于「待分配」状态</strong>
          <p>请先返回工作台点击「承接处理」进入处理中，再确认派单。</p>
        </div>

        <form id="dispatch-v11-form" class="dispatch-form" @submit.prevent="handleDispatchSubmit">
          <div class="form-group">
            <label class="form-label" for="dispatch-is-claim">
              是否索赔 <em class="req">*</em>
            </label>
            <select
              id="dispatch-is-claim"
              v-model="form.isClaim"
              class="field-input"
              :disabled="fieldsLocked"
            >
              <option value="">请选择</option>
              <option value="yes">是</option>
              <option value="no">否</option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label" for="dispatch-return-factory">
              桩是否返厂 <em class="req">*</em>
            </label>
            <select
              id="dispatch-return-factory"
              v-model="form.returnToFactory"
              class="field-input"
              :disabled="fieldsLocked"
            >
              <option value="">请选择</option>
              <option value="yes">是</option>
              <option value="no">否</option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label" for="dispatch-project-code">
              项目编号 <em class="req">*</em>
            </label>
            <input
              id="dispatch-project-code"
              v-model="form.projectCode"
              class="field-input"
              type="text"
              maxlength="64"
              :disabled="fieldsLocked"
              placeholder="默认带入设备项目编号，如 PRJ-2026-AH01"
            >
          </div>

          <div class="form-group">
            <label class="form-label" for="dispatch-visit-time">
              预约上门时间 <em class="req">*</em>
            </label>
            <input
              id="dispatch-visit-time"
              v-model="form.appointmentAt"
              class="field-input"
              type="datetime-local"
              :disabled="fieldsLocked"
            >
          </div>

          <!-- 换装照片与旧件 -->
          <div class="upload-section">
            <div class="upload-head">
              <span class="form-label">换装照片与旧件</span>
              <span class="upload-count">{{ uploadedFiles.length }}/3</span>
            </div>
            <p class="upload-hint">支持jpg/png/pdf，单个文件不超过10MB，最多上传3张</p>

            <input
              id="dispatch-file-input"
              ref="fileInputRef"
              class="sr-only"
              type="file"
              accept=".jpg,.jpeg,.png,.pdf"
              multiple
              :disabled="fieldsLocked || uploadedFiles.length >= 3"
              @change="onFilesSelected"
            >

            <div
              class="dropzone"
              :class="{ dragging: isDragging, disabled: fieldsLocked || uploadedFiles.length >= 3 }"
              role="button"
              tabindex="0"
              @click="triggerFilePicker"
              @keydown.enter.prevent="triggerFilePicker"
              @keydown.space.prevent="triggerFilePicker"
              @dragover.prevent="onDragOver"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="onDropFiles"
            >
              <span class="drop-icon" aria-hidden="true">⬆</span>
              <span class="drop-text">
                {{ uploadedFiles.length >= 3 ? '已达到最多 3 张上传上限' : '点击或拖拽文件到此处上传' }}
              </span>
            </div>

            <p v-if="uploadError" class="form-error" role="alert">{{ uploadError }}</p>

            <div class="file-grid">
              <div v-for="(file, idx) in uploadedFiles" :key="file.id" class="file-card">
                <div class="file-card-top">
                  <span class="file-tag">{{ file.extLabel }}</span>
                  <button
                    v-if="!fieldsLocked"
                    class="remove-file"
                    type="button"
                    aria-label="移除文件"
                    @click="removeFile(idx)"
                  >×</button>
                </div>
                <strong class="file-name" :title="file.name">{{ file.name }}</strong>
                <small class="file-meta">{{ file.sizeText }}</small>
              </div>

              <button
                v-if="!fieldsLocked && uploadedFiles.length < 3"
                type="button"
                class="file-slot-btn"
                @click="triggerFilePicker"
              >
                + 上传第{{ nextUploadSlotIndex }}张
              </button>
            </div>
          </div>

          <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
        </form>
      </article>
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
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { dateLabel, displayStatus, disposalLabel, orderNumber } from '../status'

const props = defineProps({
  problem: { type: Object, required: true },
  device: { type: Object, default: null },
  busy: Boolean,
  canProcess: Boolean
})

const emit = defineEmits(['link', 'field-result', 'dispatch-v11', 'lz-callback', 'back'])

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
const MAX_FILES = 3
const MAX_FILE_BYTES = 10 * 1024 * 1024
const ALLOWED_EXT = /\.(jpe?g|png|pdf)$/i

const form = reactive({
  isClaim: '',
  returnToFactory: '',
  projectCode: '',
  appointmentAt: ''
})

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

const fileInputRef = ref(null)
const uploadedFiles = ref([])
const isDragging = ref(false)
const uploadError = ref('')
const formError = ref('')
const refreshTip = ref('')
const auxTip = ref('')
const projectTouched = ref(false)

const snapshot = computed(() => {
  const raw = props.problem?.snapshotJson
  if (!raw) return null
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch (ignore) {
    return null
  }
})

const isDevicePending = computed(() =>
  Number(props.problem?.devicePending || 0) === 1 || props.problem?.devicePending === true
)

const isProblemTerminal = computed(() =>
  ['closed', 'cancelled', 'resolved'].includes(props.problem?.state)
)

const orderCode = computed(() => orderNumber(props.problem) || '未生成')
const statusInfo = computed(() => displayStatus(props.problem))
const disposalText = computed(() => disposalLabel(props.problem?.disposalType) || '')

const resolvedCustomer = computed(() =>
  props.device?.customerName || snapshot.value?.customerName || props.problem?.customerName || '合肥滨湖示例充电站（模拟）'
)
const resolvedAddress = computed(() =>
  props.device?.siteAddress || snapshot.value?.siteAddress || props.problem?.siteAddress || '合肥市滨湖新区示例小区 3 栋地下车库（模拟）'
)
const resolvedContactName = computed(() =>
  props.device?.contactName || snapshot.value?.contactName || props.problem?.contactName || '王经理'
)
const resolvedPhone = computed(() =>
  props.device?.customerPhone || props.device?.contactPhone || snapshot.value?.customerPhone || props.problem?.contactPhone || '19900000001'
)
const resolvedDeviceCode = computed(() =>
  props.problem?.device || props.device?.device || snapshot.value?.device || (isDevicePending.value ? '设备信息待补' : '未关联')
)
const resolvedProject = computed(() =>
  props.device?.projectName || snapshot.value?.projectName || props.problem?.projectName || '合肥滨湖示范站运维项目（模拟）'
)
const defaultProjectCode = computed(() =>
  props.device?.projectCode || snapshot.value?.projectCode || ''
)

const submittedTimeText = computed(() => {
  const stamp = props.problem?.createdAt || props.problem?.faultOccurredAt
  return stamp ? dateLabel(stamp) : '刚刚'
})

const readonlyFields = computed(() => [
  { key: 'customer', label: '客户名称', value: resolvedCustomer.value },
  { key: 'address', label: '安装地址', value: resolvedAddress.value },
  { key: 'device', label: '桩编码', value: resolvedDeviceCode.value },
  { key: 'phone', label: '联系电话', value: resolvedPhone.value },
  { key: 'project', label: '所属项目', value: resolvedProject.value }
])

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

const isFormComplete = computed(() =>
  Boolean(form.isClaim && form.returnToFactory && form.projectCode.trim() && form.appointmentAt)
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

const nextUploadSlotIndex = computed(() =>
  Math.min(MAX_FILES, uploadedFiles.value.length + 1)
)

// 4 节点进度：已接单 / 已上门 / 完工回传 / 待验收
const callbackNodes = computed(() => {
  const order = latestOrder.value
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
  const order = latestOrder.value
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

function fieldStateLabel(state) {
  return fieldStateTextMap[state] || state || '未知'
}

function isTerminalFieldState(state) {
  return state === 'rejected' || state === 'cancelled'
}

function formatSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '已同步'
  if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024)) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function fileExtLabel(name) {
  const ext = String(name || '').split('.').pop().toUpperCase()
  return ext || '文件'
}

function triggerFilePicker() {
  if (fieldsLocked.value || uploadedFiles.value.length >= MAX_FILES) return
  fileInputRef.value?.click()
}

function appendFiles(fileList) {
  uploadError.value = ''
  const files = Array.from(fileList || [])
  if (!files.length) return
  for (const file of files) {
    if (uploadedFiles.value.length >= MAX_FILES) {
      uploadError.value = '最多上传 3 张文件'
      break
    }
    if (!ALLOWED_EXT.test(file.name)) {
      uploadError.value = `文件「${file.name}」格式不支持，仅支持 jpg/png/pdf`
      continue
    }
    if (file.size > MAX_FILE_BYTES) {
      uploadError.value = `文件「${file.name}」超过 10MB 限制`
      continue
    }
    uploadedFiles.value.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}-${file.name}`,
      name: file.name,
      sizeText: formatSize(file.size),
      extLabel: fileExtLabel(file.name)
    })
  }
}

function onFilesSelected(event) {
  appendFiles(event.target?.files)
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function onDragOver() {
  if (!fieldsLocked.value && uploadedFiles.value.length < MAX_FILES) {
    isDragging.value = true
  }
}

function onDropFiles(event) {
  isDragging.value = false
  if (fieldsLocked.value || uploadedFiles.value.length >= MAX_FILES) return
  appendFiles(event.dataTransfer?.files)
}

function removeFile(idx) {
  uploadedFiles.value.splice(idx, 1)
  uploadError.value = ''
}

function toDatetimeLocalValue(val) {
  if (!val) return ''
  const norm = String(val).trim().replace(' ', 'T')
  return norm.slice(0, 16)
}

function syncFromProps() {
  formError.value = ''
  uploadError.value = ''
  auxTip.value = ''
  const order = latestV11Order.value
  if (order) {
    form.isClaim = Number(order.isClaim) === 1 || order.isClaim === true ? 'yes' : 'no'
    form.returnToFactory = Number(order.returnToFactory) === 1 || order.returnToFactory === true ? 'yes' : 'no'
    form.projectCode = order.projectCode || defaultProjectCode.value || ''
    form.appointmentAt = toDatetimeLocalValue(order.appointmentAt)
    const names = String(order.photoNames || '').split(',').map(s => s.trim()).filter(Boolean)
    uploadedFiles.value = names.slice(0, MAX_FILES).map((name, idx) => ({
      id: `saved-${idx}-${name}`,
      name,
      sizeText: '已随单同步',
      extLabel: fileExtLabel(name)
    }))
  } else {
    if (!projectTouched.value || !form.projectCode) {
      form.projectCode = defaultProjectCode.value || ''
    }
  }
}

function refreshView() {
  syncFromProps()
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

function handleDispatchSubmit() {
  formError.value = ''
  if (confirmDisabled.value) return
  if (!form.isClaim) {
    formError.value = '请选择「是否索赔」'
    return
  }
  if (!form.returnToFactory) {
    formError.value = '请选择「桩是否返厂」'
    return
  }
  if (!form.projectCode.trim()) {
    formError.value = '请填写「项目编号」'
    return
  }
  if (!form.appointmentAt) {
    formError.value = '请选择「预约上门时间」'
    return
  }
  emit('dispatch-v11', {
    isClaim: form.isClaim === 'yes',
    returnToFactory: form.returnToFactory === 'yes',
    projectCode: form.projectCode.trim(),
    appointmentAt: form.appointmentAt,
    photoNames: uploadedFiles.value.map(f => f.name).join(',')
  })
}

function triggerSimCallback(eventState) {
  if (!latestV11Order.value || !props.canProcess || props.busy || isProblemTerminal.value) return
  const externalNo = latestV11Order.value.externalNo
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
    oldPartPhoto: uploadedFiles.value[0]?.name || '旧件铭牌照.jpg',
    customerAcceptResult: simForm.customerAcceptResult || '客户现场验收通过',
    feeOwner: simForm.feeOwner || '厂家质保'
  })
}

function handleLegacyReceipt() {
  const receipt = legacyReceipt.receipt.trim()
  if (!legacyActiveOrder.value || !props.canProcess || props.busy || !receipt) return
  if (legacyReceipt.state === legacyActiveOrder.value.state) return
  emit('field-result', {
    externalNo: legacyActiveOrder.value.externalNo,
    state: legacyReceipt.state,
    receipt
  })
  legacyReceipt.receipt = ''
}

watch(
  () => form.projectCode,
  val => {
    if (val && val !== defaultProjectCode.value) {
      projectTouched.value = true
    }
  }
)

watch(
  () => [props.problem?.id, props.problem?.version, props.device?.projectCode],
  ([newId], oldVals) => {
    const oldId = oldVals ? oldVals[0] : undefined
    if (newId !== oldId) {
      form.isClaim = ''
      form.returnToFactory = ''
      form.projectCode = ''
      form.appointmentAt = ''
      uploadedFiles.value = []
      projectTouched.value = false
      refreshTip.value = ''
      auxTip.value = ''
    }
    syncFromProps()
  },
  { immediate: true }
)
</script>

<style scoped>
.dispatch-shell {
  display: grid;
  gap: 14px;
  color: var(--ink, #334155);
}

/* 顶部步骤与返回栏 */
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

.back-btn:hover {
  background: #d7eeeb;
}

.top-title-group {
  min-width: 0;
}

.top-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.top-title-row h2 {
  margin: 0;
  color: var(--heading, #0f172a);
  font-size: 18px;
  font-weight: 700;
}

.badge-soft {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 11px;
  font-weight: 600;
}

.top-subtitle {
  margin: 2px 0 0;
  color: var(--muted, #8a96a5);
  font-size: 12px;
}

.step-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  list-style: none;
  margin: 0;
  padding: 8px 14px;
  background: #f8fafc;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 10px;
}

.step-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--muted, #8a96a5);
}

.step-num {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
}

.step-item.done .step-num {
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  border: 1px solid var(--pine-bright, #0d9488);
}

.step-item.active .step-num {
  background: var(--pine, #0f766e);
  color: #fff;
  box-shadow: 0 0 0 3px #0d948824;
}

.step-item.done .step-text,
.step-item.active .step-text {
  color: var(--heading, #0f172a);
  font-weight: 600;
}

.step-arrow {
  margin-left: 2px;
  color: #cbd5e1;
}

/* 三栏主区域 */
.dispatch-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.95fr) minmax(260px, 0.95fr) minmax(320px, 1.15fr);
  gap: 14px;
  align-items: stretch;
}

.card-col,
.card-bottom {
  min-width: 0;
  padding: 18px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 12px;
  box-shadow: 0 4px 14px #132c4908;
}

.col-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--line, #eaeef2);
}

.col-kicker {
  display: block;
  color: var(--pine, #0f766e);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.col-head h3 {
  margin: 2px 0 0;
  color: var(--heading, #0f172a);
  font-size: 15px;
  font-weight: 700;
}

.title-inline {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.info-mark {
  display: inline-grid;
  place-items: center;
  color: var(--pine, #0f766e);
  font-size: 13px;
  font-weight: 600;
  cursor: help;
}

.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid #dce3ec;
  border-radius: 7px;
  background: #fff;
  color: var(--ink, #334155);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.refresh-btn:hover:not(:disabled) {
  border-color: var(--pine, #0f766e);
  color: var(--pine, #0f766e);
  background: var(--pine-soft, #e8f6f4);
}

.inline-feedback {
  margin: 0 0 10px;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 12px;
}

/* 左栏：待派工单 */
.order-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--pine-soft, #e8f6f4);
  border: 1px solid #cce9e4;
  margin-bottom: 12px;
}

.order-banner-main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.order-label {
  color: var(--pine, #0f766e);
  font-size: 11px;
  font-weight: 600;
}

.order-code {
  color: var(--pine-ink, #102c2b);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1.2;
}

/* 状态胶囊 */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.status-pill i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-pill.processing { background: #e6f7f5; color: #0f766e; }
.status-pill.waiting { background: #fef3c7; color: #b45309; }
.status-pill.dispatch { background: #e8f6f4; color: #0f766e; }
.status-pill.onsite { background: #e0f2fe; color: #0369a1; }
.status-pill.verify { background: #f3e8ff; color: #7e22ce; }
.status-pill.closed { background: #f1f5f9; color: #475569; }
.status-pill.triage { background: #fce7f3; color: #9d174d; }
.status-pill.follow { background: #ecfccb; color: #3f6212; }

.info-list {
  display: grid;
  gap: 6px;
  margin: 0 0 14px;
}

.info-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px solid #f1f5f9;
  align-items: center;
}

.info-row dt {
  color: var(--muted, #8a96a5);
  font-size: 12px;
}

.info-row dd {
  margin: 0;
  color: var(--heading, #0f172a);
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.contact-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.phone-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.remark-box {
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid var(--line, #eaeef2);
}

.remark-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--muted, #8a96a5);
  font-size: 11px;
  font-weight: 600;
}

.remark-text {
  margin: 0;
  color: var(--ink, #334155);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.mini-chip {
  padding: 2px 7px;
  border-radius: 4px;
  background: #eef2f6;
  color: #475569;
  font-size: 11px;
  font-weight: 500;
}

.mini-chip.pine {
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
}

/* 中栏：自动带入（只读） */
.lock-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
}

.readonly-stack {
  display: grid;
  gap: 11px;
}

.readonly-field {
  display: grid;
  gap: 4px;
}

.readonly-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.readonly-box {
  position: relative;
  display: flex;
  align-items: center;
}

.readonly-input {
  width: 100%;
  min-height: 36px;
  padding: 7px 32px 7px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  cursor: default;
  text-overflow: ellipsis;
}

.readonly-input:focus {
  outline: none;
  box-shadow: none;
  border-color: #e2e8f0;
}

.box-lock {
  position: absolute;
  right: 10px;
  font-size: 12px;
  opacity: 0.7;
  pointer-events: none;
}

.sync-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 14px 0 0;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 12px;
}

/* 右栏：需补填字段（单列纵向） */
.required-hint {
  font-size: 11px;
  color: var(--muted, #8a96a5);
  white-space: nowrap;
}

.req {
  font-style: normal;
  color: #c0362c;
  font-weight: 700;
}

.state-alert {
  padding: 10px 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  border: 1px solid #f3e2b8;
  background: #fff8e8;
  color: #8a5a00;
  font-size: 12px;
}

.state-alert.info {
  border-color: #cce9e4;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine-ink, #102c2b);
}

.state-alert.danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.state-alert strong {
  display: block;
  margin-bottom: 2px;
}

.state-alert p {
  margin: 0;
  line-height: 1.5;
}

.dispatch-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-group {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.form-label {
  color: var(--ink, #334155);
  font-size: 12px;
  font-weight: 600;
}

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

.field-input.compact {
  min-height: 32px;
  padding: 4px 8px;
}

.field-input:focus {
  outline: none;
  border-color: var(--pine-bright, #0d9488);
  box-shadow: 0 0 0 3px #0d94881f;
}

.field-input:disabled {
  background: #f1f5f9;
  color: #64748b;
  cursor: not-allowed;
}

/* 上传区 */
.upload-section {
  display: grid;
  gap: 6px;
  padding-top: 4px;
}

.upload-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.upload-count {
  font-size: 11px;
  color: var(--muted, #8a96a5);
  font-variant-numeric: tabular-nums;
}

.upload-hint {
  margin: 0;
  font-size: 11px;
  color: var(--muted, #8a96a5);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}

.dropzone {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 52px;
  padding: 10px 12px;
  border: 1.5px dashed #a4d2c9;
  border-radius: 8px;
  background: #f8fcfb;
  color: var(--pine, #0f766e);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.dropzone:hover:not(.disabled),
.dropzone.dragging {
  background: var(--pine-soft, #e8f6f4);
  border-color: var(--pine, #0f766e);
}

.dropzone.disabled {
  border-color: #dce3ec;
  background: #f8fafc;
  color: #94a3b8;
  cursor: not-allowed;
}

.drop-icon {
  font-size: 13px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 2px;
}

.file-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding: 8px 9px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
  background: #fff;
}

.file-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.file-tag {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 10px;
  font-weight: 600;
}

.remove-file {
  border: 0;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  line-height: 1;
  padding: 0 2px;
  cursor: pointer;
}

.remove-file:hover {
  color: #c0362c;
}

.file-name {
  font-size: 11px;
  color: var(--heading, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  color: var(--muted, #8a96a5);
  font-size: 10px;
}

.file-slot-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60px;
  padding: 8px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--pine, #0f766e);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  text-align: center;
}

.file-slot-btn:hover {
  border-color: var(--pine, #0f766e);
  background: var(--pine-soft, #e8f6f4);
}

.form-error {
  margin: 0;
  color: #c0362c;
  font-size: 12px;
  font-weight: 600;
}

/* 底部左右分栏 */
.bottom-split {
  display: grid;
  grid-template-columns: minmax(320px, 0.92fr) minmax(420px, 1.4fr);
  gap: 14px;
  align-items: start;
}

.bottom-card-head h3,
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

.btn-primary {
  border: 1px solid var(--pine, #0f766e);
  background: var(--pine, #0f766e);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--pine-bright, #0d9488);
  border-color: var(--pine-bright, #0d9488);
}

.btn-secondary {
  border: 1px solid #dce3ec;
  background: #fff;
  color: var(--ink, #334155);
}

.btn-secondary:hover:not(:disabled) {
  border-color: var(--pine, #0f766e);
  color: var(--pine, #0f766e);
}

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.compact-btn {
  min-height: 32px;
  padding: 4px 12px;
}

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

.perm-tip {
  margin: 10px 0 0;
  color: #b45309;
  font-size: 12px;
}

.aux-tip {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 12px;
}

/* 底部右卡：回传状态 */
.bottom-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.state-chip {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 11px;
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

.anomaly-alert strong {
  display: block;
  margin-bottom: 2px;
}

.anomaly-alert p {
  margin: 0;
}

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

.track-node {
  display: grid;
  gap: 6px;
}

.node-line-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.node-dot {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: 999px;
  background: #e2e8f0;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
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

.node-connector {
  flex: 1;
  height: 2px;
  border-radius: 2px;
  background: #e2e8f0;
}

.node-connector.filled {
  background: var(--pine-bright, #0d9488);
}

.node-copy strong {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.track-node.done .node-copy strong,
.track-node.current .node-copy strong {
  color: var(--heading, #0f172a);
}

.node-time {
  display: block;
  color: var(--muted, #8a96a5);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

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

/* 模拟智享乐装回传触发器 */
.sim-callback-box {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #eaeef2);
  display: grid;
  gap: 8px;
}

.sim-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.sim-head strong {
  color: var(--heading, #0f172a);
  font-size: 13px;
}

.muted-note {
  color: var(--muted, #8a96a5);
  font-size: 11px;
}

.sim-btn-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

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

.sim-btn:hover:not(:disabled) {
  background: #d7eeeb;
}

.sim-btn.primary {
  border-color: var(--pine, #0f766e);
  background: var(--pine, #0f766e);
  color: #fff;
}

.sim-btn.primary:hover:not(:disabled) {
  background: var(--pine-bright, #0d9488);
}

.sim-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.sim-fields-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.sim-field {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.sim-field span {
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
}

.legacy-receipt-form {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

@media (max-width: 1120px) {
  .dispatch-grid {
    grid-template-columns: 1fr 1fr;
  }
  .dispatch-grid > :last-child {
    grid-column: 1 / -1;
  }
  .bottom-split {
    grid-template-columns: 1fr;
  }
  .sim-fields-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
  .dispatch-grid,
  .file-grid,
  .left-btn-group,
  .callback-track,
  .sim-fields-grid,
  .legacy-receipt-form {
    grid-template-columns: 1fr;
  }
  .dispatch-grid > :last-child {
    grid-column: auto;
  }
  .node-connector {
    display: none;
  }
}
</style>
