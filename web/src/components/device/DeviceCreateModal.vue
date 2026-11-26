<template>
  <!-- 入口 A 弹窗：为该设备建单 -->
  <div
    v-if="open && device"
    class="modal-backdrop"
    role="presentation"
    @click.self="requestClose"
  >
    <section
      class="modal-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-modal-title"
    >
      <header class="modal-header">
        <div>
          <h2 id="create-modal-title">为该设备建单</h2>
          <p>已自动关联设备主档与客户 SLA 信息，提交后进入待分配流转</p>
        </div>
        <button type="button" class="modal-close" aria-label="关闭弹窗" @click="requestClose">×</button>
      </header>

      <div class="modal-body">
        <div class="inset readonly-snapshot">
          <div><span>客户</span><strong>{{ textOrDash(device.customerName) }}</strong></div>
          <div><span>桩 SN</span><strong class="mono">{{ textOrDash(deviceSn) }}</strong></div>
          <div><span>质保</span><strong>{{ textOrDash(device.warrantyStatus) }}<template v-if="device.warrantyUntil">（至 {{ device.warrantyUntil }}）</template></strong></div>
          <div class="snap-full"><span>安装地址</span><strong>{{ textOrDash(device.siteAddress) }}</strong></div>
        </div>

        <div v-if="openProblems.length" class="modal-dup-warn" role="status">
          注意：该设备尚有 {{ openProblems.length }} 条未闭环工单（如 {{ orderNumber(openProblems[0]) }}），请确认非重复报修后再提交。
        </div>

        <form class="v11-form" @submit.prevent="onSubmit">
          <div class="form-grid-2">
            <div class="field-group">
              <label for="create-channel">报修渠道 <span class="req">*</span></label>
              <select id="create-channel" v-model="deviceForm.channel" class="form-control" required>
                <option v-for="opt in CHANNEL_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>

            <div class="field-group">
              <label for="create-category">故障大类 <span class="req">*</span></label>
              <select id="create-category" v-model="deviceForm.category" class="form-control" required>
                <option v-for="cat in CATEGORY_OPTIONS" :key="cat" :value="cat">{{ cat }}</option>
              </select>
            </div>

            <div class="field-group">
              <label for="create-urgency">紧急度 <span class="req">*</span></label>
              <select id="create-urgency" v-model="deviceForm.urgency" class="form-control" required>
                <option v-for="urg in URGENCY_OPTIONS" :key="urg.value" :value="urg.value">{{ urg.label }}</option>
              </select>
            </div>

            <div class="field-group">
              <label for="create-occurred">故障发生时点（北京时间） <span class="req">*</span></label>
              <input
                id="create-occurred"
                v-model="deviceForm.faultOccurredAt"
                type="datetime-local"
                class="form-control"
                required
              >
            </div>
          </div>

          <div class="field-group">
            <label for="create-title">故障现象标题 <span class="req">*</span></label>
            <input
              id="create-title"
              v-model="deviceForm.title"
              type="text"
              class="form-control"
              maxlength="150"
              placeholder="简要概括故障现象，例如：插枪无法启动充电（报 PE 异常）"
              required
            >
          </div>

          <div class="field-group">
            <label for="create-desc">现象描述 <span class="req">*</span></label>
            <textarea
              id="create-desc"
              v-model="deviceForm.description"
              class="form-control form-textarea"
              maxlength="1000"
              placeholder="请描述车主/现场反馈的具体情况、指示灯颜色、报错代码与复现条件"
              required
            ></textarea>
          </div>

          <p v-if="error" class="form-error" role="alert">{{ error }}</p>

          <div class="form-actions">
            <button type="button" class="btn btn--secondary" :disabled="isBusy" @click="requestClose">取消</button>
            <button type="submit" class="btn btn--primary" :disabled="isBusy || !canSubmit">
              {{ submitting ? '提交建单中…' : '确认创建问题单' }}
            </button>
          </div>
        </form>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, watch } from 'vue'
import { orderNumber } from '../../status'
import {
  CATEGORY_OPTIONS,
  CHANNEL_OPTIONS,
  URGENCY_OPTIONS,
  createTicketFormState,
  textOrDash
} from './deviceShared'

const props = defineProps({
  open: { type: Boolean, default: false },
  device: { type: Object, default: null },
  deviceSn: { type: String, default: '' },
  openProblems: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false },
  isBusy: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['close', 'submit'])

const deviceForm = reactive(createTicketFormState())

const canSubmit = computed(() => Boolean(
  props.deviceSn
  && deviceForm.faultOccurredAt
  && deviceForm.title.trim()
  && deviceForm.description.trim()
))

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      Object.assign(deviceForm, createTicketFormState())
    }
  }
)

function requestClose() {
  if (props.submitting) return
  emit('close')
}

function onSubmit() {
  if (props.isBusy || !canSubmit.value) return
  emit('submit', {
    channel: deviceForm.channel,
    category: deviceForm.category,
    urgency: deviceForm.urgency,
    faultOccurredAt: deviceForm.faultOccurredAt,
    title: deviceForm.title.trim(),
    description: deviceForm.description.trim()
  })
}

function onKeyDown(e) {
  if (e.key === 'Escape' && props.open) {
    requestClose()
  }
}

onMounted(() => window.addEventListener('keydown', onKeyDown))
onUnmounted(() => window.removeEventListener('keydown', onKeyDown))
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(15, 23, 42, 0.45);
  display: grid;
  place-items: center;
  padding: 16px;
}

.modal-dialog {
  width: min(680px, 100%);
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 14px;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.22);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line, #eaeef2);
  background: #e8f6f4;
}

.modal-header h2 {
  margin: 0;
  font-size: 16px;
  color: #0f766e;
}

.modal-header p {
  margin: 3px 0 0;
  font-size: 12px;
  color: #475569;
}

.modal-close {
  width: 30px;
  height: 30px;
  border: 1px solid #c5e4e1;
  border-radius: 7px;
  background: #fff;
  color: #0f766e;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.modal-body {
  padding: 18px 20px 20px;
  display: grid;
  gap: 14px;
}

.readonly-snapshot {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
  border-radius: 9px;
  background: var(--canvas, #f5f7fa);
  border: 1px solid var(--line, #eaeef2);
}

.readonly-snapshot > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.readonly-snapshot .snap-full {
  grid-column: 1 / -1;
}

.readonly-snapshot span {
  font-size: 12px;
  color: var(--text-tertiary, #7a8686);
}

.readonly-snapshot strong {
  font-size: 12px;
  color: #0f172a;
  overflow-wrap: anywhere;
}

.modal-dup-warn {
  padding: 9px 12px;
  border-radius: 8px;
  background: #fff9ec;
  border: 1px solid #f5d08a;
  color: #9a5b00;
  font-size: 12px;
}

@media (max-width: 640px) {
  .readonly-snapshot {
    grid-template-columns: 1fr;
  }

  .modal-body,
  .modal-header {
    padding: 14px;
  }
}
</style>
