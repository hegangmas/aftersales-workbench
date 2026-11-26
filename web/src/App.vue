<template>
  <div v-if="!authReady" class="booting"></div>
  <LoginScreen
    v-else-if="!account" ref="loginPanel"
    v-model:username="username" v-model:password="password" v-model:remember="rememberAccount"
    :busy="busy" :error="error" @submit="signIn"
  />
  <div v-else class="v11-shell" @click="closeMenus">
    <TopNav
      ref="topNav"
      v-model:agent-status="agentStatus"
      :account="account" :view="view" :nav-group="navGroup" :due-count="dueCount"
      @open-workbench="openWorkbenchTab" @open-tickets="openTicketsRoot"
      @navigate="showNav" @open-admin="showAdmin" @open-due="openDue"
      @save-duty="saveDutyStatus" @sign-out="signOut"
    />

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
          :problem="problem" :draft="draft" :busy="busy"
          :can-process="canProcess" :can-handoff="canHandoff" :handling-denied="handlingDenied"
          :next-action="nextAction" :refresh-note="refreshNote" :assignees="assignees"
          :evidence-labels="evidenceLabels" :field-in-progress="fieldInProgress"
          @back="openTicketsRoot" @open="openProblem" @claim="claim"
          @save-note="saveNote" @save-schedule="saveSchedule" @begin-callback="beginCallback"
          @save-outcome="saveOutcome" @handoff="handoff" @withdraw="withdrawDispatch"
          @link="saveFieldLink" @field-result="saveFieldResult" @suspend="suspendTicket"
          @quick-close="quickCloseTicket" @escalate="escalateTicket" @add-evidence="saveEvidence"
          @assess="saveAssessment" @go-device="jumpToDevice" @go-knowledge="showNav('knowledge')"
          @bind-device="bindDevice" @assign-v11="assignV11" @dispatch-v11="dispatchV11"
          @lz-callback="lzCallbackV11" @accept-v11="acceptV11" @reopen-v11="reopenV11"
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
              v-for="tab in visibleTicketTabs" :key="tab.key"
              type="button" role="tab" :aria-selected="ticketTab === tab.key"
              @click="switchTicketTab(tab.key)"
            >{{ tab.label }}</button>
          </div>
          <div class="head-actions">
            <button v-if="canSee(role, 'ticket-create')" class="btn primary" type="button" @click="showNav('device')">+ 新建工单</button>
          </div>
        </div>

        <DispatchDashboard
          v-if="ticketTab === 'dispatch'"
          :items="boardItems" :summary="summary" :assignees="assignees" :busy="busy"
          @open="openProblem" @open-board="openPoolWithBucket"
          @claim="claimFromDispatch" @force-distribute="runForceDistribute"
        />
        <ProblemBoard
          v-else-if="ticketTab === 'pool'"
          :items="boardItems" :total="boardTotal" :page="boardPage" :page-size="pageSize"
          :has-next="boardHasNext" :busy="busy" :filters="boardFilters"
          :summary="summary" :categories="boardCategories" :assignees="assignees"
          @open="openProblem" @claim="claimRow" @search="searchBoard" @page="goToBoardPage"
        />
        <QueueList
          v-else
          :items="mineItems" :total="mineTotal" :busy="busy" :has-more="Boolean(mineNext)"
          empty="当前没有负责中的工单" @open="openProblem" @more="moreMine"
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
          :items="dueItems" :total="dueTotal" :busy="busy" :has-more="Boolean(dueNext)"
          empty="当前没有到期回访工单" @open="openProblem" @more="moreDue"
        />
      </template>

      <!-- 一级菜单 3：设备（单框 4 源并行 6 段档案 + 建单） -->
      <DeviceLookupView
        v-else-if="view === 'device'" :busy="busy" :initial-query="devicePrefill"
        @open="openProblem" @created="onTicketCreated"
      />

      <!-- 一级菜单 4：知识库（只读发布版，不渲染写端操作或审批阈值） -->
      <KnowledgeSopView v-else-if="view === 'knowledge'" />

      <!-- 一级菜单 5：管理 → 4 个二级页面 -->
      <AdminCustomersView
        v-else-if="view === 'admin-customers' && canSee(role, 'admin-customers')"
        :customers="customers" @refresh="loadCustomersList"
      />
      <AdminSopView v-else-if="view === 'admin-sop' && canSeeAdminMenu" />
      <AdminReportsView
        v-else-if="view === 'admin-reports' && canSee(role, 'admin-reports')"
        :monthly-report="monthlyReport" @refresh="loadMonthlyReport"
      />
      <AccountPanel
        v-else-if="view === 'admin-accounts' && canSee(role, 'admin-accounts')"
        :accounts="accounts" :busy="busy" :notice="accountNotice"
        :create-account="addAccount" :reset-password="resetAccountPassword"
        @toggle="toggleAccount"
      />

      <PasswordPanel
        v-else-if="view === 'security'" :busy="busy" :notice="passwordNotice"
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
import { computed, defineAsyncComponent, nextTick, onMounted, onUnmounted, ref } from 'vue'
import LoginScreen from './components/LoginScreen.vue'
import QueueList from './components/QueueList.vue'
import TopNav from './components/layout/TopNav.vue'
import { useAppRoute } from './composables/useAppRoute'
import { emptyFilters, useBoardQueues } from './composables/useBoardQueues'
import { useProblemActions } from './composables/useProblemActions'
import { useProblemDraft } from './composables/useProblemDraft'
import { TICKET_TABS, canSee } from './nav/menuConfig'
import {
  assignableAccounts, changePassword, createAccount, currentAccount,
  fetchMonthlyReport, listAccounts, listCustomers, loadProblem, login, logout,
  resetAccountPassword as resetAccountPasswordApi, setAccountEnabled, updateOwnDuty
} from './api/workbench'

