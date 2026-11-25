<template>
  <section class="sop-workspace" :aria-label="readOnly ? '知识库' : 'SOP 与规则管理'">
    <!-- 顶部操作条（松果青绿浅色白卡） -->
    <header class="sop-topbar">
      <div class="topbar-left">
        <nav class="sop-breadcrumb" aria-label="当前位置">
          <span>{{ readOnly ? '知识库' : '管理' }}</span>
          <template v-if="!readOnly">
            <span class="crumb-sep" aria-hidden="true">/</span>
            <span>SOP 与规则</span>
          </template>
          <span class="crumb-sep" aria-hidden="true">/</span>
          <strong class="crumb-current">{{ activeGroup ? activeGroup.category : '全部故障' }}</strong>
        </nav>

        <span v-if="readOnly" class="mode-badge readonly">
          只读发布版 · 仅展示生效 SOP
        </span>
        <template v-else>
          <span class="mode-badge admin">唯一写入端</span>
          <span v-if="displayedEntry" class="version-badge">
            {{ displayedEntry.version }} · {{ statusLabel(displayedEntry.status) }}
          </span>
        </template>
      </div>

      <!-- 管理侧操作按钮（readOnly === true 时绝对不渲染） -->
      <div v-if="!readOnly" class="topbar-actions">
        <button
          type="button"
          class="btn-sop btn-secondary"
          :disabled="busy"
          :aria-expanded="showNewFaultForm"
          @click="toggleNewFaultForm"
        >
          + 新建故障
        </button>
        <button
          type="button"
          class="btn-sop btn-secondary"
          :disabled="busy || !displayedEntry"
          @click="handleReview"
        >
          评审
        </button>
        <button
          type="button"
          class="btn-sop btn-primary"
          :disabled="busy || !displayedEntry"
          @click="handleTopbarPublish"
        >
          发布
        </button>
      </div>
    </header>

    <!-- 新建故障内联面板（仅管理侧） -->
    <form
      v-if="!readOnly && showNewFaultForm"
      class="new-fault-panel"
      @submit.prevent="submitNewFault(false)"
    >
      <div class="panel-head">
        <div>
          <h3>新建故障分类与 SOP</h3>
          <p>保存后写入唯一 SOP 主库；选择「创建并发布」将单向同步至一线只读知识库。</p>
        </div>
        <button type="button" class="btn-sop btn-secondary btn-sm" @click="cancelNewFault">
          关闭
        </button>
      </div>

      <div class="new-fault-grid">
        <label class="form-field">
          <span class="field-label">故障分类名称 *</span>
          <input
            v-model.trim="newFault.category"
            type="text"
            class="sop-input"
            maxlength="80"
            placeholder="例如：枪锁卡滞、屏幕黑屏"
            required
          >
        </label>
        <label class="form-field">
          <span class="field-label">SOP 标题 *</span>
          <input
            v-model.trim="newFault.title"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="例如：枪锁卡滞 · SOP步骤"
            required
          >
        </label>
        <label class="form-field">
          <span class="field-label">初始版本号 *</span>
          <input
            v-model.trim="newFault.version"
            type="text"
            class="sop-input"
            maxlength="20"
            placeholder="V1.0"
            required
          >
        </label>
        <label class="form-field">
          <span class="field-label">取证模式</span>
          <input
            v-model.trim="newFault.evidenceMode"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="例如：时序+可视混合类"
          >
        </label>
      </div>

      <label class="form-field">
        <span class="field-label">一线级别提示（仅提示取证方向，不暴露判定阈值）</span>
        <input
          v-model.trim="newFault.gradeHint"
          type="text"
          class="sop-input"
          maxlength="200"
          placeholder="例如：A级需平台日志佐证；B级需现场采集与对照；C级禁止换装"
        >
      </label>

      <div class="new-fault-stages">
        <div class="mini-stage">
          <div class="mini-stage-head">
            <strong>第一段：客户可执行（1–3 步）</strong>
            <button
              type="button"
              class="btn-sop btn-secondary btn-sm"
              :disabled="newFault.customerSteps.length >= 3"
              @click="addNewFaultStep('customer')"
            >
              + 加一步
            </button>
          </div>
          <div
            v-for="(step, idx) in newFault.customerSteps"
            :key="'nf-c-' + idx"
            class="mini-step-row"
          >
            <span class="step-index">{{ idx + 1 }}</span>
            <input
              v-model.trim="newFault.customerSteps[idx]"
              type="text"
              class="sop-input"
              maxlength="160"
              :placeholder="'客户步骤 ' + (idx + 1)"
            >
            <button
              type="button"
              class="btn-sop btn-danger-ghost"
              :disabled="newFault.customerSteps.length <= 1"
              @click="removeNewFaultStep('customer', idx)"
            >
              删除
            </button>
          </div>
        </div>

        <div class="mini-stage">
          <div class="mini-stage-head">
            <strong>第二段：上门验证（4–6 步）</strong>
            <button
              type="button"
              class="btn-sop btn-secondary btn-sm"
              :disabled="newFault.onsiteSteps.length >= 3"
              @click="addNewFaultStep('onsite')"
            >
              + 加一步
            </button>
          </div>
          <div
            v-for="(step, idx) in newFault.onsiteSteps"
            :key="'nf-o-' + idx"
            class="mini-step-row"
          >
            <span class="step-index onsite">{{ idx + 4 }}</span>
            <input
              v-model.trim="newFault.onsiteSteps[idx]"
              type="text"
              class="sop-input"
              maxlength="160"
              :placeholder="'上门步骤 ' + (idx + 4)"
            >
            <button
              type="button"
              class="btn-sop btn-danger-ghost"
              :disabled="newFault.onsiteSteps.length <= 1"
              @click="removeNewFaultStep('onsite', idx)"
            >
              删除
            </button>
          </div>
        </div>
      </div>

      <div class="form-field">
        <span class="field-label">所需证据清单</span>
        <div class="evidence-checkbox-group">
          <label
            v-for="opt in evidenceOptions"
            :key="'nf-ev-' + opt.value"
            class="evidence-check-pill"
            :class="{ checked: newFault.requiredEvidence.includes(opt.value) }"
          >
            <input
              v-model="newFault.requiredEvidence"
              type="checkbox"
              :value="opt.value"
            >
            <span>{{ opt.label }}</span>
          </label>
        </div>
      </div>

      <div class="new-fault-grid three">
        <label class="form-field">
          <span class="field-label">A级阈值（仅管理侧）</span>
          <input
            v-model.trim="newFault.gradeThresholds.A"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="平台故障码命中且有日志"
          >
        </label>
        <label class="form-field">
          <span class="field-label">B级阈值（仅管理侧）</span>
          <input
            v-model.trim="newFault.gradeThresholds.B"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="现场实测/对照记录>=2项"
          >
        </label>
        <label class="form-field">
          <span class="field-label">C级阈值（仅管理侧）</span>
          <input
            v-model.trim="newFault.gradeThresholds.C"
            type="text"
            class="sop-input"
            maxlength="120"
            placeholder="仅口述现象无日志"
          >
        </label>
      </div>

      <div class="new-fault-actions">
        <button type="button" class="btn-sop btn-secondary" :disabled="busy" @click="cancelNewFault">
          取消
        </button>
        <button type="submit" class="btn-sop btn-secondary" :disabled="busy">
          保存为草稿
        </button>
        <button
          type="button"
          class="btn-sop btn-primary"
          :disabled="busy"
          @click="submitNewFault(true)"
        >
          创建并发布
        </button>
      </div>
    </form>

    <!-- 页内状态提示 -->
    <div
      v-if="statusNotice.text"
      class="status-banner"
      :class="statusNotice.tone"
      role="status"
      aria-live="polite"
    >
      <span>{{ statusNotice.text }}</span>
      <button
        type="button"
        class="banner-close"
        aria-label="关闭提示"
        @click="statusNotice.text = ''"
      >
        ×
      </button>
    </div>

    <!-- 三栏主体结构 -->
    <div class="sop-columns">
      <!-- 左栏：故障分类树 -->
      <aside class="sop-card tree-card" aria-label="故障分类树">
        <div class="card-head">
          <h2>故障分类树</h2>
          <span class="head-sub">{{ loading ? '加载中…' : `共 ${categoryGroups.length} 个分类` }}</span>
        </div>

        <label class="search-box">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="m16 16 4.5 4.5" />
          </svg>
          <span class="visually-hidden">搜索故障分类</span>
          <input
            v-model.trim="categorySearch"
            type="search"
            placeholder="搜索分类"
            maxlength="40"
            autocomplete="off"
          >
        </label>

        <ul v-if="filteredGroups.length" class="category-tree">
          <li v-for="group in filteredGroups" :key="group.category">
            <button
              type="button"
              class="tree-node"
              :class="{ active: group.category === activeCategoryName }"
              :aria-current="group.category === activeCategoryName ? 'true' : undefined"
              @click="selectCategory(group.category)"
            >
              <span class="tree-node-name">{{ group.category }}</span>
              <span class="tree-node-meta">
                <span v-if="!readOnly && group.latestDraft" class="tree-draft-dot" title="存在待发布草稿">草稿</span>
                <span class="tree-ver">{{ group.defaultEntry.version }}</span>
                <span class="tree-count">
                  {{ group.defaultEntry.customerSteps.length + group.defaultEntry.onsiteSteps.length }}步
                </span>
              </span>
            </button>
          </li>
        </ul>
        <div v-else class="tree-empty">
          <p>{{ loading ? '正在加载分类…' : '未找到匹配的故障分类' }}</p>
          <button
            v-if="categorySearch"
            type="button"
            class="link-btn"
            @click="categorySearch = ''"
          >
            清空搜索
          </button>
        </div>

        <footer class="tree-footer">
          <span>共 {{ categoryGroups.length }} 个分类 &gt;</span>
          <span v-if="categorySearch" class="tree-filtered">当前匹配 {{ filteredGroups.length }} 个</span>
        </footer>
      </aside>

      <!-- 中栏：两段式 SOP 步骤卡片 -->
      <section class="sop-card sop-main-card" aria-label="两段式 SOP 步骤">
        <template v-if="displayedEntry">
          <div class="card-head main-head">
            <div class="main-title-wrap">
              <h2>{{ displayedEntry.title || activeGroup.category }}</h2>
              <span class="version-badge">
                {{ readOnly ? '生效版本：' : '当前查看：' }}{{ displayedEntry.version }}
              </span>
              <span
                v-if="!readOnly && displayedEntry.status"
                class="version-state"
                :class="statusTone(displayedEntry.status)"
              >
                {{ statusLabel(displayedEntry.status) }}
              </span>
              <button
                v-if="!readOnly && isViewingNonDefaultVersion"
                type="button"
                class="link-btn"
                @click="selectedVersionId = null"
              >
                返回默认版本
              </button>
            </div>

            <div v-if="!readOnly" class="main-head-actions">
              <button
                v-if="!isEditingSop"
                type="button"
                class="btn-sop btn-secondary"
                :disabled="busy"
                @click="enterEditSop"
              >
                编辑 SOP
              </button>
              <template v-else>
                <button type="button" class="btn-sop btn-secondary" :disabled="busy" @click="cancelEditSop">
                  取消
                </button>
                <button type="button" class="btn-sop btn-secondary" :disabled="busy" @click="saveEditSop(false)">
                  保存草稿
                </button>
                <button type="button" class="btn-sop btn-primary" :disabled="busy" @click="saveEditSop(true)">
                  保存并发布
                </button>
              </template>
            </div>
          </div>

          <div class="sop-meta-strip">
            <span v-if="displayedEntry.evidenceMode" class="meta-chip">
              取证模式：{{ displayedEntry.evidenceMode }}
            </span>
            <span class="sop-summary-note">
              两段式排查标准：先引导客户完成远程自查（限 3 步内）并留痕，无法恢复再派单执行上门核验。
            </span>
          </div>

          <!-- 编辑模式下的元数据配置（仅管理侧） -->
          <div v-if="!readOnly && isEditingSop" class="sop-edit-meta">
            <div class="new-fault-grid three">
              <label class="form-field">
                <span class="field-label">SOP 标题 *</span>
                <input v-model.trim="sopDraft.title" type="text" class="sop-input" maxlength="120">
              </label>
              <label class="form-field">
                <span class="field-label">新版本号 *</span>
                <input v-model.trim="sopDraft.version" type="text" class="sop-input" maxlength="20">
              </label>
              <label class="form-field">
                <span class="field-label">取证模式</span>
                <input v-model.trim="sopDraft.evidenceMode" type="text" class="sop-input" maxlength="120">
              </label>
            </div>

            <label class="form-field">
              <span class="field-label">一线级别提示（仅提示取证方向，不显示阈值数字）</span>
              <input v-model.trim="sopDraft.gradeHint" type="text" class="sop-input" maxlength="200">
            </label>

            <div class="form-field">
              <span class="field-label">所需证据清单</span>
              <div class="evidence-checkbox-group">
                <label
                  v-for="opt in evidenceOptions"
                  :key="'edit-ev-' + opt.value"
                  class="evidence-check-pill"
                  :class="{ checked: sopDraft.requiredEvidence.includes(opt.value) }"
                >
                  <input v-model="sopDraft.requiredEvidence" type="checkbox" :value="opt.value">
                  <span>{{ opt.label }}</span>
                </label>
              </div>
            </div>
          </div>

          <!-- 第一段：客户可执行 1-3（严格 ≤3 步） -->
          <div class="sop-stage">
            <div class="stage-head">
              <div class="stage-title">
                <span class="stage-pill stage-customer">第一段</span>
                <h3>客户可执行 1-3</h3>
              </div>
              <span class="stage-limit">
                控制在 3 步以内（当前 {{ displayedCustomerSteps.length }}/3 步）
              </span>
            </div>

            <ol class="step-list">
              <li
                v-for="(step, idx) in displayedCustomerSteps"
                :key="'cust-' + idx"
                class="step-item"
              >
                <span class="step-index">{{ idx + 1 }}</span>

                <div v-if="readOnly || !isEditingSop" class="step-body">
                  <p class="step-desc">{{ step }}</p>
                </div>

                <div v-else class="step-editor">
                  <div class="step-editor-inline">
                    <input
                      v-model.trim="sopDraft.customerSteps[idx]"
                      type="text"
                      class="sop-input"
                      maxlength="200"
                      :aria-label="'客户步骤 ' + (idx + 1) + ' 说明'"
                      placeholder="请输入客户排查步骤说明"
                    >
                    <button
                      type="button"
                      class="btn-sop btn-danger-ghost"
                      :disabled="sopDraft.customerSteps.length <= 1"
                      @click="removeDraftStep('customer', idx)"
                    >
                      删除
                    </button>
                  </div>
                </div>
              </li>
            </ol>

            <div v-if="!readOnly && isEditingSop" class="stage-edit-bar">
              <button
                type="button"
                class="btn-sop btn-secondary btn-sm"
                :disabled="sopDraft.customerSteps.length >= 3"
                @click="addDraftStep('customer')"
              >
                + 新增客户步骤
              </button>
              <span class="limit-hint">
                {{ sopDraft.customerSteps.length >= 3 ? '已达上限：客户可执行最多 3 步' : '还可新增 ' + (3 - sopDraft.customerSteps.length) + ' 步' }}
              </span>
            </div>
          </div>

          <!-- 第二段：上门验证 4-6 -->
          <div class="sop-stage stage-onsite-wrap">
            <div class="stage-head">
              <div class="stage-title">
                <span class="stage-pill stage-onsite">第二段</span>
                <h3>上门验证 4-6</h3>
              </div>
              <span class="stage-limit">
                现场核验与换装留痕（第 4–{{ 3 + displayedOnsiteSteps.length }} 步）
              </span>
            </div>

            <ol class="step-list">
              <li
                v-for="(step, idx) in displayedOnsiteSteps"
                :key="'onsite-' + idx"
                class="step-item onsite"
              >
                <span class="step-index onsite">{{ idx + 4 }}</span>

                <div v-if="readOnly || !isEditingSop" class="step-body">
                  <p class="step-desc">{{ step }}</p>
                </div>

                <div v-else class="step-editor">
                  <div class="step-editor-inline">
                    <input
                      v-model.trim="sopDraft.onsiteSteps[idx]"
                      type="text"
                      class="sop-input"
                      maxlength="200"
                      :aria-label="'上门步骤 ' + (idx + 4) + ' 说明'"
                      placeholder="请输入上门验证步骤说明"
                    >
                    <button
                      type="button"
                      class="btn-sop btn-danger-ghost"
                      :disabled="sopDraft.onsiteSteps.length <= 1"
                      @click="removeDraftStep('onsite', idx)"
                    >
                      删除
                    </button>
                  </div>
                </div>
              </li>
            </ol>

            <div v-if="!readOnly && isEditingSop" class="stage-edit-bar">
              <button
                type="button"
                class="btn-sop btn-secondary btn-sm"
                :disabled="sopDraft.onsiteSteps.length >= 3"
                @click="addDraftStep('onsite')"
              >
                + 新增上门步骤
              </button>
              <span class="limit-hint">
                {{ sopDraft.onsiteSteps.length >= 3 ? '已达上限：上门验证第 4-6 步' : '还可新增 ' + (3 - sopDraft.onsiteSteps.length) + ' 步' }}
              </span>
            </div>
          </div>
        </template>
        <div v-else class="tree-empty">
          <p>暂无 SOP 数据，请先新建故障分类或刷新页面。</p>
        </div>
      </section>

      <!-- 右栏 -->
      <aside class="sop-right-col" :aria-label="readOnly ? '级别提示与标准证据说明' : '分级阈值、审批规则与版本记录'">
        <!-- ===== 分支 1：只读知识库（readOnly === true）===== -->
        <!-- 严禁出现任何编辑控件、任何阈值数字与换装自动审批条件 -->
        <template v-if="readOnly">
          <section class="sop-card">
            <div class="card-head">
              <h2>级别提示与所需证据</h2>
              <span class="head-sub">一线指引</span>
            </div>

            <div v-if="displayedEntry" class="readonly-hint-box">
              <span class="hint-label">该故障所属级别提示</span>
              <p class="hint-text">
                {{ displayedEntry.gradeHint || '请严格按两段式 SOP 收集完整故障现象与对应记录，由系统与质检复核判定。' }}
              </p>
              <small class="hint-footnote">
                为防止反向凑证据，具体审批阈值由系统后台校验，知识库仅提供取证要求与规范说明。
              </small>
            </div>

            <div class="required-evidence-block">
              <div class="sub-head">
                <h3>所需证据清单</h3>
                <span class="rule-summary">{{ readonlyEvidenceList.length }} 项要求</span>
              </div>

              <ul v-if="readonlyEvidenceList.length" class="evidence-checklist">
                <li
                  v-for="item in readonlyEvidenceList"
                  :key="item.code"
                  class="evidence-check-item"
                >
                  <div class="evidence-item-top">
                    <strong>{{ item.label }}</strong>
                    <span class="evidence-tag teal">建议必传</span>
                  </div>
                  <small>{{ item.tip }}</small>
                </li>
              </ul>
              <p v-else class="muted-note">当前分类暂无特殊必传证据要求，请按标准流程留痕。</p>
            </div>
          </section>

          <section class="sop-card">
            <div class="card-head">
              <h2>标准证据样例说明</h2>
              <span class="head-sub">采集规范</span>
            </div>

            <ul class="sample-guide-list">
              <li
                v-for="sample in readonlyEvidenceSamples"
                :key="sample.code"
                class="sample-guide-item"
              >
                <div class="sample-head">
                  <span class="evidence-tag teal-deep">{{ sample.label }}</span>
                </div>
                <p class="sample-desc">{{ sample.sample }}</p>
              </li>
            </ul>

            <footer v-if="displayedEntry" class="readonly-meta-footer">
              <span>生效版本：{{ displayedEntry.version }}</span>
              <span v-if="displayedEntry.updatedAt">更新于 {{ formatTimestamp(displayedEntry.updatedAt) }}</span>
            </footer>
          </section>
        </template>

        <!-- ===== 分支 2：管理 → SOP 与规则（readOnly === false，唯一写端）===== -->
        <template v-else>
          <!-- 上卡：仅管理侧可见的 A/B/C 阈值与换装自动审批条件 -->
          <section class="sop-card">
            <div class="card-head">
              <div>
                <h2>分级阈值与审批规则</h2>
                <span class="admin-only-pill">仅管理侧可见 · 不对一线展示</span>
              </div>
              <button
                v-if="displayedEntry && !isEditingRules"
                type="button"
                class="btn-sop btn-secondary btn-sm"
                :disabled="busy"
                @click="enterEditRules"
              >
                ⚙ 设置规则
              </button>
              <button
                v-else-if="displayedEntry"
                type="button"
                class="btn-sop btn-secondary btn-sm"
                :disabled="busy"
                @click="cancelRuleChanges"
              >
                取消编辑
              </button>
            </div>

            <template v-if="displayedEntry">
              <!-- A/B/C 阈值配置 -->
              <div class="grade-list" aria-label="A/B/C 分级阈值配置">
                <div class="grade-item grade-a">
                  <span class="grade-chip">A级 阈值条件</span>
                  <input
                    v-if="isEditingRules"
                    v-model.trim="ruleDraft.gradeThresholds.A"
                    type="text"
                    class="sop-input"
                    maxlength="120"
                    aria-label="A级阈值条件"
                    placeholder="填写 A 级触发阈值"
                  >
                  <small v-else>{{ ruleDraft.gradeThresholds.A || '未配置 A 级阈值' }}</small>
                </div>
                <div class="grade-item grade-b">
                  <span class="grade-chip">B级 阈值条件</span>
                  <input
                    v-if="isEditingRules"
                    v-model.trim="ruleDraft.gradeThresholds.B"
                    type="text"
                    class="sop-input"
                    maxlength="120"
                    aria-label="B级阈值条件"
                    placeholder="填写 B 级触发阈值"
                  >
                  <small v-else>{{ ruleDraft.gradeThresholds.B || '未配置 B 级阈值' }}</small>
                </div>
                <div class="grade-item grade-c">
                  <span class="grade-chip">C级 阈值条件（禁止换装）</span>
                  <input
                    v-if="isEditingRules"
                    v-model.trim="ruleDraft.gradeThresholds.C"
                    type="text"
                    class="sop-input"
                    maxlength="120"
                    aria-label="C级阈值条件"
                    placeholder="填写 C 级判定阈值"
                  >
                  <small v-else>{{ ruleDraft.gradeThresholds.C || '未配置 C 级阈值' }}</small>
                </div>
              </div>

              <!-- 换装自动审批条件（含开关） -->
              <div class="auto-rules">
                <div class="sub-head">
                  <h3>换装自动审批条件</h3>
                  <div class="sub-head-right">
                    <span class="rule-summary">
                      {{ autoPassCount }}/{{ ruleDraft.autoApprovalRules.length }} 项自动通过
                    </span>
                    <button
                      v-if="isEditingRules"
                      type="button"
                      class="btn-sop btn-secondary btn-sm"
                      @click="addAutoRule"
                    >
                      + 条件
                    </button>
                  </div>
                </div>

                <ul class="rule-list">
                  <li
                    v-for="(rule, rIdx) in ruleDraft.autoApprovalRules"
                    :key="rule.id || rIdx"
                    class="rule-row"
                  >
                    <div class="rule-info">
                      <input
                        v-if="isEditingRules"
                        v-model.trim="rule.label"
                        type="text"
                        class="sop-input"
                        maxlength="80"
                        :aria-label="'自动审批条件 ' + (rIdx + 1)"
                        placeholder="请输入审批条件名称"
                      >
                      <strong v-else>{{ rule.label }}</strong>
                      <small v-if="rule.desc">{{ rule.desc }}</small>
                    </div>

                    <div class="rule-actions">
                      <button
                        type="button"
                        role="switch"
                        class="rule-switch"
                        :class="{ on: rule.enabled }"
                        :aria-checked="rule.enabled"
                        :aria-label="rule.label + '：当前为' + (rule.enabled ? '自动通过' : '人工复核')"
                        @click="toggleAutoRule(rule)"
                      >
                        <span class="switch-track" aria-hidden="true">
                          <span class="switch-thumb" />
                        </span>
                        <span class="switch-label">
                          {{ rule.enabled ? '自动通过' : '人工复核' }}
                        </span>
                      </button>

                      <button
                        v-if="isEditingRules && ruleDraft.autoApprovalRules.length > 1"
                        type="button"
                        class="btn-sop btn-danger-ghost"
                        @click="removeAutoRule(rIdx)"
                      >
                        删
                      </button>
                    </div>
                  </li>
                </ul>

                <!-- 规则保存条：存在变更或处于设置规则模式时出现 -->
                <div v-if="isEditingRules || hasPendingRuleChanges" class="rule-save-bar">
                  <div class="rule-version-row">
                    <span class="limit-hint">规则变更将生成新版本并可发布同步知识库</span>
                    <input
                      v-model.trim="ruleDraft.version"
                      type="text"
                      class="sop-input version-mini-input"
                      maxlength="20"
                      aria-label="规则保存版本号"
                      placeholder="版本号"
                    >
                  </div>
                  <div class="rule-save-actions">
                    <button
                      type="button"
                      class="btn-sop btn-secondary btn-sm"
                      :disabled="busy"
                      @click="cancelRuleChanges"
                    >
                      还原
                    </button>
                    <button
                      type="button"
                      class="btn-sop btn-secondary btn-sm"
                      :disabled="busy"
                      @click="saveRuleChanges(false)"
                    >
                      保存草稿
                    </button>
                    <button
                      type="button"
                      class="btn-sop btn-primary btn-sm"
                      :disabled="busy"
                      @click="saveRuleChanges(true)"
                    >
                      保存并发布
                    </button>
                  </div>
                </div>
              </div>
            </template>
          </section>

          <!-- 下卡：版本记录（支持点击草稿条目的「发布」调用 publishAdminSop(id)） -->
          <section class="sop-card">
            <div class="card-head">
              <h2>版本记录</h2>
              <span class="head-sub">共 {{ activeGroup ? activeGroup.versions.length : 0 }} 个版本</span>
            </div>

            <ul v-if="activeGroup && activeGroup.versions.length" class="version-list">
              <li
                v-for="ver in activeGroup.versions"
                :key="ver.id"
                class="version-item"
                :class="[statusTone(ver.status), { selected: displayedEntry && ver.id === displayedEntry.id }]"
                @click="selectVersion(ver.id)"
              >
                <div class="version-top">
                  <strong class="version-label">
                    {{ ver.version }} · {{ statusLabel(ver.status) }}
                  </strong>
                  <div class="version-top-right">
                    <span class="version-state" :class="statusTone(ver.status)">
                      {{ statusTag(ver.status) }}
                    </span>
                    <button
                      v-if="ver.status === 'draft'"
                      type="button"
                      class="btn-sop btn-primary btn-xs"
                      :disabled="busy"
                      @click.stop="publishVersion(ver)"
                    >
                      发布
                    </button>
                  </div>
                </div>
                <div class="version-meta">
                  <span>{{ ver.updatedBy || 'system' }}</span>
                  <span>{{ formatTimestamp(ver.updatedAt) }}</span>
                </div>
              </li>
            </ul>
            <p v-else class="muted-note">暂无版本记录</p>
          </section>
        </template>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import {
  listAdminSops,
  listKnowledgeSops,
  publishAdminSop,
  saveAdminSop
} from '../api/workbench'
import { EVIDENCE_TYPE_LABELS, evidenceTypeLabel } from '../status'

