<template>
  <section class="sop-workspace" aria-label="知识库与 SOP 管理">
    <!-- 顶部操作条（浅色白底卡片，严禁深蓝顶栏） -->
    <header class="sop-topbar">
      <div class="topbar-left">
        <nav class="sop-breadcrumb" aria-label="当前位置">
          <span>知识库与 SOP 管理</span>
          <span class="crumb-sep" aria-hidden="true">/</span>
          <strong class="crumb-current">{{ activeCategory.name }}</strong>
        </nav>
        <span class="demo-badge">配置演示 · 模拟数据</span>
      </div>
      <div class="topbar-actions">
        <button
          type="button"
          class="btn-sop btn-secondary"
          :aria-expanded="showNewCategoryForm"
          @click="toggleNewCategoryForm"
        >
          + 新建分类（演示）
        </button>
        <button
          type="button"
          class="btn-sop btn-secondary"
          @click="handleReview"
        >
          评审
        </button>
        <button
          type="button"
          class="btn-sop btn-primary"
          @click="handlePublish"
        >
          发布
        </button>
      </div>
    </header>

    <!-- 新建分类（演示）内联面板 -->
    <form
      v-if="showNewCategoryForm"
      class="new-category-bar"
      @submit.prevent="submitNewCategory"
    >
      <div class="new-category-field">
        <label class="field-label" for="new-sop-category">新建故障分类名称</label>
        <input
          id="new-sop-category"
          v-model.trim="newCategoryName"
          type="text"
          class="sop-input"
          maxlength="24"
          placeholder="例如：屏幕黑屏、枪锁卡滞"
          required
        >
      </div>
      <div class="new-category-actions">
        <button type="submit" class="btn-sop btn-primary" :disabled="!newCategoryName">
          确认添加
        </button>
        <button type="button" class="btn-sop btn-secondary" @click="cancelNewCategory">
          取消
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
      <!-- 左栏：故障分类树 -->
      <aside class="sop-card tree-card" aria-label="故障分类树">
        <div class="card-head">
          <h2>故障分类树</h2>
          <span class="head-sub">共 {{ categories.length }} 个分类</span>
        </div>

        <label class="search-box">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="m16 16 4.5 4.5" />
          </svg>
          <span class="visually-hidden">搜索故障分类</span>
          <input
            v-model.trim="categorySearch"
            type="search"
            placeholder="搜索分类名称…"
            maxlength="40"
            autocomplete="off"
          >
        </label>

        <ul v-if="filteredCategories.length" class="category-tree">
          <li v-for="cat in filteredCategories" :key="cat.id">
            <button
              type="button"
              class="tree-node"
              :class="{ active: cat.id === activeCategoryId }"
              :aria-current="cat.id === activeCategoryId ? 'true' : undefined"
              @click="selectCategory(cat.id)"
            >
              <span class="tree-node-name">{{ cat.name }}</span>
              <span class="tree-node-meta">
                <span class="tree-ver">{{ cat.currentVersion }}</span>
                <span class="tree-count">{{ cat.customerSteps.length + cat.onsiteSteps.length }}步</span>
              </span>
            </button>
          </li>
        </ul>
        <div v-else class="tree-empty">
          <p>未找到匹配的故障分类</p>
          <button type="button" class="link-btn" @click="categorySearch = ''">清空搜索</button>
        </div>

        <footer class="tree-footer">
          <span>共 {{ categories.length }} 个分类</span>
          <span v-if="categorySearch" class="tree-filtered">当前匹配 {{ filteredCategories.length }} 个</span>
        </footer>
      </aside>

      <!-- 中栏：两段式 SOP 步骤卡片 -->
      <section class="sop-card sop-main-card" aria-label="两段式 SOP 步骤">
        <div class="card-head main-head">
          <div class="main-title-wrap">
            <h2>{{ activeCategory.name }}</h2>
            <span class="version-badge">当前最新版本：{{ activeCategory.currentVersion }}</span>
          </div>
          <div class="main-head-actions">
            <button
              v-if="!isEditingSop"
              type="button"
              class="btn-sop btn-secondary"
              @click="enterEditSop"
            >
              编辑 SOP
            </button>
            <template v-else>
              <button type="button" class="btn-sop btn-secondary" @click="cancelEditSop">
                取消
              </button>
              <button type="button" class="btn-sop btn-primary" @click="saveEditSop">
                保存 SOP
              </button>
            </template>
          </div>
        </div>

        <p class="sop-summary-note">
          两段式排查标准：先引导客户完成远程自查（限 3 步内）并留痕，无法恢复再派单执行上门核验。
        </p>

        <!-- 第一段：客户可执行 1-3（控制 ≤3 步） -->
        <div class="sop-stage">
          <div class="stage-head">
            <div class="stage-title">
              <span class="stage-pill stage-customer">第一段</span>
              <h3>客户可执行 1-3</h3>
            </div>
            <span class="stage-limit">
              控制在 3 步以内（当前 {{ displayedCustomerSteps.length }}/3 步）
            </span>
          </div>

          <ol class="step-list">
            <li
              v-for="(step, idx) in displayedCustomerSteps"
              :key="step.id"
              class="step-item"
            >
              <span class="step-index">{{ idx + 1 }}</span>

              <div v-if="!isEditingSop" class="step-body">
                <p class="step-desc">{{ step.text }}</p>
                <span class="evidence-tag" :class="evidenceTone(step.evidence)">
                  {{ step.evidence }}
                </span>
              </div>

              <div v-else class="step-editor">
                <input
                  v-model.trim="step.text"
                  type="text"
                  class="sop-input"
                  maxlength="120"
                  :aria-label="'客户步骤 ' + (idx + 1) + ' 说明'"
                  placeholder="请输入客户排查步骤说明"
                >
                <div class="step-editor-row">
                  <select
                    v-model="step.evidence"
                    class="sop-select"
                    :aria-label="'客户步骤 ' + (idx + 1) + ' 证据要求'"
                  >
                    <option v-for="opt in customerEvidenceOptions" :key="opt" :value="opt">
                      {{ opt }}
                    </option>
                  </select>
                  <button
                    type="button"
                    class="btn-sop btn-danger-ghost"
                    :disabled="draftCustomerSteps.length <= 1"
                    @click="removeDraftStep('customer', idx)"
                  >
                    删除
                  </button>
                </div>
              </div>
            </li>
          </ol>

          <div v-if="isEditingSop" class="stage-edit-bar">
            <button
              type="button"
              class="btn-sop btn-secondary btn-sm"
              :disabled="draftCustomerSteps.length >= 3"
              @click="addDraftStep('customer')"
            >
              + 新增客户步骤
            </button>
            <span class="limit-hint">
              {{ draftCustomerSteps.length >= 3 ? '已达上限：客户可执行最多 3 步' : '还可新增 ' + (3 - draftCustomerSteps.length) + ' 步' }}
            </span>
          </div>
        </div>

        <!-- 第二段：上门验证 4-6 -->
        <div class="sop-stage stage-onsite-wrap">
          <div class="stage-head">
            <div class="stage-title">
              <span class="stage-pill stage-onsite">第二段</span>
              <h3>上门验证 4-6</h3>
            </div>
            <span class="stage-limit">
              现场核验与换装留痕（第 4–{{ 3 + displayedOnsiteSteps.length }} 步）
            </span>
          </div>

          <ol class="step-list">
            <li
              v-for="(step, idx) in displayedOnsiteSteps"
              :key="step.id"
              class="step-item onsite"
            >
              <span class="step-index onsite">{{ idx + 4 }}</span>

              <div v-if="!isEditingSop" class="step-body">
                <p class="step-desc">{{ step.text }}</p>
                <span class="evidence-tag" :class="evidenceTone(step.evidence)">
                  {{ step.evidence }}
                </span>
              </div>

              <div v-else class="step-editor">
                <input
                  v-model.trim="step.text"
                  type="text"
                  class="sop-input"
                  maxlength="120"
                  :aria-label="'上门步骤 ' + (idx + 4) + ' 说明'"
                  placeholder="请输入上门验证步骤说明"
                >
                <div class="step-editor-row">
                  <select
                    v-model="step.evidence"
                    class="sop-select"
                    :aria-label="'上门步骤 ' + (idx + 4) + ' 证据要求'"
                  >
                    <option v-for="opt in onsiteEvidenceOptions" :key="opt" :value="opt">
                      {{ opt }}
                    </option>
                  </select>
                  <button
                    type="button"
                    class="btn-sop btn-danger-ghost"
                    :disabled="draftOnsiteSteps.length <= 1"
                    @click="removeDraftStep('onsite', idx)"
                  >
                    删除
                  </button>
                </div>
              </div>
            </li>
          </ol>

          <div v-if="isEditingSop" class="stage-edit-bar">
            <button
              type="button"
              class="btn-sop btn-secondary btn-sm"
              :disabled="draftOnsiteSteps.length >= 3"
              @click="addDraftStep('onsite')"
            >
              + 新增上门步骤
            </button>
            <span class="limit-hint">
              {{ draftOnsiteSteps.length >= 3 ? '已达上限：上门验证第 4-6 步' : '还可新增 ' + (3 - draftOnsiteSteps.length) + ' 步' }}
            </span>
          </div>
        </div>
      </section>

      <!-- 右栏：上下两张卡 -->
      <aside class="sop-right-col" aria-label="规则与版本记录">
        <!-- 上卡：分级与审批规则 -->
        <section class="sop-card">
          <div class="card-head">
            <h2>分级与审批规则</h2>
            <span class="head-sub">实时演示</span>
          </div>

          <div class="grade-list" aria-label="故障证据分级标准">
            <div class="grade-item grade-a">
              <span class="grade-chip">A级 有后台数据</span>
              <small>平台日志/告警报文命中，可直接推进处置</small>
            </div>
            <div class="grade-item grade-b">
              <span class="grade-chip">B级 需多维佐证</span>
              <small>需结合现场照片、录像与时点日志综合判定</small>
            </div>
            <div class="grade-item grade-c">
              <span class="grade-chip">C级 仅现象描述（禁止换装）</span>
              <small>证据不足严禁发起换装，仅限远程引导或继续补证</small>
            </div>
          </div>

          <div class="auto-rules">
            <div class="sub-head">
              <h3>换装自动审批条件</h3>
              <span class="rule-summary">
                {{ autoPassCount }}/{{ activeCategory.autoRules.length }} 项自动通过
              </span>
            </div>

            <ul class="rule-list">
              <li
                v-for="rule in activeCategory.autoRules"
                :key="rule.id"
                class="rule-row"
              >
                <div class="rule-info">
                  <strong>{{ rule.label }}</strong>
                  <small>{{ rule.desc }}</small>
                </div>
                <button
                  type="button"
                  role="switch"
                  class="rule-switch"
                  :class="{ on: rule.autoApprove }"
                  :aria-checked="rule.autoApprove"
                  :aria-label="rule.label + '：当前为' + (rule.autoApprove ? '自动通过' : '人工复核')"
                  @click="toggleAutoRule(rule)"
                >
                  <span class="switch-track" aria-hidden="true">
                    <span class="switch-thumb" />
                  </span>
                  <span class="switch-label">
                    {{ rule.autoApprove ? '自动通过' : '人工复核' }}
                  </span>
                </button>
              </li>
            </ul>
          </div>
        </section>

        <!-- 下卡：版本记录 -->
        <section class="sop-card">
          <div class="card-head">
            <h2>版本记录</h2>
            <span class="head-sub">共 {{ activeCategory.versions.length }} 个版本</span>
          </div>

          <ul class="version-list">
            <li
              v-for="ver in activeCategory.versions"
              :key="ver.version"
              class="version-item"
              :class="ver.tone"
            >
              <div class="version-top">
                <strong class="version-label">{{ ver.label }}</strong>
                <span class="version-state" :class="ver.tone">{{ ver.stateTag }}</span>
              </div>
              <div class="version-meta">
                <span>{{ ver.actor }}</span>
                <span>{{ ver.updatedAt }}</span>
              </div>
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'

