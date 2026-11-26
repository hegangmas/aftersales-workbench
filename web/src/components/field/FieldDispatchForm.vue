<template>
  <!-- 右栏：需补填字段 -->
  <article class="card-col">
    <header class="col-head">
      <div>
        <span class="col-kicker">STEP 2 · 补全字段</span>
        <h3>需补填字段</h3>
      </div>
      <span class="required-hint"><em class="req">*</em> 请完善以下信息后再提交</span>
    </header>

    <div v-if="isDevicePending" class="state-alert danger" role="alert">
      <strong>设备信息待补，禁止派单</strong>
      <p>请先返回工作台补齐设备桩 SN 档案后，再执行派单至智享乐装。</p>
    </div>
    <div v-else-if="latestV11Order" class="state-alert info" role="status">
      <strong>已派单至智享乐装（单号：{{ latestV11Order.externalNo }}）</strong>
      <p>工单当前状态为「{{ fieldStateLabel(latestV11Order.state) }}」，可在下方回传状态区模拟推进。</p>
    </div>
    <div v-else-if="needsClaimBeforeDispatch" class="state-alert" role="status">
      <strong>当前工单处于「待分配」状态</strong>
      <p>请先返回工作台点击「承接处理」进入处理中，再确认派单。</p>
    </div>

    <form id="dispatch-v11-form" class="dispatch-form" @submit.prevent="handleDispatchSubmit">
      <div class="form-group">
        <label class="form-label" for="dispatch-is-claim">是否索赔 <em class="req">*</em></label>
        <select id="dispatch-is-claim" v-model="form.isClaim" class="field-input" :disabled="fieldsLocked">
          <option value="">请选择</option>
          <option value="yes">是</option>
          <option value="no">否</option>
        </select>
      </div>

      <div class="form-group">
        <label class="form-label" for="dispatch-return-factory">桩是否返厂 <em class="req">*</em></label>
        <select id="dispatch-return-factory" v-model="form.returnToFactory" class="field-input" :disabled="fieldsLocked">
          <option value="">请选择</option>
          <option value="yes">是</option>
          <option value="no">否</option>
        </select>
      </div>

      <div class="form-group">
        <label class="form-label" for="dispatch-project-code">项目编号 <em class="req">*</em></label>
        <input id="dispatch-project-code" v-model="form.projectCode" class="field-input" type="text" maxlength="64" :disabled="fieldsLocked" placeholder="默认带入设备项目编号，如 PRJ-2026-AH01">
      </div>

      <div class="form-group">
        <label class="form-label" for="dispatch-visit-time">预约上门时间 <em class="req">*</em></label>
        <input id="dispatch-visit-time" v-model="form.appointmentAt" class="field-input" type="datetime-local" :disabled="fieldsLocked">
      </div>

      <!-- 换装照片与旧件 -->
      <div class="upload-section">
        <div class="upload-head">
          <span class="form-label">换装照片与旧件</span>
          <span class="upload-count">{{ uploadedFiles.length }}/3</span>
        </div>
        <p class="upload-hint">支持jpg/png/pdf，单个文件不超过10MB，最多上传3张</p>

        <input id="dispatch-file-input" ref="fileInputRef" class="sr-only" type="file" accept=".jpg,.jpeg,.png,.pdf" multiple :disabled="fieldsLocked || uploadedFiles.length >= 3" @change="onFilesSelected">

        <div
          class="dropzone"
          :class="{ dragging: isDragging, disabled: fieldsLocked || uploadedFiles.length >= 3 }"
          role="button"
          tabindex="0"
          @click="triggerFilePicker"
          @keydown.enter.prevent="triggerFilePicker"
          @keydown.space.prevent="triggerFilePicker"
          @dragover.prevent="onDragOver"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="onDropFiles"
        >
          <span class="drop-icon" aria-hidden="true">⬆</span>
          <span class="drop-text">{{ uploadedFiles.length >= 3 ? '已达到最多 3 张上传上限' : '点击或拖拽文件到此处上传' }}</span>
        </div>

        <p v-if="uploadError" class="form-error" role="alert">{{ uploadError }}</p>

        <div class="file-grid">
          <div v-for="(file, idx) in uploadedFiles" :key="file.id" class="file-card">
            <div class="file-card-top">
              <span class="file-tag">{{ file.extLabel }}</span>
              <button v-if="!fieldsLocked" class="remove-file" type="button" aria-label="移除文件" @click="removeFile(idx)">×</button>
            </div>
            <strong class="file-name" :title="file.name">{{ file.name }}</strong>
            <small class="file-meta">{{ file.sizeText }}</small>
          </div>

          <button v-if="!fieldsLocked && uploadedFiles.length < 3" type="button" class="file-slot-btn" @click="triggerFilePicker">
            + 上传第{{ nextUploadSlotIndex }}张
          </button>
        </div>
      </div>

      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
    </form>
  </article>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'

