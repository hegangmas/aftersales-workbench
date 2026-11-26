# 后端接口边界与新旧收口规范

- **适用版本**：V1.1（`feat/v11-strict-rebuild`）
- **面向对象**：后端/前端开发同事、AI 协作者与独立验收人员
- **核心约束**：[开发约定](../../AGENTS.md) · [V1.1 核心需求](../requirements/REQ-003-v11-core-workflow.md) · [状态机实现](../../src/main/java/com/csg/workbench/problem/ProblemStateMachine.java)

> [!IMPORTANT]
> **新需求收口铁律**：**所有新需求严禁往旧写接口加字段，统一收口到 V1.1 状态机与接口（`/api/v11/problems/*`）**。`ProblemController` 中保留的旧版流转写接口仅用于历史回归兼容（如 `scripts/smoke.py` 旧用例），不得再承载新业务字段或新状态流转。

---

## 一、V1.1 现行主干接口分组

### 1. 工单核心流转（`/api/v11/problems/*`）

实现类：[ProblemV11Controller.java](../../src/main/java/com/csg/workbench/problem/ProblemV11Controller.java)、[ProblemV11Service.java](../../src/main/java/com/csg/workbench/problem/ProblemV11Service.java)、[ProblemStateMachine.java](../../src/main/java/com/csg/workbench/problem/ProblemStateMachine.java)。
遵循 V1.1 严格 6+1 状态机：`pending_assign`（待分配）→ `processing`（处理中）→ `suspended`（挂起）/ `pending_callback`（待回传）→ `pending_accept`（待验收）→ `closed`（已闭环）/ `cancelled`（已取消），禁止任何跨状态直跳。写操作统一要求 `Idempotency-Key` 请求头与 `expectedVersion` 乐观锁（创建与重开除外）。

| 方法与路径 | 说明 | 关键规则 / 权限边界 |
| --- | --- | --- |
| `POST /api/v11/problems` | 受理建单 | 生成 `GD+YYYYMMDD+4位流水` 编号；`faultOccurredAt` 必填且不晚于当前时间；支持 `devicePending=true` 入待补队列，否则校验桩 SN 并固化弹屏快照 |
| `POST /api/v11/problems/{id}/bind-device` | 补齐设备档案 | 将待补队列工单绑定桩 SN 并固化快照，解除禁派单限制 |
| `POST /api/v11/problems/{id}/assign` | 认领或指派承接 | `pending_assign` → `processing`；`target` 为空表示本人认领，指派他人仅限 `admin` / `dispatcher` |
| `POST /api/v11/problems/{id}/dispatch` | 派单至智享乐装 | `processing` → `pending_callback`；校验非 `devicePending`，补全项目编号与预约时间并生成 `LZ-*` 外部单号 |
| `POST /api/v11/problems/{id}/lezhuang-callback` | 智享乐装结构化回传 | 处理 `dispatched` / `accepted` / `arrived` / `completed`；检测状态回退异常并留痕；`completed` 时推进至 `pending_accept` 并同步旧件编号 |
| `POST /api/v11/problems/{id}/accept` | 验收闭环或驳回 | `pending_accept` → `closed`（`approved=true`）或退回 `processing`（`approved=false`）；换装/换件工单未登记旧件禁止闭环 |
| `POST /api/v11/problems/{id}/cancel` | 取消工单 | 流转至终态 `cancelled`，需填写取消原因 |
| `POST /api/v11/problems/{id}/reopen` | 已闭环重开新单 | 仅 `closed`（含兼容 `resolved`）终态可重开，生成关联 `reopened_from_id` 的新工单 |
| `POST /api/v11/problems/force-distribute` | 调度一键均分 | 仅 `admin` / `dispatcher` 可调用，将 `pending_assign` 工单平均分配给 `ready` 在岗坐席 |

### 2. 看板与查询（`/api/problems/board|mine|due|{id}`）

