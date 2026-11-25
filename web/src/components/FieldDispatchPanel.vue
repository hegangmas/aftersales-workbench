<template>
  <section class="dispatch-shell" aria-label="派单至智享乐装">
    <header class="dispatch-top">
      <div class="top-left">
        <button class="back-btn" type="button" @click="$emit('back')">
          <svg viewBox="0 0 20 20" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path d="M12.5 4.5 7 10l5.5 5.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>返回工作台</span>
        </button>
        <div class="top-title-group">
          <div class="top-title-row">
            <h2>派单至智享乐装</h2>
            <span class="badge-soft">双端字段自动带入</span>
          </div>
          <p class="top-subtitle">核对受理单信息、补填现场派单字段并关联智享乐装工单回传状态</p>
        </div>
      </div>

      <ol class="step-bar" aria-label="派单流程步骤">
        <li
          v-for="(step, idx) in dispatchSteps"
          :key="step.id"
          class="step-item"
          :class="{ done: idx < activeDispatchStep, active: idx === activeDispatchStep }"
        >
          <span class="step-num">{{ idx < activeDispatchStep ? '✓' : idx + 1 }}</span>
          <span class="step-text">{{ step.label }}</span>
          <span v-if="idx < dispatchSteps.length - 1" class="step-arrow" aria-hidden="true">→</span>
        </li>
      </ol>
    </header>

    <div class="dispatch-grid">
      <!-- 左栏：待派工单 -->
      <article class="card-col">
        <header class="col-head">
          <div>
            <span class="col-kicker">STEP 1 · 核对信息</span>
            <h3>待派工单</h3>
          </div>
          <span class="status-pill" :class="statusInfo.tone">
            <i aria-hidden="true"></i>{{ statusInfo.label }}
          </span>
        </header>

        <div class="order-banner">
          <span class="order-label">问题单号</span>
          <strong class="order-code">{{ orderCode }}</strong>
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
            <dd>{{ resolvedContact }}</dd>
          </div>
          <div class="info-row">
            <dt>桩 SN</dt>
            <dd class="mono">{{ resolvedDeviceCode }}</dd>
          </div>
        </dl>

        <div class="fault-summary">
          <div class="fault-head">
            <span>故障标题与描述摘要</span>
            <span v-if="problem.category" class="mini-chip">{{ problem.category }}</span>
          </div>
          <strong class="fault-title">{{ problem.title || '未填写故障标题' }}</strong>
          <p class="fault-desc">{{ problem.description || '暂无故障现象描述' }}</p>
          <div v-if="problem.grade || problem.disposalType" class="fault-meta">
            <span v-if="problem.grade" class="mini-chip pine">{{ problem.grade }} 级证据</span>
            <span v-if="problem.disposalType" class="mini-chip">{{ disposalText }}</span>
          </div>
        </div>
      </article>

      <!-- 中栏：自动带入（只读） -->
      <article class="card-col">
        <header class="col-head">
          <div>
            <span class="col-kicker">STEP 1 · 只读同步</span>
            <h3>自动带入（只读）</h3>
          </div>
          <span class="lock-badge">
            <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
              <rect x="3.5" y="7" width="9" height="6.5" rx="1.5" />
              <path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2" />
            </svg>
            已锁定
          </span>
        </header>

        <div class="readonly-stack">
          <div v-for="field in readonlyFields" :key="field.key" class="readonly-field">
            <div class="readonly-label">
              <span>{{ field.label }}</span>
              <span class="field-lock" title="只读锁定">
                <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
                  <rect x="3.5" y="7" width="9" height="6.5" rx="1.5" />
                  <path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2" />
                </svg>
                只读
              </span>
            </div>
            <input class="readonly-input" type="text" :value="field.value" readonly tabindex="-1" :aria-label="field.label + '（只读）'">
          </div>
        </div>

        <p class="sync-tip" role="note">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
            <circle cx="8" cy="8" r="6.2" />
            <path d="M8 7.2v3.6M8 4.9h.01" stroke-linecap="round" />
          </svg>
          <span>以上信息由受理建单自动带入，避免两端重复建档</span>
        </p>
      </article>

      <!-- 右栏：需补填字段 -->
      <article class="card-col">
        <header class="col-head">
          <div>
            <span class="col-kicker">STEP 2 · 补全字段</span>
            <h3>需补填字段</h3>
          </div>
          <span v-if="activeFieldOrder" class="active-order-tag">已关联 {{ activeFieldOrder.externalNo }}</span>
          <span v-else-if="!isFieldState" class="preview-tag">预填/预览模式</span>
        </header>

        <div v-if="!isFieldState" class="state-alert" role="status">
          <strong>当前工单尚未进入「待现场派单」状态</strong>
          <p>请先在回访结果选择「待现场派单」后再正式关联；您仍可在此填写字段进行模拟填报预览。</p>
        </div>
        <div v-else-if="activeFieldOrder" class="state-alert info" role="status">
          <strong>已关联智享乐装现场单：{{ activeFieldOrder.externalNo }}</strong>
          <p>当前现场单处于「{{ fieldStateLabel(activeFieldOrder.state) }}」状态，请在下方回传进度区登记最新现场回执。</p>
        </div>

        <form class="dispatch-form" @submit.prevent="handleSubmitDispatch">
          <div class="bool-grid">
            <div class="form-group">
              <span class="form-label">是否索赔</span>
              <div class="choice-pills" role="radiogroup" aria-label="是否索赔">
                <button
                  type="button"
                  class="choice-pill"
                  :class="{ selected: form.isClaim === '是' }"
                  :aria-pressed="form.isClaim === '是'"
                  @click="form.isClaim = '是'"
                >是</button>
                <button
                  type="button"
                  class="choice-pill"
                  :class="{ selected: form.isClaim === '否' }"
                  :aria-pressed="form.isClaim === '否'"
                  @click="form.isClaim = '否'"
                >否</button>
              </div>
            </div>

            <div class="form-group">
              <span class="form-label">桩是否返厂</span>
              <div class="choice-pills" role="radiogroup" aria-label="桩是否返厂">
                <button
                  type="button"
                  class="choice-pill"
                  :class="{ selected: form.returnToFactory === '是' }"
                  :aria-pressed="form.returnToFactory === '是'"
                  @click="form.returnToFactory = '是'"
                >是</button>
                <button
                  type="button"
                  class="choice-pill"
                  :class="{ selected: form.returnToFactory === '否' }"
                  :aria-pressed="form.returnToFactory === '否'"
                  @click="form.returnToFactory = '否'"
                >否</button>
              </div>
            </div>
          </div>

          <div class="two-col-inputs">
            <div class="form-group">
              <label class="form-label" for="dispatch-project-code">项目编号</label>
              <input
                id="dispatch-project-code"
                v-model="form.projectCode"
                class="field-input"
                type="text"
                maxlength="64"
                placeholder="如：PRJ-2026-AH01"
              >
            </div>
            <div class="form-group">
              <label class="form-label" for="dispatch-visit-time">预约上门时间</label>
              <input
                id="dispatch-visit-time"
                v-model="form.appointmentAt"
                class="field-input"
                type="datetime-local"
              >
            </div>
          </div>

          <div class="form-group">
            <label class="form-label" for="dispatch-external-no">
              智享乐装工单号 <em class="req">*</em>
            </label>
            <input
              id="dispatch-external-no"
              v-model="form.externalNo"
              class="field-input"
              type="text"
              maxlength="64"
              :disabled="Boolean(activeFieldOrder)"
              placeholder="输入智享乐装工单号，如 ZXLZ-20261125-001"
            >
          </div>

          <!-- 换装照片与旧件上传区 -->
          <div class="upload-section">
            <div class="upload-head">
              <span class="form-label">换装照片与旧件上传区</span>
              <label class="upload-trigger" for="dispatch-file-input">
                <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
                  <path d="M8 10.5V3M5.2 5.5 8 2.7l2.8 2.8M2.5 11.5v1.3a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1v-1.3" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <span>选择文件</span>
              </label>
              <input
                id="dispatch-file-input"
                ref="fileInputRef"
                class="sr-only"
                type="file"
                multiple
                @change="onFilesSelected"
              >
            </div>

            <div v-if="uploadedFiles.length" class="file-grid">
              <div v-for="(file, idx) in uploadedFiles" :key="file.id" class="file-card">
                <div class="file-card-top">
                  <span class="file-tag">已选择</span>
                  <button class="remove-file" type="button" aria-label="移除文件" @click="removeFile(idx)">×</button>
                </div>
                <strong class="file-name" :title="file.name">{{ file.name }}</strong>
                <small class="file-meta">{{ file.sizeText }} · 待同步至乐装</small>
              </div>
            </div>

            <div v-else class="file-grid">
              <div class="file-card placeholder">
                <span class="file-tag muted">模拟占位</span>
                <strong class="file-name">换装照片.jpg</strong>
                <small class="file-meta">新桩/换件安装位照片</small>
              </div>
              <div class="file-card placeholder">
                <span class="file-tag muted">模拟占位</span>
                <strong class="file-name">旧件照片.jpg</strong>
                <small class="file-meta">拆下旧件铭牌与外观照</small>
              </div>
            </div>
          </div>

          <div class="form-actions">
            <button
              v-if="canLinkDirectly"
              class="btn-primary"
              type="submit"
              :disabled="busy || !canProcess || !form.externalNo.trim()"
            >
              {{ busy ? '提交关联中…' : '确认派单并关联' }}
            </button>
            <button
              v-else-if="!isFieldState"
              class="btn-secondary"
              type="button"
              :disabled="busy"
              @click="showPreview = !showPreview"
            >
              {{ showPreview ? '收起模拟填报预览' : '模拟填报预览' }}
            </button>
            <span v-if="canLinkDirectly && !canProcess" class="perm-tip">当前账号无处理权限，请先认领或联系负责人</span>
          </div>

          <div v-if="showPreview && !isFieldState" class="preview-box" role="region" aria-label="模拟填报预览">
            <strong>模拟填报预览（尚未写入智享乐装）</strong>
            <p>
              问题单 {{ orderCode }} · 索赔：{{ form.isClaim }} · 返厂：{{ form.returnToFactory }}
              <span v-if="form.projectCode"> · 项目：{{ form.projectCode }}</span>
              <span v-if="form.appointmentAt"> · 预约：{{ formatStamp(form.appointmentAt) }}</span>
              <span v-if="form.externalNo.trim()"> · 乐装单号：{{ form.externalNo.trim() }}</span>
              <span> · 附件：{{ uploadedFiles.length ? uploadedFiles.map(f => f.name).join('、') : '换装照片.jpg、旧件照片.jpg（占位）' }}</span>
            </p>
          </div>
        </form>
      </article>
    </div>

    <!-- 底部区域：回传状态 4 节点进度条 & 快捷回执 -->
    <section class="card-bottom" aria-label="现场回传进度与快捷回执">
      <div class="bottom-head">
        <div>
          <h3>智享乐装回传状态</h3>
          <p class="bottom-sub">
            <template v-if="latestFieldOrder">
              当前跟踪现场单 <strong>{{ latestFieldOrder.externalNo }}</strong>（{{ fieldStateLabel(latestFieldOrder.state) }}）
              <span v-if="latestFieldOrder.receipt"> · 回执：{{ latestFieldOrder.receipt }}</span>
            </template>
            <template v-else>尚未关联智享乐装现场单，关联后将在此同步 4 节点执行进度</template>
          </p>
        </div>
        <span v-if="latestFieldOrder && isClosedFieldState(latestFieldOrder.state)" class="closed-pill">
          {{ fieldStateLabel(latestFieldOrder.state) }}
        </span>
      </div>

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
            <small>{{ node.hint }}</small>
          </div>
        </li>
      </ol>

      <div v-if="activeFieldOrder && canProcess" class="quick-receipt-bar">
        <div class="receipt-bar-head">
          <strong>快捷登记回执</strong>
          <span class="muted-note">现场单 {{ activeFieldOrder.externalNo }} 进行中，可直接同步智享乐装人工回执</span>
        </div>
        <form class="receipt-form" @submit.prevent="handleSubmitReceipt">
          <select v-model="receiptForm.state" class="field-input receipt-select" aria-label="现场单回传状态">
            <option value="in_progress" :disabled="activeFieldOrder.state === 'in_progress'">已上门（处理中）</option>
            <option value="complete">完工回传（进入待验收）</option>
            <option value="rejected">拒单</option>
            <option value="cancelled">取消</option>
          </select>
          <input
            v-model="receiptForm.receipt"
            class="field-input receipt-input"
            type="text"
            maxlength="700"
            placeholder="填写现场处理结果、更换备件或拒单/取消原因"
          >
          <button
            class="btn-primary"
            type="submit"
            :disabled="busy || !receiptForm.receipt.trim() || receiptForm.state === activeFieldOrder.state"
          >
            {{ busy ? '保存中…' : '保存现场回执' }}
          </button>
        </form>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { displayStatus, disposalLabel, orderNumber } from '../status'

