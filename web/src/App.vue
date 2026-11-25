<template>
  <div v-if="!authReady" class="booting"></div>
  <LoginScreen v-else-if="!account" ref="loginPanel" v-model:username="username" v-model:password="password" v-model:remember="rememberAccount" :busy="busy" :error="error" @submit="signIn" />
  <div v-else class="shell" :class="{ 'personal-mode': view === 'detail' && !sidebarExpanded }">
    <header class="mobile-bar">
      <button class="menu-button" type="button" aria-label="打开菜单" :aria-expanded="navOpen" aria-controls="workbench-nav" @click="navOpen = true">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
      </button>
      <BrandLogo class="bar-logo" variant="teal" alt="松果" width="108px" />
      <strong>售后工作台</strong>
      <span class="test-tag">模拟数据</span>
    </header>
    <button v-if="!wide && navOpen" class="backdrop" type="button" aria-label="关闭菜单" @click="navOpen = false"></button>
    <aside id="workbench-nav" class="sidebar" :class="{ open: navOpen }" :inert="!wide && !navOpen" :role="!wide && navOpen ? 'dialog' : undefined" :aria-modal="!wide && navOpen ? 'true' : undefined" aria-label="工作台导航">
      <div class="brand">
        <BrandLogo class="sidebar-logo" variant="teal" alt="松果" width="156px" />
        <div class="brand-copy">
          <h1 class="brand-title">售后工作台</h1>
          <div class="brand-sub"><span class="test-tag">模拟数据</span></div>
        </div>
      </div>
      <button v-if="wide && view === 'detail'" class="sidebar-toggle" type="button" :aria-expanded="sidebarExpanded" :aria-label="sidebarExpanded ? '收起导航' : '展开导航'" @click="sidebarExpanded = !sidebarExpanded">{{ sidebarExpanded ? '‹' : '›' }}</button>
      <nav class="navs">
        <p class="group-title">工作台</p>
        <button type="button" class="nav-link" aria-label="问题单总表" title="问题单总表" :aria-current="currentNav('board') ? 'page' : undefined" @click="showNav('board')"><NavIcon name="board" /><span>问题单总表</span></button>
        <button type="button" class="nav-link" aria-label="待认领池" title="待认领池" :aria-current="currentNav('pool') ? 'page' : undefined" @click="showNav('pool')"><NavIcon name="pool" /><span>待认领池</span><b v-if="unclaimedCount !== null" class="count-pill">{{ unclaimedCount }}</b></button>
        <button type="button" class="nav-link" aria-label="我的待办" title="我的待办" :aria-current="currentNav('mine') ? 'page' : undefined" @click="showNav('mine')"><NavIcon name="mine" /><span>我的待办</span></button>
        <button type="button" class="nav-link" aria-label="到期回访" title="到期回访" :aria-current="currentNav('due') ? 'page' : undefined" @click="showNav('due')"><NavIcon name="due" /><span>到期回访</span><span v-if="dueCount !== null" class="due-pill"><i></i>{{ dueCount }}</span></button>
        <p class="group-title next">受理与查询</p>
        <button type="button" class="nav-link" aria-label="查桩与建单" title="查桩与建单" :aria-current="currentNav('intake') ? 'page' : undefined" @click="showNav('intake')"><NavIcon name="intake" /><span>查桩与建单</span></button>
        <p class="group-title next">系统管理</p>
        <button v-if="account.role === 'admin'" type="button" class="nav-link" aria-label="坐席与账号" title="坐席与账号" :aria-current="currentNav('accounts') ? 'page' : undefined" @click="showNav('accounts')"><NavIcon name="accounts" /><span>坐席与账号</span></button>
        <button type="button" class="nav-link" aria-label="安全与改密" title="安全与改密" :aria-current="currentNav('security') ? 'page' : undefined" @click="showNav('security')"><NavIcon name="security" /><span>安全与改密</span></button>
      </nav>
      <div class="profile">
        <span class="avatar" aria-hidden="true">{{ initial(account.username) }}</span>
        <div class="profile-copy">
          <span
            ref="whoLabel"
            class="who"
            :class="{ truncated: nameOverflows }"
            :tabindex="nameOverflows ? 0 : undefined"
            @pointerenter="openNameTip"
            @pointerleave="scheduleHideNameTip"
            @focus="openNameTip"
            @blur="hideNameTip"
            @click="toggleNameTip"
          >{{ account.username }}</span>
          <div class="profile-meta">
            <span class="role-pill">{{ account.role === 'admin' ? '管理员' : '坐席' }}</span>
            <select v-model="agentStatus" class="agent-status-select" :class="agentStatus" aria-label="坐席在岗状态" @change="persistAgentStatus">
              <option value="ready">● 就绪</option>
              <option value="break">● 休息</option>
              <option value="away">● 离岗</option>
            </select>
          </div>
        </div>
        <button class="exit" type="button" aria-label="退出登录" @click="signOut">
          <NavIcon name="exit" />
        </button>
        <div
          v-show="nameTip && nameOverflows"
          id="account-name-tip"
          class="name-tip"
          role="tooltip"
          aria-hidden="true"
          @pointerenter="openNameTip"
          @pointerleave="scheduleHideNameTip"
        >{{ account.username }}</div>
      </div>
    </aside>
    <main class="workspace">
      <div v-if="error" ref="errorSummary" tabindex="-1" class="error-banner">
        <el-alert :title="error" type="error" :closable="false" show-icon />
        <button v-if="conflict" class="btn secondary" type="button" @click="refreshAfterConflict">一键刷新工单</button>
        <button v-else-if="problem && view === 'detail'" class="btn secondary" type="button" @click="openProblem(problem.id)">刷新问题单</button>
      </div>
      <header v-if="view !== 'detail'" class="page-head">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageLede }}</p>
        </div>
        <div class="head-actions">
          <div v-if="view === 'due'" class="scope-switch" role="group" aria-label="到期回访范围">
            <button type="button" :aria-pressed="dueScope === 'mine'" @click="openDue('mine')">我的</button>
            <button type="button" :aria-pressed="dueScope === 'team'" @click="openDue('team')">团队</button>
          </div>
          <button v-if="view === 'board'" class="btn primary" type="button" @click="showNav('intake')">+ 新建问题单</button>
        </div>
      </header>
      <section v-if="view === 'board' && summary" class="summary" aria-label="全量问题单统计摘要">
        <div class="quick-links">
          <button class="quick quick-claim" type="button" @click="showNav('pool')"><i></i>待认领 <strong>{{ unclaimedCount }}</strong> 单 <span class="arrow">→</span></button>
          <button class="quick quick-due" type="button" @click="openDue('team')"><i></i>到期回访 <strong>{{ dueCount }}</strong> 单 <span class="arrow">→</span></button>
        </div>
        <span class="summary-divider"></span>
        <p>全部问题单共 <strong>{{ summary.total }}</strong> 条<span v-if="scopeText" class="scope">（{{ scopeText }}）</span></p>
      </section>
      <ProblemBoard v-if="view === 'board'" :items="boardItems" :total="boardTotal" :page="boardPage" :page-size="pageSize" :has-next="boardHasNext" :busy="busy" :filters="boardFilters" :summary="summary" :categories="boardCategories" :assignees="assignees" @open="openProblem" @claim="claimRow" @search="searchBoard" @page="goToBoardPage" />
      <QueueList v-else-if="view === 'mine'" :items="mineItems" :total="mineTotal" :busy="busy" :has-more="Boolean(mineNext)" empty="当前没有负责中的问题单" @open="openProblem" @more="moreMine" />
      <QueueList v-else-if="view === 'due'" :items="dueItems" :total="dueTotal" :busy="busy" :has-more="Boolean(dueNext)" :empty="dueScope === 'mine' ? '我的到期回访为空' : '团队到期回访为空'" @open="openProblem" @more="moreDue" />
      <IntakePanel v-else-if="view === 'intake'" v-model:query="query" v-model:title="title" v-model:description="description" :device="device" :orders="deviceOrders" :busy="busy" @search="search" @create="create" @open="openProblem" />
      <AccountPanel v-else-if="view === 'accounts' && account.role === 'admin'" :accounts="accounts" :busy="busy" :notice="accountNotice" :create-account="addAccount" :reset-password="resetAccountPassword" @toggle="toggleAccount" />
      <PasswordPanel v-else-if="view === 'security'" :busy="busy" :notice="passwordNotice" :change-password="changeOwnPassword" />
      <PersonalWorkbench
        v-else-if="view === 'detail' && problem"
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
        @back="backToList"
        @intake="showNav('intake')"
        @mine="showNav('mine')"
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
      />
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import AccountPanel from './components/AccountPanel.vue'
import BrandLogo from './components/BrandLogo.vue'
import IntakePanel from './components/IntakePanel.vue'
import LoginScreen from './components/LoginScreen.vue'
import NavIcon from './components/NavIcon.vue'
import PasswordPanel from './components/PasswordPanel.vue'
import PersonalWorkbench from './components/PersonalWorkbench.vue'
import ProblemBoard from './components/ProblemBoard.vue'
import QueueList from './components/QueueList.vue'
import { emptyFilters, useBoardQueues } from './composables/useBoardQueues'
import { useProblemDraft } from './composables/useProblemDraft'
import { initial, orderNumber, summaryScope } from './status'
import { currentAccount, login, logout, addEvidence, addFollowup, assessProblem, assignableAccounts, changePassword, claimProblem, createAccount, createProblem, escalateProblem, findProblems, finishCallback, handoffProblem, linkFieldOrder, listAccounts, loadProblem, lookupDevice, quickCloseProblem, recordFieldResult, resetAccountPassword as resetAccountPasswordApi, scheduleCallback, setAccountEnabled, suspendProblem, withdrawField } from './api/workbench'

