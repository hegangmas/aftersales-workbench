<template>
  <section class="sop-workspace" aria-label="SOP 与规则管理">
    <!-- 顶部操作条（管理写端加 3px 琥珀色内顶边） -->
    <header class="card sop-topbar card--write-end">
      <div class="topbar-left">
        <nav class="sop-breadcrumb" aria-label="当前位置">
          <span>管理</span>
          <span class="crumb-sep" aria-hidden="true">/</span>
          <span>SOP 与规则</span>
          <span class="crumb-sep" aria-hidden="true">/</span>
          <strong class="crumb-current">{{ activeGroup ? activeGroup.category : '全部故障' }}</strong>
        </nav>

        <span class="badge badge--warning">唯一写入端</span>
        <span
          v-if="displayedEntry"
          class="badge badge--dot"
          :class="statusBadgeClass(displayedEntry.status)"
        >
          <span class="mono">{{ displayedEntry.version }}</span> · {{ statusLabel(displayedEntry.status) }}
        </span>
      </div>

      <div class="topbar-actions">
        <button
          type="button"
          class="btn btn--secondary"
          :disabled="busy"
          :aria-expanded="showNewFaultForm"
          @click="toggleNewFaultForm"
        >
          + 新建故障
        </button>
        <button
          type="button"
          class="btn btn--secondary"
          :disabled="busy || !displayedEntry"
          @click="handleReview"
        >
          评审
        </button>
        <button
          type="button"
          class="btn btn--primary"
          :disabled="busy || !displayedEntry"
          @click="handleTopbarPublish"
        >
          发布
        </button>
      </div>
    </header>

    <!-- 新建故障内联面板 -->
    <form
      v-if="showNewFaultForm"
      class="card new-fault-panel"
      @submit.prevent="submitNewFault(false)"
    >
      <div class="panel-head">
        <div>
          <h3>新建故障分类与 SOP</h3>
          <p>保存后写入唯一 SOP 主库；选择「创建并发布」将单向同步至一线只读知识库。</p>
        </div>
        <button type="button" class="btn btn--secondary btn--sm" @click="cancelNewFault">
          关闭
        </button>
      </div>

      <div class="new-fault-grid">
        <label class="form-field">
          <span class="field-label">故障分类名称 *</span>
          <input
            v-model.trim="newFault.category"
            type="text"
            class="sop-input"
            maxlength="80"
            placeholder="例如：枪锁卡滞、屏幕黑屏"
            required
          >
        </label>
        <label class="form-field">
          <span class="field-label">SOP 标题 *</span>
          <input
            v-model.trim="newFault.title"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="例如：枪锁卡滞 · SOP步骤"
            required
          >
        </label>
        <label class="form-field">
          <span class="field-label">初始版本号 *</span>
          <input
            v-model.trim="newFault.version"
            type="text"
            class="sop-input mono"
            maxlength="20"
            placeholder="V1.0"
            required
          >
        </label>
        <label class="form-field">
          <span class="field-label">取证模式</span>
          <input
            v-model.trim="newFault.evidenceMode"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="例如：时序+可视混合类"
          >
        </label>
      </div>

      <label class="form-field">
        <span class="field-label">一线级别提示（仅提示取证方向，不暴露判定阈值）</span>
        <input
          v-model.trim="newFault.gradeHint"
          type="text"
          class="sop-input"
          maxlength="200"
          placeholder="例如：A级需平台日志佐证；B级需现场采集与对照；C级禁止换装"
        >
      </label>

      <div class="new-fault-stages">
        <div class="inset mini-stage">
          <div class="mini-stage-head">
            <strong>第一段：客户可执行（1–3 步）</strong>
            <button
              type="button"
              class="btn btn--secondary btn--sm"
              :disabled="newFault.customerSteps.length >= 3"
              @click="addNewFaultStep('customer')"
            >
              + 加一步
            </button>
          </div>
          <div
            v-for="(step, idx) in newFault.customerSteps"
            :key="'nf-c-' + idx"
            class="mini-step-row"
          >
            <span class="step-index">{{ idx + 1 }}</span>
            <input
              v-model.trim="newFault.customerSteps[idx]"
              type="text"
              class="sop-input"
              maxlength="160"
              :placeholder="'客户步骤 ' + (idx + 1)"
            >
            <button
              type="button"
              class="btn btn--danger-outline btn--sm"
              :disabled="newFault.customerSteps.length <= 1"
              @click="removeNewFaultStep('customer', idx)"
            >
              删除
            </button>
          </div>
        </div>

        <div class="inset mini-stage">
          <div class="mini-stage-head">
            <strong>第二段：上门验证（4–6 步）</strong>
            <button
              type="button"
              class="btn btn--secondary btn--sm"
              :disabled="newFault.onsiteSteps.length >= 3"
              @click="addNewFaultStep('onsite')"
            >
              + 加一步
            </button>
          </div>
          <div
            v-for="(step, idx) in newFault.onsiteSteps"
            :key="'nf-o-' + idx"
            class="mini-step-row"
          >
            <span class="step-index onsite">{{ idx + 4 }}</span>
            <input
              v-model.trim="newFault.onsiteSteps[idx]"
              type="text"
              class="sop-input"
              maxlength="160"
              :placeholder="'上门步骤 ' + (idx + 4)"
            >
            <button
              type="button"
              class="btn btn--danger-outline btn--sm"
              :disabled="newFault.onsiteSteps.length <= 1"
              @click="removeNewFaultStep('onsite', idx)"
            >
              删除
            </button>
          </div>
        </div>
      </div>

      <div class="form-field">
        <span class="field-label">所需证据清单</span>
        <div class="evidence-checkbox-group">
          <label
            v-for="opt in evidenceOptions"
            :key="'nf-ev-' + opt.value"
            class="evidence-check-pill"
            :class="{ checked: newFault.requiredEvidence.includes(opt.value) }"
          >
            <input
              v-model="newFault.requiredEvidence"
              type="checkbox"
              class="checkbox"
              :value="opt.value"
            >
            <span>{{ opt.label }}</span>
          </label>
        </div>
      </div>

      <div class="new-fault-grid three">
        <label class="form-field">
          <span class="field-label">A级阈值（仅管理侧）</span>
          <input
            v-model.trim="newFault.gradeThresholds.A"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="平台故障码命中且有日志"
          >
        </label>
        <label class="form-field">
          <span class="field-label">B级阈值（仅管理侧）</span>
          <input
            v-model.trim="newFault.gradeThresholds.B"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="现场实测/对照记录>=2项"
          >
        </label>
        <label class="form-field">
          <span class="field-label">C级阈值（仅管理侧）</span>
          <input
            v-model.trim="newFault.gradeThresholds.C"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="仅口述现象无日志"
          >
        </label>
      </div>

      <div class="new-fault-actions">
        <button type="button" class="btn btn--secondary" :disabled="busy" @click="cancelNewFault">
          取消
        </button>
        <button type="submit" class="btn btn--secondary" :disabled="busy">
          保存为草稿
        </button>
        <button
          type="button"
          class="btn btn--primary"
          :disabled="busy"
          @click="submitNewFault(true)"
        >
          创建并发布
        </button>
      </div>
    </form>

    <!-- 页内状态提示 -->
    <div
      v-if="statusNotice.text"
      class="status-banner"
      :class="statusNotice.tone"
      role="status"
      aria-live="polite"
    >
      <span>{{ statusNotice.text }}</span>
      <button
        type="button"
        class="banner-close"
        aria-label="关闭提示"
        @click="statusNotice.text = ''"
      >
        ×
      </button>
    </div>

    <!-- 三栏主体结构 -->
    <div class="sop-columns">
      <SopCategoryTree
        :read-only="false"
        :loading="loading"
        :category-groups="categoryGroups"
        :active-category-name="activeCategoryName"
        @select="selectCategory"
      />

      <SopStepsPanel
        :read-only="false"
        :busy="busy"
        :active-group="activeGroup"
        :displayed-entry="displayedEntry"
        :is-viewing-non-default-version="isViewingNonDefaultVersion"
        :is-editing-sop="isEditingSop"
        :sop-draft="sopDraft"
        :evidence-options="evidenceOptions"
        :status-badge-class="statusBadgeClass"
        :status-label="statusLabel"
        @reset-version="selectedVersionId = null"
        @enter-edit="enterEditSop"
        @cancel-edit="cancelEditSop"
        @save-edit="saveEditSop"
        @add-step="addDraftStep"
        @remove-step="removeDraftStep"
      />

      <SopRulesSidebar
        :read-only="false"
        :busy="busy"
        :active-group="activeGroup"
        :displayed-entry="displayedEntry"
        :is-editing-rules="isEditingRules"
        :has-pending-rule-changes="hasPendingRuleChanges"
        :rule-draft="ruleDraft"
        :status-badge-class="statusBadgeClass"
        :status-label="statusLabel"
        @enter-edit-rules="enterEditRules"
        @cancel-rule-changes="cancelRuleChanges"
        @toggle-auto-rule="toggleAutoRule"
        @add-auto-rule="addAutoRule"
        @remove-auto-rule="removeAutoRule"
        @save-rule-changes="saveRuleChanges"
        @select-version="selectVersion"
        @publish-version="publishVersion"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  listAdminSops,
  publishAdminSop,
  saveAdminSop
} from '../../api/workbench'
import SopCategoryTree from '../../components/sop/SopCategoryTree.vue'
import SopRulesSidebar from '../../components/sop/SopRulesSidebar.vue'
import SopStepsPanel from '../../components/sop/SopStepsPanel.vue'
import { EVIDENCE_TYPE_LABELS } from '../../status'

