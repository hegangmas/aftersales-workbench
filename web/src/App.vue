<template>
  <div v-if="!authReady" class="booting"></div>
  <LoginScreen
    v-else-if="!account"
    ref="loginPanel"
    v-model:username="username"
    v-model:password="password"
    v-model:remember="rememberAccount"
    :busy="busy"
    :error="error"
    @submit="signIn"
  />
  <div v-else class="v11-shell" @click="closeMenus">
    <header class="topbar">
      <div class="topbar-inner">
        <div class="topbar-brand">
          <BrandLogo variant="teal" alt="松果" width="90px" />
          <span class="topbar-brand-product">售后工单中台</span>
        </div>

        <nav class="topbar-nav" aria-label="一级导航">
          <button
            v-if="canSeeInternal"
            type="button"
            class="top-link"
            :aria-current="navGroup === 'workbench' ? 'page' : undefined"
            @click="openWorkbenchTab"
          >工作台</button>

          <button
            type="button"
            class="top-link"
            :aria-current="navGroup === 'tickets' ? 'page' : undefined"
            @click="openTicketsRoot"
          >工单</button>

          <button
            v-if="canSeeInternal"
            type="button"
            class="top-link"
            :aria-current="navGroup === 'device' ? 'page' : undefined"
            @click="showNav('device')"
          >设备</button>

          <button
            type="button"
            class="top-link"
            :aria-current="navGroup === 'knowledge' ? 'page' : undefined"
            @click="showNav('knowledge')"
          >知识库</button>

          <div v-if="canSeeAdminMenu" class="nav-dropdown" @click.stop>
            <button
              type="button"
              class="top-link"
              :class="{ active: navGroup === 'admin' }"
              :aria-expanded="adminMenuOpen"
              @click="adminMenuOpen = !adminMenuOpen; userMenuOpen = false"
            >管理 ▾</button>
            <div v-if="adminMenuOpen" class="dropdown-panel" role="menu">
              <button type="button" class="dropdown-item" :aria-current="view === 'admin-customers' ? 'page' : undefined" @click="showAdmin('admin-customers')">客户与项目</button>
              <button type="button" class="dropdown-item" :aria-current="view === 'admin-sop' ? 'page' : undefined" @click="showAdmin('admin-sop')">SOP 与规则</button>
              <button type="button" class="dropdown-item" :aria-current="view === 'admin-reports' ? 'page' : undefined" @click="showAdmin('admin-reports')">指标与报表</button>
              <button type="button" class="dropdown-item" :aria-current="view === 'admin-accounts' ? 'page' : undefined" @click="showAdmin('admin-accounts')">权限与配置</button>
            </div>
          </div>
        </nav>

        <div class="topbar-right">
          <button v-if="canSeeInternal" type="button" class="bell-btn icon-btn" title="到期回访提醒" aria-label="到期回访提醒" @click="openDue('team')">
            <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M15 7a5 5 0 0 0-10 0c0 4.5-2 6-2 6h14s-2-1.5-2-6" />
              <path d="M8.5 16a1.7 1.7 0 0 0 3 0" />
            </svg>
            <span v-if="dueCount" class="icon-btn__dot" :title="`有 ${dueCount} 条到期回访`"></span>
          </button>

          <select
            v-if="canSeeInternal"
            v-model="agentStatus"
            class="agent-status-select"
            :class="agentStatus"
            aria-label="在岗状态"
            @change="saveDutyStatus"
          >
            <option value="ready">● 就绪</option>
            <option value="break">● 休息</option>
            <option value="away">● 离岗</option>
          </select>

          <div class="nav-dropdown" @click.stop>
            <button type="button" class="top-link" @click="userMenuOpen = !userMenuOpen; adminMenuOpen = false">
              <span class="avatar mini teal">{{ initial(account.username) }}</span>
              <span>{{ account.username }}</span>
              <span class="role-pill">{{ roleLabel(account.role) }}</span>
              <span>▾</span>
            </button>
            <div v-if="userMenuOpen" class="dropdown-panel" style="right:0;left:auto;">
              <button type="button" class="dropdown-item" @click="showNav('security'); userMenuOpen = false">安全与改密</button>
              <button type="button" class="dropdown-item" @click="signOut">退出登录</button>
            </div>
          </div>
        </div>
      </div>
    </header>

    <main class="v11-main">
      <div v-if="error" ref="errorSummary" tabindex="-1" class="error-banner">
        <el-alert :title="error" type="error" :closable="false" show-icon />
        <button v-if="conflict" class="btn secondary" type="button" @click="refreshAfterConflict">一键刷新工单</button>
        <button v-else-if="problem && view === 'detail'" class="btn secondary" type="button" @click="openProblem(problem.id)">刷新问题单</button>
      </div>

      <!-- 一级菜单 1：工作台（图 2 受理弹屏） -->
      <template v-if="view === 'detail'">
        <PersonalWorkbench
          v-if="problem"
          :problem="problem"
          :draft="draft"
          :busy="busy"
          :can-process="canProcess"
          :can-handoff="canHandoff"
          :handling-denied="handlingDenied"
          :next-action="nextAction"
          :refresh-note="refreshNote"
          :assignees="assignees"
          :evidence-labels="evidenceLabels"
          :field-in-progress="fieldInProgress"
          @back="openTicketsRoot"
          @open="openProblem"
          @claim="claim"
          @save-note="saveNote"
          @save-schedule="saveSchedule"
          @begin-callback="beginCallback"
          @save-outcome="saveOutcome"
          @handoff="handoff"
          @withdraw="withdrawDispatch"
          @link="saveFieldLink"
          @field-result="saveFieldResult"
          @suspend="suspendTicket"
          @quick-close="quickCloseTicket"
          @escalate="escalateTicket"
          @add-evidence="saveEvidence"
          @assess="saveAssessment"
          @go-device="jumpToDevice"
          @go-knowledge="showNav('knowledge')"
          @bind-device="bindDevice"
          @assign-v11="assignV11"
          @dispatch-v11="dispatchV11"
          @lz-callback="lzCallbackV11"
          @accept-v11="acceptV11"
          @reopen-v11="reopenV11"
        />
        <section v-else class="panel">
          <h2>售后受理工作台</h2>
          <p class="muted">当前尚未打开工单。请从「工单」选择工单进入处理，或前往「设备」查桩建单。</p>
          <div class="quick-bar" style="border:0;padding-top:0;">
            <button class="btn primary" type="button" @click="openTicketsRoot">进入工单池</button>
            <button class="btn secondary" type="button" @click="showNav('device')">查设备建单</button>
          </div>
        </section>
      </template>

      <!-- 一级菜单 2：工单（3 页签：调度大屏 | 统一工单池 | 我的待办） -->
      <template v-else-if="view === 'tickets'">
        <div class="ticket-tabs-bar">
          <div class="ticket-tabs" role="tablist" aria-label="工单视图">
            <button
              v-if="canSeeInternal"
              type="button"
              role="tab"
              :aria-selected="ticketTab === 'dispatch'"
              @click="switchTicketTab('dispatch')"
            >调度大屏</button>
            <button
              type="button"
              role="tab"
              :aria-selected="ticketTab === 'pool'"
              @click="switchTicketTab('pool')"
            >统一工单池</button>
            <button
              type="button"
              role="tab"
              :aria-selected="ticketTab === 'mine'"
              @click="switchTicketTab('mine')"
            >我的待办</button>
          </div>
          <div class="head-actions">
            <button v-if="canSeeInternal" class="btn primary" type="button" @click="showNav('device')">+ 新建工单</button>
          </div>
        </div>

        <DispatchDashboard
          v-if="ticketTab === 'dispatch'"
          :items="boardItems"
          :summary="summary"
          :assignees="assignees"
          :busy="busy"
          @open="openProblem"
          @open-board="openPoolWithBucket"
          @claim="claimFromDispatch"
          @force-distribute="runForceDistribute"
        />
        <ProblemBoard
          v-else-if="ticketTab === 'pool'"
          :items="boardItems"
          :total="boardTotal"
          :page="boardPage"
          :page-size="pageSize"
          :has-next="boardHasNext"
          :busy="busy"
          :filters="boardFilters"
          :summary="summary"
          :categories="boardCategories"
          :assignees="assignees"
          @open="openProblem"
          @claim="claimRow"
          @search="searchBoard"
          @page="goToBoardPage"
        />
        <QueueList
          v-else
          :items="mineItems"
          :total="mineTotal"
          :busy="busy"
          :has-more="Boolean(mineNext)"
          empty="当前没有负责中的工单"
          @open="openProblem"
          @more="moreMine"
        />
      </template>

      <!-- 到期回访提醒列表（铃铛入口） -->
      <template v-else-if="view === 'due'">
        <header class="page-head">
          <div>
            <h1>到期回访提醒</h1>
            <p>{{ dueScope === 'mine' ? '我负责且已到回访时间的工单' : '团队已到回访时间的工单' }}</p>
          </div>
          <div class="scope-switch" role="group">
            <button type="button" :aria-pressed="dueScope === 'mine'" @click="openDue('mine')">我的</button>
            <button type="button" :aria-pressed="dueScope === 'team'" @click="openDue('team')">团队</button>
          </div>
        </header>
        <QueueList
          :items="dueItems"
          :total="dueTotal"
          :busy="busy"
          :has-more="Boolean(dueNext)"
          empty="当前没有到期回访工单"
          @open="openProblem"
          @more="moreDue"
        />
      </template>

      <!-- 一级菜单 3：设备（单框 4 源并行 6 段档案 + 建单） -->
      <DeviceLookupView
        v-else-if="view === 'device'"
        :busy="busy"
        :initial-query="devicePrefill"
        @open="openProblem"
        @created="onTicketCreated"
      />

      <!-- 一级菜单 4：知识库（一线/二线/服务商只读，隐藏审批阈值） -->
      <KnowledgeSopView v-else-if="view === 'knowledge'" read-only />

      <!-- 一级菜单 5：管理 → 4 个二级页面 -->
      <section v-else-if="view === 'admin-customers'" class="panel">
        <div class="fig2-card-head">
          <h2>客户与项目档案</h2>
          <button class="btn secondary" type="button" @click="loadCustomersList">刷新</button>
        </div>
        <p class="muted">本地模拟客户与项目主档（外部安装系统接入后自动同步）。</p>
        <table v-if="customers.length" class="tickets" style="margin-top:12px;">
          <thead>
            <tr><th>客户ID</th><th>客户名称</th><th>联系人</th><th>电话</th><th>项目编号</th><th>项目名称</th><th>类型/SLA</th></tr>
          </thead>
          <tbody>
            <tr v-for="c in customers" :key="c.id">
              <td>{{ c.id }}</td><td>{{ c.name }}</td><td>{{ c.contactName }}</td><td>{{ c.phone }}</td>
              <td>{{ c.projectCode }}</td><td>{{ c.projectName }}</td><td>{{ c.customerType }} · {{ c.slaMinutes }}分钟</td>
            </tr>
          </tbody>
        </table>
      </section>

      <KnowledgeSopView v-else-if="view === 'admin-sop'" :read-only="false" />

      <section v-else-if="view === 'admin-reports'" class="panel">
        <div class="fig2-card-head">
          <h2>指标与报表（V1.1 4.11 六项月度指标）</h2>
          <button class="btn secondary" type="button" @click="loadMonthlyReport">刷新指标</button>
        </div>
        <div v-if="monthlyReport" class="evidence-thumbs" style="margin-top:12px;">
          <div class="evidence-thumb"><span class="thumb-badge">1. 工单总量 / 漏单率</span><strong>{{ monthlyReport.totalTickets }} 单</strong><small>漏单率：{{ monthlyReport.leakRateSimulated }}</small></div>
          <div class="evidence-thumb"><span class="thumb-badge">2. 平均受理时长</span><strong>{{ monthlyReport.avgAcceptMinutesSimulated }} 分钟</strong><small>模拟节点均值</small></div>
          <div class="evidence-thumb"><span class="thumb-badge">3. SLA 达成率</span><strong>{{ monthlyReport.slaPassRate }}</strong><small>当前超时：{{ monthlyReport.slaOverdueCount }} 单</small></div>
          <div class="evidence-thumb"><span class="thumb-badge">4. 换装率 / 误判率</span><strong>{{ monthlyReport.replaceRate }}</strong><small>误判率：{{ monthlyReport.misjudgeRateSimulated }}</small></div>
          <div class="evidence-thumb"><span class="thumb-badge">5. 妥协工单占比</span><strong>{{ monthlyReport.compromiseRate }}</strong><small>设备待补：{{ monthlyReport.devicePendingCount }} 单</small></div>
          <div class="evidence-thumb"><span class="thumb-badge">6. 催单与升级投诉</span><strong>{{ monthlyReport.urgeAndComplaintSimulated }} 次</strong><small>含旁路升级记录</small></div>
        </div>
      </section>

      <AccountPanel
        v-else-if="view === 'admin-accounts' && account.role === 'admin'"
        :accounts="accounts"
        :busy="busy"
        :notice="accountNotice"
        :create-account="addAccount"
        :reset-password="resetAccountPassword"
        @toggle="toggleAccount"
      />

      <PasswordPanel
        v-else-if="view === 'security'"
        :busy="busy"
        :notice="passwordNotice"
        :change-password="changeOwnPassword"
      />
    </main>

    <footer class="v11-footer">
      <span>售后中台 · 让充电服务更简单</span>
      <span>v1.1.0 · 本地模拟主档模式</span>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import BrandLogo from './components/BrandLogo.vue'