const REMEMBER_KEY = 'songguo-workbench-username'
const AGENT_STATUS_KEY = 'songguo-agent-status'
const pageSize = 10
const agentStatus = ref(localStorage.getItem(AGENT_STATUS_KEY) || 'ready')
function persistAgentStatus() {
  localStorage.setItem(AGENT_STATUS_KEY, agentStatus.value)
}
const query = ref('')
const title = ref('')
const description = ref('')
const device = ref(null)
const problem = ref(null)
const busy = ref(false)
const error = ref('')
const errorSummary = ref(null)
const loginPanel = ref(null)
const deviceOrders = ref([])
const account = ref(null)
const authReady = ref(false)
const username = ref('')
const password = ref('')
const rememberAccount = ref(false)
const evidenceLabels = { customer: '车主确认', platform: '平台充电记录', field: '现场复测' }
const accounts = ref([])
const assignees = ref([])
const accountNotice = ref('')
const conflict = ref(false)
const passwordNotice = ref('')
const view = ref('board')
const returnView = ref('board')
const detailFocus = ref('')
const navOpen = ref(false)
const sidebarExpanded = ref(false)
const wide = ref(true)
let mediaQuery

const {
  boardItems, boardTotal, boardPage, boardHasNext, boardCategories, boardFilters, boardCursors, summary,
  dueScope, dueItems, dueTotal, dueNext,
  mineItems, mineTotal, mineNext,
  loadBoard, loadDue, loadMine, refreshQueues: refreshBoardQueues, ensureFreshQueue, resetQueues
} = useBoardQueues(pageSize)