const props = defineProps({
  problem: { type: Object, required: true },
  device: { type: Object, default: null },
  busy: Boolean,
  canProcess: Boolean
})

const emit = defineEmits(['link', 'field-result', 'back'])

const dispatchSteps = [
  { id: 'check', label: '核对信息' },
  { id: 'fill', label: '补全字段' },
  { id: 'confirm', label: '确认派单' }
]

const callbackNodes = [
  { id: 'accepted', label: '已接单', hint: '现场单已关联并派发' },
  { id: 'onsite', label: '已上门', hint: '工程师到达现场排查' },
  { id: 'completed', label: '完工回传', hint: '维修完工并回传记录' },
  { id: 'verify', label: '待验收', hint: '回访车主确认恢复' }
]

const fieldStateTextMap = {
  linked: '已接单（已关联）',
  in_progress: '已上门（处理中）',
  complete: '完工回传',
  rejected: '已拒单',
  cancelled: '已取消'
}

const form = reactive({
  isClaim: '否',
  returnToFactory: '否',
  projectCode: '',
  appointmentAt: '',
  externalNo: ''
})

const receiptForm = reactive({
  state: 'complete',
  receipt: ''
})

const fileInputRef = ref(null)
const uploadedFiles = ref([])
const showPreview = ref(false)

