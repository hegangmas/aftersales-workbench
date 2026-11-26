<template>
  <section class="dash-card agent-card" aria-label="坐席在岗状态">
    <header class="card-header">
      <div>
        <h2>坐席在岗状态</h2>
        <p class="card-sub">
          {{ hasRealAssignees ? '点击右侧胶囊切换状态（就绪 → 休息 → 离岗）' : '未加载账号列表，展示模拟坐席（可点击切换演示）' }}
        </p>
      </div>
      <span class="duty-ratio-badge">{{ onDutyCount }} / {{ agentRows.length }} 在岗</span>
    </header>

    <ul class="agent-list">
      <li v-for="agent in agentRows" :key="agent.username" class="agent-row">
        <div class="agent-main">
          <span class="agent-avatar" :class="agent.tone">{{ agent.initial }}</span>
          <div class="agent-info">
            <div class="agent-name-line">
              <strong>{{ agent.username }}</strong>
              <span class="role-tag">{{ agent.roleLabel }}</span>
            </div>
            <span class="agent-duration">{{ agent.durationText }}</span>
          </div>
        </div>
        <button
          type="button"
          class="presence-pill"
          :class="agent.status"
          :disabled="busy || updatingAgent === agent.username"
          :title="'点击切换在岗状态（当前：' + agent.statusLabel + '）'"
          @click="$emit('cycle-duty', agent)"
        >
          <i></i>{{ agent.statusLabel }}
        </button>
      </li>
    </ul>

    <p v-if="dutyError" role="alert" class="duty-error">{{ dutyError }}</p>

    <div class="agent-footer">
      <button
        type="button"
        class="assign-btn"
        :disabled="busy"
        @click="$emit('force-distribute')"
      >
        一键强制平均分配
      </button>
      <p v-if="assignNotice" role="status" class="assign-notice">
        {{ assignNotice }}
      </p>
    </div>
  </section>
</template>

<script setup>
defineProps({
  hasRealAssignees: Boolean,
  onDutyCount: { type: Number, default: 0 },
  agentRows: { type: Array, default: () => [] },
  busy: Boolean,
  updatingAgent: { type: String, default: '' },
  dutyError: { type: String, default: '' },
  assignNotice: { type: String, default: '' }
})
defineEmits(['cycle-duty', 'force-distribute'])
</script>

<style scoped>
.dash-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 20px;
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  background: var(--bg-surface, #ffffff);
  box-shadow: var(--elev-1);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.card-header h2 {
  margin: 0;
  color: var(--text-primary, #152020);
  font-size: 15px;
  font-weight: 600;
}

.card-sub {
  margin: 2px 0 0;
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
}

.duty-ratio-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border: 1px solid var(--brand-100, #ccede8);
  border-radius: 999px;
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.agent-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.agent-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 56px;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid var(--border-subtle, #edf0f0);
  background: transparent;
}
.agent-row:last-child { border-bottom: 0; }

.agent-main { display: flex; align-items: center; gap: 10px; min-width: 0; }
.agent-info { min-width: 0; }

.agent-avatar {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
}
.agent-avatar.teal { background: var(--avatar-1-bg, #e8f6f4); color: var(--avatar-1-fg, #0f766e); }
.agent-avatar.blue { background: var(--avatar-2-bg, #edf1f5); color: var(--avatar-2-fg, #45607a); }
.agent-avatar.purple { background: var(--avatar-3-bg, #f5f1e8); color: var(--avatar-3-fg, #7a5c2e); }
.agent-avatar.pink { background: var(--avatar-4-bg, #eef3ec); color: var(--avatar-4-fg, #4c6b45); }

.agent-name-line { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.agent-name-line strong { color: var(--text-primary, #152020); font-size: 13px; font-weight: 600; }

.role-tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--neutral-bg, #f1f4f4);
  color: var(--text-secondary, #4a5656);
  font-size: 12px;
  font-weight: 500;
}

.agent-duration {
  display: block;
  margin-top: 2px;
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.presence-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  height: 26px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color .15s ease, background .15s ease;
}
.presence-pill:hover:not(:disabled) { border-color: currentColor; }
.presence-pill:disabled { opacity: .6; cursor: not-allowed; }
.presence-pill i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.presence-pill.ready { background: var(--success-bg, #eaf6ef); border-color: var(--success-border, #bfe3ce); color: var(--success-fg, #18794e); }
.presence-pill.break { background: var(--warning-bg, #fef5e6); border-color: var(--warning-border, #f3d7a6); color: var(--warning-fg, #a15c07); }
.presence-pill.away { background: var(--neutral-bg, #f1f4f4); border-color: var(--neutral-border, #e2e7e7); color: var(--neutral-fg, #4a5656); }

.duty-error {
  margin: 8px 0 0;
  padding: 8px 10px;
  border: 1px solid var(--danger-border, #f4c5c0);
  border-radius: 8px;
  background: var(--danger-bg, #fdeeec);
  color: var(--danger-fg, #c0362c);
  font-size: 12px;
}

.agent-footer { margin-top: auto; padding-top: 12px; }

.assign-btn {
  width: 100%;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--border-strong, #cfd7d7);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-surface, #ffffff);
  color: var(--text-primary, #152020);
  font-size: 13px;
  font-weight: 500;
  box-shadow: var(--elev-1);
  transition: background .15s ease, border-color .15s ease;
}
.assign-btn:hover:not(:disabled) { background: var(--bg-subtle, #f8fafa); border-color: var(--border-hover, #b5c0c0); }
.assign-btn:disabled { opacity: .55; cursor: not-allowed; }

.assign-notice {
  margin: 8px 0 0;
  padding: 8px 10px;
  border: 1px solid var(--brand-100, #ccede8);
  border-radius: 8px;
  background: var(--brand-50, #e8f6f4);
  color: var(--brand-700, #0f766e);
  font-size: 12px;
  line-height: 1.5;
}
</style>
