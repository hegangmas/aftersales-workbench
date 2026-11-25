import { reactive, ref } from 'vue'
import { shanghaiStamp } from '../status'

function emptyDraft() {
  return {
    note: '',
    owner: '',
    dueAt: '',
    nextStep: '',
    outcome: 'continue',
    result: '',
    showCallback: false,
    contactedAt: '',
    evidenceSource: '',
    evidence: '',
    withdrawReason: '',
    handoffTarget: '',
    handoffReason: '',
    fieldNumber: '',
    fieldReceipt: '',
    fieldState: 'complete'
  }
}

function textOf(draft) {
  const parts = []
  if (draft.note) parts.push('跟进「' + draft.note + '」')
  if (draft.nextStep || draft.owner) parts.push('回访安排「' + [draft.owner, draft.dueAt, draft.nextStep].filter(Boolean).join(' ') + '」')
  if (draft.result) parts.push('回访结果「' + draft.result + '」')
  if (draft.handoffReason || draft.handoffTarget) parts.push('转交「' + [draft.handoffTarget, draft.handoffReason].filter(Boolean).join(' ') + '」')
  if (draft.withdrawReason) parts.push('撤回原因「' + draft.withdrawReason + '」')
  if (draft.fieldNumber) parts.push('现场单号「' + draft.fieldNumber + '」')
  if (draft.fieldReceipt) parts.push('现场回执「' + draft.fieldReceipt + '」')
  return parts.join('；')
}

export function useProblemDraft({ canHandle, unowned, account, assignees }) {
  const draft = reactive(emptyDraft())
  const refreshNote = ref('')

  function beginCallback() {
    draft.contactedAt = shanghaiStamp().replace(' ', 'T')
    draft.showCallback = true
  }

  function snapshotDraft() {
    return { ...draft }
  }

  function fillFromServer(current) {
    const task = current.pendingFollowup
    Object.assign(draft, emptyDraft(), {
      owner: task?.owner || '',
      dueAt: task?.dueAt?.replace(' ', 'T').slice(0, 16) || '',
      nextStep: task?.nextStep || '',
      showCallback: Boolean(task)
    })
    refreshNote.value = ''
  }

  function restoreDraft(saved, current) {
    if (current.state !== 'resolved' && !canHandle(current)) {
      const kept = textOf(saved)
      refreshNote.value = (unowned(current) ? '刷新后问题单仍无主，请先认领再处理。' : '刷新后当前账号不能处理这张问题单。')
        + (kept ? '未提交内容暂不能提交：' + kept : '')
      return
    }
    const active = current.fieldOrders?.some(item => ['linked', 'in_progress'].includes(item.state))
    const notes = []
    if (current.state === 'resolved') {
      notes.push('刷新后问题单已结案，跟进、回访、转交、撤回和现场单都不能再提交。')
      const kept = textOf(saved)
      if (kept) notes.push('未提交内容仅作保留，无法提交：' + kept)
      refreshNote.value = notes.join('')
      return
    }
    draft.note = saved.note
    const canCallback = current.state !== 'field' && !active
    if (canCallback) {
      draft.owner = saved.owner
      draft.dueAt = saved.dueAt
      draft.nextStep = saved.nextStep
      draft.showCallback = saved.showCallback
      draft.outcome = saved.outcome
      draft.result = saved.result
      draft.contactedAt = saved.contactedAt
      draft.evidenceSource = saved.evidenceSource
      draft.evidence = saved.evidence
    } else if (saved.nextStep || saved.result || saved.owner || saved.dueAt) {
      notes.push(current.state === 'field'
        ? '刷新后问题处于现场服务，回访安排和回访结果暂不能提交。'
        : '刷新后还有未结束的现场单，请先登记结果，回访暂不能提交。')
      const kept = [saved.owner, saved.dueAt, saved.nextStep, saved.result].filter(Boolean).join(' ')
      if (kept) notes.push('未提交的回访内容：' + kept + '。')
    }
    const handoffOpen = !unowned(current)
      && (account.value?.role === 'admin' || account.value?.username === current.owner)
    if (handoffOpen) {
      const known = assignees.value.some(item => item.username === saved.handoffTarget)
      draft.handoffTarget = known ? saved.handoffTarget : ''
      draft.handoffReason = saved.handoffReason
      if (saved.handoffTarget && !known) notes.push('原接收人已不在可转交名单中，请重新选择。转交原因仍保留。')
    } else if (saved.handoffReason || saved.handoffTarget) {
      notes.push(unowned(current) ? '刷新后问题单仍无主，需要先认领才能转交。' : '刷新后当前账号不能转交这张问题单。')
      notes.push('未提交的转交：' + [saved.handoffTarget, saved.handoffReason].filter(Boolean).join(' ') + '。')
    }
    const canWithdraw = current.state === 'field' && !active
    if (canWithdraw) draft.withdrawReason = saved.withdrawReason
    else if (saved.withdrawReason) {
      notes.push(active ? '刷新后已有未结束的现场单，不能撤回待派单。' : '刷新后问题单已不在待派单阶段，不能撤回。')
      notes.push('未提交的撤回原因：' + saved.withdrawReason + '。')
    }
    if (current.state === 'field' && !active) draft.fieldNumber = saved.fieldNumber
    else if (saved.fieldNumber) notes.push('刷新后不能再关联新的现场单。未提交的现场单号：' + saved.fieldNumber + '。')
    if (current.state === 'field' && active) {
      draft.fieldReceipt = saved.fieldReceipt
      draft.fieldState = saved.fieldState || 'complete'
    } else if (saved.fieldReceipt) {
      notes.push('刷新后现场回执不再适用。未提交的回执：' + saved.fieldReceipt + '。')
    }
    refreshNote.value = notes.length ? notes.join('') : '已刷新到最新版本，未提交的内容仍留在表单中。'
  }

  function resetDraft() {
    Object.assign(draft, emptyDraft())
    refreshNote.value = ''
  }

  return { draft, refreshNote, beginCallback, snapshotDraft, fillFromServer, restoreDraft, resetDraft }
}