const CATEGORY_ORDER = ['无法充电', '充电中断', '设备离线', '计费异常', '安装问题', '其他']

const evidenceOptions = Object.keys(EVIDENCE_TYPE_LABELS).map(value => ({
  value,
  label: EVIDENCE_TYPE_LABELS[value]
}))

const loading = ref(false)
const busy = ref(false)
const categoryGroups = ref([])
const activeCategoryName = ref('')
const selectedVersionId = ref(null)
const statusNotice = reactive({ text: '', tone: 'info' })

const isEditingSop = ref(false)
const sopDraft = reactive({
  title: '',
  version: '',
  evidenceMode: '',
  gradeHint: '',
  customerSteps: [],
  onsiteSteps: [],
  requiredEvidence: []
})

const isEditingRules = ref(false)
const hasPendingRuleChanges = ref(false)
const ruleDraft = reactive({
  version: '',
  gradeThresholds: { A: '', B: '', C: '' },
  autoApprovalRules: []
})

const showNewFaultForm = ref(false)
const newFault = reactive(createEmptyNewFault())

function createEmptyNewFault() {
  return {
    category: '',
    title: '',
    version: 'V1.0',
    evidenceMode: '时序+可视混合类',
    gradeHint: 'A级需平台报文/日志佐证；B级需现场采集与对照；C级仅现象描述禁止换装',
    customerSteps: ['拍摄设备铭牌与故障指示灯状态', '录制故障触发及复现短视频', '断电复位并记录故障发生时点'],
    onsiteSteps: ['现场检测供电、接线与相关部件状态', '读取设备故障码与平台报文对照', '确认故障部件后留痕处置并回收旧件'],
    requiredEvidence: ['photo', 'video', 'platform_data'],
    gradeThresholds: {
      A: '平台故障码命中且有日志',
      B: '现场实测/对照记录>=2项',
      C: '仅口述现象无日志'
    },
    autoApprovalRules: [
      { id: 'r1', label: '条件1 故障码命中换装清单', enabled: true },
      { id: 'r2', label: '条件2 必填证据齐全且设备在保', enabled: true },
      { id: 'r3', label: '条件3 同SN历史换装次数未超阈值(<=2次)', enabled: true }
    ]
  }
}

