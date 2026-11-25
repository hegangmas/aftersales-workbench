<template>
  <section class="device-lookup-page" aria-label="设备档案与建单">
    <!-- 顶部唯一搜索区 + 单源降级模拟 + 入口 B 触发 -->
    <section class="panel search-panel" aria-label="设备检索">
      <form class="search-bar" @submit.prevent="runSearch">
        <div class="search-input-wrap">
          <label class="sr-only" for="device-lookup-query">设备检索</label>
          <svg class="search-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
          </svg>
          <input
            id="device-lookup-query"
            v-model="query"
            type="search"
            class="search-input"
            placeholder="输入桩 SN、手机号或项目编号（如 CD26080101 / 13800001234 / PRJ-HF-2026-01 / SIM-AC-001）"
            autocomplete="off"
          >
        </div>

        <div class="degrade-select-wrap">
          <label class="degrade-label" for="fail-source-select">模拟单源降级</label>
          <select
            id="fail-source-select"
            v-model="failSource"
            class="degrade-select"
            aria-label="模拟单源降级"
            @change="onFailSourceChange"
          >
            <option value="">默认正常</option>
            <option value="mes">模拟 MES 超时</option>
            <option value="install">模拟安装不可用</option>
            <option value="400">模拟 400 不可用</option>
            <option value="lezhuang">模拟乐装不可用</option>
          </select>
        </div>

        <button
          type="submit"
          class="btn primary search-submit"
          :disabled="isBusy || !query.trim()"
        >
          {{ searching ? '检索中…' : '查询设备' }}
        </button>
      </form>

      <!-- 入口 B：搜索区下方唯一的「设备信息待补」例外建单入口 -->
      <div class="exception-entry-bar">
        <span class="exception-hint">未带桩号或客户现场无法读取铭牌时，可先走待补例外队列：</span>
        <button
          type="button"
          class="btn secondary exception-trigger"
          :aria-expanded="pendingOpen"
          :disabled="isBusy"
          @click="togglePendingForm"
        >
          {{ pendingOpen ? '收起待补建单表单' : '客户未提供桩信息？创建『设备信息待补』工单' }}
        </button>
      </div>

      <!-- 入口 B 展开表单 -->
      <section v-if="pendingOpen" class="pending-form-card" aria-label="设备信息待补建单表单">
        <div class="pending-alert" role="alert">
          <strong>例外建单提醒：</strong>将强制标记「设备信息待补」并进入待补队列，未补齐前不得派单、不得换装
        </div>

        <form class="v11-form" @submit.prevent="submitPendingOrder">
          <div class="form-grid-2">
            <div class="field-group">
              <label for="pending-channel">报修渠道 <span class="req">*</span></label>
              <select id="pending-channel" v-model="pendingForm.channel" class="form-control" required>
                <option v-for="opt in CHANNEL_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>

            <div class="field-group">
              <label for="pending-category">故障大类 <span class="req">*</span></label>
              <select id="pending-category" v-model="pendingForm.category" class="form-control" required>
                <option v-for="cat in CATEGORY_OPTIONS" :key="cat" :value="cat">{{ cat }}</option>
              </select>
            </div>

            <div class="field-group">
              <label for="pending-urgency">紧急度 <span class="req">*</span></label>
              <select id="pending-urgency" v-model="pendingForm.urgency" class="form-control" required>
                <option v-for="urg in URGENCY_OPTIONS" :key="urg.value" :value="urg.value">{{ urg.label }}</option>
              </select>
            </div>

            <div class="field-group">
              <label for="pending-occurred">故障发生时点（北京时间） <span class="req">*</span></label>
              <input
                id="pending-occurred"
                v-model="pendingForm.faultOccurredAt"
                type="datetime-local"
                class="form-control"
                required
              >
            </div>
          </div>

          <div class="field-group">
            <label for="pending-title">故障现象标题 <span class="req">*</span></label>
            <input
              id="pending-title"
              v-model="pendingForm.title"
              type="text"
              class="form-control"
              maxlength="150"
              placeholder="例如：车主反馈插枪无反应，暂未提供桩 SN"
              required
            >
          </div>

          <div class="field-group">
            <label for="pending-desc">现象描述 <span class="req">*</span></label>
            <textarea
              id="pending-desc"
              v-model="pendingForm.description"
              class="form-control form-textarea"
              maxlength="1000"
              placeholder="请记录故障表现、客户姓名/联系方式及大致安装位置线索，便于后续核实补齐设备档案"
              required
            ></textarea>
          </div>

          <p v-if="pendingError" class="form-error" role="alert">{{ pendingError }}</p>

          <div class="form-actions">
            <button type="button" class="btn secondary" :disabled="isBusy" @click="pendingOpen = false">取消</button>
            <button type="submit" class="btn primary" :disabled="isBusy || !canSubmitPending">
              {{ submitting ? '提交中…' : '提交「设备信息待补」工单' }}
            </button>
          </div>
        </form>
      </section>

      <!-- 查询出错提示 -->
      <div v-if="searchError" class="inline-alert error" role="alert">
        <div>
          <strong>未检索到设备档案：</strong>{{ searchError }}
        </div>
        <button
          v-if="!pendingOpen"
          type="button"
          class="btn-link-danger"
          @click="openPendingForm"
        >
          转为创建「设备信息待补」工单 →
        </button>
      </div>

      <!-- 4 源并行状态条（查询后展示） -->
      <section v-if="device" class="sources-strip" aria-label="4 源并行同步状态">
        <div class="sources-head">
          <span class="sources-title">4 源并行聚合状态</span>
          <span class="sources-meta">
            {{ device.source || '4源并行聚合' }} · 观测时间 {{ dateLabel(device.observedAt) }}
            <template v-if="Number(device.matchCount || 0) > 1">
              · 共匹配 {{ device.matchCount }} 台，当前展示首条
            </template>
          </span>
        </div>
        <div class="sources-grid">
          <div
            v-for="src in sourceStatuses"
            :key="src.name"
            class="source-pill"
            :class="src.ok ? 'ok' : 'degraded'"
          >
            <div class="source-pill-top">
              <span class="source-dot" aria-hidden="true"></span>
              <strong class="source-name">{{ src.name }}</strong>
              <span class="source-badge">{{ src.ok ? '正常' : '降级缺口' }}</span>
            </div>
            <div class="source-note">{{ src.note || (src.ok ? '已同步' : '接口不可用') }}</div>
            <div v-if="src.queriedAt" class="source-time">{{ dateLabel(src.queriedAt) }}</div>
          </div>
        </div>
      </section>
    </section>

    <!-- 深链模拟地址提示条 -->
    <div v-if="deepLinkNotice" class="inline-alert info" role="status">
      <div>
        <strong>{{ deepLinkNotice.label }}：</strong>
        <code class="deep-link-code">{{ deepLinkNotice.url }}</code>
        <span class="deep-link-sub">（模拟内部系统深链地址，可在生产环境单点直达对应单据）</span>
      </div>
      <button type="button" class="btn-link-teal" @click="deepLinkNotice = null">关闭</button>
    </div>

    <!-- 成功建单反馈 -->
    <div v-if="createdNotice" class="inline-alert success" role="status">
      <div>
        <strong>建单成功：</strong>{{ createdNotice }}
      </div>
      <button type="button" class="btn-link-teal" @click="createdNotice = ''">知道了</button>
    </div>

    <!-- 空状态引导 -->
    <section v-if="!device && !searching" class="panel empty-guide">
      <div class="empty-guide-badge">设备档案</div>
      <h2>输入桩 SN、手机号或项目编号开始查桩</h2>
      <p>支持按 4 源并行聚合查询基本信息、生产与安装、历史报修、历史维修、质保结算及关联工单，并一键带入设备档案建单。</p>
    </section>

    <!-- 同一张设备档案页，严格按 6 段纵向排列 -->
    <div v-if="device" class="profile-sections">
      <!-- ① 基本信息 -->
      <section class="panel section-block" aria-label="基本信息">
        <header class="section-header">
          <div class="section-title-group">
            <span class="section-index">①</span>
            <div>
              <h2>基本信息</h2>
              <p class="section-sub">设备身份、客户联系信息与实时在线报文</p>
            </div>
          </div>
          <button
            type="button"
            class="btn primary create-main-btn"
            :disabled="isBusy"
            @click="openCreateModal"
          >
            为该设备建单
          </button>
        </header>

        <dl class="kv-grid">
          <div class="kv-item">
            <dt>客户</dt>
            <dd>{{ textOrDash(device.customerName) }}</dd>
          </div>
          <div class="kv-item">
            <dt>联系人</dt>
            <dd>{{ textOrDash(device.contactName) }}</dd>
          </div>
          <div class="kv-item">
            <dt>联系电话</dt>
            <dd>{{ textOrDash(device.customerPhone) }}</dd>
          </div>
          <div class="kv-item">
            <dt>桩 SN</dt>
            <dd class="mono strong-val">{{ textOrDash(deviceSn) }}</dd>
          </div>
          <div class="kv-item kv-span-2">
            <dt>安装地址</dt>
            <dd>{{ textOrDash(device.siteAddress) }}</dd>
          </div>
          <div class="kv-item">
            <dt>型号</dt>
            <dd>{{ textOrDash(device.model) }}<span v-if="device.productType" class="sub-tag">{{ device.productType }}</span></dd>
          </div>
          <div class="kv-item">
            <dt>在线状态</dt>
            <dd>
              <span class="inline-pill" :class="onlineTone(device.onlineStatus)">
                <i></i>{{ textOrDash(device.onlineStatus) }}
              </span>
              <span v-if="device.currentStatus" class="sub-muted">· {{ device.currentStatus }}</span>
            </dd>
          </div>
          <div class="kv-item kv-span-full">
            <dt>实时报文</dt>
            <dd class="telemetry-box">{{ textOrDash(device.realtimeTelemetry) }}</dd>
          </div>
        </dl>
      </section>

      <!-- ② 生产与安装 -->
      <section class="panel section-block" aria-label="生产与安装">
        <header class="section-header">
          <div class="section-title-group">
            <span class="section-index">②</span>
            <div>
              <h2>生产与安装</h2>
              <p class="section-sub">MES 生产追溯与售后安装验收档案</p>
            </div>
          </div>
          <div class="section-actions">
            <button
              type="button"
              class="btn secondary btn-sm"
              @click="showDeepLink('MES 原始批次深链', deepLinks.mesBatch || ('https://mes.internal/trace/' + deviceSn))"
            >
              查看 MES 原始批次 ↗
            </button>
            <button
              type="button"
              class="btn secondary btn-sm"
              @click="showDeepLink('安装验收单深链', deepLinks.installOrder || ('https://lezhuang.internal/install/' + (device.projectCode || deviceSn)))"
            >
              查看安装验收单 ↗
            </button>
          </div>
        </header>

        <dl class="kv-grid">
          <div class="kv-item" :class="{ 'kv-degraded': isSourceDegraded('MES') && !device.manufacturedAt }">
            <dt>出厂日期</dt>
            <dd>{{ device.manufacturedAt || (isSourceDegraded('MES') ? '暂缺（MES 降级）' : '—') }}</dd>
          </div>
          <div class="kv-item" :class="{ 'kv-degraded': isSourceDegraded('MES') && !device.mesBatch }">
            <dt>MES 批次</dt>
            <dd class="mono">{{ device.mesBatch || (isSourceDegraded('MES') ? '暂缺（MES 降级）' : '—') }}</dd>
          </div>
          <div class="kv-item" :class="{ 'kv-degraded': isSourceDegraded('MES') }">
            <dt>出厂检验结果</dt>
            <dd>{{ textOrDash(device.mesTestResult) }}</dd>
          </div>
          <div class="kv-item" :class="{ 'kv-degraded': isSourceDegraded('安装') && !device.installedAt }">
            <dt>安装日期</dt>
            <dd>{{ device.installedAt || (isSourceDegraded('安装') ? '暂缺（安装系统降级）' : '—') }}</dd>
          </div>
          <div class="kv-item">
            <dt>程序版本</dt>
            <dd class="mono">{{ textOrDash(device.firmwareVersion) }}</dd>
          </div>
          <div class="kv-item">
            <dt>SIM 到期时间</dt>
            <dd>{{ textOrDash(device.simExpiresAt) }}</dd>
          </div>
        </dl>
      </section>

      <!-- ③ 历史报修 -->
      <section class="panel section-block" aria-label="历史报修">
        <header class="section-header">
          <div class="section-title-group">
            <span class="section-index">③</span>
            <div>
              <h2>历史报修</h2>
              <p class="section-sub">同桩历次报修工单与未闭环防重核查（点击工单号可直达详情）</p>
            </div>
          </div>
          <span class="count-chip">共 {{ relatedProblems.length }} 条</span>
        </header>

        <div v-if="openProblems.length" class="dup-banner" role="status">
          <div class="dup-banner-title">
            同桩未闭环防重提示：当前有 {{ openProblems.length }} 条未闭环工单，请优先点击原工单号跟进，避免重复建单
          </div>
          <div class="dup-links">
            <button
              v-for="item in openProblems"
              :key="item.id"
              type="button"
              class="dup-order-chip"
              @click="$emit('open', item.id)"
            >
              <span class="mono">{{ orderNumber(item) }}</span>
              <span>{{ item.title }}</span>
              <span class="status-tag" :class="displayStatus(item).tone">{{ displayStatus(item).label }}</span>
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
              <span class="status-tag" :class="displayStatus(item).tone">
                <i></i>{{ displayStatus(item).label }}
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
            <span class="section-index">④</span>
            <div>
              <h2>历史维修</h2>
              <p class="section-sub">智享乐装现场维修与远程运维记录</p>
            </div>
          </div>
          <span class="count-chip" :class="{ degraded: isSourceDegraded('乐装') }">
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
              <span class="row-date">{{ textOrDash(item.date) }}</span>
              <span class="row-title">{{ textOrDash(item.summary) }}</span>
            </div>
            <div class="list-row-meta">
              <span v-if="item.source" class="sub-tag">{{ item.source }}</span>
              <button
                v-if="item.deepLink"
                type="button"
                class="btn-link-teal"
                @click="showDeepLink('维修单深链（' + (item.orderNo || '乐装') + '）', item.deepLink)"
              >
                查看维修单 ↗
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
            <span class="section-index">⑤</span>
            <div>
              <h2>质保结算</h2>
              <p class="section-sub">质保期、延保政策、所属项目与客户 SLA 时效</p>
            </div>
          </div>
          <span class="inline-pill" :class="warrantyTone(device.warrantyStatus)">
            <i></i>{{ textOrDash(device.warrantyStatus) }}
          </span>
        </header>

        <dl class="kv-grid">
          <div class="kv-item">
            <dt>质保状态</dt>
            <dd>{{ textOrDash(device.warrantyStatus) }}</dd>
          </div>
          <div class="kv-item">
            <dt>质保至</dt>
            <dd>{{ textOrDash(device.warrantyUntil) }}</dd>
          </div>
          <div class="kv-item">
            <dt>延保</dt>
            <dd>{{ textOrDash(device.extendedWarranty) }}</dd>
          </div>
          <div class="kv-item">
            <dt>所属项目编号</dt>
            <dd class="mono">{{ textOrDash(device.projectCode) }}</dd>
          </div>
          <div class="kv-item">
            <dt>项目名称</dt>
            <dd>{{ textOrDash(device.projectName) }}</dd>
          </div>
          <div class="kv-item">
            <dt>客户类型与 SLA 时效</dt>
            <dd>{{ customerTypeLabel(device.customerType) }} · {{ slaLabel(device.slaMinutes) }}</dd>
          </div>
        </dl>
      </section>

      <!-- ⑥ 关联工单 -->
      <section class="panel section-block" aria-label="关联工单与充电告警摘要">
        <header class="section-header">
          <div class="section-title-group">
            <span class="section-index">⑥</span>
            <div>
              <h2>关联工单</h2>
              <p class="section-sub">在途/已闭环工单汇总及最近充电与告警摘要</p>
            </div>
          </div>
          <button
            type="button"
            class="btn secondary btn-sm"
            @click="showDeepLink('充电桩管理平台深链', deepLinks.devicePlatform || ('https://charger-platform.internal/devices/' + deviceSn))"
          >
            桩平台设备详情 ↗
          </button>
        </header>

        <div class="summary-kpis">
          <div class="kpi-tile">
            <span class="kpi-label">在途工单</span>
            <strong class="kpi-num accent">{{ openProblems.length }}</strong>
          </div>
          <div class="kpi-tile">
            <span class="kpi-label">已闭环 / 已取消</span>
            <strong class="kpi-num">{{ closedProblems.length }}</strong>
          </div>
          <div class="kpi-tile">
            <span class="kpi-label">最近充电记录</span>
            <strong class="kpi-num">{{ chargingRecords.length }}</strong>
          </div>
          <div class="kpi-tile">
            <span class="kpi-label">最近告警</span>
            <strong class="kpi-num" :class="{ danger: recentAlerts.length > 0 }">{{ recentAlerts.length }}</strong>
          </div>
        </div>

        <div class="sub-columns">
          <div class="sub-card">
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
                <span class="status-tag" :class="displayStatus(item).tone">{{ displayStatus(item).label }}</span>
              </button>
            </div>
            <p v-else class="empty-inline">暂无关联工单</p>
          </div>

          <div class="sub-card">
            <h3>最近充电记录</h3>
            <ul v-if="chargingRecords.length" class="compact-list">
              <li v-for="(rec, idx) in chargingRecords" :key="rec.orderNo || idx">
                <div class="compact-top">
                  <span class="mono">{{ rec.orderNo }}</span>
                  <span class="sub-tag" :class="{ warn: rec.status && rec.status.includes('中断') }">{{ rec.status }}</span>
                </div>
                <div class="compact-sub">{{ rec.startedAt }} · {{ rec.energy }} · {{ rec.stopReason }}</div>
              </li>
            </ul>
            <p v-else class="empty-inline">暂无最近充电记录</p>
          </div>

          <div class="sub-card">
            <h3>最近告警摘要</h3>
            <ul v-if="recentAlerts.length" class="compact-list">
              <li v-for="(alert, idx) in recentAlerts" :key="alert.code || idx">
                <div class="compact-top">
                  <strong class="alert-code mono">{{ alert.code }}</strong>
                  <span class="status-tag danger-soft">{{ alert.status || '告警' }}</span>
                </div>
                <div class="compact-sub">{{ alert.name }} · {{ alert.reportedAt }}</div>
              </li>
            </ul>
            <p v-else class="empty-inline">暂无最近告警</p>
          </div>
        </div>
      </section>
    </div>

    <!-- 入口 A 弹窗：为该设备建单 -->
    <div
      v-if="createModalOpen && device"
      class="modal-backdrop"
      role="presentation"
      @click.self="closeCreateModal"
    >
      <section
        class="modal-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-modal-title"
      >
        <header class="modal-header">
          <div>
            <h2 id="create-modal-title">为该设备建单</h2>
            <p>已自动关联设备主档与客户 SLA 信息，提交后进入待分配流转</p>
          </div>
          <button type="button" class="modal-close" aria-label="关闭弹窗" @click="closeCreateModal">×</button>
        </header>

        <div class="modal-body">
          <div class="readonly-snapshot">
            <div><span>客户</span><strong>{{ textOrDash(device.customerName) }}</strong></div>
            <div><span>桩 SN</span><strong class="mono">{{ textOrDash(deviceSn) }}</strong></div>
            <div><span>质保</span><strong>{{ textOrDash(device.warrantyStatus) }}<template v-if="device.warrantyUntil">（至 {{ device.warrantyUntil }}）</template></strong></div>
            <div class="snap-full"><span>安装地址</span><strong>{{ textOrDash(device.siteAddress) }}</strong></div>
          </div>

          <div v-if="openProblems.length" class="modal-dup-warn" role="status">
            注意：该设备尚有 {{ openProblems.length }} 条未闭环工单（如 {{ orderNumber(openProblems[0]) }}），请确认非重复报修后再提交。
          </div>

          <form class="v11-form" @submit.prevent="submitDeviceOrder">
            <div class="form-grid-2">
              <div class="field-group">
                <label for="create-channel">报修渠道 <span class="req">*</span></label>
                <select id="create-channel" v-model="deviceForm.channel" class="form-control" required>
                  <option v-for="opt in CHANNEL_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
              </div>

              <div class="field-group">
                <label for="create-category">故障大类 <span class="req">*</span></label>
                <select id="create-category" v-model="deviceForm.category" class="form-control" required>
                  <option v-for="cat in CATEGORY_OPTIONS" :key="cat" :value="cat">{{ cat }}</option>
                </select>
              </div>

              <div class="field-group">
                <label for="create-urgency">紧急度 <span class="req">*</span></label>
                <select id="create-urgency" v-model="deviceForm.urgency" class="form-control" required>
                  <option v-for="urg in URGENCY_OPTIONS" :key="urg.value" :value="urg.value">{{ urg.label }}</option>
                </select>
              </div>

              <div class="field-group">
                <label for="create-occurred">故障发生时点（北京时间） <span class="req">*</span></label>
                <input
                  id="create-occurred"
                  v-model="deviceForm.faultOccurredAt"
                  type="datetime-local"
                  class="form-control"
                  required
                >
              </div>
            </div>

            <div class="field-group">
              <label for="create-title">故障现象标题 <span class="req">*</span></label>
              <input
                id="create-title"
                v-model="deviceForm.title"
                type="text"
                class="form-control"
                maxlength="150"
                placeholder="简要概括故障现象，例如：插枪无法启动充电（报 PE 异常）"
                required
              >
            </div>

            <div class="field-group">
              <label for="create-desc">现象描述 <span class="req">*</span></label>
              <textarea
                id="create-desc"
                v-model="deviceForm.description"
                class="form-control form-textarea"
                maxlength="1000"
                placeholder="请描述车主/现场反馈的具体情况、指示灯颜色、报错代码与复现条件"
                required
              ></textarea>
            </div>

            <p v-if="createError" class="form-error" role="alert">{{ createError }}</p>

            <div class="form-actions">
              <button type="button" class="btn secondary" :disabled="isBusy" @click="closeCreateModal">取消</button>
              <button type="submit" class="btn primary" :disabled="isBusy || !canSubmitDevice">
                {{ submitting ? '提交建单中…' : '确认创建问题单' }}
              </button>
            </div>
          </form>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { createProblemV11, lookupDevice } from '../api/workbench'
