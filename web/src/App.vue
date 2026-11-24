<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand">
        <img class="brand-logo" :src="logoUrl" alt="松果" width="90" height="32">
        <strong>售后工作台</strong>
        <span class="env-chip">模拟数据</span>
      </div>
      <div v-if="account" class="account"><span>{{ account.username }}</span><button type="button" @click="signOut">退出</button></div>
    </header>
    <main v-if="authReady && !account" class="login-layout">
      <section class="login-panel">
        <h1>账号登录</h1>
        <p class="sim-note">模拟数据 · 未连接生产系统</p>
        <div v-if="error" ref="errorSummary" tabindex="-1" class="error-summary">
          <el-alert :title="error" type="error" :closable="false" show-icon />
        </div>
        <form @submit.prevent="signIn">
          <label for="username">账号</label>
          <el-input id="username" v-model.trim="username" autocomplete="username" required />
          <label for="password">密码</label>
          <el-input id="password" v-model="password" type="password" autocomplete="current-password" required />
          <el-button native-type="submit" type="primary" :loading="busy" :disabled="!username || !password">登录</el-button>
        </form>
      </section>
    </main>
    <main v-if="account" class="workspace">
      <div class="page-heading"><h1>问题处理</h1><p>模拟数据 · 未连接生产系统</p></div>
      <div v-if="error" ref="errorSummary" tabindex="-1" class="error-summary">
        <el-alert :title="error" type="error" :closable="false" show-icon />
        <button v-if="problem" type="button" @click="openProblem(problem.id)">刷新问题单</button>
      </div>
      <div class="workspace-grid">
        <aside class="sidebar" aria-label="问题与设备">
    <el-card header="问题单与回访">
      <form @submit.prevent="openProblem(orderId)">
        <label for="order-id">问题单号</label>
        <el-input id="order-id" v-model.trim="orderId" placeholder="例如 AS-12" />
        <el-button native-type="submit" :disabled="busy || !orderId">打开问题单</el-button>
      </form>
      <el-button class="section-button" :disabled="busy" @click="refreshDue('mine')">我的到期回访</el-button>
      <el-button class="section-button" :disabled="busy" @click="refreshDue('team')">团队到期回访</el-button>
      <el-button class="section-button" :disabled="busy" @click="refreshMine">我的待办</el-button>
      <p v-if="dueOrders && !dueOrders.length">当前没有到期回访</p>
      <div v-if="dueOrders" class="scroll-region" tabindex="0" role="region" aria-label="到期回访">
      <ul class="work-list">
        <li v-for="item in dueOrders" :key="item.problemId">
          <button type="button" :aria-current="problem?.id === item.problemId ? 'true' : undefined" @click="openProblem(item.problemId)">
            <span class="row-title">{{ item.title }}</span>
            <span class="row-meta">{{ item.owner }} · {{ item.dueAt }} · {{ item.nextStep }}</span>
          </button>
        </li>
      </ul>
      </div>
      <div v-if="mine" class="scroll-region" tabindex="0" role="region" aria-label="我的待办">
        <p v-if="!mine.length">当前没有负责中的问题单</p>
        <ul v-else class="work-list">
          <li v-for="item in mine" :key="item.id">
            <button type="button" :aria-current="problem?.id === item.id ? 'true' : undefined" @click="openProblem(item.id)">
              <span class="row-title">{{ orderNumber(item) }} · {{ item.title }}</span>
              <span class="row-meta">{{ item.device }} · {{ statusLabel(item.state) }}</span>
            </button>
          </li>
        </ul>
      </div>
    </el-card>
    <el-card header="设备查询">
      <form @submit.prevent="search">
        <label for="query">桩编号或手机号</label>
        <el-input id="query" v-model.trim="query" placeholder="SIM-AC-001 或 19900000001" />
        <el-button native-type="submit" type="primary" :disabled="busy || !query">查询</el-button>
      </form>
      <section v-if="device">
        <h2>{{ device.device }}</h2>
        <p>{{ device.fault }}</p>
        <p>{{ device.source }} · {{ device.observedAt }}</p>
        <ul v-if="deviceOrders.length" class="work-list">
          <li v-for="item in deviceOrders" :key="item.id">
            <button type="button" :aria-current="problem?.id === item.id ? 'true' : undefined" @click="openProblem(item.id)">
              <span class="row-title">{{ orderNumber(item) }} · {{ item.title }}</span>
              <span class="row-meta">{{ item.device }} · {{ statusLabel(item.state) }}</span>
            </button>
          </li>
        </ul>
      </section>
    </el-card>
    <el-card v-if="account?.role === 'admin'" header="账号">
      <form @submit.prevent="addAccount">
        <label for="new-username">新账号</label>
        <el-input id="new-username" v-model.trim="newUsername" maxlength="20" autocomplete="off" required />
        <label for="new-password">初始密码</label>
        <el-input id="new-password" v-model="newPassword" type="password" autocomplete="new-password" minlength="8" required />
        <label for="new-role">角色</label>
        <select id="new-role" v-model="newRole">
          <option value="operator">坐席</option>
          <option value="admin">管理员</option>
        </select>
        <el-button native-type="submit" :disabled="busy">创建账号</el-button>
      </form>
      <div class="scroll-region" tabindex="0" role="region" aria-label="账号列表">
        <ul class="work-list">
          <li v-for="item in accounts" :key="item.username">
            <span class="row-title">{{ item.username }} · {{ item.role === 'admin' ? '管理员' : '坐席' }} · {{ item.enabled ? '启用' : '禁用' }}</span>
            <button type="button" :disabled="busy" @click="toggleAccount(item)">{{ item.enabled ? '禁用' : '启用' }}</button>
          </li>
        </ul>
      </div>
    </el-card>
        </aside>
        <section class="details">
    <div v-if="!device && !problem" class="empty-panel">
      <h2>打开问题单或查询设备</h2>
      <p>可使用模拟桩号 SIM-AC-001 查看设备记录并受理问题。</p>
    </div>
    <el-card v-if="device && !problem" header="受理问题">
      <form @submit.prevent="create">
        <label for="title">问题标题</label>
        <el-input id="title" v-model.trim="title" maxlength="150" />
        <label for="description">现象描述</label>
        <el-input id="description" v-model.trim="description" type="textarea" maxlength="1000" />
        <el-button native-type="submit" type="primary" :disabled="busy || !title || !description">创建问题单</el-button>
      </form>
    </el-card>
    <el-card v-if="problem">
      <header class="issue-head">
        <p class="issue-kicker">问题单 <strong>{{ orderNumber(problem) }}</strong></p>
        <h2 class="issue-title">{{ problem.title }}</h2>
        <dl class="facts">
          <div><dt>桩编码</dt><dd>{{ problem.device }}</dd></div>
          <div><dt>状态</dt><dd><span class="status-chip" :data-state="problem.state">{{ statusLabel(problem.state) }}</span></dd></div>
          <div><dt>负责人</dt><dd>{{ ownerLabel(problem.owner) }}</dd></div>
          <div><dt>下一步</dt><dd>{{ nextAction }}</dd></div>
        </dl>
      </header>
      <p v-if="unowned(problem) && problem.state !== 'resolved'"><button type="button" :disabled="busy" @click="claim">认领问题单</button></p>
      <form v-if="canHandoff" @submit.prevent="handoff">
        <label for="handoff-target">转交给</label>
        <select id="handoff-target" v-model="handoffTarget" required>
          <option value="" disabled>请选择</option>
          <option v-for="item in assignees" :key="item.username" :value="item.username">{{ item.username }} · {{ item.role === 'admin' ? '管理员' : '坐席' }}</option>
        </select>
        <label for="handoff-reason">转交原因</label>
        <el-input id="handoff-reason" v-model.trim="handoffReason" maxlength="500" />
        <el-button native-type="submit" :disabled="busy || !handoffTarget || !handoffReason">转交</el-button>
      </form>
      <section v-if="problem.ownershipEvents?.length">
        <h2>认领与转交</h2>
        <div class="scroll-region" tabindex="0" role="region" aria-label="认领与转交">
          <ul class="work-list">
            <li v-for="(item, index) in problem.ownershipEvents" :key="index">
              {{ dateLabel(item.createdAt) }} · {{ item.actor }} · {{ item.action === 'claim' ? '认领' : '由 ' + (item.fromOwner === 'early' ? '无主' : item.fromOwner) + ' 交给 ' + item.toOwner }}<span v-if="item.reason"> · {{ item.reason }}</span>
            </li>
          </ul>
        </div>
      </section>
      <p class="issue-description">{{ problem.description }}</p>
      <h2>处理记录</h2>
      <ol class="timeline">
        <li v-for="(activity, index) in problem.activities" :key="index">
          <time>{{ dateLabel(activity.createdAt) }}</time>
          <div><span v-if="activity.actor">{{ activity.actor }} · </span>{{ activity.note }}</div>
        </li>
      </ol>
      <div v-if="problem.callbackResults?.length" class="callback-history">
        <h2>实际回访</h2>
        <article v-for="(item, index) in problem.callbackResults" :key="index">
          <time>{{ dateLabel(item.contactedAt) }} · {{ item.actor }}</time>
          <div>{{ item.note }}</div>
          <div v-if="item.evidence">{{ evidenceLabels[item.evidenceSource] }} · {{ item.evidence }}</div>
        </article>
      </div>
      <form v-if="problem.state !== 'resolved'" @submit.prevent="saveNote">
        <label for="note">跟进记录</label>
        <el-input id="note" v-model.trim="note" type="textarea" maxlength="1500" />
        <el-button native-type="submit" type="primary" :disabled="busy || !note">保存跟进</el-button>
      </form>
      <FieldOrders :key="problem.id + ':' + (problem.fieldOrders?.[0]?.state || '')"
        :problem="problem" :busy="busy" @link="saveFieldLink" @result="saveFieldResult" />
      <p v-if="problem.state === 'field'" class="stage-note">待现场派单后，回访先停住。尚未关联现场单时，可以填写原因撤回并返回排查；已关联且未结束的现场单必须先登记结果。</p>
      <form v-if="problem.state === 'field' && !fieldInProgress" @submit.prevent="withdrawDispatch">
        <label for="withdraw-reason">撤回原因</label>
        <el-input id="withdraw-reason" v-model.trim="withdrawReason" type="textarea" maxlength="500" />
        <el-button native-type="submit" :disabled="busy || !withdrawReason">撤回待派单，返回排查</el-button>
      </form>
      <section v-if="problem.state !== 'resolved' && problem.state !== 'field' && !fieldInProgress" class="callback">
        <h2>回访安排</h2>
        <p v-if="problem.state === 'verify' || problem.resumeState === 'verify'" class="stage-note">待验证问题可以安排回访。选择继续跟进后，问题仍回到待验证。</p>
        <p v-if="problem.pendingFollowup">
          {{ problem.pendingFollowup.overdue ? '已到期' : '待回访' }} ·
          {{ problem.pendingFollowup.dueAt }} · {{ problem.pendingFollowup.owner }} ·
          {{ problem.pendingFollowup.nextStep }}
        </p>
        <form @submit.prevent="saveSchedule">
          <label for="owner">负责人</label>
          <el-input id="owner" v-model.trim="owner" maxlength="20" />
          <label for="due">回访时间（北京时间）</label>
          <input id="due" v-model="dueAt" type="datetime-local" required />
          <label for="next">待确认事项</label>
          <el-input id="next" v-model.trim="nextStep" maxlength="500" />
          <el-button native-type="submit" :disabled="busy || !owner || !dueAt || !nextStep">
            {{ problem.pendingFollowup ? '更新安排' : '设回访' }}
          </el-button>
        </form>
        <el-button class="section-button" :disabled="busy" @click="beginCallback">现在回访</el-button>
        <form v-if="showCallback" class="callback" @submit.prevent="saveOutcome">
          <label for="contacted-at">实际联系时间（北京时间）</label>
          <input id="contacted-at" v-model="contactedAt" type="datetime-local" required />
          <label for="outcome">回访结果</label>
          <select id="outcome" v-model="outcome">
            <option value="continue">继续跟进</option>
            <option value="field">待现场派单</option>
            <option value="resolved">确认恢复并结案</option>
          </select>
          <label for="result">回访结果</label>
          <el-input id="result" v-model.trim="result" type="textarea" maxlength="1000" />
          <template v-if="outcome === 'resolved'">
            <label for="evidence-source">恢复核验来源</label>
            <select id="evidence-source" v-model="evidenceSource" required>
              <option value="" disabled>请选择</option>
              <option v-for="(label, value) in evidenceLabels" :key="value" :value="value">{{ label }}</option>
            </select>
            <label for="evidence">具体恢复依据</label>
            <el-input id="evidence" v-model.trim="evidence" maxlength="1000" placeholder="例如：车主确认已正常完成一次充电" />
          </template>
          <el-button native-type="submit" type="primary"
            :disabled="busy || !result || !contactedAt || (outcome === 'resolved' && (!evidenceSource || !evidence))">提交回访结果</el-button>
        </form>
      </section>
    </el-card>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import logoUrl from './assets/logo-soongo-teal.png'
