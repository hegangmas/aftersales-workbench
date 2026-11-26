# 松果售后工单中台：UI 美化方案与 CSS 视觉规范

> 评审依据：6 张 1440×900 实机截图。我只做了视觉评审，没有读代码、改文件或执行命令。
> 业务铁律全部保留：全宽顶栏加二级下拉、一级菜单 5 项、工单下 3 个页签、各页字段、按钮数量和文案、知识库与管理端的读写隔离、青绿浅色品牌风。

---

## 一、全局视觉诊断与设计定调

### 1.1 五个共性问题

| # | 问题 | 截图中的典型位置 | 根因 |
|---|---|---|---|
| ① | **框里套框，边框太多** | 知识库和管理端：卡片 → 分段框 → 步骤框，共 3 层边框。设备档案：每个字段都是灰底框，单页有 30 多个框。工作台 SOP 清单每行一个灰框。调度大屏的表格在卡片里又包了一层边框。 | 用边框表达所有层级，没有区分「容器」和「分组」 |
| ② | **颜色语义失控** | 工作台「直接派单」用了品牌外的蓝色（约 `#3B82C4`）。坐席头像粉蓝随机。A/B/C 阈值是绿、黄、红三块大面积粉色底。「实时统计 / 模拟 / 建议必传」等小标签到处都是。 | 没有状态色板，也没有「一个区域只有一个主色焦点」的约束 |
| ③ | **字号阶梯太平，关键数字没有分量** | KPI 数字约 22px，和卡片标题差距不大。表单的 label 和 value 同色同字重。11px 文字太多（筛选计数、徽标），可读性低于标准。单号、SN、时间没用等宽字体，纵向对不齐。 | 缺少显示字号和数字字体，文字色只有两档 |
| ④ | **间距节奏乱，栏高参差，空白多** | 工作台三栏底部高低不齐；来电条和卡片之间约 32px，卡片之间约 24px，两者不统一。调度大屏「渠道分布」和工单表下方大片空白。工单池整页 70% 是空画布。 | 没有 4px 基准的间距阶梯，栅格未定义拉伸和吸顶规则 |
| ⑤ | **控件风格不统一** | 原生 checkbox；「查看全部」「>」是原生灰色按钮。按钮高度 28、32、36 混用。描边按钮「认领」和状态胶囊「就绪」长得几乎一样。工单池单号列溢出，压住了「故障」标签。排班表里的人名被截断成 `dispatc…`。 | 没有统一的控件尺寸和基础组件 |

### 1.2 气质升级方向：安静、精密、可信

售后中台是「长时间盯屏 + 高频决策」的工具。目标气质是**冷静的仪表盘感**，参照 Linear、Stripe Dashboard 这类浅色专业风，不是营销页那种热闹。

三条设计原则：

1. **主色只用在两处：主操作和选中态。** 其余地方用灰阶加低饱和的状态色。页面一屏内实心主色按钮不超过 2 个。
2. **层级靠底色和 1px 边框表达，不靠阴影。** 结构固定为：冷灰画布 → 白色卡片（1px 边框 + 极轻阴影）→ 卡内分组（浅灰底、无边框）。**卡片内部不允许再出现带边框的容器套带边框的子项。**
3. **数字是主角。** 单号、SN、时间、计数一律用 `tabular-nums` 等宽数字。KPI 用 32px 显示字号，值为 0 时降为灰色，减少噪音。

---

## 二、Design Tokens 与基础组件规范（完整 CSS）