function normalizeSteps(raw) {
  if (!Array.isArray(raw)) return []
  return raw
    .map(item => (typeof item === 'string' ? item : item && item.text ? String(item.text) : ''))
    .map(text => text.trim())
    .filter(Boolean)
}

function normalizeEvidence(raw) {
  if (!Array.isArray(raw)) return []
  return raw.map(item => String(item || '').trim()).filter(Boolean)
}

function normalizeThresholds(raw) {
  const source = raw && typeof raw === 'object' ? raw : {}
  return {
    A: String(source.A || ''),
    B: String(source.B || ''),
    C: String(source.C || '')
  }
}

function normalizeAutoRules(raw) {
  if (!Array.isArray(raw)) return []
  return raw.map((rule, idx) => ({
    id: rule && rule.id ? String(rule.id) : `r${idx + 1}`,
    label: rule && rule.label ? String(rule.label) : `条件${idx + 1}`,
    desc: rule && rule.desc ? String(rule.desc) : '',
    enabled: Boolean(rule && (rule.enabled !== undefined ? rule.enabled : rule.autoApprove))
  }))
}

function toAdminEntry(row) {
  return {
    id: row.id,
    category: String(row.category || '').trim() || '未分类',
    title: String(row.title || '').trim() || String(row.category || ''),
    version: String(row.version || 'V1.0'),
    evidenceMode: String(row.evidenceMode || ''),
    customerSteps: normalizeSteps(row.customerSteps).slice(0, 3),
    onsiteSteps: normalizeSteps(row.onsiteSteps),
    requiredEvidence: normalizeEvidence(row.requiredEvidence),
    gradeHint: String(row.gradeHint || ''),
    updatedAt: row.updatedAt || '',
    status: String(row.status || 'published'),
    gradeThresholds: normalizeThresholds(row.gradeThresholds),
    autoApprovalRules: normalizeAutoRules(row.autoApprovalRules),
    updatedBy: String(row.updatedBy || '')
  }
}

