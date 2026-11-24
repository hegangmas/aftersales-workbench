<template>
  <article>
    <div class="crumb">
      <button type="button" @click="$emit('back')">返回列表</button>
      <span aria-hidden="true">/</span>
      <span>问题单 {{ number }}</span>
    </div>
    <header class="panel">
      <p class="lede">问题单 {{ number }}</p>
      <h1 class="issue-title">{{ problem.title }}</h1>
      <p v-if="refreshNote" role="status" class="stage-note">{{ refreshNote }}</p>
      <dl class="facts">
        <div><dt>桩编码</dt><dd>{{ problem.device || '无' }}</dd></div>
        <div><dt>状态</dt><dd><span class="status" :class="status.tone"><i></i>{{ status.label }}</span></dd></div>
        <div><dt>负责人</dt><dd>{{ ownerText }}</dd></div>
        <div><dt>下一步</dt><dd>{{ nextAction }}</dd></div>
      </dl>
    </header>
    <div class="detail-grid">
      <div>
        <section class="section-card">
          <h2>问题与设备</h2>
          <p>{{ problem.description }}</p>
          <p class="muted">{{ kindText }}<span v-if="problem.category"> · {{ problem.category }}</span></p>
          <p class="muted">桩编码 {{ problem.device || '无' }}</p>
        </section>
        <section class="section-card">
          <h2>认领与转交</h2>
          <p v-if="!problem.ownershipEvents?.length" class="muted">还没有认领或转交记录</p>
          <ul v-else class="record-list">
            <li v-for="(item, index) in problem.ownershipEvents" :key="index">
              <time>{{ dateLabel(item.createdAt) }} · {{ item.actor }}</time>
              {{ item.action === 'claim' ? '认领' : '由 ' + ownerName(item.fromOwner) + ' 交给 ' + item.toOwner }}<span v-if="item.reason"> · {{ item.reason }}</span>
            </li>
          </ul>
        </section>
        <section class="section-card">
          <h2>处理时间线</h2>
          <ol class="timeline">
            <li v-for="(activity, index) in problem.activities" :key="index">
              <time>{{ dateLabel(activity.createdAt) }}</time>
              <div><span v-if="activity.actor">{{ activity.actor }} · </span>{{ activity.note }}</div>
            </li>
          </ol>
        </section>
        <section v-if="problem.callbackResults?.length" class="section-card">
          <h2>实际回访</h2>
          <article v-for="(item, index) in problem.callbackResults" :key="index">
            <time>{{ dateLabel(item.contactedAt) }} · {{ item.actor }}</time>
            <div>{{ item.note }}</div>
            <div v-if="item.evidence">{{ evidenceLabels[item.evidenceSource] }} · {{ item.evidence }}</div>
          </article>
        </section>
        <section class="section-card">
          <h2>已关联现场单与回执</h2>
          <p v-if="!problem.fieldOrders?.length" class="muted">尚未关联现场单</p>
          <ul v-else class="record-list">
            <li v-for="item in problem.fieldOrders" :key="item.externalNo">
              {{ item.externalNo }} · {{ fieldLabels[item.state] || item.state }}<span v-if="item.receipt"> · {{ item.receipt }}</span>
            </li>
          </ul>
        </section>
      </div>
      <aside class="panel">
        <h2>下一步操作</h2>
        <p v-if="handlingDenied" role="status" class="permission-note">{{ handlingDenied }}</p>
        <p v-if="unowned && problem.state !== 'resolved'"><button class="btn primary" type="button" :disabled="busy" @click="$emit('claim')">认领问题单</button></p>
        <form v-if="canProcess" id="action-note" class="stack-form" @submit.prevent="$emit('save-note')">
          <label class="field-label" for="note">跟进记录</label>
          <textarea id="note" class="text-area" :value="note" maxlength="1500" @input="$emit('update:note', $event.target.value)"></textarea>
          <button class="btn primary" type="submit" :disabled="busy || !note.trim()">保存跟进</button>
        </form>
        <FieldOrders forms-only :editable="canProcess" :problem="problem" :busy="busy" :number="fieldNumber" :receipt="fieldReceipt" :state="fieldState"
          @update:number="$emit('update:fieldNumber', $event)" @update:receipt="$emit('update:fieldReceipt', $event)" @update:state="$emit('update:fieldState', $event)"
          @link="$emit('link', $event)" @result="$emit('field-result', $event)" />
        <p v-if="canProcess && problem.state === 'field'" class="stage-note">待现场派单后，回访先停住。尚未关联现场单时，可以填写原因撤回并返回排查；已关联且未结束的现场单必须先登记结果。</p>
        <form v-if="canProcess && problem.state === 'field' && !fieldInProgress" class="stack-form" @submit.prevent="$emit('withdraw')">
          <label class="field-label" for="withdraw-reason">撤回原因</label>
          <textarea id="withdraw-reason" class="text-area" :value="withdrawReason" maxlength="500" @input="$emit('update:withdrawReason', $event.target.value)"></textarea>
          <button class="btn secondary" type="submit" :disabled="busy || !withdrawReason.trim()">撤回待派单，返回排查</button>
        </form>
        <section v-if="canProcess && problem.state !== 'field' && !fieldInProgress" id="action-callback" class="action-block">
          <h3>回访安排</h3>
          <p v-if="problem.state === 'verify' || problem.resumeState === 'verify'" class="stage-note">待验证问题可以安排回访。选择继续跟进后，问题仍回到待验证。</p>
          <p v-if="problem.pendingFollowup" class="stage-note">
            {{ problem.pendingFollowup.overdue ? '已到期' : '待回访' }} ·
            {{ problem.pendingFollowup.dueAt }} · {{ problem.pendingFollowup.owner }} ·
            {{ problem.pendingFollowup.nextStep }}
          </p>
          <form class="stack-form" @submit.prevent="$emit('save-schedule')">
            <label class="field-label" for="owner">负责人</label>
            <input id="owner" class="text-input" :value="owner" maxlength="20" @input="$emit('update:owner', $event.target.value)">
            <label class="field-label" for="due">回访时间（北京时间）</label>
            <input id="due" class="text-input" :value="dueAt" type="datetime-local" required @input="$emit('update:dueAt', $event.target.value)">
            <label class="field-label" for="next">待确认事项</label>
            <input id="next" class="text-input" :value="nextStep" maxlength="500" @input="$emit('update:nextStep', $event.target.value)">
            <button class="btn secondary" type="submit" :disabled="busy || !owner.trim() || !dueAt || !nextStep.trim()">{{ problem.pendingFollowup ? '更新安排' : '设回访' }}</button>
          </form>
          <button class="btn secondary" type="button" :disabled="busy" @click="$emit('begin-callback')">现在回访</button>
          <form v-if="showCallback" class="stack-form" @submit.prevent="$emit('save-outcome')">
            <label class="field-label" for="contacted-at">实际联系时间（北京时间）</label>
            <input id="contacted-at" class="text-input" :value="contactedAt" type="datetime-local" required @input="$emit('update:contactedAt', $event.target.value)">
            <label class="field-label" for="outcome">回访结果</label>
            <select id="outcome" class="text-input" :value="outcome" @change="$emit('update:outcome', $event.target.value)">
              <option value="continue">继续跟进</option>
              <option value="field">待现场派单</option>
              <option value="resolved">确认恢复并结案</option>
            </select>
            <label class="field-label" for="result">回访说明</label>
            <textarea id="result" class="text-area" :value="result" maxlength="1000" @input="$emit('update:result', $event.target.value)"></textarea>
            <template v-if="outcome === 'resolved'">
              <label class="field-label" for="evidence-source">恢复核验来源</label>
              <select id="evidence-source" class="text-input" :value="evidenceSource" required @change="$emit('update:evidenceSource', $event.target.value)">
                <option value="" disabled>请选择</option>
                <option v-for="(label, value) in evidenceLabels" :key="value" :value="value">{{ label }}</option>
              </select>
              <label class="field-label" for="evidence">具体恢复依据</label>
              <input id="evidence" class="text-input" :value="evidence" maxlength="1000" placeholder="例如：车主确认已正常完成一次充电" @input="$emit('update:evidence', $event.target.value)">
            </template>
            <button class="btn primary" type="submit" :disabled="busy || !result.trim() || !contactedAt || (outcome === 'resolved' && (!evidenceSource || !evidence.trim()))">提交回访结果</button>
          </form>
        </section>
        <form v-if="canHandoff" class="stack-form action-block" @submit.prevent="$emit('handoff')">
          <h3>转交</h3>
          <label class="field-label" for="handoff-target">转交给</label>
          <select id="handoff-target" class="text-input" :value="handoffTarget" required @change="$emit('update:handoffTarget', $event.target.value)">
            <option value="" disabled>请选择</option>
            <option v-for="item in assignees" :key="item.username" :value="item.username">{{ item.username }} · {{ item.role === 'admin' ? '管理员' : '坐席' }}</option>
          </select>
          <label class="field-label" for="handoff-reason">转交原因</label>
          <textarea id="handoff-reason" class="text-area" :value="handoffReason" maxlength="500" @input="$emit('update:handoffReason', $event.target.value)"></textarea>
          <button class="btn secondary" type="submit" :disabled="busy || !handoffTarget || !handoffReason.trim()">转交</button>
        </form>
      </aside>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import FieldOrders from './FieldOrders.vue'
