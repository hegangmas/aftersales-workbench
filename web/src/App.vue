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
          <span class="role-pill">{{ account.role === 'admin' ? '管理员' : '坐席' }}</span>
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
      <QueueList v-else-if="view === 'mine'" mode="mine" :items="mineItems" :total="mineTotal" :busy="busy" :has-more="Boolean(mineNext)" empty="当前没有负责中的问题单" @open="openProblem" @more="moreMine" />
      <QueueList v-else-if="view === 'due'" mode="due" :items="dueItems" :total="dueTotal" :busy="busy" :has-more="Boolean(dueNext)" :empty="dueScope === 'mine' ? '我的到期回访为空' : '团队到期回访为空'" @open="openProblem" @more="moreDue" />
      <IntakePanel v-else-if="view === 'intake'" v-model:query="query" v-model:title="title" v-model:description="description" :device="device" :orders="deviceOrders" :busy="busy" @search="search" @create="create" @open="openProblem" />
      <AccountPanel v-else-if="view === 'accounts' && account.role === 'admin'" :accounts="accounts" :busy="busy" :notice="accountNotice" :create-account="addAccount" :reset-password="resetAccountPassword" @toggle="toggleAccount" />
      <PasswordPanel v-else-if="view === 'security'" :busy="busy" :notice="passwordNotice" :change-password="changeOwnPassword" />
      <template v-else-if="view === 'detail' && problem">
      <PersonalWorkbench :problem="problem" :busy="busy" :can-process="canProcess" :handling-denied="handlingDenied" :field-in-progress="fieldInProgress" v-model:note="note" @back="backToList" @intake="showNav('intake')" @mine="showNav('mine')" @open="openProblem" @claim="claim" @save-note="saveNote" />
      <div id="problem-actions"><ProblemDetail :problem="problem" :busy="busy" :can-process="canProcess" :can-handoff="canHandoff" :handling-denied="handlingDenied" :next-action="nextAction" :refresh-note="refreshNote"
        v-model:note="note" v-model:owner="owner" v-model:due-at="dueAt" v-model:next-step="nextStep" v-model:outcome="outcome" v-model:result="result" :show-callback="showCallback" v-model:contacted-at="contactedAt" v-model:evidence-source="evidenceSource" v-model:evidence="evidence"
        v-model:withdraw-reason="withdrawReason" v-model:handoff-target="handoffTarget" v-model:handoff-reason="handoffReason" v-model:field-number="fieldNumber" v-model:field-receipt="fieldReceipt" v-model:field-state="fieldState"
        :assignees="assignees" :evidence-labels="evidenceLabels" :field-in-progress="fieldInProgress" @back="backToList" @claim="claim" @save-note="saveNote" @save-schedule="saveSchedule" @begin-callback="beginCallback" @save-outcome="saveOutcome" @handoff="handoff" @withdraw="withdrawDispatch" @link="saveFieldLink" @field-result="saveFieldResult" /></div>
      </template>
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
import ProblemDetail from './components/ProblemDetail.vue'
import QueueList from './components/QueueList.vue'
import { initial, orderNumber, summaryScope } from './status'
import { currentAccount, login, logout, addFollowup, assignableAccounts, changePassword, claimProblem, createAccount, createProblem, dueCallbacks, findProblems, finishCallback, handoffProblem, linkFieldOrder, listAccounts, loadProblem, lookupDevice, mineProblems, recordFieldResult, resetAccountPassword as resetAccountPasswordApi, scheduleCallback, searchProblems, setAccountEnabled, withdrawField } from './api/workbench'

