CREATE TABLE account (
  username VARCHAR(20) PRIMARY KEY,
  password_hash VARCHAR(100) NOT NULL,
  role ENUM('admin','operator') NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ownership_event (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  actor VARCHAR(20) NOT NULL,
  from_owner VARCHAR(20) NULL,
  to_owner VARCHAR(20) NOT NULL,
  action ENUM('claim','handoff') NOT NULL,
  reason VARCHAR(500) NOT NULL DEFAULT '',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  INDEX ownership_problem (problem_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX problem_owner_queue ON problem (owner, state, updated_at, id);