```css
/* =========================================================
   松果售后工单中台 · Design Tokens v2.0
   基准：1440×900，4px 间距网格
   ========================================================= */
:root {
  /* ---------- 品牌色 ---------- */
  --brand-900: #134E4A;
  --brand-800: #115E59;   /* 主按钮 hover */
  --brand-700: #0F766E;   /* 主色：主按钮 / 选中文字 */
  --brand-600: #0D9488;   /* 辅色：进度、图表、图标 */
  --brand-200: #99DBD2;
  --brand-100: #CCEDE8;
  --brand-50:  #E8F6F4;   /* 浅青绿点缀：选中底 */

  /* ---------- 分层背景（带一点青调的冷灰） ---------- */
  --bg-canvas:       #F3F5F5;   /* 页面画布 */
  --bg-surface:      #FFFFFF;   /* 卡片 */
  --bg-subtle:       #F8FAFA;   /* 卡内分组 / 表头 */
  --bg-muted:        #F1F4F4;   /* 只读锁定框 / 分段控件底 */
  --bg-hover:        #F4F8F7;   /* 行和菜单 hover（带青调） */
  --bg-selected:     #E8F6F4;
  --bg-overlay:      rgba(17, 33, 33, 0.40);

  /* ---------- 边框 ---------- */
  --border-subtle:   #EDF0F0;   /* 表格行分隔、卡内分隔 */
  --border-default:  #E2E7E7;   /* 卡片描边 */
  --border-strong:   #CFD7D7;   /* 输入框、次按钮 */
  --border-hover:    #B5C0C0;
  --border-brand:    #99D6CE;

  /* ---------- 文字 ---------- */
  --text-primary:    #152020;   /* 主文字 / 数值 */
  --text-secondary:  #4A5656;   /* 正文辅助 / 标签 */
  --text-tertiary:   #7A8686;   /* 说明、表头、时间 */
  --text-disabled:   #A8B2B2;
  --text-brand:      #0F766E;
  --text-on-brand:   #FFFFFF;

  /* ---------- 状态语义色（低饱和底 + 高对比字，对比度 ≥ 4.5:1） ---------- */
  --success-fg: #18794E;  --success-bg: #EAF6EF;  --success-border: #BFE3CE;  --success-solid: #1F8F5C;
  --warning-fg: #A15C07;  --warning-bg: #FEF5E6;  --warning-border: #F3D7A6;  --warning-solid: #D97706;
  --danger-fg:  #C0362C;  --danger-bg:  #FDEEEC;  --danger-border:  #F4C5C0;  --danger-solid:  #C0362C;
  --info-fg:    #2F5F8A;  --info-bg:    #EDF3F9;  --info-border:    #C9DAEA;
  --neutral-fg: #4A5656;  --neutral-bg: #F1F4F4;  --neutral-border: #E2E7E7;

  /* ---------- 图表 ---------- */
  --chart-1: #0D9488;  --chart-2: #C0362C;  --chart-3: #6B8E9E;  --chart-4: #C9A227;
  --chart-grid: #EEF1F1;  --chart-axis: #7A8686;

  /* ---------- 头像色板（低饱和，品牌邻近色） ---------- */
  --avatar-1-bg: #E8F6F4; --avatar-1-fg: #0F766E;
  --avatar-2-bg: #EDF1F5; --avatar-2-fg: #45607A;
  --avatar-3-bg: #F5F1E8; --avatar-3-fg: #7A5C2E;
  --avatar-4-bg: #EEF3EC; --avatar-4-fg: #4C6B45;

  /* ---------- 圆角 ---------- */
  --radius-xs: 4px;    /* tag、checkbox */
  --radius-sm: 6px;    /* 徽标、菜单项、小按钮 */
  --radius-md: 8px;    /* 按钮、输入框 */
  --radius-lg: 10px;   /* 卡内分组、来电条、下拉面板 */
  --radius-xl: 12px;   /* 卡片 */
  --radius-pill: 999px;

  /* ---------- 阴影层级 ---------- */
  --elev-0: none;
  --elev-1: 0 1px 2px rgba(15, 41, 41, 0.04);                                         /* 卡片 */
  --elev-2: 0 1px 2px rgba(15, 41, 41, 0.04), 0 4px 12px -2px rgba(15, 41, 41, 0.07); /* 卡片 hover / 吸底操作栏 */
  --elev-3: 0 2px 4px rgba(15, 41, 41, 0.04), 0 12px 32px -4px rgba(15, 41, 41, 0.14);/* 下拉 / popover */
  --elev-4: 0 24px 48px -12px rgba(15, 41, 41, 0.22);                                 /* 弹窗 */
  --elev-seg: 0 1px 2px rgba(15, 41, 41, 0.08), 0 0 0 1px rgba(15, 41, 41, 0.04);    /* 分段控件选中块 */
  --focus-ring:        0 0 0 3px rgba(13, 148, 136, 0.22);
  --focus-ring-danger: 0 0 0 3px rgba(192, 54, 44, 0.18);

  /* ---------- 间距阶梯 ---------- */
  --space-0: 0;   --space-1: 4px;  --space-2: 8px;   --space-3: 12px;
  --space-4: 16px; --space-5: 20px; --space-6: 24px;  --space-8: 32px;
  --space-10: 40px; --space-12: 48px;

  /* ---------- 版式 ---------- */
  --topbar-h: 56px;
  --page-px: 24px;          /* 页面左右边距 */
  --gutter: 16px;           /* 栏间距 / 卡片间距（全站统一） */
  --card-px: 20px;          /* 卡片内左右 padding */
  --card-py: 16px;
  --control-h-sm: 28px;
  --control-h-md: 32px;     /* 默认控件高度 */
  --control-h-lg: 36px;
  --control-h-xl: 40px;     /* 搜索主框 */
  --row-h: 48px;            /* 表格单行 */
  --row-h-2line: 64px;      /* 表格双行 */

  /* ---------- 字体 ---------- */
  --font-sans: "PingFang SC", "HarmonyOS Sans SC", "Microsoft YaHei", -apple-system,
               BlinkMacSystemFont, "Segoe UI", "Helvetica Neue", Arial, sans-serif;
  --font-num:  "Inter", "SF Pro Display", "DIN Alternate", var(--font-sans);
  --font-mono: "JetBrains Mono", "SF Mono", "Roboto Mono", Menlo, Consolas, monospace;

  /* 字号/行高：最小 12px，禁止 11px */
  --fs-caption: 12px;   --lh-caption: 18px;
  --fs-body-sm: 13px;   --lh-body-sm: 20px;   /* 表格、表单默认 */
  --fs-body:    14px;   --lh-body:    22px;
  --fs-title-sm:15px;   --lh-title-sm:22px;   /* 卡片标题 */
  --fs-title:   16px;   --lh-title:   24px;
  --fs-h2:      18px;   --lh-h2:      26px;   /* 页面标题 */
  --fs-display-sm: 24px; --lh-display-sm: 32px; /* 工单号 */
  --fs-display: 32px;   --lh-display: 40px;   /* KPI 数字 */
  --fw-regular: 400; --fw-medium: 500; --fw-semibold: 600; --fw-bold: 700;

  /* ---------- 动效 ---------- */
  --ease: cubic-bezier(0.2, 0, 0, 1);
  --dur-fast: 120ms;  --dur: 180ms;

  /* ---------- 层级 ---------- */
  --z-topbar: 100; --z-banner: 90; --z-dropdown: 200; --z-modal: 300;
}

/* =========================================================
   Base
   ========================================================= */
html, body {
  background: var(--bg-canvas);
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: var(--fs-body-sm);
  line-height: var(--lh-body-sm);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}
.num, .tabular, time, .col-num, .col-time { font-variant-numeric: tabular-nums; font-feature-settings: "tnum" 1; }
.mono { font-family: var(--font-mono); font-variant-numeric: tabular-nums; letter-spacing: 0; }
.text-secondary { color: var(--text-secondary); }
.text-tertiary  { color: var(--text-tertiary); }

.page {
  padding: var(--space-4) var(--page-px) var(--space-6);
  min-height: calc(100vh - var(--topbar-h) - 40px);  /* 40px = footer，保证页脚贴底 */
}
.app-footer {
  height: 40px; padding: 0 var(--page-px);
  display: flex; align-items: center; justify-content: space-between;
  font-size: var(--fs-caption); color: var(--text-tertiary);
  border-top: 1px solid var(--border-subtle); background: var(--bg-canvas);
}

/* =========================================================
   1. 全宽顶栏 + 二级下拉
   ========================================================= */
.app-topbar {
  position: sticky; top: 0; z-index: var(--z-topbar);
  height: var(--topbar-h);
  display: flex; align-items: center; gap: var(--space-2);
  padding: 0 var(--page-px);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: saturate(180%) blur(8px);
  border-bottom: 1px solid var(--border-default);
}
.app-topbar__brand { display: flex; align-items: center; gap: var(--space-3); }
.app-topbar__logo  { height: 26px; width: auto; }
.app-topbar__product {
  font-size: var(--fs-title-sm); font-weight: var(--fw-semibold); color: var(--text-primary);
  padding-left: var(--space-3); border-left: 1px solid var(--border-default); line-height: 16px;
}
.app-nav { display: flex; align-items: center; gap: var(--space-1); margin-left: var(--space-8); }
.app-nav__item {
  position: relative; height: 36px; padding: 0 14px;
  display: inline-flex; align-items: center; gap: 4px;
  border-radius: var(--radius-md);
  font-size: var(--fs-body); font-weight: var(--fw-medium); color: var(--text-secondary);
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}
.app-nav__item:hover { background: var(--bg-muted); color: var(--text-primary); }
.app-nav__item.is-active { background: var(--brand-50); color: var(--brand-700); font-weight: var(--fw-semibold); }
.app-nav__item .chevron { width: 12px; height: 12px; transition: transform var(--dur) var(--ease); }
.app-nav__item[aria-expanded="true"] .chevron { transform: rotate(180deg); }

.dropdown {
  position: absolute; top: calc(100% + 6px); left: 0; z-index: var(--z-dropdown);
  min-width: 208px; padding: 6px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default); border-radius: var(--radius-lg);
  box-shadow: var(--elev-3);
  transform-origin: top left;
  animation: sg-dropdown-in var(--dur) var(--ease);
}
.dropdown__group-label { padding: 8px 10px 4px; font-size: var(--fs-caption); color: var(--text-tertiary); }
.dropdown__item {
  display: flex; align-items: center; gap: var(--space-2);
  height: 36px; padding: 0 10px; border-radius: var(--radius-sm);
  font-size: var(--fs-body-sm); color: var(--text-primary); cursor: pointer;
}
.dropdown__item:hover { background: var(--bg-hover); }
.dropdown__item.is-active { background: var(--brand-50); color: var(--brand-700); font-weight: var(--fw-medium); }
.dropdown__item .desc { margin-left: auto; font-size: var(--fs-caption); color: var(--text-tertiary); }
.dropdown__divider { height: 1px; margin: 6px 4px; background: var(--border-subtle); }
@keyframes sg-dropdown-in { from { opacity: 0; transform: translateY(-4px) scale(0.98); } to { opacity: 1; transform: none; } }

/* 顶栏右侧 */
.app-topbar__right { margin-left: auto; display: flex; align-items: center; gap: var(--space-3); }
.icon-btn {
  position: relative; width: 32px; height: 32px; display: inline-grid; place-items: center;
  border-radius: var(--radius-md); border: 1px solid var(--border-default); background: var(--bg-surface);
  color: var(--text-secondary); transition: all var(--dur-fast) var(--ease);
}
.icon-btn:hover { background: var(--bg-muted); color: var(--text-primary); border-color: var(--border-strong); }
.icon-btn--ghost { border-color: transparent; background: transparent; }
.icon-btn__dot {
  position: absolute; top: 6px; right: 6px; width: 8px; height: 8px; border-radius: 50%;
  background: var(--danger-solid); box-shadow: 0 0 0 2px #fff;
}
.presence-pill {
  height: 28px; padding: 0 8px 0 10px; display: inline-flex; align-items: center; gap: 6px;
  border-radius: var(--radius-pill); border: 1px solid var(--success-border); background: var(--success-bg);
  font-size: var(--fs-caption); font-weight: var(--fw-medium); color: var(--success-fg);
}
.presence-pill::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: var(--success-solid);
  box-shadow: 0 0 0 3px rgba(31, 143, 92, 0.18); }
.user-chip { display: inline-flex; align-items: center; gap: var(--space-2); height: 32px; padding: 0 6px; border-radius: var(--radius-md); }
.user-chip:hover { background: var(--bg-muted); }
.avatar {
  width: 28px; height: 28px; border-radius: 50%; display: inline-grid; place-items: center;
  font-size: 12px; font-weight: var(--fw-semibold); background: var(--avatar-1-bg); color: var(--avatar-1-fg);
}
.avatar--lg { width: 36px; height: 36px; font-size: 14px; }
.avatar--empty { background: transparent; border: 1.5px dashed var(--border-strong); }

/* 工单 3 页签：分段控件 */
.segmented {
  display: inline-flex; padding: 3px; gap: 2px;
  background: var(--bg-muted); border-radius: var(--radius-lg);
}
.segmented__item {
  height: 30px; padding: 0 14px; border-radius: 7px;
  font-size: var(--fs-body-sm); font-weight: var(--fw-medium); color: var(--text-secondary);
  transition: all var(--dur-fast) var(--ease);
}
.segmented__item:hover { color: var(--text-primary); }
.segmented__item.is-active { background: var(--bg-surface); color: var(--brand-700); font-weight: var(--fw-semibold); box-shadow: var(--elev-seg); }
.segmented--sm .segmented__item { height: 24px; padding: 0 10px; font-size: var(--fs-caption); }

/* =========================================================
   2. 卡片容器
   ========================================================= */
.card {
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  box-shadow: var(--elev-1);
  display: flex; flex-direction: column;
  min-width: 0;
}
.card__header {
  min-height: 52px; padding: var(--card-py) var(--card-px) var(--space-3);
  display: flex; align-items: center; gap: var(--space-2);
}
.card__title { font-size: var(--fs-title-sm); line-height: var(--lh-title-sm); font-weight: var(--fw-semibold); color: var(--text-primary); }
.card__subtitle { font-size: var(--fs-caption); color: var(--text-tertiary); margin-top: 2px; }
.card__meta { font-size: var(--fs-caption); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
.card__actions { margin-left: auto; display: flex; align-items: center; gap: var(--space-2); }
.card__body { padding: 0 var(--card-px) var(--space-5); flex: 1; }
.card__footer {
  margin-top: auto; padding: var(--space-3) var(--card-px);
  border-top: 1px solid var(--border-subtle);
  font-size: var(--fs-caption); color: var(--text-tertiary);
  display: flex; justify-content: space-between; align-items: center;
}
.card--divided > .card__header { border-bottom: 1px solid var(--border-subtle); padding-bottom: var(--space-3); margin-bottom: var(--space-4); }
.card--flush > .card__body { padding: 0; }                 /* 表格贴边 */
.card--interactive { transition: box-shadow var(--dur) var(--ease), border-color var(--dur) var(--ease); cursor: pointer; }
.card--interactive:hover { border-color: var(--border-strong); box-shadow: var(--elev-2); }
.card--warning  { border-color: var(--warning-border); background: linear-gradient(180deg, #FFFAF1 0%, #FFFFFF 72%); }
.card--write-end { box-shadow: inset 0 3px 0 var(--warning-solid), var(--elev-1); }  /* 管理写端标识 */

/* 卡内分组：只允许浅底无边框，禁止边框套边框 */
.inset { background: var(--bg-subtle); border-radius: var(--radius-lg); padding: var(--space-3) 14px; }
.section-divider { height: 1px; background: var(--border-subtle); margin: var(--space-4) 0; }
.section-label { font-size: var(--fs-body-sm); font-weight: var(--fw-semibold); color: var(--text-secondary); margin-bottom: var(--space-3); display: flex; align-items: center; gap: 6px; }

/* 键值描述列表 */
.desc { display: grid; grid-template-columns: 72px minmax(0, 1fr); column-gap: var(--space-3); row-gap: 10px; }
.desc dt { font-size: var(--fs-body-sm); color: var(--text-tertiary); }
.desc dd { margin: 0; font-size: var(--fs-body-sm); font-weight: var(--fw-medium); color: var(--text-primary); word-break: break-all; }
.desc-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); column-gap: var(--space-6); row-gap: var(--space-4); }
.desc-grid__label { font-size: var(--fs-caption); color: var(--text-tertiary); margin-bottom: 4px; }
.desc-grid__value { font-size: var(--fs-body); line-height: var(--lh-body); font-weight: var(--fw-medium); color: var(--text-primary); }

/* KPI 统计 */
.stat__label { font-size: var(--fs-body-sm); font-weight: var(--fw-medium); color: var(--text-secondary); }
.stat__value { font-family: var(--font-num); font-size: var(--fs-display); line-height: var(--lh-display); font-weight: var(--fw-semibold);
  letter-spacing: -0.02em; font-variant-numeric: tabular-nums; color: var(--text-primary); margin: var(--space-2) 0 var(--space-1); }
.stat__value.is-zero    { color: var(--text-disabled); }
.stat__value.is-warning { color: var(--warning-fg); }
.stat__value.is-danger  { color: var(--danger-fg); }
.stat__desc { font-size: var(--fs-caption); color: var(--text-tertiary); }

/* =========================================================
   3. 表格
   ========================================================= */
.table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: var(--fs-body-sm); }
.table thead th {
  position: sticky; top: 0; z-index: 1;
  height: 40px; padding: 0 var(--space-3);
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--border-default);
  font-size: var(--fs-caption); font-weight: var(--fw-medium); color: var(--text-tertiary);
  letter-spacing: 0.02em; text-align: left; white-space: nowrap;
}
.table thead th:first-child, .table tbody td:first-child { padding-left: var(--card-px); }
.table thead th:last-child,  .table tbody td:last-child  { padding-right: var(--card-px); text-align: right; }
.table tbody td {
  height: var(--row-h); padding: 10px var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-primary); vertical-align: middle;
  transition: background var(--dur-fast) var(--ease);
}
.table tbody tr:last-child td { border-bottom: 0; }
.table tbody tr:hover td { background: var(--bg-hover); }
.table tbody tr.is-selected td { background: var(--bg-selected); }
.table tbody tr.is-urgent td { background: #FFFBF2; }
.table tbody tr.is-urgent td:first-child { box-shadow: inset 3px 0 0 var(--warning-solid); }
.table .col-id   { min-width: 156px; white-space: nowrap; font-family: var(--font-mono); font-size: 13px; font-weight: var(--fw-medium); color: var(--brand-700); }
.table .col-num  { text-align: right; font-variant-numeric: tabular-nums; }
.table .col-time { white-space: nowrap; font-variant-numeric: tabular-nums; color: var(--text-secondary); }
.table .cell-main { font-size: var(--fs-body); font-weight: var(--fw-medium); color: var(--text-primary);
  max-width: 380px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.table .cell-sub  { margin-top: 2px; font-size: var(--fs-caption); color: var(--text-tertiary); display: flex; align-items: center; gap: 6px; }
.table--2line tbody td { height: var(--row-h-2line); }

/* =========================================================
   4. 状态胶囊（pill + 圆点）与标签（tag）
   ========================================================= */
.badge {
  display: inline-flex; align-items: center; gap: 5px;
  height: 22px; padding: 0 8px; border-radius: var(--radius-pill);
  font-size: var(--fs-caption); font-weight: var(--fw-medium); line-height: 1; white-space: nowrap;
  border: 1px solid var(--neutral-border); background: var(--neutral-bg); color: var(--neutral-fg);
  font-variant-numeric: tabular-nums;
}
.badge--dot::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.badge--success { color: var(--success-fg); background: var(--success-bg); border-color: var(--success-border); }
.badge--warning { color: var(--warning-fg); background: var(--warning-bg); border-color: var(--warning-border); }
.badge--danger  { color: var(--danger-fg);  background: var(--danger-bg);  border-color: var(--danger-border); }
.badge--info    { color: var(--info-fg);    background: var(--info-bg);    border-color: var(--info-border); }
.badge--brand   { color: var(--brand-700);  background: var(--brand-50);   border-color: var(--brand-100); }
.badge--lock::before { content: ""; width: 12px; height: 12px; background: currentColor;
  -webkit-mask: var(--icon-lock) center / contain no-repeat; mask: var(--icon-lock) center / contain no-repeat; }

/* 元信息标签：方角、无边框、弱化。每个卡片头部最多 1 个 */
.tag {
  display: inline-flex; align-items: center; height: 20px; padding: 0 6px;
  border-radius: var(--radius-xs); background: var(--neutral-bg);
  font-size: var(--fs-caption); color: var(--text-tertiary); white-space: nowrap;
}
.tag--mono  { font-family: var(--font-mono); }
.tag--brand { background: var(--brand-50); color: var(--brand-700); }
.tag--warning { background: var(--warning-bg); color: var(--warning-fg); }
.tag--danger  { background: var(--danger-bg);  color: var(--danger-fg); }

/* 计数气泡 */
.count { min-width: 18px; height: 18px; padding: 0 5px; border-radius: var(--radius-pill);
  display: inline-grid; place-items: center; background: var(--bg-muted);
  font-size: var(--fs-caption); font-variant-numeric: tabular-nums; color: var(--text-tertiary); }
.count.is-zero { color: var(--text-disabled); }

/* =========================================================
   5. 按钮
   ========================================================= */
.btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  height: var(--control-h-md); padding: 0 14px;
  border-radius: var(--radius-md); border: 1px solid transparent;
  font-family: inherit; font-size: var(--fs-body-sm); font-weight: var(--fw-medium); line-height: 1;
  white-space: nowrap; cursor: pointer; user-select: none;
  transition: background var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease),
              color var(--dur-fast) var(--ease), box-shadow var(--dur-fast) var(--ease);
}
.btn svg { width: 16px; height: 16px; flex: none; }
.btn:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.btn--sm { height: var(--control-h-sm); padding: 0 10px; font-size: var(--fs-caption); border-radius: var(--radius-sm); }
.btn--lg { height: var(--control-h-lg); padding: 0 18px; font-size: var(--fs-body); }
.btn--block { width: 100%; }

/* 主按钮：一个操作区最多 1 个 */
.btn--primary { background: var(--brand-700); color: var(--text-on-brand);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.10), 0 1px 2px rgba(15,118,110,0.24); }
.btn--primary:hover  { background: var(--brand-800); }
.btn--primary:active { background: var(--brand-900); }

/* 辅助按钮（浅青绿）：第二重要操作，替代所有品牌外的蓝色 */
.btn--soft { background: var(--brand-50); color: var(--brand-700); border-color: var(--brand-100); }
.btn--soft:hover { background: #DAF0EC; border-color: var(--brand-200); }

/* 次按钮 */
.btn--secondary { background: var(--bg-surface); color: var(--text-primary); border-color: var(--border-strong); box-shadow: var(--elev-1); }
.btn--secondary:hover { background: var(--bg-subtle); border-color: var(--border-hover); }

/* 幽灵按钮 */
.btn--ghost { background: transparent; color: var(--text-secondary); }
.btn--ghost:hover { background: var(--bg-muted); color: var(--text-primary); }
.btn--link { background: transparent; color: var(--brand-700); padding: 0 4px; height: auto; }
.btn--link:hover { color: var(--brand-800); text-decoration: underline; text-underline-offset: 3px; }

/* 危险按钮 */
.btn--danger { background: var(--danger-solid); color: #fff; box-shadow: 0 1px 2px rgba(192,54,44,0.24); }
.btn--danger:hover { background: #A52E25; }
.btn--danger:focus-visible { box-shadow: var(--focus-ring-danger); }
.btn--danger-outline { background: var(--bg-surface); color: var(--danger-fg); border-color: var(--danger-border); }
.btn--danger-outline:hover { background: var(--danger-bg); }

.btn:disabled, .btn[aria-disabled="true"] {
  background: var(--bg-muted); color: var(--text-disabled); border-color: transparent; box-shadow: none; cursor: not-allowed;
}

/* =========================================================
   6. 表单控件
   ========================================================= */
:root {
  --icon-chevron: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Cpath d='M4 6l4 4 4-4' fill='none' stroke='%237A8686' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  --icon-check: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Cpath d='M4 8.5l2.5 2.5L12 5.5' fill='none' stroke='white' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  --icon-lock: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Crect x='3' y='7' width='10' height='7' rx='1.5' fill='black'/%3E%3Cpath d='M5 7V5a3 3 0 0 1 6 0v2' fill='none' stroke='black' stroke-width='1.5'/%3E%3C/svg%3E");
}
.field__label { display: block; margin-bottom: 6px; font-size: var(--fs-caption); font-weight: var(--fw-medium); color: var(--text-secondary); }
.input, .select {
  height: var(--control-h-md); width: 100%; padding: 0 10px;
  font: inherit; font-size: var(--fs-body-sm); color: var(--text-primary);
  background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: var(--radius-md);
  transition: border-color var(--dur-fast) var(--ease), box-shadow var(--dur-fast) var(--ease);
}
.input::placeholder { color: var(--text-disabled); }
.input:hover, .select:hover { border-color: var(--border-hover); }
.input:focus, .select:focus { outline: none; border-color: var(--brand-600); box-shadow: var(--focus-ring); }
.input.is-invalid { border-color: var(--danger-solid); }
.input.is-invalid:focus { box-shadow: var(--focus-ring-danger); }
.input--lg, .select--lg { height: var(--control-h-xl); font-size: var(--fs-body); }
.input-search { position: relative; }
.input-search > svg { position: absolute; left: 10px; top: 50%; width: 16px; height: 16px; transform: translateY(-50%); color: var(--text-tertiary); pointer-events: none; }
.input-search > .input { padding-left: 32px; }
.select { appearance: none; padding-right: 28px; cursor: pointer;
  background: var(--bg-surface) var(--icon-chevron) no-repeat right 8px center / 14px; }
.select--auto { width: auto; min-width: 112px; }

/* 只读锁定框：系统同步、不可编辑的字段 */
.field-locked {
  position: relative; height: var(--control-h-md); padding: 0 32px 0 10px;
  display: flex; align-items: center;
  background: var(--bg-muted); border: 1px solid var(--border-default); border-radius: var(--radius-md);
  font-size: var(--fs-body-sm); color: var(--text-secondary); cursor: not-allowed;
}
.field-locked::after { content: ""; position: absolute; right: 10px; width: 14px; height: 14px; background: var(--text-disabled);
  -webkit-mask: var(--icon-lock) center / contain no-repeat; mask: var(--icon-lock) center / contain no-repeat; }

/* Checkbox */
.checkbox { appearance: none; width: 16px; height: 16px; flex: none; margin: 0; cursor: pointer;
  border: 1.5px solid var(--border-strong); border-radius: var(--radius-xs); background: #fff;
  transition: all var(--dur-fast) var(--ease); }
.checkbox:hover { border-color: var(--brand-600); }
.checkbox:checked { background: var(--brand-700) var(--icon-check) center / 12px no-repeat; border-color: var(--brand-700); }
.checkbox:focus-visible { outline: none; box-shadow: var(--focus-ring); }

/* Switch */
.switch { appearance: none; position: relative; width: 32px; height: 18px; border-radius: var(--radius-pill);
  background: var(--border-strong); cursor: pointer; transition: background var(--dur) var(--ease); }
.switch::after { content: ""; position: absolute; top: 2px; left: 2px; width: 14px; height: 14px; border-radius: 50%;
  background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.2); transition: transform var(--dur) var(--ease); }
.switch:checked { background: var(--brand-700); }
.switch:checked::after { transform: translateX(14px); }
.switch:focus-visible { outline: none; box-shadow: var(--focus-ring); }

/* =========================================================
   7. 顶部来电告警条
   ========================================================= */
.call-banner {
  position: sticky; top: calc(var(--topbar-h) + 12px); z-index: var(--z-banner);
  height: 44px; margin-bottom: var(--gutter); padding: 0 var(--space-4) 0 var(--space-3);
  display: flex; align-items: center; gap: var(--space-3);
  background: linear-gradient(90deg, #FDECEA 0%, #FEF5F4 60%, #FFF8F7 100%);
  border: 1px solid var(--danger-border); border-radius: var(--radius-lg);
  box-shadow: inset 3px 0 0 var(--danger-solid), 0 4px 12px -4px rgba(192, 54, 44, 0.18);
  color: #8E241C;
}
.call-banner__icon {
  width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center;
  background: var(--danger-solid); color: #fff; animation: sg-ring 1.6s var(--ease) infinite;
}
.call-banner__icon svg { width: 14px; height: 14px; }
.call-banner__status { font-size: var(--fs-body-sm); font-weight: var(--fw-semibold); color: var(--danger-fg); }
.call-banner__phone  { font-family: var(--font-mono); font-size: var(--fs-body); font-weight: var(--fw-semibold); color: #7A1F18; letter-spacing: 0.02em; }
.call-banner__meta   { font-size: var(--fs-body-sm); color: #9A3A32; font-variant-numeric: tabular-nums; }
.call-banner__sep    { width: 1px; height: 14px; background: #EBB3AD; }
.call-banner__rec    { display: inline-flex; align-items: center; gap: 6px; }
.call-banner__rec::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: var(--danger-solid); animation: sg-blink 1.2s steps(2) infinite; }
.call-banner__wave   { margin-left: auto; display: flex; align-items: center; gap: 2px; height: 16px; }
.call-banner__wave i { width: 2px; height: 100%; border-radius: 1px; background: var(--danger-solid); transform-origin: center; animation: sg-wave 1s var(--ease) infinite; }
.call-banner__wave i:nth-child(2) { animation-delay: .15s; } .call-banner__wave i:nth-child(3) { animation-delay: .3s; }
.call-banner__wave i:nth-child(4) { animation-delay: .45s; } .call-banner__wave i:nth-child(5) { animation-delay: .6s; }
@keyframes sg-ring  { 0% { box-shadow: 0 0 0 0 rgba(192,54,44,.40); } 100% { box-shadow: 0 0 0 8px rgba(192,54,44,0); } }
@keyframes sg-blink { 50% { opacity: .25; } }
@keyframes sg-wave  { 0%, 100% { transform: scaleY(.35); } 50% { transform: scaleY(1); } }

/* =========================================================
   8. 步骤进度条
   ========================================================= */
.stepper {
  display: grid; grid-template-columns: repeat(6, minmax(0, 1fr));
  padding: var(--space-4) var(--space-2) 14px;
  background: var(--bg-subtle); border-radius: var(--radius-lg);
}
.step { position: relative; display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center; }
.step::before {   /* 连接线 */
  content: ""; position: absolute; top: 11px; height: 2px; border-radius: 1px;
  left: calc(-50% + 18px); right: calc(50% + 18px); background: var(--border-default);
}
.step:first-child::before { display: none; }
.step__node {
  position: relative; z-index: 1; width: 24px; height: 24px; border-radius: 50%;
  display: grid; place-items: center;
  font-family: var(--font-num); font-size: var(--fs-caption); font-weight: var(--fw-semibold);
  background: #fff; border: 1.5px solid var(--border-strong); color: var(--text-tertiary);
}
.step__label { font-size: var(--fs-body-sm); color: var(--text-tertiary); }
.step__time  { font-size: var(--fs-caption); color: var(--text-disabled); font-variant-numeric: tabular-nums; min-height: 18px; }
.step.is-done::before, .step.is-current::before { background: var(--brand-600); }
.step.is-done .step__node { background: var(--brand-700); border-color: var(--brand-700); color: #fff; }
.step.is-done .step__label { color: var(--text-secondary); }
.step.is-current .step__node { background: #fff; border: 2px solid var(--brand-700); color: var(--brand-700); box-shadow: 0 0 0 4px rgba(13,148,136,.14); }
.step.is-current .step__label { color: var(--text-primary); font-weight: var(--fw-semibold); }
.step.is-current .step__time  { color: var(--text-tertiary); }

/* 竖向编号列表（SOP 步骤） */
.step-list { position: relative; display: flex; flex-direction: column; }
.step-list__item { position: relative; display: flex; align-items: flex-start; gap: var(--space-3); padding: 10px 0; }
.step-list__item + .step-list__item { border-top: 1px solid var(--border-subtle); }
.step-list__no {
  flex: none; width: 22px; height: 22px; border-radius: 50%; display: grid; place-items: center;
  font-family: var(--font-num); font-size: var(--fs-caption); font-weight: var(--fw-semibold);
  background: var(--brand-50); color: var(--brand-700);
}
.step-list--warning .step-list__no { background: var(--warning-bg); color: var(--warning-fg); }
.step-list__text { font-size: var(--fs-body); line-height: var(--lh-body); color: var(--text-primary); }

/* 无障碍：减少动效 */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; transition-duration: 0.01ms !important; }
}
```

