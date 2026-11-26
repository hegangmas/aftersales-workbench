# 售后工作台（Aftersales Workbench）

面向充电桩售后团队的工单调度与处置工作台，支持设备查档、智能派单、SOP 证据核验与闭环追踪。

- 开发约定与红线：[AGENTS.md](AGENTS.md)
- 业务与需求索引：[docs/README.md](docs/README.md)
- 现行接口边界：[docs/architecture/api-boundaries.md](docs/architecture/api-boundaries.md)
- 视觉与组件规范：[docs/ui-design-system.md](docs/ui-design-system.md)

## 技术栈与当前边界

- **后端**：Java 8 · Spring Boot 2.7.16 · MyBatis-Plus 3.5.3.1 · MySQL 8.0 · Flyway（V1–V9）
- **前端**：Vue 3 · Element Plus · Vue CLI 5（全宽 5 项顶栏 + 二级下拉，松果青绿浅色主题）
- **当前边界**：外部 MES、安装平台、智享乐装与 400 通话当前默认 `simulated` 模拟模式，真实接口后续按客户端边界逐项接入。

## 本地启动与默认账号

1. 准备 JDK 8、Maven、Node.js、npm 与 MySQL 8.0，创建空库（如 `soongo_aftersales_trial`）。
2. 配置环境变量（参考 [deploy/aliyun-test.env.example](deploy/aliyun-test.env.example)）：
   - 数据库连接：`WORKBENCH_DB_URL`、`WORKBENCH_DB_USER`、`WORKBENCH_DB_PASSWORD`
   - 管理员初始化：`WORKBENCH_LOGIN_USER`、`WORKBENCH_LOGIN_PASSWORD_HASH`
   - 会话 Cookie：`WORKBENCH_SECURE_COOKIE`（本地与 HTTP 测试环境保持 `false`，HTTPS 再改 `true`）
3. 生成 `{bcrypt}` 口令哈希示例（取冒号后内容加 `{bcrypt}` 前缀并在环境文件中用单引号包裹）：
   ```bash
   htpasswd -nBC 12 demo
   ```
4. 启动后端（`mvn spring-boot:run`，默认监听 `127.0.0.1:8788`）：
   - 空库首次启动自动初始化管理员（`WORKBENCH_LOGIN_USER`）及 3 个演示号：`dispatcher`（调度）、`agent`（坐席 `operator`）、`engineer`（工程师）。
   - 演示号共用同一初始 `{bcrypt}` 哈希；若与管理员重名则跳过，库内已有账号时绝不覆盖。
5. 启动前端（开发代理将 `/api` 转发至 `127.0.0.1:8788`）：
   ```bash
   cd web && npm ci && npm run serve
   ```

## 代码目录地图

| 端别 | 路径 | 职责与核心文件 |
| --- | --- | --- |
| 后端 | `src/main/java/com/csg/workbench/problem/` | `ProblemV11Controller`、`ProblemV11Service`、`ProblemStateMachine`（6+1 状态机、`GD+YYYYMMDD+4位` 发号）、`dto/` 视图组装、兼容读/旁路写 |
| 后端 | `src/main/java/com/csg/workbench/device/` | `DeviceController` / `DeviceService` / `DeviceMapper`（设备档案、故障与近期工单查询） |
| 后端 | `src/main/java/com/csg/workbench/sop/` | `SopController` / `SopService` / `SopMapper`（只读 `/api/knowledge/sops` 与唯一写端 `/api/admin/sops`） |
| 后端 | `src/main/java/com/csg/workbench/account/` | `AccountController` / `AccountService` / `AccountBootstrap`（账号、在岗状态、改密与演示初始化） |
| 后端 | `src/main/java/com/csg/workbench/auth/` | `SecurityConfiguration`、`SessionController`、`WorkbenchSession`（Session、CSRF 与角色鉴权） |
| 后端 | `src/main/java/com/csg/workbench/integration/` | `MesClient`、`InstallClient`、`LezhuangClient`、`CallCenterClient` 抽象与 `Simulated*Client` 实现 |
| 后端 | `src/main/resources/db/migration/` | Flyway `V1`–`V9` 迁移基线，受 `scripts/check_migrations.py` 不可变守护 |
| 前端 | `web/src/App.vue` + `components/layout/TopNav.vue` | 全宽壳层与固定 5 项一级顶栏（工作台 / 工单 / 设备 / 知识库 / 管理） |
| 前端 | `web/src/nav/menuConfig.js` | 7 角色显隐矩阵与工单默认页签路由守卫 |
| 前端 | `web/src/contracts/problem-status.json` + `status.js` | V1.1 6+1 状态单源契约（标签、流转、色调与只读旧态映射） |
| 前端 | `web/src/api/workbench.js` | 全站 API 请求封装与 CSRF / 错误消息处理 |
| 前端 | `web/src/composables/` | `useAppRoute.js`、`useProblemActions.js`、`useBoardQueues.js`、`useProblemDraft.js` |
| 前端 | `web/src/views/` | `PersonalWorkbench.vue`、`ProblemBoard.vue`、`DispatchDashboard.vue`、`DeviceLookupView.vue`、`AdminCustomersView.vue`、`AdminReportsView.vue`、`AccountPanel.vue`、`PasswordPanel.vue` |
| 前端 | `web/src/views/knowledge/` & `admin/` | `knowledge/KnowledgeSopView.vue`（纯只读发布版）、`admin/AdminSopView.vue`（唯一写端，含 3px 琥珀警示边） |
| 前端 | `web/src/components/` | `sop/`、`dispatch/`、`device/`、`field/` 及协作面板（`FieldDispatchPanel.vue`、`SopEvidencePanel.vue`、`WorkflowExtras.vue`） |

