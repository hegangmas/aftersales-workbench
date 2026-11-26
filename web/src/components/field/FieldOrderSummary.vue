<template>
  <!-- 左栏：待派工单 -->
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
        @click="$emit('refresh')"
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

  <!-- 中栏：自动带入（只读） ⓘ -->
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
      <span class="badge badge--lock">已锁定</span>
    </header>

    <div class="readonly-stack">
      <div v-for="field in readonlyFields" :key="field.key" class="readonly-field">
        <label class="readonly-label" :for="'ro-' + field.key">{{ field.label }}</label>
        <div class="readonly-box field-locked">
          <input
            :id="'ro-' + field.key"
            class="readonly-input"
            :class="{ mono: field.key === 'device' || field.key === 'phone' }"
            type="text"
            :value="field.value"
            readonly
            tabindex="-1"
            :aria-label="field.label + '（只读）'"
          >
        </div>
      </div>
    </div>

    <p class="sync-tip" role="note">
      <span aria-hidden="true">ⓘ</span>
      <span>以上信息由受理建单自动带入</span>
    </p>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { dateLabel, displayStatus, disposalLabel, orderNumber } from '../../status'

const props = defineProps({
  problem: { type: Object, required: true },
  device: { type: Object, default: null },
  busy: Boolean,
  refreshTip: { type: String, default: '' }
})

defineEmits(['refresh'])

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
</script>

<style scoped>
.card-col {
  min-width: 0;
  padding: 18px 20px;
  background: var(--bg-surface, #ffffff);
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  box-shadow: var(--elev-1);
}

.col-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--border-subtle, #edf0f0);
}

.col-kicker {
  display: block;
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.col-head h3 {
  margin: 2px 0 0;
  color: var(--text-primary, #152020);
  font-size: 15px;
  font-weight: 600;
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
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
}

.order-code {
  color: var(--text-primary, #152020);
  font-family: var(--font-mono, monospace);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.25;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.status-pill i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-pill.processing { background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); }
.status-pill.waiting { background: var(--warning-bg, #fef5e6); color: var(--warning-fg, #a15c07); }
.status-pill.dispatch { background: var(--info-bg, #edf3f9); color: var(--info-fg, #2f5f8a); }
.status-pill.onsite { background: var(--info-bg, #edf3f9); color: var(--info-fg, #2f5f8a); }
.status-pill.verify { background: var(--warning-bg, #fef5e6); color: var(--warning-fg, #a15c07); }
.status-pill.closed { background: var(--neutral-bg, #f1f4f4); color: var(--neutral-fg, #4a5656); }
.status-pill.triage { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); }
.status-pill.follow { background: var(--success-bg, #eaf6ef); color: var(--success-fg, #18794e); }

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
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.mono {
  font-family: var(--font-mono, monospace);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.remark-box {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--bg-subtle, #f8fafa);
  border: 0;
}

.remark-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-weight: 600;
}

.remark-text {
  margin: 0;
  color: var(--text-secondary, #4a5656);
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.mini-chip {
  padding: 1px 7px;
  border-radius: 4px;
  background: var(--neutral-bg, #f1f4f4);
  color: var(--text-secondary, #4a5656);
  font-size: 12px;
  font-weight: 500;
}

.mini-chip.pine {
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
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
</style>