function ownerLabel(value) {
  return !value || value === 'early' ? '无主' : value
}
function unowned(item) {
  return !item?.owner || item.owner === 'early'
}
function canHandle(current) {
  if (!current || current.state === 'resolved') return false
  if (account.value?.role === 'admin') return true
  if (unowned(current)) return false
  return account.value?.username === current.owner
}

const {
  draft, refreshNote, beginCallback, snapshotDraft, fillFromServer, restoreDraft, resetDraft
} = useProblemDraft({ canHandle, unowned, account, assignees })

const pageTitle = computed(() => {
  if (view.value === 'board' && boardFilters.value.bucket === 'unclaimed') return '待认领池'
  return { board: '问题单总表', due: '到期回访', mine: '我的待办', intake: '查桩与建单', accounts: '坐席与账号', security: '安全与改密' }[view.value] || '问题单总表'
})
const pageLede = computed(() => {
  if (view.value === 'board' && boardFilters.value.bucket === 'unclaimed') return '负责人为无主的问题单。认领成功后才能继续处理。'
  return {
    board: '统一管理售后受理、认领流转与回访闭环',
    due: dueScope.value === 'mine' ? '我负责或由我回访、且已到时间的问题单' : '团队里已到回访时间的问题单',
    mine: '当前账号负责且未结案的问题单',
    intake: '按桩编码或手机号查询设备，并受理新的问题单',
    accounts: '创建账号、启用或停用，以及重置密码',
    security: '修改当前账号密码。修改后当前会话保持登录'
  }[view.value] || ''
})
const unclaimedCount = computed(() => summary.value ? Number(summary.value.states?.unclaimed || 0) : null)
const dueCount = computed(() => summary.value ? Number(summary.value.due || 0) : null)
const scopeText = computed(() => summaryScope(summary.value))