import FieldOrders from './components/FieldOrders.vue'
import { currentAccount, login, logout, addFollowup, assignableAccounts, claimProblem, createAccount, createProblem, dueCallbacks, findProblems, finishCallback, handoffProblem, linkFieldOrder, listAccounts, loadProblem, lookupDevice, mineProblems, recordFieldResult, scheduleCallback, setAccountEnabled, withdrawField } from './api/workbench'

const query = ref('')
const title = ref('')
const description = ref('')
const note = ref('')
const device = ref(null)
const problem = ref(null)
const busy = ref(false)
const error = ref('')
const errorSummary = ref(null)
const orderId = ref('')
const deviceOrders = ref([])
const dueOrders = ref(null)
const owner = ref('')
const dueAt = ref('')
const nextStep = ref('')
const outcome = ref('continue')
const result = ref('')
const account = ref(null)
const authReady = ref(false)
const username = ref('')
const password = ref('')
const showCallback = ref(false)
const contactedAt = ref('')
const evidenceSource = ref('')
const evidence = ref('')
const evidenceLabels = { customer: '车主确认', platform: '平台充电记录', field: '现场复测' }
const accounts = ref([])
const assignees = ref([])
const mine = ref(null)
const withdrawReason = ref('')
const handoffTarget = ref('')
const handoffReason = ref('')
const newUsername = ref('')
const newPassword = ref('')
const newRole = ref('operator')

