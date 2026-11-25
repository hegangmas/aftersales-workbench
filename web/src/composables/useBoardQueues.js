import { ref } from 'vue'
import { dueCallbacks, mineProblems, searchProblems } from '../api/workbench'

export function emptyFilters() {
  return { q: '', state: '', owner: '', kind: '', category: '', bucket: '' }
}

export function useBoardQueues(pageSize = 10) {
  const boardItems = ref([])
  const boardTotal = ref(0)
  const boardPage = ref(0)
  const boardHasNext = ref(false)
  const boardCategories = ref([])
  const boardFilters = ref(emptyFilters())
  const boardCursors = ref([null])
  const summary = ref(null)

  const dueScope = ref('team')
  const dueItems = ref([])
  const dueTotal = ref(0)
  const dueNext = ref(null)
  const dueLoaded = ref(false)
  const dueStale = ref(false)

  const mineItems = ref([])
  const mineTotal = ref(0)
  const mineNext = ref(null)
  const mineLoaded = ref(false)
  const mineStale = ref(false)

  async function loadBoard() {
    const cursor = boardCursors.value[boardPage.value]
    const page = await searchProblems({ ...boardFilters.value, cursor, limit: pageSize })
    boardItems.value = page.items || []
    boardTotal.value = Number(page.total || 0)
    boardHasNext.value = Boolean(page.nextCursor)
    boardCategories.value = page.categories || []
    if (page.summary) summary.value = page.summary
    if (page.nextCursor) boardCursors.value[boardPage.value + 1] = page.nextCursor
  }

  async function loadDue(append) {
    const page = await dueCallbacks(dueScope.value, append ? dueNext.value : null)
    const items = page.items || []
    dueItems.value = append ? dueItems.value.concat(items) : items
    dueTotal.value = Number(page.total ?? dueItems.value.length)
    dueNext.value = page.nextCursor || null
    dueLoaded.value = true
    dueStale.value = false
  }

  async function loadMine(append) {
    const page = await mineProblems(append ? mineNext.value : null)
    const items = page.items || []
    mineItems.value = append ? mineItems.value.concat(items) : items
    mineTotal.value = Number(page.total ?? mineItems.value.length)
    mineNext.value = page.nextCursor || null
    mineLoaded.value = true
    mineStale.value = false
  }

  async function refreshQueues(activeView) {
    const tasks = [loadBoard()]
    if (activeView === 'due' && dueLoaded.value) tasks.push(loadDue(false))
    else if (dueLoaded.value) dueStale.value = true
    if (activeView === 'mine' && mineLoaded.value) tasks.push(loadMine(false))
    else if (mineLoaded.value) mineStale.value = true
    await Promise.all(tasks)
  }

  async function ensureFreshQueue(viewName) {
    if (viewName === 'due' && dueStale.value) await loadDue(false)
    if (viewName === 'mine' && mineStale.value) await loadMine(false)
  }

  function resetQueues() {
    boardItems.value = []
    boardTotal.value = 0
    boardFilters.value = emptyFilters()
    boardPage.value = 0
    boardCursors.value = [null]
    summary.value = null
    dueItems.value = []
    dueTotal.value = 0
    dueNext.value = null
    dueLoaded.value = false
    dueStale.value = false
    mineItems.value = []
    mineTotal.value = 0
    mineNext.value = null
    mineLoaded.value = false
    mineStale.value = false
  }

  return {
    boardItems, boardTotal, boardPage, boardHasNext, boardCategories, boardFilters, boardCursors, summary,
    dueScope, dueItems, dueTotal, dueNext, dueLoaded,
    mineItems, mineTotal, mineNext, mineLoaded,
    loadBoard, loadDue, loadMine, refreshQueues, ensureFreshQueue, resetQueues
  }
}