---

## 三、逐页视觉优化清单

### 01 工作台

| # | 改什么（样式指令） | 目标效果 |
|---|---|---|
| 1 | **栅格与吸顶**：三栏改为 `grid-template-columns: 288px minmax(0,1fr) 320px; gap: 16px; align-items: start`。左右栏加 `position: sticky; top: 128px`（56 顶栏 + 12 + 44 来电条 + 16）。中栏 3 张卡统一 `gap: 16px`。最后的操作卡改为 `position: sticky; bottom: 16px; box-shadow: var(--elev-2)`。 | 消除三栏底部高低不齐和不均匀的空白；滚动时客户档案、SOP 和主操作始终可见 |
| 2 | **来电条**：套用 `.call-banner`，高 44px，下方间距由约 32px 改为 16px。号码用 `.call-banner__phone`（mono 14/600），「录音中」加红点闪烁，右侧波形改为 5 根 2px 动态竖条。 | 告警很醒目，但只占一条细带，不再像一大块红色横幅压住页面 |
| 3 | **工单信息区**：工单号用 `font: 600 24px/32px var(--font-mono); letter-spacing: -0.01em`。字段区改为两组 `.desc`，label 72px、颜色 `--text-tertiary`，value 13/500 用主文字色，行距 12px。「紧急度：中」「SLA」统一为 22px `.badge--warning`，SLA 加 `--dot`。「待认领」改为 `.badge--warning.badge--dot`。「承接处理」用 `.btn--primary`（32px），左边与字段 value 列对齐。 | 单号成为视觉锚点，标签和数值一眼能分开，徽标高度一致 |
| 4 | **处理进度**：标题「处理进度」从居中改为左对齐的 `.section-label`，上方加 `.section-divider`。去掉外框，改用 `.stepper` 浅底。时间移到标签下方，用 `.step__time`，未发生的显示「--」并用 `--text-disabled`。下方「受理→…→验收闭环」说明保留，改为 12px `--text-tertiary` 左对齐。 | 进度条从「表单块」变成清晰的流程指示，弱化重复信息 |
| 5 | **按钮与右栏**：「直接派单」**去掉品牌外的蓝色**，改用 `.btn--soft`；「保存工单」用 `.btn--primary`；「远程指导」「派单至智享乐袋」用 `.btn--secondary`。4 个按钮保持等分，高 36px，`gap: 12px`。SOP 清单去掉每行灰框，改为 `.checkbox` + 20px 编号圆（`--brand-50` 底），行间用 1px `--border-subtle` 分隔、行内上下 padding 10px，已勾选项文字转 `--text-secondary`。「查看全部」「>」改为 `.btn--ghost.btn--sm` 和 `.icon-btn--ghost`。录音的「听」「⬇」用 28px `.icon-btn`。 | 去掉原生控件的粗糙感；同一操作区只有一个实心主色按钮，层级明确 |

