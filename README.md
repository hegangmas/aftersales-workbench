# 售后工作台

团队技术栈、模块划分和开发验收约定见 [AGENTS.md](AGENTS.md)。

完整功能规划、流程图及部门评审资料见 [docs](docs/README.md)。

Java / Vue 试用工程。首条链路使用模拟设备与故障，支持创建问题单并保存跟进记录。

## 本地运行

1. 准备 JDK、Maven、Node.js、npm 和 MySQL。创建空数据库 `soongo_aftersales_java`，为本地账号授权。
2. 设置数据库环境变量 `WORKBENCH_DB_URL`、`WORKBENCH_DB_USER`、`WORKBENCH_DB_PASSWORD`，以及下面的登录配置。运行 `mvn spring-boot:run`。Flyway 初始化数据库。
3. 进入 `web`，运行 `npm ci` 和 `npm run serve`。打开终端显示的本地地址。
4. 查询 `SIM-AC-001` 或模拟手机号 `19900000001`，创建问题单，保存跟进。按界面显示的问题单 ID 核对 `problem` 和 `activity` 两张表。

后端默认只监听本机 `127.0.0.1:8788`，开发时 Vue CLI 将 `/api` 转发到该地址。打开页面后使用部署时配置的试用账号登录。设备与故障仍是模拟数据。登录页右上角标「测试环境」。

界面已由旧左侧栏 228px 改为「全宽顶栏 + 二级下拉」布局：一级导航固定 5 项（工作台、工单、设备、知识库、管理）；「管理」下拉含 4 项（客户与项目、SOP 与规则、指标与报表、权限与配置）；「工单」下含 3 个页签（调度大屏 | 统一工单池 | 我的待办）。配色沿用松果青绿浅色风（品牌色 `#0F766E` / `#0D9488`、浅底 `#E8F6F4`、冷灰画布 `#F3F5F5`、卡片纯白；登录页在宽屏左右分屏，窄于 900px 时改为顶部品牌条；问题单状态用互不混淆的浅色胶囊，待认领行用 `#FFFBF2` 和左侧琥珀条；错误仍用文字和 `#C0362C`，不把青绿当作错误色），完整组件与 Design Tokens 规范见 [docs/ui-design-system.md](docs/ui-design-system.md)。

空库首次启动时，用 `WORKBENCH_LOGIN_USER` 和 `WORKBENCH_LOGIN_PASSWORD_HASH` 创建一名管理员（如 `demo`），同时自动初始化 `dispatcher`（调度）、`agent`（坐席 `operator`）、`engineer`（工程师）共 4 角色演示账号（共用同一初始 `{bcrypt}` 口令哈希，若与管理员重名则跳过），不把口令写入仓库。库里已有账号时不会覆盖。管理员可创建或禁用坐席；至少保留一名启用的管理员。历史问题的负责人仍是原来的 `early`，表示无主，不会改写成某个真人。无主问题可以认领。转交要填写原因，并记下操作者、交出方和接收方。写操作要带当前 `version` 和 `Idempotency-Key`。版本过期或其他状态冲突返回 409。跟进、回访安排、回访结果、转交、撤回待派单、现场单关联和回执出现 409 时，页面给出中文原因和「一键刷新工单」。刷新会取回最新状态和版本号，并尽量把还没提交的草稿留在原来的表单里。如果刷新后已经结案，或这项操作不再适用，页面说明原因，并保留未提交的文字。待现场派单且还没有处理中的现场单时，可以填写原因「撤回待派单，返回排查」；已关联且未结束的现场单不能靠撤回跳过。不支持的 HTTP 方法返回 405 和 `Allow`，内容类型不对返回 415，内部错误仍是 500 且不包含 SQL。

已认领的问题单，只有当前负责人和管理员可以保存跟进、安排回访、提交回访结果（含确认恢复并结案）、转交、撤回待派单、关联现场单和登记回执。其他坐席调用这些写接口得到 403 和中文原因，时间线、状态、版本、回访任务和现场单都不变。负责人为空或 `early` 的无主单仍按先认领处理：普通坐席要先认领，管理员可以继续直接处理；转交无主单仍然要先认领。当前没有单独的授权接班人。历史 `early` 保持原值，直到有人认领。页面在无权处理时说明原因；如果请求仍然返回 403，错误提示使用服务端的中文原因。

