CREATE TABLE IF NOT EXISTS problem (
  id VARCHAR(36) PRIMARY KEY,
  scenario VARCHAR(12) NOT NULL,
  title VARCHAR(150) NOT NULL,
  category VARCHAR(80) NOT NULL,
  channel VARCHAR(30) NOT NULL,
  original_state VARCHAR(40) NOT NULL DEFAULT '',
  description VARCHAR(1000) NOT NULL,
  kind ENUM('fault','consult','service') NOT NULL,
  state ENUM('open','triage','waiting','field','verify','resolved','answered','referred') NOT NULL DEFAULT 'open',
  owner VARCHAR(20) NOT NULL DEFAULT 'early',
  device VARCHAR(40) NULL,
  risk ENUM('none','suspected','raised','reviewed') NOT NULL DEFAULT 'none',
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX queue_state (state, owner, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS activity (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  actor VARCHAR(20) NOT NULL,
  action VARCHAR(32) NOT NULL,
  note VARCHAR(1500) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  INDEX timeline (problem_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS followup (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  owner VARCHAR(20) NOT NULL,
  next_step VARCHAR(500) NOT NULL,
  due_at DATETIME NOT NULL,
  state ENUM('open','done') NOT NULL DEFAULT 'open',
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  INDEX pending_tasks (problem_id,state,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS handoff (
  problem_id VARCHAR(36) PRIMARY KEY,
  target VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  accepted_at DATETIME(6) NULL,
  FOREIGN KEY (problem_id) REFERENCES problem(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS field_order (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  external_no VARCHAR(64) NOT NULL UNIQUE,
  state ENUM('linked','in_progress','complete','rejected','cancelled') NOT NULL,
  receipt VARCHAR(1000) NOT NULL DEFAULT '',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  INDEX field_problem (problem_id,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS verification (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  actor VARCHAR(20) NOT NULL,
  source ENUM('customer','platform','field') NOT NULL,
  evidence VARCHAR(1000) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  INDEX verified_problem (problem_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS command_log (
  request_id VARCHAR(64) PRIMARY KEY,
  digest CHAR(64) CHARACTER SET ascii NOT NULL,
  problem_id VARCHAR(36) NOT NULL,
  FOREIGN KEY (problem_id) REFERENCES problem(id),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
