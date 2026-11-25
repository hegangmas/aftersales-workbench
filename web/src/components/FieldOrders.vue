<template>
  <section v-if="showSection" class="action-block">
    <form v-if="editable && problem.state === 'field' && !active" id="action-field" @submit.prevent="$emit('link', number.trim())">
      <label class="field-label" for="field-number">已在智享乐装创建的工单号</label>
      <input id="field-number" class="text-input" :value="number" maxlength="64" @input="$emit('update:number', $event.target.value)">
      <button class="btn primary" type="submit" :disabled="busy || !number.trim()">关联现场单</button>
    </form>
    <form v-if="editable && problem.state === 'field' && active" id="action-receipt" @submit.prevent="submitResult">
      <label class="field-label" for="field-state">现场单状态（人工登记）</label>
      <select id="field-state" class="text-input" :value="state" @change="$emit('update:state', $event.target.value)">
        <option value="in_progress" :disabled="active.state === 'in_progress'">处理中</option>
        <option value="complete">完工</option>
        <option value="rejected">拒单</option>
        <option value="cancelled">取消</option>
      </select>
      <label class="field-label" for="field-receipt">现场回执或原因</label>
      <textarea id="field-receipt" class="text-area" :value="receipt" maxlength="700" @input="$emit('update:receipt', $event.target.value)"></textarea>
      <button class="btn primary" type="submit" :disabled="busy || !receipt.trim() || state === active.state">保存回执</button>
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
const active = computed(() => props.problem.fieldOrders?.find(item => ['linked', 'in_progress'].includes(item.state)))
const showSection = computed(() => props.editable && props.problem.state === 'field')

function submitResult() {
  emit('result', { externalNo: active.value.externalNo, state: props.state, receipt: props.receipt.trim() })
}
</script>