const props = defineProps({
  problem: { type: Object, required: true },
  device: { type: Object, default: null },
  isDevicePending: Boolean,
  needsClaimBeforeDispatch: Boolean,
  fieldsLocked: Boolean,
  confirmDisabled: Boolean,
  latestV11Order: { type: Object, default: null },
  fieldStateLabel: { type: Function, required: true }
})

const emit = defineEmits(['dispatch-v11', 'form-state'])

const MAX_FILES = 3
const MAX_FILE_BYTES = 10 * 1024 * 1024
const ALLOWED_EXT = /\.(jpe?g|png|pdf)$/i

const form = reactive({
  isClaim: '',
  returnToFactory: '',
  projectCode: '',
  appointmentAt: ''
})

const fileInputRef = ref(null)
const uploadedFiles = ref([])
const isDragging = ref(false)
const uploadError = ref('')
const formError = ref('')
const projectTouched = ref(false)

const snapshot = computed(() => {
  const raw = props.problem?.snapshotJson
  if (!raw) return null
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch (ignore) {
    return null
  }
})

const defaultProjectCode = computed(() =>
  props.device?.projectCode || snapshot.value?.projectCode || ''
)

const isFormComplete = computed(() =>
  Boolean(form.isClaim && form.returnToFactory && form.projectCode.trim() && form.appointmentAt)
)

const nextUploadSlotIndex = computed(() =>
  Math.min(MAX_FILES, uploadedFiles.value.length + 1)
)

function formatSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '已同步'
  if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024)) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function fileExtLabel(name) {
  const ext = String(name || '').split('.').pop().toUpperCase()
  return ext || '文件'
}

function triggerFilePicker() {
  if (props.fieldsLocked || uploadedFiles.value.length >= MAX_FILES) return
  fileInputRef.value?.click()
}