### 02 调度大屏

| # | 改什么（样式指令） | 目标效果 |
|---|---|---|
| 1 | **页头条**：高 56px，标题 16/600；前面的状态点改为 6px `--success-solid`，加 3px 光晕。时间胶囊去掉边框，改为 `background: var(--bg-subtle); padding: 4px 10px; radius: 6px`，文字用 mono 加 `tabular-nums`。「全屏值守模式」用 `.btn--secondary.btn--sm`。 | 页头更轻，时钟数字跳动时不左右抖动 |
| 2 | **4 张 KPI 卡**：统一高 112px、padding `16px 20px`。数字用 `.stat__value`（32/40，Inter 600），0 值加 `.is-zero` 变灰。右上角标签改为无边框 `.tag`。「待分配」卡用 `.card--warning`，数字 `.is-warning`（`#A15C07`）；底部「点击进入待认领池筛选 →」用 `.btn--link` 12/500，整卡加 `.card--interactive`。 | KPI 一秒可读；只有异常卡被强调，0 值不抢注意力 |
| 3 | **工单池列表**：去掉表格外的边框，卡片加 `.card--flush`，让表格贴边。单号列用 `.col-id`（min 156px、mono、不截断）。「剩余 24m」用 `.badge--warning.badge--dot`，超时用 `--danger`。「400电话」来源改为 `.tag`。筛选区控件统一 32px、`gap: 8px`。「查看完整总表 →」从浅绿实底改为 `.btn--link`。这张卡和坐席卡 `align-items: stretch`，分页用 `margin-top: auto` 固定在底部。 | 少一层框，单号完整显示，表格区和右侧等高对齐 |
| 4 | **坐席在岗**：去掉每位坐席的独立边框，改为列表行，行高 56px，用 1px `--border-subtle` 分隔。头像改用 `--avatar-1…4` 低饱和色板，按用户 ID 哈希取色，不再随机粉蓝。角色用 `.tag`，「就绪」用 `.badge--success.badge--dot`。「一键强制平均分配」改为 `.btn--secondary.btn--block.btn--lg`，带「强制」语义时可以用 `.btn--danger-outline`。 | 和右上角「+ 新建工单」不再争抢主色焦点，列表更干净 |
| 5 | **底部三卡**：列宽改为 `grid-template-columns: 0.8fr 1.1fr 1.1fr`，统一高 300px。三张卡的时间切换都用 `.segmented--sm`（24px，12px 字）。渠道进度条高 8px、圆角 4px，轨道 `--bg-muted`，填充 `--brand-600`。图表网格线 1px `--chart-grid` 虚线 `4 4`，折线宽 2px，节点 3px 白芯空心圆，超时序列 `--chart-2` 虚线。排班表人名标签 `min-width: 72px`，**禁止截断**；今天所在列加 `--brand-50` 底。 | 减少「渠道分布」的空白，把横向空间让给图表和排班；排班信息完整可读 |

