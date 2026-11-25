<template>
  <section class="sop-evidence-panel">
    <div v-if="problem.sop" class="personal-box">
      <strong>{{ problem.sop.title || '标准排查 SOP' }}</strong>
      <p v-if="problem.sop.evidenceMode" class="muted">证据要求：{{ problem.sop.evidenceMode }}</p>
      <p v-if="recommendedLabels.length" class="stage-note">建议证据类型：{{ recommendedLabels.join('、') }}</p>

      <div v-if="customerSteps.length" class="sop-group">
        <div class="field-label">客户排查步骤</div>
        <ul class="sop-checklist">
          <li v-for="(step, idx) in customerSteps" :key="'c-' + idx">
            <label class="sop-check">
              <input v-model="checkedCustomer" type="checkbox" :value="idx">
              <span>{{ idx + 1 }}. {{ step }}</span>
            </label>
          </li>
        </ul>
      </div>

      <div v-if="onsiteSteps.length" class="sop-group">
        <div class="field-label">上门验证步骤</div>
        <ul class="sop-checklist">
          <li v-for="(step, idx) in onsiteSteps" :key="'o-' + idx">
            <label class="sop-check">
              <input v-model="checkedOnsite" type="checkbox" :value="idx">
              <span>{{ idx + 1 }}. {{ step }}</span>
            </label>
          </li>
        </ul>
      </div>
    </div>

    <div class="personal-box sop-section">
      <strong>已登记证据</strong>
      <ul v-if="problem.evidences?.length" class="record-list sop-list">
        <li v-for="(item, idx) in problem.evidences" :key="item.id || idx">
          <time>
            {{ dateLabel(item.createdAt) }}
            <span v-if="item.actor"> · {{ item.actor }}</span>
            <span v-if="item.source"> · {{ sourceLabel(item.source) }}</span>
          </time>
          <div>
            <strong>{{ evidenceTypeLabel(item.evidenceType) || item.evidenceType }}</strong>
            <span v-if="item.fileName"> · {{ item.fileName }}</span>
          </div>
          <div v-if="item.note">{{ item.note }}</div>
        </li>
      </ul>
      <p v-else class="muted">暂无已登记证据</p>

      <form v-if="canProcess" class="stack-form sop-form" @submit.prevent="submitEvidence">
        <label class="field-label" for="sop-evidence-type">证据类型</label>
        <select id="sop-evidence-type" v-model="evidenceType" class="text-input" required>
          <option v-for="item in evidenceTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>

        <label class="field-label" for="sop-evidence-source">证据来源</label>
        <select id="sop-evidence-source" v-model="source" class="text-input" required>
          <option v-for="item in sourceOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>

        <label class="field-label" for="sop-evidence-file">本地文件（可选，当前为模拟元数据登记）</label>
        <input id="sop-evidence-file" ref="filePicker" class="text-input" type="file" @change="onFilePick">
        <p class="muted sop-hint">仅读取文件名填入下方字段，当前为模拟元数据登记，不上传文件正文</p>

        <label class="field-label" for="sop-evidence-filename">文件名 / 标识</label>
        <input id="sop-evidence-filename" v-model="fileName" class="text-input" maxlength="200" placeholder="可手动输入或通过上方文件选择自动读取">

        <label class="field-label" for="sop-evidence-note">证据说明</label>
        <textarea id="sop-evidence-note" v-model="evidenceNote" class="text-area" maxlength="1000" placeholder="描述现象、时点、报文或检测结果" required></textarea>

        <button class="btn secondary" type="submit" :disabled="busy || !evidenceType || !evidenceNote.trim()">登记证据</button>
      </form>
    </div>

    <div class="personal-box sop-section">
      <strong>分级判定与处置</strong>
      <p class="stage-note">
        当前判定：{{ problem.grade ? problem.grade + ' 级' : '未判定' }}
        <span v-if="problem.disposalType"> · {{ disposalLabel(problem.disposalType) || problem.disposalType }}</span>
      </p>
      <p v-if="problem.autoApprovalNote" class="muted">{{ problem.autoApprovalNote }}</p>

      <form v-if="canProcess" class="stack-form sop-form" @submit.prevent="submitAssess">
        <label class="field-label" for="assess-grade">证据判定等级</label>
        <select id="assess-grade" v-model="grade" class="text-input" required>
          <option value="" disabled>请选择</option>
          <option value="A">A 级（证据充分，直接锁定故障）</option>
          <option value="B">B 级（关键证据齐备，支持常规处置）</option>
          <option value="C">C 级（证据不足，仅限远程/低成本排查）</option>
        </select>

        <label class="field-label" for="assess-disposal">处置方式</label>
        <select id="assess-disposal" v-model="disposalType" class="text-input" required>
          <option value="" disabled>请选择</option>
          <option value="remote">{{ disposalLabel('remote') }}</option>
          <option value="onsite">{{ disposalLabel('onsite') }}</option>
          <option value="part">{{ disposalLabel('part') }}</option>
          <option value="replace" :disabled="grade === 'C'">
            {{ disposalLabel('replace') }}{{ grade === 'C' ? '（C级禁止）' : '' }}
          </option>
        </select>

        <p v-if="grade === 'C'" role="status" class="stage-note sop-warning">
          C级证据不足，禁止换装
        </p>

        <label class="field-label" for="assess-note">判定说明（可选）</label>
        <textarea id="assess-note" v-model="assessNote" class="text-area" maxlength="1000" placeholder="说明判定依据或处置要点"></textarea>

        <button
          class="btn primary"
          type="submit"
          :disabled="busy || !grade || !disposalType || (grade === 'C' && disposalType === 'replace')"
        >
          保存判定与处置
        </button>
      </form>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { disposalLabel, evidenceTypeLabel } from '../status'