const props = defineProps({
  readOnly: { type: Boolean, default: true }
})

const CATEGORY_ORDER = ['无法充电', '充电中断', '设备离线', '计费异常', '安装问题', '其他']

const EVIDENCE_SAMPLE_GUIDE = {
  photo: {
    sample: '桩体铭牌（含清晰 SN 条码）、指示灯/屏幕告警画面及配电箱空开全景照',
    tip: '保持光线清晰、正面拍摄，避免条码与告警字符反光遮挡'
  },
  video: {
    sample: '插枪启动至报错跳转的 10–30 秒连续短视频或手机端操作录屏',
    tip: '完整覆盖启动指令下发与桩端/车端现象反馈'
  },
  timestamp_log: {
    sample: '精确到分钟的异常发生时间点（如 14:25）、车端提示与故障现象描述',
    tip: '用于后台精确比对充电会话与告警报文时间轴'
  },
  site_compare: {
    sample: '同场站相邻正常桩交叉换车/换枪测试结果与现场信号对照情况',
    tip: '辅助定位单桩硬件故障、场站供电或网络环境问题'
  },
  swap_test: {
    sample: '单变量替换备用枪线/模块后的实车复测现象与前后对比记录',
    tip: '明确故障是否随部件转移或恢复正常'
  },
  platform_data: {
    sample: '按桩编码自动关联平台告警码、停机原因与历史充电会话报文',
    tip: '系统将自动校验后台报文，一线无需手动编造或猜测'
  }
}