const REMEMBER_KEY = 'songguo-workbench-username'
const pageSize = 10
const query = ref('')
const title = ref('')
const description = ref('')
const note = ref('')
const device = ref(null)
const problem = ref(null)
const busy = ref(false)
const error = ref('')
const errorSummary = ref(null)
const loginPanel = ref(null)
const orderId = ref('')
const deviceOrders = ref([])
const owner = ref('')
const dueAt = ref('')
const nextStep = ref('')
const outcome = ref('continue')
const result = ref('')
const account = ref(null)
const authReady = ref(false)
const username = ref('')
const password = ref('')
const rememberAccount = ref(false)
const showCallback = ref(false)
const contactedAt = ref('')
const evidenceSource = ref('')
const evidence = ref('')
const evidenceLabels = { customer: '车主确认', platform: '平台充电记录', field: '现场复测' }
const accounts = ref([])
const assignees = ref([])
const withdrawReason = ref('')
const handoffTarget = ref('')
const handoffReason = ref('')
const accountNotice = ref('')
const fieldNumber = ref('')
const fieldReceipt = ref('')
const fieldState = ref('complete')
const conflict = ref(false)
const refreshNote = ref('')
const passwordNotice = ref('')
const boardItems = ref([])
const boardTotal = ref(0)
const boardPage = ref(0)
const boardHasNext = ref(false)
const boardCategories = ref([])
const boardFilters = ref(emptyFilters())
const boardCursors = ref([null])
const summary = ref(null)
const view = ref('board')
const returnView = ref('board')
const detailFocus = ref('')
const navOpen = ref(false)
const sidebarExpanded = ref(false)
const wide = ref(true)
const dueScope = ref('team')
const dueItems = ref([])
const dueTotal = ref(0)
const dueNext = ref(null)
const dueLoaded = ref(false)
let mediaQuery
const mineItems = ref([])
const mineTotal = ref(0)
const mineNext = ref(null)
const mineLoaded = ref(false)

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

function emptyFilters() {
  return { q: '', state: '', owner: '', kind: '', category: '', bucket: '' }
}

function beginCallback() {
  contactedAt.value = new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 16)
  showCallback.value = true
}