function orderNumber(item) {
  return item.displayNumber ? 'AS-' + item.displayNumber : item.id
}

function dateLabel(value) {
  return value?.replace('T', ' ').slice(0, 16) || ''
}

function beginCallback() {
  contactedAt.value = new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 16)
  showCallback.value = true
}

function clearAccount() {
  account.value = null
  problem.value = null
  device.value = null
  dueOrders.value = null
  deviceOrders.value = []
  query.value = ''
  orderId.value = ''
  title.value = ''
  description.value = ''
}

async function restoreOrder() {
  const id = new URLSearchParams(location.search).get('problem')
  if (id) await showProblem(id)
}

function signIn() {
  return run(async () => {
    try {
      account.value = await login(username.value, password.value)
      await loadAccounts()
      await restoreOrder()
    } finally {
      password.value = ''
    }
  })
}

function signOut() {
  return run(async () => {
    await logout()
    clearAccount()
  })
}

onMounted(async () => {
  window.addEventListener('workbench-session-expired', clearAccount)
  try {
    account.value = await currentAccount()
    await loadAccounts()
  } catch {
    return
  } finally {
    authReady.value = true
  }
  await run(restoreOrder)
})
onUnmounted(() => window.removeEventListener('workbench-session-expired', clearAccount))
const fieldInProgress = computed(() => problem.value?.fieldOrders?.some(item => ['linked', 'in_progress'].includes(item.state)))
const nextAction = computed(() => {
  const current = problem.value
  if (!current) return ''
  if (current.state === 'resolved') return '已结案，不再写入'
  if (current.state === 'field') return '关联或登记智享乐装现场单'
  if (current.pendingFollowup?.nextStep) return current.pendingFollowup.nextStep
  if (current.state === 'verify' || current.resumeState === 'verify') return '安排回访或提交恢复依据'
  return '记录跟进或安排回访'
})

