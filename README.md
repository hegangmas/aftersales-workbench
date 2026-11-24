# 售后工作台

团队技术栈、模块划分和开发验收约定见 [AGENTS.md](AGENTS.md)。

完整功能规划、流程图及部门评审资料见 [docs](docs/README.md)。

Java / Vue 试用工程。首条链路使用模拟设备与故障，支持创建问题单并保存跟进记录。

## 本地运行

1. 准备 JDK、Maven、Node.js、npm 和 MySQL。创建空数据库 `soongo_aftersales_java`，为本地账号授权。
2. 设置数据库环境变量 `WORKBENCH_DB_URL`、`WORKBENCH_DB_USER`、`WORKBENCH_DB_PASSWORD`，以及下面的登录配置。运行 `mvn spring-boot:run`。Flyway 初始化数据库。
3. 进入 `web`，运行 `npm ci` 和 `npm run serve`。打开终端显示的本地地址。
4. 查询 `SIM-AC-001` 或模拟手机号 `19900000001`，创建问题单，保存跟进。按界面显示的问题单 ID 核对 `problem` 和 `activity` 两张表。

后端默认只监听本机 `127.0.0.1:8788`，开发时 Vue CLI 将 `/api` 转发到该地址。打开页面后使用部署时配置的试用账号登录。设备与故障仍是模拟数据，页面标有「模拟数据 · 未连接生产系统」。

界面使用公司提供的松果标志原图 `web/src/assets/logo-soongo-teal.png`，主色 `#006072`。成功、警告和错误分别用文字加 `#2E7D4F`、`#8F6A00`、`#C0362C`，不把青绿当作成功色。

空库首次启动时，用 `WORKBENCH_LOGIN_USER` 和 `WORKBENCH_LOGIN_PASSWORD_HASH` 创建一名管理员，不把口令写入仓库。库里已有账号时不会覆盖。管理员可创建或禁用坐席；至少保留一名启用的管理员。历史问题的负责人仍是原来的 `early`，表示无主，不会改写成某个真人。无主问题可以认领。转交要填写原因，并记下操作者、交出方和接收方。写操作要带当前 `version` 和 `Idempotency-Key`。版本过期返回 409，并可以刷新问题单后再提交。待现场派单且还没有处理中的现场单时，可以填写原因「撤回待派单，返回排查」；已关联且未结束的现场单不能靠撤回跳过。不支持的 HTTP 方法返回 405 和 `Allow`，内容类型不对返回 415，内部错误仍是 500 且不包含 SQL。

V5 只新增账号、归属记录和索引。回滚到不含 V5 的包时，设置 `SPRING_FLYWAY_IGNORE_MISSING_MIGRATIONS=true`。不要清库。

## 找代码

- `device` 提供设备和故障查询，当前使用模拟数据。
- `problem` 负责问题受理、详情和跟进。问题创建与首条活动记录在同一事务中保存。
- `web/src/api/workbench.js` 集中放置浏览器请求。
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
- 当前配置支持一个试用坐席。所有业务 API 要求登录，写入要求 CSRF 令牌。账号用于记录新操作的执行人，并显示在处理记录上。接口错误返回 `message` 字段中的中文原因，不返回堆栈或 SQL。多账号、单点登录和按项目授权属于后续交付。
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