import { displayStatus, isUnowned, kindLabel, orderNumber } from '../status'

const props = defineProps({
  problem: { type: Object, required: true },
  busy: Boolean,
  canProcess: Boolean,
  canHandoff: Boolean,
  handlingDenied: { type: String, default: '' },
  nextAction: { type: String, default: '' },
  refreshNote: { type: String, default: '' },
  note: { type: String, default: '' },
  owner: { type: String, default: '' },
  dueAt: { type: String, default: '' },
  nextStep: { type: String, default: '' },
  outcome: { type: String, default: 'continue' },
  result: { type: String, default: '' },
  showCallback: Boolean,
  contactedAt: { type: String, default: '' },
  evidenceSource: { type: String, default: '' },
  evidence: { type: String, default: '' },
  withdrawReason: { type: String, default: '' },
  handoffTarget: { type: String, default: '' },
  handoffReason: { type: String, default: '' },
  fieldNumber: { type: String, default: '' },
  fieldReceipt: { type: String, default: '' },
  fieldState: { type: String, default: 'complete' },
  assignees: { type: Array, default: () => [] },
  evidenceLabels: { type: Object, required: true },
  fieldInProgress: Boolean
})
defineEmits([
  'back', 'claim', 'save-note', 'save-schedule', 'begin-callback', 'save-outcome', 'handoff', 'withdraw', 'link', 'field-result',
  'update:note', 'update:owner', 'update:dueAt', 'update:nextStep', 'update:outcome', 'update:result', 'update:contactedAt',
  'update:evidenceSource', 'update:evidence', 'update:withdrawReason', 'update:handoffTarget', 'update:handoffReason',
  'update:fieldNumber', 'update:fieldReceipt', 'update:fieldState'
])
const fieldLabels = { linked: '已关联', in_progress: '处理中', complete: '完工', rejected: '拒单', cancelled: '取消' }
const number = computed(() => orderNumber(props.problem))
const status = computed(() => displayStatus(props.problem))
const unowned = computed(() => isUnowned(props.problem))
const ownerText = computed(() => unowned.value ? '无主' : props.problem.owner)
const kindText = computed(() => kindLabel(props.problem.kind) || '问题')

function dateLabel(value) {
  return String(value || '').replace('T', ' ').replace(/\.\d+/, '').slice(0, 16)
}
function ownerName(value) {
  return !value || value === 'early' ? '无主' : value
}
</script>