### 03 统一工单池

| # | 改什么（样式指令） | 目标效果 |
|---|---|---|
| 1 | **页签行**：3 个页签用 `.segmented`，选中态为白底 + `--brand-700` 文字 + `--elev-seg`，替换当前的深绿实底。「+ 新建工单」用 `.btn--primary`（32px），和页签行垂直居中，行下方间距 16px。 | 页签不再和主按钮抢视觉，页面上只有一个深绿焦点 |
| 2 | **状态筛选**：改为 `height: 30px; padding: 0 10px; font-size: 13px` 的文字页签。计数单独用 `.count` 气泡，0 值 `.is-zero`。选中态：页签底 `--brand-50`、文字 `--brand-700`，计数气泡底 `--brand-700`、白字。工具栏整行高 56px、padding `12px 20px`，底部 1px `--border-subtle`。 | 修复 11px 小字和低对比，状态切换可读、可点 |
| 3 | **表格结构**：单号列用 `.col-id`，min 160px、nowrap，**修复单号溢出压住「故障」标签**。问题标题用 `.cell-main`（14/500，单行省略，最宽 380px），副行 `.cell-sub` 里依次放「故障」`.tag--danger`、「无法充电 · 400电话」、SLA `.tag--warning`。表格加 `.table--2line`（行高 64px）。 | 一行信息从「三层堆叠」变成主次两层，扫读效率更高 |
| 4 | **行状态与单元格**：待认领行用 `tr.is-urgent`（底色 `#FFFBF2` + 左侧 3px 内阴影 `--warning-solid`）。「认领」改为 `.btn--soft.btn--sm`，和描边胶囊区分开。负责人为空时用 24px `.avatar--empty` 虚线圈 + 「未认领」`--text-tertiary`。更新时间用 `.col-time` 加 `tabular-nums`，「2026 年」副行用 12px 灰字。 | 可操作的按钮和状态展示不再混淆，时间纵向对齐 |
| 5 | **空画布**：列表卡加 `min-height: calc(100vh - 56px - 40px - 46px - 56px)`，让卡片撑满一屏、页脚贴底。分页按钮 28×28px、圆角 6px，当前页为 `--brand-50` 底 + `--brand-700` 字。无数据时显示 48px 线性图标 + 14px 主文案 + 12px 灰色说明的空状态。 | 消除页面 70% 的灰色空白，看上去是一个完整的工作面 |