function groupByCategory(entries) {
  const map = new Map()
  entries.forEach(raw => {
    const entry = toAdminEntry(raw)
    if (!map.has(entry.category)) {
      map.set(entry.category, [])
    }
    map.get(entry.category).push(entry)
  })

  const groups = Array.from(map.entries()).map(([category, versions]) => {
    const publishedEntry = versions.find(v => v.status === 'published') || null
    const latestDraft = versions.find(v => v.status === 'draft') || null
    const defaultEntry = publishedEntry || versions[0]
    return {
      category,
      versions,
      publishedEntry,
      latestDraft,
      defaultEntry
    }
  })

  groups.sort((a, b) => {
    const idxA = CATEGORY_ORDER.indexOf(a.category)
    const idxB = CATEGORY_ORDER.indexOf(b.category)
    if (idxA !== -1 && idxB !== -1) return idxA - idxB
    if (idxA !== -1) return -1
    if (idxB !== -1) return 1
    return a.category.localeCompare(b.category, 'zh-Hans-CN')
  })

  return groups
}

const activeGroup = computed(() => {
  if (!categoryGroups.value.length) return null
  return categoryGroups.value.find(g => g.category === activeCategoryName.value) || categoryGroups.value[0]
})

const displayedEntry = computed(() => {
  const group = activeGroup.value
  if (!group) return null
  if (selectedVersionId.value != null) {
    const found = group.versions.find(v => v.id === selectedVersionId.value)
    if (found) return found
  }
  return group.defaultEntry
})

const isViewingNonDefaultVersion = computed(() => {
  if (!activeGroup.value || !displayedEntry.value) return false
  return displayedEntry.value.id !== activeGroup.value.defaultEntry.id
})

