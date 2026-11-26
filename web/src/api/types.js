/**
 * @typedef {Object} FieldOrderView
 * @property {number} [id] 现场单记录 ID
 * @property {string} externalNo 外部现场单号（如智享乐装单号）
 * @property {string} state 状态：linked | in_progress | dispatched | accepted | arrived | completed
 * @property {string} receipt 现场回执说明
 * @property {number|boolean} [isClaim] 是否索赔单
 * @property {number|boolean} [returnToFactory] 是否返厂
 * @property {string} [projectCode] 项目编码
 * @property {string|null} [appointmentAt] 预约上门时间
 * @property {string|null} [photoNames] 派单附件照片名列表
 * @property {string|null} [acceptedAt] 接单时间
 * @property {string|null} [arrivedAt] 到场时间
 * @property {string|null} [completedAt] 完工时间
 * @property {string|null} [replacedParts] 更换配件清单
 * @property {string|null} [oldPartNo] 旧件编号
 * @property {string|null} [oldPartPhoto] 旧件照片
 * @property {string|null} [customerAcceptResult] 客户现场验收结果
 * @property {string|null} [feeOwner] 费用归属方
 * @property {string|null} [rollbackAnomalyNote] 状态回退异常留痕
 */

/**
 * @typedef {Object} ProblemBoardRow
 * @property {string} id 工单 UUID
 * @property {number} [displayNumber] 展示序号
 * @property {string} [ticketNo] V1.1 规范工单号（GD+YYYYMMDD+4位流水）
 * @property {string} title 问题标题/现象简述
 * @property {string|null} device 关联设备桩 SN
 * @property {string} state 工单状态
 * @property {string} owner 当前处理人用户名（early 或空表示待分配/无主）
 * @property {string} category 故障分类
 * @property {string} kind 问题类型：fault | consult | service
 * @property {string} channel 报修渠道：phone | 400 | miniprogram | provider | wecom
 * @property {number|boolean} [devicePending] 是否设备信息待补
 * @property {string} [urgency] 紧急度：low | normal | high
 * @property {string|null} [suspendReason] 挂起原因
 * @property {string|null} [grade] 故障定级：A | B | C
 * @property {string|null} [disposalType] 处置方式：remote | part | replace | onsite
 * @property {number|boolean} [compromiseFlag] 是否妥协工单
 * @property {number|boolean} [oldPartRegistered] 是否已登记旧件
 * @property {string|null} [escalationLevel] 升级级别：none | l2 | expert
 * @property {string|null} [slaDueAt] SLA 截止时间
 * @property {number} version 乐观锁版本号
 * @property {string} updatedAt 最近更新时间
 * @property {number} [activeFieldOrders] 进行中的现场单数量
 * @property {string|null} [nextStep] 待办回访下一步
 * @property {string|null} [nextDueAt] 最近一次回访到期时间
 */

/**
 * @typedef {Object} ProblemDetailView
 * @property {string} id 工单 UUID
 * @property {number} [displayNumber] 展示序号
 * @property {string} [ticketNo] V1.1 规范工单号
 * @property {string} title 问题标题/现象
 * @property {string|null} device 关联设备桩 SN
 * @property {string} state 工单状态
 * @property {string} description 详细描述
 * @property {string|null} [resumeState] 挂起前状态
 * @property {string} category 故障大类
 * @property {string} kind 问题类型
 * @property {string} channel 报修渠道
 * @property {string} owner 当前负责人
 * @property {number} version 乐观锁版本号
 * @property {string|null} [faultOccurredAt] 故障发生时点
 * @property {string|null} [suspendReason] 挂起原因
 * @property {string|null} [grade] 故障定级：A | B | C
 * @property {string|null} [disposalType] 处置方式
 * @property {number|boolean} [compromiseFlag] 是否妥协工单
 * @property {string|null} [escalationLevel] 升级级别
 * @property {string|null} [escalationReason] 升级原因
 * @property {string|null} [slaDueAt] SLA 截止时间
 * @property {string|null} [autoApprovalNote] 自动审批说明
 * @property {string|null} [customerId] 关联客户 ID
 * @property {number|boolean} [devicePending] 是否设备信息待补
 * @property {string} [urgency] 紧急度
 * @property {string|null} [symptomTags] 现象标签
 * @property {string|null} [acceptedBy] 受理人
 * @property {number|boolean} [warrantyConfirmed] 质保确认标记
 * @property {string|null} [snapshotJson] 建单固化设备档案快照 JSON
 * @property {number|boolean} [oldPartRegistered] 是否已登记旧件
 * @property {string|null} [oldPartNo] 旧件编号
 * @property {string|null} [reopenedFromId] 重开来源工单 ID
 * @property {string} createdAt 创建时间
 * @property {string} updatedAt 更新时间
 * @property {Object|null} [pendingFollowup] 当前待回访任务
 * @property {FieldOrderView[]} [fieldOrders] 关联现场单列表
 * @property {Array<Object>} [callbackResults] 回访结果记录
 * @property {Array<Object>} [activities] 跟进活动记录
 * @property {Array<Object>} [ownershipEvents] 归属变更记录
 * @property {Array<Object>} [evidences] 证据项列表
 * @property {Array<Object>} [transitions] 状态流转日志
 * @property {Array<Object>} [assessments] 定级与处置评估记录
 * @property {Object|null} [sop] 匹配的 SOP 指导信息
 */

/**
 * @typedef {Object} DeviceProfileView
 * @property {string} device 设备标识（桩 SN）
 * @property {string} sn 桩 SN
 * @property {number} matchCount 匹配设备数量
 * @property {string} source 数据来源说明
 * @property {string} observedAt 聚合查询时间
 * @property {string} fault 当前故障与遥测摘要
 * @property {string|null} [customerId] 客户 ID
 * @property {string|null} [customerName] 客户名称
 * @property {string|null} [contactName] 联系人
 * @property {string|null} [customerPhone] 联系电话
 * @property {string|null} [siteAddress] 安装站点地址
 * @property {string|null} [projectCode] 项目编号
 * @property {string|null} [projectName] 项目名称
 * @property {string|null} [customerType] 客户类型
 * @property {number|null} [slaMinutes] 约定 SLA 分钟数
 * @property {string|null} [model] 设备型号
 * @property {string|null} [productType] 产品类型
 * @property {string|null} [manufacturedAt] 出厂日期
 * @property {string|null} [mesBatch] MES 生产批次
 * @property {string|null} [mesTestResult] MES 出厂测试结果
 * @property {string|null} [installedAt] 安装日期
 * @property {string|null} [warrantyStatus] 质保状态
 * @property {string|null} [warrantyUntil] 质保截止日
 * @property {string|null} [extendedWarranty] 延保信息
 * @property {string|null} [simExpiresAt] SIM 卡到期日
 * @property {string|null} [firmwareVersion] 固件版本
 * @property {string|null} [onlineStatus] 在线状态
 * @property {string|null} [currentStatus] 当前运行状态
 * @property {string|null} [realtimeTelemetry] 实时报文摘要
 * @property {Array<Object>} [sourceStatuses] 4 源并行查询状态条
 * @property {Array<Object>} [repairHistory] 历史维修记录
 * @property {Array<Object>} [chargingRecords] 充电记录
 * @property {Array<Object>} [recentAlerts] 近期告警
 * @property {Array<Object>} [callRecordings] 400 通话录音记录
 * @property {Object|null} [simulatedCall] 当前模拟来电信息
 * @property {Array<Object>} [relatedProblems] 关联历史工单
 * @property {Object<string, string>} [deepLinks] 外部系统深链映射
 */

export {}
