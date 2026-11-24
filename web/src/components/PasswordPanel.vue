<template>
  <el-card header="修改密码">
    <p id="password-rules" class="hint">8到72位，且同时包含字母和数字。修改后当前会话保持登录，其他设备需要用新密码重新登录。</p>
    <p v-if="notice" role="status" class="hint">{{ notice }}</p>
    <form aria-labelledby="password-heading" @submit.prevent="submitChange">
      <h2 id="password-heading" class="visually-hidden">修改本人密码</h2>
      <div class="field">
        <label for="current-password">当前密码</label>
        <div class="secret">
          <input id="current-password" v-model="currentPassword" name="currentPassword" :type="showCurrent ? 'text' : 'password'"
            autocomplete="current-password" required maxlength="72" data-error="current-password-error"
            aria-describedby="password-rules" @blur="onBlur" @input="onInput">
          <button type="button" :aria-pressed="showCurrent" aria-label="显示当前密码" @click="showCurrent = !showCurrent">
            {{ showCurrent ? '隐藏' : '显示' }}
          </button>
        </div>
        <p id="current-password-error" class="error-msg"><span role="img" aria-label="错误">!</span> 请填写当前密码。</p>
      </div>
      <div class="field">
        <label for="next-password">新密码</label>
        <div class="secret">
          <input id="next-password" v-model="nextPassword" name="nextPassword" :type="showNext ? 'text' : 'password'"
            autocomplete="new-password" required minlength="8" maxlength="72"
            pattern="(?=.*[A-Za-z])(?=.*\d).{8,72}" data-error="next-password-error"
            aria-describedby="password-rules" @blur="onBlur" @input="onInput">
          <button type="button" :aria-pressed="showNext" aria-label="显示新密码" @click="showNext = !showNext">
            {{ showNext ? '隐藏' : '显示' }}
          </button>
        </div>
        <p id="next-password-error" class="error-msg"><span role="img" aria-label="错误">!</span> 新密码需要8到72位，并同时包含字母和数字。</p>
      </div>
      <div class="field">
        <label for="confirm-password">再次输入新密码</label>
        <input id="confirm-password" v-model="confirmPassword" name="confirmPassword" :type="showNext ? 'text' : 'password'"
          autocomplete="new-password" required minlength="8" maxlength="72" data-error="confirm-password-error"
          aria-describedby="password-rules" @blur="onBlur" @input="onInput">
        <p id="confirm-password-error" class="error-msg"><span role="img" aria-label="错误">!</span> 两次输入的新密码不一致。</p>
      </div>
      <button type="submit" :disabled="busy">保存新密码</button>
    </form>
    <form v-if="admin" class="reset-form" @submit.prevent="submitReset">
      <h2 id="reset-heading">重置账号密码</h2>
      <p id="reset-rules" class="hint">仅管理员可重置。新密码立即生效，该账号在其他会话中的下一次请求会失效。页面不会显示新密码。</p>
      <div class="field">
        <label for="reset-user">账号</label>
        <select id="reset-user" v-model="resetUser" name="username" required>
          <option value="" disabled>请选择</option>
          <option v-for="item in accounts" :key="item.username" :value="item.username">{{ item.username }}</option>
        </select>
      </div>
      <div class="field">
        <label for="reset-password">新密码</label>
        <input id="reset-password" v-model="resetPassword" name="password" type="password" autocomplete="new-password"
          required minlength="8" maxlength="72" pattern="(?=.*[A-Za-z])(?=.*\d).{8,72}"
          data-error="reset-password-error" aria-describedby="reset-rules" @blur="onBlur" @input="onInput">
        <p id="reset-password-error" class="error-msg"><span role="img" aria-label="错误">!</span> 新密码需要8到72位，并同时包含字母和数字。</p>
      </div>
      <button type="submit" :disabled="busy">重置密码</button>
    </form>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'

const props = defineProps({
  admin: Boolean,
  busy: Boolean,
  notice: { type: String, default: '' },
  accounts: { type: Array, default: () => [] },
  changePassword: { type: Function, required: true },
  resetPassword: { type: Function, required: true }
})

const currentPassword = ref('')
const nextPassword = ref('')
const confirmPassword = ref('')
const showCurrent = ref(false)
const showNext = ref(false)
const resetUser = ref('')
const resetPassword = ref('')

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
  const input = event.target
  if (input.name === 'confirmPassword') input.setCustomValidity('')
  if (input.dataset.touched === '1') mark(input)
}

function onBlur(event) {
  const input = event.target
  if (!input.value && input.dataset.touched !== '1') return
  input.dataset.touched = '1'
  mark(input)
}

onMounted(() => {
  document.querySelectorAll('#current-password, #next-password, #confirm-password, #reset-password')
    .forEach(input => input.setAttribute('aria-invalid', 'false'))
})

async function submitChange(event) {
  const form = event.target
  const again = form.elements.confirmPassword
  again.setCustomValidity(nextPassword.value === confirmPassword.value ? '' : '两次输入的新密码不一致')
  if (!form.reportValidity()) {
    Array.from(form.elements).forEach(element => {
      if (element.matches && element.matches('input')) {
        element.dataset.touched = '1'
        mark(element)
      }
    })
    return
  }
  const ok = await props.changePassword({
    currentPassword: currentPassword.value,
    nextPassword: nextPassword.value
  })
  if (ok) {
    currentPassword.value = ''
    nextPassword.value = ''
    confirmPassword.value = ''
    showCurrent.value = false
    showNext.value = false
  }
}

async function submitReset(event) {
  const form = event.target
  if (!form.reportValidity()) return
  const ok = await props.resetPassword({ username: resetUser.value, password: resetPassword.value })
  if (ok) resetPassword.value = ''
}
</script>

<style scoped>
h2 { margin: 0 0 8px; font-size: 16px; }
.hint { margin: 0 0 8px; color: var(--muted); font-size: 13px; }
form { display: grid; gap: 8px; }
.field { display: flex; flex-direction: column; gap: 4px; }
label { font-size: 13px; font-weight: 600; }
.secret { display: flex; gap: 8px; align-items: center; }
.secret input { flex: 1; }
button { min-height: 44px; padding: 0 12px; color: var(--brand); background: var(--card); border: 1px solid var(--brand); border-radius: 6px; cursor: pointer; }
button[type="submit"] { justify-self: start; }
.reset-form { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--line); }
.error-msg { display: none; margin: 0; color: var(--danger); font-size: 13px; }
input:user-invalid, select:user-invalid { border-color: var(--danger); background: #f8e8e6; }
input:user-invalid + .error-msg, .secret:has(input:user-invalid) + .error-msg { display: block; }
.visually-hidden {
  position: absolute;
  clip-path: inset(50%);
  overflow: hidden;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  border: 0;
  white-space: nowrap;
}
</style>
