-- 团队问题单总表按更新时间、主键倒序稳定翻页。不改写历史负责人。
CREATE INDEX problem_board_updated ON problem (updated_at, id);

-- 口令变更时间。已有账号填入迁移执行时刻，不回填、不输出历史口令。
ALTER TABLE account
  ADD COLUMN credentials_changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- 安全操作审计。只记录操作者、动作和目标账号，不保存口令或哈希。
CREATE TABLE security_audit (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  actor VARCHAR(20) NOT NULL,
  action VARCHAR(32) NOT NULL,
  target VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX security_audit_created (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
