<template>
  <section class="panel">
    <h2 id="password-heading">修改当前账号密码</h2>
    <p id="password-rules" class="hint">8 到 72 位，且同时包含字母和数字。修改后当前会话保持登录，其他设备需要用新密码重新登录。</p>
    <p v-if="notice" role="status" class="success-note">{{ notice }}</p>
    <form class="stack-form" :class="{ settled }" aria-labelledby="password-heading" @submit.prevent="submitChange">
      <div>
        <label class="field-label" for="current-password">当前密码</label>
        <div class="secret">
          <input id="current-password" v-model="currentPassword" class="text-input" name="currentPassword" :type="showCurrent ? 'text' : 'password'" autocomplete="current-password" required maxlength="72" data-error="current-password-error" aria-describedby="password-rules" @blur="onBlur" @input="onInput">
          <button type="button" :aria-pressed="showCurrent" :aria-label="showCurrent ? '隐藏当前密码' : '显示当前密码'" @click="showCurrent = !showCurrent">{{ showCurrent ? '隐藏' : '显示' }}</button>
        </div>
        <p id="current-password-error" class="error-msg">请填写当前密码。</p>
      </div>
      <div>
        <label class="field-label" for="next-password">新密码</label>
        <div class="secret">
          <input id="next-password" v-model="nextPassword" class="text-input" name="nextPassword" :type="showNext ? 'text' : 'password'" autocomplete="new-password" required minlength="8" maxlength="72" pattern="(?=.*[A-Za-z])(?=.*\d).{8,72}" data-error="next-password-error" aria-describedby="password-rules" @blur="onBlur" @input="onInput">
          <button type="button" :aria-pressed="showNext" :aria-label="showNext ? '隐藏新密码' : '显示新密码'" @click="showNext = !showNext">{{ showNext ? '隐藏' : '显示' }}</button>
        </div>
        <p id="next-password-error" class="error-msg">新密码需要 8 到 72 位，并同时包含字母和数字。</p>
      </div>
      <div>
        <label class="field-label" for="confirm-password">再次输入新密码</label>
        <input id="confirm-password" v-model="confirmPassword" class="text-input" name="confirmPassword" :type="showNext ? 'text' : 'password'" autocomplete="new-password" required minlength="8" maxlength="72" data-error="confirm-password-error" aria-describedby="password-rules" @blur="onBlur" @input="onInput">
        <p id="confirm-password-error" class="error-msg">两次输入的新密码不一致。</p>
      </div>
      <button class="btn primary" type="submit" :disabled="busy">保存新密码</button>
    </form>
  </section>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  busy: Boolean,
  notice: { type: String, default: '' },
  changePassword: { type: Function, required: true }
})
const currentPassword = ref('')
const nextPassword = ref('')
const confirmPassword = ref('')
const showCurrent = ref(false)
const showNext = ref(false)
const settled = ref(false)

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
  settled.value = false
  if (input.name === 'confirmPassword') input.setCustomValidity('')
  if (input.dataset.touched === '1') mark(input)
}
function onBlur(event) {
  const input = event.target
  if (!input.value && input.dataset.touched !== '1') return
  input.dataset.touched = '1'
  mark(input)
}
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
  const ok = await props.changePassword({ currentPassword: currentPassword.value, nextPassword: nextPassword.value })
  if (ok) {
    currentPassword.value = ''
    nextPassword.value = ''
    confirmPassword.value = ''
    showCurrent.value = false
    showNext.value = false
    settled.value = true
  }
}
</script>
