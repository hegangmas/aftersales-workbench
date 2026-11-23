CREATE TABLE callback_result (
  activity_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  problem_id VARCHAR(36) NOT NULL,
  actor VARCHAR(20) NOT NULL,
  contacted_at DATETIME NOT NULL,
  outcome ENUM('continue','field','resolved') NOT NULL,
  note VARCHAR(1000) NOT NULL,
  evidence_source ENUM('customer','platform','field') NULL,
  evidence VARCHAR(1000) NULL,
  INDEX callback_problem (problem_id, activity_id),
  FOREIGN KEY (problem_id) REFERENCES problem(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