const evidenceOptions = Object.keys(EVIDENCE_TYPE_LABELS).map(value => ({
  value,
  label: EVIDENCE_TYPE_LABELS[value]
}))

// 内置只读回退样例：严格剥离 A/B/C 阈值数字与换装自动审批条件
const FALLBACK_KNOWLEDGE_SOPS = [
  {
    id: 'fallback-no-charge',
    category: '无法充电',
    title: '无法充电 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '时序+可视混合类',
    customerSteps: [
      '确认车辆与枪头连接牢固并无异物',
      '确认桩体通电、空开合闸与指示灯状态',
      '引导客户断电1分钟后重启复测'
    ],
    onsiteSteps: [
      '现场测量主回路电压与PE接地',
      '读取设备故障码与实时报文',
      '执行换件或换桩判定并留存旧件'
    ],
    requiredEvidence: ['photo', 'video', 'platform_data'],
    gradeHint: 'A级需平台报文/日志佐证；B级需现场采集与对照；C级仅现象描述禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-interrupt',
    category: '充电中断',
    title: '充电中断 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '时序类（故障时点+充电记录）',
    customerSteps: [
      '确认中断精确时间（到分钟）及车端提示',
      '观察桩体告警灯并重新插拔枪头',
      '条件允许时换车或同站另一把枪对照测试'
    ],
    onsiteSteps: [
      '调取平台充电会话停止原因与电流曲线',
      '现场检测枪温、接触器与接地电阻',
      '单变量替换测试并留痕'
    ],
    requiredEvidence: ['timestamp_log', 'platform_data', 'swap_test'],
    gradeHint: 'A级需平台停止原因+日志；B级需同站对照或替换测试；C级禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-offline',
    category: '设备离线',
    title: '设备离线 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '时序/通信类（照片无效）',
    customerSteps: [
      '确认桩体电源正常与网络指示灯状态',
      '记录断网大致时间并观察周边4G信号',
      '引导断电1分钟后上电观察重连'
    ],
    onsiteSteps: [
      '核查SIM卡槽接触与天线连接',
      '核对SIM到期时间与程序版本',
      '采集本地通信日志并与同站其他桩对照'
    ],
    requiredEvidence: ['timestamp_log', 'site_compare', 'platform_data'],
    gradeHint: 'A级需通信日志/SIM状态佐证；B级需现场信号与卡槽核查；C级禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-billing',
    category: '计费异常',
    title: '计费异常 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '平台账单+电量对照',
    customerSteps: [
      '核对充电订单号、起止时间与账单金额',
      '确认车端表显充电量与结算电量差异',
      '留存异常订单截图或订单编号'
    ],
    onsiteSteps: [
      '核查电表计量模块脉冲与CT接线',
      '对比平台原始报文电量与本地电表读数',
      '确认计量误差是否超出国标范围'
    ],
    requiredEvidence: ['platform_data', 'timestamp_log'],
    gradeHint: 'A级需电表与平台报文差异佐证；B级需现场校准记录；C级禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-install',
    category: '安装问题',
    title: '安装问题 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '可视类（现场照片为主）',
    customerSteps: [
      '确认现场无漏电冒烟等安全风险',
      '请客户拍摄安装位置、配电箱与铭牌照片',
      '核对原始安装验收单与施工服务商'
    ],
    onsiteSteps: [
      '现场核验线径、空开规格与接地阻值',
      '留存整改前后对比照片',
      '关联原安装服务商责任归属'
    ],
    requiredEvidence: ['photo', 'video'],
    gradeHint: '安装施工问题优先走安装整改，非设备本身故障禁止换装',
    updatedAt: '2026-11-25 10:00'
  },
  {
    id: 'fallback-other',
    category: '其他',
    title: '其他异常 · SOP步骤',
    version: 'V2.3',
    evidenceMode: '混合类',
    customerSteps: [
      '确认现象发生时间与具体表现',
      '收集清晰照片或短视频记录现场情况',
      '排查基础供电与环境因素'
    ],
    onsiteSteps: [
      '现场复现故障现象并采集日志',
      '按实际症状归入对应故障大类',
      '形成书面排查结论'
    ],
    requiredEvidence: ['photo', 'timestamp_log'],
    gradeHint: '需先归类补充证据后再行判定，C级禁止换装',
    updatedAt: '2026-11-25 10:00'
  }
]

