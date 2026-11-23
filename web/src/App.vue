<template>
  <main>
    <h1>售后工作台</h1>
    <p>模拟数据 · 试用环境</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
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
      <p>{{ problem.description }}</p>
      <ul><li v-for="(activity, index) in problem.activities" :key="index">{{ activity.note }}</li></ul>
      <form @submit.prevent="saveNote">
        <label for="note">跟进记录</label>
        <el-input id="note" v-model.trim="note" type="textarea" maxlength="1500" />
        <el-button native-type="submit" type="primary" :disabled="busy || !note">保存跟进</el-button>
      </form>
    </el-card>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { addFollowup, createProblem, loadProblem, lookupDevice } from './api/workbench'

const query = ref('')
const title = ref('')
const description = ref('')
const note = ref('')
const device = ref(null)
const problem = ref(null)
const busy = ref(false)
const error = ref('')

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
    device.value = await lookupDevice(query.value)
    problem.value = null
    title.value = ''
    description.value = ''
    note.value = ''
  })
}

function create() {
  return run(async () => {
    const { id } = await createProblem({ device: device.value.device, title: title.value, description: description.value })
    problem.value = { id, title: title.value, description: description.value, activities: [] }
    problem.value = await loadProblem(id)
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
body { margin: 0; background: #f2f5fa; color: #19283b; font-family: system-ui, sans-serif; }
main { max-width: 780px; margin: auto; padding: 28px 18px; }
.el-card, .el-alert { margin: 16px 0; }
form { display: grid; gap: 10px; }
form .el-button { justify-self: start; }
h2 { font-size: 17px; }
</style>
