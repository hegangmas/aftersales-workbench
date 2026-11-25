<template>
  <div class="split">
    <section class="panel">
      <h2>创建账号</h2>
      <p class="hint">新密码为 8 到 72 位，且同时包含字母和数字。创建成功后表单会清空，账号列表和转交候选会立即更新。</p>
      <form class="stack-form" :class="{ settled: createSettled }" @submit.prevent="submitCreate">
        <label class="field-label" for="new-username">新账号</label>
        <input id="new-username" v-model.trim="username" class="text-input" maxlength="20" autocomplete="off" required data-error="new-username-error" @blur="onBlur" @input="onInput">
        <p id="new-username-error" class="error-msg">请填写账号。</p>
        <label class="field-label" for="new-password">初始密码</label>
        <div class="secret">
          <input id="new-password" v-model="password" class="text-input" :type="showCreate ? 'text' : 'password'" autocomplete="new-password" minlength="8" maxlength="72" pattern="(?=.*[A-Za-z])(?=.*\d).{8,72}" required data-error="new-password-error" @blur="onBlur" @input="onInput">
          <button type="button" :aria-pressed="showCreate" :aria-label="showCreate ? '隐藏初始密码' : '显示初始密码'" @click="showCreate = !showCreate">{{ showCreate ? '隐藏' : '显示' }}</button>
        </div>
        <p id="new-password-error" class="error-msg">新密码需要 8 到 72 位，并同时包含字母和数字。</p>
        <label class="field-label" for="new-role">角色</label>
        <select id="new-role" v-model="role" class="text-input">
          <option value="operator">坐席</option>
          <option value="admin">管理员</option>
        </select>
        <button class="btn primary" type="submit" :disabled="busy">创建账号</button>
      </form>
      <p v-if="notice" role="status" class="success-note">{{ notice }}</p>
    </section>
    <section class="panel">
      <h2>账号列表</h2>
      <div v-for="item in accounts" :key="item.username" class="account-row">
        <span>{{ item.username }} · {{ item.role === 'admin' ? '管理员' : '坐席' }} · {{ item.enabled ? '启用' : '停用' }}</span>
        <button class="btn secondary" type="button" :disabled="busy" @click="$emit('toggle', item)">{{ item.enabled ? '停用' : '启用' }}</button>
      </div>
      <form class="stack-form action-block" :class="{ settled: resetSettled }" @submit.prevent="submitReset">
        <h3>重置密码</h3>
        <p id="reset-rules" class="hint">仅管理员可重置。新密码立即生效，该账号在其他会话中的下一次请求会失效。页面不会显示新密码。</p>
        <label class="field-label" for="reset-user">账号</label>
        <select id="reset-user" v-model="resetUser" class="text-input" required>
          <option value="" disabled>请选择</option>
          <option v-for="item in accounts" :key="'reset-' + item.username" :value="item.username">{{ item.username }}</option>
        </select>
        <label class="field-label" for="reset-password">新密码</label>
        <div class="secret">
          <input id="reset-password" v-model="resetPassword" class="text-input" :type="showReset ? 'text' : 'password'" autocomplete="new-password" required minlength="8" maxlength="72" pattern="(?=.*[A-Za-z])(?=.*\d).{8,72}" data-error="reset-password-error" aria-describedby="reset-rules" @blur="onBlur" @input="onInput">
          <button type="button" :aria-pressed="showReset" :aria-label="showReset ? '隐藏重置密码' : '显示重置密码'" @click="showReset = !showReset">{{ showReset ? '隐藏' : '显示' }}</button>
        </div>
        <p id="reset-password-error" class="error-msg">新密码需要 8 到 72 位，并同时包含字母和数字。</p>
        <button class="btn secondary" type="submit" :disabled="busy">重置密码</button>
      </form>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  accounts: { type: Array, default: () => [] },
  busy: Boolean,
  notice: { type: String, default: '' },
  createAccount: { type: Function, required: true },
  resetPassword: { type: Function, required: true }
})
defineEmits(['toggle'])
const username = ref('')
const password = ref('')
const role = ref('operator')
const showCreate = ref(false)
const createSettled = ref(false)
const resetUser = ref('')
const resetPassword = ref('')
const showReset = ref(false)
const resetSettled = ref(false)

function linkError(input, shown) {
  const errorId = input.dataset.error
  if (!errorId) return
  const ids = (input.getAttribute('aria-describedby') || '').split(/\s+/).filter(id => id && id !== errorId)
  if (shown) ids.push(errorId)
  input.setAttribute('aria-describedby', ids.join(' '))
}
function mark(input) {
  const invalid = !input.checkValidity()
  input.setAttribute('aria-invalid', invalid ? 'true' : 'false')
  linkError(input, invalid)
}
function onInput(event) {
  createSettled.value = false
  resetSettled.value = false
  if (event.target.dataset.touched === '1') mark(event.target)
}
function onBlur(event) {
  const input = event.target
  if (!input.value && input.dataset.touched !== '1') return
  input.dataset.touched = '1'
  mark(input)
}
async function submitCreate(event) {
  if (!event.target.reportValidity()) return
  const ok = await props.createAccount({ username: username.value, password: password.value, role: role.value })
  if (ok) {
    username.value = ''
    password.value = ''
    role.value = 'operator'
    showCreate.value = false
    createSettled.value = true
  }
}
async function submitReset(event) {
  if (!event.target.reportValidity()) return
  const ok = await props.resetPassword({ username: resetUser.value, password: resetPassword.value })
  if (ok) {
    resetPassword.value = ''
    showReset.value = false
    resetSettled.value = true
  }
}
</script>