const customerEvidenceOptions = [
  '必传 照片',
  '必传 录像',
  '必传 时点日志',
  '必传 截图'
]

const onsiteEvidenceOptions = [
  '必传 照片',
  '必传 旧件',
  '必传 录像',
  '必传 现场复测'
]

function defaultVersions() {
  return [
    {
      version: 'V2.3',
      label: 'V2.3 已发布（当前版本）',
      stateTag: '生效中',
      tone: 'published',
      actor: '张工（售后主管）',
      updatedAt: '2026-11-18 14:20'
    },
    {
      version: 'V2.2',
      label: 'V2.2 已下线（已归档）',
      stateTag: '已归档',
      tone: 'archived',
      actor: '张工（售后主管）',
      updatedAt: '2026-10-09 09:45'
    },
    {
      version: 'V2.4',
      label: 'V2.4 评审中（待发布）',
      stateTag: '评审中',
      tone: 'reviewing',
      actor: '李敏（技术支持）',
      updatedAt: '2026-11-24 17:10'
    }
  ]
}

function defaultRules() {
  return [
    {
      id: 'rule-code',
      label: '条件1 故障码命中',
      desc: '平台上报致命故障码与知识库故障树匹配',
      autoApprove: true
    },
    {
      id: 'rule-evidence',
      label: '条件2 证据齐全',
      desc: '客户及现场必传照片、录像或旧件齐备',
      autoApprove: true
    },
    {
      id: 'rule-threshold',
      label: '条件3 质检/换装次数未超阈值',
      desc: '同桩近 90 天换装 ≤1 次且质检通过',
      autoApprove: false
    }
  ]
}

