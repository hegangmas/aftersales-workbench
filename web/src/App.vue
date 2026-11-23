<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand"><span class="brand-mark">S</span><strong>售后工作台</strong><span class="env-chip">试用环境</span></div>
      <div v-if="account" class="account"><span>{{ account.username }}</span><button type="button" @click="signOut">退出</button></div>
    </header>
    <main v-if="authReady && !account" class="login-layout">
      <section class="login-cover"><span class="cover-mark">S</span><h1>售后工作台</h1><p>设备查询、问题受理与回访</p></section>
      <section class="login-panel">
        <h2>账号登录</h2>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
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
      <div class="page-heading"><h1>问题处理</h1><span>模拟数据</span></div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div class="workspace-grid">
        <aside class="sidebar">
    <el-card header="问题单与回访待办">
      <form @submit.prevent="openProblem(orderId)">
        <label for="order-id">问题单号</label>
        <el-input id="order-id" v-model.trim="orderId" placeholder="例如 AS-12" />
        <el-button native-type="submit" :disabled="busy || !orderId">打开问题单</el-button>
      </form>
      <el-button class="section-button" :disabled="busy" @click="refreshDue">查看到期回访</el-button>
      <p v-if="dueOrders && !dueOrders.length">当前没有到期回访</p>
      <ul v-if="dueOrders">
        <li v-for="item in dueOrders" :key="item.problemId">
          <button type="button" @click="openProblem(item.problemId)">{{ item.title }}</button>
          {{ item.owner }} · {{ item.dueAt }} · {{ item.nextStep }}
        </li>
      </ul>
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
        <ul v-if="deviceOrders.length">
          <li v-for="item in deviceOrders" :key="item.id">
            <button type="button" @click="openProblem(item.id)">{{ item.title }}</button>
            {{ statusLabel(item.state) }}
          </li>
        </ul>
      </section>
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
    <el-card v-if="problem" :header="problem.title">
      <div class="issue-meta"><span class="status-chip">{{ statusLabel(problem.state) }}</span><span>{{ problem.device }}</span></div>
      <p class="issue-id">问题单 {{ orderNumber(problem) }}</p>
      <p class="issue-description">{{ problem.description }}</p>
      <h2>处理记录</h2>
      <ol class="timeline">
        <li v-for="(activity, index) in problem.activities" :key="index">
          <time>{{ dateLabel(activity.createdAt) }}</time><div>{{ activity.note }}</div>
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
      <section v-if="problem.state !== 'resolved' && !fieldInProgress" class="callback">
        <h2>回访安排</h2>
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
import { computed, onMounted, onUnmounted, ref } from 'vue'
import FieldOrders from './components/FieldOrders.vue'
import { currentAccount, login, logout, addFollowup, createProblem, dueCallbacks, findProblems, finishCallback, linkFieldOrder, loadProblem, lookupDevice, recordFieldResult, scheduleCallback } from './api/workbench'

const query = ref('')
const title = ref('')
const description = ref('')
const note = ref('')
const device = ref(null)
const problem = ref(null)
const busy = ref(false)
const error = ref('')
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
  } catch {
    return
  } finally {
    authReady.value = true
  }
  await run(restoreOrder)
})
onUnmounted(() => window.removeEventListener('workbench-session-expired', clearAccount))
const fieldInProgress = computed(() => problem.value?.fieldOrders?.some(item => ['linked', 'in_progress'].includes(item.state)))

function saveFieldLink(number) {
  return run(async () => {
    await linkFieldOrder(problem.value.id, number)
    await showProblem(problem.value.id)
  })
}