import LoginScreen from './components/LoginScreen.vue'
import QueueList from './components/QueueList.vue'
import AccountPanel from './views/AccountPanel.vue'
import DeviceLookupView from './views/DeviceLookupView.vue'
import DispatchDashboard from './views/DispatchDashboard.vue'
import KnowledgeSopView from './views/KnowledgeSopView.vue'
import PasswordPanel from './views/PasswordPanel.vue'
import PersonalWorkbench from './views/PersonalWorkbench.vue'
import ProblemBoard from './views/ProblemBoard.vue'
import { emptyFilters, useBoardQueues } from './composables/useBoardQueues'
import { useProblemDraft } from './composables/useProblemDraft'
import { initial, orderNumber, roleLabel } from './status'
import {
  acceptProblemV11, addEvidence, addFollowup, assessProblem, assignProblemV11, assignableAccounts,
  bindProblemDevice, changePassword, claimProblem, createAccount, currentAccount, dispatchProblemV11,
  escalateProblem, fetchMonthlyReport, finishCallback, forceDistributeV11, handoffProblem,
  linkFieldOrder, listAccounts, listCustomers, loadProblem, login, logout, quickCloseProblem,
  recordFieldResult, reopenProblemV11, resetAccountPassword as resetAccountPasswordApi,
  scheduleCallback, setAccountEnabled, suspendProblem, triggerLezhuangCallback, updateOwnDuty, withdrawField
} from './api/workbench'