const props = defineProps({
  problem: { type: Object, required: true },
  canProcess: Boolean,
  busy: Boolean
})

const emit = defineEmits(['add-evidence', 'assess'])

const evidenceTypeOptions = [
  'timestamp_log',
  'platform_data',
  'photo',
  'video',
  'site_compare',
  'swap_test'
].map(value => ({ value, label: evidenceTypeLabel(value) }))

const sourceOptions = [
  { value: 'agent', label: '坐席代传' },
  { value: 'sms', label: '短信链接' },
  { value: 'miniprogram', label: '小程序' },
  { value: 'field', label: '现场补录' }
]

const checkedCustomer = ref([])
const checkedOnsite = ref([])
const filePicker = ref(null)

const evidenceType = ref('timestamp_log')
const source = ref('agent')
const fileName = ref('')
const evidenceNote = ref('')

const grade = ref(props.problem.grade || '')
const disposalType = ref(props.problem.disposalType || '')
const assessNote = ref('')

const customerSteps = computed(() => (props.problem.sop?.customerSteps || []).slice(0, 3))
const onsiteSteps = computed(() => props.problem.sop?.onsiteSteps || [])
const recommendedLabels = computed(() => (props.problem.sop?.recommendedEvidenceTypes || [])
  .map(type => evidenceTypeLabel(type) || type)
  .filter(Boolean))

watch(() => props.problem.id, () => {
  checkedCustomer.value = []
  checkedOnsite.value = []
  grade.value = props.problem.grade || ''
  disposalType.value = props.problem.disposalType || ''
  assessNote.value = ''
})

watch(() => [props.problem.grade, props.problem.disposalType], ([nextGrade, nextDisposal]) => {
  if (nextGrade && !grade.value) grade.value = nextGrade
  if (nextDisposal && !disposalType.value) disposalType.value = nextDisposal
})

watch(grade, nextGrade => {
  if (nextGrade === 'C' && disposalType.value === 'replace') {
    disposalType.value = ''
  }
})

function sourceLabel(value) {
  return sourceOptions.find(item => item.value === value)?.label || value
}

function dateLabel(value) {
  return value ? String(value).replace('T', ' ').replace(/\.\d+/, '').slice(0, 16) : '时间未知'
}

function onFilePick(event) {
  const file = event.target?.files?.[0]
  if (file?.name) {
    fileName.value = file.name.slice(0, 200)
  }
}

function submitEvidence() {
  const note = evidenceNote.value.trim()
  if (!evidenceType.value || !note) return
  emit('add-evidence', {
    evidenceType: evidenceType.value,
    source: source.value || 'agent',
    fileName: fileName.value.trim(),
    note
  })
  fileName.value = ''
  evidenceNote.value = ''
  if (filePicker.value) filePicker.value.value = ''
}

function submitAssess() {
  if (!grade.value || !disposalType.value) return
  if (grade.value === 'C' && disposalType.value === 'replace') return
  emit('assess', {
    grade: grade.value,
    disposalType: disposalType.value,
    note: assessNote.value.trim()
  })
  assessNote.value = ''
}
</script>

<style scoped>
.sop-evidence-panel { display: grid; gap: 12px; margin-top: 12px; }
.sop-section { display: grid; gap: 8px; }
.sop-group { margin-top: 8px; }
.sop-checklist { list-style: none; margin: 4px 0 0; padding: 0; display: grid; gap: 6px; }
.sop-check { display: flex; align-items: flex-start; gap: 8px; cursor: pointer; font-weight: 400; }
.sop-check input { width: auto; min-height: 0; margin-top: 3px; }
.sop-list { margin-top: 4px; }
.sop-form { margin-top: 6px; }
.sop-hint { margin: 0; font-size: 12px; }
.sop-warning { color: var(--danger); font-weight: 600; margin: 0; }
</style>