管理员创建账号成功时返回 201，响应体为空。页面清空表单，并立即刷新账号列表和转交候选。重复账号仍返回 409 和「账号已存在」。

V5 只新增账号、归属记录和索引。回滚到不含 V5 的包时，设置 `SPRING_FLYWAY_IGNORE_MISSING_MIGRATIONS=true`。不要清库。

V6 只新增总表索引 `problem_board_updated`、账号列 `credentials_changed_at` 和审计表 `security_audit`。审计表只有操作者、动作和目标账号，不保存口令或哈希。回滚到仍带 V6 脚本的包时，多出的列、索引和表可以留着，旧查询没有 `SELECT *`。回滚到不含 V6 文件的包时，启动前设置 `SPRING_FLYWAY_IGNORE_MISSING_MIGRATIONS=true`。不要删除问题单或账号，不要执行 `flyway clean`，也不要手工删这三样对象，除非已经确认没有程序再读它们。已有负责人仍是原来的值；历史 `owner=early` 继续表示无主。

V7 只新增查桩历史索引 `problem_device_updated`（`problem(device, updated_at)`）和到期回访索引 `followup_due_queue`（`followup(state, due_at, id)`），不改动表结构与已有数据。回滚到不含 V7 文件的包时，启动前设置 `SPRING_FLYWAY_IGNORE_MISSING_MIGRATIONS=true`，不要清库。

V8（`V8__v11_core_workflow.sql`）为 `problem` 新增可空或带默认值字段（故障时点 `fault_occurred_at`、挂起原因 `suspend_reason`、A/B/C 等级 `grade`、处置方式 `disposal_type`、妥协/升级标记 `compromise_flag` / `escalation_level` / `escalation_reason`、SLA 截止时间 `sla_due_at`、自动审批说明 `auto_approval_note`）并新增证据元数据表 `evidence_item`，不改旧表结构与历史数据。回滚到不含 V8 文件的包时，启动前设置 `SPRING_FLYWAY_IGNORE_MISSING_MIGRATIONS=true`，不要清库。

最新 Flyway 迁移为 V9（`V9__v11_strict_rebuild.sql`）：在保留 V5–V8 迁移的基础上对齐 V1.1 严格一期业务底座——引入 6+1 严格状态（待分配 `pending_assign`、处理中 `processing`、挂起 `suspended`、待回传 `pending_callback`、待验收 `pending_accept`、已闭环 `closed` + 已取消 `cancelled`）、`ticket_sequence` 表支持 `GD+YYYYMMDD+4位` 发号、设备与客户本地模拟主档表（`customer_profile`、`device_profile`、`phone_sn_mapping`）、SOP 知识库与规则表（`sop_entry`）、评估/通话/状态流转日志表（`assessment_record`、`call_record`、`state_transition_log`），并扩展 `account.duty_status`、`problem` 与 `field_order` 字段；空库首次启动自动初始化 4 角色演示账号（`demo` / `WORKBENCH_LOGIN_USER` 管理员、`dispatcher` 调度、`agent` 坐席、`engineer` 工程师）。回滚到不含 V9 文件的包时，启动前设置 `SPRING_FLYWAY_IGNORE_MISSING_MIGRATIONS=true`，不要清库或执行 `flyway clean`。

## 找代码