function nextVersionString(ver) {
  const raw = String(ver || 'V1.0').trim()
  const match = raw.match(/^(V?)(\d+)\.(\d+)$/i)
  if (match) {
    const prefix = match[1] ? match[1].toUpperCase() : 'V'
    const major = Number(match[2])
    const minor = Number(match[3]) + 1
    return `${prefix}${major}.${minor}`
  }
  return raw + '.1'
}

function syncRuleDraftFromEntry(entry) {
  if (!entry) {
    ruleDraft.version = ''
    ruleDraft.gradeThresholds = { A: '', B: '', C: '' }
    ruleDraft.autoApprovalRules = []
    hasPendingRuleChanges.value = false
    isEditingRules.value = false
    return
  }
  ruleDraft.version = nextVersionString(entry.version)
  ruleDraft.gradeThresholds = { ...entry.gradeThresholds }
  ruleDraft.autoApprovalRules = entry.autoApprovalRules.map(r => ({ ...r }))
  hasPendingRuleChanges.value = false
  isEditingRules.value = false
}

watch(displayedEntry, entry => {
  syncRuleDraftFromEntry(entry)
})

function setNotice(text, tone = 'info') {
  statusNotice.text = text
  statusNotice.tone = tone
}

async function loadSops(preferredCategory, preferredVersionId) {
  loading.value = true
  isEditingSop.value = false
  isEditingRules.value = false
  hasPendingRuleChanges.value = false

  try {
    const rows = await listAdminSops()
    categoryGroups.value = groupByCategory(Array.isArray(rows) ? rows : [])

    const targetCategory = preferredCategory || activeCategoryName.value
    const foundGroup = categoryGroups.value.find(g => g.category === targetCategory)
    activeCategoryName.value = foundGroup
      ? foundGroup.category
      : (categoryGroups.value[0] ? categoryGroups.value[0].category : '')
    selectedVersionId.value = preferredVersionId || null

    if (displayedEntry.value) {
      syncRuleDraftFromEntry(displayedEntry.value)
    }
  } catch (err) {
    setNotice(err && err.message ? err.message : '加载 SOP 与规则列表失败', 'danger')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSops()
})

function selectCategory(category) {
  isEditingSop.value = false
  selectedVersionId.value = null
  activeCategoryName.value = category
}

function selectVersion(id) {
  isEditingSop.value = false
  selectedVersionId.value = id
}

function toggleNewFaultForm() {
  showNewFaultForm.value = !showNewFaultForm.value
  if (showNewFaultForm.value) {
    Object.assign(newFault, createEmptyNewFault())
  }
}

function cancelNewFault() {
  showNewFaultForm.value = false
}

function addNewFaultStep(stage) {
  if (stage === 'customer') {
    if (newFault.customerSteps.length < 3) newFault.customerSteps.push('')
  } else if (newFault.onsiteSteps.length < 3) {
    newFault.onsiteSteps.push('')
  }
}

function removeNewFaultStep(stage, idx) {
  if (stage === 'customer') {
    if (newFault.customerSteps.length > 1) newFault.customerSteps.splice(idx, 1)
  } else if (newFault.onsiteSteps.length > 1) {
    newFault.onsiteSteps.splice(idx, 1)
  }
}