const REMEMBER_KEY = 'songguo-workbench-username'
const pageSize = 10

const account = ref(null)
const authReady = ref(false)
const username = ref('')
const password = ref('')
const rememberAccount = ref(false)
const agentStatus = ref('ready')

const problem = ref(null)
const busy = ref(false)
const error = ref('')
const errorSummary = ref(null)
const loginPanel = ref(null)
const conflict = ref(false)
const accounts = ref([])
const assignees = ref([])
const customers = ref([])
const monthlyReport = ref(null)
const accountNotice = ref('')
const passwordNotice = ref('')
const evidenceLabels = { customer: '车主确认', platform: '平台充电记录', field: '现场复测' }

const view = ref('tickets')
const ticketTab = ref('pool')
const devicePrefill = ref('')
const adminMenuOpen = ref(false)
const userMenuOpen = ref(false)

const {
  boardItems, boardTotal, boardPage, boardHasNext, boardCategories, boardFilters, boardCursors, summary,
  dueScope, dueItems, dueTotal, dueNext,
  mineItems, mineTotal, mineNext,
  loadBoard, loadDue, loadMine, refreshQueues: refreshBoardQueues, ensureFreshQueue, resetQueues
} = useBoardQueues(pageSize)

function ownerLabel(value) { return !value || value === 'early' ? '无主' : value }
function unowned(item) { return !item?.owner || item.owner === 'early' }
function canHandle(current) {
  if (!current || ['resolved', 'closed', 'cancelled'].includes(current.state)) return false
  if (['admin', 'dispatcher', 'quality'].includes(account.value?.role)) return true
  if (unowned(current)) return false
  return account.value?.username === current.owner
}

