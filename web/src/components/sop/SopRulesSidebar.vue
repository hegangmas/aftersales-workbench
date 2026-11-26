<template>
  <aside class="sop-right-col" :aria-label="readOnly ? '级别提示与标准证据说明' : '分级阈值、审批规则与版本记录'">
    <!-- ===== 分支 1：只读知识库（readOnly === true）===== -->
    <!-- 严禁出现任何编辑控件、任何阈值数字与换装自动审批条件 -->
    <template v-if="readOnly">
      <section class="card sop-card">
        <div class="card-head">
          <h2>级别提示与所需证据</h2>
          <span class="head-sub">一线指引</span>
        </div>

        <div v-if="displayedEntry" class="inset readonly-hint-box">
          <span class="hint-label">该故障所属级别提示</span>
          <p class="hint-text">
            {{ displayedEntry.gradeHint || '请严格按两段式 SOP 收集完整故障现象与对应记录，由系统与质检复核判定。' }}
          </p>
          <small class="hint-footnote">
            为防止反向凑证据，具体审批阈值由系统后台校验，知识库仅提供取证要求与规范说明。
          </small>
        </div>

        <div class="required-evidence-block">
          <div class="sub-head">
            <h3>所需证据清单</h3>
            <span class="tag num">{{ readonlyEvidenceList.length }} 项要求</span>
          </div>

          <ul v-if="readonlyEvidenceList.length" class="evidence-checklist">
            <li
              v-for="item in readonlyEvidenceList"
              :key="item.code"
              class="evidence-check-item"
            >
              <div class="evidence-item-top">
                <strong>{{ item.label }}</strong>
                <span class="tag tag--brand">建议必传</span>
              </div>
              <small>{{ item.tip }}</small>
            </li>
          </ul>
          <p v-else class="muted-note">当前分类暂无特殊必传证据要求，请按标准流程留痕。</p>
        </div>
      </section>

      <section class="card sop-card">
        <div class="card-head">
          <h2>标准证据样例说明</h2>
          <span class="head-sub">采集规范</span>
        </div>

        <ul class="sample-guide-list">
          <li
            v-for="sample in readonlyEvidenceSamples"
            :key="sample.code"
            class="sample-guide-item"
          >
            <div class="sample-head">
              <span class="tag">{{ sample.label }}</span>
            </div>
            <p class="sample-desc">{{ sample.sample }}</p>
          </li>
        </ul>

        <footer v-if="displayedEntry" class="readonly-meta-footer num">
          <span>生效版本：<span class="mono">{{ displayedEntry.version }}</span></span>
          <span v-if="displayedEntry.updatedAt" class="mono">更新于 {{ formatTimestamp(displayedEntry.updatedAt) }}</span>
        </footer>
      </section>
    </template>

    <!-- ===== 分支 2：管理 → SOP 与规则（readOnly === false，唯一写端）===== -->
    <template v-else>
      <!-- 上卡：仅管理侧可见的 A/B/C 阈值与换装自动审批条件 -->
      <section class="card sop-card">
        <div class="card-head card-head-rules">
          <div class="rules-head-title">
            <h2>分级阈值与审批规则</h2>
            <span class="tag tag--warning">仅管理侧可见 · 不对一线展示</span>
          </div>
          <button
            v-if="displayedEntry && !isEditingRules"
            type="button"
            class="btn btn--secondary btn--sm"
            :disabled="busy"
            @click="emit('enter-edit-rules')"
          >
            <svg class="btn-icon-14" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <circle cx="8" cy="8" r="2.2" />
              <path d="M8 1.8v1.4M8 12.8v1.4M1.8 8h1.4M12.8 8h1.4M3.6 3.6l1 1M11.4 11.4l1 1M12.4 3.6l-1 1M4.6 11.4l-1 1" />
            </svg>
            <span>设置规则</span>
          </button>
          <button
            v-else-if="displayedEntry"
            type="button"
            class="btn btn--secondary btn--sm"
            :disabled="busy"
            @click="emit('cancel-rule-changes')"
          >
            取消编辑
          </button>
        </div>

        <template v-if="displayedEntry">
          <!-- A/B/C 阈值配置（浅灰底 .inset + 左侧 3px 语义色条） -->
          <div class="grade-list" aria-label="A/B/C 分级阈值配置">
            <div class="inset grade-item grade-a">
              <span class="tag tag--success grade-tag">A级 阈值条件</span>
              <input
                v-if="isEditingRules"
                v-model.trim="ruleDraft.gradeThresholds.A"
                type="text"
                class="sop-input"
                maxlength="120"
                aria-label="A级阈值条件"
                placeholder="填写 A 级触发阈值"
              >
              <p v-else class="grade-text">{{ ruleDraft.gradeThresholds.A || '未配置 A 级阈值' }}</p>
            </div>
            <div class="inset grade-item grade-b">
              <span class="tag tag--warning grade-tag">B级 阈值条件</span>
              <input
                v-if="isEditingRules"
                v-model.trim="ruleDraft.gradeThresholds.B"
                type="text"
                class="sop-input"
                maxlength="120"
                aria-label="B级阈值条件"
                placeholder="填写 B 级触发阈值"
              >
              <p v-else class="grade-text">{{ ruleDraft.gradeThresholds.B || '未配置 B 级阈值' }}</p>
            </div>
            <div class="inset grade-item grade-c">
              <span class="tag tag--danger grade-tag">C级 阈值条件（禁止换装）</span>
              <input
                v-if="isEditingRules"
                v-model.trim="ruleDraft.gradeThresholds.C"
                type="text"
                class="sop-input"
                maxlength="120"
                aria-label="C级阈值条件"
                placeholder="填写 C 级判定阈值"
              >
              <p v-else class="grade-text">{{ ruleDraft.gradeThresholds.C || '未配置 C 级阈值' }}</p>
            </div>
          </div>

          <!-- 换装自动审批条件（48px 分隔线列表 + 自定义 .switch） -->
          <div class="auto-rules">
            <div class="sub-head">
              <h3>换装自动审批条件</h3>
              <div class="sub-head-right">
                <span class="badge badge--success num">
                  {{ autoPassCount }}/{{ ruleDraft.autoApprovalRules.length }} 项自动通过
                </span>
                <button
                  v-if="isEditingRules"
                  type="button"
                  class="btn btn--secondary btn--sm"
                  @click="emit('add-auto-rule')"
                >
                  + 条件
                </button>
              </div>
            </div>

            <ul class="rule-list">
              <li
                v-for="(rule, rIdx) in ruleDraft.autoApprovalRules"
                :key="rule.id || rIdx"
                class="rule-row"
              >
                <div class="rule-info">
                  <input
                    v-if="isEditingRules"
                    v-model.trim="rule.label"
                    type="text"
                    class="sop-input"
                    maxlength="80"
                    :aria-label="'自动审批条件 ' + (rIdx + 1)"
                    placeholder="请输入审批条件名称"
                  >
                  <div v-else class="rule-label-text">
                    <span v-if="splitRuleLabel(rule.label).prefix" class="rule-prefix mono">
                      {{ splitRuleLabel(rule.label).prefix }}
                    </span>
                    <span>{{ splitRuleLabel(rule.label).text }}</span>
                  </div>
                  <small v-if="rule.desc">{{ rule.desc }}</small>
                </div>

                <div class="rule-actions">
                  <label class="rule-switch-control">
                    <input
                      type="checkbox"
                      class="switch"
                      :checked="rule.enabled"
                      :aria-label="rule.label + '：当前为' + (rule.enabled ? '自动通过' : '人工复核')"
                      @change="emit('toggle-auto-rule', rule)"
                    >
                    <span class="switch-state" :class="{ on: rule.enabled }">
                      {{ rule.enabled ? '自动通过' : '人工复核' }}
                    </span>
                  </label>

                  <button
                    v-if="isEditingRules && ruleDraft.autoApprovalRules.length > 1"
                    type="button"
                    class="btn btn--danger-outline btn--sm"
                    @click="emit('remove-auto-rule', rIdx)"
                  >
                    删除
                  </button>
                </div>
              </li>
            </ul>

            <!-- 规则保存条：存在变更或处于设置规则模式时出现 -->
            <div v-if="isEditingRules || hasPendingRuleChanges" class="rule-save-bar">
              <div class="rule-version-row">
                <span class="limit-hint">规则变更将生成新版本并可发布同步知识库</span>
                <input
                  v-model.trim="ruleDraft.version"
                  type="text"
                  class="sop-input mono version-mini-input"
                  maxlength="20"
                  aria-label="规则保存版本号"
                  placeholder="版本号"
                >
              </div>
              <div class="rule-save-actions">
                <button
                  type="button"
                  class="btn btn--secondary btn--sm"
                  :disabled="busy"
                  @click="emit('cancel-rule-changes')"
                >
                  还原
                </button>
                <button
                  type="button"
                  class="btn btn--secondary btn--sm"
                  :disabled="busy"
                  @click="emit('save-rule-changes', false)"
                >
                  保存草稿
                </button>
                <button
                  type="button"
                  class="btn btn--primary btn--sm"
                  :disabled="busy"
                  @click="emit('save-rule-changes', true)"
                >
                  保存并发布
                </button>
              </div>
            </div>
          </div>
        </template>
      </section>

      <!-- 下卡：版本记录（支持点击草稿条目的「发布」调用 publishAdminSop(id)） -->
      <section class="card sop-card">
        <div class="card-head">
          <h2>版本记录</h2>
          <span class="head-sub num">共 {{ activeGroup ? activeGroup.versions.length : 0 }} 个版本</span>
        </div>

        <ul v-if="activeGroup && activeGroup.versions.length" class="version-list">
          <li
            v-for="ver in activeGroup.versions"
            :key="ver.id"
            class="version-item"
            :class="[statusTone(ver.status), { selected: displayedEntry && ver.id === displayedEntry.id }]"
            @click="emit('select-version', ver.id)"
          >
            <div class="version-top">
              <strong class="version-label">
                <span class="mono">{{ ver.version }}</span> · {{ statusLabel(ver.status) }}
              </strong>
              <div class="version-top-right">
                <span
                  class="badge"
                  :class="[ver.status !== 'archived' ? 'badge--dot' : '', statusBadgeClass(ver.status)]"
                >
                  {{ statusTag(ver.status) }}
                </span>
                <button
                  v-if="ver.status === 'draft'"
                  type="button"
                  class="btn btn--primary btn--sm"
                  :disabled="busy"
                  @click.stop="emit('publish-version', ver)"
                >
                  发布
                </button>
              </div>
            </div>
            <div class="version-meta mono">
              <span>{{ ver.updatedBy || 'system' }}</span>
              <span>{{ formatTimestamp(ver.updatedAt) }}</span>
            </div>
          </li>
        </ul>
        <p v-else class="muted-note">暂无版本记录</p>
      </section>
    </template>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { evidenceTypeLabel } from '../../status'