async function submitNewFault(publishNow) {
  if (busy.value) return
  const category = newFault.category.trim()
  const title = (newFault.title || `${category} · SOP步骤`).trim()
  const version = newFault.version.trim() || 'V1.0'
  const customerSteps = newFault.customerSteps.map(s => s.trim()).filter(Boolean).slice(0, 3)
  const onsiteSteps = newFault.onsiteSteps.map(s => s.trim()).filter(Boolean)

  if (!category || !title || !version) {
    setNotice('请填写故障分类名称、SOP 标题和版本号', 'danger')
    return
  }
  if (!customerSteps.length || customerSteps.length > 3) {
    setNotice('客户可执行步骤需控制在 1 到 3 步以内', 'danger')
    return
  }
  if (!onsiteSteps.length) {
    setNotice('请至少填写 1 条上门验证步骤', 'danger')
    return
  }

  busy.value = true
  try {
    await saveAdminSop({
      category,
      title,
      version,
      evidenceMode: newFault.evidenceMode.trim(),
      customerSteps,
      onsiteSteps,
      requiredEvidence: [...newFault.requiredEvidence],
      gradeHint: newFault.gradeHint.trim(),
      gradeThresholds: { ...newFault.gradeThresholds },
      autoApprovalRules: newFault.autoApprovalRules.map(r => ({ ...r })),
      publishNow
    })
    showNewFaultForm.value = false
    await loadSops(category)
    setNotice(
      publishNow
        ? `已创建「${category}」${version} 并发布生效，已同步至只读知识库`
        : `已保存「${category}」${version} 为待发布草稿`,
      'info'
    )
  } catch (err) {
    setNotice(err && err.message ? err.message : '新建故障 SOP 失败', 'danger')
  } finally {
    busy.value = false
  }
}

function enterEditSop() {
  if (!displayedEntry.value) return
  const entry = displayedEntry.value
  sopDraft.title = entry.title
  sopDraft.version = entry.status === 'draft' ? entry.version : nextVersionString(entry.version)
  sopDraft.evidenceMode = entry.evidenceMode
  sopDraft.gradeHint = entry.gradeHint
  sopDraft.customerSteps = entry.customerSteps.length ? [...entry.customerSteps.slice(0, 3)] : ['']
  sopDraft.onsiteSteps = entry.onsiteSteps.length ? [...entry.onsiteSteps] : ['']
  sopDraft.requiredEvidence = [...entry.requiredEvidence]
  isEditingSop.value = true
}

function cancelEditSop() {
  isEditingSop.value = false
}

function addDraftStep(stage) {
  if (stage === 'customer') {
    if (sopDraft.customerSteps.length < 3) sopDraft.customerSteps.push('')
  } else if (sopDraft.onsiteSteps.length < 3) {
    sopDraft.onsiteSteps.push('')
  }
}

function removeDraftStep(stage, idx) {
  if (stage === 'customer') {
    if (sopDraft.customerSteps.length > 1) sopDraft.customerSteps.splice(idx, 1)
  } else if (sopDraft.onsiteSteps.length > 1) {
    sopDraft.onsiteSteps.splice(idx, 1)
  }
}

async function saveEditSop(publishNow) {
  if (!displayedEntry.value || busy.value) return
  const entry = displayedEntry.value
  const customerSteps = sopDraft.customerSteps.map(s => s.trim()).filter(Boolean).slice(0, 3)
  const onsiteSteps = sopDraft.onsiteSteps.map(s => s.trim()).filter(Boolean)

  if (!sopDraft.title.trim() || !sopDraft.version.trim()) {
    setNotice('SOP 标题和版本号不能为空', 'danger')
    return
  }
  if (!customerSteps.length || customerSteps.length > 3) {
    setNotice('客户可执行步骤需控制在 1 到 3 步以内', 'danger')
    return
  }
  if (!onsiteSteps.length) {
    setNotice('请至少保留 1 条上门验证步骤', 'danger')
    return
  }

  busy.value = true
  try {
    await saveAdminSop({
      category: entry.category,
      title: sopDraft.title.trim(),
      version: sopDraft.version.trim(),
      evidenceMode: sopDraft.evidenceMode.trim(),
      customerSteps,
      onsiteSteps,
      requiredEvidence: [...sopDraft.requiredEvidence],
      gradeHint: sopDraft.gradeHint.trim(),
      gradeThresholds: { ...ruleDraft.gradeThresholds },
      autoApprovalRules: ruleDraft.autoApprovalRules.map(r => ({
        id: r.id,
        label: r.label,
        enabled: Boolean(r.enabled),
        ...(r.desc ? { desc: r.desc } : {})
      })),
      publishNow
    })
    isEditingSop.value = false
    await loadSops(entry.category)
    setNotice(
      publishNow
        ? `已保存并发布「${entry.category}」${sopDraft.version.trim()}，已单向同步至知识库`
        : `已保存「${entry.category}」${sopDraft.version.trim()} 为草稿（待评审发布）`,
      'info'
    )
  } catch (err) {
    setNotice(err && err.message ? err.message : '保存 SOP 失败', 'danger')
  } finally {
    busy.value = false
  }
}