const loading = ref(false)
const busy = ref(false)
const categoryGroups = ref([])
const activeCategoryName = ref('')
const selectedVersionId = ref(null)
const categorySearch = ref('')
const statusNotice = reactive({ text: '', tone: 'info' })

const isEditingSop = ref(false)
const sopDraft = reactive({
  title: '',
  version: '',
  evidenceMode: '',
  gradeHint: '',
  customerSteps: [],
  onsiteSteps: [],
  requiredEvidence: []
})

const isEditingRules = ref(false)
const hasPendingRuleChanges = ref(false)
const ruleDraft = reactive({
  version: '',
  gradeThresholds: { A: '', B: '', C: '' },
  autoApprovalRules: []
})

const showNewFaultForm = ref(false)
const newFault = reactive(createEmptyNewFault())

function createEmptyNewFault() {
  return {
    category: '',
    title: '',
    version: 'V1.0',
    evidenceMode: '时序+可视混合类',
    gradeHint: 'A级需平台报文/日志佐证；B级需现场采集与对照；C级仅现象描述禁止换装',
    customerSteps: ['拍摄设备铭牌与故障指示灯状态', '录制故障触发及复现短视频', '断电复位并记录故障发生时点'],
    onsiteSteps: ['现场检测供电、接线与相关部件状态', '读取设备故障码与平台报文对照', '确认故障部件后留痕处置并回收旧件'],
    requiredEvidence: ['photo', 'video', 'platform_data'],
    gradeThresholds: {
      A: '平台故障码命中且有日志',
      B: '现场实测/对照记录>=2项',
      C: '仅口述现象无日志'
    },
    autoApprovalRules: [
      { id: 'r1', label: '条件1 故障码命中换装清单', enabled: true },
      { id: 'r2', label: '条件2 必填证据齐全且设备在保', enabled: true },
      { id: 'r3', label: '条件3 同SN历史换装次数未超阈值(<=2次)', enabled: true }
    ]
  }
}