const categories = reactive([
  {
    id: 'cat-no-charge',
    name: '无法充电',
    currentVersion: 'V2.3',
    customerSteps: [
      { id: 'c-1', text: '核对枪头是否完全插紧，观察桩体指示灯与屏幕报错提示', evidence: '必传 照片' },
      { id: 'c-2', text: '记录扫码或刷卡启动失败的完整过程及屏幕倒计时画面', evidence: '必传 录像' },
      { id: 'c-3', text: '断电/急停复位重启后重新插枪，反馈故障发生精确时点', evidence: '必传 时点日志' }
    ],
    onsiteSteps: [
      { id: 'o-1', text: '现场测量输出端电压、CP 引导信号及枪锁微动开关状态', evidence: '必传 照片' },
      { id: 'o-2', text: '使用标准测试工装或同站邻桩对照复测，确认主板/接触器故障', evidence: '必传 照片' },
      { id: 'o-3', text: '如需换件或整桩换装，拆卸故障模块并贴标留存旧件照片', evidence: '必传 旧件' }
    ],
    autoRules: defaultRules(),
    versions: defaultVersions()
  },
  {
    id: 'cat-interrupt',
    name: '充电中断',
    currentVersion: 'V2.3',
    customerSteps: [
      { id: 'c-21', text: '拍摄小程序订单结束页面及车端仪表盘停充提示信息', evidence: '必传 照片' },
      { id: 'c-22', text: '说明停充发生时间点、SOC 电量百分比及是否触发急停误碰', evidence: '必传 时点日志' },
      { id: 'c-23', text: '录制重新插枪启动后 30 秒内是否再次跳枪的现象视频', evidence: '必传 录像' }
    ],
    onsiteSteps: [
      { id: 'o-21', text: '检查枪线温感 NTC 阻值、急停回路与内部端子紧固力矩', evidence: '必传 照片' },
      { id: 'o-22', text: '调取桩端本地断充报文并与充电桩平台记录核对停机码', evidence: '必传 照片' },
      { id: 'o-23', text: '更换异常枪线或继电器后实车复测，旧件编号登记回收', evidence: '必传 旧件' }
    ],
    autoRules: defaultRules(),
    versions: defaultVersions()
  },
  {
    id: 'cat-offline',
    name: '设备离线',
    currentVersion: 'V2.3',
    customerSteps: [
      { id: 'c-31', text: '拍摄桩体供电空开及电源指示灯状态，确认设备已通电', evidence: '必传 照片' },
      { id: 'c-32', text: '反馈所处地库/车位信号格数及故障发生时间点', evidence: '必传 时点日志' },
      { id: 'c-33', text: '拉闸断电 30 秒后合闸重启，录制指示灯联网闪烁过程', evidence: '必传 录像' }
    ],
    onsiteSteps: [
      { id: 'o-31', text: '开盖检测 4G 天线馈线接头、物联网卡状态及现场 RSRP 信号值', evidence: '必传 照片' },
      { id: 'o-32', text: '替换备用通信模组或物联卡验证平台心跳恢复上线', evidence: '必传 照片' },
      { id: 'o-33', text: '确认通信模组损坏换新后，留存拆下旧件及 SN 条码照片', evidence: '必传 旧件' }
    ],
    autoRules: defaultRules(),
    versions: defaultVersions()
  },
  {
    id: 'cat-billing',
    name: '计费异常',
    currentVersion: 'V2.3',
    customerSteps: [
      { id: 'c-41', text: '上传账单扣费明细截图及场站现场公示电价牌照片', evidence: '必传 照片' },
      { id: 'c-42', text: '提供订单起止时间、异常电量或未退预授权的精确时点', evidence: '必传 时点日志' },
      { id: 'c-43', text: '若屏幕电量与手机端不一致，录制桩屏显示与订单对比视频', evidence: '必传 录像' }
    ],
    onsiteSteps: [
      { id: 'o-41', text: '现场核查电表脉冲计数、计量芯片版本及费率下发配置', evidence: '必传 照片' },
      { id: 'o-42', text: '使用校验负荷测试电量精度，核对计量模块铅封完好性', evidence: '必传 照片' },
      { id: 'o-43', text: '计量电表超差更换时，登记旧电表底数并留存旧件照片', evidence: '必传 旧件' }
    ],
    autoRules: defaultRules(),
    versions: defaultVersions()
  },
  {
    id: 'cat-install',
    name: '安装问题',
    currentVersion: 'V2.3',
    customerSteps: [
      { id: 'c-51', text: '拍摄安装位置、配电箱空开规格及线缆走线全景照片', evidence: '必传 照片' },
      { id: 'c-52', text: '拍摄支架松动、防水封堵缺失或线缆异响等具体问题视频', evidence: '必传 录像' },
      { id: 'c-53', text: '记录智享乐装工单完工时间及发现安装隐患的时间点', evidence: '必传 时点日志' }
    ],
    onsiteSteps: [
      { id: 'o-51', text: '现场复测接地电阻、漏保动作及线径规格是否符合安装规范', evidence: '必传 照片' },
      { id: 'o-52', text: '整改接线或加固立柱后拍摄整机垂直度与防雨密封验收照', evidence: '必传 照片' },
      { id: 'o-53', text: '因安装损坏的受损配件或破损壳体拆除留痕并回收旧件', evidence: '必传 旧件' }
    ],
    autoRules: defaultRules(),
    versions: defaultVersions()
  },
  {
    id: 'cat-other',
    name: '其他',
    currentVersion: 'V2.3',
    customerSteps: [
      { id: 'c-61', text: '拍摄设备铭牌、外观异常部位及周围环境清晰照片', evidence: '必传 照片' },
      { id: 'c-62', text: '录制异常声音、屏幕闪烁或机械卡滞的连续现象视频', evidence: '必传 录像' },
      { id: 'c-63', text: '补充描述首次出现异常的时间点及触发场景', evidence: '必传 时点日志' }
    ],
    onsiteSteps: [
      { id: 'o-61', text: '现场全面巡检外观结构、内部线束及基础电气安全指标', evidence: '必传 照片' },
      { id: 'o-62', text: '核验辅助功能模块运行状态并完成一次完整充放电测试', evidence: '必传 照片' },
      { id: 'o-63', text: '如涉及配件更换，完成旧件回收登记并上传对比照片', evidence: '必传 旧件' }
    ],
    autoRules: defaultRules(),
    versions: defaultVersions()
  }
])

