import {
  acceptProblemV11,
  addEvidence,
  addFollowup,
  assessProblem,
  assignProblemV11,
  bindProblemDevice,
  dispatchProblemV11,
  escalateProblem,
  finishCallback,
  forceDistributeV11,
  handoffProblem,
  linkFieldOrder,
  quickCloseProblem,
  recordFieldResult,
  reopenProblemV11,
  scheduleCallback,
  suspendProblem,
  triggerLezhuangCallback,
  withdrawField
} from '../api/workbench'

export function useProblemActions({ problem, draft, boardItems, run, showProblem, refreshQueues }) {
  function runOnCurrent(action, afterSuccess) {
    return run(async () => {
      const id = problem.value.id
      await action(id, problem.value.version)
      if (afterSuccess) afterSuccess()
      await showProblem(id)
      await refreshQueues()
    }, { conflict: true })
  }

  function claim() {
    return assignV11('')
  }

  function claimRow(item) {
    return run(async () => {
      await assignProblemV11(item.id, '', item.version)
      await refreshQueues()
    }, { conflict: true })
  }

  function claimFromDispatch(target) {
    const item = typeof target === 'string' ? boardItems.value.find(row => row.id === target) : target
    if (item) return claimRow(item)
    return undefined
  }

  function runForceDistribute() {
    return run(async () => {
      await forceDistributeV11()
      await refreshQueues()
    })
  }

  function bindDevice(sn) {
    return runOnCurrent((id, version) => bindProblemDevice(id, sn, version))
  }

  function assignV11(target) {
    return runOnCurrent((id, version) => assignProblemV11(id, target, version))
  }

  function dispatchV11(form) {
    return runOnCurrent((id, version) => dispatchProblemV11(id, { ...form, expectedVersion: version }))
  }

  function lzCallbackV11(form) {
    return runOnCurrent((id, version) => triggerLezhuangCallback(id, { ...form, expectedVersion: version }))
  }

  function acceptV11(form) {
    return runOnCurrent((id, version) => acceptProblemV11(id, { ...form, expectedVersion: version }))
  }

  function reopenV11(reason) {
    return run(async () => {
      const created = await reopenProblemV11(problem.value.id, reason)
      await showProblem(created.id)
      await refreshQueues()
    })
  }

  function saveNote() {
    return runOnCurrent(
      (id, version) => addFollowup(id, draft.note, version),
      () => { draft.note = '' }
    )
  }

  function handoff() {
    return runOnCurrent(
      (id, version) => handoffProblem(id, {
        target: draft.handoffTarget,
        reason: draft.handoffReason,
        expectedVersion: version
      }),
      () => { draft.handoffReason = '' }
    )
  }

  function saveSchedule() {
    return runOnCurrent((id, version) => scheduleCallback(id, {
      owner: draft.owner,
      nextStep: draft.nextStep,
      dueAt: draft.dueAt,
      expectedVersion: version
    }))
  }

  function saveOutcome() {
    return runOnCurrent((id, version) => finishCallback(id, {
      outcome: draft.outcome,
      note: draft.result,
      contactedAt: draft.contactedAt,
      evidenceSource: draft.evidenceSource || null,
      evidence: draft.evidence || null,
      expectedVersion: version
    }))
  }

  function withdrawDispatch() {
    return runOnCurrent(
      (id, version) => withdrawField(id, draft.withdrawReason, version),
      () => { draft.withdrawReason = '' }
    )
  }

  function saveFieldLink(number) {
    return runOnCurrent(
      (id, version) => linkFieldOrder(id, number, version),
      () => { draft.fieldNumber = '' }
    )
  }

  function saveFieldResult(form) {
    return runOnCurrent(
      (id, version) => recordFieldResult(id, { ...form, expectedVersion: version }),
      () => { draft.fieldReceipt = '' }
    )
  }

  function suspendTicket(form) {
    return runOnCurrent((id, version) => suspendProblem(id, { ...form, expectedVersion: version }))
  }

  function quickCloseTicket(form) {
    return runOnCurrent((id, version) => quickCloseProblem(id, { ...form, expectedVersion: version }))
  }

  function escalateTicket(form) {
    return runOnCurrent((id, version) => escalateProblem(id, { ...form, expectedVersion: version }))
  }

  function saveEvidence(form) {
    return runOnCurrent((id, version) => addEvidence(id, { ...form, expectedVersion: version }))
  }

  function saveAssessment(form) {
    return runOnCurrent((id, version) => assessProblem(id, { ...form, expectedVersion: version }))
  }

  return {
    claim,
    bindDevice,
    assignV11,
    dispatchV11,
    lzCallbackV11,
    acceptV11,
    reopenV11,
    saveNote,
    handoff,
    saveSchedule,
    saveOutcome,
    withdrawDispatch,
    saveFieldLink,
    saveFieldResult,
    suspendTicket,
    quickCloseTicket,
    escalateTicket,
    saveEvidence,
    saveAssessment,
    claimRow,
    claimFromDispatch,
    runForceDistribute
  }
}