function normalizeSteps(raw) {
  if (!Array.isArray(raw)) return []
  return raw
    .map(item => (typeof item === 'string' ? item : item && item.text ? String(item.text) : ''))
    .map(text => text.trim())
    .filter(Boolean)
}

function normalizeEvidence(raw) {
  if (!Array.isArray(raw)) return []
  return raw.map(item => String(item || '').trim()).filter(Boolean)
}

function normalizeThresholds(raw) {
  const source = raw && typeof raw === 'object' ? raw : {}
  return {
    A: String(source.A || ''),
    B: String(source.B || ''),
    C: String(source.C || '')
  }
}

function normalizeAutoRules(raw) {
  if (!Array.isArray(raw)) return []
  return raw.map((rule, idx) => ({
    id: rule && rule.id ? String(rule.id) : `r${idx + 1}`,
    label: rule && rule.label ? String(rule.label) : `条件${idx + 1}`,
    desc: rule && rule.desc ? String(rule.desc) : '',
    enabled: Boolean(rule && (rule.enabled !== undefined ? rule.enabled : rule.autoApprove))
  }))
}

// 只读视图严格清洗字段，绝不保留 gradeThresholds / autoApprovalRules
function toReadOnlyEntry(row) {
  return {
    id: row.id,
    category: String(row.category || '').trim() || '未分类',
    title: String(row.title || '').trim() || String(row.category || ''),
    version: String(row.version || 'V1.0'),
    evidenceMode: String(row.evidenceMode || ''),
    customerSteps: normalizeSteps(row.customerSteps).slice(0, 3),
    onsiteSteps: normalizeSteps(row.onsiteSteps),
    requiredEvidence: normalizeEvidence(row.requiredEvidence),
    gradeHint: String(row.gradeHint || ''),
    updatedAt: row.updatedAt || ''
  }
}

function toAdminEntry(row) {
  return {
    ...toReadOnlyEntry(row),
    status: String(row.status || 'published'),
    gradeThresholds: normalizeThresholds(row.gradeThresholds),
    autoApprovalRules: normalizeAutoRules(row.autoApprovalRules),
    updatedBy: String(row.updatedBy || '')
  }
}

function groupByCategory(entries, isAdmin) {
  const map = new Map()
  entries.forEach(raw => {
    const entry = isAdmin ? toAdminEntry(raw) : toReadOnlyEntry(raw)
    if (!map.has(entry.category)) {
      map.set(entry.category, [])
    }
    map.get(entry.category).push(entry)
  })

  const groups = Array.from(map.entries()).map(([category, versions]) => {
    const publishedEntry = versions.find(v => v.status === 'published') || null
    const latestDraft = versions.find(v => v.status === 'draft') || null
    const defaultEntry = isAdmin ? (publishedEntry || versions[0]) : versions[0]
    return {
      category,
      versions,
      publishedEntry,
      latestDraft,
      defaultEntry
    }
  })

  groups.sort((a, b) => {
    const idxA = CATEGORY_ORDER.indexOf(a.category)
    const idxB = CATEGORY_ORDER.indexOf(b.category)
    if (idxA !== -1 && idxB !== -1) return idxA - idxB
    if (idxA !== -1) return -1
    if (idxB !== -1) return 1
    return a.category.localeCompare(b.category, 'zh-Hans-CN')
  })

  return groups
}

const activeGroup = computed(() => {
  if (!categoryGroups.value.length) return null
  return categoryGroups.value.find(g => g.category === activeCategoryName.value) || categoryGroups.value[0]
})

const displayedEntry = computed(() => {
  const group = activeGroup.value
  if (!group) return null
  if (!props.readOnly && selectedVersionId.value != null) {
    const found = group.versions.find(v => v.id === selectedVersionId.value)
    if (found) return found
  }
  return group.defaultEntry
})

const isViewingNonDefaultVersion = computed(() => {
  if (props.readOnly || !activeGroup.value || !displayedEntry.value) return false
  return displayedEntry.value.id !== activeGroup.value.defaultEntry.id
})

const filteredGroups = computed(() => {
  const kw = categorySearch.value.toLowerCase()
  if (!kw) return categoryGroups.value
  return categoryGroups.value.filter(g => g.category.toLowerCase().includes(kw))
})

const displayedCustomerSteps = computed(() => {
  if (!props.readOnly && isEditingSop.value) return sopDraft.customerSteps
  return displayedEntry.value ? displayedEntry.value.customerSteps.slice(0, 3) : []
})

const displayedOnsiteSteps = computed(() => {
  if (!props.readOnly && isEditingSop.value) return sopDraft.onsiteSteps
  return displayedEntry.value ? displayedEntry.value.onsiteSteps : []
})

// 只读右栏所需证据清单与标准样例
const readonlyEvidenceList = computed(() => {
  if (!displayedEntry.value) return []
  return displayedEntry.value.requiredEvidence.map(code => {
    const guide = EVIDENCE_SAMPLE_GUIDE[code] || {
      sample: '请按现场实际情况收集清晰可追溯的证明材料',
      tip: '确保设备编码、异常现象与时间点清晰可辨'
    }
    return {
      code,
      label: evidenceTypeLabel(code) || code,
      tip: guide.tip
    }
  })
})

const readonlyEvidenceSamples = computed(() => {
  const codes = displayedEntry.value && displayedEntry.value.requiredEvidence.length
    ? displayedEntry.value.requiredEvidence
    : ['photo', 'video', 'timestamp_log', 'platform_data']
  return codes.map(code => {
    const guide = EVIDENCE_SAMPLE_GUIDE[code] || {
      sample: '提供可核验的故障现象记录或设备状态信息',
      tip: ''
    }
    return {
      code,
      label: evidenceTypeLabel(code) || code,
      sample: guide.sample
    }
  })
})

const autoPassCount = computed(() => {
  return ruleDraft.autoApprovalRules.filter(r => r.enabled).length
})

function syncRuleDraftFromEntry(entry) {
  if (!entry || props.readOnly) {
    ruleDraft.version = ''
    ruleDraft.gradeThresholds = { A: '', B: '', C: '' }
    ruleDraft.autoApprovalRules = []
    hasPendingRuleChanges.value = false
    isEditingRules.value = false
    return
  }
  ruleDraft.version = nextVersionString(entry.version)
  ruleDraft.gradeThresholds = { ...entry.gradeThresholds }
  ruleDraft.autoApprovalRules = entry.autoApprovalRules.map(r => ({ ...r }))
  hasPendingRuleChanges.value = false
  isEditingRules.value = false
}

watch(displayedEntry, entry => {
  if (!props.readOnly) {
    syncRuleDraftFromEntry(entry)
  }
})

function nextVersionString(ver) {
  const raw = String(ver || 'V1.0').trim()
  const match = raw.match(/^(V?)(\d+)\.(\d+)$/i)
  if (match) {
    const prefix = match[1] ? match[1].toUpperCase() : 'V'
    const major = Number(match[2])
    const minor = Number(match[3]) + 1
    return `${prefix}${major}.${minor}`
  }
  return raw + '.1'
}

function setNotice(text, tone = 'info') {
  statusNotice.text = text
  statusNotice.tone = tone
}

async function loadSops(preferredCategory, preferredVersionId) {
  loading.value = true
  isEditingSop.value = false
  isEditingRules.value = false
  hasPendingRuleChanges.value = false
  if (props.readOnly) {
    showNewFaultForm.value = false
  }

  try {
    if (props.readOnly) {
      const rows = await listKnowledgeSops()
      const list = Array.isArray(rows) && rows.length ? rows : FALLBACK_KNOWLEDGE_SOPS
      if (!Array.isArray(rows) || !rows.length) {
        setNotice('后端暂无已发布 SOP，当前展示内置只读样例', 'warn')
      }
      categoryGroups.value = groupByCategory(list, false)
    } else {
      const rows = await listAdminSops()
      categoryGroups.value = groupByCategory(Array.isArray(rows) ? rows : [], true)
    }

    const targetCategory = preferredCategory || activeCategoryName.value
    const foundGroup = categoryGroups.value.find(g => g.category === targetCategory)
    activeCategoryName.value = foundGroup
      ? foundGroup.category
      : (categoryGroups.value[0] ? categoryGroups.value[0].category : '')
    selectedVersionId.value = preferredVersionId || null

    if (!props.readOnly && displayedEntry.value) {
      syncRuleDraftFromEntry(displayedEntry.value)
    }
  } catch (err) {
    if (props.readOnly) {
      categoryGroups.value = groupByCategory(FALLBACK_KNOWLEDGE_SOPS, false)
      activeCategoryName.value = categoryGroups.value[0] ? categoryGroups.value[0].category : ''
      selectedVersionId.value = null
      setNotice('知识库接口暂不可用，已自动切换为内置只读样例', 'warn')
    } else {
      setNotice(err && err.message ? err.message : '加载 SOP 与规则列表失败', 'danger')
    }
  } finally {
    loading.value = false
  }
}