### 04 设备档案

| # | 改什么（样式指令） | 目标效果 |
|---|---|---|
| 1 | **查询区**：搜索框、模拟 select 和「查询设备」统一用 40px 的 `--lg` 尺寸，搜索框加前置 16px 图标。提示文字 12px `--text-tertiary`。「客户未提供桩信息？创建…工单」改为 `.btn--ghost`，文字用 `--brand-700`。4 个数据源状态块由青绿实底改为**白底 + 1px `--border-default` + 左侧 3px `--success-solid` 内阴影**；系统名 13/600，时间 mono 12px 灰字；只有异常时整块换 `--danger-bg`。 | 全部正常时安静，出现异常才刺眼，体现「异常驱动」的信息设计 |
| 2 | **字段区（核心改动）**：基本信息、生产与安装、质保结算的所有字段**去掉灰底框**，改为 `.desc-grid`（4 列，列间距 24px，行间距 16px）。label 12px 灰字，value 14/500 主文字色；SN、MES 批次、项目编号、手机号用 `.mono`。「在线 · 告警中」改为 `.badge--success` 加 `.badge--danger` 两个胶囊。 | 页面上 30 多个灰框消失，字段不再像禁用的输入框，信息密度不变但视觉轻一半 |
| 3 | **分节头**：编号圆 22px，`--brand-50` 底、`--brand-700` 字、12/600。标题 15/600，说明 12px 灰字。右侧「查看 MES 原始批次 ↗」等按钮统一 `.btn--secondary.btn--sm`，配 14px 外链图标。分节卡片之间由约 28px 改为 16px，卡内 padding 统一 20px。 | 6 个分节节奏一致，长页面滚动时不再松散 |
| 4 | **历史报修和维修列表**：防重提示条用 `background: var(--warning-bg); border: 1px solid var(--warning-border); border-radius: 8px; padding: 12px 14px`，前置 16px 警示图标，里面的工单引用改为白底 `.tag`。维修记录行高 44px，用 1px `--border-subtle` 分隔；单号 mono 13/500，日期 `tabular-nums` 灰字；「智享乐袋（模拟）」用 `.tag`，「查看维修单 ↗」用 `.btn--link` 并右对齐。 | 警示有分量但不刺眼，列表整齐，可以纵向扫读 |
| 5 | **关联工单**：4 个计数改为小型统计（label 13px 灰字在左，数字 `font: 600 24px/32px var(--font-num)` 在右）；0 值 `--text-disabled`，「最近告警 1」用 `--danger-fg`。下方 3 个子卡改为 `.inset`（浅底、无边框、圆角 10px），里面的记录项用白底 + 1px `--border-subtle`。 | 去掉卡中卡，核心计数立刻能读出来 |