function clearAccount() {
  account.value = null
  problem.value = null
  device.value = null
  deviceOrders.value = []
  query.value = ''
  title.value = ''
  description.value = ''
  resetQueues()
  resetDraft()
  passwordNotice.value = ''
  accountNotice.value = ''
  conflict.value = false
  view.value = 'board'
  navOpen.value = false
}

function syncUrl() {
  if (!account.value) {
    history.replaceState(null, '', location.pathname)
    return
  }
  const params = new URLSearchParams()
  if (view.value === 'detail' && problem.value) {
    params.set('problem', orderNumber(problem.value))
    if (returnView.value && returnView.value !== 'board') params.set('from', returnView.value)
    if (returnView.value === 'board' && boardFilters.value.bucket) params.set('bucket', boardFilters.value.bucket)
  } else if (view.value === 'board') {
    if (boardFilters.value.bucket) params.set('bucket', boardFilters.value.bucket)
  } else if (view.value === 'due') {
    params.set('view', 'due')
    if (dueScope.value === 'mine') params.set('scope', 'mine')
  } else if (view.value && view.value !== 'board') {
    params.set('view', view.value)
  }
  const queryText = params.toString()
  history.replaceState(null, '', queryText ? '?' + queryText : location.pathname)
}

function currentNav(name) {
  const onDetail = view.value === 'detail'
  const base = onDetail ? returnView.value : view.value
  const bucket = boardFilters.value.bucket
  if (name === 'pool') return base === 'board' && bucket === 'unclaimed'
  if (name === 'board') return base === 'board' && bucket !== 'unclaimed'
  return base === name
}

async function prepareWorkspace() {
  await loadAccounts()
  assignees.value = await assignableAccounts()
  boardFilters.value = emptyFilters()
  boardCursors.value = [null]
  boardPage.value = 0
  await loadBoard()
  await restoreRoute()
}

async function restoreRoute() {
  const params = new URLSearchParams(location.search)
  const problemId = params.get('problem')
  const views = ['board', 'due', 'mine', 'intake', 'accounts', 'security']
  if (problemId) {
    const from = params.get('from')
    returnView.value = views.includes(from) ? from : 'board'
    if (params.get('bucket')) boardFilters.value = { ...emptyFilters(), bucket: params.get('bucket') }
    await showProblem(problemId)
    return
  }
  const requested = params.get('view')
  if (requested === 'accounts' && account.value?.role !== 'admin') view.value = 'board'
  else if (views.includes(requested)) view.value = requested
  else view.value = 'board'
  if (params.get('bucket') && view.value === 'board') {
    boardFilters.value = { ...emptyFilters(), bucket: params.get('bucket') }
    boardPage.value = 0
    boardCursors.value = [null]
    await loadBoard()
  }
  if (view.value === 'due') {
    dueScope.value = params.get('scope') === 'mine' ? 'mine' : 'team'
    await loadDue(false)
  }
  if (view.value === 'mine') await loadMine(false)
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

function onMedia(event) {
  wide.value = event.matches
  if (wide.value) navOpen.value = false
}
const whoLabel = ref(null)
const nameOverflows = ref(false)
const nameTip = ref(false)
const namePinned = ref(false)
let nameObserver = null
let nameHideTimer = 0

function measureName() {
  const el = whoLabel.value
  if (!el) {
    nameOverflows.value = false
    return
  }
  const overflow = el.scrollWidth > el.clientWidth + 1
  nameOverflows.value = overflow
  if (!overflow) hideNameTip()
}

function bindNameObserver() {
  if (typeof ResizeObserver === 'undefined') return
  if (!nameObserver) nameObserver = new ResizeObserver(() => measureName())
  nameObserver.disconnect()
  if (whoLabel.value) {
    nameObserver.observe(whoLabel.value)
    measureName()
  }
}

function openNameTip() {
  if (!nameOverflows.value) return
  window.clearTimeout(nameHideTimer)
  nameTip.value = true
}

function scheduleHideNameTip() {
  if (namePinned.value) return
  window.clearTimeout(nameHideTimer)
  nameHideTimer = window.setTimeout(() => {
    if (!namePinned.value) nameTip.value = false
  }, 180)
}

function toggleNameTip() {
  if (!nameOverflows.value) return
  namePinned.value = !namePinned.value
  nameTip.value = namePinned.value
}

function hideNameTip(event) {
  if (event && event.type === 'blur' && whoLabel.value && document.activeElement === whoLabel.value) return
  namePinned.value = false
  nameTip.value = false
  window.clearTimeout(nameHideTimer)
}

function onKey(event) {
  if (event.key !== 'Escape') return
  if (nameTip.value) {
    hideNameTip()
    return
  }
  navOpen.value = false
}

onMounted(async () => {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    username.value = saved
    rememberAccount.value = true
  }
  mediaQuery = window.matchMedia('(min-width: 960px)')
  wide.value = mediaQuery.matches
  mediaQuery.addEventListener('change', onMedia)
  window.addEventListener('keydown', onKey)
  window.addEventListener('workbench-session-expired', clearAccount)
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => measureName())
  try {
    account.value = await currentAccount()
  } catch (failure) {
    return
  } finally {
    authReady.value = true
  }
  if (account.value) await run(prepareWorkspace)
})
watch(account, async () => {
  await nextTick()
  bindNameObserver()
})

