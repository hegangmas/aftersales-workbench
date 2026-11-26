<template>
  <!-- 同一张设备档案页，严格按 6 段纵向排列 -->
  <div class="profile-sections">
    <!-- ① 基本信息 -->
    <section class="panel section-block" aria-label="基本信息">
      <header class="section-header">
        <div class="section-title-group">
          <span class="section-index">1</span>
          <div>
            <h2>基本信息</h2>
            <p class="section-sub">设备身份、客户联系信息与实时在线报文</p>
          </div>
        </div>
        <button
          type="button"
          class="btn btn--primary create-main-btn"
          :disabled="isBusy"
          @click="$emit('create')"
        >
          为该设备建单
        </button>
      </header>

      <dl class="desc-grid">
        <div class="kv-item">
          <dt class="desc-grid__label">客户</dt>
          <dd class="desc-grid__value">{{ textOrDash(device.customerName) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">联系人</dt>
          <dd class="desc-grid__value">{{ textOrDash(device.contactName) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">联系电话</dt>
          <dd class="desc-grid__value mono">{{ textOrDash(device.customerPhone) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">桩 SN</dt>
          <dd class="desc-grid__value mono strong-val">{{ textOrDash(deviceSn) }}</dd>
        </div>
        <div class="kv-item kv-span-2">
          <dt class="desc-grid__label">安装地址</dt>
          <dd class="desc-grid__value">{{ textOrDash(device.siteAddress) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">型号</dt>
          <dd class="desc-grid__value">
            <span>{{ textOrDash(device.model) }}</span>
            <span v-if="device.productType" class="tag sub-tag">{{ device.productType }}</span>
          </dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">在线状态</dt>
          <dd class="desc-grid__value status-badges">
            <span class="badge badge--dot" :class="onlineTone(device.onlineStatus)">
              {{ textOrDash(device.onlineStatus) }}
            </span>
            <span v-if="device.currentStatus" class="badge badge--dot" :class="currentStatusTone(device.currentStatus)">
              {{ device.currentStatus }}
            </span>
          </dd>
        </div>
        <div class="kv-item kv-span-full">
          <dt class="desc-grid__label">实时报文</dt>
          <dd class="desc-grid__value telemetry-box">{{ textOrDash(device.realtimeTelemetry) }}</dd>
        </div>
      </dl>
    </section>

    <!-- ② 生产与安装 -->
    <section class="panel section-block" aria-label="生产与安装">
      <header class="section-header">
        <div class="section-title-group">
          <span class="section-index">2</span>
          <div>
            <h2>生产与安装</h2>
            <p class="section-sub">MES 生产追溯与售后安装验收档案</p>
          </div>
        </div>
        <div class="section-actions">
          <button
            type="button"
            class="btn btn--secondary btn--sm"
            @click="$emit('deepLink', { label: 'MES 原始批次深链', url: deepLinks.mesBatch || ('https://mes.internal/trace/' + deviceSn) })"
          >
            <span>查看 MES 原始批次</span>
            <svg class="ext-icon" viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M6 3h7v7" /><path d="M13 3L6 10" /><path d="M11 9v4H3V5h4" />
            </svg>
          </button>
          <button
            type="button"
            class="btn btn--secondary btn--sm"
            @click="$emit('deepLink', { label: '安装验收单深链', url: deepLinks.installOrder || ('https://lezhuang.internal/install/' + (device.projectCode || deviceSn)) })"
          >
            <span>查看安装验收单</span>
            <svg class="ext-icon" viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M6 3h7v7" /><path d="M13 3L6 10" /><path d="M11 9v4H3V5h4" />
            </svg>
          </button>
        </div>
      </header>

      <dl class="desc-grid">
        <div class="kv-item" :class="{ 'kv-degraded': isSourceDegraded('MES') && !device.manufacturedAt }">
          <dt class="desc-grid__label">出厂日期</dt>
          <dd class="desc-grid__value mono">{{ device.manufacturedAt || (isSourceDegraded('MES') ? '暂缺（MES 降级）' : '—') }}</dd>
        </div>
        <div class="kv-item" :class="{ 'kv-degraded': isSourceDegraded('MES') && !device.mesBatch }">
          <dt class="desc-grid__label">MES 批次</dt>
          <dd class="desc-grid__value mono">{{ device.mesBatch || (isSourceDegraded('MES') ? '暂缺（MES 降级）' : '—') }}</dd>
        </div>
        <div class="kv-item" :class="{ 'kv-degraded': isSourceDegraded('MES') }">
          <dt class="desc-grid__label">出厂检验结果</dt>
          <dd class="desc-grid__value">{{ textOrDash(device.mesTestResult) }}</dd>
        </div>
        <div class="kv-item" :class="{ 'kv-degraded': isSourceDegraded('安装') && !device.installedAt }">
          <dt class="desc-grid__label">安装日期</dt>
          <dd class="desc-grid__value mono">{{ device.installedAt || (isSourceDegraded('安装') ? '暂缺（安装系统降级）' : '—') }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">程序版本</dt>
          <dd class="desc-grid__value mono">{{ textOrDash(device.firmwareVersion) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">SIM 到期时间</dt>
          <dd class="desc-grid__value mono">{{ textOrDash(device.simExpiresAt) }}</dd>
        </div>
      </dl>
    </section>

    <!-- ③ 历史报修 -->
    <section class="panel section-block" aria-label="历史报修">
      <header class="section-header">
        <div class="section-title-group">
          <span class="section-index">3</span>
          <div>
            <h2>历史报修</h2>
            <p class="section-sub">同桩历次报修工单与未闭环防重核查（点击工单号可直达详情）</p>
          </div>
        </div>
        <span class="tag tabular count-chip">共 {{ relatedProblems.length }} 条</span>
      </header>

      <div v-if="openProblems.length" class="dup-banner" role="status">
        <div class="dup-banner-title">
          <svg class="dup-warn-icon" viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M8 2.2 14.2 13H1.8L8 2.2Z" />
            <path d="M8 6.4v3.2" />
            <circle cx="8" cy="11.4" r="0.5" fill="currentColor" stroke="none" />
          </svg>
          <span>同桩未闭环防重提示：当前有 {{ openProblems.length }} 条未闭环工单，请优先点击原工单号跟进，避免重复建单</span>
        </div>
        <div class="dup-links">
          <button
            v-for="item in openProblems"
            :key="item.id"
            type="button"
            class="tag dup-order-chip"
            @click="$emit('open', item.id)"
          >
            <span class="mono dup-order-no">{{ orderNumber(item) }}</span>
            <span class="dup-order-title">{{ item.title }}</span>
            <span class="badge badge--dot" :class="statusBadgeClass(displayStatus(item).tone)">{{ displayStatus(item).label }}</span>
          </button>
        </div>
      </div>

      <div v-if="relatedProblems.length" class="list-table">
        <div v-for="item in relatedProblems" :key="item.id" class="list-row">
          <div class="list-row-main">
            <button
              type="button"
              class="order-link-btn mono"
              @click="$emit('open', item.id)"
            >
              {{ orderNumber(item) }}
            </button>
            <span class="row-title">{{ item.title }}</span>
          </div>
          <div class="list-row-meta">
            <span class="badge badge--dot" :class="statusBadgeClass(displayStatus(item).tone)">
              {{ displayStatus(item).label }}
            </span>
            <span class="meta-owner">
              负责人：{{ item.owner && item.owner !== 'early' ? item.owner : '待分配' }}
            </span>
          </div>
        </div>
      </div>
      <p v-else class="empty-inline">该设备暂无历史报修工单</p>
    </section>

    <!-- ④ 历史维修 -->
    <section class="panel section-block" aria-label="历史维修">
      <header class="section-header">
        <div class="section-title-group">
          <span class="section-index">4</span>
          <div>
            <h2>历史维修</h2>
            <p class="section-sub">智享乐装现场维修与远程运维记录</p>
          </div>
        </div>
        <span
          class="tabular"
          :class="isSourceDegraded('乐装') ? 'badge badge--danger' : 'tag count-chip'"
        >
          {{ isSourceDegraded('乐装') ? '乐装降级' : '共 ' + repairHistory.length + ' 条' }}
        </span>
      </header>

      <div v-if="isSourceDegraded('乐装')" class="inline-alert error compact" role="status">
        智享乐装接口当前降级不可用，历史维修记录暂无法同步（已标注数据缺口）。
      </div>

      <div v-else-if="repairHistory.length" class="list-table">
        <div v-for="(item, idx) in repairHistory" :key="item.orderNo || idx" class="list-row">
          <div class="list-row-main">
            <span class="mono repair-no">{{ textOrDash(item.orderNo) }}</span>
            <span class="row-date tabular">{{ textOrDash(item.date) }}</span>
            <span class="row-title">{{ textOrDash(item.summary) }}</span>
          </div>
          <div class="list-row-meta">
            <span v-if="item.source" class="tag">{{ item.source }}</span>
            <button
              v-if="item.deepLink"
              type="button"
              class="btn btn--link repair-link-btn"
              @click="$emit('deepLink', { label: '维修单深链（' + (item.orderNo || '乐装') + '）', url: item.deepLink })"
            >
              <span>查看维修单</span>
              <svg class="ext-icon" viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M6 3h7v7" /><path d="M13 3L6 10" /><path d="M11 9v4H3V5h4" />
              </svg>
            </button>
          </div>
        </div>
      </div>
      <p v-else class="empty-inline">暂无历史维修记录</p>
    </section>

    <!-- ⑤ 质保结算 -->
    <section class="panel section-block" aria-label="质保结算">
      <header class="section-header">
        <div class="section-title-group">
          <span class="section-index">5</span>
          <div>
            <h2>质保结算</h2>
            <p class="section-sub">质保期、延保政策、所属项目与客户 SLA 时效</p>
          </div>
        </div>
        <span class="badge badge--dot" :class="warrantyTone(device.warrantyStatus)">
          {{ textOrDash(device.warrantyStatus) }}
        </span>
      </header>

      <dl class="desc-grid">
        <div class="kv-item">
          <dt class="desc-grid__label">质保状态</dt>
          <dd class="desc-grid__value">{{ textOrDash(device.warrantyStatus) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">质保至</dt>
          <dd class="desc-grid__value mono">{{ textOrDash(device.warrantyUntil) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">延保</dt>
          <dd class="desc-grid__value">{{ textOrDash(device.extendedWarranty) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">所属项目编号</dt>
          <dd class="desc-grid__value mono">{{ textOrDash(device.projectCode) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">项目名称</dt>
          <dd class="desc-grid__value">{{ textOrDash(device.projectName) }}</dd>
        </div>
        <div class="kv-item">
          <dt class="desc-grid__label">客户类型与 SLA 时效</dt>
          <dd class="desc-grid__value tabular">{{ customerTypeLabel(device.customerType) }} · {{ slaLabel(device.slaMinutes) }}</dd>
        </div>
      </dl>
    </section>

    <!-- ⑥ 关联工单 -->
    <section class="panel section-block" aria-label="关联工单与充电告警摘要">
      <header class="section-header">
        <div class="section-title-group">
          <span class="section-index">6</span>
          <div>
            <h2>关联工单</h2>
            <p class="section-sub">在途/已闭环工单汇总及最近充电与告警摘要</p>
          </div>
        </div>
        <button
          type="button"
          class="btn btn--secondary btn--sm"
          @click="$emit('deepLink', { label: '充电桩管理平台深链', url: deepLinks.devicePlatform || ('https://charger-platform.internal/devices/' + deviceSn) })"
        >
          <span>桩平台设备详情</span>
          <svg class="ext-icon" viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M6 3h7v7" /><path d="M13 3L6 10" /><path d="M11 9v4H3V5h4" />
          </svg>
        </button>
      </header>

      <div class="summary-kpis">
        <div class="inset kpi-tile">
          <span class="kpi-label">在途工单</span>
          <strong class="kpi-num" :class="openProblems.length ? 'accent' : 'is-zero'">{{ openProblems.length }}</strong>
        </div>
        <div class="inset kpi-tile">
          <span class="kpi-label">已闭环 / 已取消</span>
          <strong class="kpi-num" :class="{ 'is-zero': !closedProblems.length }">{{ closedProblems.length }}</strong>
        </div>
        <div class="inset kpi-tile">
          <span class="kpi-label">最近充电记录</span>
          <strong class="kpi-num" :class="{ 'is-zero': !chargingRecords.length }">{{ chargingRecords.length }}</strong>
        </div>
        <div class="inset kpi-tile">
          <span class="kpi-label">最近告警</span>
          <strong class="kpi-num" :class="recentAlerts.length ? 'danger' : 'is-zero'">{{ recentAlerts.length }}</strong>
        </div>
      </div>

      <div class="sub-columns">
        <div class="inset sub-card">
          <h3>工单快速跳转</h3>
          <div v-if="relatedProblems.length" class="quick-ticket-list">
            <button
              v-for="item in relatedProblems.slice(0, 6)"
              :key="item.id"
              type="button"
              class="quick-ticket-item"
              @click="$emit('open', item.id)"
            >
              <span class="mono order-link-text">{{ orderNumber(item) }}</span>
              <span class="clip-text">{{ item.title }}</span>
              <span class="badge badge--dot" :class="statusBadgeClass(displayStatus(item).tone)">{{ displayStatus(item).label }}</span>
            </button>
          </div>
          <p v-else class="empty-inline">暂无关联工单</p>
        </div>

        <div class="inset sub-card">
          <h3>最近充电记录</h3>
          <ul v-if="chargingRecords.length" class="compact-list">
            <li v-for="(rec, idx) in chargingRecords" :key="rec.orderNo || idx">
              <div class="compact-top">
                <span class="mono">{{ rec.orderNo }}</span>
                <span class="tag" :class="{ 'tag--warning': rec.status && rec.status.includes('中断') }">{{ rec.status }}</span>
              </div>
              <div class="compact-sub tabular">{{ rec.startedAt }} · {{ rec.energy }} · {{ rec.stopReason }}</div>
            </li>
          </ul>
          <p v-else class="empty-inline">暂无最近充电记录</p>
        </div>

        <div class="inset sub-card">
          <h3>最近告警摘要</h3>
          <ul v-if="recentAlerts.length" class="compact-list">
            <li v-for="(alert, idx) in recentAlerts" :key="alert.code || idx">
              <div class="compact-top">
                <strong class="alert-code mono">{{ alert.code }}</strong>
                <span class="badge badge--dot badge--danger">{{ alert.status || '告警' }}</span>
              </div>
              <div class="compact-sub tabular">{{ alert.name }} · {{ alert.reportedAt }}</div>
            </li>
          </ul>
          <p v-else class="empty-inline">暂无最近告警</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { displayStatus, orderNumber } from '../../status'
import { textOrDash } from './deviceShared'

const props = defineProps({
  device: { type: Object, required: true },
  deviceSn: { type: String, default: '' },
  isBusy: { type: Boolean, default: false },
  sourceStatuses: { type: Array, default: () => [] },
  relatedProblems: { type: Array, default: () => [] },
  repairHistory: { type: Array, default: () => [] },
  chargingRecords: { type: Array, default: () => [] },
  recentAlerts: { type: Array, default: () => [] },
  deepLinks: { type: Object, default: () => ({}) },
  openProblems: { type: Array, default: () => [] },
  closedProblems: { type: Array, default: () => [] }
})

defineEmits(['open', 'create', 'deepLink'])

function isSourceDegraded(keyword) {
  return props.sourceStatuses.some(src => src && !src.ok && String(src.name || '').includes(keyword))
}

function onlineTone(status) {
  const text = String(status || '')
  if (text.includes('在线')) return 'badge--success'
  if (text.includes('离线') || text.includes('故障')) return 'badge--danger'
  return ''
}

function currentStatusTone(status) {
  const text = String(status || '')
  if (text.includes('告警') || text.includes('故障') || text.includes('异常') || text.includes('离线')) {
    return 'badge--danger'
  }
  if (text.includes('充') || text.includes('待') || text.includes('维护')) {
    return 'badge--warning'
  }
  if (text.includes('正常') || text.includes('空闲')) {
    return 'badge--success'
  }
  return ''
}

function warrantyTone(status) {
  const text = String(status || '')
  if (text.includes('在保')) return 'badge--success'
  if (text.includes('过保')) return 'badge--danger'
  return 'badge--warning'
}

function statusBadgeClass(tone) {
  if (tone === 'processing' || tone === 'follow' || tone === 'onsite') return 'badge--brand'
  if (tone === 'waiting' || tone === 'dispatch' || tone === 'triage' || tone === 'verify') return 'badge--warning'
  if (tone === 'danger-soft') return 'badge--danger'
  return ''
}

function customerTypeLabel(type) {
  const map = {
    retail: '零售客户',
    enterprise: '政企 / 大客户',
    project: '工程项目客户',
    oem: '车企客户',
    operator: '运营商客户'
  }
  return map[type] || textOrDash(type)
}

function slaLabel(minutes) {
  const n = Number(minutes)
  if (!Number.isFinite(n) || n <= 0) return '默认 SLA'
  if (n >= 60 && n % 60 === 0) return `SLA ${n / 60} 小时（${n} 分钟）`
  return `SLA ${n} 分钟`
}
</script>

<style scoped>
/* 6 段纵向排列 */
.profile-sections {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.section-block {
  display: grid;
  gap: 14px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line, #eaeef2);
}

.section-title-group {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.section-index {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: #e8f6f4;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
  flex: none;
}

.section-title-group h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.35;
}

.section-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #64748b;
}

.section-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.count-chip {
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--canvas, #f5f7fa);
  border: 1px solid var(--line, #eaeef2);
  font-size: 12px;
  color: #475569;
  font-weight: 600;
}

.kv-item {
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
}

.kv-item.kv-span-2 {
  grid-column: span 2;
}

.kv-item.kv-span-full {
  grid-column: 1 / -1;
}

.kv-item.kv-degraded {
  padding: 6px 10px;
  border-radius: var(--radius-md, 8px);
  background: var(--danger-bg, #fdeeec);
}

.kv-item.kv-degraded dd {
  color: var(--danger-fg, #c0362c);
}

.kv-item dt {
  font-size: 12px;
  color: var(--text-tertiary, #7a8686);
  margin-bottom: 4px;
}

.kv-item dd {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-primary, #152020);
  overflow-wrap: anywhere;
}

.strong-val {
  color: var(--brand-700, #0f766e) !important;
  font-weight: 600 !important;
}

.telemetry-box {
  font-family: var(--font-mono, monospace);
  font-size: 13px !important;
  font-weight: 500 !important;
  color: var(--text-secondary, #4a5656) !important;
  padding: 8px 12px;
  border-radius: var(--radius-md, 8px);
  background: var(--bg-subtle, #f8fafa);
}

.sub-tag {
  display: inline-flex;
  align-items: center;
  margin-left: 6px;
  padding: 0 6px;
  height: 20px;
  border-radius: 4px;
  background: var(--neutral-bg, #f1f4f4);
  color: var(--text-secondary, #4a5656);
  font-size: 12px;
  font-weight: 500;
}

/* 防重提示与列表 */
.dup-banner {
  padding: 12px 14px;
  border-radius: 9px;
  background: #fff9ec;
  border: 1px solid #f5d08a;
  display: grid;
  gap: 8px;
}

.dup-banner-title {
  font-size: 13px;
  font-weight: 700;
  color: #9a5b00;
}

.dup-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dup-order-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 7px;
  border: 1px solid #efd29c;
  background: #fff;
  color: #0f172a;
  font-size: 12px;
  cursor: pointer;
}

.dup-order-chip:hover {
  border-color: #0d9488;
  background: #e8f6f4;
}

.list-table {
  display: grid;
}

.list-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--line, #eaeef2);
}

.list-row:last-child {
  border-bottom: 0;
  padding-bottom: 2px;
}

.list-row-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
  flex: 1 1 300px;
}

.list-row-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.order-link-btn {
  border: 0;
  background: transparent;
  padding: 0;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.order-link-btn:hover {
  color: #0d9488;
  text-decoration: underline;
}

.repair-no {
  font-weight: 700;
  color: #0f172a;
  font-size: 13px;
}

.row-date {
  font-size: 12px;
  color: #64748b;
}

.row-title {
  font-size: 13px;
  color: #334155;
  overflow-wrap: anywhere;
}

.meta-owner {
  font-size: 12px;
  color: #64748b;
}

.empty-inline {
  margin: 0;
  padding: 6px 0;
  font-size: 12px;
  color: var(--text-tertiary, #7a8686);
}

/* 第 6 段：关联工单与充电告警摘要 */
.summary-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kpi-tile {
  padding: 12px 14px;
  border-radius: var(--radius-lg, 10px);
  background: var(--bg-subtle, #f8fafa);
  border: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.kpi-label {
  font-size: 13px;
  color: var(--text-tertiary, #7a8686);
}

.kpi-num {
  font-family: var(--font-num, sans-serif);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary, #152020);
}

.kpi-num.accent {
  color: var(--brand-700, #0f766e);
}

.kpi-num.danger {
  color: var(--danger-fg, #c0362c);
}

.sub-columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.sub-card {
  padding: 12px 14px;
  border-radius: var(--radius-lg, 10px);
  border: 0;
  background: var(--bg-subtle, #f8fafa);
  min-width: 0;
}

.sub-card h3 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary, #4a5656);
}

.quick-ticket-list {
  display: grid;
  gap: 6px;
}

.quick-ticket-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--border-subtle, #edf0f0);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-surface, #ffffff);
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.quick-ticket-item:hover {
  border-color: var(--brand-200, #99dbd2);
  background: var(--brand-50, #e8f6f4);
}

.order-link-text {
  color: var(--brand-700, #0f766e);
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  font-weight: 600;
  flex: none;
}

.clip-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-primary, #152020);
}

.compact-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.compact-list li {
  padding: 8px 10px;
  border-radius: var(--radius-md, 8px);
  background: var(--bg-surface, #ffffff);
  border: 1px solid var(--border-subtle, #edf0f0);
}

.compact-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.alert-code {
  color: #c0362c;
}

.compact-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
  overflow-wrap: anywhere;
}

@media (max-width: 1024px) {
  .summary-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sub-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .summary-kpis {
    grid-template-columns: 1fr;
  }

  .kv-item.kv-span-2 {
    grid-column: span 1;
  }

  .create-main-btn {
    width: 100%;
  }
}
</style>