function enterEditRules() {
  if (!displayedEntry.value) return
  isEditingRules.value = true
}

function cancelRuleChanges() {
  syncRuleDraftFromEntry(displayedEntry.value)
}

function toggleAutoRule(rule) {
  rule.enabled = !rule.enabled
  hasPendingRuleChanges.value = true
}

function addAutoRule() {
  const nextIndex = ruleDraft.autoApprovalRules.length + 1
  ruleDraft.autoApprovalRules.push({
    id: `r${Date.now()}`,
    label: `条件${nextIndex} 新增审批条件`,
    desc: '',
    enabled: false
  })
  hasPendingRuleChanges.value = true
}

function removeAutoRule(idx) {
  if (ruleDraft.autoApprovalRules.length <= 1) return
  ruleDraft.autoApprovalRules.splice(idx, 1)
  hasPendingRuleChanges.value = true
}

async function saveRuleChanges(publishNow) {
  if (!displayedEntry.value || busy.value) return
  const entry = displayedEntry.value
  const version = ruleDraft.version.trim() || nextVersionString(entry.version)
  const cleanRules = ruleDraft.autoApprovalRules
    .filter(r => r.label && r.label.trim())
    .map((r, idx) => ({
      id: r.id || `r${idx + 1}`,
      label: r.label.trim(),
      enabled: Boolean(r.enabled),
      ...(r.desc ? { desc: r.desc } : {})
    }))

  if (!cleanRules.length) {
    setNotice('请至少保留一条换装自动审批条件', 'danger')
    return
  }

  busy.value = true
  try {
    await saveAdminSop({
      category: entry.category,
      title: entry.title,
      version,
      evidenceMode: entry.evidenceMode,
      customerSteps: entry.customerSteps.slice(0, 3),
      onsiteSteps: entry.onsiteSteps,
      requiredEvidence: [...entry.requiredEvidence],
      gradeHint: entry.gradeHint,
      gradeThresholds: {
        A: ruleDraft.gradeThresholds.A.trim(),
        B: ruleDraft.gradeThresholds.B.trim(),
        C: ruleDraft.gradeThresholds.C.trim()
      },
      autoApprovalRules: cleanRules,
      publishNow
    })
    await loadSops(entry.category)
    setNotice(
      publishNow
        ? `「${entry.category}」规则 ${version} 已保存并发布生效`
        : `「${entry.category}」规则变更已保存为草稿 ${version}`,
      'info'
    )
  } catch (err) {
    setNotice(err && err.message ? err.message : '保存审批规则失败', 'danger')
  } finally {
    busy.value = false
  }
}

async function handleReview() {
  if (!activeGroup.value || !displayedEntry.value || busy.value) return
  if (isEditingSop.value) {
    await saveEditSop(false)
    return
  }
  if (isEditingRules.value || hasPendingRuleChanges.value) {
    await saveRuleChanges(false)
    return
  }
  const draft = activeGroup.value.latestDraft
  if (draft) {
    setNotice(
      `已提交「${activeGroup.value.category}」草稿 ${draft.version} 评审，确认无误后可点击「发布」生效`,
      'warn'
    )
    return
  }
  setNotice(
    `「${activeGroup.value.category}」当前无待评审草稿，可先点击「编辑 SOP」或「⚙ 设置规则」生成新版本草稿`,
    'info'
  )
}