function appendFiles(fileList) {
  uploadError.value = ''
  const files = Array.from(fileList || [])
  if (!files.length) return
  for (const file of files) {
    if (uploadedFiles.value.length >= MAX_FILES) {
      uploadError.value = '最多上传 3 张文件'
      break
    }
    if (!ALLOWED_EXT.test(file.name)) {
      uploadError.value = `文件「${file.name}」格式不支持，仅支持 jpg/png/pdf`
      continue
    }
    if (file.size > MAX_FILE_BYTES) {
      uploadError.value = `文件「${file.name}」超过 10MB 限制`
      continue
    }
    uploadedFiles.value.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}-${file.name}`,
      name: file.name,
      sizeText: formatSize(file.size),
      extLabel: fileExtLabel(file.name)
    })
  }
}

function onFilesSelected(event) {
  appendFiles(event.target?.files)
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function onDragOver() {
  if (!props.fieldsLocked && uploadedFiles.value.length < MAX_FILES) {
    isDragging.value = true
  }
}

function onDropFiles(event) {
  isDragging.value = false
  if (props.fieldsLocked || uploadedFiles.value.length >= MAX_FILES) return
  appendFiles(event.dataTransfer?.files)
}

function removeFile(idx) {
  uploadedFiles.value.splice(idx, 1)
  uploadError.value = ''
}

function toDatetimeLocalValue(val) {
  if (!val) return ''
  const norm = String(val).trim().replace(' ', 'T')
  return norm.slice(0, 16)
}

function syncFromProps() {
  formError.value = ''
  uploadError.value = ''
  const order = props.latestV11Order
  if (order) {
    form.isClaim = Number(order.isClaim) === 1 || order.isClaim === true ? 'yes' : 'no'
    form.returnToFactory = Number(order.returnToFactory) === 1 || order.returnToFactory === true ? 'yes' : 'no'
    form.projectCode = order.projectCode || defaultProjectCode.value || ''
    form.appointmentAt = toDatetimeLocalValue(order.appointmentAt)
    const names = String(order.photoNames || '').split(',').map(s => s.trim()).filter(Boolean)
    uploadedFiles.value = names.slice(0, MAX_FILES).map((name, idx) => ({
      id: `saved-${idx}-${name}`,
      name,
      sizeText: '已随单同步',
      extLabel: fileExtLabel(name)
    }))
  } else if (!projectTouched.value || !form.projectCode) {
    form.projectCode = defaultProjectCode.value || ''
  }
}

function handleDispatchSubmit() {
  formError.value = ''
  if (props.confirmDisabled) return
  if (!form.isClaim) {
    formError.value = '请选择「是否索赔」'
    return
  }
  if (!form.returnToFactory) {
    formError.value = '请选择「桩是否返厂」'
    return
  }
  if (!form.projectCode.trim()) {
    formError.value = '请填写「项目编号」'
    return
  }
  if (!form.appointmentAt) {
    formError.value = '请选择「预约上门时间」'
    return
  }
  emit('dispatch-v11', {
    isClaim: form.isClaim === 'yes',
    returnToFactory: form.returnToFactory === 'yes',
    projectCode: form.projectCode.trim(),
    appointmentAt: form.appointmentAt,
    photoNames: uploadedFiles.value.map(f => f.name).join(',')
  })
}

watch(
  () => form.projectCode,
  val => {
    if (val && val !== defaultProjectCode.value) {
      projectTouched.value = true
    }
  }
)

watch(
  [isFormComplete, () => uploadedFiles.value[0]?.name || ''],
  ([isComplete, firstPhotoName]) => {
    emit('form-state', { isComplete, firstPhotoName })
  },
  { immediate: true }
)

watch(
  () => [props.problem?.id, props.problem?.version, props.device?.projectCode],
  ([newId], oldVals) => {
    const oldId = oldVals ? oldVals[0] : undefined
    if (newId !== oldId) {
      form.isClaim = ''
      form.returnToFactory = ''
      form.projectCode = ''
      form.appointmentAt = ''
      uploadedFiles.value = []
      projectTouched.value = false
    }
    syncFromProps()
  },
  { immediate: true }
)

defineExpose({ syncFromProps })
</script>

<style scoped>
.card-col {
  min-width: 0;
  padding: 18px 20px;
  background: var(--bg-surface, #ffffff);
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  box-shadow: var(--elev-1);
}
.col-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--border-subtle, #edf0f0);
}
.col-kicker { display: block; color: var(--brand-700, #0f766e); font-size: 12px; font-weight: 600; letter-spacing: 0.04em; }
.col-head h3 { margin: 2px 0 0; color: var(--text-primary, #152020); font-size: 15px; font-weight: 600; }
.required-hint { font-size: 12px; color: var(--text-tertiary, #7a8686); white-space: nowrap; }
.req { font-style: normal; color: #c0362c; font-weight: 700; }

.state-alert {
  padding: 10px 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  border: 1px solid #f3e2b8;
  background: #fff8e8;
  color: #8a5a00;
  font-size: 12px;
}
.state-alert.info { border-color: #cce9e4; background: var(--pine-soft, #e8f6f4); color: var(--pine-ink, #102c2b); }
.state-alert.danger { border-color: #fecaca; background: #fef2f2; color: #b91c1c; }
.state-alert strong { display: block; margin-bottom: 2px; }
.state-alert p { margin: 0; line-height: 1.5; }

.dispatch-form { display: flex; flex-direction: column; gap: 12px; }
.form-group { display: grid; gap: 5px; min-width: 0; }
.form-label { color: var(--ink, #334155); font-size: 12px; font-weight: 600; }
.field-input {
  width: 100%;
  min-width: 0;
  min-height: 36px;
  padding: 6px 10px;
  border: 1px solid #dce3ec;
  border-radius: 8px;
  background: #fff;
  color: var(--ink, #334155);
  font-size: 12px;
}
.field-input:focus { outline: none; border-color: var(--pine-bright, #0d9488); box-shadow: 0 0 0 3px #0d94881f; }
.field-input:disabled { background: #f1f5f9; color: #64748b; cursor: not-allowed; }

.upload-section { display: grid; gap: 6px; padding-top: 4px; }
.upload-head { display: flex; align-items: center; justify-content: space-between; }
.upload-count { font-size: 12px; color: var(--text-tertiary, #7a8686); font-variant-numeric: tabular-nums; }
.upload-hint { margin: 0; font-size: 12px; color: var(--text-tertiary, #7a8686); }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); border: 0; }

.dropzone {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 52px;
  padding: 10px 12px;
  border: 1.5px dashed #a4d2c9;
  border-radius: 8px;
  background: #f8fcfb;
  color: var(--pine, #0f766e);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.dropzone:hover:not(.disabled), .dropzone.dragging { background: var(--pine-soft, #e8f6f4); border-color: var(--pine, #0f766e); }
.dropzone.disabled { border-color: #dce3ec; background: #f8fafc; color: #94a3b8; cursor: not-allowed; }
.drop-icon { font-size: 13px; }

.file-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 2px; }
.file-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding: 8px 9px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
  background: #fff;
}
.file-card-top { display: flex; align-items: center; justify-content: space-between; gap: 4px; }
.file-tag { padding: 1px 6px; border-radius: 4px; background: var(--brand-50, #e8f6f4); color: var(--brand-700, #0f766e); font-size: 12px; font-weight: 600; }
.remove-file { border: 0; background: transparent; color: #94a3b8; font-size: 14px; line-height: 1; padding: 0 2px; cursor: pointer; }
.remove-file:hover { color: #c0362c; }
.file-name { font-size: 12px; color: var(--text-primary, #152020); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-meta { color: var(--text-tertiary, #7a8686); font-size: 12px; }
.file-slot-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60px;
  padding: 8px;
  border: 1px dashed var(--border-strong, #cfd7d7);
  border-radius: 8px;
  background: var(--bg-subtle, #f8fafa);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  text-align: center;
}
.file-slot-btn:hover { border-color: var(--pine, #0f766e); background: var(--pine-soft, #e8f6f4); }
.form-error { margin: 0; color: #c0362c; font-size: 12px; font-weight: 600; }
@media (max-width: 760px) { .file-grid { grid-template-columns: 1fr; } }
</style>