### 05 知识库（只读端）

| # | 改什么（样式指令） | 目标效果 |
|---|---|---|
| 1 | **只读身份标识**：面包屑条高 48px，当前页名 14/600，上级 13px 灰字，分隔符 `/` 用 `--text-disabled`。「只读发布版 · 仅展示生效 SOP」改为 `.badge.badge--lock`（中性灰底 + 12px 锁图标）。**本页不加任何写端色带，不出现 `.card--write-end`**。 | 用锁图标和中性色建立「只读」心智，和管理端从视觉上隔离 |
| 2 | **SOP 步骤去掉三层框**：「第一段 / 第二段」分组去掉边框和灰底，改为：分组头（`.tag--brand` 或 `.tag--warning` 段标签 + 14/600 标题 + 右侧 12px 灰色说明），下方接 `.step-list`。第一段用默认青绿编号，第二段用 `.step-list--warning`。步骤之间只用 1px 分隔线，两段之间 `margin-top: 20px`。 | 层级从「卡 → 框 → 框」压成「卡 → 列表」，阅读顺序清晰 |
| 3 | **分类树**：项高 36px、圆角 8px、padding `0 10px`。选中态：底 `--brand-50`，文字 `--brand-700` 600，左侧内阴影 `inset 3px 0 0 var(--brand-600)`。版本号用 `.tag.tag--mono`；「6步」12px `tabular-nums` 灰字右对齐。搜索框 32px 带图标。 | 选中态更精致，版本和步数纵向对齐 |
| 4 | **右栏**：级别提示块用 `.inset`，底色 `--brand-50`、无边框，A/B/C 级说明 14/22 600 `--brand-900`，脚注 12px 灰字。证据清单去掉逐项边框，改为分隔线列表：标题 13/600，说明 12/18 `--text-tertiary`，「建议必传」改为 `.tag--brand`。证据样例的类别标签用 `.tag`，正文 13px。 | 右栏信息从「格子墙」变成可以连续阅读的说明 |
| 5 | **栏高和页脚**：三栏 `align-items: start`；中栏卡加 `min-height` 和右栏对齐，避免中栏下方大片空白。右栏底部「生效版本：V2.4 / 更新于…」用 `.card__footer`（12px、`tabular-nums`、上分隔线）。 | 版面底部整齐，元信息不打扰正文 |

