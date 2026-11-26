<template>
  <section class="device-lookup-page" aria-label="设备档案与建单">
    <!-- 顶部唯一搜索区 + 单源降级模拟 + 入口 B 触发 + 4 源状态条 -->
    <DeviceSearchHeader
      v-model:query="query"
      v-model:fail-source="failSource"
      :searching="searching"
      :submitting="submitting"
      :is-busy="isBusy"
      :search-error="searchError"
      :device="device"
      :source-statuses="sourceStatuses"
      :pending-open="pendingOpen"
      :pending-error="pendingError"
      @search="runSearch"
      @fail-source-change="onFailSourceChange"
      @toggle-pending="togglePendingForm"
      @open-pending="openPendingForm"
      @close-pending="pendingOpen = false"
      @submit-pending="submitPendingOrder"
    />

    <!-- 深链模拟地址提示条 -->
    <div v-if="deepLinkNotice" class="inline-alert info" role="status">
      <div>
        <strong>{{ deepLinkNotice.label }}：</strong>
        <code class="deep-link-code">{{ deepLinkNotice.url }}</code>
        <span class="deep-link-sub">（模拟内部系统深链地址，可在生产环境单点直达对应单据）</span>
      </div>
      <button type="button" class="btn btn--link" @click="deepLinkNotice = null">关闭</button>
    </div>

    <!-- 成功建单反馈 -->
    <div v-if="createdNotice" class="inline-alert success" role="status">
      <div>
        <strong>建单成功：</strong>{{ createdNotice }}
      </div>
      <button type="button" class="btn btn--link" @click="createdNotice = ''">知道了</button>
    </div>

    <!-- 空状态引导 -->
    <section v-if="!device && !searching" class="panel empty-guide">
      <div class="badge badge--brand empty-guide-badge">设备档案</div>
      <h2>输入桩 SN、手机号或项目编号开始查桩</h2>
      <p>支持按 4 源并行聚合查询基本信息、生产与安装、历史报修、历史维修、质保结算及关联工单，并一键带入设备档案建单。</p>
    </section>

    <!-- 同一张设备档案页，严格按 6 段纵向排列 -->
    <DeviceProfileSections
      v-if="device"
      :device="device"
      :device-sn="deviceSn"
      :is-busy="isBusy"
      :source-statuses="sourceStatuses"
      :related-problems="relatedProblems"
      :repair-history="repairHistory"
      :charging-records="chargingRecords"
      :recent-alerts="recentAlerts"
      :deep-links="deepLinks"
      :open-problems="openProblems"
      :closed-problems="closedProblems"
      @open="$emit('open', $event)"
      @create="openCreateModal"
      @deep-link="showDeepLink($event.label, $event.url)"
    />

    <!-- 入口 A 弹窗：为该设备建单 -->
    <DeviceCreateModal
      :open="createModalOpen"
      :device="device"
      :device-sn="deviceSn"
      :open-problems="openProblems"
      :submitting="submitting"
      :is-busy="isBusy"
      :error="createError"
      @close="closeCreateModal"
      @submit="submitDeviceOrder"
    />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { createProblemV11, lookupDevice } from '../api/workbench'
import DeviceCreateModal from '../components/device/DeviceCreateModal.vue'
import DeviceProfileSections from '../components/device/DeviceProfileSections.vue'
import DeviceSearchHeader from '../components/device/DeviceSearchHeader.vue'

const props = defineProps({
  busy: { type: Boolean, default: false },
  initialQuery: { type: String, default: '' }
})

const emit = defineEmits(['open', 'created'])

const CLOSED_STATES = new Set(['closed', 'cancelled', 'resolved', 'answered', 'referred'])

const query = ref('')
const lastSearchedQuery = ref('')
const failSource = ref('')
const device = ref(null)
const searching = ref(false)
const submitting = ref(false)
const searchError = ref('')
const deepLinkNotice = ref(null)
const createdNotice = ref('')

const createModalOpen = ref(false)
const createError = ref('')
const pendingOpen = ref(false)
const pendingError = ref('')

const isBusy = computed(() => Boolean(props.busy || searching.value || submitting.value))
const deviceSn = computed(() => (device.value ? (device.value.sn || device.value.device || '') : ''))
const sourceStatuses = computed(() => (device.value && Array.isArray(device.value.sourceStatuses) ? device.value.sourceStatuses : []))
const relatedProblems = computed(() => (device.value && Array.isArray(device.value.relatedProblems) ? device.value.relatedProblems : []))
const repairHistory = computed(() => (device.value && Array.isArray(device.value.repairHistory) ? device.value.repairHistory : []))
const chargingRecords = computed(() => (device.value && Array.isArray(device.value.chargingRecords) ? device.value.chargingRecords : []))
const recentAlerts = computed(() => (device.value && Array.isArray(device.value.recentAlerts) ? device.value.recentAlerts : []))
const deepLinks = computed(() => (device.value && device.value.deepLinks) || {})

const openProblems = computed(() => relatedProblems.value.filter(item => item && !CLOSED_STATES.has(item.state)))
const closedProblems = computed(() => relatedProblems.value.filter(item => item && CLOSED_STATES.has(item.state)))

function showDeepLink(label, url) {
  deepLinkNotice.value = { label, url }
}

async function runSearch(overrideQuery) {
  const target = (typeof overrideQuery === 'string' ? overrideQuery : query.value).trim()
  if (!target || isBusy.value) return
  searching.value = true
  searchError.value = ''
  deepLinkNotice.value = null
  try {
    const result = await lookupDevice(target, failSource.value || undefined)
    device.value = result
    lastSearchedQuery.value = target
  } catch (err) {
    device.value = null
    searchError.value = (err && err.message) || '查询设备失败，请稍后重试'
  } finally {
    searching.value = false
  }
}

