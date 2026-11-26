<template>
  <section class="sop-workspace" aria-label="知识库">
    <header class="card sop-topbar">
      <div class="topbar-left">
        <nav class="sop-breadcrumb" aria-label="当前位置">
          <span>知识库</span>
          <span class="crumb-sep" aria-hidden="true">/</span>
          <strong class="crumb-current">{{ activeGroup ? activeGroup.category : '全部故障' }}</strong>
        </nav>

        <span class="badge badge--lock">
          只读发布版 · 仅展示生效 SOP
        </span>
      </div>
    </header>

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

    <div class="sop-columns">
      <SopCategoryTree
        :read-only="true"
        :loading="loading"
        :category-groups="categoryGroups"
        :active-category-name="activeCategoryName"
        @select="selectCategory"
      />

      <SopStepsPanel
        :read-only="true"
        :busy="false"
        :active-group="activeGroup"
        :displayed-entry="displayedEntry"
        :is-viewing-non-default-version="false"
        :is-editing-sop="false"
        :sop-draft="readonlySopDraft"
        :evidence-options="[]"
        :status-badge-class="noopStatusBadge"
        :status-label="noopStatusLabel"
      />

      <SopRulesSidebar
        :read-only="true"
        :busy="false"
        :active-group="activeGroup"
        :displayed-entry="displayedEntry"
        :is-editing-rules="false"
        :has-pending-rule-changes="false"
        :rule-draft="readonlyRuleDraft"
        :status-badge-class="noopStatusBadge"
        :status-label="noopStatusLabel"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { listKnowledgeSops } from '../../api/workbench'
import SopCategoryTree from '../../components/sop/SopCategoryTree.vue'
import SopRulesSidebar from '../../components/sop/SopRulesSidebar.vue'
import SopStepsPanel from '../../components/sop/SopStepsPanel.vue'

const CATEGORY_ORDER = ['无法充电', '充电中断', '设备离线', '计费异常', '安装问题', '其他']

const FALLBACK_KNOWLEDGE_SOPS = [
  {
    id: 'fallback-no-charge',
    category: '无法充电',
    title: '无法充电 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '时序+可视混合类',
    customerSteps: [
      '确认车辆与枪头连接牢固并无异物',
      '确认桩体通电、空开合闸与指示灯状态',
      '引导客户断电1分钟后重启复测'
    ],
    onsiteSteps: [
      '现场测量主回路电压与PE接地',
      '读取设备故障码与实时报文',
      '执行换件或换桩判定并留存旧件'
    ],
    requiredEvidence: ['photo', 'video', 'platform_data'],
    gradeHint: 'A级需平台报文/日志佐证；B级需现场采集与对照；C级仅现象描述禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-interrupt',
    category: '充电中断',
    title: '充电中断 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '时序类（故障时点+充电记录）',
    customerSteps: [
      '确认中断精确时间（到分钟）及车端提示',
      '观察桩体告警灯并重新插拔枪头',
      '条件允许时换车或同站另一把枪对照测试'
    ],
    onsiteSteps: [
      '调取平台充电会话停止原因与电流曲线',
      '现场检测枪温、接触器与接地电阻',
      '单变量替换测试并留痕'
    ],
    requiredEvidence: ['timestamp_log', 'platform_data', 'swap_test'],
    gradeHint: 'A级需平台停止原因+日志；B级需同站对照或替换测试；C级禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-offline',
    category: '设备离线',
    title: '设备离线 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '时序/通信类（照片无效）',
    customerSteps: [
      '确认桩体电源正常与网络指示灯状态',
      '记录断网大致时间并观察周边4G信号',
      '引导断电1分钟后上电观察重连'
    ],
    onsiteSteps: [
      '核查SIM卡槽接触与天线连接',
      '核对SIM到期时间与程序版本',
      '采集本地通信日志并与同站其他桩对照'
    ],
    requiredEvidence: ['timestamp_log', 'site_compare', 'platform_data'],
    gradeHint: 'A级需通信日志/SIM状态佐证；B级需现场信号与卡槽核查；C级禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-billing',
    category: '计费异常',
    title: '计费异常 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '平台账单+电量对照',
    customerSteps: [
      '核对充电订单号、起止时间与账单金额',
      '确认车端表显充电量与结算电量差异',
      '留存异常订单截图或订单编号'
    ],
    onsiteSteps: [
      '核查电表计量模块脉冲与CT接线',
      '对比平台原始报文电量与本地电表读数',
      '确认计量误差是否超出国标范围'
    ],
    requiredEvidence: ['platform_data', 'timestamp_log'],
    gradeHint: 'A级需电表与平台报文差异佐证；B级需现场校准记录；C级禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-install',
    category: '安装问题',
    title: '安装问题 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '可视类（现场照片为主）',
    customerSteps: [
      '确认现场无漏电冒烟等安全风险',
      '请客户拍摄安装位置、配电箱与铭牌照片',
      '核对原始安装验收单与施工服务商'
    ],
    onsiteSteps: [
      '现场核验线径、空开规格与接地阻值',
      '留存整改前后对比照片',
      '关联原安装服务商责任归属'
    ],
    requiredEvidence: ['photo', 'video'],
    gradeHint: '安装施工问题优先走安装整改，非设备本身故障禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-other',
    category: '其他',
    title: '其他异常 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '混合类',
    customerSteps: [
      '确认现象发生时间与具体表现',
      '收集清晰照片或短视频记录现场情况',
      '排查基础供电与环境因素'
    ],
    onsiteSteps: [
      '现场复现故障现象并采集日志',
      '按实际症状归入对应故障大类',
      '形成书面排查结论'
    ],
    requiredEvidence: ['photo', 'timestamp_log'],
    gradeHint: '需先归类补充证据后再行判定，C级禁止换装',
    updatedAt: '2026-11-25 10:00'
  }
]