const AccountPanel = defineAsyncComponent(() => import(/* webpackChunkName: "view-account" */ './views/AccountPanel.vue'))
const AdminCustomersView = defineAsyncComponent(() => import(/* webpackChunkName: "view-admin-customers" */ './views/AdminCustomersView.vue'))
const AdminReportsView = defineAsyncComponent(() => import(/* webpackChunkName: "view-admin-reports" */ './views/AdminReportsView.vue'))
const AdminSopView = defineAsyncComponent(() => import(/* webpackChunkName: "view-admin-sop" */ './views/admin/AdminSopView.vue'))
const DeviceLookupView = defineAsyncComponent(() => import(/* webpackChunkName: "view-device" */ './views/DeviceLookupView.vue'))
const DispatchDashboard = defineAsyncComponent(() => import(/* webpackChunkName: "view-dispatch" */ './views/DispatchDashboard.vue'))
const KnowledgeSopView = defineAsyncComponent(() => import(/* webpackChunkName: "view-knowledge-sop" */ './views/knowledge/KnowledgeSopView.vue'))
const PasswordPanel = defineAsyncComponent(() => import(/* webpackChunkName: "view-password" */ './views/PasswordPanel.vue'))
const PersonalWorkbench = defineAsyncComponent(() => import(/* webpackChunkName: "view-workbench" */ './views/PersonalWorkbench.vue'))
const ProblemBoard = defineAsyncComponent(() => import(/* webpackChunkName: "view-board" */ './views/ProblemBoard.vue'))

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
const topNav = ref(null)
const conflict = ref(false)
const accounts = ref([])
const assignees = ref([])
const customers = ref([])
const monthlyReport = ref(null)
const accountNotice = ref('')
const passwordNotice = ref('')
const evidenceLabels = { customer: '车主确认', platform: '平台充电记录', field: '现场复测' }

