import { describe, expect, it } from 'vitest'
import {
  ADMIN_MENU_ROLES,
  ADMIN_SUBMENUS,
  ALL_ROLES,
  EXTERNAL_ROLES,
  INTERNAL_ROLES,
  PRIMARY_MENUS,
  ROLE_VISIBILITY,
  TICKET_TABS,
  canSee,
  defaultTicketTabForRole,
  guardViewForRole
} from './menuConfig'

describe('menuConfig structure invariants (AGENTS.md V1.1)', () => {
  it('declares fixed 5 primary menus, 3 ticket tabs, 4 admin submenus, and 7 roles', () => {
    expect(PRIMARY_MENUS.map(item => item.label)).toEqual([
      '工作台',
      '工单',
      '设备',
      '知识库',
      '管理'
    ])
    expect(TICKET_TABS.map(item => item.label)).toEqual([
      '调度大屏',
      '统一工单池',
      '我的待办'
    ])
    expect(ADMIN_SUBMENUS.map(item => item.label)).toEqual([
      '客户与项目',
      'SOP 与规则',
      '指标与报表',
      '权限与配置'
    ])
    expect(ALL_ROLES).toEqual([
      'admin',
      'dispatcher',
      'operator',
      'engineer',
      'quality',
      'provider',
      'oem'
    ])
    expect(Object.keys(ROLE_VISIBILITY).sort()).toEqual([...ALL_ROLES].sort())
  })
})

describe('7-role visibility matrix', () => {
  it('allows internal roles to see workbench, device, dispatch, ticket-create, and internal controls', () => {
    for (const role of INTERNAL_ROLES) {
      expect(canSee(role, 'workbench')).toBe(true)
      expect(canSee(role, 'tickets')).toBe(true)
      expect(canSee(role, 'device')).toBe(true)
      expect(canSee(role, 'knowledge')).toBe(true)
      expect(canSee(role, 'dispatch')).toBe(true)
      expect(canSee(role, 'pool')).toBe(true)
      expect(canSee(role, 'mine')).toBe(true)
      expect(canSee(role, 'ticket-create')).toBe(true)
      expect(canSee(role, 'due')).toBe(true)
      expect(canSee(role, 'duty-status')).toBe(true)
      expect(canSee(role, 'internal')).toBe(true)
    }
  })

  it('strictly hides workbench, device, admin, dispatch, +新建工单 and internal controls from provider and oem', () => {
    for (const role of EXTERNAL_ROLES) {
      expect(canSee(role, 'tickets')).toBe(true)
      expect(canSee(role, 'pool')).toBe(true)
      expect(canSee(role, 'mine')).toBe(true)
      expect(canSee(role, 'knowledge')).toBe(true)
      expect(canSee(role, 'security')).toBe(true)

      expect(canSee(role, 'workbench')).toBe(false)
      expect(canSee(role, 'device')).toBe(false)
      expect(canSee(role, 'admin')).toBe(false)
      expect(canSee(role, 'admin-customers')).toBe(false)
      expect(canSee(role, 'admin-sop')).toBe(false)
      expect(canSee(role, 'admin-reports')).toBe(false)
      expect(canSee(role, 'admin-accounts')).toBe(false)
      expect(canSee(role, 'dispatch')).toBe(false)
      expect(canSee(role, 'ticket-create')).toBe(false)
      expect(canSee(role, 'due')).toBe(false)
      expect(canSee(role, 'duty-status')).toBe(false)
      expect(canSee(role, 'internal')).toBe(false)
    }
  })

  it('restricts admin menu to admin/dispatcher/quality and admin-accounts strictly to admin', () => {
    for (const role of ALL_ROLES) {
      const expectsAdminMenu = ADMIN_MENU_ROLES.includes(role)
      expect(canSee(role, 'admin')).toBe(expectsAdminMenu)
      expect(canSee(role, 'admin-customers')).toBe(expectsAdminMenu)
      expect(canSee(role, 'admin-sop')).toBe(expectsAdminMenu)
      expect(canSee(role, 'admin-reports')).toBe(expectsAdminMenu)
      expect(canSee(role, 'admin-accounts')).toBe(role === 'admin')
    }
  })

  it('returns false for missing or unknown roles/keys', () => {
    expect(canSee(undefined, 'tickets')).toBe(false)
    expect(canSee(null, 'tickets')).toBe(false)
    expect(canSee('unknown', 'tickets')).toBe(false)
    expect(canSee('admin', '')).toBe(false)
    expect(canSee('admin', 'nonexistent')).toBe(false)
  })
})

describe('defaultTicketTabForRole & guardViewForRole', () => {
  it('picks expected default ticket tab per role', () => {
    expect(defaultTicketTabForRole('dispatcher')).toBe('dispatch')
    expect(defaultTicketTabForRole('engineer')).toBe('mine')
    expect(defaultTicketTabForRole('provider')).toBe('mine')
    expect(defaultTicketTabForRole('admin')).toBe('pool')
    expect(defaultTicketTabForRole('operator')).toBe('pool')
    expect(defaultTicketTabForRole('quality')).toBe('pool')
    expect(defaultTicketTabForRole('oem')).toBe('pool')
  })

  it('guards restricted views back to tickets', () => {
    expect(guardViewForRole('provider', 'device')).toBe('tickets')
    expect(guardViewForRole('oem', 'due')).toBe('tickets')
    expect(guardViewForRole('oem', 'admin-sop')).toBe('tickets')
    expect(guardViewForRole('operator', 'admin-customers')).toBe('tickets')
    expect(guardViewForRole('engineer', 'admin-reports')).toBe('tickets')
    expect(guardViewForRole('dispatcher', 'admin-accounts')).toBe('tickets')
    expect(guardViewForRole('quality', 'admin-accounts')).toBe('tickets')
    expect(guardViewForRole('dispatcher', 'admin-sop')).toBe('admin-sop')
    expect(guardViewForRole('quality', 'admin-reports')).toBe('admin-reports')
    expect(guardViewForRole('admin', 'admin-accounts')).toBe('admin-accounts')
    expect(guardViewForRole('oem', 'knowledge')).toBe('knowledge')
  })
})