const activeCategoryId = ref('cat-no-charge')
const categorySearch = ref('')
const showNewCategoryForm = ref(false)
const newCategoryName = ref('')
const isEditingSop = ref(false)
const draftCustomerSteps = ref([])
const draftOnsiteSteps = ref([])
const statusNotice = reactive({ text: '', tone: 'info' })

const activeCategory = computed(() => {
  return categories.find(item => item.id === activeCategoryId.value) || categories[0]
})

const filteredCategories = computed(() => {
  const keyword = categorySearch.value.toLowerCase()
  if (!keyword) return categories
  return categories.filter(item => item.name.toLowerCase().includes(keyword))
})

const displayedCustomerSteps = computed(() => {
  return isEditingSop.value ? draftCustomerSteps.value : activeCategory.value.customerSteps
})

const displayedOnsiteSteps = computed(() => {
  return isEditingSop.value ? draftOnsiteSteps.value : activeCategory.value.onsiteSteps
})

const autoPassCount = computed(() => {
  return activeCategory.value.autoRules.filter(rule => rule.autoApprove).length
})

function setNotice(text, tone = 'info') {
  statusNotice.text = text
  statusNotice.tone = tone
}

function selectCategory(id) {
  if (isEditingSop.value) {
    isEditingSop.value = false
  }
  activeCategoryId.value = id
}