## 核心业务与接口约束速览

- **V1.1 6+1 状态机单源契约**：统一使用 `pending_assign`、`processing`、`suspended`、`pending_callback`、`pending_accept`、`closed` + `cancelled`，由后端 `ProblemStateMachine` 与前端 `problem-status.json` 共同约束。
- **写操作安全与并发控制**：所有业务写接口强制登录与 CSRF 令牌，需携带 `Idempotency-Key` 与 `expectedVersion` 乐观锁；版本冲突返回 `409` 中文原因，前端一键刷新工单并保留未提交草稿。
- **知识库与管理端物理隔离**：`知识库`（`/api/knowledge/sops`）只读发布内容且不展示判定阈值；`管理 → SOP 与规则`（`/api/admin/sops`）为唯一编辑与发布入口。
- **角色权限与密钥安全**：7 角色视图与写权限由前后端联合校验；本人改密与管理员重置密码均轮换受影响会话、记录 `security_audit` 且响应/日志绝不回显密码或哈希。
- **详细接口契约**：V1.1 主干接口、兼容读/旁路写清单与集成层切换规则见 [docs/architecture/api-boundaries.md](docs/architecture/api-boundaries.md)。

## 测试与门禁验证

```bash
python3 scripts/check_migrations.py                    # 校验 Flyway V1–V9 基线不可篡改
mvn test                                               # 后端单测与 ArchUnit 架构门禁
mvn package -DskipTests                                # 打包后端可运行 JAR
cd web && npm run lint && npm test && npm run build    # 前端 ESLint、Vitest 单测与生产构建
python3 scripts/smoke.py                               # 冒烟验收（需 WORKBENCH_TEST_USER / WORKBENCH_TEST_PASSWORD）
```

## 阿里云测试环境部署与 Flyway 回滚原则

- **运行环境与产物**：运行机仅需 OpenJDK 8，在构建机执行打包后上传后端 JAR 与前端 `web/dist`（勿连生产库）。
- **环境配置与反代**：环境变量模板见 [deploy/aliyun-test.env.example](deploy/aliyun-test.env.example)，Nginx 80 端口反代 `/api/` 配置见 [deploy/nginx-aliyun-test.conf.example](deploy/nginx-aliyun-test.conf.example)。
- **进程启动**：后端仅监听 `127.0.0.1:8788`，内存受限时可限制堆大小：
  ```bash
  java -Xms128m -Xmx384m -jar target/aftersales-workbench-0.1.0-SNAPSHOT.jar
  ```
- **健康检查**：请求 `http://127.0.0.1:8788/api/health`（或经 Nginx 访问），确认返回 `status=up` 与 `mode=simulated`。
- **Flyway 升级/回滚铁律**：已部署的 `V1`–`V9` 迁移文件严禁修改或执行 `flyway clean`，新增表结构须走 `V10+` 可空或带默认值增量迁移；若回滚到不含高版本迁移脚本的旧包，启动前设置 `SPRING_FLYWAY_IGNORE_MISSING_MIGRATIONS=true`，严禁清库。
