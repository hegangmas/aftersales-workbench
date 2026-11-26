<template>
  <aside class="card sop-card tree-card" aria-label="故障分类树">
    <div class="card-head">
      <h2>故障分类树</h2>
      <span class="head-sub num">{{ loading ? '加载中…' : `共 ${categoryGroups.length} 个分类` }}</span>
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
          @click="emit('select', group.category)"
        >
          <span class="tree-node-name">{{ group.category }}</span>
          <span class="tree-node-meta">
            <span v-if="!readOnly && group.latestDraft" class="tag tag--warning" title="存在待发布草稿">草稿</span>
            <span class="tag tag--mono tree-ver">{{ group.defaultEntry.version }}</span>
            <span class="tree-count num">
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
        class="btn btn--link btn--sm"
        @click="categorySearch = ''"
      >
        清空搜索
      </button>
    </div>

    <footer class="tree-footer num">
      <span>共 {{ categoryGroups.length }} 个分类</span>
      <span v-if="categorySearch" class="tree-filtered">当前匹配 {{ filteredGroups.length }} 个</span>
    </footer>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  readOnly: { type: Boolean, default: true },
  loading: { type: Boolean, default: false },
  categoryGroups: { type: Array, default: () => [] },
  activeCategoryName: { type: String, default: '' }
})

const emit = defineEmits(['select'])

const categorySearch = ref('')

const filteredGroups = computed(() => {
  const kw = categorySearch.value.toLowerCase()
  if (!kw) return props.categoryGroups
  return props.categoryGroups.filter(g => g.category.toLowerCase().includes(kw))
})
</script>

<style scoped>
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.sop-card {
  padding: var(--card-py) var(--card-px) var(--space-5);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.card-head h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--fs-title-sm);
  line-height: var(--lh-title-sm);
  font-weight: var(--fw-semibold);
}

.head-sub {
  color: var(--text-tertiary);
  font-size: var(--fs-caption);
  white-space: nowrap;
}

.tree-card {
  display: flex;
  flex-direction: column;
}

.search-box {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: var(--control-h-md);
  padding: 0 10px;
  margin-bottom: var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  transition: border-color var(--dur-fast) var(--ease), box-shadow var(--dur-fast) var(--ease);
}

.search-box:hover {
  border-color: var(--border-hover);
}

.search-box:focus-within {
  border-color: var(--brand-600);
  box-shadow: var(--focus-ring);
}

.search-box svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: var(--text-tertiary);
  stroke-width: 1.75;
  flex: none;
}

.search-box input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  box-shadow: none;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--fs-body-sm);
}

.search-box input::placeholder {
  color: var(--text-disabled);
}

.search-box input:focus,
.search-box input:focus-visible {
  outline: none;
  box-shadow: none;
}

.category-tree {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 2px;
}

.tree-node {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--fs-body-sm);
  font-weight: var(--fw-medium);
  text-align: left;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.tree-node:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.tree-node.active {
  background: var(--brand-50);
  color: var(--brand-700);
  font-weight: var(--fw-semibold);
  box-shadow: inset 3px 0 0 var(--brand-600);
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
  gap: 6px;
  flex: none;
}

.tree-ver {
  font-size: var(--fs-caption);
}

.tree-node.active .tree-ver {
  background: var(--bg-surface);
  color: var(--brand-700);
}

.tree-count {
  min-width: 28px;
  text-align: right;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.tree-node.active .tree-count {
  color: var(--brand-700);
}

.tree-empty {
  padding: 24px 8px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--fs-caption);
}

.tree-empty p {
  margin: 0 0 6px;
}

.tree-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-subtle);
  color: var(--text-tertiary);
  font-size: var(--fs-caption);
}

.tree-filtered {
  color: var(--brand-700);
}
</style>
