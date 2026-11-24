<template>
  <section v-if="(editable && problem.state === 'field') || problem.fieldOrders?.length" class="callback">
    <h2>智享乐装现场单</h2>
    <ul class="work-list">
      <li v-for="item in problem.fieldOrders" :key="item.externalNo">
        {{ item.externalNo }} · {{ labels[item.state] }}<span v-if="item.receipt"> · {{ item.receipt }}</span>
      </li>
    </ul>
    <form v-if="editable && problem.state === 'field' && !active" @submit.prevent="$emit('link', number.trim())">
      <label for="field-number">已在智享乐装创建的工单号</label>
      <el-input id="field-number" :model-value="number" maxlength="64" @update:model-value="updateNumber" />
      <el-button native-type="submit" :disabled="busy || !number">关联现场单</el-button>
    </form>
    <form v-if="editable && problem.state === 'field' && active" @submit.prevent="submitResult">
      <label for="field-state">现场单状态（人工登记）</label>
      <select id="field-state" :value="state" @change="$emit('update:state', $event.target.value)">
        <option value="in_progress" :disabled="active.state === 'in_progress'">处理中</option>
        <option value="complete">完工</option>
        <option value="rejected">拒单</option>
        <option value="cancelled">取消</option>
      </select>
      <label for="field-receipt">现场回执或原因</label>
      <el-input id="field-receipt" :model-value="receipt" type="textarea" maxlength="700" @update:model-value="updateReceipt" />
      <el-button native-type="submit" :disabled="busy || !receipt || state === active.state">保存回执</el-button>
    </form>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  problem: { type: Object, required: true },
  busy: Boolean,
  number: { type: String, default: '' },
  receipt: { type: String, default: '' },
  state: { type: String, default: 'complete' },
  editable: { type: Boolean, default: true }
})
const emit = defineEmits(['link', 'result', 'update:number', 'update:receipt', 'update:state'])
const labels = { linked: '已关联', in_progress: '处理中', complete: '完工', rejected: '拒单', cancelled: '取消' }
const active = computed(() => props.problem.fieldOrders?.find(item => ['linked', 'in_progress'].includes(item.state)))

function updateNumber(value) {
  emit('update:number', String(value || ''))
}

function updateReceipt(value) {
  emit('update:receipt', String(value || ''))
}

function submitResult() {
  emit('result', { externalNo: active.value.externalNo, state: props.state, receipt: props.receipt.trim() })
}
</script>
