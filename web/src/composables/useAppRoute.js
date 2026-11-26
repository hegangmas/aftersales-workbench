import { computed, ref } from 'vue'
import { emptyFilters } from './useBoardQueues'
import {
  canSee,
  defaultTicketTabForRole,
  guardViewForRole as guardRoleView
} from '../nav/menuConfig'
import { orderNumber } from '../status'

const VALID_VIEWS = [
  'tickets',
  'device',
  'knowledge',
  'due',
  'security',
  'admin-customers',
  'admin-sop',
  'admin-reports',
  'admin-accounts'
]

export function useAppRoute({
  account,
  role,
  problem,
  boardItems,
  boardFilters,
  boardCursors,
  boardPage,
  dueScope,
  run,
  closeMenus,
  loadBoard,
  loadMine,
  loadDue,
  loadCustomersList,
  loadMonthlyReport,
  loadAccounts,
  refreshAssignees,
  showProblem,
  openProblem
}) {
  const view = ref('tickets')
  const ticketTab = ref('pool')
  const devicePrefill = ref('')

  const navGroup = computed(() => {
    if (view.value === 'detail') return 'workbench'
    if (view.value === 'tickets' || view.value === 'due') return 'tickets'
    if (view.value === 'device') return 'device'
    if (view.value === 'knowledge') return 'knowledge'
    if (view.value.startsWith('admin-')) return 'admin'
    return ''
  })

  function guardViewForRole(reqView) {
    return guardRoleView(role.value, reqView)
  }

  function applyDefaultTicketTab() {
    ticketTab.value = defaultTicketTabForRole(account.value?.role)
  }

  function resetRoute() {
    view.value = 'tickets'
  }

  function syncUrl(push = false) {
    if (!account.value) {
      history.replaceState(null, '', location.pathname)
      return
    }
    const params = new URLSearchParams()
    if (view.value === 'detail' && problem.value) {
      params.set('problem', orderNumber(problem.value))
    } else {
      params.set('view', view.value)
      if (view.value === 'tickets') params.set('tab', ticketTab.value)
    }
    const target = '?' + params.toString()
    if (location.search === target) return
    if (push) history.pushState(null, '', target)
    else history.replaceState(null, '', target)
  }

  async function restoreRoute() {
    const params = new URLSearchParams(location.search)
    const problemId = params.get('problem')
    if (problemId) {
      await showProblem(problemId, { fromPopState: true })
      return
    }
    const reqView = params.get('view')
    if (reqView && VALID_VIEWS.includes(reqView)) {
      view.value = guardViewForRole(reqView)
    } else {
      view.value = 'tickets'
    }
    if (view.value === 'tickets') {
      const t = params.get('tab')
      if (['dispatch', 'pool', 'mine'].includes(t) && canSee(role.value, t)) {
        ticketTab.value = t
      }
      if (ticketTab.value === 'mine') await loadMine(false)
    } else if (view.value === 'admin-customers') {
      await loadCustomersList()
    } else if (view.value === 'admin-reports') {
      await loadMonthlyReport()
    }
    syncUrl(false)
  }

  function handlePopState() {
    if (account.value) run(restoreRoute)
  }

  function openWorkbenchTab() {
    closeMenus()
    if (problem.value) {
      view.value = 'detail'
      syncUrl(true)
      return
    }
    if (boardItems.value.length) {
      openProblem(boardItems.value[0].id)
      return
    }
    view.value = 'detail'
    syncUrl(true)
  }

  function openTicketsRoot() {
    closeMenus()
    view.value = 'tickets'
    syncUrl(true)
    run(async () => {
      await loadBoard()
      if (ticketTab.value === 'mine') await loadMine(false)
    })
  }

  function switchTicketTab(tab) {
    ticketTab.value = tab
    syncUrl(true)
    if (tab === 'mine') run(() => loadMine(false))
    else run(() => loadBoard())
  }

  function openPoolWithBucket(bucket) {
    view.value = 'tickets'
    ticketTab.value = 'pool'
    boardFilters.value = { ...emptyFilters(), bucket: bucket || '' }
    boardCursors.value = [null]
    boardPage.value = 0
    syncUrl(true)
    return run(loadBoard)
  }

  function showNav(next) {
    closeMenus()
    view.value = guardViewForRole(next)
    syncUrl(true)
  }

  function showAdmin(next) {
    closeMenus()
    view.value = guardViewForRole(next)
    syncUrl(true)
    if (view.value === 'admin-customers') run(loadCustomersList)
    else if (view.value === 'admin-reports') run(loadMonthlyReport)
    else if (view.value === 'admin-accounts') {
      run(async () => {
        await loadAccounts()
        await refreshAssignees()
      })
    }
  }

  function jumpToDevice(sn) {
    devicePrefill.value = sn || ''
    showNav('device')
  }

  function openDue(scope) {
    closeMenus()
    dueScope.value = scope === 'mine' ? 'mine' : 'team'
    view.value = 'due'
    syncUrl()
    return run(() => loadDue(false))
  }

  return {
    view,
    ticketTab,
    devicePrefill,
    navGroup,
    guardViewForRole,
    applyDefaultTicketTab,
    resetRoute,
    syncUrl,
    restoreRoute,
    handlePopState,
    openWorkbenchTab,
    openTicketsRoot,
    switchTicketTab,
    openPoolWithBucket,
    showNav,
    showAdmin,
    jumpToDevice,
    openDue
  }
}