function ownerLabel(owner) {
  return !owner || owner === 'early' ? '无主' : owner
}

function unowned(item) {
  return !item?.owner || item.owner === 'early'
}

const canHandoff = computed(() => problem.value && problem.value.state !== 'resolved' && !unowned(problem.value)
  && (account.value?.role === 'admin' || account.value?.username === problem.value.owner))

function saveFieldLink(number) {
  return run(async () => {
    await linkFieldOrder(problem.value.id, number, problem.value.version)
    await showProblem(problem.value.id)
  })
}

function saveFieldResult(form) {
  return run(async () => {
    await recordFieldResult(problem.value.id, { ...form, expectedVersion: problem.value.version })
    await showProblem(problem.value.id)
  })
}

function statusLabel(state) {
  return { open: '处理中', waiting: '待回访', field: '现场服务', verify: '待验证', triage: '待排查', resolved: '已结案' }[state] || state
}

async function showProblem(id) {
  problem.value = await loadProblem(id)
  orderId.value = orderNumber(problem.value)
  const task = problem.value.pendingFollowup
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
  history.replaceState(null, '', '?problem=' + encodeURIComponent(orderId.value))
  assignees.value = await assignableAccounts()
}

function openProblem(id) {
  return run(() => showProblem(id))
}

function refreshDue(scope) {
  return run(async () => {
    const page = await dueCallbacks(scope)
    dueOrders.value = page.items || []
  })
}

