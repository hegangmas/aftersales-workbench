<template>
  <section class="panel">
    <div class="fig2-card-head">
      <h2>指标与报表（V1.1 4.11 六项月度指标）</h2>
      <button class="btn secondary" type="button" @click="$emit('refresh')">刷新指标</button>
    </div>
    <div v-if="monthlyReport" class="evidence-thumbs" style="margin-top:12px;">
      <div class="evidence-thumb">
        <span class="thumb-badge">1. 工单总量 / 漏单率</span>
        <strong>{{ monthlyReport.totalTickets }} 单</strong>
        <small>漏单率：{{ monthlyReport.leakRateSimulated }}</small>
      </div>
      <div class="evidence-thumb">
        <span class="thumb-badge">2. 平均受理时长</span>
        <strong>{{ monthlyReport.avgAcceptMinutesSimulated }} 分钟</strong>
        <small>模拟节点均值</small>
      </div>
      <div class="evidence-thumb">
        <span class="thumb-badge">3. SLA 达成率</span>
        <strong>{{ monthlyReport.slaPassRate }}</strong>
        <small>当前超时：{{ monthlyReport.slaOverdueCount }} 单</small>
      </div>
      <div class="evidence-thumb">
        <span class="thumb-badge">4. 换装率 / 误判率</span>
        <strong>{{ monthlyReport.replaceRate }}</strong>
        <small>误判率：{{ monthlyReport.misjudgeRateSimulated }}</small>
      </div>
      <div class="evidence-thumb">
        <span class="thumb-badge">5. 妥协工单占比</span>
        <strong>{{ monthlyReport.compromiseRate }}</strong>
        <small>设备待补：{{ monthlyReport.devicePendingCount }} 单</small>
      </div>
      <div class="evidence-thumb">
        <span class="thumb-badge">6. 催单与升级投诉</span>
        <strong>{{ monthlyReport.urgeAndComplaintSimulated }} 次</strong>
        <small>含旁路升级记录</small>
      </div>
    </div>
    <p v-else class="empty-row">正在加载月度指标…</p>
  </section>
</template>

<script setup>
defineProps({
  monthlyReport: { type: Object, default: null }
})
defineEmits(['refresh'])
</script>
