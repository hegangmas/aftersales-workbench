# 售后工作台

Java / Vue 试用工程。首条链路使用模拟设备与故障，支持创建问题单并保存跟进记录。

## 本地运行

1. 准备 JDK、Maven、Node.js、npm 和 MySQL。创建空数据库 `soongo_aftersales_java`，为本地账号授权。
2. 设置环境变量 `WORKBENCH_DB_URL`、`WORKBENCH_DB_USER`、`WORKBENCH_DB_PASSWORD`。运行 `mvn spring-boot:run`。Flyway 初始化数据库。
3. 进入 `web`，运行 `npm ci` 和 `npm run serve`。打开终端显示的本地地址。
4. 查询 `SIM-AC-001` 或模拟手机号 `19900000001`，创建问题单，保存跟进。按界面显示的问题单 ID 核对 `problem` 和 `activity` 两张表。

后端默认监听本机 8780 端口。开发时 Vue CLI 将 `/api` 转发到后端。试用 API 暂无登录鉴权，适用于本机联调。

## 找代码

- `device` 提供设备和故障查询，当前使用模拟数据。
- `problem` 负责问题受理、详情和跟进。问题创建与首条活动记录在同一事务中保存。
- `web/src/api/workbench.js` 集中放置浏览器请求。
- `src/main/resources/db/migration` 存放数据库迁移。当前从 Python 试用版表结构建立独立测试库。已有数据库的接入需核对迁移基线。

首批审核范围是接口、包结构和写库事务。派单、鉴权、上游真实接口、通话与线上部署属于后续批次。

启动后可以运行 `python3 scripts/smoke.py` 自动验收。脚本会向测试库写入一条问题单和跟进记录。