function toggleNewCategoryForm() {
  showNewCategoryForm.value = !showNewCategoryForm.value
  if (!showNewCategoryForm.value) {
    newCategoryName.value = ''
  }
}

function cancelNewCategory() {
  showNewCategoryForm.value = false
  newCategoryName.value = ''
}

function submitNewCategory() {
  const name = newCategoryName.value.trim()
  if (!name) return
  const exists = categories.some(item => item.name === name)
  if (exists) {
    setNotice(`分类「${name}」已存在，请直接选择或更换名称（配置演示 · 模拟数据）`, 'warn')
    return
  }
  const newId = 'cat-demo-' + Date.now()
  categories.push({
    id: newId,
    name,
    currentVersion: 'V2.3',
    customerSteps: [
      { id: `${newId}-c1`, text: `引导客户拍摄「${name}」故障现场及桩体指示灯状态`, evidence: '必传 照片' },
      { id: `${newId}-c2`, text: `录制「${name}」异常复现过程短视频`, evidence: '必传 录像' },
      { id: `${newId}-c3`, text: '记录故障触发时点并尝试重启复测', evidence: '必传 时点日志' }
    ],
    onsiteSteps: [
      { id: `${newId}-o1`, text: `现场核查「${name}」关联硬件与接线状态`, evidence: '必传 照片' },
      { id: `${newId}-o2`, text: '完成功能复测并留存处置后照片', evidence: '必传 照片' },
      { id: `${newId}-o3`, text: '涉及更换部件时回收旧件并拍照登记', evidence: '必传 旧件' }
    ],
    autoRules: defaultRules(),
    versions: defaultVersions()
  })
  activeCategoryId.value = newId
  isEditingSop.value = false
  showNewCategoryForm.value = false
  newCategoryName.value = ''
  setNotice(`已新增演示分类「${name}」，默认载入两段式 SOP 与规则模板（配置演示 · 模拟数据）`, 'info')
}

function enterEditSop() {
  draftCustomerSteps.value = activeCategory.value.customerSteps.map(step => ({ ...step }))
  draftOnsiteSteps.value = activeCategory.value.onsiteSteps.map(step => ({ ...step }))
  isEditingSop.value = true
}

function cancelEditSop() {
  isEditingSop.value = false
  draftCustomerSteps.value = []
  draftOnsiteSteps.value = []
}