const EVIDENCE_SAMPLE_GUIDE = {
  photo: {
    sample: '桩体铭牌（含清晰 SN 条码）、指示灯/屏幕告警画面及配电箱空开全景照',
    tip: '保持光线清晰、正面拍摄，避免条码与告警字符反光遮挡'
  },
  video: {
    sample: '插枪启动至报错跳转的 10–30 秒连续短视频或手机端操作录屏',
    tip: '完整覆盖启动指令下发与桩端/车端现象反馈'
  },
  timestamp_log: {
    sample: '精确到分钟的异常发生时间点（如 14:25）、车端提示与故障现象描述',
    tip: '用于后台精确比对充电会话与告警报文时间轴'
  },
  site_compare: {
    sample: '同场站相邻正常桩交叉换车/换枪测试结果与现场信号对照情况',
    tip: '辅助定位单桩硬件故障、场站供电或网络环境问题'
  },
  swap_test: {
    sample: '单变量替换备用枪线/模块后的实车复测现象与前后对比记录',
    tip: '明确故障是否随部件转移或恢复正常'
  },
  platform_data: {
    sample: '按桩编码自动关联平台告警码、停机原因与历史充电会话报文',
    tip: '系统将自动校验后台报文，一线无需手动编造或猜测'
  }
}

const props = defineProps({
  readOnly: { type: Boolean, default: true },
  busy: { type: Boolean, default: false },
  activeGroup: { type: Object, default: null },
  displayedEntry: { type: Object, default: null },
  isEditingRules: { type: Boolean, default: false },
  hasPendingRuleChanges: { type: Boolean, default: false },
  ruleDraft: { type: Object, required: true },
  statusBadgeClass: { type: Function, required: true },
  statusLabel: { type: Function, required: true }
})

