> [!NOTE]
> **落地状态说明（2026-11-26 更新）**
> - **波次 1（工程护栏 D1–D8）**：**已完成**（提交 `dd2ad2a`）——CI MySQL + `smoke.py`、Flyway SHA-256 守卫、`problem-status.json` 单源契约、ArchUnit 规则冻结、ESLint 门禁、写入口登记表、Vitest 导航矩阵、死 CSS 清理均已入库。
> - **波次 2（架构中枢重构）**：**已完成**（提交 `a06eb24`）——7 个旁路写接口全部收口至 `ProblemV11Service`（`quick-close` 走 `pending_accept`、`suspend` 统一 `suspended`）、4 热点强类型 DTO 与 49 组状态矩阵单测落地、知识库与管理 SOP 前后端物理拆分、`App.vue` 瘦身至 451 行。
> - **波次 3（真实外部对接与收尾）**：**待启动**——待外部系统（智享乐装签名与回调契约、MES、充电桩平台、400 客服）联调条件就绪后推进 `real` 客户端实现与 `V10__integration_inbox.sql`。
> - **原文保留**：以下完整保留 Claude 基于快照 `ed90b15` 的架构评审原文供回溯核查。

# 松果售后工单中台：架构体检与改进建议

> 本次评审只依据快照 `ed90b15`。我没有读取源码，没有修改文件，也没有执行命令。凡标「需核实」的地方，都是从快照推断出来的，开发代理落地前要先确认。

---

## 一、最致命的 5 个隐患（按风险排序）

### 1. 工单写入口有两套：`ProblemService` (878 行) 和 `ProblemV11Service` (447 行) 并存
- **位置**：`ProblemController` 里有 7 个旁路写接口（`followups` / `handoff` / `suspend` / `quick-close` / `escalate` / `evidences` / `assessment`），走的是 `ProblemService`。V1.1 的主状态流转走 `ProblemV11Controller` → `ProblemV11Service`。
- **根因**：谁负责改 `problem` 没有唯一答案。AI 代理一般改它先搜到的服务，而 878 行的旧服务更容易被搜到。
- **典型回归**：
  - `quick-close` 绕过旧件结案卡点。
  - `suspend` / `escalate` 改了状态，但不写 `state_transition_log`，也不走乐观锁。
  - 新规则只加在 V11 服务里，旧路径上的规则悄悄失效。
- **后果**：直接产生脏数据，事后很难追溯。

### 2. 状态口径散落在 5 处以上
- **散落位置**：
  - `ProblemStateMachine` (78 行)，推测是旧口径，需核实。
  - `ProblemV11Service` 内置的 6+1 状态。
  - `ProblemMapper` / `BoardQueries` 的 SQL 字符串里的状态字面量。
  - `web/src/status.js` (228 行)，新旧状态并存。
- **根因**：没有单一状态源，新旧兼容逻辑前后端各写了一遍。
- **典型分歧**：
  - 看板计数和列表条数不一致。
  - 新增状态时只改了一端。
  - 旧单的 `resolved` 在前端显示为「已结案」，后端却不认为它是 `closed`。

### 3. 门禁「假绿」，AI 代理会把 CI 绿灯当成「完成」
- **集成测试不在 CI 里跑**：真正守护升级、权限和链路的 13 个 MySQL 集成测试，在没有数据库凭据时会**静默跳过**。它们是 `SchemaUpgradeTest`、`ProblemHandlingAccessTest`、`HttpApiChainTest`、`WorkbenchApiCoverageTest`。
- **其他门禁空缺**：`smoke.py` 不在 CI 里；`web/.eslintrc.cjs` 已建，但没装 eslint，等于空壳。
- **Flyway 有两个漏洞**：
  - CI 用全新库，`flyway validate` 发现不了有人改了 V1–V9 的内容。
  - 多个代理并行开分支，会各自新建 `V10__*.sql` 撞号。

### 4. `Map<String,Object>` 加注解 SQL 别名，成了隐式接口契约
- **现状**：`ProblemMapper` (339 行) 里 `@Select` 的列别名，经 Service / Controller 原样透传，直接成了纯 JS 前端读取的 JSON 字段名。
- **后果**：
  - 改一个别名（比如 `created_at` → `createdAt`），编译和单测全过，前端静默拿到 `undefined`。
  - 接真实系统后，外部系统的字段名也会顺着 Map 渗进业务层。