function addDraftStep(stage) {
  if (stage === 'customer') {
    if (draftCustomerSteps.value.length >= 3) return
    draftCustomerSteps.value.push({
      id: 'c-new-' + Date.now(),
      text: '请输入新的客户排查步骤',
      evidence: '必传 照片'
    })
    return
  }
  if (draftOnsiteSteps.value.length >= 3) return
  draftOnsiteSteps.value.push({
    id: 'o-new-' + Date.now(),
    text: '请输入新的上门验证步骤',
    evidence: '必传 照片'
  })
}

function removeDraftStep(stage, idx) {
  if (stage === 'customer') {
    if (draftCustomerSteps.value.length <= 1) return
    draftCustomerSteps.value.splice(idx, 1)
    return
  }
  if (draftOnsiteSteps.value.length <= 1) return
  draftOnsiteSteps.value.splice(idx, 1)
}

function saveEditSop() {
  const validCustomer = draftCustomerSteps.value
    .slice(0, 3)
    .every(step => step.text && step.text.trim())
  const validOnsite = draftOnsiteSteps.value
    .slice(0, 3)
    .every(step => step.text && step.text.trim())

  if (!validCustomer || !validOnsite) {
    setNotice('步骤说明不能为空，请补充完整后再保存', 'danger')
    return
  }

  activeCategory.value.customerSteps = draftCustomerSteps.value
    .slice(0, 3)
    .map(step => ({ ...step, text: step.text.trim() }))
  activeCategory.value.onsiteSteps = draftOnsiteSteps.value
    .slice(0, 3)
    .map(step => ({ ...step, text: step.text.trim() }))

  isEditingSop.value = false
  setNotice(`已保存「${activeCategory.value.name}」SOP 步骤变更，当前页面实时生效（配置演示 · 模拟数据）`, 'info')
}

function toggleAutoRule(rule) {
  rule.autoApprove = !rule.autoApprove
  setNotice(
    `「${activeCategory.value.name}」${rule.label} 已切换为：${rule.autoApprove ? '自动通过' : '人工复核'}（配置演示 · 模拟数据）`,
    rule.autoApprove ? 'info' : 'warn'
  )
}

function handleReview() {
  const reviewing = activeCategory.value.versions.find(v => v.tone === 'reviewing')
  if (reviewing) {
    reviewing.updatedAt = '刚刚（已发起评审）'
  }
  setNotice(
    `已提交「${activeCategory.value.name}」SOP 评审通知：${reviewing ? reviewing.label : '待评审版本'}，等待质检与技术负责人确认（配置演示 · 模拟数据）`,
    'warn'
  )
}

function handlePublish() {
  const current = activeCategory.value.versions.find(v => v.tone === 'published')
  const reviewing = activeCategory.value.versions.find(v => v.tone === 'reviewing')
  if (reviewing && current) {
    current.tone = 'archived'
    current.stateTag = '已归档'
    current.label = `${current.version} 已下线（已归档）`

    reviewing.tone = 'published'
    reviewing.stateTag = '生效中'
    reviewing.label = `${reviewing.version} 已发布（当前版本）`
    reviewing.updatedAt = '刚刚（演示发布）'
    activeCategory.value.currentVersion = reviewing.version

    const nextNum = (Number.parseFloat(reviewing.version.replace('V', '')) + 0.1).toFixed(1)
    activeCategory.value.versions.push({
      version: `V${nextNum}`,
      label: `V${nextNum} 评审中（待发布）`,
      stateTag: '评审中',
      tone: 'reviewing',
      actor: '当前坐席（配置演示）',
      updatedAt: '待提交评审'
    })
    setNotice(
      `「${activeCategory.value.name}」已发布 ${reviewing.version} 为当前生效版本（配置演示 · 模拟数据）`,
      'info'
    )
    return
  }
  setNotice(
    `「${activeCategory.value.name}」当前版本 ${activeCategory.value.currentVersion} 已处于最新生效状态（配置演示 · 模拟数据）`,
    'info'
  )
}

function evidenceTone(evidence) {
  if (evidence.includes('旧件')) return 'warn'
  if (evidence.includes('录像')) return 'teal-deep'
  if (evidence.includes('时点')) return 'neutral'
  return 'teal'
}
</script>