实现类：[ProblemController.java](../../src/main/java/com/csg/workbench/problem/ProblemController.java)、[ProblemService.java](../../src/main/java/com/csg/workbench/problem/ProblemService.java)、[BoardQueries.java](../../src/main/java/com/csg/workbench/problem/BoardQueries.java)。
读接口与 V1.1 共用，已兼容 `GD*` 单号、`AS-*` 短号及 V1.1 全部状态与分桶。

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/problems/board` | 统一工单池 / 调度看板分页与统计（支持 `state`、`owner`、`category`、`kind`、`q`、`cursor`、`limit`、`bucket`） |
| `GET /api/problems/mine` | 我的待办分页列表 |
| `GET /api/problems/due` | 到期 / 待回访列表（`scope=team|mine`） |
| `GET /api/problems/{id}` | 工单详情聚合（基础信息、现场单、回访结果、活动时间线、流转记录、证据元数据、A/B/C 判定、SOP） |
| `GET /api/problems?device={sn}` | 按桩 SN 查询历史关联工单 |

### 3. 设备与客户档案（`/api/devices/*`、`/api/customers`）

实现类：[DeviceController.java](../../src/main/java/com/csg/workbench/device/DeviceController.java)、[DeviceService.java](../../src/main/java/com/csg/workbench/device/DeviceService.java)。

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/devices/lookup` / `GET /api/devices/search` | 按桩 SN、手机号或项目编号检索设备与 4 源聚合档案（支持 `failSource=mes\|install\|400\|lezhuang` 模拟单源降级） |
| `GET /api/customers` | 查询客户与项目档案列表 |
| `POST /api/customers` | 新增客户/项目与设备档案（仅 `admin` / `dispatcher`） |

### 4. 知识库只读 vs 管理写端（`/api/knowledge/sops` vs `/api/admin/sops`）

实现类：[SopController.java](../../src/main/java/com/csg/workbench/sop/SopController.java)、[SopService.java](../../src/main/java/com/csg/workbench/sop/SopService.java)。

- **只读知识库（`GET /api/knowledge/sops`）**：面向全体已登录角色（含外部角色 `provider` / `oem`），仅返回已发布（`published`）条目，**绝不返回** `gradeThresholds`（A/B/C 判定阈值）与 `autoApprovalRules`（自动审批条件），严禁挂接任何写操作。
- **管理写端（`GET /api/admin/sops`、`POST /api/admin/sops`、`POST /api/admin/sops/{id}/publish`）**：**唯一写端**，仅限 `ROLE_ADMIN` 或 `ROLE_QUALITY` 访问（否则返回 `403`）；维护草稿并发布后单向同步至知识库只读视图。

### 5. 账号、在岗与会话（`/api/accounts/*`、`/api/session/*`、`/api/health`）

实现类：[AccountController.java](../../src/main/java/com/csg/workbench/account/AccountController.java)、[SessionController.java](../../src/main/java/com/csg/workbench/auth/SessionController.java)、[HealthController.java](../../src/main/java/com/csg/workbench/health/HealthController.java)。

| 分组 | 方法与路径 | 说明 |
| --- | --- | --- |
| 会话与健康检查 | `GET /api/health`、`GET /api/session/csrf`、`POST /api/session/login`、`POST /api/session/logout`、`GET /api/session` | 登录鉴权、CSRF 令牌、当前登录用户与在岗状态 |
| 个人与在岗状态 | `GET /api/accounts/assignable`、`POST /api/accounts/duty`、`POST /api/accounts/{username}/duty`、`POST /api/accounts/password` | 可派单账号列表；本人或 `admin` / `dispatcher` 调整在岗状态（`ready` / `break` / `away`）；修改本人密码 |
| 账号管理（仅管理员） | `GET /api/accounts`、`POST /api/accounts`、`POST /api/accounts/{username}/password`、`POST /api/accounts/{username}/status` | 严格校验 `ROLE_ADMIN`，支持建号、重置密码与启停用 |

### 6. 月度运营报表（`/api/reports/monthly`）

实现类：[ProblemV11Controller.java](../../src/main/java/com/csg/workbench/problem/ProblemV11Controller.java)（`monthlyReport`）。

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/reports/monthly` | 返回 V1.1 月度运营指标（总单量、闭环量、渠道分布、SLA 达标率、换装率、妥协率、待补设备数等；其中漏单率/复测误判率等标记 `*Simulated` 模拟口径） |

---

## 二、`ProblemController` 旧写接口保留清单与 V1.1 替代对照表

> [!WARNING]
> **所有新需求严禁往旧写接口加字段，统一收口到 V1.1 状态机与接口。**

### 1. 已被 V1.1 取代的旧流转写接口（已标 `@Deprecated`）

以下接口在 [ProblemController.java](../../src/main/java/com/csg/workbench/problem/ProblemController.java) 中已标注 `@Deprecated`，仅作向后兼容保留，严禁在新页面或新业务中调用或扩展字段：

| 旧接口（`ProblemController`） | 方法名 | 原旧版语义 | 对应 V1.1 替代接口（`ProblemV11Controller`） |
| --- | --- | --- | --- |
| `POST /api/problems` | `create` | 旧版建单（固定进入 `open`，无 `GD` 单号与 `devicePending`） | `POST /api/v11/problems`（`create`） |
| `POST /api/problems/{id}/claim` | `claim` | 旧版无主认领（仅改 `owner`，不推进 V1.1 状态） | `POST /api/v11/problems/{id}/assign`（`assign`，`target` 置空即本人认领） |
| `POST /api/problems/{id}/callback` | `schedule` | 旧版安排回访并切入旧态 `waiting` | 收口至 V1.1 状态机（暂停等待使用挂起流程；不再产生 `waiting` 态） |
| `POST /api/problems/{id}/callback/result` | `finish` | 旧版回访登记并切向 `open` / `verify` / `field` / `resolved` | `POST /api/v11/problems/{id}/accept`（`accept` 验收闭环/驳回）或 `POST /api/v11/problems/{id}/dispatch`（转现场） |
| `POST /api/problems/{id}/field-withdrawal` | `withdrawField` | 旧版 `field` → `triage` 待派单撤回 | `POST /api/v11/problems/{id}/accept`（`approved=false` 退回处理中）或 `POST /api/v11/problems/{id}/cancel` |
| `POST /api/problems/{id}/field-orders` | `linkFieldOrder` | 旧版在 `field` 态人工关联外部现场单号 | `POST /api/v11/problems/{id}/dispatch`（`dispatch`，直接派单生成 `LZ-*` 并进入 `pending_callback`） |
| `POST /api/problems/{id}/field-orders/result` | `recordFieldResult` | 旧版手工登记 `field_order` 状态与回执 | `POST /api/v11/problems/{id}/lezhuang-callback`（`lezhuangCallback`，含状态防回退与旧件同步） |

### 2. 仍承载 V1.1 辅助 / 旁路写能力的兼容接口（已在 Controller 内收口委托 `ProblemV11Service`）

以下接口仍沿用 `/api/problems/{id}/*` 路径（URL、入参与 HTTP 状态码保持 100% 兼容），但已收口委托至 `ProblemV11Service` 执行 V1.1 规则与状态机校验：

| 接口路径 | 方法名 | 委托 Service 方法 | V1.1 收口规则 |
| --- | --- | --- | --- |
| `POST /api/problems/{id}/followups` | `followup` | `ProblemV11Service.addFollowupV11` | 仅写活动时间线并递增 `version`，不改工单主状态 |
| `POST /api/problems/{id}/handoff` | `handoff` | `ProblemV11Service.handoffV11` | 仅变更已认领非终态工单的 `owner` 并写交接事件与活动日志，不改主状态 |
| `POST /api/problems/{id}/suspend` | `suspend` | `ProblemV11Service.suspendV11` | 校验 `ProblemStateMachine.requireTransition(..., "suspended")`，落库态为 `suspended` 并写 `state_transition_log`；`returnToPool=true` 时保持 `suspended` 并置 `owner=early` |
| `POST /api/problems/{id}/quick-close` | `quickClose` | `ProblemV11Service.quickCloseV11` | 拦截换装/换件/上门工单，校验 `ProblemStateMachine.requireTransition(..., "pending_accept")`，流转至 `pending_accept` 并写核验依据、活动日志与 `state_transition_log`，后续走正常验收闭环进入 `closed` |
| `POST /api/problems/{id}/escalate` | `escalate` | `ProblemV11Service.escalateV11` | 仅更新升级级别与妥协标记并写活动日志，不改主状态 |
| `POST /api/problems/{id}/evidences` | `addEvidence` | `ProblemV11Service.addEvidenceV11` | 仅写入 `evidence_item` 并递增 `version`，供 A/B/C 与换装校验使用 |
| `POST /api/problems/{id}/assessment` | `assess` | `ProblemV11Service.assessV11` | 更新判定字段并写入 `assessment_record`（C 级禁换装、换装需证据） |

---

## 三、外部系统集成层 `com.csg.workbench.integration` 切换边界

外部系统（MES 生产系统、售后安装系统、智享乐装、400 客服系统）的接入与切换统一隔离在 `com.csg.workbench.integration` 包中，通过配置项 `workbench.integration.mode=simulated|real` 控制 Spring Bean 装配：

1. **接口抽象与当前默认实现（`simulated` 模式）**
   - 包内定义了 4 个外部系统客户端接口及对应默认模拟实现（均标注 `@ConditionalOnProperty(name = "workbench.integration.mode", havingValue = "simulated", matchIfMissing = true)`）：
     - `MesClient` → `SimulatedMesClient`（MES 生产批次、出厂日期、出厂测试与溯源深链）
     - `InstallClient` → `SimulatedInstallClient`（售后安装日期、安装单深链）
     - `LezhuangClient` → `SimulatedLezhuangClient`（智享乐装历史维修记录、派单深链）
     - `CallCenterClient` → `SimulatedCallCenterClient`（400 通话摘要、录音与模拟来电状态）
   - 统一使用 `SourceStatus` 构造 4 源独立健康状态（`name`、`ok`、`note`、`queriedAt`），由 [DeviceService.java](../../src/main/java/com/csg/workbench/device/DeviceService.java) 注入并聚合，支持通过 `failSource=mes|install|400|lezhuang` 演练单源超时降级。
   - 当前 `HealthController` 返回 `mode=simulated`；`ProblemV11Service` 中的派单单号生成（`LZ-*`）与回调触发亦处于模拟联调阶段。
2. **真实外部系统切换边界（`real` 模式）**
   - 切换 `workbench.integration.mode=real` 时，应在 `com.csg.workbench.integration` 包下新增实现上述 4 个接口的真实客户端 Bean（标注 `@ConditionalOnProperty(name = "workbench.integration.mode", havingValue = "real")`），集中封装 HTTP/MQ 调用、外部凭据读取（严禁硬编码或提交仓库）、字段映射、超时重试与错误语义转换。
   - 若后续扩展充电桩管理平台实时只读查询（见 [REQ-002](../requirements/REQ-002-charger-platform-read-integration.md)）或智享乐装真实派单客户端，同样必须收口在 `com.csg.workbench.integration` 内完成，不得直接将外部 DTO 或 HTTP 细节散落到 Controller 与 Service 层。
3. **上下游隔离要求**
   - **不得穿透上层契约**：无论 `simulated` 还是 `real`，Controller 层出入参、错误码（`400`/`401`/`403`/`404`/`409`/`503`）及 `ProblemStateMachine` 状态流转规则保持一致。
   - **单源独立降级**：任一外部只读源超时或不可用时，仅返回该源的 `degraded` 结果并在 `sourceStatuses` 标注缺口，不得阻塞其他 3 源数据展示或工单主流程。
   - **回传幂等与状态防回退**：智享乐装真实回调进入工作台时，必须经过 `ProblemV11Service` 的幂等键校验与阶段秩防回退校验（不允许从高阶段回退覆盖低阶段，异常仅留痕告警）。

---

## 四、写入口登记表（D6 强约束台账）

> [!IMPORTANT]
> **变更登记铁律**：任何新增、修改或收口后端写接口的 PR，必须同步更新本表，并遵守 PR 模板勾选规范：
> 1. 所有**工单主状态变更**只能通过 `ProblemV11Service` 调用 `ProblemMapper` 写状态方法，并在同一事务写入 `state_transition_log`（受 `ArchitectureRulesTest` 门禁守护）。
> 2. 标注为「待收口，禁止新增逻辑」或「已废弃，禁止新增逻辑」的接口严禁新增业务字段、新状态流转或旁路逻辑。

| 写接口 | 负责的 Service 方法 | 是否改状态 | 是否写 `state_transition_log` | 状态 |
| --- | --- | --- | --- | --- |
| `POST /api/v11/problems` | `ProblemV11Service.createV11` | 是（新建进入 `pending_assign`） | 是（`new -> pending_assign`） | 主干 |
| `POST /api/v11/problems/{id}/bind-device` | `ProblemV11Service.bindDevice` | 否（补齐设备档案与快照，不改主状态） | 否 | 主干 |
| `POST /api/v11/problems/{id}/assign` | `ProblemV11Service.acceptOrAssign` | 是（`pending_assign -> processing`） | 是 | 主干 |
| `POST /api/v11/problems/{id}/dispatch` | `ProblemV11Service.dispatchToLezhuang` | 是（`processing -> pending_callback`） | 是 | 主干 |
| `POST /api/v11/problems/{id}/lezhuang-callback` | `ProblemV11Service.handleLezhuangCallback` | 条件（仅 `completed` 时 `pending_callback -> pending_accept`；回退异常不改状态） | 条件（状态发生流转时写入） | 主干 |
| `POST /api/v11/problems/{id}/accept` | `ProblemV11Service.acceptOrReject` | 是（`pending_accept -> closed / processing`） | 是 | 主干 |
| `POST /api/v11/problems/{id}/cancel` | `ProblemV11Service.cancel` | 是（流转至 `cancelled`） | 是 | 主干 |
| `POST /api/v11/problems/{id}/reopen` | `ProblemV11Service.reopen` | 是（新建关联单进入 `pending_assign`） | 是（`new -> pending_assign`） | 主干 |
| `POST /api/v11/problems/force-distribute` | `ProblemV11Service.forceDistribute` | 是（`pending_assign -> processing`） | 是 | 主干 |
| `POST /api/problems/{id}/followups` | `ProblemV11Service.addFollowupV11` | 否（仅写 `activity` 并递增 `version`） | 否 | 已收口至 V1.1 |
| `POST /api/problems/{id}/handoff` | `ProblemV11Service.handoffV11` | 否（仅变更 `owner` 并写交接事件，不改主状态） | 否 | 已收口至 V1.1 |
| `POST /api/problems/{id}/suspend` | `ProblemV11Service.suspendV11` | 是（校验状态机后流转至 `suspended`，可选回池置 `owner=early`） | 是 | 已收口至 V1.1 |
| `POST /api/problems/{id}/quick-close` | `ProblemV11Service.quickCloseV11` | 是（非换装/换件/上门工单流转至 `pending_accept`，后续经验收闭环至 `closed`） | 是 | 已收口至 V1.1 |
| `POST /api/problems/{id}/escalate` | `ProblemV11Service.escalateV11` | 否（仅更新升级级别与妥协标记，不改主状态） | 否 | 已收口至 V1.1 |
| `POST /api/problems/{id}/evidences` | `ProblemV11Service.addEvidenceV11` | 否（仅写入 `evidence_item` 并递增 `version`） | 否 | 已收口至 V1.1 |
| `POST /api/problems/{id}/assessment` | `ProblemV11Service.assessV11` | 否（更新判定字段并写入 `assessment_record`） | 否 | 已收口至 V1.1 |
| `POST /api/problems`（`@Deprecated`） | `ProblemService.create` | 是（旧版建单默认 `open`） | 否 | 已废弃，禁止新增逻辑 |
| `POST /api/problems/{id}/claim`（`@Deprecated`） | `ProblemService.claim` | 否（仅变更 `owner`） | 否 | 已废弃，禁止新增逻辑 |
| `POST /api/problems/{id}/callback`（`@Deprecated`） | `ProblemService.schedule` | 是（切入旧态 `waiting`） | 否 | 已废弃，禁止新增逻辑 |
| `POST /api/problems/{id}/callback/result`（`@Deprecated`） | `ProblemService.finishCallback` | 是（切向 `open` / `verify` / `field` / `resolved`） | 否 | 已废弃，禁止新增逻辑 |
| `POST /api/problems/{id}/field-withdrawal`（`@Deprecated`） | `ProblemService.withdrawField` | 是（`field -> triage`） | 否 | 已废弃，禁止新增逻辑 |
| `POST /api/problems/{id}/field-orders`（`@Deprecated`） | `ProblemService.linkFieldOrder` | 否（仅关联 `field_order` 并递增 `version`） | 否 | 已废弃，禁止新增逻辑 |
| `POST /api/problems/{id}/field-orders/result`（`@Deprecated`） | `ProblemService.recordFieldResult` | 是（切向 `field` / `verify` / `triage`） | 否 | 已废弃，禁止新增逻辑 |
| `POST /api/customers` | `DeviceService.createCustomerAndDevice` | 否（客户/设备档案，非工单状态） | 否 | 主干 |
| `POST /api/admin/sops` | `SopService.saveDraft` | 否（SOP 草稿/发布，非工单状态） | 否 | 主干 |
| `POST /api/admin/sops/{id}/publish` | `SopService.publish` | 否（SOP 发布，非工单状态） | 否 | 主干 |
| `POST /api/accounts` | `AccountService.create` | 否（账号创建，非工单状态） | 否 | 主干 |
| `POST /api/accounts/duty` | `AccountService.setDutyStatus` | 否（本人调整在岗状态，非工单状态） | 否 | 主干 |
| `POST /api/accounts/{username}/duty` | `AccountService.setDutyStatus` | 否（调度/管理员调整在岗状态，非工单状态） | 否 | 主干 |
| `POST /api/accounts/password` | `AccountService.changePassword` | 否（本人改密，非工单状态） | 否 | 主干 |
| `POST /api/accounts/{username}/password` | `AccountService.resetPassword` | 否（管理员重置密码，非工单状态） | 否 | 主干 |
| `POST /api/accounts/{username}/status` | `AccountService.setEnabled` | 否（账号启停用，非工单状态） | 否 | 主干 |