function saveFieldResult(form) {
  return run(async () => {
    await recordFieldResult(problem.value.id, form)
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
}

function openProblem(id) {
  return run(() => showProblem(id))
}

function refreshDue() {
  return run(async () => { dueOrders.value = await dueCallbacks() })
}

function saveSchedule() {
  return run(async () => {
    const id = problem.value.id
    await scheduleCallback(id, { owner: owner.value, nextStep: nextStep.value, dueAt: dueAt.value })
    await showProblem(id)
    if (dueOrders.value) dueOrders.value = await dueCallbacks()
  })
}

function saveOutcome() {
  return run(async () => {
    const id = problem.value.id
    await finishCallback(id, { outcome: outcome.value, note: result.value, contactedAt: contactedAt.value,
      evidenceSource: evidenceSource.value || null, evidence: evidence.value || null })
    await showProblem(id)
    if (dueOrders.value) dueOrders.value = await dueCallbacks()
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
    await addFollowup(id, note.value)
    note.value = ''
    problem.value = await loadProblem(id)
  })
}
</script>

<style>
:root { color-scheme: light; --ink: #172d42; --muted: #566b7d; --surface: #fff; --accent: #087e79; --line: #dbe6eb; --el-color-primary: #087e79; }
* { box-sizing: border-box; }
body { margin: 0; background: #edf3f5; color: var(--ink); font-family: system-ui, "PingFang SC", "Microsoft YaHei", sans-serif; }
h1, h2, p { margin-top: 0; }
h1 { letter-spacing: -.035em; }
h2 { font-size: 16px; }
.topbar { height: 68px; padding: 0 clamp(16px, 4vw, 52px); display: flex; align-items: center; justify-content: space-between; background: #122b3c; color: white; }
.brand, .account { display: flex; align-items: center; gap: 12px; }
.brand-mark { width: 32px; height: 32px; display: grid; place-items: center; background: #48d7b8; color: #102d3f; border-radius: 9px; font-weight: 850; }
.env-chip { color: #bdede2; border: 1px solid #41616e; border-radius: 100px; font-size: 12px; padding: 3px 8px; }
.account button { background: transparent; color: white; border: 1px solid #6a828e; padding: 5px 11px; border-radius: 8px; cursor: pointer; }
.workspace { max-width: 1360px; padding: 28px clamp(16px, 4vw, 52px); margin: auto; }
.page-heading { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; }
.page-heading h1 { margin: 0; font-size: 29px; }
.page-heading span, .issue-id, time { color: var(--muted); font-size: 13px; }
.workspace-grid { display: grid; grid-template-columns: minmax(260px, .8fr) minmax(0, 2fr); gap: 22px; align-items: start; }
.el-card { --el-card-border-radius: 16px; --el-card-border-color: var(--line); margin-bottom: 16px; }
.el-card__header { font-weight: 700; }
.el-alert { margin-bottom: 16px; }
form { display: grid; gap: 9px; }
form label { margin-top: 2px; font-size: 13px; font-weight: 600; }
form .el-button { justify-self: start; }
.callback { margin-top: 22px; padding-top: 17px; border-top: 1px solid var(--line); }
.section-button { margin-top: 12px; }
input[type=datetime-local], select { min-height: 36px; padding: 6px 9px; color: var(--ink); border: 1px solid #ccdbe2; border-radius: 7px; background: white; font: inherit; }
.issue-meta { display: flex; align-items: center; gap: 10px; margin-bottom: 9px; }
.status-chip { border-radius: 100px; padding: 4px 10px; color: #05615a; background: #d8f4ee; font-size: 13px; font-weight: 700; }
.issue-id { overflow-wrap: anywhere; }
.issue-description { padding: 14px; background: #f2f7f8; border-radius: 9px; }
.timeline { list-style: none; margin: 0 0 22px 6px; padding: 0 0 0 17px; border-left: 2px solid #cae6df; }
.timeline li { position: relative; margin: 0 0 12px; overflow-wrap: anywhere; }
.timeline li::before { content: ""; position: absolute; left: -23px; top: 6px; width: 9px; height: 9px; border-radius: 100%; background: var(--accent); }
.login-layout { max-width: 910px; margin: clamp(28px, 10vh, 100px) auto; display: grid; grid-template-columns: 1fr 1fr; border: 1px solid var(--line); border-radius: 22px; overflow: hidden; background: white; }
.login-cover { min-height: 355px; padding: 48px; color: white; background: radial-gradient(at 95% 90%, #087e79 0, #142d41 55%); display: flex; flex-direction: column; justify-content: center; }
.cover-mark { font-size: 42px; font-weight: 850; color: #63e0c3; }
.login-cover h1 { margin-bottom: 9px; font-size: 34px; }
.login-cover p { margin: 0; color: #b9deda; }
.login-panel { align-self: center; padding: 44px; }
.login-panel h2 { font-size: 25px; }
.empty-panel, .callback-history article { background: white; padding: 20px; border: 1px solid var(--line); border-radius: 12px; margin-bottom: 12px; }
.empty-panel p { margin: 0; color: var(--muted); }
@media (max-width: 760px) {
  .workspace-grid, .login-layout { grid-template-columns: 1fr; }
  .login-layout { margin: 22px 16px; }
  .login-cover { min-height: 180px; padding: 25px; }
  .login-panel { padding: 25px; }
  .topbar { height: 60px; }
}
</style>
