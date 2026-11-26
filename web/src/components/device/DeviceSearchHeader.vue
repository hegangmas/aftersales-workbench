<template>
  <!-- 顶部唯一搜索区 + 单源降级模拟 + 入口 B 触发 + 4 源状态条 -->
  <section class="panel search-panel" aria-label="设备检索">
    <form class="search-bar" @submit.prevent="$emit('search')">
      <div class="search-input-wrap">
        <label class="sr-only" for="device-lookup-query">设备检索</label>
        <svg class="search-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        </svg>
        <input
          id="device-lookup-query"
          :value="query"
          type="search"
          class="search-input"
          placeholder="输入桩 SN、手机号或项目编号（如 CD26080101 / 13800001234 / PRJ-HF-2026-01 / SIM-AC-001）"
          autocomplete="off"
          @input="$emit('update:query', $event.target.value)"
        >
      </div>

      <div class="degrade-select-wrap">
        <label class="degrade-label" for="fail-source-select">模拟单源降级</label>
        <select
          id="fail-source-select"
          :value="failSource"
          class="degrade-select"
          aria-label="模拟单源降级"
          @change="onSelectFailSource"
        >
          <option value="">默认正常</option>
          <option value="mes">模拟 MES 超时</option>
          <option value="install">模拟安装不可用</option>
          <option value="400">模拟 400 不可用</option>
          <option value="lezhuang">模拟乐装不可用</option>
        </select>
      </div>

      <button
        type="submit"
        class="btn btn--primary search-submit"
        :disabled="isBusy || !query.trim()"
      >
        {{ searching ? '检索中…' : '查询设备' }}
      </button>
    </form>

    <!-- 入口 B：搜索区下方唯一的「设备信息待补」例外建单入口 -->
    <div class="exception-entry-bar">
      <span class="exception-hint">未带桩号或客户现场无法读取铭牌时，可先走待补例外队列：</span>
      <button
        type="button"
        class="btn btn--ghost exception-trigger"
        :aria-expanded="pendingOpen"
        :disabled="isBusy"
        @click="$emit('togglePending')"
      >
        {{ pendingOpen ? '收起待补建单表单' : '客户未提供桩信息？创建『设备信息待补』工单' }}
      </button>
    </div>

    <!-- 入口 B 展开表单 -->
    <section v-if="pendingOpen" class="pending-form-card" aria-label="设备信息待补建单表单">
      <div class="pending-alert" role="alert">
        <strong>例外建单提醒：</strong>将强制标记「设备信息待补」并进入待补队列，未补齐前不得派单、不得换装
      </div>

      <form class="v11-form" @submit.prevent="onSubmitPending">
        <div class="form-grid-2">
          <div class="field-group">
            <label for="pending-channel">报修渠道 <span class="req">*</span></label>
            <select id="pending-channel" v-model="pendingForm.channel" class="form-control" required>
              <option v-for="opt in CHANNEL_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>

          <div class="field-group">
            <label for="pending-category">故障大类 <span class="req">*</span></label>
            <select id="pending-category" v-model="pendingForm.category" class="form-control" required>
              <option v-for="cat in CATEGORY_OPTIONS" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </div>

          <div class="field-group">
            <label for="pending-urgency">紧急度 <span class="req">*</span></label>
            <select id="pending-urgency" v-model="pendingForm.urgency" class="form-control" required>
              <option v-for="urg in URGENCY_OPTIONS" :key="urg.value" :value="urg.value">{{ urg.label }}</option>
            </select>
          </div>

          <div class="field-group">
            <label for="pending-occurred">故障发生时点（北京时间） <span class="req">*</span></label>
            <input
              id="pending-occurred"
              v-model="pendingForm.faultOccurredAt"
              type="datetime-local"
              class="form-control"
              required
            >
          </div>
        </div>

        <div class="field-group">
          <label for="pending-title">故障现象标题 <span class="req">*</span></label>
          <input
            id="pending-title"
            v-model="pendingForm.title"
            type="text"
            class="form-control"
            maxlength="150"
            placeholder="例如：车主反馈插枪无反应，暂未提供桩 SN"
            required
          >
        </div>

        <div class="field-group">
          <label for="pending-desc">现象描述 <span class="req">*</span></label>
          <textarea
            id="pending-desc"
            v-model="pendingForm.description"
            class="form-control form-textarea"
            maxlength="1000"
            placeholder="请记录故障表现、客户姓名/联系方式及大致安装位置线索，便于后续核实补齐设备档案"
            required
          ></textarea>
        </div>

        <p v-if="pendingError" class="form-error" role="alert">{{ pendingError }}</p>

        <div class="form-actions">
          <button type="button" class="btn btn--secondary" :disabled="isBusy" @click="$emit('closePending')">取消</button>
          <button type="submit" class="btn btn--primary" :disabled="isBusy || !canSubmitPending">
            {{ submitting ? '提交中…' : '提交「设备信息待补」工单' }}
          </button>
        </div>
      </form>
    </section>

    <!-- 查询出错提示 -->
    <div v-if="searchError" class="inline-alert error" role="alert">
      <div>
        <strong>未检索到设备档案：</strong>{{ searchError }}
      </div>
      <button
        v-if="!pendingOpen"
        type="button"
        class="btn-link-danger"
        @click="$emit('openPending')"
      >
        转为创建「设备信息待补」工单 →
      </button>
    </div>

    <!-- 4 源并行状态条（查询后展示） -->
    <section v-if="device" class="sources-strip" aria-label="4 源并行同步状态">
      <div class="sources-head">
        <span class="sources-title">4 源并行聚合状态</span>
        <span class="sources-meta tabular">
          {{ device.source || '4源并行聚合' }} · 观测时间 {{ dateLabel(device.observedAt) }}
          <template v-if="Number(device.matchCount || 0) > 1">
            · 共匹配 {{ device.matchCount }} 台，当前展示首条
          </template>
        </span>
      </div>
      <div class="sources-grid">
        <div
          v-for="src in sourceStatuses"
          :key="src.name"
          class="source-pill"
          :class="src.ok ? 'ok' : 'degraded'"
        >
          <div class="source-pill-top">
            <span class="source-dot" aria-hidden="true"></span>
            <strong class="source-name">{{ src.name }}</strong>
            <span class="source-badge">{{ src.ok ? '正常' : '降级缺口' }}</span>
          </div>
          <div class="source-note">{{ src.note || (src.ok ? '已同步' : '接口不可用') }}</div>
          <div v-if="src.queriedAt" class="source-time mono">{{ dateLabel(src.queriedAt) }}</div>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { dateLabel } from '../../status'