const {
  draft, refreshNote, beginCallback, snapshotDraft, fillFromServer, restoreDraft, resetDraft
} = useProblemDraft({ canHandle, unowned, account, assignees })

const canSeeInternal = computed(() => !['provider', 'oem'].includes(account.value?.role))
const canSeeAdminMenu = computed(() => ['admin', 'dispatcher', 'quality'].includes(account.value?.role))
const navGroup = computed(() => {
  if (view.value === 'detail') return 'workbench'
  if (view.value === 'tickets' || view.value === 'due') return 'tickets'
  if (view.value === 'device') return 'device'
  if (view.value === 'knowledge') return 'knowledge'
  if (view.value.startsWith('admin-')) return 'admin'
  return ''
})
const dueCount = computed(() => summary.value ? Number(summary.value.due || 0) : 0)
const fieldInProgress = computed(() => problem.value?.fieldOrders?.some(item => ['linked', 'in_progress', 'dispatched', 'accepted', 'arrived'].includes(item.state)))
const nextAction = computed(() => {
  const cur = problem.value
  if (!cur) return ''
  if (['closed', 'resolved', 'cancelled'].includes(cur.state)) return '终态，不再写入'
  if (cur.devicePending) return '请先补齐设备 SN'
  if (cur.state === 'pending_accept') return '请核对完工回传与旧件并验收闭环'
  if (cur.state === 'pending_callback') return '等待智享乐装回传'
  return '处理排查、记录证据或派单'
})
const canProcess = computed(() => canHandle(problem.value))
const handlingDenied = computed(() => {
  const cur = problem.value
  if (!cur || ['resolved', 'closed', 'cancelled'].includes(cur.state) || canHandle(cur)) return ''
  if (unowned(cur)) return '该工单处于待分配状态，请先点击「承接处理」后再操作。'
  return '当前工单由 ' + ownerLabel(cur.owner) + ' 负责，当前账号无权直接写入。'
})
const canHandoff = computed(() => Boolean(problem.value) && !['resolved', 'closed', 'cancelled'].includes(problem.value.state))

