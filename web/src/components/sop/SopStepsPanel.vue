<template>
  <section class="card sop-card sop-main-card" aria-label="两段式 SOP 步骤">
    <template v-if="displayedEntry">
      <div class="card-head main-head">
        <div class="main-title-wrap">
          <h2>{{ displayedEntry.title || (activeGroup && activeGroup.category) }}</h2>
          <span class="badge badge--brand">
            {{ readOnly ? '生效版本：' : '当前查看：' }}<span class="mono">{{ displayedEntry.version }}</span>
          </span>
          <span
            v-if="!readOnly && displayedEntry.status"
            class="badge badge--dot"
            :class="statusBadgeClass(displayedEntry.status)"
          >
            {{ statusLabel(displayedEntry.status) }}
          </span>
          <button
            v-if="!readOnly && isViewingNonDefaultVersion"
            type="button"
            class="btn btn--link btn--sm"
            @click="emit('reset-version')"
          >
            返回默认版本
          </button>
        </div>

        <div v-if="!readOnly" class="main-head-actions">
          <button
            v-if="!isEditingSop"
            type="button"
            class="btn btn--secondary btn--sm"
            :disabled="busy"
            @click="emit('enter-edit')"
          >
            <svg class="btn-icon-14" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M11.5 2.5a1.4 1.4 0 0 1 2 2L5.5 12.5l-3 .75.75-3 8.25-7.75Z" />
            </svg>
            <span>编辑 SOP</span>
          </button>
          <template v-else>
            <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="emit('cancel-edit')">
              取消
            </button>
            <button type="button" class="btn btn--secondary btn--sm" :disabled="busy" @click="emit('save-edit', false)">
              保存草稿
            </button>
            <button type="button" class="btn btn--primary btn--sm" :disabled="busy" @click="emit('save-edit', true)">
              保存并发布
            </button>
          </template>
        </div>
      </div>

      <div class="sop-meta-strip">
        <span v-if="displayedEntry.evidenceMode" class="tag">
          取证模式：{{ displayedEntry.evidenceMode }}
        </span>
        <span class="sop-summary-note">
          两段式排查标准：先引导客户完成远程自查（限 3 步内）并留痕，无法恢复再派单执行上门核验。
        </span>
      </div>

      <!-- 编辑模式下的元数据配置（仅管理侧） -->
      <div v-if="!readOnly && isEditingSop" class="inset sop-edit-meta">
        <div class="new-fault-grid three">
          <label class="form-field">
            <span class="field-label">SOP 标题 *</span>
            <input v-model.trim="sopDraft.title" type="text" class="sop-input" maxlength="120">
          </label>
          <label class="form-field">
            <span class="field-label">新版本号 *</span>
            <input v-model.trim="sopDraft.version" type="text" class="sop-input mono" maxlength="20">
          </label>
          <label class="form-field">
            <span class="field-label">取证模式</span>
            <input v-model.trim="sopDraft.evidenceMode" type="text" class="sop-input" maxlength="120">
          </label>
        </div>

        <label class="form-field">
          <span class="field-label">一线级别提示（仅提示取证方向，不显示阈值数字）</span>
          <input v-model.trim="sopDraft.gradeHint" type="text" class="sop-input" maxlength="200">
        </label>

        <div class="form-field">
          <span class="field-label">所需证据清单</span>
          <div class="evidence-checkbox-group">
            <label
              v-for="opt in evidenceOptions"
              :key="'edit-ev-' + opt.value"
              class="evidence-check-pill"
              :class="{ checked: sopDraft.requiredEvidence.includes(opt.value) }"
            >
              <input v-model="sopDraft.requiredEvidence" type="checkbox" class="checkbox" :value="opt.value">
              <span>{{ opt.label }}</span>
            </label>
          </div>
        </div>
      </div>

      <!-- 第一段：客户可执行 1-3（无框分组 + 编号圆点 + 1px 分隔线） -->
      <div class="sop-stage">
        <div class="stage-head">
          <div class="stage-title">
            <span class="tag tag--brand">第一段</span>
            <h3>客户可执行 1-3</h3>
          </div>
          <span class="stage-limit num">
            控制在 3 步以内（当前 {{ displayedCustomerSteps.length }}/3 步）
          </span>
        </div>

        <ol class="step-list">
          <li
            v-for="(step, idx) in displayedCustomerSteps"
            :key="'cust-' + idx"
            class="step-item"
          >
            <span class="step-index">{{ idx + 1 }}</span>

            <div v-if="readOnly || !isEditingSop" class="step-body">
              <p class="step-desc">{{ step }}</p>
            </div>

            <div v-else class="step-editor">
              <div class="step-editor-inline">
                <input
                  v-model.trim="sopDraft.customerSteps[idx]"
                  type="text"
                  class="sop-input"
                  maxlength="200"
                  :aria-label="'客户步骤 ' + (idx + 1) + ' 说明'"
                  placeholder="请输入客户排查步骤说明"
                >
                <button
                  type="button"
                  class="btn btn--danger-outline btn--sm"
                  :disabled="sopDraft.customerSteps.length <= 1"
                  @click="emit('remove-step', 'customer', idx)"
                >
                  删除
                </button>
              </div>
            </div>
          </li>
        </ol>

        <div v-if="!readOnly && isEditingSop" class="stage-edit-bar">
          <button
            type="button"
            class="btn btn--secondary btn--sm"
            :disabled="sopDraft.customerSteps.length >= 3"
            @click="emit('add-step', 'customer')"
          >
            + 新增客户步骤
          </button>
          <span class="limit-hint num">
            {{ sopDraft.customerSteps.length >= 3 ? '已达上限：客户可执行最多 3 步' : '还可新增 ' + (3 - sopDraft.customerSteps.length) + ' 步' }}
          </span>
        </div>
      </div>

      <!-- 第二段：上门验证 4-6 -->
      <div class="sop-stage stage-onsite-wrap">
        <div class="stage-head">
          <div class="stage-title">
            <span class="tag tag--warning">第二段</span>
            <h3>上门验证 4-6</h3>
          </div>
          <span class="stage-limit num">
            现场核验与换装留痕（第 4–{{ 3 + displayedOnsiteSteps.length }} 步）
          </span>
        </div>

        <ol class="step-list step-list--warning">
          <li
            v-for="(step, idx) in displayedOnsiteSteps"
            :key="'onsite-' + idx"
            class="step-item onsite"
          >
            <span class="step-index onsite">{{ idx + 4 }}</span>

            <div v-if="readOnly || !isEditingSop" class="step-body">
              <p class="step-desc">{{ step }}</p>
            </div>

            <div v-else class="step-editor">
              <div class="step-editor-inline">
                <input
                  v-model.trim="sopDraft.onsiteSteps[idx]"
                  type="text"
                  class="sop-input"
                  maxlength="200"
                  :aria-label="'上门步骤 ' + (idx + 4) + ' 说明'"
                  placeholder="请输入上门验证步骤说明"
                >
                <button
                  type="button"
                  class="btn btn--danger-outline btn--sm"
                  :disabled="sopDraft.onsiteSteps.length <= 1"
                  @click="emit('remove-step', 'onsite', idx)"
                >
                  删除
                </button>
              </div>
            </div>
          </li>
        </ol>

        <div v-if="!readOnly && isEditingSop" class="stage-edit-bar">
          <button
            type="button"
            class="btn btn--secondary btn--sm"
            :disabled="sopDraft.onsiteSteps.length >= 3"
            @click="emit('add-step', 'onsite')"
          >
            + 新增上门步骤
          </button>
          <span class="limit-hint num">
            {{ sopDraft.onsiteSteps.length >= 3 ? '已达上限：上门验证第 4-6 步' : '还可新增 ' + (3 - sopDraft.onsiteSteps.length) + ' 步' }}
          </span>
        </div>
      </div>
    </template>
    <div v-else class="tree-empty">
      <p>暂无 SOP 数据，请先新建故障分类或刷新页面。</p>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  readOnly: { type: Boolean, default: true },
  busy: { type: Boolean, default: false },
  activeGroup: { type: Object, default: null },
  displayedEntry: { type: Object, default: null },
  isViewingNonDefaultVersion: { type: Boolean, default: false },
  isEditingSop: { type: Boolean, default: false },
  sopDraft: { type: Object, required: true },
  evidenceOptions: { type: Array, default: () => [] },
  statusBadgeClass: { type: Function, required: true },
  statusLabel: { type: Function, required: true }
})