async function loadMine() {
  const page = await mineProblems()
  mine.value = page.items || []
}

function refreshMine() {
  return run(loadMine)
}

function withdrawDispatch() {
  return run(async () => {
    const id = problem.value.id
    await withdrawField(id, withdrawReason.value, problem.value.version)
    withdrawReason.value = ''
    await showProblem(id)
  })
}

function claim() {
  return run(async () => {
    const id = problem.value.id
    await claimProblem(id, problem.value.version)
    await showProblem(id)
    if (mine.value) await loadMine()
  })
}

function handoff() {
  return run(async () => {
    const id = problem.value.id
    await handoffProblem(id, { target: handoffTarget.value, reason: handoffReason.value, expectedVersion: problem.value.version })
    handoffReason.value = ''
    await showProblem(id)
    if (mine.value) await loadMine()
  })
}

async function loadAccounts() {
  if (account.value?.role !== 'admin') {
    accounts.value = []
    return
  }
  accounts.value = await listAccounts()
}

function addAccount() {
  return run(async () => {
    await createAccount({ username: newUsername.value, password: newPassword.value, role: newRole.value })
    newUsername.value = ''
    newPassword.value = ''
    await loadAccounts()
    assignees.value = await assignableAccounts()
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
    if (dueOrders.value) dueOrders.value = (await dueCallbacks('team')).items || []
  })
}

function saveOutcome() {
  return run(async () => {
    const id = problem.value.id
    await finishCallback(id, { outcome: outcome.value, note: result.value, contactedAt: contactedAt.value,
      evidenceSource: evidenceSource.value || null, evidence: evidence.value || null, expectedVersion: problem.value.version })
    await showProblem(id)
    if (dueOrders.value) dueOrders.value = (await dueCallbacks('team')).items || []
  })
}

async function run(action) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (failure) {
    error.value = failure.message
    await nextTick()
    if (errorSummary.value) errorSummary.value.focus()
  } finally {
    busy.value = false
  }
}

function search() {
  return run(async () => {
    device.value = null
    deviceOrders.value = []
    device.value = await lookupDevice(query.value)
    deviceOrders.value = await findProblems(device.value.device)
    problem.value = null
    orderId.value = ''
    history.replaceState(null, '', location.pathname)
    title.value = ''
    description.value = ''
    note.value = ''
  })
}

