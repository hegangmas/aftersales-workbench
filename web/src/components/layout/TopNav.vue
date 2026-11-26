<template>
  <header class="topbar">
    <div class="topbar-inner">
      <div class="topbar-brand">
        <BrandLogo variant="teal" alt="松果" width="90px" />
        <span class="topbar-brand-product">售后工单中台</span>
      </div>

      <nav class="topbar-nav" aria-label="一级导航">
        <button
          v-if="canSee(role, 'workbench')"
          type="button"
          class="top-link"
          :aria-current="navGroup === 'workbench' ? 'page' : undefined"
          @click="handleAction('open-workbench')"
        >工作台</button>

        <button
          v-if="canSee(role, 'tickets')"
          type="button"
          class="top-link"
          :aria-current="navGroup === 'tickets' ? 'page' : undefined"
          @click="handleAction('open-tickets')"
        >工单</button>

        <button
          v-if="canSee(role, 'device')"
          type="button"
          class="top-link"
          :aria-current="navGroup === 'device' ? 'page' : undefined"
          @click="navigateTo('device')"
        >设备</button>

        <button
          v-if="canSee(role, 'knowledge')"
          type="button"
          class="top-link"
          :aria-current="navGroup === 'knowledge' ? 'page' : undefined"
          @click="navigateTo('knowledge')"
        >知识库</button>

        <div v-if="canSeeAdminMenu" class="nav-dropdown" @click.stop>
          <button
            type="button"
            class="top-link"
            :class="{ active: navGroup === 'admin' }"
            :aria-expanded="adminMenuOpen"
            @click="toggleAdminMenu"
          >管理 ▾</button>
          <div v-if="adminMenuOpen" class="dropdown-panel" role="menu">
            <button
              v-for="item in visibleAdminMenus"
              :key="item.key"
              type="button"
              class="dropdown-item"
              :aria-current="view === item.key ? 'page' : undefined"
              @click="openAdminMenu(item.key)"
            >{{ item.label }}</button>
          </div>
        </div>
      </nav>

      <div class="topbar-right">
        <button
          v-if="canSee(role, 'due')"
          type="button"
          class="bell-btn icon-btn"
          title="到期回访提醒"
          aria-label="到期回访提醒"
          @click="openDueTeam"
        >
          <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M15 7a5 5 0 0 0-10 0c0 4.5-2 6-2 6h14s-2-1.5-2-6" />
            <path d="M8.5 16a1.7 1.7 0 0 0 3 0" />
          </svg>
          <span v-if="dueCount" class="icon-btn__dot" :title="`有 ${dueCount} 条到期回访`"></span>
        </button>

        <select
          v-if="canSee(role, 'duty-status')"
          :value="agentStatus"
          class="agent-status-select"
          :class="agentStatus"
          aria-label="在岗状态"
          @change="onDutyChange"
        >
          <option value="ready">● 就绪</option>
          <option value="break">● 休息</option>
          <option value="away">● 离岗</option>
        </select>

        <div class="nav-dropdown" @click.stop>
          <button type="button" class="top-link" @click="toggleUserMenu">
            <span class="avatar mini teal">{{ initial(account.username) }}</span>
            <span>{{ account.username }}</span>
            <span class="role-pill">{{ roleLabel(account.role) }}</span>
            <span>▾</span>
          </button>
          <div v-if="userMenuOpen" class="dropdown-panel" style="right:0;left:auto;">
            <button type="button" class="dropdown-item" @click="navigateTo('security')">安全与改密</button>
            <button type="button" class="dropdown-item" @click="handleSignOut">退出登录</button>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, ref } from 'vue'
import BrandLogo from '../BrandLogo.vue'
import { ADMIN_SUBMENUS, canSee } from '../../nav/menuConfig'
import { initial, roleLabel } from '../../status'

const props = defineProps({
  account: { type: Object, required: true },
  view: { type: String, required: true },
  navGroup: { type: String, default: '' },
  dueCount: { type: Number, default: 0 },
  agentStatus: { type: String, default: 'ready' }
})

const emit = defineEmits([
  'open-workbench',
  'open-tickets',
  'navigate',
  'open-admin',
  'open-due',
  'update:agentStatus',
  'save-duty',
  'sign-out'
])

const adminMenuOpen = ref(false)
const userMenuOpen = ref(false)

const role = computed(() => props.account?.role)
const canSeeAdminMenu = computed(() => canSee(role.value, 'admin'))
const visibleAdminMenus = computed(() => ADMIN_SUBMENUS.filter(item => canSee(role.value, item.key)))

function closeMenus() {
  adminMenuOpen.value = false
  userMenuOpen.value = false
}

function toggleAdminMenu() {
  adminMenuOpen.value = !adminMenuOpen.value
  userMenuOpen.value = false
}

function toggleUserMenu() {
  userMenuOpen.value = !userMenuOpen.value
  adminMenuOpen.value = false
}

function handleAction(eventName) {
  closeMenus()
  emit(eventName)
}

function navigateTo(nextView) {
  closeMenus()
  emit('navigate', nextView)
}

function openAdminMenu(key) {
  closeMenus()
  emit('open-admin', key)
}

function openDueTeam() {
  closeMenus()
  emit('open-due', 'team')
}

function onDutyChange(event) {
  const next = event.target.value
  emit('update:agentStatus', next)
  emit('save-duty', next)
}

function handleSignOut() {
  closeMenus()
  emit('sign-out')
}

defineExpose({ closeMenus })
</script>