const emit = defineEmits([
  'reset-version',
  'enter-edit',
  'cancel-edit',
  'save-edit',
  'add-step',
  'remove-step'
])

const displayedCustomerSteps = computed(() => {
  if (!props.readOnly && props.isEditingSop) return props.sopDraft.customerSteps
  return props.displayedEntry ? props.displayedEntry.customerSteps.slice(0, 3) : []
})

const displayedOnsiteSteps = computed(() => {
  if (!props.readOnly && props.isEditingSop) return props.sopDraft.onsiteSteps
  return props.displayedEntry ? props.displayedEntry.onsiteSteps : []
})
</script>

<style scoped>
.btn-icon-14 {
  width: 14px;
  height: 14px;
  flex: none;
}

.sop-card {
  padding: var(--card-py) var(--card-px) var(--space-5);
}

.sop-main-card {
  min-height: 540px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.card-head h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--fs-title-sm);
  line-height: var(--lh-title-sm);
  font-weight: var(--fw-semibold);
}

.main-head {
  flex-wrap: wrap;
  margin-bottom: var(--space-2);
}

.main-title-wrap {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.main-title-wrap h2 {
  font-size: var(--fs-title);
  line-height: var(--lh-title);
  font-weight: var(--fw-semibold);
}

.main-head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.sop-meta-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--border-subtle);
}