const orderCode = computed(() => orderNumber(props.problem) || '未生成')
const statusInfo = computed(() => displayStatus(props.problem))
const disposalText = computed(() => disposalLabel(props.problem.disposalType) || props.problem.disposalType || '')

const resolvedCustomer = computed(() =>
  props.device?.customerName || props.problem?.customerName || '李先生（模拟）'
)
const resolvedAddress = computed(() =>
  props.device?.siteAddress || props.problem?.siteAddress || '合肥市滨湖新区示例小区 3 栋地下车库（模拟）'
)
const resolvedContact = computed(() =>
  props.device?.customerPhone || props.device?.contactPhone || props.problem?.contactPhone || '19900000001'
)
const resolvedDeviceCode = computed(() =>
  props.problem?.device || props.device?.device || '未关联'
)
const resolvedProject = computed(() =>
  props.device?.projectName || props.problem?.projectName || '合肥滨湖示范站运维项目（模拟）'
)

const readonlyFields = computed(() => [
  { key: 'customer', label: '客户名称', value: resolvedCustomer.value },
  { key: 'address', label: '安装地址', value: resolvedAddress.value },
  { key: 'device', label: '桩编码', value: resolvedDeviceCode.value },
  { key: 'phone', label: '联系电话', value: resolvedContact.value },
  { key: 'project', label: '所属项目', value: resolvedProject.value }
])