### 5. 前端枢纽文件既是合并冲突热点，也是铁律泄露点
- **`App.vue` (916 行)**：
  - 菜单、角色守卫、手写的 `pushState` 路由、20 多个写事件透传全挤在一个文件里，任何新功能都要改它。
  - 角色显隐在前端只有这一份实现，误改一处就会破坏「5 项一级菜单」铁律。
- **`KnowledgeSopView.vue` (1387 行) + `SopRulesSidebar.vue` (876 行)**：
  - 如果只读知识库和管理写端共用组件、靠 prop / `v-if` 切换（需核实），改一次就可能把编辑按钮或 A/B/C 阈值区块漏到知识库。
  - 后端剥离字段只能防数据泄露，防不住 UI 控件泄露。

---

## 二、该做与坚决不做

### ✅ 现在值得做（每项不超过 3 天）

| # | 改造点 | 具体动作 | 验收标准 |
|---|---|---|---|
| D1 | CI 补上真门禁 | 1. `ci.yml` 增加 `mysql:8.0` service。<br>2. 设置 `WORKBENCH_IT_REQUIRED=true`，没有数据库时集成测试**失败而不是跳过**。<br>3. 启动 JAR 后执行 `scripts/smoke.py`。 | 13 项集成测试和 smoke 在 PR 上真正执行；故意去掉数据库凭据时 CI 变红 |
| D2 | Flyway 防篡改、防撞号 | 1. 新增 `scripts/check_migrations.py`，校验 V1–V9 的 sha256 与 `docs/architecture/flyway-baseline.sha256` 一致，且版本号不重复、连续。<br>2. `AGENTS.md` 写明：新迁移号在 PR 标题里声明，合并前 rebase 重新排号。 | 改动 V1–V9 任一字节，CI 变红；出现两个 V10，CI 变红 |
| D3 | 状态单一源 | 1. 新增 `web/src/contracts/problem-status.json`，内容包括状态码、中文名、分组、允许的迁移和旧码映射。<br>2. 后端新增 `problem/ProblemStatus.java` 枚举，以及 `ProblemStatusContractTest`：读取这份 JSON，比对枚举和 `ProblemStateMachine` 的迁移表。<br>3. `status.js` 改为从 JSON 派生。 | 只在一侧增删状态，单测变红；旧码只能用于展示，写操作传入旧码会被拒绝 |
| D4 | 架构规则测试（冻结存量） | `pom.xml` 增加 test 范围依赖 `archunit-junit5`。`ArchitectureRulesTest` 用 `FreezingArchRule` 冻结以下三条规则的现有违规：<br>① `integration..` 包的公共方法不得用 `Map` 作入参或出参；<br>② Controller 不得直接依赖 Mapper；<br>③ 只有 `ProblemV11Service` 可以调用改状态的 Mapper 方法。 | 现有违规写入冻结存档（ArchUnit 冻结存储）；新增违规时 CI 变红 |
| D5 | ESLint 真正跑起来 | 1. 安装 `eslint@8`、`eslint-plugin-vue@9`、`@vue/cli-plugin-eslint@5`。<br>2. `npm run lint` 加入 CI。<br>3. 规则用 `plugin:vue/recommended`、`no-unused-vars`、`max-lines: [warn, 500]`。 | lint 0 error；现有大文件只报 warn |
| D6 | 写入口登记表 | 1. 在 `docs/architecture/api-boundaries.md` 增加一张表：每个写接口 → 负责的 Service 方法 → 是否改状态 → 是否写 `state_transition_log`。<br>2. 7 个旁路接口标为「待收口，禁止新增逻辑」。<br>3. PR 模板加勾选项。 | 改写接口的 PR 必须同步更新这张表 |
| D7 | 菜单配置化并加测试 | 1. 抽出 `web/src/nav/menuConfig.js`（5 菜单、3 页签、4 管理项、7 角色可见矩阵）和 `canSee(role, key)`。<br>2. 引入 `vitest`，只作开发依赖、只测纯 JS。<br>3. `menuConfig.spec.js` 按 7 角色 × 菜单逐项断言 AGENTS 铁律。 | `provider` / `oem` 能看到工作台、设备、管理、调度大屏或「+ 新建工单」中任意一项时，测试变红 |
| D8 | 清理死样式 | 删除 `workbench.css` 中 `.sidebar` / `.workspace` 约 200 行旧样式 | 全仓 grep 无引用；build 通过；主要页面目视无变化 |

### ❌ 现阶段坚决不做

