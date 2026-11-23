ALTER TABLE problem
  ADD COLUMN display_number BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ADD UNIQUE KEY problem_display_number (display_number);