function closeMenus() {
  adminMenuOpen.value = false
  userMenuOpen.value = false
}

function defaultTicketTabForRole(role) {
  if (role === 'dispatcher') return 'dispatch'
  if (role === 'engineer' || role === 'provider') return 'mine'
  return 'pool'
}

function clearAccount() {
  account.value = null
  problem.value = null
  resetQueues()
  resetDraft()
  passwordNotice.value = ''
  accountNotice.value = ''
  conflict.value = false
  view.value = 'tickets'
  closeMenus()
}

function syncUrl() {
  if (!account.value) {
    history.replaceState(null, '', location.pathname)
    return
  }
  const params = new URLSearchParams()
  if (view.value === 'detail' && problem.value) {
    params.set('problem', orderNumber(problem.value))
  } else {
    params.set('view', view.value)
    if (view.value === 'tickets') params.set('tab', ticketTab.value)
  }
  history.replaceState(null, '', '?' + params.toString())
}

async function prepareWorkspace() {
  agentStatus.value = account.value?.dutyStatus || 'ready'
  ticketTab.value = defaultTicketTabForRole(account.value?.role)
  await Promise.all([loadAccounts(), assignableAccounts().then(res => { assignees.value = res })])
  boardFilters.value = emptyFilters()
  boardCursors.value = [null]
  boardPage.value = 0
  await loadBoard()
  await restoreRoute()
}

async function restoreRoute() {
  const params = new URLSearchParams(location.search)
  const problemId = params.get('problem')
  if (problemId) {
    await showProblem(problemId)
    return
  }
  const reqView = params.get('view')
  const validViews = ['tickets', 'device', 'knowledge', 'due', 'security', 'admin-customers', 'admin-sop', 'admin-reports', 'admin-accounts']
  if (reqView && validViews.includes(reqView)) {
    view.value = reqView
  } else {
    view.value = 'tickets'
  }
  if (view.value === 'tickets') {
    const t = params.get('tab')
    if (['dispatch', 'pool', 'mine'].includes(t)) ticketTab.value = t
    if (ticketTab.value === 'mine') await loadMine(false)
  } else if (view.value === 'admin-customers') {
    await loadCustomersList()
  } else if (view.value === 'admin-reports') {
    await loadMonthlyReport()
  }
  syncUrl()
}

function signIn() {
  return run(async () => {
    try {
      const name = username.value.trim()
      username.value = name
      account.value = await login(name, password.value)
      if (rememberAccount.value) localStorage.setItem(REMEMBER_KEY, name)
      else localStorage.removeItem(REMEMBER_KEY)
      await prepareWorkspace()
    } finally {
      password.value = ''
    }
  })
}

function signOut() {
  return run(async () => {
    await logout()
    clearAccount()
    syncUrl()
  })
}

function saveDutyStatus() {
  return run(async () => {
    await updateOwnDuty(agentStatus.value)
    assignees.value = await assignableAccounts()
  })
}