| 常见建议 | 不做的理由 | 替代方案 / 何时重新评估 |
|---|---|---|
| 全量迁移 TypeScript | 改写 1.3 万行 JS 期间和业务开发冲突最大，Vue CLI 5 加 TS 还要额外维护配置 | 用 `web/src/api/types.js` 写 JSDoc `@typedef`，覆盖热点接口 |
| 引入 Pinia | 跨视图共享的只有登录态、通知、当前路由，composable 足够 | 3 个以上视图共享可写状态，或 props 透传超过 3 层时再评估 |
| 用 vue-router 替换手写路由 | 现有深链和 Nginx 子路径兼容已经稳定，迁移有回归风险、收益小 | 先抽出 `useAppRoute.js`；视图超过 12 个或需要嵌套路由时再换 |
| 微服务、Spring Cloud、网关 | 单实例单库，拆分只会增加部署和一致性成本 | 不做 |
| 用 Kafka / RabbitMQ 处理回传 | 回传量低，数据库收件表加唯一约束就能做到幂等 | 回传峰值达到每秒数十次，或需要多个消费者时 |
| Redis 缓存或分布式锁 | 单实例部署，乐观锁加唯一索引已经够用 | 改为多实例部署时 |
| Resilience4j / Hystrix 熔断 | 4 个源都是只读查询，超时 + 并行 + 降级能覆盖绝大部分场景 | 某个源频繁超时、拖垮线程池时，再引入 Resilience4j 1.7（兼容 Java 8） |
| 全量把 Map 改成 DTO，注解 SQL 迁到 XML | 改动覆盖全部接口，回归不可控 | 只在三类边界上强类型，见第三部分 3.3 |
| 一次性删除旧状态或旧服务 | 涉及试用库旧单的数据迁移，这是业务决策 | 先收口写入口，保留旧码的展示映射 |
| OpenAPI 代码生成、Playwright E2E、DDD / CQRS 重分层、升级 Boot 3 | 当前体量下投入产出比低，或违反运行基线 | 用 `smoke.py` 加关键契约测试 |

---

## 三、接真实外部系统前的 3 个防坑设计

### 3.1 `integration/` 四源聚合：结果显式化、逐源切换、有界并行

1. **统一返回契约**：把 `SourceStatus` 扩展为 `SourceResult<T>`。
   - 字段：`state`（取值 `OK` / `EMPTY` / `UNAUTHORIZED` / `UNAVAILABLE` / `TIMEOUT`）、`data`、`fetchedAt`、`deepLink`、`errorCode`。
   - Client 约定：**不抛异常，也不返回 null**，异常在 Client 内部映射成状态。这对应 AGENTS 要求的「无记录 / 未授权 / 暂不可用」三种区分。
2. **逐源切换**：把全局的 `workbench.integration.mode` 改为每个源单独配置：`workbench.integration.{mes|install|lezhuang|callcenter}.mode=simulated|http`，`@ConditionalOnProperty` 也改成逐源判断。这样可以先只切 MES，其余继续用模拟。
3. **有界并行（Java 8 写法）**：
   - 新增 `integration/IntegrationExecutorConfig`，配置独立的 `ThreadPoolTaskExecutor`：core 8、max 16、queue 64，被拒绝的任务直接记为 `UNAVAILABLE`。
   - `DeviceService` 用 `CompletableFuture.supplyAsync(…, executor)` 同时发起 4 个源的请求，统一截止时间约 2.5 秒。
   - 用 `future.get(剩余毫秒)` 收集结果（Java 8 没有 `orTimeout`）；超时的记 `TIMEOUT` 并调用 `cancel(true)`。
4. **按源分子包**：以 MES 为例，`integration/mes/` 下放三个类：
   - `MesHttpClient implements MesClient`；
   - `MesApiDto`，用外部字段名，只在包内可见；
   - `MesResponseMapper`，负责外部 → 内部的字段映射。
   每个源用独立的 `RestTemplate`，连接超时 1 秒、读取超时 2 秒；基础地址和凭据只从环境变量读。
5. **测试**：用 spring-test 自带的 `MockRestServiceServer`（不加新依赖），覆盖 200、空结果、401、5xx、超时到 `SourceResult` 的映射。样例 JSON 放在 `src/test/resources/integration/<source>/`，只用模拟数据。
6. **收紧测试钩子**：`?failSource=` 只在对应源 `mode=simulated` 时生效，http 模式下忽略，防止进入生产。