import {
  CATEGORY_OPTIONS,
  CHANNEL_OPTIONS,
  URGENCY_OPTIONS,
  createTicketFormState
} from './deviceShared'

const props = defineProps({
  query: { type: String, default: '' },
  failSource: { type: String, default: '' },
  searching: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  isBusy: { type: Boolean, default: false },
  searchError: { type: String, default: '' },
  device: { type: Object, default: null },
  sourceStatuses: { type: Array, default: () => [] },
  pendingOpen: { type: Boolean, default: false },
  pendingError: { type: String, default: '' }
})

const emit = defineEmits([
  'update:query',
  'update:failSource',
  'search',
  'failSourceChange',
  'togglePending',
  'openPending',
  'closePending',
  'submitPending'
])

const pendingForm = reactive(createTicketFormState())

const canSubmitPending = computed(() => Boolean(
  pendingForm.faultOccurredAt
  && pendingForm.title.trim()
  && pendingForm.description.trim()
))

watch(
  () => props.pendingOpen,
  (open) => {
    if (open) {
      Object.assign(pendingForm, createTicketFormState())
    }
  }
)

function onSelectFailSource(event) {
  emit('update:failSource', event.target.value)
  emit('failSourceChange')
}

function onSubmitPending() {
  if (props.isBusy || !canSubmitPending.value) return
  emit('submitPending', {
    channel: pendingForm.channel,
    category: pendingForm.category,
    urgency: pendingForm.urgency,
    faultOccurredAt: pendingForm.faultOccurredAt,
    title: pendingForm.title.trim(),
    description: pendingForm.description.trim()
  })
}
</script>

<style scoped>
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.search-panel {
  display: grid;
  gap: 12px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input-wrap {
  position: relative;
  flex: 1 1 360px;
  min-width: 0;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #8a96a5;
  pointer-events: none;
}

.search-input {
  width: 100%;
  min-height: 40px;
  padding: 8px 12px 8px 36px;
  border: 1px solid #d7e0ea;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
}

.search-input:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.14);
  outline: none;
}

.degrade-select-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  min-height: 40px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
  background: var(--canvas, #f5f7fa);
  flex: 0 0 auto;
}

.degrade-label {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.degrade-select {
  border: 0;
  background: transparent;
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 2px;
  cursor: pointer;
}

.degrade-select:focus {
  outline: none;
}

.search-submit {
  min-height: 40px;
  padding: 8px 18px;
}

.btn-link-danger {
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  color: #c0362c;
}

.btn-link-danger:hover {
  text-decoration: underline;
}

/* 入口 B：待补例外区 */
.exception-entry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line, #eaeef2);
}

.exception-hint {
  font-size: 12px;
  color: #64748b;
}

.exception-trigger {
  min-height: 34px;
  font-size: 12px;
  border-color: #cbd5e1;
}

.pending-form-card {
  padding: 14px 16px;
  border: 1px solid #f5d08a;
  border-radius: 10px;
  background: #fffdf7;
  display: grid;
  gap: 12px;
}

.pending-alert {
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff5dd;
  border: 1px solid #f3d285;
  color: #92400e;
  font-size: 13px;
  line-height: 1.5;
}

/* 4 源并行状态条 */
.sources-strip {
  display: grid;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #eaeef2);
}

.sources-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.sources-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.sources-meta {
  font-size: 12px;
  color: #64748b;
}

.sources-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.source-pill {
  padding: 10px 14px;
  border-radius: var(--radius-lg, 10px);
  border: 1px solid var(--border-default, #e2e7e7);
  background: var(--bg-surface, #ffffff);
  display: grid;
  gap: 4px;
  min-width: 0;
}

.source-pill.ok {
  background: var(--bg-surface, #ffffff);
  border-color: var(--border-default, #e2e7e7);
  box-shadow: inset 3px 0 0 var(--success-solid, #1f8f5c);
  color: var(--success-fg, #18794e);
}

.source-pill.degraded {
  background: var(--danger-bg, #fdeeec);
  border-color: var(--danger-border, #f4c5c0);
  box-shadow: inset 3px 0 0 var(--danger-solid, #c0362c);
  color: var(--danger-fg, #c0362c);
}

.source-pill-top {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.source-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  flex: none;
}

.source-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary, #152020);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-badge {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  flex: none;
}

.source-note {
  font-size: 12px;
  color: var(--text-secondary, #4a5656);
  line-height: 1.45;
}

.source-time {
  font-size: 12px;
  color: var(--text-tertiary, #7a8686);
}

@media (max-width: 1024px) {
  .sources-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input-wrap {
    flex: none;
  }

  .degrade-select-wrap {
    justify-content: space-between;
  }

  .sources-grid {
    grid-template-columns: 1fr;
  }
}
</style>