.sop-summary-note {
  color: var(--text-tertiary);
  font-size: var(--fs-caption);
}

.sop-edit-meta {
  display: grid;
  gap: 10px;
  margin-bottom: var(--space-4);
}

.new-fault-grid.three {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.form-field {
  display: grid;
  gap: 6px;
}

.field-label {
  font-size: var(--fs-caption);
  font-weight: var(--fw-medium);
  color: var(--text-secondary);
}

.evidence-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.evidence-check-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  background: var(--bg-surface);
  font-size: var(--fs-caption);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);
}

.evidence-check-pill.checked {
  border-color: var(--brand-200);
  background: var(--brand-50);
  color: var(--brand-700);
  font-weight: var(--fw-semibold);
}

.evidence-check-pill .checkbox {
  margin: 0;
}

.sop-stage {
  padding: 0;
  border: 0;
  background: transparent;
}

.stage-onsite-wrap {
  margin-top: var(--space-5);
  padding-top: var(--space-5);
  border-top: 1px solid var(--border-subtle);
}

.stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.stage-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.stage-title .tag {
  font-weight: var(--fw-semibold);
}

.stage-title h3 {
  margin: 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  font-weight: var(--fw-semibold);
  color: var(--text-primary);
}

.stage-limit {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.step-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: 10px 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.step-item + .step-item {
  border-top: 1px solid var(--border-subtle);
}

.step-index {
  flex: none;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  margin-top: 1px;
  border-radius: 50%;
  background: var(--brand-50);
  color: var(--brand-700);
  font-family: var(--font-num);
  font-size: var(--fs-caption);
  font-weight: var(--fw-semibold);
  font-variant-numeric: tabular-nums;
}

.step-list--warning .step-index,
.step-index.onsite {
  background: var(--warning-bg);
  color: var(--warning-fg);
}

.step-body {
  flex: 1 1 auto;
  min-width: 0;
}

.step-desc {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
}

.step-editor {
  flex: 1 1 auto;
  min-width: 0;
  display: grid;
  gap: var(--space-2);
}

.step-editor-inline {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.sop-input {
  width: 100%;
  min-width: 0;
  height: var(--control-h-md);
  padding: 0 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  color: var(--text-primary);
  font-size: var(--fs-body-sm);
  transition: border-color var(--dur-fast) var(--ease), box-shadow var(--dur-fast) var(--ease);
}

.sop-input::placeholder {
  color: var(--text-disabled);
}

.sop-input:hover {
  border-color: var(--border-hover);
}

.sop-input:focus,
.sop-input:focus-visible {
  border-color: var(--brand-600);
  box-shadow: var(--focus-ring);
  outline: none;
}

.stage-edit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px dashed var(--border-default);
}

.limit-hint {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.tree-empty {
  padding: 24px 8px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--fs-caption);
}

.tree-empty p {
  margin: 0 0 6px;
}

@media (max-width: 780px) {
  .new-fault-grid.three {
    grid-template-columns: 1fr;
  }

  .sop-main-card {
    min-height: 0;
  }

  .step-editor-inline {
    flex-direction: column;
    align-items: stretch;
    gap: 6px;
  }
}
</style>
