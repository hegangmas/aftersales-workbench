let csrfToken

async function csrf() {
  if (!csrfToken) {
    const response = await fetch('./api/session/csrf', { cache: 'no-store' })
    if (!response.ok) throw new Error('无法建立会话，请稍后重试')
    csrfToken = (await response.json()).token
  }
  return csrfToken
}

async function request(path, body) {
  const options = body === undefined ? {} : {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': await csrf() },
    body: JSON.stringify(body)
  }
  const response = await fetch('./api' + path, options)
  if (!response.ok) {
    if (response.status === 401) {
      csrfToken = undefined
      window.dispatchEvent(new Event('workbench-session-expired'))
    }
    if (response.status === 403) csrfToken = undefined
    throw new Error(response.status === 401 ? '请重新登录' : '请求失败（' + response.status + '）')
  }
  return response.status === 204 ? undefined : response.json()
}

export const lookupDevice = query => request('/devices/lookup?query=' + encodeURIComponent(query))
export const createProblem = form => request('/problems', form)
export const loadProblem = id => request('/problems/' + encodeURIComponent(id))
export const addFollowup = (id, note) => request('/problems/' + encodeURIComponent(id) + '/followups', { note })

export const findProblems = device => request('/problems?device=' + encodeURIComponent(device))
export const dueCallbacks = () => request('/problems/due')
export const scheduleCallback = (id, form) => request('/problems/' + encodeURIComponent(id) + '/callback', form)
export const finishCallback = (id, form) => request('/problems/' + encodeURIComponent(id) + '/callback/result', form)
export const linkFieldOrder = (id, externalNo) => request('/problems/' + encodeURIComponent(id) + '/field-orders', { externalNo })
export const recordFieldResult = (id, form) => request('/problems/' + encodeURIComponent(id) + '/field-orders/result', form)

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