const emit = defineEmits([
  'enter-edit-rules',
  'cancel-rule-changes',
  'toggle-auto-rule',
  'add-auto-rule',
  'remove-auto-rule',
  'save-rule-changes',
  'select-version',
  'publish-version'
])

const readonlyEvidenceList = computed(() => {
  if (!props.displayedEntry) return []
  return props.displayedEntry.requiredEvidence.map(code => {
    const guide = EVIDENCE_SAMPLE_GUIDE[code] || {
      sample: '请按现场实际情况收集清晰可追溯的证明材料',
      tip: '确保设备编码、异常现象与时间点清晰可辨'
    }
    return {
      code,
      label: evidenceTypeLabel(code) || code,
      tip: guide.tip
    }
  })
})

const readonlyEvidenceSamples = computed(() => {
  const codes = props.displayedEntry && props.displayedEntry.requiredEvidence.length
    ? props.displayedEntry.requiredEvidence
    : ['photo', 'video', 'timestamp_log', 'platform_data']
  return codes.map(code => {
    const guide = EVIDENCE_SAMPLE_GUIDE[code] || {
      sample: '提供可核验的故障现象记录或设备状态信息',
      tip: ''
    }
    return {
      code,
      label: evidenceTypeLabel(code) || code,
      sample: guide.sample
    }
  })
})