watch(() => props.readOnly, () => {
  statusNotice.text = ''
  loadSops()
}, { immediate: true })

function selectCategory(category) {
  isEditingSop.value = false
  selectedVersionId.value = null
  activeCategoryName.value = category
}

function selectVersion(id) {
  if (props.readOnly) return
  isEditingSop.value = false
  selectedVersionId.value = id
}

// ===== 新建故障（仅管理侧）=====
function toggleNewFaultForm() {
  if (props.readOnly) return
  showNewFaultForm.value = !showNewFaultForm.value
  if (showNewFaultForm.value) {
    Object.assign(newFault, createEmptyNewFault())
  }
}

function cancelNewFault() {
  showNewFaultForm.value = false
}

function addNewFaultStep(stage) {
  if (stage === 'customer') {
    if (newFault.customerSteps.length < 3) newFault.customerSteps.push('')
  } else if (newFault.onsiteSteps.length < 3) {
    newFault.onsiteSteps.push('')
  }
}

function removeNewFaultStep(stage, idx) {
  if (stage === 'customer') {
    if (newFault.customerSteps.length > 1) newFault.customerSteps.splice(idx, 1)
  } else if (newFault.onsiteSteps.length > 1) {
    newFault.onsiteSteps.splice(idx, 1)
  }
}

async function submitNewFault(publishNow) {
  if (props.readOnly || busy.value) return
  const category = newFault.category.trim()
  const title = (newFault.title || `${category} · SOP步骤`).trim()
  const version = newFault.version.trim() || 'V1.0'
  const customerSteps = newFault.customerSteps.map(s => s.trim()).filter(Boolean).slice(0, 3)
  const onsiteSteps = newFault.onsiteSteps.map(s => s.trim()).filter(Boolean)

  if (!category || !title || !version) {
    setNotice('请填写故障分类名称、SOP 标题和版本号', 'danger')
    return
  }
  if (!customerSteps.length || customerSteps.length > 3) {
    setNotice('客户可执行步骤需控制在 1 到 3 步以内', 'danger')
    return
  }
  if (!onsiteSteps.length) {
    setNotice('请至少填写 1 条上门验证步骤', 'danger')
    return
  }

  busy.value = true
  try {
    await saveAdminSop({
      category,
      title,
      version,
      evidenceMode: newFault.evidenceMode.trim(),
      customerSteps,
      onsiteSteps,
      requiredEvidence: [...newFault.requiredEvidence],
      gradeHint: newFault.gradeHint.trim(),
      gradeThresholds: { ...newFault.gradeThresholds },
      autoApprovalRules: newFault.autoApprovalRules.map(r => ({ ...r })),
      publishNow
    })
    showNewFaultForm.value = false
    await loadSops(category)
    setNotice(
      publishNow
        ? `已创建「${category}」${version} 并发布生效，已同步至只读知识库`
        : `已保存「${category}」${version} 为待发布草稿`,
      'info'
    )
  } catch (err) {
    setNotice(err && err.message ? err.message : '新建故障 SOP 失败', 'danger')
  } finally {
    busy.value = false
  }
}

// ===== 中栏：编辑 SOP（仅管理侧）=====
function enterEditSop() {
  if (props.readOnly || !displayedEntry.value) return
  const entry = displayedEntry.value
  sopDraft.title = entry.title
  sopDraft.version = entry.status === 'draft' ? entry.version : nextVersionString(entry.version)
  sopDraft.evidenceMode = entry.evidenceMode
  sopDraft.gradeHint = entry.gradeHint
  sopDraft.customerSteps = entry.customerSteps.length ? [...entry.customerSteps.slice(0, 3)] : ['']
  sopDraft.onsiteSteps = entry.onsiteSteps.length ? [...entry.onsiteSteps] : ['']
  sopDraft.requiredEvidence = [...entry.requiredEvidence]
  isEditingSop.value = true
}

function cancelEditSop() {
  isEditingSop.value = false
}

function addDraftStep(stage) {
  if (stage === 'customer') {
    if (sopDraft.customerSteps.length < 3) sopDraft.customerSteps.push('')
  } else if (sopDraft.onsiteSteps.length < 3) {
    sopDraft.onsiteSteps.push('')
  }
}

function removeDraftStep(stage, idx) {
  if (stage === 'customer') {
    if (sopDraft.customerSteps.length > 1) sopDraft.customerSteps.splice(idx, 1)
  } else if (sopDraft.onsiteSteps.length > 1) {
    sopDraft.onsiteSteps.splice(idx, 1)
  }
}

async function saveEditSop(publishNow) {
  if (props.readOnly || !displayedEntry.value || busy.value) return
  const entry = displayedEntry.value
  const customerSteps = sopDraft.customerSteps.map(s => s.trim()).filter(Boolean).slice(0, 3)
  const onsiteSteps = sopDraft.onsiteSteps.map(s => s.trim()).filter(Boolean)

  if (!sopDraft.title.trim() || !sopDraft.version.trim()) {
    setNotice('SOP 标题和版本号不能为空', 'danger')
    return
  }
  if (!customerSteps.length || customerSteps.length > 3) {
    setNotice('客户可执行步骤需控制在 1 到 3 步以内', 'danger')
    return
  }
  if (!onsiteSteps.length) {
    setNotice('请至少保留 1 条上门验证步骤', 'danger')
    return
  }

  busy.value = true
  try {
    await saveAdminSop({
      category: entry.category,
      title: sopDraft.title.trim(),
      version: sopDraft.version.trim(),
      evidenceMode: sopDraft.evidenceMode.trim(),
      customerSteps,
      onsiteSteps,
      requiredEvidence: [...sopDraft.requiredEvidence],
      gradeHint: sopDraft.gradeHint.trim(),
      gradeThresholds: { ...ruleDraft.gradeThresholds },
      autoApprovalRules: ruleDraft.autoApprovalRules.map(r => ({
        id: r.id,
        label: r.label,
        enabled: Boolean(r.enabled),
        ...(r.desc ? { desc: r.desc } : {})
      })),
      publishNow
    })
    isEditingSop.value = false
    await loadSops(entry.category)
    setNotice(
      publishNow
        ? `已保存并发布「${entry.category}」${sopDraft.version.trim()}，已单向同步至知识库`
        : `已保存「${entry.category}」${sopDraft.version.trim()} 为草稿（待评审发布）`,
      'info'
    )
  } catch (err) {
    setNotice(err && err.message ? err.message : '保存 SOP 失败', 'danger')
  } finally {
    busy.value = false
  }
}

// ===== 右栏：分级阈值与换装自动审批规则（仅管理侧）=====
function enterEditRules() {
  if (props.readOnly || !displayedEntry.value) return
  isEditingRules.value = true
}

function cancelRuleChanges() {
  syncRuleDraftFromEntry(displayedEntry.value)
}

function toggleAutoRule(rule) {
  if (props.readOnly) return
  rule.enabled = !rule.enabled
  hasPendingRuleChanges.value = true
}

function addAutoRule() {
  if (props.readOnly) return
  const nextIndex = ruleDraft.autoApprovalRules.length + 1
  ruleDraft.autoApprovalRules.push({
    id: `r${Date.now()}`,
    label: `条件${nextIndex} 新增审批条件`,
    desc: '',
    enabled: false
  })
  hasPendingRuleChanges.value = true
}

function removeAutoRule(idx) {
  if (props.readOnly || ruleDraft.autoApprovalRules.length <= 1) return
  ruleDraft.autoApprovalRules.splice(idx, 1)
  hasPendingRuleChanges.value = true
}