async function handleTopbarPublish() {
  if (!activeGroup.value || !displayedEntry.value || busy.value) return
  if (isEditingSop.value) {
    await saveEditSop(true)
    return
  }
  if (isEditingRules.value || hasPendingRuleChanges.value) {
    await saveRuleChanges(true)
    return
  }
  const draft = activeGroup.value.latestDraft
  if (draft) {
    await publishVersion(draft)
    return
  }
  setNotice(
    `「${activeGroup.value.category}」当前版本 ${displayedEntry.value.version} 已为最新生效状态`,
    'info'
  )
}

async function publishVersion(ver) {
  if (!ver || !ver.id || busy.value) return
  busy.value = true
  try {
    await publishAdminSop(ver.id)
    await loadSops(activeCategoryName.value)
    setNotice(`「${activeCategoryName.value}」${ver.version} 已发布生效，并已单向同步至知识库`, 'info')
  } catch (err) {
    setNotice(err && err.message ? err.message : '发布 SOP 版本失败', 'danger')
  } finally {
    busy.value = false
  }
}

function statusBadgeClass(status) {
  if (status === 'published') return 'badge--success'
  if (status === 'draft') return 'badge--warning'
  return ''
}

function statusLabel(status) {
  if (status === 'published') return '已发布'
  if (status === 'draft') return '草稿（待发布）'
  return '已归档'
}
</script>

<style scoped>
.sop-workspace {
  display: grid;
  gap: var(--gutter);
  color: var(--text-primary);
  font-size: var(--fs-body-sm);
  line-height: var(--lh-body-sm);
}

.sop-topbar {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-3);
  min-height: 48px;
  padding: var(--space-2) var(--card-px);
}

.sop-topbar.card--write-end {
  box-shadow: inset 0 3px 0 var(--warning-solid), var(--elev-1);
}

.topbar-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}

.sop-breadcrumb {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-body-sm);
  color: var(--text-tertiary);
}

.crumb-sep {
  color: var(--text-disabled);
}

.crumb-current {
  color: var(--text-primary);
  font-size: var(--fs-body);
  font-weight: var(--fw-semibold);
}

.topbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.new-fault-panel {
  display: grid;
  gap: var(--space-3);
  padding: var(--card-py) var(--card-px);
  border-color: var(--brand-200);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.panel-head h3 {
  margin: 0;
  font-size: var(--fs-title-sm);
  font-weight: var(--fw-semibold);
  color: var(--text-primary);
}

.panel-head p {
  margin: 2px 0 0;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.new-fault-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.new-fault-grid.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.new-fault-stages {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.mini-stage {
  display: grid;
  gap: var(--space-2);
}

.mini-stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: var(--fs-body-sm);
  color: var(--text-primary);
}

.mini-step-row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
}

.step-index {
  flex: none;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--brand-50);
  color: var(--brand-700);
  font-family: var(--font-num);
  font-size: var(--fs-caption);
  font-weight: var(--fw-semibold);
  font-variant-numeric: tabular-nums;
}

.step-index.onsite {
  background: var(--warning-bg);
  color: var(--warning-fg);
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

.new-fault-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.status-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: var(--fs-body-sm);
}

.status-banner.info {
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
  color: var(--brand-700);
}

.status-banner.warn {
  background: var(--warning-bg);
  border: 1px solid var(--warning-border);
  color: var(--warning-fg);
}

.status-banner.danger {
  background: var(--danger-bg);
  border: 1px solid var(--danger-border);
  color: var(--danger-fg);
}

.banner-close {
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 16px;
  line-height: 1;
  padding: 2px 6px;
  cursor: pointer;
}

.sop-columns {
  display: grid;
  grid-template-columns: minmax(220px, 252px) minmax(0, 1.45fr) minmax(300px, 356px);
  gap: var(--gutter);
  align-items: start;
}

@media (max-width: 1120px) {
  .sop-columns {
    grid-template-columns: minmax(220px, 252px) minmax(0, 1fr);
  }

  .new-fault-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 780px) {
  .sop-columns,
  .new-fault-stages,
  .new-fault-grid,
  .new-fault-grid.three {
    grid-template-columns: 1fr;
  }
}
</style>