- `device` 提供设备和故障查询，当前使用模拟数据。
- `problem` 负责问题受理、详情、跟进和团队总表；其中 `ProblemV11Controller`、`ProblemV11Service` 与 `ProblemStateMachine` 负责 V1.1 严格 6+1 状态机校验、`GD+YYYYMMDD+4位` 发号、设备待补例外队列、智享乐装派单与结构化回传、旧件登记闭环、重开新单及月度指标。`GET /api/problems/board` 按状态、负责人、故障分类和类型筛选，并按短号、桩编码或标题搜索。排序固定为更新时间倒序、主键倒序。响应另附只读 `summary`：`total` 与 `states` 各桶（待认领、处理中、待排查、待回访、待派单、现场处理中、待验证、已结案、已答复、已转出）之和相等，并与不带其他筛选的 `bucket` 查询总数一致；`due`、`dueMine`、`mine` 分别与到期回访团队、到期回访我的、我的待办的 `total` 相同。`bucket` 只改变当前列表，不改统计口径。无主且未结案计入待认领，不重复计入流程状态。待派单与现场处理中都对应 `field`，以是否还有未结束的现场单区分。问题创建与首条活动记录在同一事务中保存。页面按松果青绿视觉规范呈现登录、总表、详情、查桩建单、账号与改密；待认领数和到期回访数取自上述统计。
- `sop`（`SopController`、`SopService`、`SopMapper`）提供只读知识库接口（`/api/knowledge/sops`）与管理端唯一 SOP/审批规则写入和发布接口（`/api/admin/sops`）。
- `account` 负责账号、本人改密和管理员重置。重置和改密写入 `security_audit`，响应和日志不包含口令或哈希。
- `web/src/api/workbench.js` 集中放置浏览器请求。
- 页面级组件位于 `web/src/views/`（含设备档案与建单 `DeviceLookupView.vue`、调度大屏 `DispatchDashboard.vue`、知识库与 SOP `KnowledgeSopView.vue`、三栏个人工作台 `PersonalWorkbench.vue`、问题单总表 `ProblemBoard.vue`、查桩与建单 `IntakePanel.vue`、账号管理 `AccountPanel.vue` 与改密 `PasswordPanel.vue`），复用组件位于 `web/src/components/`（含三栏派单至智享乐装 `FieldDispatchPanel.vue`、SOP 与证据缩略卡 `SopEvidencePanel.vue`、挂起回池/快通道/SLA 升级/A-B-C 判定 `WorkflowExtras.vue` 等）。个人工作台含 6 节点进度条、证据缩略卡、模拟维修/录音信息、挂起回池、快通道、SLA 升级、A/B/C 判定与三栏派单至智享乐装；外部设备档案、维修履历、400 录音摘要与自动校验留痕均为明确标注的模拟数据展示，尚未接入真实生产接口。
- `src/main/resources/db/migration` 存放数据库迁移。当前从 Python 试用版表结构建立独立测试库。已有数据库的接入需核对迁移基线。

首批审核范围是接口、包结构和写库事务。项目范围授权、上游真实接口与通话属于后续批次。

启动后设置 `WORKBENCH_TEST_USER` 和 `WORKBENCH_TEST_PASSWORD`，运行 `python3 scripts/smoke.py` 自动验收。脚本会向测试库写入模拟问题单、跟进与现场单回执。

## 回访与恢复历史

按短单号（例如 AS-12，或直接输入 12）打开，或查桩后选择这台桩的近期问题单。已有 UUID 链接仍可用，写操作和数据关联继续使用原 UUID。打开问题单会更新地址栏，刷新可恢复详情。回访时间按北京时间录入，点击「查看到期回访」查询已到期任务。每张单保留一条当前待回访任务，历史安排和结果均写入活动记录。

回访可标记继续跟进、待现场派单或确认恢复并结案。待现场派单由坐席在智享乐装提交，再到工作台关联。确认恢复会保存验证依据。

## 现场单试用

把回访结果提交为「待现场派单」后，可输入已在智享乐装创建的现场单号。工作台记录人工登记的进度和回执。尚未关联现场单时，不能用回访跳过派单。已关联且未结束的现场单必须先登记结果。完工进入待验证，拒单或取消退回待排查。待验证可以安排回访；选择继续跟进会回到待验证，不会变成处理中。确认恢复仍须带来源和依据。此功能使用模拟数据试用，尚未通过接口核验单号或同步智享乐装状态。

## 后续交付

- P0 真数据小样需要补齐跨班接续、受理队列、设备与客户关系核实、排查指引、恢复验证和结案边界。联通 MES、智享乐装与充电桩管理平台的只读接口，逐字段核对来源、时效和异常。试用真实数据前接入企业账号、项目权限和完整操作审计。
- P1 有限试用需要按项目开通派修及回执联动，验证超时、重复回调和对账。并行验证现有群助手转人工、400 话单、外呼及录音接口，确认后接入摘要供坐席复核。
- P2 扩展客户补传和进度查询、来电弹屏、辅助分配、质量复盘及更多项目。报价、支付和审批流程另行评审。

