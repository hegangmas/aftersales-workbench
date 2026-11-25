-- V1.1 中台业务内核一期：增量扩展工单渠道/时点/挂起回池/A-B-C判定/升级/SLA字段，并新增证据记录表。
-- 不改动历史表结构与已有数据，旧单新列保持可空或安全默认值。

ALTER TABLE problem
  ADD COLUMN fault_occurred_at DATETIME NULL,
  ADD COLUMN suspend_reason VARCHAR(40) NULL,
  ADD COLUMN grade ENUM('A','B','C') NULL,
  ADD COLUMN disposal_type ENUM('remote','part','replace','onsite') NULL,
  ADD COLUMN compromise_flag TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN escalation_level ENUM('none','l2','expert') NOT NULL DEFAULT 'none',
  ADD COLUMN escalation_reason VARCHAR(500) NULL,
  ADD COLUMN sla_due_at DATETIME NULL,
  ADD COLUMN auto_approval_note VARCHAR(500) NULL;

CREATE TABLE evidence_item (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  evidence_type VARCHAR(40) NOT NULL,
  source VARCHAR(30) NOT NULL DEFAULT 'agent',
  file_name VARCHAR(200) NOT NULL DEFAULT '',
  note VARCHAR(1000) NOT NULL,
  actor VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  INDEX evidence_problem (problem_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