import { dateLabel, displayStatus, orderNumber, shanghaiStamp } from '../status'

const props = defineProps({
  busy: { type: Boolean, default: false }
})

const emit = defineEmits(['open', 'created'])

const CHANNEL_OPTIONS = [
  { value: '400', label: '400电话' },
  { value: 'miniprogram', label: '客户小程序' },
  { value: 'provider', label: '服务商扫码' },
  { value: 'wecom', label: '企微群' },
  { value: 'phone', label: '电话受理' }
]

const CATEGORY_OPTIONS = [
  '无法充电',
  '充电中断',
  '设备离线',
  '计费异常',
  '安装问题',
  '其他'
]

const URGENCY_OPTIONS = [
  { value: 'high', label: '高' },
  { value: 'normal', label: '中' },
  { value: 'low', label: '低' }
]

const CLOSED_STATES = new Set(['closed', 'cancelled', 'resolved', 'answered', 'referred'])

function nowBeijingInput() {
  return shanghaiStamp().replace(' ', 'T')
}

const query = ref('')
const lastSearchedQuery = ref('')
const failSource = ref('')
const device = ref(null)
const searching = ref(false)
const submitting = ref(false)
const searchError = ref('')
const deepLinkNotice = ref(null)
const createdNotice = ref('')

const createModalOpen = ref(false)
const createError = ref('')
const deviceForm = reactive({
  channel: '400',
  category: '无法充电',
  urgency: 'normal',
  faultOccurredAt: nowBeijingInput(),
  title: '',
  description: ''
})