watch(
  () => props.initialQuery,
  (val) => {
    const next = String(val || '').trim()
    if (!next) return
    query.value = next
    runSearch(next)
  },
  { immediate: true }
)

function onFailSourceChange() {
  const currentTarget = (query.value.trim() || lastSearchedQuery.value).trim()
  if (device.value && currentTarget) {
    runSearch(currentTarget)
  }
}

function openCreateModal() {
  createError.value = ''
  createModalOpen.value = true
}

function closeCreateModal() {
  if (submitting.value) return
  createModalOpen.value = false
}

function openPendingForm() {
  pendingError.value = ''
  pendingOpen.value = true
}

function togglePendingForm() {
  if (pendingOpen.value) {
    pendingOpen.value = false
  } else {
    openPendingForm()
  }
}

async function submitDeviceOrder(formPayload) {
  if (isBusy.value || !deviceSn.value) return
  submitting.value = true
  createError.value = ''
  try {
    const res = await createProblemV11({
      device: deviceSn.value,
      devicePending: false,
      ...formPayload
    })
    createModalOpen.value = false
    const no = res && res.ticketNo ? `（工单号：${res.ticketNo}）` : ''
    createdNotice.value = `已为设备 ${deviceSn.value} 创建问题单${no}`
    emit('created', res && res.id)
  } catch (err) {
    createError.value = (err && err.message) || '建单失败，请检查表单内容'
  } finally {
    submitting.value = false
  }
}

async function submitPendingOrder(formPayload) {
  if (isBusy.value) return
  submitting.value = true
  pendingError.value = ''
  try {
    const res = await createProblemV11({
      device: null,
      devicePending: true,
      ...formPayload
    })
    pendingOpen.value = false
    const no = res && res.ticketNo ? `（工单号：${res.ticketNo}）` : ''
    createdNotice.value = `已创建「设备信息待补」工单并进入待补队列${no}`
    emit('created', res && res.id)
  } catch (err) {
    pendingError.value = (err && err.message) || '创建待补工单失败，请检查表单内容'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.device-lookup-page {
  display: grid;
  gap: 14px;
  color: var(--ink, #334155);
}

.device-lookup-page :deep(.panel) {
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 12px;
  padding: 18px 20px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.device-lookup-page :deep(.mono) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-variant-numeric: tabular-nums;
}

.device-lookup-page :deep(.btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.15s ease, border-color 0.15s ease, opacity 0.15s ease;
}

.device-lookup-page :deep(.btn:disabled) {
  opacity: 0.58;
  cursor: not-allowed;
}

/* 提示条（供页面级及 SearchHeader / ProfileSections 复用） */
.device-lookup-page :deep(.inline-alert) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.device-lookup-page :deep(.inline-alert.compact) {
  padding: 8px 12px;
  font-size: 12px;
}

.device-lookup-page :deep(.inline-alert.error) {
  background: #fdecec;
  border: 1px solid #f5c2be;
  color: #c0362c;
}

.device-lookup-page :deep(.inline-alert.info) {
  background: #e8f6f4;
  border: 1px solid #bfe3dd;
  color: #0f766e;
}

.device-lookup-page :deep(.inline-alert.success) {
  background: #e8f6f4;
  border: 1px solid #0d9488;
  color: #0f766e;
}

.deep-link-code {
  padding: 2px 6px;
  border-radius: 4px;
  background: #fff;
  border: 1px solid #c5e4e1;
  color: #0f172a;
  font-size: 12px;
  word-break: break-all;
}

.deep-link-sub {
  margin-left: 6px;
  color: #475569;
  font-size: 12px;
}

/* 空状态 */
.empty-guide {
  text-align: center;
  padding: 40px 20px;
  background: #fff;
}

.empty-guide-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  background: #e8f6f4;
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 10px;
}

.empty-guide h2 {
  margin: 0 0 6px;
  font-size: 16px;
  color: #0f172a;
}

.empty-guide p {
  margin: 0 auto;
  max-width: 560px;
  font-size: 13px;
  color: #64748b;
}

/* 共享表单样式（供 SearchHeader 与 CreateModal 复用） */
.device-lookup-page :deep(.v11-form) {
  display: grid;
  gap: 12px;
}

.device-lookup-page :deep(.form-grid-2) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}

.device-lookup-page :deep(.field-group) {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.device-lookup-page :deep(.field-group label) {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.device-lookup-page :deep(.req) {
  color: #c0362c;
}

.device-lookup-page :deep(.form-control) {
  width: 100%;
  min-height: 38px;
  padding: 7px 10px;
  border: 1px solid #d7e0ea;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
}

.device-lookup-page :deep(.form-control:focus) {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.14);
  outline: none;
}

.device-lookup-page :deep(.form-textarea) {
  min-height: 86px;
  resize: vertical;
}

.device-lookup-page :deep(.form-error) {
  margin: 0;
  padding: 8px 10px;
  border-radius: 7px;
  background: #fdecec;
  border: 1px solid #f5c2be;
  color: #c0362c;
  font-size: 12px;
}

.device-lookup-page :deep(.form-actions) {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

@media (max-width: 640px) {
  .device-lookup-page :deep(.panel) {
    padding: 14px;
  }

  .device-lookup-page :deep(.form-grid-2) {
    grid-template-columns: 1fr;
  }
}
</style>
