let csrfToken

async function csrf() {
  if (!csrfToken) {
    const response = await fetch('./api/session/csrf', { cache: 'no-store' })
    if (!response.ok) throw new Error('无法建立会话，请稍后重试')
    csrfToken = (await response.json()).token
  }
  return csrfToken
}

function safeServerMessage(payload) {
  const message = payload && typeof payload.message === 'string' ? payload.message.trim() : ''
  if (!message || message.length > 200) return ''
  if (/exception|sql|stack|jdbc|password|secret|select |insert |update |delete /i.test(message)) return ''
  return message
}

async function failureMessage(response) {
  if (response.status === 401) return '请重新登录'
  let payload = null
  try {
    payload = await response.json()
  } catch (ignore) {
    payload = null
  }
  return safeServerMessage(payload) || ('请求失败（' + response.status + '）')
}

function requestKey() {
  return 'ui-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}

async function request(path, body, key) {
  const headers = body === undefined ? {} : { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': await csrf() }
  if (key) headers['Idempotency-Key'] = key
  const options = body === undefined ? {} : {
    method: 'POST',
    headers,
    body: JSON.stringify(body)
  }
  const response = await fetch('./api' + path, options)
  if (!response.ok) {
    if (response.status === 401) {
      csrfToken = undefined
      window.dispatchEvent(new Event('workbench-session-expired'))
    }
    if (response.status === 403) csrfToken = undefined
    const message = await failureMessage(response)
    const error = new Error(message)
    error.status = response.status
    throw error
  }
  if (response.status === 204) return undefined
  const text = await response.text()
  if (!text || !text.trim()) return undefined
  try {
    return JSON.parse(text)
  } catch (ignore) {
    throw new Error('响应格式不正确')
  }
}

export const lookupDevice = query => request('/devices/lookup?query=' + encodeURIComponent(query))
export const createProblem = form => request('/problems', form, requestKey())
export const loadProblem = id => request('/problems/' + encodeURIComponent(id))
export const addFollowup = (id, note, version) => request('/problems/' + encodeURIComponent(id) + '/followups', { note, expectedVersion: version }, requestKey())

export const findProblems = device => request('/problems?device=' + encodeURIComponent(device))
export function dueCallbacks(scope, cursor) {
  const query = new URLSearchParams()
  query.set('scope', scope || 'team')
  query.set('limit', '20')
  if (cursor) query.set('cursor', cursor)
  return request('/problems/due?' + query.toString())
}
export function mineProblems(cursor) {
  const query = new URLSearchParams()
  query.set('limit', '20')
  if (cursor) query.set('cursor', cursor)
  return request('/problems/mine?' + query.toString())
}
export function searchProblems(filters) {
  const query = new URLSearchParams()
  Object.keys(filters || {}).forEach(key => {
    const value = filters[key]
    if (value !== undefined && value !== null && String(value) !== '') query.set(key, value)
  })
  return request('/problems/board?' + query.toString())
}
export const changePassword = form => request('/accounts/password', form)
export const resetAccountPassword = (username, password) => request('/accounts/' + encodeURIComponent(username) + '/password', { password })
export const scheduleCallback = (id, form) => request('/problems/' + encodeURIComponent(id) + '/callback', form, requestKey())
export const finishCallback = (id, form) => request('/problems/' + encodeURIComponent(id) + '/callback/result', form, requestKey())
export const linkFieldOrder = (id, externalNo, version) => request('/problems/' + encodeURIComponent(id) + '/field-orders', { externalNo, expectedVersion: version }, requestKey())
export const recordFieldResult = (id, form) => request('/problems/' + encodeURIComponent(id) + '/field-orders/result', form, requestKey())
export const withdrawField = (id, reason, version) => request('/problems/' + encodeURIComponent(id) + '/field-withdrawal', { reason, expectedVersion: version }, requestKey())
export const claimProblem = (id, version) => request('/problems/' + encodeURIComponent(id) + '/claim', { expectedVersion: version }, requestKey())
export const handoffProblem = (id, form) => request('/problems/' + encodeURIComponent(id) + '/handoff', form, requestKey())
export const listAccounts = () => request('/accounts')
export const assignableAccounts = () => request('/accounts/assignable')
export const createAccount = form => request('/accounts', form)
export const setAccountEnabled = (username, enabled) => request('/accounts/' + encodeURIComponent(username) + '/status', { enabled })

export const currentAccount = () => request('/session')

export async function login(username, password) {
  const response = await fetch('./api/session/login', {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': await csrf() },
    body: new URLSearchParams({ username, password })
  })
  csrfToken = undefined
  if (!response.ok) throw new Error(response.status === 401 ? '账号或密码不正确' : '暂时无法登录，请重试')
  return currentAccount()
}

export async function logout() {
  await request('/session/logout', {})
  csrfToken = undefined
}