const readonlySopDraft = {
  title: '',
  version: '',
  evidenceMode: '',
  gradeHint: '',
  customerSteps: [],
  onsiteSteps: [],
  requiredEvidence: []
}
const readonlyRuleDraft = {}

const loading = ref(false)
const categoryGroups = ref([])
const activeCategoryName = ref('')
const statusNotice = reactive({ text: '', tone: 'info' })

function noopStatusBadge() {
  return ''
}

function noopStatusLabel() {
  return ''
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

function toReadOnlyEntry(row) {
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
    updatedAt: row.updatedAt || ''
  }
}

function groupByCategory(entries) {
  const map = new Map()
  entries.forEach(raw => {
    const entry = toReadOnlyEntry(raw)
    if (!map.has(entry.category)) {
      map.set(entry.category, [])
    }
    map.get(entry.category).push(entry)
  })

  const groups = Array.from(map.entries()).map(([category, versions]) => ({
    category,
    versions,
    publishedEntry: versions[0] || null,
    latestDraft: null,
    defaultEntry: versions[0]
  }))

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
  return activeGroup.value ? activeGroup.value.defaultEntry : null
})

function selectCategory(category) {
  activeCategoryName.value = category
}

async function loadSops() {
  loading.value = true
  statusNotice.text = ''
  try {
    const rows = await listKnowledgeSops()
    const list = Array.isArray(rows) && rows.length ? rows : FALLBACK_KNOWLEDGE_SOPS
    if (!Array.isArray(rows) || !rows.length) {
      statusNotice.text = '后端暂无已发布 SOP，当前展示内置只读样例'
      statusNotice.tone = 'warn'
    }
    categoryGroups.value = groupByCategory(list)
    activeCategoryName.value = categoryGroups.value[0] ? categoryGroups.value[0].category : ''
  } catch (ignore) {
    categoryGroups.value = groupByCategory(FALLBACK_KNOWLEDGE_SOPS)
    activeCategoryName.value = categoryGroups.value[0] ? categoryGroups.value[0].category : ''
    statusNotice.text = '知识库接口暂不可用，已自动切换为内置只读样例'
    statusNotice.tone = 'warn'
  } finally {
    loading.value = false
  }
}

onMounted(loadSops)
</script>

<style scoped>
.sop-workspace {
  display: grid;
  gap: var(--gutter);
  color: var(--text-primary);
  font-size: var(--fs-body-sm);
  line-height: var(--lh-body-sm);
}

.badge--lock::before {
  content: "";
  width: 12px;
  height: 12px;
  flex: none;
  background: currentColor;
  -webkit-mask: var(--icon-lock) center / contain no-repeat;
  mask: var(--icon-lock) center / contain no-repeat;
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
}

@media (max-width: 780px) {
  .sop-columns {
    grid-template-columns: 1fr;
  }
}
</style>