function openWorkbenchTab() {
  closeMenus()
  if (problem.value) {
    view.value = 'detail'
    syncUrl()
    return
  }
  if (boardItems.value.length) {
    openProblem(boardItems.value[0].id)
    return
  }
  view.value = 'detail'
  syncUrl()
}

function openTicketsRoot() {
  closeMenus()
  view.value = 'tickets'
  syncUrl()
  run(async () => {
    await loadBoard()
    if (ticketTab.value === 'mine') await loadMine(false)
  })
}

function switchTicketTab(tab) {
  ticketTab.value = tab
  syncUrl()
  if (tab === 'mine') run(() => loadMine(false))
  else run(() => loadBoard())
}

function openPoolWithBucket(bucket) {
  view.value = 'tickets'
  ticketTab.value = 'pool'
  boardFilters.value = { ...emptyFilters(), bucket: bucket || '' }
  boardCursors.value = [null]
  boardPage.value = 0
  syncUrl()
  return run(loadBoard)
}

function showNav(next) {
  closeMenus()
  view.value = next
  syncUrl()
}

function showAdmin(next) {
  closeMenus()
  view.value = next
  syncUrl()
  if (next === 'admin-customers') run(loadCustomersList)
  else if (next === 'admin-reports') run(loadMonthlyReport)
  else if (next === 'admin-accounts') run(async () => { await loadAccounts(); assignees.value = await assignableAccounts() })
}

function jumpToDevice(sn) {
  devicePrefill.value = sn || ''
  showNav('device')
}

function onTicketCreated(id) {
  openProblem(id)
  refreshQueues()
}

async function showProblem(id, options) {
  const preserve = Boolean(options && options.preserveDraft)
  const saved = preserve ? snapshotDraft() : null
  const [loaded, assignable] = await Promise.all([loadProblem(id), assignableAccounts()])
  problem.value = loaded
  assignees.value = assignable
  view.value = 'detail'
  if (preserve) restoreDraft(saved, problem.value)
  else fillFromServer(problem.value)
  syncUrl()
}

function openProblem(id) {
  closeMenus()
  return run(() => showProblem(id))
}

function refreshAfterConflict() {
  return run(async () => {
    conflict.value = false
    await showProblem(problem.value.id, { preserveDraft: true })
  })
}

function refreshQueues() {
  return refreshBoardQueues(ticketTab.value === 'mine' ? 'mine' : 'board')
}

function searchBoard(filters) {
  boardFilters.value = filters
  boardCursors.value = [null]
  boardPage.value = 0
  return run(loadBoard)
}

function goToBoardPage(target) {
  return run(async () => {
    const last = Math.max(0, Math.ceil(boardTotal.value / pageSize) - 1)
    const page = Math.max(0, Math.min(target, last))
    if (page === boardPage.value) return
    if (page < boardPage.value) {
      boardPage.value = page
      await loadBoard()
      return
    }
    while (boardPage.value < page && boardHasNext.value) {
      boardPage.value += 1
      await loadBoard()
    }
  })
}

function openDue(scope) {
  closeMenus()
  dueScope.value = scope === 'mine' ? 'mine' : 'team'
  view.value = 'due'
  syncUrl()
  return run(() => loadDue(false))
}

function moreDue() { return run(() => loadDue(true)) }
function moreMine() { return run(() => loadMine(true)) }

async function loadAccounts() {
  if (account.value?.role !== 'admin') { accounts.value = []; return }
  accounts.value = await listAccounts()
}
async function loadCustomersList() { customers.value = await listCustomers() }
async function loadMonthlyReport() { monthlyReport.value = await fetchMonthlyReport() }

function addAccount(form) {
  accountNotice.value = ''
  return run(async () => {
    await createAccount(form)
    await loadAccounts()
    assignees.value = await assignableAccounts()
    accountNotice.value = '账号已创建，列表已刷新'
  })
}
function toggleAccount(item) {
  return run(async () => {
    await setAccountEnabled(item.username, !item.enabled)
    await loadAccounts()
    assignees.value = await assignableAccounts()
  })
}
function changeOwnPassword(form) {
  return run(async () => {
    await changePassword(form)
    passwordNotice.value = '密码已更新，当前会话保持登录。'
  })
}
function resetAccountPassword(form) {
  return run(async () => {
    await resetAccountPasswordApi(form.username, form.password)
    accountNotice.value = '已重置该账号密码。'
    await loadAccounts()
  })
}