const {
  boardItems, boardTotal, boardPage, boardHasNext, boardCategories, boardFilters, boardCursors, summary,
  dueScope, dueItems, dueTotal, dueNext, mineItems, mineTotal, mineNext,
  loadBoard, loadDue, loadMine, refreshQueues: refreshBoardQueues, resetQueues
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

const role = computed(() => account.value?.role)
const canSeeAdminMenu = computed(() => canSee(role.value, 'admin'))
const visibleTicketTabs = computed(() => TICKET_TABS.filter(tab => canSee(role.value, tab.key)))
const dueCount = computed(() => (summary.value ? Number(summary.value.due || 0) : 0))
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

function closeMenus() { topNav.value?.closeMenus() }
async function loadAccounts() {
  if (account.value?.role !== 'admin') { accounts.value = []; return }
  accounts.value = await listAccounts()
}
async function loadCustomersList() { customers.value = await listCustomers() }
async function loadMonthlyReport() { monthlyReport.value = await fetchMonthlyReport() }
async function refreshAssignees() { assignees.value = await assignableAccounts() }

const {
  view, ticketTab, devicePrefill, navGroup,
  applyDefaultTicketTab, resetRoute, syncUrl, restoreRoute, handlePopState,
  openWorkbenchTab, openTicketsRoot, switchTicketTab, openPoolWithBucket,
  showNav, showAdmin, jumpToDevice, openDue
} = useAppRoute({
  account, role, problem, boardItems, boardFilters, boardCursors, boardPage, dueScope,
  run, closeMenus, loadBoard, loadMine, loadDue,
  loadCustomersList, loadMonthlyReport, loadAccounts, refreshAssignees,
  showProblem: (id, options) => showProblem(id, options),
  openProblem: id => openProblem(id)
})

function refreshQueues() { return refreshBoardQueues(ticketTab.value === 'mine' ? 'mine' : 'board') }

async function showProblem(id, options) {
  const preserve = Boolean(options && options.preserveDraft)
  const fromPopState = Boolean(options && options.fromPopState)
  const saved = preserve ? snapshotDraft() : null
  const [loaded, assignable] = await Promise.all([loadProblem(id), assignableAccounts()])
  problem.value = loaded
  assignees.value = assignable
  view.value = 'detail'
  if (preserve) restoreDraft(saved, problem.value)
  else fillFromServer(problem.value)
  syncUrl(!fromPopState)
}

function openProblem(id) {
  closeMenus()
  return run(() => showProblem(id))
}

const {
  claim, bindDevice, assignV11, dispatchV11, lzCallbackV11, acceptV11, reopenV11,
  saveNote, handoff, saveSchedule, saveOutcome, withdrawDispatch, saveFieldLink,
  saveFieldResult, suspendTicket, quickCloseTicket, escalateTicket, saveEvidence,
  saveAssessment, claimRow, claimFromDispatch, runForceDistribute
} = useProblemActions({ problem, draft, boardItems, run, showProblem, refreshQueues })

function clearAccount() {
  account.value = null
  problem.value = null
  resetQueues()
  resetDraft()
  passwordNotice.value = ''
  accountNotice.value = ''
  conflict.value = false
  resetRoute()
  closeMenus()
}

async function prepareWorkspace() {
  agentStatus.value = account.value?.dutyStatus || 'ready'
  applyDefaultTicketTab()
  await Promise.all([loadAccounts(), refreshAssignees()])
  boardFilters.value = emptyFilters()
  boardCursors.value = [null]
  boardPage.value = 0
  await loadBoard()
  await restoreRoute()
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
    await refreshAssignees()
  })
}

function onTicketCreated(id) {
  openProblem(id)
  refreshQueues()
}

function refreshAfterConflict() {
  return run(async () => {
    conflict.value = false
    await showProblem(problem.value.id, { preserveDraft: true })
  })
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

function moreDue() { return run(() => loadDue(true)) }
function moreMine() { return run(() => loadMine(true)) }

function addAccount(form) {
  accountNotice.value = ''
  return run(async () => {
    await createAccount(form)
    await loadAccounts()
    await refreshAssignees()
    accountNotice.value = '账号已创建，列表已刷新'
  })
}
function toggleAccount(item) {
  return run(async () => {
    await setAccountEnabled(item.username, !item.enabled)
    await loadAccounts()
    await refreshAssignees()
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
  window.addEventListener('popstate', handlePopState)
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
  window.removeEventListener('popstate', handlePopState)
})
</script>
