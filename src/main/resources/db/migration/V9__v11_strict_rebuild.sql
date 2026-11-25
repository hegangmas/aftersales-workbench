-- V1.1 严格一期重建迁移：新增客户/设备/手机号映射/SOP知识库/判定记录/通话记录/状态流水/发号器表，
-- 扩展 account（6角色+在岗状态）、problem（GD单号/6+1严格状态机/设备待补/快照/旧件闭环）与 field_order（图8派单与结构化回传）。

ALTER TABLE account
  MODIFY COLUMN role VARCHAR(24) NOT NULL DEFAULT 'operator',
  ADD COLUMN duty_status VARCHAR(16) NOT NULL DEFAULT 'ready',
  ADD COLUMN duty_updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE problem
  MODIFY COLUMN state VARCHAR(24) NOT NULL DEFAULT 'pending_assign',
  ADD COLUMN ticket_no VARCHAR(24) NULL,
  ADD COLUMN customer_id VARCHAR(36) NULL,
  ADD COLUMN device_pending TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN urgency VARCHAR(16) NOT NULL DEFAULT 'normal',
  ADD COLUMN symptom_tags VARCHAR(200) NOT NULL DEFAULT '',
  ADD COLUMN accepted_by VARCHAR(20) NULL,
  ADD COLUMN warranty_confirmed VARCHAR(20) NULL,
  ADD COLUMN snapshot_json TEXT NULL,
  ADD COLUMN old_part_registered TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN old_part_no VARCHAR(64) NULL,
  ADD COLUMN reopened_from_id VARCHAR(36) NULL,
  ADD COLUMN settlement_flag VARCHAR(24) NULL,
  ADD UNIQUE KEY problem_ticket_no (ticket_no);

ALTER TABLE field_order
  MODIFY COLUMN state VARCHAR(24) NOT NULL DEFAULT 'dispatched',
  ADD COLUMN is_claim TINYINT(1) NULL,
  ADD COLUMN return_to_factory TINYINT(1) NULL,
  ADD COLUMN project_code VARCHAR(64) NULL,
  ADD COLUMN appointment_at DATETIME NULL,
  ADD COLUMN photo_names VARCHAR(500) NOT NULL DEFAULT '',
  ADD COLUMN accepted_at DATETIME NULL,
  ADD COLUMN arrived_at DATETIME NULL,
  ADD COLUMN completed_at DATETIME NULL,
  ADD COLUMN replaced_parts VARCHAR(500) NULL,
  ADD COLUMN old_part_no VARCHAR(64) NULL,
  ADD COLUMN old_part_photo VARCHAR(200) NULL,
  ADD COLUMN customer_accept_result VARCHAR(100) NULL,
  ADD COLUMN fee_owner VARCHAR(40) NULL,
  ADD COLUMN rollback_anomaly_note VARCHAR(500) NULL;