### 06 管理 → SOP 与规则（写端）

| # | 改什么（样式指令） | 目标效果 |
|---|---|---|
| 1 | **写端身份标识**：面包屑卡加 `.card--write-end`（顶部 3px `--warning-solid` 色带）。「唯一写入端」用 `.badge--warning`，「V2.4 · 已发布」用 `.badge--success.badge--dot`。右侧「+ 新建故障」「评审」用 `.btn--secondary`，「发布」用 `.btn--primary`，都是 32px、`gap: 8px`。 | 一进页面就知道这里是「写」的地方，和只读知识库在视觉上隔离 |
| 2 | **SOP 主区**：步骤列表的间距、字号、编号色**和知识库完全一致**（复用 `.step-list`），保证两边的差异只在写端控件。「编辑 SOP」用 `.btn--secondary.btn--sm`，加 14px 铅笔图标。「当前查看：V2.4」用 `.badge--brand` 加 mono 版本号。 | 同一份内容两端看起来一样，降低认知成本，写端控件一目了然 |
| 3 | **A/B/C 阈值块**：去掉绿、黄、红三块大面积粉底。改为 `.inset` 浅灰底，加左侧 3px 内阴影（A：`--success-solid`，B：`--warning-solid`，C：`--danger-solid`）。级别名用对应的 `.tag--*`（12/600），条件文字 13px 主文字色，块间距 8px。「仅管理侧可见 · 不对一线展示」用 `.tag--warning`，放在标题下方；「设置规则」用 `.btn--secondary.btn--sm`，配齿轮图标。 | 颜色只做提示，不再铺满，右栏不再像彩色便签墙 |
| 4 | **自动审批条件**：去掉逐行边框，改为行高 48px 的分隔线列表。「条件1」前缀 mono 12px 灰字，条件文字 13/500。开关用 `.switch`（32×18，开启为 `--brand-700`），右侧「自动通过」12px `--success-fg`。头部「3/3 项目自动通过」用 `.badge--success`。 | 开关和文案对齐整齐，状态一眼可读 |
| 5 | **版本记录**：当前版本行**去掉发光阴影**，改为 `background: var(--brand-50); border: 1px solid var(--brand-200); border-radius: 10px`。「生效中」用 `.badge--success.badge--dot`；已归档行用白底 + 1px `--border-subtle`，标题 `--text-secondary`，「已归档」用中性 `.badge`。作者和时间用 12px mono 灰字，时间右对齐。 | 去掉廉价的光晕感，「当前生效」靠底色和描边就能表达，更克制 |

---

### 给前端落地的执行顺序

1. 先把 `:root` Tokens 和 Base 整体替换进去，同时删除页面里写死的颜色和像素。
2. 再统一基础组件：`.btn`、`.badge`、`.tag`、`.input`、`.select`、`.checkbox`、`.switch`、`.table`。
3. 最后按 03 → 01 → 02 → 04 → 05/06 的顺序逐页执行清单。03 改动最小、见效最快；05 和 06 要成对验收读写隔离。

**全站有三条红线，可以用 lint 或 code review 卡住：**
- 不出现 `#3B82C4` 这类品牌外的蓝色。
- 字号不小于 12px。
- 卡片内不出现「带边框的容器套带边框的子项」。