const pendingOpen = ref(false)
const pendingError = ref('')
const pendingForm = reactive({
  channel: '400',
  category: '无法充电',
  urgency: 'normal',
  faultOccurredAt: nowBeijingInput(),
  title: '',
  description: ''
})

const isBusy = computed(() => Boolean(props.busy || searching.value || submitting.value))
const deviceSn = computed(() => (device.value ? (device.value.sn || device.value.device || '') : ''))
const sourceStatuses = computed(() => (device.value && Array.isArray(device.value.sourceStatuses) ? device.value.sourceStatuses : []))
const relatedProblems = computed(() => (device.value && Array.isArray(device.value.relatedProblems) ? device.value.relatedProblems : []))
const repairHistory = computed(() => (device.value && Array.isArray(device.value.repairHistory) ? device.value.repairHistory : []))
const chargingRecords = computed(() => (device.value && Array.isArray(device.value.chargingRecords) ? device.value.chargingRecords : []))
const recentAlerts = computed(() => (device.value && Array.isArray(device.value.recentAlerts) ? device.value.recentAlerts : []))
const deepLinks = computed(() => (device.value && device.value.deepLinks) || {})

const openProblems = computed(() => relatedProblems.value.filter(item => item && !CLOSED_STATES.has(item.state)))
const closedProblems = computed(() => relatedProblems.value.filter(item => item && CLOSED_STATES.has(item.state)))