<style scoped>
.sop-workspace {
  display: grid;
  gap: 14px;
  color: var(--ink, #334155);
  font-size: 13px;
  line-height: 1.5;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

/* 顶部操作条：浅色白卡片，不使用深蓝顶栏 */
.sop-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 10px;
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
  font-size: 13px;
  color: var(--muted, #8a96a5);
}

.crumb-sep {
  color: #cbd5e1;
}

.crumb-current {
  color: var(--heading, #0f172a);
  font-size: 15px;
  font-weight: 700;
}

.demo-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  background: #fff5dd;
  border: 1px solid #f3e2b8;
  color: #a86c08;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.topbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

/* 按钮体系：对齐松果青绿主题 */
.btn-sop {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  padding: 6px 13px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.btn-sop:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-sm {
  min-height: 30px;
  padding: 4px 10px;
}

.btn-primary {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #0d9488;
  border-color: #0d9488;
}

.btn-secondary {
  border: 1px solid #dfe5ec;
  background: #fff;
  color: #475569;
}

.btn-secondary:hover:not(:disabled) {
  border-color: #0d9488;
  color: #0f766e;
  background: #e8f6f4;
}

.btn-danger-ghost {
  border: 1px solid #f3c6c3;
  background: #fff;
  color: #c0362c;
  min-height: 32px;
  padding: 4px 10px;
}

.btn-danger-ghost:hover:not(:disabled) {
  background: #fdecec;
}

/* 新建分类内联条 */
.new-category-bar {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 10px;
}

.new-category-field {
  display: grid;
  gap: 5px;
  flex: 1 1 240px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.new-category-actions {
  display: flex;
  gap: 8px;
}

/* 页内状态提示 */
.status-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
}

.status-banner.info {
  background: #e8f6f4;
  border: 1px solid #c5e4e1;
  color: #0f766e;
}

.status-banner.warn {
  background: #fff5dd;
  border: 1px solid #f3e2b8;
  color: #a86c08;
}

.status-banner.danger {
  background: #fdecec;
  border: 1px solid #f5c2be;
  color: #c0362c;
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

/* 三栏主体 */
.sop-columns {
  display: grid;
  grid-template-columns: minmax(210px, 240px) minmax(0, 1.45fr) minmax(280px, 330px);
  gap: 14px;
  align-items: start;
}

.sop-card {
  min-width: 0;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 12px;
  box-shadow: 0 2px 8px #132c4906;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.card-head h2 {
  margin: 0;
  color: var(--heading, #0f172a);
  font-size: 15px;
  font-weight: 700;
}

.head-sub {
  color: var(--muted, #8a96a5);
  font-size: 12px;
  white-space: nowrap;
}

/* 左栏：故障分类树 */
.tree-card {
  display: flex;
  flex-direction: column;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  padding: 6px 10px;
  margin-bottom: 10px;
  border: 1px solid #dce3ec;
  border-radius: 8px;
  background: #fff;
}

.search-box:focus-within {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px #0d94881f;
}

.search-box svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: #94a3b8;
  stroke-width: 2;
  flex: none;
}

.search-box input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #334155;
  font-size: 12px;
}

.category-tree {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 4px;
}

.tree-node {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 40px;
  padding: 8px 10px 8px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #475569;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.tree-node:hover {
  background: #f8fafc;
  color: #0f172a;
}

.tree-node.active {
  background: #e8f6f4;
  border-color: #cdebe6;
  color: #0f766e;
  font-weight: 700;
}

.tree-node.active::before {
  content: "";
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 3px;
  background: #0d9488;
}

.tree-node-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  font-size: 11px;
  font-weight: 500;
}

.tree-ver {
  padding: 1px 5px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
}

.tree-node.active .tree-ver {
  background: #fff;
  color: #0f766e;
}

.tree-count {
  color: #94a3b8;
}

.tree-node.active .tree-count {
  color: #0d9488;
}

.tree-empty {
  padding: 20px 8px;
  text-align: center;
  color: var(--muted, #8a96a5);
  font-size: 12px;
}

.tree-empty p {
  margin: 0 0 6px;
}

.link-btn {
  border: 0;
  background: transparent;
  color: #0f766e;
  font-size: 12px;
  text-decoration: underline;
  cursor: pointer;
}

.tree-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--line, #eaeef2);
  color: var(--muted, #8a96a5);
  font-size: 12px;
}

.tree-filtered {
  color: #0f766e;
}

/* 中栏：两段式 SOP */
.main-head {
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.main-title-wrap {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.main-title-wrap h2 {
  font-size: 16px;
}

.version-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  background: #e8f6f4;
  border: 1px solid #c5e4e1;
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
}

.main-head-actions {
  display: flex;
  gap: 8px;
}

.sop-summary-note {
  margin: 0 0 14px;
  color: #64748b;
  font-size: 12px;
}

.sop-stage {
  padding: 14px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 10px;
  background: #fafcfd;
}

.stage-onsite-wrap {
  margin-top: 12px;
}

.stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.stage-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stage-title h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--heading, #0f172a);
}

.stage-pill {
  padding: 1px 7px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
}

.stage-customer {
  background: #e8f6f4;
  color: #0f766e;
}

.stage-onsite {
  background: #fff5dd;
  color: #a86c08;
}

.stage-limit {
  font-size: 12px;
  color: var(--muted, #8a96a5);
}

.step-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.step-item {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
}

.step-index {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #e8f6f4;
  border: 1px solid #0d9488;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.step-index.onsite {
  background: #fff5dd;
  border-color: #eab65f;
  color: #a86c08;
}

.step-body {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.step-desc {
  margin: 2px 0 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
}

.evidence-tag {
  flex: none;
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.evidence-tag.teal {
  background: #e8f6f4;
  color: #0f766e;
}

.evidence-tag.teal-deep {
  background: #d7eeeb;
  color: #0c5f59;
}

.evidence-tag.warn {
  background: #fff5dd;
  color: #a86c08;
}

.evidence-tag.neutral {
  background: #f1f5f9;
  color: #475569;
}

/* SOP 编辑态 */
.step-editor {
  display: grid;
  gap: 8px;
}

.step-editor-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.sop-input,
.sop-select {
  width: 100%;
  min-height: 34px;
  padding: 5px 10px;
  border: 1px solid #dce3ec;
  border-radius: 7px;
  background: #fff;
  color: #334155;
  font-size: 12px;
}

.sop-input:focus,
.sop-select:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px #0d94881f;
  outline: none;
}

.sop-select {
  max-width: 160px;
}

.stage-edit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #dce3ec;
}

.limit-hint {
  font-size: 12px;
  color: #64748b;
}

/* 右栏：上下两张卡 */
.sop-right-col {
  display: grid;
  gap: 14px;
}

.grade-list {
  display: grid;
  gap: 8px;
}

.grade-item {
  display: grid;
  gap: 4px;
  padding: 9px 10px;
  border-radius: 8px;
  border: 1px solid var(--line, #eaeef2);
}

.grade-item small {
  font-size: 11px;
  color: #64748b;
}

.grade-chip {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 700;
}

.grade-item.grade-a {
  background: #f5fbfa;
  border-color: #d7eeeb;
}

.grade-item.grade-a .grade-chip {
  background: #e8f6f4;
  color: #0f766e;
}

.grade-item.grade-b {
  background: #fffcf3;
  border-color: #f3e2b8;
}

.grade-item.grade-b .grade-chip {
  background: #fff5dd;
  color: #a86c08;
}

.grade-item.grade-c {
  background: #fef7f7;
  border-color: #f6cecb;
}

.grade-item.grade-c .grade-chip {
  background: #fdecec;
  color: #c0362c;
}

.auto-rules {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line, #eaeef2);
}

.sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.sub-head h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--heading, #0f172a);
}

.rule-summary {
  font-size: 11px;
  color: #0f766e;
  background: #e8f6f4;
  padding: 2px 7px;
  border-radius: 999px;
}

.rule-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.rule-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 10px;
  background: #f8fafc;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
}

.rule-info {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.rule-info strong {
  font-size: 12px;
  color: #1e293b;
}

.rule-info small {
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}

/* 开关控件 */
.rule-switch {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: 1px solid #f3e2b8;
  border-radius: 999px;
  background: #fff5dd;
  color: #a86c08;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.rule-switch.on {
  border-color: #c5e4e1;
  background: #e8f6f4;
  color: #0f766e;
}

.switch-track {
  position: relative;
  width: 28px;
  height: 16px;
  border-radius: 999px;
  background: #d9a441;
  transition: background-color 0.15s ease;
}

.rule-switch.on .switch-track {
  background: #0d9488;
}

.switch-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.15s ease;
}

.rule-switch.on .switch-thumb {
  transform: translateX(12px);
}

.switch-label {
  white-space: nowrap;
}

/* 版本记录 */
.version-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.version-item {
  padding: 10px 12px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
  background: #fafcfd;
}

.version-item.published {
  background: #f5fbfa;
  border-color: #cdebe6;
}

.version-item.reviewing {
  background: #fffcf3;
  border-color: #f3e2b8;
}

.version-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.version-label {
  font-size: 12px;
  color: var(--heading, #0f172a);
}

.version-state {
  flex: none;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.version-state.published {
  background: #e8f6f4;
  color: #0f766e;
}

.version-state.archived {
  background: #f1f5f9;
  color: #64748b;
}

.version-state.reviewing {
  background: #fff5dd;
  color: #a86c08;
}

.version-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 5px;
  font-size: 11px;
  color: var(--muted, #8a96a5);
}

/* 响应式：窄屏自动堆叠 */
@media (max-width: 1120px) {
  .sop-columns {
    grid-template-columns: minmax(210px, 240px) minmax(0, 1fr);
  }

  .sop-right-col {
    grid-column: 1 / -1;
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 780px) {
  .sop-columns {
    grid-template-columns: 1fr;
  }

  .sop-right-col {
    grid-column: auto;
    grid-template-columns: 1fr;
  }

  .step-body {
    flex-direction: column;
    gap: 6px;
  }

  .step-editor-row {
    flex-wrap: wrap;
  }

  .sop-select {
    max-width: none;
    flex: 1 1 140px;
  }
}
</style>