function create() {
  return run(async () => {
    const { id } = await createProblem({ device: device.value.device, title: title.value, description: description.value })
    problem.value = { id, title: title.value, description: description.value, activities: [] }
    await showProblem(id)
  })
}

function saveNote() {
  return run(async () => {
    const id = problem.value.id
    await addFollowup(id, note.value, problem.value.version)
    note.value = ''
    problem.value = await loadProblem(id)
  })
}
</script>

<style>
:root {
  color-scheme: light;
  --brand: #006072;
  --brand-deep: #0B5869;
  --canvas: #F4F8F9;
  --card: #fff;
  --ink: #1A2B30;
  --muted: #5B6E74;
  --line: #D5E3E6;
  --selected: #E8F2F4;
  --ok: #2E7D4F;
  --warn: #8F6A00;
  --danger: #C0362C;
  --el-color-primary: #006072;
  --el-color-primary-light-3: #4d909c;
  --el-color-primary-light-5: #80b0b9;
  --el-color-primary-light-7: #b3d0d5;
  --el-color-primary-light-8: #cce0e3;
  --el-color-primary-light-9: #E8F2F4;
  --el-color-primary-dark-2: #0B5869;
  --el-color-success: #2E7D4F;
  --el-color-warning: #8F6A00;
  --el-color-danger: #C0362C;
  --el-color-error: #C0362C;
  --el-text-color-primary: #1A2B30;
  --el-text-color-regular: #1A2B30;
  --el-text-color-secondary: #5B6E74;
  --el-border-color: #D5E3E6;
  --el-border-radius-base: 6px;
  --el-font-family: "Source Han Sans SC", "Noto Sans CJK SC", "PingFang SC", "Microsoft YaHei", "Heiti SC", sans-serif;
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--canvas); color: var(--ink); font-family: var(--el-font-family); }
h1, h2, p { margin-top: 0; }
h1 { margin-bottom: 8px; font-size: 22px; font-weight: 700; }
h2 { margin-bottom: 8px; font-size: 16px; }
button, input, select, textarea { font: inherit; }
:focus-visible { outline: 3px solid var(--brand); outline-offset: 2px; }
.topbar { position: sticky; top: 0; z-index: 2; min-height: 56px; padding: 8px 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px; background: var(--card); color: var(--ink); border-bottom: 1px solid var(--line); }
.brand, .account { display: flex; align-items: center; gap: 10px; min-width: 0; }
.brand-logo { display: block; height: 32px; width: auto; aspect-ratio: 715 / 254; }
.env-chip, .sim-note { color: var(--brand-deep); background: var(--selected); border: 1px solid var(--line); border-radius: 6px; font-size: 12px; }
.env-chip { padding: 4px 8px; }
.sim-note { display: inline-block; margin: 0 0 12px; padding: 4px 8px; }
.account button, .el-button { min-height: 44px; border-radius: 6px; }
.account button { background: var(--card); color: var(--brand); border: 1px solid var(--brand); padding: 0 12px; cursor: pointer; }
.el-button--primary { --el-button-bg-color: #006072; --el-button-border-color: #006072; --el-button-text-color: #fff; --el-button-hover-bg-color: #0B5869; --el-button-hover-border-color: #0B5869; --el-button-hover-text-color: #fff; }
.el-button:not(.el-button--primary) { --el-button-bg-color: #fff; --el-button-border-color: #006072; --el-button-text-color: #006072; --el-button-hover-bg-color: #E8F2F4; --el-button-hover-border-color: #0B5869; --el-button-hover-text-color: #006072; }
.workspace { max-width: 1180px; padding: 12px 16px 24px; margin: auto; }
.page-heading { display: flex; align-items: baseline; gap: 12px; margin-bottom: 8px; }
.page-heading h1 { margin: 0; }
.page-heading p, .stage-note, time, .row-meta { color: var(--muted); font-size: 13px; }
.page-heading p { margin: 0; }
.workspace-grid { display: grid; grid-template-columns: minmax(240px, 320px) minmax(0, 1fr); gap: 12px; align-items: start; }
.sidebar { min-width: 0; }
.el-card { --el-card-border-radius: 8px; --el-card-border-color: var(--line); --el-card-padding: 12px; margin-bottom: 12px; background: var(--card); }
.el-card__header { min-height: 44px; padding: 10px 12px; font-weight: 700; }
.el-card__body { padding: 12px; }
.el-alert { margin-bottom: 12px; }
.error-summary:focus { outline: 3px solid var(--brand); outline-offset: 2px; }
form { display: grid; gap: 8px; }
form label { font-size: 13px; font-weight: 600; }
form .el-button { justify-self: start; }
.el-input__wrapper { min-height: 44px; }
input[type=datetime-local], select { min-height: 44px; padding: 8px 10px; color: var(--ink); border: 1px solid var(--line); border-radius: 6px; background: var(--card); }
.callback { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--line); }
.section-button { margin-top: 8px; }
.issue-head { margin-bottom: 8px; }
.issue-kicker, .issue-title { margin: 0 0 8px; }
.issue-kicker strong, .facts dd { overflow-wrap: anywhere; }
.facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 0; }
.facts div { min-height: 44px; padding: 6px 8px; background: var(--selected); border-radius: 6px; }
.facts dt { color: var(--muted); font-size: 12px; }
.facts dd { margin: 2px 0 0; font-weight: 700; }
.status-chip { display: inline-block; border-radius: 6px; padding: 2px 8px; background: var(--selected); color: var(--brand-deep); font-size: 13px; font-weight: 700; }
.status-chip[data-state="resolved"] { background: #e7f3ec; color: var(--ok); }
.status-chip[data-state="waiting"], .status-chip[data-state="verify"] { background: #f8f1dc; color: var(--warn); }
.status-chip[data-state="triage"] { background: #f8e8e6; color: var(--danger); }
.issue-description, .empty-panel, .callback-history article { background: var(--card); padding: 12px; border: 1px solid var(--line); border-radius: 8px; }
.issue-description { margin-bottom: 12px; }
.empty-panel p { margin: 0; color: var(--muted); }
.scroll-region { max-inline-size: 100%; max-block-size: 320px; overflow: auto; overscroll-behavior: contain; }
.work-list { list-style: none; margin: 8px 0 0; padding: 0; }
.work-list li { border-bottom: 1px solid var(--line); }
.work-list button, .work-list li { min-height: 48px; }
.work-list button { width: 100%; padding: 6px 8px; display: flex; flex-direction: column; align-items: flex-start; justify-content: center; background: var(--card); color: var(--ink); border: 0; border-inline-start: 3px solid transparent; text-align: start; cursor: pointer; }
.work-list button[aria-current="true"] { background: var(--selected); border-inline-start-color: var(--brand); }
.row-title { font-weight: 650; }
.timeline { list-style: none; margin: 0 0 12px; padding: 0; }
.timeline li { min-height: 48px; padding: 8px 0 8px 14px; border-inline-start: 3px solid var(--line); }
.timeline li div { overflow-wrap: anywhere; }
.login-layout { max-width: 420px; margin: 32px auto; padding: 0 16px; }
.login-panel { padding: 20px; background: var(--card); border: 1px solid var(--line); border-radius: 8px; }
.login-panel .brand-logo { margin-bottom: 12px; }
h1, h2 { text-wrap: balance; }
p, li { text-wrap: pretty; }
img { max-inline-size: 100%; block-size: auto; }
body { overflow-wrap: break-word; }
html { scroll-padding-top: calc(4rem + env(safe-area-inset-top, 0px)); }
.topbar { padding-top: max(8px, env(safe-area-inset-top, 0px)); padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); }
.workspace { container: workspace / inline-size; }
@container workspace (inline-size < 720px) {
  .workspace-grid, .facts { grid-template-columns: 1fr; }
}
@media (max-width: 760px), (pointer: coarse) {
  .workspace-grid, .facts { grid-template-columns: 1fr; }
  .topbar, .brand, .account { align-items: flex-start; flex-wrap: wrap; }
  :where(button, select, .el-button) { min-block-size: 44px; min-inline-size: 44px; }
}
</style>