const canSubmitDevice = computed(() => Boolean(
  deviceSn.value
  && deviceForm.faultOccurredAt
  && deviceForm.title.trim()
  && deviceForm.description.trim()
))

const canSubmitPending = computed(() => Boolean(
  pendingForm.faultOccurredAt
  && pendingForm.title.trim()
  && pendingForm.description.trim()
))

function textOrDash(val) {
  if (val === undefined || val === null) return '—'
  const text = String(val).trim()
  return text || '—'
}

function isSourceDegraded(keyword) {
  return sourceStatuses.value.some(src => src && !src.ok && String(src.name || '').includes(keyword))
}

function onlineTone(status) {
  const text = String(status || '')
  if (text.includes('在线')) return 'tone-ok'
  if (text.includes('离线') || text.includes('故障')) return 'tone-danger'
  return 'tone-muted'
}

function warrantyTone(status) {
  const text = String(status || '')
  if (text.includes('在保')) return 'tone-ok'
  if (text.includes('过保')) return 'tone-danger'
  return 'tone-warn'
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

function showDeepLink(label, url) {
  deepLinkNotice.value = { label, url }
}

async function runSearch(overrideQuery) {
  const target = (typeof overrideQuery === 'string' ? overrideQuery : query.value).trim()
  if (!target || isBusy.value) return
  searching.value = true
  searchError.value = ''
  deepLinkNotice.value = null
  try {
    const result = await lookupDevice(target, failSource.value || undefined)
    device.value = result
    lastSearchedQuery.value = target
  } catch (err) {
    device.value = null
    searchError.value = (err && err.message) || '查询设备失败，请稍后重试'
  } finally {
    searching.value = false
  }
}

function onFailSourceChange() {
  const currentTarget = (query.value.trim() || lastSearchedQuery.value).trim()
  if (device.value && currentTarget) {
    runSearch(currentTarget)
  }
}

function resetDeviceForm() {
  deviceForm.channel = '400'
  deviceForm.category = '无法充电'
  deviceForm.urgency = 'normal'
  deviceForm.faultOccurredAt = nowBeijingInput()
  deviceForm.title = ''
  deviceForm.description = ''
  createError.value = ''
}

function openCreateModal() {
  resetDeviceForm()
  createModalOpen.value = true
}

function closeCreateModal() {
  if (submitting.value) return
  createModalOpen.value = false
}

function resetPendingForm() {
  pendingForm.channel = '400'
  pendingForm.category = '无法充电'
  pendingForm.urgency = 'normal'
  pendingForm.faultOccurredAt = nowBeijingInput()
  pendingForm.title = ''
  pendingForm.description = ''
  pendingError.value = ''
}

function openPendingForm() {
  resetPendingForm()
  pendingOpen.value = true
}

function togglePendingForm() {
  if (pendingOpen.value) {
    pendingOpen.value = false
  } else {
    openPendingForm()
  }
}

async function submitDeviceOrder() {
  if (isBusy.value || !canSubmitDevice.value) return
  submitting.value = true
  createError.value = ''
  try {
    const res = await createProblemV11({
      device: deviceSn.value,
      devicePending: false,
      channel: deviceForm.channel,
      category: deviceForm.category,
      urgency: deviceForm.urgency,
      faultOccurredAt: deviceForm.faultOccurredAt,
      title: deviceForm.title.trim(),
      description: deviceForm.description.trim()
    })
    createModalOpen.value = false
    const no = res && res.ticketNo ? `（工单号：${res.ticketNo}）` : ''
    createdNotice.value = `已为设备 ${deviceSn.value} 创建问题单${no}`
    emit('created', res && res.id)
  } catch (err) {
    createError.value = (err && err.message) || '建单失败，请检查表单内容'
  } finally {
    submitting.value = false
  }
}

async function submitPendingOrder() {
  if (isBusy.value || !canSubmitPending.value) return
  submitting.value = true
  pendingError.value = ''
  try {
    const res = await createProblemV11({
      device: null,
      devicePending: true,
      channel: pendingForm.channel,
      category: pendingForm.category,
      urgency: pendingForm.urgency,
      faultOccurredAt: pendingForm.faultOccurredAt,
      title: pendingForm.title.trim(),
      description: pendingForm.description.trim()
    })
    pendingOpen.value = false
    resetPendingForm()
    const no = res && res.ticketNo ? `（工单号：${res.ticketNo}）` : ''
    createdNotice.value = `已创建「设备信息待补」工单并进入待补队列${no}`
    emit('created', res && res.id)
  } catch (err) {
    pendingError.value = (err && err.message) || '创建待补工单失败，请检查表单内容'
  } finally {
    submitting.value = false
  }
}

function onKeyDown(e) {
  if (e.key === 'Escape' && createModalOpen.value) {
    closeCreateModal()
  }
}

onMounted(() => window.addEventListener('keydown', onKeyDown))
onUnmounted(() => window.removeEventListener('keydown', onKeyDown))
</script>

<style scoped>
.device-lookup-page {
  display: grid;
  gap: 14px;
  color: var(--ink, #334155);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.panel {
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 12px;
  padding: 18px 20px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-variant-numeric: tabular-nums;
}

/* 搜索条 */
.search-panel {
  display: grid;
  gap: 12px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input-wrap {
  position: relative;
  flex: 1 1 360px;
  min-width: 0;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #8a96a5;
  pointer-events: none;
}

.search-input {
  width: 100%;
  min-height: 40px;
  padding: 8px 12px 8px 36px;
  border: 1px solid #d7e0ea;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
}

.search-input:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.14);
  outline: none;
}

.degrade-select-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  min-height: 40px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
  background: var(--canvas, #f5f7fa);
  flex: 0 0 auto;
}

.degrade-label {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.degrade-select {
  border: 0;
  background: transparent;
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 2px;
  cursor: pointer;
}

.degrade-select:focus {
  outline: none;
}

.search-submit {
  min-height: 40px;
  padding: 8px 18px;
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.15s ease, border-color 0.15s ease, opacity 0.15s ease;
}

.btn.primary {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #fff;
}

.btn.primary:hover:not(:disabled) {
  background: #0d9488;
  border-color: #0d9488;
}

.btn.secondary {
  border: 1px solid #d7e0ea;
  background: #fff;
  color: #334155;
}

.btn.secondary:hover:not(:disabled) {
  border-color: #0d9488;
  color: #0f766e;
  background: #e8f6f4;
}

.btn.btn-sm {
  min-height: 30px;
  padding: 4px 10px;
  font-size: 12px;
}

.btn:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.btn-link-teal,
.btn-link-danger {
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.btn-link-teal {
  color: #0f766e;
}

.btn-link-teal:hover {
  text-decoration: underline;
}

.btn-link-danger {
  color: #c0362c;
}

.btn-link-danger:hover {
  text-decoration: underline;
}

/* 入口 B：待补例外区 */
.exception-entry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line, #eaeef2);
}

.exception-hint {
  font-size: 12px;
  color: #64748b;
}

.exception-trigger {
  min-height: 34px;
  font-size: 12px;
  border-color: #cbd5e1;
}

.pending-form-card {
  padding: 14px 16px;
  border: 1px solid #f5d08a;
  border-radius: 10px;
  background: #fffdf7;
  display: grid;
  gap: 12px;
}

.pending-alert {
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff5dd;
  border: 1px solid #f3d285;
  color: #92400e;
  font-size: 13px;
  line-height: 1.5;
}

/* 提示条 */
.inline-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.inline-alert.compact {
  padding: 8px 12px;
  font-size: 12px;
}

.inline-alert.error {
  background: #fdecec;
  border: 1px solid #f5c2be;
  color: #c0362c;
}

.inline-alert.info {
  background: #e8f6f4;
  border: 1px solid #bfe3dd;
  color: #0f766e;
}

.inline-alert.success {
  background: #e8f6f4;
  border: 1px solid #0d9488;
  color: #0f766e;
}

.deep-link-code {
  padding: 2px 6px;
  border-radius: 4px;
  background: #fff;
  border: 1px solid #c5e4e1;
  color: #0f172a;
  font-size: 12px;
  word-break: break-all;
}

.deep-link-sub {
  margin-left: 6px;
  color: #475569;
  font-size: 12px;
}

/* 4 源并行状态条 */
.sources-strip {
  display: grid;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #eaeef2);
}

.sources-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.sources-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.sources-meta {
  font-size: 12px;
  color: #64748b;
}

.sources-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.source-pill {
  padding: 10px 12px;
  border-radius: 9px;
  border: 1px solid transparent;
  display: grid;
  gap: 4px;
  min-width: 0;
}

.source-pill.ok {
  background: #e8f6f4;
  border-color: #c5e4e1;
  color: #0f766e;
}

.source-pill.degraded {
  background: #fdecec;
  border-color: #f5c2be;
  color: #c0362c;
}

.source-pill-top {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.source-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  flex: none;
}

.source-name {
  font-size: 12px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-badge {
  margin-left: auto;
  font-size: 11px;
  font-weight: 700;
  flex: none;
}

.source-note {
  font-size: 12px;
  line-height: 1.4;
}

.source-time {
  font-size: 11px;
  opacity: 0.8;
}

/* 空状态 */
.empty-guide {
  text-align: center;
  padding: 40px 20px;
  background: #fff;
}

.empty-guide-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  background: #e8f6f4;
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 10px;
}

.empty-guide h2 {
  margin: 0 0 6px;
  font-size: 16px;
  color: #0f172a;
}

.empty-guide p {
  margin: 0 auto;
  max-width: 560px;
  font-size: 13px;
  color: #64748b;
}

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

.count-chip.degraded {
  background: #fdecec;
  border-color: #f5c2be;
  color: #c0362c;
}

/* 键值网格 */
.kv-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.kv-item {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--canvas, #f5f7fa);
  border: 1px solid var(--line, #eaeef2);
}

.kv-item.kv-span-2 {
  grid-column: span 2;
}

.kv-item.kv-span-full {
  grid-column: 1 / -1;
}

.kv-item.kv-degraded {
  background: #fdf4f3;
  border-color: #f5c2be;
}

.kv-item.kv-degraded dd {
  color: #c0362c;
}

.kv-item dt {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}

.kv-item dd {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  overflow-wrap: anywhere;
}

.strong-val {
  color: #0f766e !important;
}

.telemetry-box {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px !important;
  font-weight: 500 !important;
  color: #1e293b !important;
}

.sub-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-weight: 500;
}

.sub-tag.warn {
  background: #fff5dd;
  color: #b45309;
}

.sub-muted {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
}

.inline-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.inline-pill i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.inline-pill.tone-ok {
  background: #e8f6f4;
  color: #0f766e;
}

.inline-pill.tone-danger {
  background: #fdecec;
  color: #c0362c;
}

.inline-pill.tone-warn {
  background: #fff5dd;
  color: #b45309;
}

.inline-pill.tone-muted {
  background: #f1f5f9;
  color: #475569;
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

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  background: #f1f5f9;
  color: #475569;
}

.status-tag i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
}

.status-tag.processing { background: #e8f6f4; color: #0f766e; }
.status-tag.waiting { background: #fef3c7; color: #b45309; }
.status-tag.dispatch { background: #eef2ff; color: #4338ca; }
.status-tag.onsite { background: #e0f2fe; color: #0369a1; }
.status-tag.verify { background: #f3e8ff; color: #7e22ce; }
.status-tag.closed { background: #f1f5f9; color: #475569; }
.status-tag.triage { background: #fce7f3; color: #9d174d; }
.status-tag.follow { background: #ecfccb; color: #3f6212; }
.status-tag.danger-soft { background: #fdecec; color: #c0362c; }

.empty-inline {
  margin: 0;
  padding: 6px 0;
  font-size: 12px;
  color: #8a96a5;
}

/* 第 6 段：关联工单与充电告警摘要 */
.summary-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.kpi-tile {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--canvas, #f5f7fa);
  border: 1px solid var(--line, #eaeef2);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.kpi-label {
  font-size: 12px;
  color: #64748b;
}

.kpi-num {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.kpi-num.accent {
  color: #0f766e;
}

.kpi-num.danger {
  color: #c0362c;
}

.sub-columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.sub-card {
  padding: 12px;
  border-radius: 9px;
  border: 1px solid var(--line, #eaeef2);
  background: #fff;
  min-width: 0;
}

.sub-card h3 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
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
  padding: 7px 9px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 7px;
  background: var(--canvas, #f5f7fa);
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.quick-ticket-item:hover {
  border-color: #0d9488;
  background: #e8f6f4;
}

.order-link-text {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  flex: none;
}

.clip-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: #334155;
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
  border-radius: 7px;
  background: var(--canvas, #f5f7fa);
  border: 1px solid var(--line, #eaeef2);
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

/* 弹窗与表单 */
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(15, 23, 42, 0.45);
  display: grid;
  place-items: center;
  padding: 16px;
}

.modal-dialog {
  width: min(680px, 100%);
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 14px;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.22);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line, #eaeef2);
  background: #e8f6f4;
}

.modal-header h2 {
  margin: 0;
  font-size: 16px;
  color: #0f766e;
}

.modal-header p {
  margin: 3px 0 0;
  font-size: 12px;
  color: #475569;
}

.modal-close {
  width: 30px;
  height: 30px;
  border: 1px solid #c5e4e1;
  border-radius: 7px;
  background: #fff;
  color: #0f766e;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.modal-body {
  padding: 18px 20px 20px;
  display: grid;
  gap: 14px;
}

.readonly-snapshot {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
  border-radius: 9px;
  background: var(--canvas, #f5f7fa);
  border: 1px solid var(--line, #eaeef2);
}

.readonly-snapshot > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.readonly-snapshot .snap-full {
  grid-column: 1 / -1;
}

.readonly-snapshot span {
  font-size: 11px;
  color: #64748b;
}

.readonly-snapshot strong {
  font-size: 12px;
  color: #0f172a;
  overflow-wrap: anywhere;
}

.modal-dup-warn {
  padding: 9px 12px;
  border-radius: 8px;
  background: #fff9ec;
  border: 1px solid #f5d08a;
  color: #9a5b00;
  font-size: 12px;
}

.v11-form {
  display: grid;
  gap: 12px;
}

.form-grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}

.field-group {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.field-group label {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.req {
  color: #c0362c;
}

.form-control {
  width: 100%;
  min-height: 38px;
  padding: 7px 10px;
  border: 1px solid #d7e0ea;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
}

.form-control:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.14);
  outline: none;
}

.form-textarea {
  min-height: 86px;
  resize: vertical;
}

.form-error {
  margin: 0;
  padding: 8px 10px;
  border-radius: 7px;
  background: #fdecec;
  border: 1px solid #f5c2be;
  color: #c0362c;
  font-size: 12px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

/* 响应式：中窄屏兼容 */
@media (max-width: 1024px) {
  .sources-grid,
  .kv-grid,
  .summary-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sub-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .panel {
    padding: 14px;
  }

  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input-wrap {
    flex: none;
  }

  .degrade-select-wrap {
    justify-content: space-between;
  }

  .sources-grid,
  .kv-grid,
  .summary-kpis,
  .form-grid-2,
  .readonly-snapshot {
    grid-template-columns: 1fr;
  }

  .kv-item.kv-span-2 {
    grid-column: span 1;
  }

  .create-main-btn {
    width: 100%;
  }

  .modal-body,
  .modal-header {
    padding: 14px;
  }
}
</style>