function clearAccount() {
  account.value = null
  problem.value = null
  device.value = null
  deviceOrders.value = []
  query.value = ''
  orderId.value = ''
  title.value = ''
  description.value = ''
  boardItems.value = []
  boardTotal.value = 0
  boardFilters.value = emptyFilters()
  boardPage.value = 0
  boardCursors.value = [null]
  summary.value = null
  passwordNotice.value = ''
  accountNotice.value = ''
  refreshNote.value = ''
  conflict.value = false
  view.value = 'board'
  navOpen.value = false
  dueItems.value = []
  mineItems.value = []
  dueLoaded.value = false
  mineLoaded.value = false
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
const canProcess = computed(() => canHandle(problem.value))
const handlingDenied = computed(() => {
  const current = problem.value
  if (!current || current.state === 'resolved' || canHandle(current)) return ''
  if (unowned(current)) return '这张问题单还没有负责人。请先认领，认领成功后才能保存跟进、安排回访、撤回待派单或登记现场单。'
  return '这张问题单由 ' + ownerLabel(current.owner) + ' 负责。当前账号无权处理，跟进、回访、转交、撤回和现场单都不会被保存。需要接手时，请由负责人转交，或由管理员处理。'
})
const canHandoff = computed(() => problem.value && problem.value.state !== 'resolved' && !unowned(problem.value)
  && (account.value?.role === 'admin' || account.value?.username === problem.value.owner))

function saveFieldLink(number) {
  return run(async () => {
    await linkFieldOrder(problem.value.id, number, problem.value.version)
    fieldNumber.value = ''
    await showProblem(problem.value.id)
    await refreshQueues()
  }, { conflict: true })
}
function saveFieldResult(form) {
  return run(async () => {
    await recordFieldResult(problem.value.id, { ...form, expectedVersion: problem.value.version })
    fieldReceipt.value = ''
    await showProblem(problem.value.id)
    await refreshQueues()
  }, { conflict: true })
}

function snapshotDraft() {
  return {
    note: note.value, owner: owner.value, dueAt: dueAt.value, nextStep: nextStep.value, outcome: outcome.value,
    result: result.value, showCallback: showCallback.value, contactedAt: contactedAt.value, evidenceSource: evidenceSource.value,
    evidence: evidence.value, withdrawReason: withdrawReason.value, handoffTarget: handoffTarget.value, handoffReason: handoffReason.value,
    fieldNumber: fieldNumber.value, fieldReceipt: fieldReceipt.value, fieldState: fieldState.value
  }
}
function textOf(draft) {
  const parts = []
  if (draft.note) parts.push('跟进「' + draft.note + '」')
  if (draft.nextStep || draft.owner) parts.push('回访安排「' + [draft.owner, draft.dueAt, draft.nextStep].filter(Boolean).join(' ') + '」')
  if (draft.result) parts.push('回访结果「' + draft.result + '」')
  if (draft.handoffReason || draft.handoffTarget) parts.push('转交「' + [draft.handoffTarget, draft.handoffReason].filter(Boolean).join(' ') + '」')
  if (draft.withdrawReason) parts.push('撤回原因「' + draft.withdrawReason + '」')
  if (draft.fieldNumber) parts.push('现场单号「' + draft.fieldNumber + '」')
  if (draft.fieldReceipt) parts.push('现场回执「' + draft.fieldReceipt + '」')
  return parts.join('；')
}
function fillFromServer(current) {
  const task = current.pendingFollowup
  owner.value = task?.owner || ''
  dueAt.value = task?.dueAt?.replace(' ', 'T').slice(0, 16) || ''
  nextStep.value = task?.nextStep || ''
  note.value = ''
  outcome.value = 'continue'
  result.value = ''
  showCallback.value = Boolean(task)
  contactedAt.value = ''
  evidenceSource.value = ''
  evidence.value = ''
  fieldNumber.value = ''
  fieldReceipt.value = ''
  fieldState.value = 'complete'
  refreshNote.value = ''
}
function restoreDraft(draft, current) {
  if (current.state !== 'resolved' && !canHandle(current)) {
    const kept = textOf(draft)
    refreshNote.value = (unowned(current) ? '刷新后问题单仍无主，请先认领再处理。' : '刷新后当前账号不能处理这张问题单。') + (kept ? '未提交内容暂不能提交：' + kept : '')
    return
  }
  const active = current.fieldOrders?.some(item => ['linked', 'in_progress'].includes(item.state))
  const notes = []
  if (current.state === 'resolved') {
    notes.push('刷新后问题单已结案，跟进、回访、转交、撤回和现场单都不能再提交。')
    const kept = textOf(draft)
    if (kept) notes.push('未提交内容仅作保留，无法提交：' + kept)
    refreshNote.value = notes.join('')
    return
  }
  note.value = draft.note
  const canCallback = current.state !== 'field' && !active
  if (canCallback) {
    owner.value = draft.owner
    dueAt.value = draft.dueAt
    nextStep.value = draft.nextStep
    showCallback.value = draft.showCallback
    outcome.value = draft.outcome
    result.value = draft.result
    contactedAt.value = draft.contactedAt
    evidenceSource.value = draft.evidenceSource
    evidence.value = draft.evidence
  } else if (draft.nextStep || draft.result || draft.owner || draft.dueAt) {
    notes.push(current.state === 'field' ? '刷新后问题处于现场服务，回访安排和回访结果暂不能提交。' : '刷新后还有未结束的现场单，请先登记结果，回访暂不能提交。')
    const kept = [draft.owner, draft.dueAt, draft.nextStep, draft.result].filter(Boolean).join(' ')
    if (kept) notes.push('未提交的回访内容：' + kept + '。')
  }
  const handoffOpen = !unowned(current) && (account.value?.role === 'admin' || account.value?.username === current.owner)
  if (handoffOpen) {
    const known = assignees.value.some(item => item.username === draft.handoffTarget)
    handoffTarget.value = known ? draft.handoffTarget : ''
    handoffReason.value = draft.handoffReason
    if (draft.handoffTarget && !known) notes.push('原接收人已不在可转交名单中，请重新选择。转交原因仍保留。')
  } else if (draft.handoffReason || draft.handoffTarget) {
    notes.push(unowned(current) ? '刷新后问题单仍无主，需要先认领才能转交。' : '刷新后当前账号不能转交这张问题单。')
    notes.push('未提交的转交：' + [draft.handoffTarget, draft.handoffReason].filter(Boolean).join(' ') + '。')
  }
  const canWithdraw = current.state === 'field' && !active
  if (canWithdraw) withdrawReason.value = draft.withdrawReason
  else if (draft.withdrawReason) {
    notes.push(active ? '刷新后已有未结束的现场单，不能撤回待派单。' : '刷新后问题单已不在待派单阶段，不能撤回。')
    notes.push('未提交的撤回原因：' + draft.withdrawReason + '。')
  }
  if (current.state === 'field' && !active) fieldNumber.value = draft.fieldNumber
  else if (draft.fieldNumber) notes.push('刷新后不能再关联新的现场单。未提交的现场单号：' + draft.fieldNumber + '。')
  if (current.state === 'field' && active) {
    fieldReceipt.value = draft.fieldReceipt
    fieldState.value = draft.fieldState || 'complete'
  } else if (draft.fieldReceipt) {
    notes.push('刷新后现场回执不再适用。未提交的回执：' + draft.fieldReceipt + '。')
  }
  refreshNote.value = notes.length ? notes.join('') : '已刷新到最新版本，未提交的内容仍留在表单中。'
}

async function showProblem(id, options) {
  const preserve = Boolean(options && options.preserveDraft)
  const draft = preserve ? snapshotDraft() : null
  problem.value = await loadProblem(id)
  orderId.value = orderNumber(problem.value)
  view.value = 'detail'
  assignees.value = await assignableAccounts()
  if (preserve) restoreDraft(draft, problem.value)
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
}

async function loadBoard() {
  const cursor = boardCursors.value[boardPage.value]
  const page = await searchProblems({ ...boardFilters.value, cursor, limit: pageSize })
  boardItems.value = page.items || []
  boardTotal.value = Number(page.total || 0)
  boardHasNext.value = Boolean(page.nextCursor)
  boardCategories.value = page.categories || []
  if (page.summary) summary.value = page.summary
  if (page.nextCursor) boardCursors.value[boardPage.value + 1] = page.nextCursor
}
async function refreshQueues() {
  await loadBoard()
  if (dueLoaded.value) await loadDue(false)
  if (mineLoaded.value) await loadMine(false)
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
async function loadDue(append) {
  const page = await dueCallbacks(dueScope.value, append ? dueNext.value : null)
  const items = page.items || []
  dueItems.value = append ? dueItems.value.concat(items) : items
  dueTotal.value = Number(page.total ?? dueItems.value.length)
  dueNext.value = page.nextCursor || null
  dueLoaded.value = true
}
async function loadMine(append) {
  const page = await mineProblems(append ? mineNext.value : null)
  const items = page.items || []
  mineItems.value = append ? mineItems.value.concat(items) : items
  mineTotal.value = Number(page.total ?? mineItems.value.length)
  mineNext.value = page.nextCursor || null
  mineLoaded.value = true
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
    await withdrawField(id, withdrawReason.value, problem.value.version)
    withdrawReason.value = ''
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
    await handoffProblem(id, { target: handoffTarget.value, reason: handoffReason.value, expectedVersion: problem.value.version })
    handoffReason.value = ''
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
    await scheduleCallback(id, { owner: owner.value, nextStep: nextStep.value, dueAt: dueAt.value, expectedVersion: problem.value.version })
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
function saveOutcome() {
  return run(async () => {
    const id = problem.value.id
    await finishCallback(id, { outcome: outcome.value, note: result.value, contactedAt: contactedAt.value, evidenceSource: evidenceSource.value || null, evidence: evidence.value || null, expectedVersion: problem.value.version })
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
function create() {
  return run(async () => {
    const created = await createProblem({ device: device.value.device, title: title.value.trim(), description: description.value.trim() })
    returnView.value = 'intake'
    view.value = 'detail'
    await showProblem(created.id)
    await refreshQueues()
  })
}
function saveNote() {
  return run(async () => {
    const id = problem.value.id
    await addFollowup(id, note.value, problem.value.version)
    note.value = ''
    await showProblem(id)
    await refreshQueues()
  }, { conflict: true })
}
</script>
