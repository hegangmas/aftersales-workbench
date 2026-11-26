# 架构与业务决策台账（ADR）

记录影响业务规则、系统边界、数据模型与技术架构的重要决策。未确认的方案标记为待决，不写入已落地结论；历史资料见 [资料总入口](../README.md)，现行架构与接口边界见 [架构审查 V1.1](../architecture/arch-review-v11.md) 与 [接口边界说明](../architecture/api-boundaries.md)。

## 已确认并落地的核心决策总览

| 编号 | 决策主题 | 状态 | 确认日期 | 影响范围 |
| --- | --- | --- | --- | --- |
| **ADR-001** | V1.1 严格 6+1 状态机与单源契约 | 已确认并落地 | 2026-11-26 | 状态机、`ProblemV11Service`、前后端契约 |
| **ADR-002** | 简单工单快通道严格遵守 V1.1 状态机 | 已确认并落地 | 2026-11-26 | `quick-close` 流转、[REQ-003](../requirements/REQ-003-v11-core-workflow.md) |
| **ADR-003** | 挂起与回池统一使用 `suspended` 状态 | 已确认并落地 | 2026-11-26 | 挂起/回池落库、流转日志、[REQ-003](../requirements/REQ-003-v11-core-workflow.md) |
| **ADR-004** | 知识库只读端与管理 SOP 写端物理隔离 | 已确认并落地 | 2026-11-25 | `/api/knowledge/sops`、`/api/admin/sops`、CI 门禁 |
| **ADR-005** | 外部系统防腐层隔离与模拟先行 | 已确认并落地 | 2026-11-26 | `integration` 包、模拟模式、[REQ-002](../requirements/REQ-002-charger-platform-read-integration.md) |

## 决策明细

### ADR-001：V1.1 严格 6+1 状态机与单源契约
- **决定**：采用 `pending_assign`、`processing`、`suspended`、`pending_callback`、`pending_accept`、`closed` + `cancelled`；以 `web/src/contracts/problem-status.json` 为前后端唯一真源。
- **落地约束**：所有状态变更收口至 `ProblemV11Service` 并写入 `state_transition_log`，由 `ProblemStatusContractTest` 与 `ProblemTransitionMatrixTest` 守护。

### ADR-002：简单工单快通道严格遵守 V1.1 状态机
- **决定**：`quick-close` 仅限非换装/非换件/非上门工单，不再直跳历史 `resolved` 终态。
- **落地约束**：统一流转 `processing → pending_accept`（写入核验依据、活动与状态流转日志），再经正常验收闭环进入 `closed`。

### ADR-003：挂起与回池统一使用 `suspended` 状态
- **决定**：修正历史 `'waiting'` 落库残留，挂起统一写入 V1.1 `suspended` 并记录流转日志。
- **落地约束**：挂起回池（`returnToPool=true`）保持 `suspended` 状态并释放责任人（`owner=early`）。

### ADR-004：知识库只读端与管理 SOP 写端前后端物理隔离
- **决定**：后端拆分为全体可读 `/api/knowledge/sops`（剥离阈值与自动审批条件）与仅 `admin`/`quality` 可写 `/api/admin/sops`。
- **落地约束**：前端拆分为独立路由 chunk `views/knowledge/KnowledgeSopView.vue` 与 `views/admin/AdminSopView.vue`，由 CI 静态扫描强制守护。

### ADR-005：外部系统防腐层隔离与模拟先行
- **决定**：MES、安装、智享乐装、400 通话统一收口在 `com.csg.workbench.integration` 客户端接口下，当前默认 `workbench.integration.mode=simulated`。
- **落地约束**：待真实接口契约与签名规范确认后，在波次三实现 `real` 模式与 `V10__integration_inbox.sql`。

## 维护约定
- 新增或变更决策须注明确认日期、状态与影响范围，并与 [需求清单](../requirements/README.md) 及 [验收记录](../acceptance/README.md) 保持同步。