### 3.2 智享乐装回传幂等：收件表、单调推进、签名校验

1. **新建收件表**：`V10__integration_inbox.sql` 创建 `integration_inbox`：
   - 字段：`id, source, event_key, payload_hash, payload_json, result, field_order_id, problem_id, received_at, processed_at, error_msg`。
   - `result` 取值：`accepted` / `duplicate` / `stale` / `conflict` / `unmatched` / `failed`。
   - 约束：`UNIQUE(source, event_key)`。
   - 同一迁移里给 `field_order` 增加 `external_event_at` 列。
   - `idempotency_record` 继续只管内部 UI 防重复提交，内外分开。
2. **幂等键**：`event_key = 乐装单号 + 事件类型 + 对方事件 ID`；对方没有事件 ID 时用对方的事件时间戳。
3. **处理流程（同一事务）**：
   1. 先 INSERT 收件表。如果抛 `DuplicateKeyException`：内容 hash 相同，返回首次处理结果（记 `duplicate`）；hash 不同，记 `conflict` 并告警，**不覆盖**。
   2. 查找本地 `field_order`。找不到就记 `unmatched`，进入人工关联队列，和现在「坐席人工关联单号」的做法一致。
   3. 事件时间早于 `external_event_at`，或状态等级倒退，记 `stale`，只留痕不改数据。
   4. 需要改状态时，一律调用 `ProblemV11Service` 的统一迁移方法，在同一事务里写 `activity` 和 `state_transition_log`。
4. **响应语义**：`accepted` / `duplicate` / `stale` 都返回 200，让对方停止重试；只有签名或格式错误返回 4xx。这一点**需要和智享乐装书面约定**。
5. **入站鉴权**：
   - 使用独立路径 `/api/integration/lezhuang/callbacks`。
   - `SecurityConfiguration` 只对这一个路径免 Session / CSRF，改用 HMAC-SHA256 签名、5 分钟时间戳窗口，再加 Nginx IP 白名单。
   - 密钥只放在部署环境里。
6. **必测用例**：
   - 同一事件 2 个线程并发，只生效一次；
   - 同键不同内容；
   - 乱序到达（先收到「完工」，后收到「到场」）；
   - 单号匹配不到；
   - 事务中途异常，整体回滚。

### 3.3 `Map<String,Object>` 弱类型：只在三道边界上强类型

| 边界 | 动作 | 门禁 |
|---|---|---|
| 外部系统出入参 | `integration..` 包的公共接口一律用 POJO，禁止用 `Map` | ArchUnit 规则，零容忍，不冻结存量 |
| 外部回传请求体 | 定义 `LezhuangCallbackRequest`，用 `@Valid` 校验（Boot 2.7 用的是 `javax.validation`） | 缺字段返回 400 的单测 |
| 前端热点响应（先做 4 个） | 1. 定义 `ProblemDetailView`、`ProblemBoardRow`、`FieldOrderView`、`DeviceProfileView`。<br>2. Mapper 可以暂时继续返回 Map，统一在 `problem/ProblemViewAssembler` 一处转换。<br>3. Jackson 全局固定 camelCase，SQL 别名不再决定 JSON 字段名。 | 1. 纯单测：把 DTO 序列化后的字段集合，与 `src/test/resources/contracts/*.keys.json` 比对。<br>2. 前端在 `web/src/api/types.js` 写同名 typedef。 |

规则：**新接口禁止返回 Map**；存量接口改到哪个就顺手转换哪个，不专门排期。

---

## 四、落地路线图

**每个 PR 都要过的门禁链**：

`mvn package` → MySQL 集成测试（不得跳过） → `check_migrations.py` → `npm ci && npm run lint && npm test && npm run build` → UI 红线 grep → `smoke.py`

**协作约定**：
- 一个 PR 只做一个条目，并在描述里引用条目编号（D1–D8 / W2-x / W3-x）。
- 热点文件同一时间只允许一个代理改：`App.vue`、`ProblemController`、`ProblemMapper`、`status.js` / `problem-status.json`、`ci.yml`、新 Flyway 版本号。