const autoPassCount = computed(() => {
  return props.ruleDraft.autoApprovalRules.filter(r => r.enabled).length
})

function statusTone(status) {
  if (status === 'published') return 'published'
  if (status === 'draft') return 'reviewing'
  return 'archived'
}

function statusTag(status) {
  if (status === 'published') return '生效中'
  if (status === 'draft') return '待发布'
  return '已归档'
}

function splitRuleLabel(raw) {
  const text = String(raw || '').trim()
  const match = text.match(/^(条件\s*\d+)\s+(.+)$/)
  if (match) {
    return { prefix: match[1].replace(/\s+/g, ''), text: match[2] }
  }
  return { prefix: '', text }
}

function formatTimestamp(raw) {
  if (!raw) return ''
  return String(raw).replace('T', ' ').replace(/\.\d+.*$/, '').slice(0, 16)
}
</script>

<style scoped>
.btn-icon-14 {
  width: 14px;
  height: 14px;
  flex: none;
}

.tag--success {
  background: var(--success-bg);
  color: var(--success-fg);
}

.sop-right-col {
  display: grid;
  gap: var(--gutter);
}

.sop-card {
  padding: var(--card-py) var(--card-px) var(--space-5);
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

.card-head-rules {
  align-items: flex-start;
}

.rules-head-title {
  display: grid;
  justify-items: start;
  gap: 6px;
}

.head-sub {
  color: var(--text-tertiary);
  font-size: var(--fs-caption);
  white-space: nowrap;
}

.readonly-hint-box {
  display: grid;
  gap: 6px;
  background: var(--brand-50);
  border: 0;
}

.hint-label {
  font-size: var(--fs-caption);
  font-weight: var(--fw-semibold);
  color: var(--brand-700);
}

.hint-text {
  margin: 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  font-weight: var(--fw-semibold);
  color: var(--brand-900);
}

.hint-footnote {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.required-evidence-block {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.evidence-checklist,
.sample-guide-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.evidence-check-item,
.sample-guide-item {
  padding: 10px 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.evidence-check-item + .evidence-check-item,
.sample-guide-item + .sample-guide-item {
  border-top: 1px solid var(--border-subtle);
}

.evidence-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: 4px;
}

.evidence-item-top strong {
  font-size: var(--fs-body-sm);
  line-height: var(--lh-body-sm);
  font-weight: var(--fw-semibold);
  color: var(--text-primary);
}

.evidence-check-item small {
  display: block;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.sample-head {
  margin-bottom: 6px;
}

.sample-desc {
  margin: 0;
  font-size: var(--fs-body-sm);
  line-height: var(--lh-body-sm);
  color: var(--text-secondary);
}

.readonly-meta-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-subtle);
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.muted-note {
  margin: 0;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.grade-list {
  display: grid;
  gap: var(--space-2);
}

.grade-item {
  display: grid;
  gap: 6px;
  padding: 10px 14px;
  border: 0;
}

.grade-item.grade-a {
  box-shadow: inset 3px 0 0 var(--success-solid);
}

.grade-item.grade-b {
  box-shadow: inset 3px 0 0 var(--warning-solid);
}

.grade-item.grade-c {
  box-shadow: inset 3px 0 0 var(--danger-solid);
}

.grade-tag {
  justify-self: start;
  font-weight: var(--fw-semibold);
}

.grade-text {
  margin: 0;
  font-size: var(--fs-body-sm);
  line-height: var(--lh-body-sm);
  color: var(--text-primary);
}

.auto-rules {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.sub-head h3 {
  margin: 0;
  font-size: var(--fs-body-sm);
  font-weight: var(--fw-semibold);
  color: var(--text-primary);
}

.sub-head-right {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.rule-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.rule-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: var(--row-h);
  padding: 10px 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.rule-row + .rule-row {
  border-top: 1px solid var(--border-subtle);
}

.rule-info {
  flex: 1 1 auto;
  min-width: 0;
  display: grid;
  gap: 2px;
}

.rule-label-text {
  font-size: var(--fs-body-sm);
  line-height: var(--lh-body-sm);
  font-weight: var(--fw-medium);
  color: var(--text-primary);
}

.rule-prefix {
  margin-right: 6px;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.rule-info small {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-tertiary);
}

.rule-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  flex: none;
}

.rule-switch-control {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.switch-state {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  white-space: nowrap;
}

.switch-state.on {
  color: var(--success-fg);
  font-weight: var(--fw-medium);
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

.limit-hint {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.rule-save-bar {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-3);
  padding: var(--space-3);
  background: var(--brand-50);
  border: 1px solid var(--brand-200);
  border-radius: var(--radius-lg);
}

.rule-version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.version-mini-input {
  max-width: 88px;
  height: var(--control-h-sm);
  padding: 0 8px;
}

.rule-save-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
}

.version-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--space-2);
}

.version-item {
  padding: 10px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--bg-surface);
  box-shadow: none;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.version-item:hover {
  border-color: var(--border-strong);
}

.version-item.published {
  background: var(--brand-50);
  border: 1px solid var(--brand-200);
  border-radius: var(--radius-lg);
  box-shadow: none;
}

.version-item.reviewing {
  background: var(--warning-bg);
  border-color: var(--warning-border);
  box-shadow: none;
}

.version-item.selected:not(.published):not(.reviewing) {
  border-color: var(--brand-600);
}

.version-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.version-top-right {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.version-label {
  font-size: var(--fs-body-sm);
  font-weight: var(--fw-semibold);
  color: var(--text-primary);
}

.version-item.archived .version-label {
  color: var(--text-secondary);
  font-weight: var(--fw-medium);
}

.version-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: 6px;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

@media (max-width: 1120px) {
  .sop-right-col {
    grid-column: 1 / -1;
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 780px) {
  .sop-right-col {
    grid-column: auto;
    grid-template-columns: 1fr;
  }
}
</style>