CREATE TABLE ticket_sequence (
  biz_date CHAR(8) PRIMARY KEY,
  seq INT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_profile (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  contact_name VARCHAR(50) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  project_code VARCHAR(64) NOT NULL DEFAULT '',
  project_name VARCHAR(120) NOT NULL DEFAULT '',
  address VARCHAR(255) NOT NULL DEFAULT '',
  customer_type VARCHAR(20) NOT NULL DEFAULT 'retail',
  sla_minutes INT UNSIGNED NOT NULL DEFAULT 120,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX customer_phone (phone),
  INDEX customer_project (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE device_profile (
  sn VARCHAR(40) PRIMARY KEY,
  customer_id VARCHAR(36) NULL,
  model VARCHAR(80) NOT NULL,
  product_type VARCHAR(40) NOT NULL DEFAULT '交流桩',
  manufactured_at VARCHAR(32) NOT NULL DEFAULT '',
  mes_batch VARCHAR(40) NOT NULL DEFAULT '',
  mes_test_result VARCHAR(80) NOT NULL DEFAULT '出厂检验合格',
  installed_at VARCHAR(32) NOT NULL DEFAULT '',
  warranty_status VARCHAR(24) NOT NULL DEFAULT '在保',
  warranty_until VARCHAR(32) NOT NULL DEFAULT '',
  extended_warranty VARCHAR(40) NOT NULL DEFAULT '无',
  sim_expires_at VARCHAR(32) NOT NULL DEFAULT '',
  firmware_version VARCHAR(32) NOT NULL DEFAULT '',
  online_status VARCHAR(24) NOT NULL DEFAULT '在线',
  current_status VARCHAR(40) NOT NULL DEFAULT '正常',
  realtime_telemetry VARCHAR(120) NOT NULL DEFAULT 'PE 在线',
  replaced_from_sn VARCHAR(40) NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX device_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE phone_sn_mapping (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(30) NOT NULL,
  sn VARCHAR(40) NOT NULL,
  confidence VARCHAR(20) NOT NULL DEFAULT 'high',
  verified_by VARCHAR(20) NOT NULL DEFAULT 'system',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY phone_sn_unique (phone, sn),
  INDEX phone_lookup (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sop_entry (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(80) NOT NULL,
  title VARCHAR(120) NOT NULL,
  version VARCHAR(20) NOT NULL DEFAULT 'V2.3',
  status VARCHAR(20) NOT NULL DEFAULT 'published',
  evidence_mode VARCHAR(120) NOT NULL DEFAULT '',
  customer_steps_json TEXT NOT NULL,
  onsite_steps_json TEXT NOT NULL,
  required_evidence_json TEXT NOT NULL,
  grade_hint VARCHAR(200) NOT NULL DEFAULT '',
  grade_thresholds_json TEXT NOT NULL,
  auto_approval_rules_json TEXT NOT NULL,
  updated_by VARCHAR(20) NOT NULL DEFAULT 'admin',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX sop_cat_status (category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE assessment_record (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  grade VARCHAR(8) NOT NULL,
  disposal_type VARCHAR(24) NOT NULL,
  rule_version VARCHAR(20) NOT NULL DEFAULT 'V2.3',
  auto_approved TINYINT(1) NOT NULL DEFAULT 0,
  approval_note VARCHAR(500) NULL,
  note VARCHAR(1000) NOT NULL DEFAULT '',
  actor VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  INDEX assessment_problem (problem_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE call_record (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NULL,
  call_id VARCHAR(64) NOT NULL,
  channel VARCHAR(24) NOT NULL DEFAULT '400',
  caller_phone VARCHAR(30) NOT NULL,
  duration_sec INT UNSIGNED NOT NULL DEFAULT 0,
  recording_url VARCHAR(255) NOT NULL DEFAULT '',
  recording_status VARCHAR(40) NOT NULL DEFAULT '录音已挂接（模拟）',
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX call_problem (problem_id, id),
  INDEX call_phone (caller_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE state_transition_log (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  from_state VARCHAR(24) NOT NULL,
  to_state VARCHAR(24) NOT NULL,
  actor VARCHAR(20) NOT NULL,
  action VARCHAR(32) NOT NULL,
  reason VARCHAR(500) NOT NULL DEFAULT '',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  INDEX transition_problem (problem_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预置本地模拟主档数据（MES/安装/400 未接入期间的完整过渡数据）
INSERT INTO customer_profile (id, name, contact_name, phone, project_code, project_name, address, customer_type, sla_minutes) VALUES
  ('CUST-001', '合肥滨湖示例充电站', '王经理', '13800001234', 'PRJ-HF-2026-01', '合肥滨湖产业园充电项目', '合肥市滨湖新区产业园3号门', 'station', 120),
  ('CUST-002', '李先生（个人车主）', '李先生', '19900000001', 'PRJ-HOME-2026-08', '家充零售项目', '合肥市滨湖新区示例小区3栋地下车库', 'retail', 30),
  ('CUST-003', '高新区公共快充站', '赵站长', '19900000002', 'PRJ-HF-2026-02', '高新区直流快充项目', '合肥市高新区示例公共充电站2号位', 'oem', 120);

INSERT INTO device_profile (sn, customer_id, model, product_type, manufactured_at, mes_batch, mes_test_result, installed_at, warranty_status, warranty_until, extended_warranty, sim_expires_at, firmware_version, online_status, current_status, realtime_telemetry) VALUES
  ('CD26080101', 'CUST-001', '7kW交流桩', '交流桩', '2025-03-10', 'MES-202503-A1', '出厂测试通过', '2025-04-10', '在保', '2028-04-10', '无', '2027-06-30', 'V2.3.1', '在线', '告警中', 'PE 在线'),
  ('SIM-AC-001', 'CUST-002', '7kW交流桩', '交流桩', '2025-06-18', 'MES-202506-B2', '出厂测试通过', '2025-06-28', '在保', '2027-06-18', '无', '2027-05-31', 'V2.4.18', '在线', '告警中', 'PE 断线告警'),
  ('SIM-DC-002', 'CUST-003', '120kW直流双枪桩', '直流桩', '2025-03-10', 'MES-202503-D1', '出厂测试通过', '2025-03-22', '在保', '2027-03-10', '延保1年', '2026-12-05', 'V3.1.04', '离线', '通信中断', '4G 心跳超时');

INSERT INTO phone_sn_mapping (phone, sn, confidence, verified_by) VALUES
  ('13800001234', 'CD26080101', 'high', 'system'),
  ('19900000001', 'SIM-AC-001', 'high', 'system'),
  ('19900000002', 'SIM-DC-002', 'high', 'system');

INSERT INTO sop_entry (category, title, version, status, evidence_mode, customer_steps_json, onsite_steps_json, required_evidence_json, grade_hint, grade_thresholds_json, auto_approval_rules_json, updated_by) VALUES
  ('无法充电', '无法充电 · SOP步骤', 'V2.3', 'published', '时序+可视混合类',
   '["确认车辆与枪头连接牢固并无异物","确认桩体通电、空开合闸与指示灯状态","引导客户断电1分钟后重启复测"]',
   '["现场测量主回路电压与PE接地","读取设备故障码与实时报文","执行换件或换桩判定并留存旧件"]',
   '["photo","video","platform_data"]',
   'A级需平台报文/日志佐证；B级需现场采集与对照；C级仅现象描述禁止换装',
   '{"A":"平台故障码命中且有日志","B":"现场实测电压/对照记录>=2项","C":"仅口述现象无日志"}',
   '[{"id":"r1","label":"条件1 故障码命中换装清单","enabled":true},{"id":"r2","label":"条件2 必填证据齐全且设备在保","enabled":true},{"id":"r3","label":"条件3 同SN历史换装次数未超阈值(<=2次)","enabled":true}]',
   'admin'),
  ('充电中断', '充电中断 · SOP步骤', 'V2.3', 'published', '时序类（故障时点+充电记录）',
   '["确认中断精确时间（到分钟）及车端提示","观察桩体告警灯并重新插拔枪头","条件允许时换车或同站另一把枪对照测试"]',
   '["调取平台充电会话停止原因与电流曲线","现场检测枪温、接触器与接地电阻","单变量替换测试并留痕"]',
   '["timestamp_log","platform_data","swap_test"]',
   'A级需平台停止原因+日志；B级需同站对照或替换测试；C级禁止换装',
   '{"A":"平台停止原因明确为桩端硬件故障","B":"同站对照确认非车端问题","C":"无法复现"}',
   '[{"id":"r1","label":"条件1 故障码命中换装清单","enabled":true},{"id":"r2","label":"条件2 必填证据齐全且设备在保","enabled":true},{"id":"r3","label":"条件3 同SN历史换装次数未超阈值(<=2次)","enabled":true}]',
   'admin'),
  ('设备离线', '设备离线 · SOP步骤', 'V2.3', 'published', '时序/通信类（照片无效）',
   '["确认桩体电源正常与网络指示灯状态","记录断网大致时间并观察周边4G信号","引导断电1分钟后上电观察重连"]',
   '["核查SIM卡槽接触与天线连接","核对SIM到期时间与程序版本","采集本地通信日志并与同站其他桩对照"]',
   '["timestamp_log","site_compare","platform_data"]',
   'A级需通信日志/SIM状态佐证；B级需现场信号与卡槽核查；C级禁止换装',
   '{"A":"确认4G模块硬件失效","B":"排除SIM欠费与弱信号","C":"偶发离线"}',
   '[{"id":"r1","label":"条件1 故障码命中换装清单","enabled":true},{"id":"r2","label":"条件2 必填证据齐全且设备在保","enabled":true},{"id":"r3","label":"条件3 同SN历史换装次数未超阈值(<=2次)","enabled":true}]',
   'admin'),
  ('计费异常', '计费异常 · SOP步骤', 'V2.3', 'published', '平台账单+电量对照',
   '["核对充电订单号、起止时间与账单金额","确认车端表显充电量与结算电量差异","留存异常订单截图或订单编号"]',
   '["核查电表计量模块脉冲与CT接线","对比平台原始报文电量与本地电表读数","确认计量误差是否超出国标范围"]',
   '["platform_data","timestamp_log"]',
   'A级需电表与平台报文差异佐证；B级需现场校准记录；C级禁止换装',
   '{"A":"计量误差>2%且电表故障","B":"需现场校准复测","C":"仅客户主观异议"}',
   '[{"id":"r1","label":"条件1 故障码命中换装清单","enabled":true},{"id":"r2","label":"条件2 必填证据齐全且设备在保","enabled":true},{"id":"r3","label":"条件3 同SN历史换装次数未超阈值(<=2次)","enabled":true}]',
   'admin'),
  ('安装问题', '安装问题 · SOP步骤', 'V2.3', 'published', '可视类（现场照片为主）',
   '["确认现场无漏电冒烟等安全风险","请客户拍摄安装位置、配电箱与铭牌照片","核对原始安装验收单与施工服务商"]',
   '["现场核验线径、空开规格与接地阻值","留存整改前后对比照片","关联原安装服务商责任归属"]',
   '["photo","video"]',
   '安装施工问题优先走安装整改，非设备本身故障禁止换装',
   '{"A":"明确施工不符合规范","B":"需现场核查责任归属","C":"无现场照片"}',
   '[{"id":"r1","label":"条件1 故障码命中换装清单","enabled":false},{"id":"r2","label":"条件2 必填证据齐全且设备在保","enabled":true},{"id":"r3","label":"条件3 同SN历史换装次数未超阈值(<=2次)","enabled":true}]',
   'admin'),
  ('其他', '其他异常 · SOP步骤', 'V2.3', 'published', '混合类',
   '["确认现象发生时间与具体表现","收集清晰照片或短视频记录现场情况","排查基础供电与环境因素"]',
   '["现场复现故障现象并采集日志","按实际症状归入对应故障大类","形成书面排查结论"]',
   '["photo","timestamp_log"]',
   '需先归类补充证据后再行判定，C级禁止换装',
   '{"A":"有明确硬件故障日志","B":"现场可稳定复现","C":"信息不足"}',
   '[{"id":"r1","label":"条件1 故障码命中换装清单","enabled":true},{"id":"r2","label":"条件2 必填证据齐全且设备在保","enabled":true},{"id":"r3","label":"条件3 同SN历史换装次数未超阈值(<=2次)","enabled":true}]',
   'admin');