const isFieldState = computed(() => props.problem?.state === 'field')

const activeFieldOrder = computed(() =>
  (props.problem?.fieldOrders || []).find(item => item && (item.state === 'linked' || item.state === 'in_progress')) || null
)

const latestFieldOrder = computed(() => {
  if (activeFieldOrder.value) return activeFieldOrder.value
  const list = props.problem?.fieldOrders || []
  if (!list.length) return null
  const completed = [...list].reverse().find(item => item && item.state === 'complete')
  return completed || list[list.length - 1]
})

const canLinkDirectly = computed(() => isFieldState.value && !activeFieldOrder.value)

const activeDispatchStep = computed(() => {
  if (activeFieldOrder.value || latestFieldOrder.value?.state === 'complete') return 2
  if (form.externalNo.trim() && form.projectCode.trim()) return 2
  if (isFieldState.value || form.projectCode.trim() || form.appointmentAt || form.externalNo.trim()) return 1
  return 0
})

// 4 节点进度映射：
// linked -> 节点 1（idx 0 已完成/当前）
// in_progress -> 节点 2（idx 0 完成, idx 1 当前）
// complete -> 节点 3/4（idx 0,1,2 完成, idx 3 待验收当前；若主单已 resolved 则全部完成）
const callbackStage = computed(() => {
  const order = latestFieldOrder.value
  if (!order) return { completedUpTo: -1, currentIdx: -1 }
  if (order.state === 'linked') return { completedUpTo: 0, currentIdx: 0 }
  if (order.state === 'in_progress') return { completedUpTo: 0, currentIdx: 1 }
  if (order.state === 'complete') {
    if (props.problem?.state === 'resolved') return { completedUpTo: 3, currentIdx: -1 }
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

function isClosedFieldState(state) {
  return state === 'rejected' || state === 'cancelled'
}

function formatSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 KB'
  if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024)) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatStamp(value) {
  return String(value || '').replace('T', ' ').slice(0, 16)
}

function onFilesSelected(event) {
  const files = Array.from(event.target?.files || [])
  if (!files.length) return
  const next = files.map((file, index) => ({
    id: `${Date.now()}-${index}-${file.name}`,
    name: file.name,
    sizeText: formatSize(file.size)
  }))
  uploadedFiles.value = uploadedFiles.value.concat(next)
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function removeFile(idx) {
  uploadedFiles.value.splice(idx, 1)
}

function handleSubmitDispatch() {
  const externalNo = form.externalNo.trim()
  if (!canLinkDirectly.value || !props.canProcess || props.busy || !externalNo) return
  emit('link', externalNo)
}

function handleSubmitReceipt() {
  const receipt = receiptForm.receipt.trim()
  if (!activeFieldOrder.value || !props.canProcess || props.busy || !receipt) return
  if (receiptForm.state === activeFieldOrder.value.state) return
  emit('field-result', {
    externalNo: activeFieldOrder.value.externalNo,
    state: receiptForm.state,
    receipt
  })
  receiptForm.receipt = ''
}

watch(
  () => props.problem?.id,
  () => {
    form.externalNo = activeFieldOrder.value?.externalNo || ''
    receiptForm.receipt = ''
    receiptForm.state = activeFieldOrder.value?.state === 'in_progress' ? 'complete' : 'complete'
    showPreview.value = false
  },
  { immediate: true }
)

watch(
  activeFieldOrder,
  nextOrder => {
    if (nextOrder?.externalNo) form.externalNo = nextOrder.externalNo
    if (nextOrder?.state === 'in_progress' && receiptForm.state === 'in_progress') {
      receiptForm.state = 'complete'
    }
  }
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
  padding: 16px 18px;
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
  border: 1px solid #d5ebe5;
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
  margin: 3px 0 0;
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
  padding: 8px 12px;
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
  grid-template-columns: minmax(250px, 0.95fr) minmax(260px, 1fr) minmax(320px, 1.25fr);
  gap: 14px;
  align-items: start;
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

/* 状态胶囊 */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.status-pill i {
  width: 5px;
  height: 5px;
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

/* 左栏内容 */
.order-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--pine-soft, #e8f6f4);
  border: 1px solid #cce9e4;
  margin-bottom: 12px;
}

.order-label {
  color: var(--pine, #0f766e);
  font-size: 12px;
  font-weight: 600;
}

.order-code {
  color: var(--pine-ink, #102c2b);
  font-size: 14px;
  font-weight: 700;
}

.info-list {
  display: grid;
  gap: 8px;
  margin: 0 0 14px;
}

.info-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px solid #f1f5f9;
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
}

.mono {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.fault-summary {
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid var(--line, #eaeef2);
}

.fault-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--muted, #8a96a5);
  font-size: 11px;
}

.fault-title {
  display: block;
  color: var(--heading, #0f172a);
  font-size: 13px;
  margin-bottom: 4px;
}

.fault-desc {
  margin: 0;
  color: var(--ink, #334155);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.fault-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.mini-chip {
  padding: 2px 7px;
  border-radius: 4px;
  background: #eef2f6;
  color: #475569;
  font-size: 11px;
}

.mini-chip.pine {
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
}

/* 中栏只读带入 */
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
  gap: 10px;
}

.readonly-field {
  display: grid;
  gap: 4px;
}

.readonly-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.field-lock {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 500;
}

.readonly-input {
  width: 100%;
  min-height: 36px;
  padding: 7px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  cursor: default;
}

.readonly-input:focus {
  outline: none;
  box-shadow: none;
  border-color: #e2e8f0;
}

.sync-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 14px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 12px;
  line-height: 1.5;
}

.sync-tip svg {
  flex: none;
  margin-top: 2px;
}

/* 右栏补填字段 */
.active-order-tag,
.preview-tag {
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}

.active-order-tag {
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
}

.preview-tag {
  background: #fff8e8;
  color: #b45309;
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

.state-alert strong {
  display: block;
  margin-bottom: 2px;
}

.state-alert p {
  margin: 0;
  line-height: 1.5;
}

.dispatch-form {
  display: grid;
  gap: 12px;
}

.bool-grid,
.two-col-inputs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
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

.req {
  font-style: normal;
  color: #c0362c;
}

.choice-pills {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.choice-pill {
  min-height: 36px;
  border: 1px solid #dce3ec;
  border-radius: 8px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.choice-pill.selected {
  border-color: var(--pine, #0f766e);
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-weight: 700;
}

.field-input {
  width: 100%;
  min-width: 0;
  min-height: 38px;
  padding: 7px 10px;
  border: 1px solid #dce3ec;
  border-radius: 8px;
  background: #fff;
  color: var(--ink, #334155);
  font-size: 12px;
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
  gap: 8px;
  padding-top: 4px;
}

.upload-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.upload-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid #a4d2c9;
  border-radius: 6px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
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

.file-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.file-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
  background: #fff;
}

.file-card.placeholder {
  border-style: dashed;
  background: #f8fafc;
}

.file-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.file-tag {
  align-self: flex-start;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--pine-soft, #e8f6f4);
  color: var(--pine, #0f766e);
  font-size: 10px;
  font-weight: 600;
}

.file-tag.muted {
  background: #eef2f6;
  color: #64748b;
}

.remove-file {
  border: 0;
  background: transparent;
  color: #94a3b8;
  font-size: 15px;
  line-height: 1;
  padding: 0 2px;
  cursor: pointer;
}

.remove-file:hover {
  color: #c0362c;
}

.file-name {
  font-size: 12px;
  color: var(--heading, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  color: var(--muted, #8a96a5);
  font-size: 11px;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding-top: 4px;
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 8px 16px;
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

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.perm-tip {
  color: #b45309;
  font-size: 12px;
}

.preview-box {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--pine-soft, #e8f6f4);
  border: 1px solid #cce9e4;
  color: var(--pine-ink, #102c2b);
  font-size: 12px;
}

.preview-box strong {
  display: block;
  margin-bottom: 4px;
}

.preview-box p {
  margin: 0;
  line-height: 1.6;
}

/* 底部 4 节点进度条 */
.bottom-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
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
}

.closed-pill {
  padding: 3px 8px;
  border-radius: 6px;
  background: #fdecec;
  color: #c0362c;
  font-size: 12px;
  font-weight: 600;
}

.callback-track {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  list-style: none;
  margin: 0;
  padding: 14px;
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
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: 999px;
  background: #e2e8f0;
  color: #64748b;
  font-size: 12px;
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
  box-shadow: 0 0 0 4px #0d948824;
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
  font-size: 13px;
}

.track-node.done .node-copy strong,
.track-node.current .node-copy strong {
  color: var(--heading, #0f172a);
}

.node-copy small {
  display: block;
  color: var(--muted, #8a96a5);
  font-size: 11px;
}

/* 快捷回执条 */
.quick-receipt-bar {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line, #eaeef2);
}

.receipt-bar-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.receipt-bar-head strong {
  color: var(--heading, #0f172a);
  font-size: 13px;
}

.muted-note {
  color: var(--muted, #8a96a5);
  font-size: 12px;
}

.receipt-form {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

/* 响应式：窄屏自动堆叠 */
@media (max-width: 1120px) {
  .dispatch-grid {
    grid-template-columns: 1fr 1fr;
  }
  .dispatch-grid > :last-child {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .dispatch-grid,
  .bool-grid,
  .two-col-inputs,
  .file-grid,
  .callback-track,
  .receipt-form {
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