onUnmounted(() => {
  window.removeEventListener('workbench-session-expired', clearAccount)
  window.removeEventListener('keydown', onKey)
  if (mediaQuery) mediaQuery.removeEventListener('change', onMedia)
  if (nameObserver) nameObserver.disconnect()
  window.clearTimeout(nameHideTimer)
})

const fieldInProgress = computed(() => problem.value?.fieldOrders?.some(item => ['linked', 'in_progress'].includes(item.state)))
const nextAction = computed(() => {
  const current = problem.value
  if (!current) return ''
  if (current.state === 'resolved') return '已结案，不再写入'
  if (current.state === 'field' && fieldInProgress.value) return '登记现场回执'
  if (current.state === 'field') return '关联智享乐装现场单'
  if (current.pendingFollowup?.nextStep) return current.pendingFollowup.nextStep
  if (current.state === 'verify' || current.resumeState === 'verify') return '安排回访或提交恢复依据'
  return '记录跟进或安排回访'
})

const canProcess = computed(() => canHandle(problem.value))
const handlingDenied = computed(() => {
  const current = problem.value
  if (!current || current.state === 'resolved' || canHandle(current)) return ''
  if (unowned(current)) return '这张问题单还没有负责人。请先认领，认领成功后才能保存跟进、安排回访、撤回待派单或登记现场单。'
  return '这张问题单由 ' + ownerLabel(current.owner) + ' 负责。当前账号无权处理，跟进、回访、转交、撤回和现场单都不会被保存。需要接手时，请由负责人转交，或由管理员处理。'
})
const canHandoff = computed(() => problem.value && problem.value.state !== 'resolved' && !unowned(problem.value)
  && (account.value?.role === 'admin' || account.value?.username === problem.value.owner))

function refreshQueues() {
  return refreshBoardQueues(returnView.value)
}

