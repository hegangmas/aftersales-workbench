export const ALL_ROLES = [
  'admin',
  'dispatcher',
  'operator',
  'engineer',
  'quality',
  'provider',
  'oem'
]

export const INTERNAL_ROLES = ['admin', 'dispatcher', 'operator', 'engineer', 'quality']
export const EXTERNAL_ROLES = ['provider', 'oem']
export const ADMIN_MENU_ROLES = ['admin', 'dispatcher', 'quality']

export const PRIMARY_MENUS = [
  { key: 'workbench', label: '工作台' },
  { key: 'tickets', label: '工单' },
  { key: 'device', label: '设备' },
  { key: 'knowledge', label: '知识库' },
  { key: 'admin', label: '管理' }
]

export const TICKET_TABS = [
  { key: 'dispatch', label: '调度大屏' },
  { key: 'pool', label: '统一工单池' },
  { key: 'mine', label: '我的待办' }
]

export const ADMIN_SUBMENUS = [
  { key: 'admin-customers', label: '客户与项目' },
  { key: 'admin-sop', label: 'SOP 与规则' },
  { key: 'admin-reports', label: '指标与报表' },
  { key: 'admin-accounts', label: '权限与配置' }
]

const COMMON_EXTERNAL_KEYS = ['tickets', 'pool', 'mine', 'knowledge', 'security']
const COMMON_INTERNAL_KEYS = [
  ...COMMON_EXTERNAL_KEYS,
  'internal',
  'workbench',
  'device',
  'dispatch',
  'ticket-create',
  'due',
  'duty-status'
]
const MANAGER_SUBMENU_KEYS = ['admin', 'admin-customers', 'admin-sop', 'admin-reports']

export const ROLE_VISIBILITY = {
  admin: [...COMMON_INTERNAL_KEYS, ...MANAGER_SUBMENU_KEYS, 'admin-accounts'],
  dispatcher: [...COMMON_INTERNAL_KEYS, ...MANAGER_SUBMENU_KEYS],
  operator: [...COMMON_INTERNAL_KEYS],
  engineer: [...COMMON_INTERNAL_KEYS],
  quality: [...COMMON_INTERNAL_KEYS, ...MANAGER_SUBMENU_KEYS],
  provider: [...COMMON_EXTERNAL_KEYS],
  oem: [...COMMON_EXTERNAL_KEYS]
}

export function canSee(role, key) {
  if (!role || !key) return false
  const allowed = ROLE_VISIBILITY[role]
  if (!allowed) return false
  return allowed.includes(key)
}

export function defaultTicketTabForRole(role) {
  if (role === 'dispatcher') return 'dispatch'
  if (role === 'engineer' || role === 'provider') return 'mine'
  return 'pool'
}

export function guardViewForRole(role, reqView) {
  if (!reqView) return 'tickets'
  if (reqView === 'tickets' || reqView === 'knowledge' || reqView === 'security') {
    return reqView
  }
  if (canSee(role, reqView)) return reqView
  return 'tickets'
}