## 试用账号与回访验证

- 启动时配置 `WORKBENCH_LOGIN_USER` 和 `WORKBENCH_LOGIN_PASSWORD_HASH`。用 `htpasswd -nBC 12 operator` 交互生成哈希，取冒号后的部分并加上 `{bcrypt}` 前缀。部署环境文件中用单引号包住整个值。密码、哈希和环境文件留在部署环境。
- 所有业务 API 要求登录，写入要求 CSRF 令牌。账号用于记录新操作的执行人，并显示在处理记录上。接口错误返回 `message` 字段中的中文原因，不返回堆栈或 SQL。按项目授权属于后续交付。
- 已登录的坐席和管理员可以修改自己的密码。必须提交当前密码。新密码为 8 到 72 位，且同时包含字母和数字，不能与当前密码或账号相同。保存后旧密码立即不能登录。当前浏览器会话保持登录：服务端轮换会话标识，并把本会话记下的凭证时间更新为数据库中的新时间，CSRF 令牌仍随这个会话使用。同一账号在其他浏览器里的旧会话，下一次请求返回 401。
- 只有管理员可以重置指定账号的密码。普通坐席调用返回 403，未登录返回 401。重置他人时，管理员自己的会话不变，被重置账号已有的会话在下一次请求返回 401。管理员重置自己时，与本人改密一样保留当前会话。页面、接口、日志和审计表都不回显新密码或哈希。
- 会话 cookie 的 `Secure` 由 `WORKBENCH_SECURE_COOKIE` 控制，默认 `false`。当前阿里云测试环境使用 HTTP，保持 `false`，不要把 HTTP 跳转到 HTTPS。以后若改为 HTTPS 正式环境，再把该变量设为 `true`。

## 阿里云独立测试部署

运行机只有 OpenJDK 8，没有 Maven、Node 或 javac。在构建机执行 `mvn package` 和 `npm ci && npm run build`，把 jar 与 `web/dist` 拷到服务器。数据仍是模拟设备，不要指向生产库。

可复制的变量名见 [deploy/aliyun-test.env.example](deploy/aliyun-test.env.example)，Nginx 见 [deploy/nginx-aliyun-test.conf.example](deploy/nginx-aliyun-test.conf.example)。示例里密码和哈希留空。测试环境使用 HTTP，`WORKBENCH_SECURE_COOKIE=false`，监听 `127.0.0.1:8788`，由本机 Nginx 的 80 端口提供页面并把 `/api/` 转到该端口。内存紧张时限制堆：

```bash
java -Xms128m -Xmx384m -jar aftersales-workbench-0.1.0-SNAPSHOT.jar
```

健康检查：`curl -sS http://127.0.0.1:8788/api/health`，期望 `status` 为 `up`、`mode` 为 `simulated`。再经 Nginx 访问 `http://<测试域名>/api/health` 和首页。用测试库跑 `python3 scripts/smoke.py`。脚本会写入模拟问题单。

回滚：停掉新进程，换回上一份仍包含已执行迁移脚本的 jar。V4 只增加可空列 `problem.resume_state`，不改 V1、V2、V3。旧程序的查询没有 `SELECT *`，多出的列不影响读取。若回滚到不含 V4 文件的包，而库里已经执行过 V4，启动前设置 `SPRING_FLYWAY_IGNORE_MISSING_MIGRATIONS=true`，否则 Flyway 会拒绝启动。不要删除问题单，也不要在回滚时执行 `flyway clean`。

## 状态规则

已结案不能再写。存在 `linked` 或 `in_progress` 现场单时，不能用回访绕过。处于现场阶段但还没关联现场单时，也不能直接改约或提交回访结果。待验证可以安排回访，继续跟进回到待验证；带核验来源和依据才能结案。待排查的继续跟进仍回到处理中，这次没有新增状态。
- 「设回访」保存计划，「现在回访」保存实际联系时间，也可在未安排任务时直接记录结果。确认恢复需要选择车主确认、平台记录或现场复测，并说明具体依据。历史结案保留原记录。
