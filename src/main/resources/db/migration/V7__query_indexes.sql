-- 查桩历史问题单按设备编码与更新时间倒序检索；到期回访队列按状态、到期时间与主键稳定翻页。
CREATE INDEX problem_device_updated ON problem (device, updated_at);
CREATE INDEX followup_due_queue ON followup (state, due_at, id);
