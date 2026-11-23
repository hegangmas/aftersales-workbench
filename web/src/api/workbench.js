async function request(path, body) {
  const options = body === undefined ? {} : {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }
  const response = await fetch('./api' + path, options)
  if (!response.ok) throw new Error('请求失败（' + response.status + '）')
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