async function saveRuleChanges(publishNow) {
  if (props.readOnly || !displayedEntry.value || busy.value) return
  const entry = displayedEntry.value
  const version = ruleDraft.version.trim() || nextVersionString(entry.version)
  const cleanRules = ruleDraft.autoApprovalRules
    .filter(r => r.label && r.label.trim())
    .map((r, idx) => ({
      id: r.id || `r${idx + 1}`,
      label: r.label.trim(),
      enabled: Boolean(r.enabled),
      ...(r.desc ? { desc: r.desc } : {})
    }))

  if (!cleanRules.length) {
    setNotice('请至少保留一条换装自动审批条件', 'danger')
    return
  }

  busy.value = true
  try {
    await saveAdminSop({
      category: entry.category,
      title: entry.title,
      version,
      evidenceMode: entry.evidenceMode,
      customerSteps: entry.customerSteps.slice(0, 3),
      onsiteSteps: entry.onsiteSteps,
      requiredEvidence: [...entry.requiredEvidence],
      gradeHint: entry.gradeHint,
      gradeThresholds: {
        A: ruleDraft.gradeThresholds.A.trim(),
        B: ruleDraft.gradeThresholds.B.trim(),
        C: ruleDraft.gradeThresholds.C.trim()
      },
      autoApprovalRules: cleanRules,
      publishNow
    })
    await loadSops(entry.category)
    setNotice(
      publishNow
        ? `「${entry.category}」规则 ${version} 已保存并发布生效`
        : `「${entry.category}」规则变更已保存为草稿 ${version}`,
      'info'
    )
  } catch (err) {
    setNotice(err && err.message ? err.message : '保存审批规则失败', 'danger')
  } finally {
    busy.value = false
  }
}

// ===== 顶部评审 / 发布 & 版本列表发布 =====
async function handleReview() {
  if (props.readOnly || !activeGroup.value || !displayedEntry.value || busy.value) return
  if (isEditingSop.value) {
    await saveEditSop(false)
    return
  }
  if (isEditingRules.value || hasPendingRuleChanges.value) {
    await saveRuleChanges(false)
    return
  }
  const draft = activeGroup.value.latestDraft
  if (draft) {
    setNotice(
      `已提交「${activeGroup.value.category}」草稿 ${draft.version} 评审，确认无误后可点击「发布」生效`,
      'warn'
    )
    return
  }
  setNotice(
    `「${activeGroup.value.category}」当前无待评审草稿，可先点击「编辑 SOP」或「⚙ 设置规则」生成新版本草稿`,
    'info'
  )
}

async function handleTopbarPublish() {
  if (props.readOnly || !activeGroup.value || !displayedEntry.value || busy.value) return
  if (isEditingSop.value) {
    await saveEditSop(true)
    return
  }
  if (isEditingRules.value || hasPendingRuleChanges.value) {
    await saveRuleChanges(true)
    return
  }
  const draft = activeGroup.value.latestDraft
  if (draft) {
    await publishVersion(draft)
    return
  }
  setNotice(
    `「${activeGroup.value.category}」当前版本 ${displayedEntry.value.version} 已为最新生效状态`,
    'info'
  )
}

async function publishVersion(ver) {
  if (props.readOnly || !ver || !ver.id || busy.value) return
  busy.value = true
  try {
    await publishAdminSop(ver.id)
    await loadSops(activeCategoryName.value)
    setNotice(`「${activeCategoryName.value}」${ver.version} 已发布生效，并已单向同步至知识库`, 'info')
  } catch (err) {
    setNotice(err && err.message ? err.message : '发布 SOP 版本失败', 'danger')
  } finally {
    busy.value = false
  }
}

function statusTone(status) {
  if (status === 'published') return 'published'
  if (status === 'draft') return 'reviewing'
  return 'archived'
}

function statusLabel(status) {
  if (status === 'published') return '已发布'
  if (status === 'draft') return '草稿（待发布）'
  return '已归档'
}

function statusTag(status) {
  if (status === 'published') return '生效中'
  if (status === 'draft') return '待发布'
  return '已归档'
}

function formatTimestamp(raw) {
  if (!raw) return ''
  return String(raw).replace('T', ' ').replace(/\.\d+.*$/, '').slice(0, 16)
}
</script>

<style scoped>
.sop-workspace {
  display: grid;
  gap: 14px;
  color: var(--ink, #334155);
  font-size: 13px;
  line-height: 1.5;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

/* 顶部操作条：浅色白卡片，松果青绿风格 */
.sop-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 10px;
}

.topbar-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}

.sop-breadcrumb {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--muted, #8a96a5);
}

.crumb-sep {
  color: #cbd5e1;
}

.crumb-current {
  color: var(--heading, #0f172a);
  font-size: 15px;
  font-weight: 700;
}

.mode-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.mode-badge.readonly {
  background: #e8f6f4;
  border: 1px solid #c5e4e1;
  color: #0f766e;
}

.mode-badge.admin {
  background: #fff5dd;
  border: 1px solid #f3e2b8;
  color: #a86c08;
}

.admin-only-pill {
  display: inline-flex;
  align-items: center;
  margin-top: 2px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #fff5dd;
  border: 1px solid #f3e2b8;
  color: #a86c08;
  font-size: 11px;
  font-weight: 600;
}

.topbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

/* 按钮体系：对齐松果青绿主题 */
.btn-sop {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  padding: 6px 13px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.btn-sop:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-sm {
  min-height: 30px;
  padding: 4px 10px;
}

.btn-xs {
  min-height: 24px;
  padding: 2px 8px;
  font-size: 11px;
  border-radius: 5px;
}

.btn-primary {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #0d9488;
  border-color: #0d9488;
}

.btn-secondary {
  border: 1px solid #dfe5ec;
  background: #fff;
  color: #475569;
}

.btn-secondary:hover:not(:disabled) {
  border-color: #0d9488;
  color: #0f766e;
  background: #e8f6f4;
}

.btn-danger-ghost {
  border: 1px solid #f3c6c3;
  background: #fff;
  color: #c0362c;
  min-height: 32px;
  padding: 4px 10px;
}

.btn-danger-ghost:hover:not(:disabled) {
  background: #fdecec;
}

/* 新建故障表单面板 */
.new-fault-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border: 1px solid #c5e4e1;
  border-radius: 10px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--heading, #0f172a);
}

.panel-head p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--muted, #8a96a5);
}

.new-fault-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.new-fault-grid.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.new-fault-stages {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mini-stage {
  display: grid;
  gap: 8px;
  padding: 12px;
  background: #fafcfd;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
}

.mini-stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mini-step-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.form-field {
  display: grid;
  gap: 5px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.evidence-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.evidence-check-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid #dce3ec;
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
}

.evidence-check-pill.checked {
  border-color: #0d9488;
  background: #e8f6f4;
  color: #0f766e;
  font-weight: 600;
}

.evidence-check-pill input {
  accent-color: #0f766e;
}

.new-fault-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 页内状态提示 */
.status-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
}

.status-banner.info {
  background: #e8f6f4;
  border: 1px solid #c5e4e1;
  color: #0f766e;
}

.status-banner.warn {
  background: #fff5dd;
  border: 1px solid #f3e2b8;
  color: #a86c08;
}

.status-banner.danger {
  background: #fdecec;
  border: 1px solid #f5c2be;
  color: #c0362c;
}

.banner-close {
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 16px;
  line-height: 1;
  padding: 2px 6px;
  cursor: pointer;
}

/* 三栏主体 */
.sop-columns {
  display: grid;
  grid-template-columns: minmax(210px, 240px) minmax(0, 1.45fr) minmax(290px, 340px);
  gap: 14px;
  align-items: start;
}

.sop-card {
  min-width: 0;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 12px;
  box-shadow: 0 2px 8px #132c4906;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.card-head h2 {
  margin: 0;
  color: var(--heading, #0f172a);
  font-size: 15px;
  font-weight: 700;
}

.head-sub {
  color: var(--muted, #8a96a5);
  font-size: 12px;
  white-space: nowrap;
}

/* 左栏：故障分类树 */
.tree-card {
  display: flex;
  flex-direction: column;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  padding: 6px 10px;
  margin-bottom: 10px;
  border: 1px solid #dce3ec;
  border-radius: 8px;
  background: #fff;
}

.search-box:focus-within {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px #0d94881f;
}

.search-box svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: #94a3b8;
  stroke-width: 2;
  flex: none;
}

.search-box input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #334155;
  font-size: 12px;
}

.category-tree {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 4px;
}

.tree-node {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 40px;
  padding: 8px 10px 8px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #475569;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.tree-node:hover {
  background: #f8fafc;
  color: #0f172a;
}

.tree-node.active {
  background: #e8f6f4;
  border-color: #cdebe6;
  color: #0f766e;
  font-weight: 700;
}

.tree-node.active::before {
  content: "";
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 3px;
  background: #0d9488;
}

.tree-node-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  font-size: 11px;
  font-weight: 500;
}

.tree-draft-dot {
  padding: 1px 4px;
  border-radius: 4px;
  background: #fff5dd;
  color: #a86c08;
  font-size: 10px;
  font-weight: 600;
}

.tree-ver {
  padding: 1px 5px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
}