function claim() { return assignV11('') }
function claimRow(item) {
  return run(async () => {
    await assignProblemV11(item.id, '', item.version)
    await refreshQueues()
  }, { conflict: true })
}
function claimFromDispatch(target) {
  const item = typeof target === 'string' ? boardItems.value.find(row => row.id === target) : target
  if (item) return claimRow(item)
  return undefined
}
function runForceDistribute() {
  return run(async () => {
    await forceDistributeV11()
    await refreshQueues()
  })
}

function bindDevice(sn) {
  return run(async () => {
    const id = problem.value.id
    await bindProblemDevice(id, sn, problem.value.version)
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function assignV11(target) {
  return run(async () => {
    const id = problem.value.id
    await assignProblemV11(id, target, problem.value.version)
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function dispatchV11(form) {
  return run(async () => {
    const id = problem.value.id
    await dispatchProblemV11(id, { ...form, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function lzCallbackV11(form) {
  return run(async () => {
    const id = problem.value.id
    await triggerLezhuangCallback(id, { ...form, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function acceptV11(form) {
  return run(async () => {
    const id = problem.value.id
    await acceptProblemV11(id, { ...form, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function reopenV11(reason) {
  return run(async () => {
    const created = await reopenProblemV11(problem.value.id, reason)
    await showProblem(created.id)
    await refreshQueues()
  })
}
function saveNote() {
  return run(async () => {
    const id = problem.value.id
    await addFollowup(id, draft.note, problem.value.version)
    draft.note = ''
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function handoff() {
  return run(async () => {
    const id = problem.value.id
    await handoffProblem(id, { target: draft.handoffTarget, reason: draft.handoffReason, expectedVersion: problem.value.version })
    draft.handoffReason = ''
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function saveSchedule() {
  return run(async () => {
    const id = problem.value.id
    await scheduleCallback(id, { owner: draft.owner, nextStep: draft.nextStep, dueAt: draft.dueAt, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function saveOutcome() {
  return run(async () => {
    const id = problem.value.id
    await finishCallback(id, { outcome: draft.outcome, note: draft.result, contactedAt: draft.contactedAt, evidenceSource: draft.evidenceSource || null, evidence: draft.evidence || null, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function withdrawDispatch() {
  return run(async () => {
    const id = problem.value.id
    await withdrawField(id, draft.withdrawReason, problem.value.version)
    draft.withdrawReason = ''
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function saveFieldLink(number) {
  return run(async () => {
    const id = problem.value.id
    await linkFieldOrder(id, number, problem.value.version)
    draft.fieldNumber = ''
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function saveFieldResult(form) {
  return run(async () => {
    const id = problem.value.id
    await recordFieldResult(id, { ...form, expectedVersion: problem.value.version })
    draft.fieldReceipt = ''
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function suspendTicket(form) {
  return run(async () => {
    const id = problem.value.id
    await suspendProblem(id, { ...form, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function quickCloseTicket(form) {
  return run(async () => {
    const id = problem.value.id
    await quickCloseProblem(id, { ...form, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function escalateTicket(form) {
  return run(async () => {
    const id = problem.value.id
    await escalateProblem(id, { ...form, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function saveEvidence(form) {
  return run(async () => {
    const id = problem.value.id
    await addEvidence(id, { ...form, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function saveAssessment(form) {
  return run(async () => {
    const id = problem.value.id
    await assessProblem(id, { ...form, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}

async function run(action, options) {
  if (busy.value) return false
  busy.value = true
  error.value = ''
  conflict.value = false
  try {
    await action()
    return true
  } catch (failure) {
    error.value = failure.message
    conflict.value = Boolean(options && options.conflict && failure.status === 409 && problem.value)
    await nextTick()
    if (loginPanel.value && loginPanel.value.focusError) loginPanel.value.focusError()
    else if (errorSummary.value) errorSummary.value.focus()
    return false
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    username.value = saved
    rememberAccount.value = true
  }
  window.addEventListener('workbench-session-expired', clearAccount)
  try {
    account.value = await currentAccount()
  } catch (ignore) {
    return
  } finally {
    authReady.value = true
  }
  if (account.value) await run(prepareWorkspace)
})

onUnmounted(() => {
  window.removeEventListener('workbench-session-expired', clearAccount)
})
</script>
