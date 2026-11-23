<template>
  <main>
    <h1>售后工作台</h1>
    <p>模拟数据 · 试用环境</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card header="问题单与回访待办">
      <form @submit.prevent="openProblem(orderId)">
        <label for="order-id">问题单号</label>
        <el-input id="order-id" v-model.trim="orderId" />
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
      <p>问题单 {{ problem.id }}</p>
      <p>{{ problem.device }} · {{ statusLabel(problem.state) }}</p>
      <p>{{ problem.description }}</p>
      <ul><li v-for="(activity, index) in problem.activities" :key="index">{{ activity.note }}</li></ul>
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
        <form v-if="problem.pendingFollowup" class="callback" @submit.prevent="saveOutcome">
          <label for="outcome">回访结果</label>
          <select id="outcome" v-model="outcome">
            <option value="continue">继续跟进</option>
            <option value="field">待现场派单</option>
            <option value="resolved">确认恢复并结案</option>
          </select>
          <label for="result">回访结果与验证依据</label>
          <el-input id="result" v-model.trim="result" type="textarea" maxlength="1000" />
          <el-button native-type="submit" type="primary" :disabled="busy || !result">提交回访结果</el-button>
        </form>
      </section>
    </el-card>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import FieldOrders from './components/FieldOrders.vue'
import { addFollowup, createProblem, dueCallbacks, findProblems, finishCallback, linkFieldOrder, loadProblem, lookupDevice, recordFieldResult, scheduleCallback } from './api/workbench'

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
  orderId.value = id
  const task = problem.value.pendingFollowup
  owner.value = task?.owner || ''
  dueAt.value = task?.dueAt?.replace(' ', 'T').slice(0, 16) || ''
  nextStep.value = task?.nextStep || ''
  note.value = ''
  outcome.value = 'continue'
  result.value = ''
  history.replaceState(null, '', '?problem=' + encodeURIComponent(id))
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
    await finishCallback(id, { outcome: outcome.value, note: result.value })
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
const linkedOrder = new URLSearchParams(location.search).get('problem')
if (linkedOrder) openProblem(linkedOrder)
</script>

<style>
body { margin: 0; background: #f2f5fa; color: #19283b; font-family: system-ui, sans-serif; }
main { max-width: 780px; margin: auto; padding: 28px 18px; }
.el-card, .el-alert { margin: 16px 0; }
form { display: grid; gap: 10px; }
form .el-button { justify-self: start; }
h2 { font-size: 17px; }
.callback { margin-top: 20px; }
.section-button { margin-top: 12px; }
input[type=datetime-local], select { padding: 8px; font: inherit; }
</style>