### 波次 1：先上护栏（约 3–4 天，不改业务行为）
- **改动范围**（即 D1–D8）：
  - CI 与 Flyway：`.github/workflows/ci.yml`、`pom.xml`（只加 test 依赖）、`scripts/check_migrations.py`、`docs/architecture/flyway-baseline.sha256`。
  - 后端规则与状态：`ArchitectureRulesTest.java`、`problem/ProblemStatus.java`、`ProblemStatusContractTest.java`。
  - 前端：`web/src/contracts/problem-status.json`、`web/src/status.js`、`web/package.json` 和 lock 文件、`web/.eslintrc.cjs`、`web/src/nav/menuConfig.js` 及其 spec、`workbench.css`。
  - 文档：`api-boundaries.md`、`AGENTS.md`、PR 模板。
- **不碰**：业务 Service、Flyway 迁移脚本。
- **前置**：业务负责人确认「旧状态 → V1.1」的展示映射表。
- **并行拆分**：
  - 代理 A：D1 / D2 / D4 / D6（后端与 CI）；
  - 代理 B：D5 / D7 / D8（前端）；
  - D3 跨前后端，由同一个代理独立完成。
- **出口条件**：门禁链全绿，并且故意制造违规时 CI 确实变红。

### 波次 2：收口写入口、拆前端枢纽（约 5–7 天）
- **后端**：
  1. `ProblemController` 的 7 个旁路写接口**URL 保持不变**，内部改为调用 V1.1 路径。不改状态的部分可以按职责拆到 `ProblemFollowupService` / `ProblemEvidenceService`，但凡改状态，都必须调用 `ProblemV11Service` 的统一迁移方法。
  2. `ProblemService` 缩减为只读查询。
  3. 相关的 `ProblemServiceTest` 用例迁移过去。
  4. 新增 `ProblemTransitionMatrixTest`，由 `problem-status.json` 驱动，覆盖「状态 × 操作」的全部组合。
  5. 实现 3.3 中的 4 个 DTO 和 `ProblemViewAssembler`。
- **前端**：
  1. `App.vue` 拆出 `composables/useAppRoute.js`、`composables/useProblemActions.js`、`components/layout/TopNav.vue`，目标不超过 500 行。
  2. 知识库和管理端 SOP 物理分离：`views/knowledge/KnowledgeSopView.vue` 只读；`views/admin/AdminSopView.vue` 是写端，带 3px 琥珀色警示边。两者只共享纯展示子组件。
  3. 新增 `web/src/api/types.js`。
- **前置**：
  - 波次 1 门禁全绿；
  - 业务负责人确认 `quick-close` / `escalate` / `handoff` / `suspend` 在 V1.1 状态机下的语义。
- **门禁**：
  - ArchUnit 规则③解冻，必须零违规；
  - grep 检查：`views/knowledge/**` 及其 import 链里不得出现 `/api/admin/`、`gradeThresholds`、`autoApprovalRules`；
  - `App.vue` 的 `max-lines` 从 warn 升为 error；
  - `SchemaUpgradeTest` 验证旧单在升级前后都可查。

### 波次 3：外部接入就绪（约 5 天，在首个真实系统联调前完成）
- **改动范围**：
  - 聚合层：`integration/SourceResult.java`、`IntegrationExecutorConfig.java`、逐源 mode 配置（`application*.yml` 只放环境变量占位）、`DeviceService` 改为并行聚合。
  - 首个真实源：`integration/mes/*`，建议从只读的 MES 开始。
  - 回传：`V10__integration_inbox.sql`，以及 `integration/lezhuang/` 下的 `LezhuangCallbackController` / `LezhuangCallbackService` / `LezhuangCallbackRequest`。
  - `SecurityConfiguration` 只豁免回传这一个路径；同步更新 `README.md`。
- **前置**：
  - 波次 2 完成，因为回传必须走统一迁移方法；
  - 拿到对方的接口文档、签名方案、沙箱地址，并书面确认响应语义；
  - 400 在供应商确认前只定义接口，不做实现。
- **门禁**：
  - `MockRestServiceServer` 覆盖超时、401、5xx；
  - 3.2 中的 5 类回传用例全部通过；
  - `SchemaUpgradeTest` 覆盖带旧数据从 V9 升级到 V10；
  - smoke 增加「MES 用 http mock、其余用模拟」的组合；
  - 仓库凭据扫描。

---

### 需要业务负责人确认后才能动手的事项
1. 旧状态 `open` / `waiting` / `field` / `verify` / `resolved` 怎样映射到 V1.1，以及是否需要用 V1x 做数据迁移。
2. `quick-close` / `escalate` / `handoff` / `suspend` 在 V1.1 下是否保留，各自对应哪条状态迁移。
3. 和智享乐装约定回传的响应语义（200 / 4xx 的边界）和签名方案。