<template>
  <div class="kpi-grid">
    <div class="kpi-card">
      <div class="kpi-head">
        <span class="kpi-label">今日工单</span>
        <span class="mock-badge">{{ hasSummaryTotal ? '实时统计' : '模拟' }}</span>
      </div>
      <div class="kpi-value" :class="{ 'is-zero': todayTotalDisplay === 0 }">{{ todayTotalDisplay }}</div>
      <p class="kpi-sub">
        {{ hasSummaryTotal ? '工作台全量累计工单池' : '未接入实时总量，展示演示基准' }}
      </p>
    </div>

    <button
      type="button"
      class="kpi-card kpi-interactive"
      :class="{ warn: unclaimedCountDisplay > 0 }"
      @click="$emit('open-unclaimed')"
    >
      <div class="kpi-head">
        <span class="kpi-label">待分配</span>
        <span class="mock-badge" :class="{ warn: unclaimedCountDisplay > 0 }">
          {{ hasUnclaimedSummary ? '待认领池' : '模拟' }}
        </span>
      </div>
      <div class="kpi-value" :class="{ warn: unclaimedCountDisplay > 0, 'is-zero': unclaimedCountDisplay === 0 }">
        {{ unclaimedCountDisplay }}
      </div>
      <p class="kpi-sub kpi-link">点击进入待认领池筛选 <span>→</span></p>
    </button>

    <div class="kpi-card" :class="{ danger: overdueCountDisplay > 0 }">
      <div class="kpi-head">
        <span class="kpi-label">处理中超时</span>
        <span class="mock-badge" :class="{ danger: overdueCountDisplay > 0 }">
          {{ hasSlaData ? 'SLA 实时' : '模拟' }}
        </span>
      </div>
      <div class="kpi-value" :class="{ danger: overdueCountDisplay > 0, 'is-zero': overdueCountDisplay === 0 }">
        {{ overdueCountDisplay }}
      </div>
      <p class="kpi-sub">
        {{ hasSlaData ? '依据当前工单列表 SLA 时限统计' : '未加载 SLA 时限，展示模拟值' }}
      </p>
    </div>

    <div class="kpi-card">
      <div class="kpi-head">
        <span class="kpi-label">妥协工单</span>
        <span class="mock-badge" :class="{ warn: compromiseCountDisplay > 0 }">
          {{ hasCompromiseData ? '实时统计' : '模拟' }}
        </span>
      </div>
      <div class="kpi-value" :class="{ 'is-zero': compromiseCountDisplay === 0 }">{{ compromiseCountDisplay }}</div>
      <p class="kpi-sub">
        {{ hasCompromiseData ? '标记为妥协/特殊处理的工单数' : '暂无妥协标记数据，展示模拟值' }}
      </p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  hasSummaryTotal: Boolean,
  todayTotalDisplay: { type: Number, default: 0 },
  hasUnclaimedSummary: Boolean,
  unclaimedCountDisplay: { type: Number, default: 0 },
  hasSlaData: Boolean,
  overdueCountDisplay: { type: Number, default: 0 },
  hasCompromiseData: Boolean,
  compromiseCountDisplay: { type: Number, default: 0 }
})
defineEmits(['open-unclaimed'])
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--gutter, 16px);
}

.kpi-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 112px;
  gap: 4px;
  padding: 16px 20px;
  border: 1px solid var(--border-default, #e2e7e7);
  border-radius: var(--radius-xl, 12px);
  background: var(--bg-surface, #ffffff);
  box-shadow: var(--elev-1);
  text-align: left;
  transition: border-color .15s ease, box-shadow .15s ease;
}

.kpi-interactive { cursor: pointer; }
.kpi-interactive:hover {
  border-color: var(--border-strong, #cfd7d7);
  box-shadow: var(--elev-2);
}
.kpi-card.warn {
  border-color: var(--warning-border, #f3d7a6);
  background: linear-gradient(180deg, #FFFAF1 0%, #FFFFFF 72%);
}
.kpi-card.danger {
  border-color: var(--danger-border, #f4c5c0);
  background: linear-gradient(180deg, #FFF8F7 0%, #FFFFFF 72%);
}

.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.kpi-label {
  color: var(--text-secondary, #4a5656);
  font-size: 13px;
  font-weight: 500;
}

.kpi-value {
  color: var(--text-primary, #152020);
  font-family: var(--font-num, sans-serif);
  font-size: 32px;
  line-height: 40px;
  font-weight: 600;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.kpi-value.is-zero { color: var(--text-disabled, #a8b2b2); }
.kpi-value.warn { color: var(--warning-fg, #a15c07); }
.kpi-value.danger { color: var(--danger-fg, #c0362c); }

.kpi-sub {
  margin: 0;
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  line-height: 1.45;
}

.kpi-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--brand-700, #0f766e);
  font-weight: 500;
}

.mock-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border: 0;
  border-radius: 4px;
  background: var(--neutral-bg, #f1f4f4);
  color: var(--text-tertiary, #7a8686);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.mock-badge.warn { background: var(--warning-bg, #fef5e6); color: var(--warning-fg, #a15c07); }
.mock-badge.danger { background: var(--danger-bg, #fdeeec); color: var(--danger-fg, #c0362c); }

@media (max-width: 1100px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 720px) {
  .kpi-grid { grid-template-columns: 1fr; }
}
</style>