function saveFieldLink(number) {
  return run(async () => {
    await linkFieldOrder(problem.value.id, number, problem.value.version)
    draft.fieldNumber = ''
    await showProblem(problem.value.id)
    await refreshQueues()
  }, { conflict: true })
}
function saveFieldResult(form) {
  return run(async () => {
    await recordFieldResult(problem.value.id, { ...form, expectedVersion: problem.value.version })
    draft.fieldReceipt = ''
    await showProblem(problem.value.id)
    await refreshQueues()
  }, { conflict: true })
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
function refreshAfterConflict() {
  return run(async () => {
    conflict.value = false
    await showProblem(problem.value.id, { preserveDraft: true })
  })
}
function openProblem(id, focus) {
  if (view.value !== 'detail') returnView.value = view.value
  detailFocus.value = typeof focus === 'string' ? focus : ''
  navOpen.value = false
  return run(async () => {
    await showProblem(id)
    await nextTick()
    const target = detailFocus.value && document.getElementById('action-' + detailFocus.value)
    if (target) target.scrollIntoView({ block: 'nearest' })
  })
}
function backToList() {
  view.value = returnView.value || 'board'
  problem.value = null
  refreshNote.value = ''
  navOpen.value = false
  syncUrl()
  run(() => ensureFreshQueue(view.value))
}

function searchBoard(filters) {
  view.value = 'board'
  boardFilters.value = filters
  boardCursors.value = [null]
  boardPage.value = 0
  syncUrl()
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
function openBoard(bucket) {
  problem.value = null
  refreshNote.value = ''
  view.value = 'board'
  boardFilters.value = { ...emptyFilters(), bucket: bucket || '' }
  boardCursors.value = [null]
  boardPage.value = 0
  navOpen.value = false
  syncUrl()
  return run(loadBoard)
}
function openDue(scope) {
  problem.value = null
  refreshNote.value = ''
  dueScope.value = scope === 'mine' ? 'mine' : 'team'
  dueNext.value = null
  view.value = 'due'
  navOpen.value = false
  syncUrl()
  return run(() => loadDue(false))
}
function moreDue() { return run(() => loadDue(true)) }
function moreMine() { return run(() => loadMine(true)) }
function showNav(next) {
  navOpen.value = false
  problem.value = null
  refreshNote.value = ''
  if (next === 'board') return openBoard('')
  if (next === 'pool') return openBoard('unclaimed')
  if (next === 'due') return openDue(dueScope.value || 'team')
  if (next === 'mine') {
    view.value = 'mine'
    syncUrl()
    return run(() => loadMine(false))
  }
  if (next === 'accounts' && account.value?.role !== 'admin') return showNav('board')
  view.value = next
  syncUrl()
  if (next === 'accounts') return run(async () => { await loadAccounts(); assignees.value = await assignableAccounts() })
  return undefined
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
function claim() {
  return run(async () => {
    const id = problem.value.id
    await claimProblem(id, problem.value.version)
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function claimRow(item) {
  return run(async () => {
    try {
      await claimProblem(item.id, item.version)
    } catch (failure) {
      await loadBoard()
      throw failure
    }
    if (problem.value && problem.value.id === item.id) await showProblem(item.id)
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
async function loadAccounts() {
  if (account.value?.role !== 'admin') {
    accounts.value = []
    return
  }
  accounts.value = await listAccounts()
}
function addAccount(form) {
  accountNotice.value = ''
  return run(async () => {
    await createAccount({ username: form.username, password: form.password, role: form.role })
    await loadAccounts()
    assignees.value = await assignableAccounts()
    accountNotice.value = '账号已创建，账号列表和转交候选已更新'
  })
}
function toggleAccount(item) {
  return run(async () => {
    await setAccountEnabled(item.username, !item.enabled)
    await loadAccounts()
    assignees.value = await assignableAccounts()
  })
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
function changeOwnPassword(form) {
  return run(async () => {
    await changePassword(form)
    passwordNotice.value = '密码已更新。当前会话保持登录，其他设备需要用新密码重新登录。'
  })
}
function resetAccountPassword(form) {
  return run(async () => {
    await resetAccountPasswordApi(form.username, form.password)
    accountNotice.value = form.username === account.value?.username
      ? '已重置自己的密码。当前会话保持登录，其他设备需要用新密码重新登录。'
      : '已重置该账号的密码。该账号的旧会话会在下次请求时失效。'
    await loadAccounts()
  })
}
function search() {
  return run(async () => {
    device.value = null
    deviceOrders.value = []
    device.value = await lookupDevice(query.value.trim())
    deviceOrders.value = await findProblems(device.value.device)
    title.value = ''
    description.value = ''
  })
}
function create(meta) {
  return run(async () => {
    const payload = {
      device: device.value.device,
      title: title.value.trim(),
      description: description.value.trim(),
      channel: meta?.channel || '400',
      category: meta?.category || '充电故障',
      faultOccurredAt: meta?.faultOccurredAt || null
    }
    const created = await createProblem(payload)
    returnView.value = 'intake'
    view.value = 'detail'
    await showProblem(created.id)
    await refreshQueues()
  })
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
function saveNote() {
  return run(async () => {
    const id = problem.value.id
    await addFollowup(id, draft.note, problem.value.version)
    draft.note = ''
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
</script>