.tree-node.active .tree-ver {
  background: #fff;
  color: #0f766e;
}

.tree-count {
  color: #94a3b8;
}

.tree-node.active .tree-count {
  color: #0d9488;
}

.tree-empty {
  padding: 20px 8px;
  text-align: center;
  color: var(--muted, #8a96a5);
  font-size: 12px;
}

.tree-empty p {
  margin: 0 0 6px;
}

.link-btn {
  border: 0;
  background: transparent;
  color: #0f766e;
  font-size: 12px;
  text-decoration: underline;
  cursor: pointer;
}

.tree-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--line, #eaeef2);
  color: var(--muted, #8a96a5);
  font-size: 12px;
}

.tree-filtered {
  color: #0f766e;
}

/* 中栏：两段式 SOP */
.main-head {
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.main-title-wrap {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.main-title-wrap h2 {
  font-size: 16px;
}

.version-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  background: #e8f6f4;
  border: 1px solid #c5e4e1;
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
}

.main-head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sop-meta-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  background: #f1f5f9;
  color: #334155;
  font-size: 11px;
  font-weight: 600;
}

.sop-summary-note {
  color: #64748b;
  font-size: 12px;
}

.sop-edit-meta {
  display: grid;
  gap: 10px;
  padding: 12px;
  margin-bottom: 12px;
  background: #f8fbfb;
  border: 1px solid #d7eeeb;
  border-radius: 10px;
}

.sop-stage {
  padding: 14px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 10px;
  background: #fafcfd;
}

.stage-onsite-wrap {
  margin-top: 12px;
}

.stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.stage-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stage-title h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--heading, #0f172a);
}

.stage-pill {
  padding: 1px 7px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
}

.stage-customer {
  background: #e8f6f4;
  color: #0f766e;
}

.stage-onsite {
  background: #fff5dd;
  color: #a86c08;
}

.stage-limit {
  font-size: 12px;
  color: var(--muted, #8a96a5);
}

.step-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.step-item {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
}

.step-index {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #e8f6f4;
  border: 1px solid #0d9488;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.step-index.onsite {
  background: #fff5dd;
  border-color: #eab65f;
  color: #a86c08;
}

.step-body {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.step-desc {
  margin: 2px 0 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
}

.evidence-tag {
  flex: none;
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.evidence-tag.teal {
  background: #e8f6f4;
  color: #0f766e;
}

.evidence-tag.teal-deep {
  background: #d7eeeb;
  color: #0c5f59;
}

/* SOP 编辑态 */
.step-editor {
  display: grid;
  gap: 8px;
}

.step-editor-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sop-input {
  width: 100%;
  min-width: 0;
  min-height: 34px;
  padding: 5px 10px;
  border: 1px solid #dce3ec;
  border-radius: 7px;
  background: #fff;
  color: #334155;
  font-size: 12px;
}

.sop-input:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px #0d94881f;
  outline: none;
}

.stage-edit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #dce3ec;
}

.limit-hint {
  font-size: 12px;
  color: #64748b;
}

/* 右栏：通用与只读知识库样式 */
.sop-right-col {
  display: grid;
  gap: 14px;
}

.readonly-hint-box {
  display: grid;
  gap: 6px;
  padding: 12px;
  background: #f5fbfa;
  border: 1px solid #cdebe6;
  border-radius: 8px;
}

.hint-label {
  font-size: 11px;
  font-weight: 700;
  color: #0f766e;
}

.hint-text {
  margin: 0;
  font-size: 13px;
  color: #1e293b;
  font-weight: 500;
}

.hint-footnote {
  font-size: 11px;
  color: #64748b;
}

.required-evidence-block {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line, #eaeef2);
}

.evidence-checklist,
.sample-guide-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.evidence-check-item,
.sample-guide-item {
  padding: 9px 10px;
  background: #fafcfd;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
}

.evidence-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 3px;
}

.evidence-item-top strong {
  font-size: 12px;
  color: #0f172a;
}

.evidence-check-item small {
  display: block;
  font-size: 11px;
  color: #64748b;
}

.sample-head {
  margin-bottom: 4px;
}

.sample-desc {
  margin: 0;
  font-size: 12px;
  color: #334155;
}

.readonly-meta-footer {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--line, #eaeef2);
  font-size: 11px;
  color: var(--muted, #8a96a5);
}

.muted-note {
  margin: 0;
  font-size: 12px;
  color: var(--muted, #8a96a5);
}

/* 右栏（管理侧）：阈值与开关规则 */
.grade-list {
  display: grid;
  gap: 8px;
}

.grade-item {
  display: grid;
  gap: 5px;
  padding: 9px 10px;
  border-radius: 8px;
  border: 1px solid var(--line, #eaeef2);
}

.grade-item small {
  font-size: 12px;
  color: #475569;
}

.grade-chip {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 700;
}

.grade-item.grade-a {
  background: #f5fbfa;
  border-color: #d7eeeb;
}

.grade-item.grade-a .grade-chip {
  background: #e8f6f4;
  color: #0f766e;
}

.grade-item.grade-b {
  background: #fffcf3;
  border-color: #f3e2b8;
}

.grade-item.grade-b .grade-chip {
  background: #fff5dd;
  color: #a86c08;
}

.grade-item.grade-c {
  background: #fef7f7;
  border-color: #f6cecb;
}

.grade-item.grade-c .grade-chip {
  background: #fdecec;
  color: #c0362c;
}

.auto-rules {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line, #eaeef2);
}

.sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.sub-head h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--heading, #0f172a);
}

.sub-head-right {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.rule-summary {
  font-size: 11px;
  color: #0f766e;
  background: #e8f6f4;
  padding: 2px 7px;
  border-radius: 999px;
}

.rule-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.rule-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 10px;
  background: #f8fafc;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
}

.rule-info {
  flex: 1 1 auto;
  min-width: 0;
  display: grid;
  gap: 2px;
}

.rule-info strong {
  font-size: 12px;
  color: #1e293b;
}

.rule-info small {
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}

.rule-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

/* 开关控件 */
.rule-switch {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: 1px solid #f3e2b8;
  border-radius: 999px;
  background: #fff5dd;
  color: #a86c08;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.rule-switch.on {
  border-color: #c5e4e1;
  background: #e8f6f4;
  color: #0f766e;
}

.switch-track {
  position: relative;
  width: 28px;
  height: 16px;
  border-radius: 999px;
  background: #d9a441;
  transition: background-color 0.15s ease;
}

.rule-switch.on .switch-track {
  background: #0d9488;
}

.switch-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.15s ease;
}

.rule-switch.on .switch-thumb {
  transform: translateX(12px);
}

.switch-label {
  white-space: nowrap;
}

.rule-save-bar {
  display: grid;
  gap: 8px;
  margin-top: 10px;
  padding: 10px;
  background: #f5fbfa;
  border: 1px solid #c5e4e1;
  border-radius: 8px;
}

.rule-version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.version-mini-input {
  max-width: 84px;
  min-height: 28px;
  padding: 3px 8px;
}

.rule-save-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
}

/* 版本记录 */
.version-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.version-item {
  padding: 10px 12px;
  border: 1px solid var(--line, #eaeef2);
  border-radius: 8px;
  background: #fafcfd;
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.version-item:hover {
  border-color: #99d5cf;
}

.version-item.selected {
  box-shadow: 0 0 0 2px #0d948826;
}

.version-item.published {
  background: #f5fbfa;
  border-color: #cdebe6;
}

.version-item.reviewing {
  background: #fffcf3;
  border-color: #f3e2b8;
}

.version-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.version-top-right {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.version-label {
  font-size: 12px;
  color: var(--heading, #0f172a);
}

.version-state {
  flex: none;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.version-state.published {
  background: #e8f6f4;
  color: #0f766e;
}

.version-state.archived {
  background: #f1f5f9;
  color: #64748b;
}

.version-state.reviewing {
  background: #fff5dd;
  color: #a86c08;
}

.version-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 5px;
  font-size: 11px;
  color: var(--muted, #8a96a5);
}

/* 响应式：窄屏自动堆叠 */
@media (max-width: 1120px) {
  .sop-columns {
    grid-template-columns: minmax(210px, 240px) minmax(0, 1fr);
  }

  .sop-right-col {
    grid-column: 1 / -1;
    grid-template-columns: 1fr 1fr;
  }

  .new-fault-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 780px) {
  .sop-columns,
  .new-fault-stages,
  .new-fault-grid,
  .new-fault-grid.three {
    grid-template-columns: 1fr;
  }

  .sop-right-col {
    grid-column: auto;
    grid-template-columns: 1fr;
  }

  .step-body,
  .step-editor-inline {
    flex-direction: column;
    align-items: stretch;
    gap: 6px;
  }
}
</style>
