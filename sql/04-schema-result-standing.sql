-- =============================================================================
-- 项目名称：田径运动会管理系统（TrackMeet）
-- 脚本文件：sql/04-schema-result-standing.sql
-- 脚本用途：创建【成绩层（6 张）+ 统计与输出层（7 张）】共 13 张业务表的表结构
--          成绩层：result、result_attempt、wind_reading、result_audit、
--                  advance_result、result_import_log
--          统计输出层：points、team_score、medal_standing、announcement、
--                  print_template、certificate、screen_config
-- 目标数据库：MySQL 8.0（InnoDB / utf8mb4 / utf8mb4_0900_ai_ci）
-- 字段依据：docs/12-05-字段清单-成绩与统计输出层.md（唯一字段依据）
--          docs/12-00-字段清单总览.md（类型规范、通用字段、命名与约束规范）
--          docs/03-数据库ER说明.md 第 4.3 节（成绩字段建模约定）
-- 执行顺序说明：
--   01-schema-system.sql            系统层（sys_user / sys_file / sys_config / sys_operation_log ...）
--   02-schema-meet-athlete.sql      届次基础层 11 张 + 人员报名层 5 张
--   03-schema-event-schedule.sql    项目与规则层 10 张 + 编排层 8 张 + 赛前层 5 张
--   04-schema-result-standing.sql   【本脚本】成绩层 6 张 + 统计与输出层 7 张
--   05-init-dict-data.sql           字典与内置初始化数据
--   依赖说明：本脚本仅建表与索引，不创建物理 FOREIGN KEY（沿用总览第 3 节规范：
--             外键由应用层保证，便于备份恢复与批量导入），亦不执行任何数据写入；
--             因无物理外键，建表顺序无强依赖。
-- 执行说明：1) 脚本可重复执行，每张表先 DROP TABLE IF EXISTS 再 CREATE TABLE；
--          2) 不含 INSERT、不含存储过程与触发器，执行后仅建表、不写入数据；
--          3) 通用字段 id / meet_id / create_by / create_time / update_by /
--             update_time / deleted / version 为所有业务表共有，业务字段见各表注释；
--          4) 布尔字段统一 TINYINT(1)（1 真 / 0 假），枚举码统一 VARCHAR(32)，
--             成绩原文 VARCHAR(16) + 成绩数值 DECIMAL(10,2) + 单位 VARCHAR(8)，
--             风速 DECIMAL(5,2)，名次 SMALLINT，时间点 DATETIME，扩展快照 JSON；
--          5) 保留字列名（`scope`、`action`、`version` 等）一律使用反引号包裹。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 一、成绩层（6 张）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. result 成绩表
--    一名运动员在一个日程条目（项目 × 轮次）上的最终成绩记录，
--    是名次、积分、晋级、纪录与证书的唯一数据源。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `result`;
CREATE TABLE `result` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `schedule_id` BIGINT UNSIGNED NULL COMMENT '日程条目ID（schedule.id）',
  `heat_id` BIGINT UNSIGNED NULL COMMENT '所在分组ID（heat.id）',
  `heat_member_id` BIGINT UNSIGNED NULL COMMENT '组内人员ID（heat_member.id）',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID（event.id）',
  `round_id` BIGINT UNSIGNED NULL COMMENT '轮次ID（event_round.id）',
  `registration_id` BIGINT UNSIGNED NULL COMMENT '报名记录ID（registration.id）',
  `athlete_id` BIGINT UNSIGNED NULL COMMENT '运动员ID（athlete.id，接力为队首人）',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '参赛单位ID（unit.id）',
  `group_id` BIGINT UNSIGNED NULL COMMENT '组别ID（meet_group.id）',
  `lane_no` SMALLINT NULL COMMENT '道次号，田赛与长跑可为空',
  `result_raw` VARCHAR(16) NULL COMMENT '成绩原文（显示用），如 11"23、1:02.45',
  `result_value` DECIMAL(10,2) NULL COMMENT '成绩数值（秒/米/分），用于排序比较；无效成绩为空',
  `result_unit` VARCHAR(8) NULL COMMENT '成绩单位：s 秒 / m 米 / cm 厘米 / pt 分（全能总分与单项分）',
  `result_score` DECIMAL(10,2) NULL COMMENT '全能单项得分，非全能项为空',
  `final_height` DECIMAL(10,2) NULL COMMENT '田赛高度项最终过杆高度（米）',
  `rank_no` SMALLINT NULL COMMENT '名次序号，并列时取相同值',
  `rank_display` VARCHAR(16) NULL COMMENT '名次显示文本，如 1、2（并列）',
  `status` VARCHAR(32) NULL COMMENT '成绩状态：VALID 有效 / DNS 未出发 / DNF 未完成 / DSQ 取消资格 / NM 无有效成绩 / PASS 免跳 / SCRATCH 弃权',
  `foul_type` VARCHAR(32) NULL COMMENT '犯规类型码，取自犯规类型字典',
  `foul_reason` VARCHAR(200) NULL COMMENT '判罚依据说明，DSQ 时必填',
  `wind_speed` DECIMAL(5,2) NULL COMMENT '风速（m/s），顺风为正、逆风为负',
  `is_wind_legal` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '风速是否合法：1 合法 / 0 超风速',
  `is_record_candidate` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否破纪录候选：1 是 / 0 否',
  `is_record_confirmed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '纪录是否已认定：1 已认定 / 0 未认定',
  `record_id` BIGINT UNSIGNED NULL COMMENT '对标纪录ID（record.id）',
  `record_type` VARCHAR(32) NULL COMMENT '认定纪录层级：WORLD / ASIA / NATION / PROVINCE / CITY / MEET 赛会 / GROUP 组别',
  `is_pb` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否个人最好成绩：1 是 / 0 否',
  `is_sb` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否个人赛季最好成绩：1 是 / 0 否',
  `is_manual` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否手工录入：1 手工录入 / 0 设备导入',
  `allround_group_id` BIGINT UNSIGNED NULL COMMENT '全能归并标识，同一全能用同值',
  `audit_status` VARCHAR(32) NULL COMMENT '审核状态：DRAFT 草稿 / SUBMITTED 待审 / APPROVED 已审 / REJECTED 驳回',
  `audit_by` BIGINT UNSIGNED NULL COMMENT '审核人ID（sys_user.id）',
  `audit_time` DATETIME NULL COMMENT '审核时间',
  `reject_reason` VARCHAR(200) NULL COMMENT '驳回原因，REJECTED 时必填',
  `is_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '成绩是否锁定：1 锁定（须撤销审核才能改）/ 0 未锁定',
  `client_token` VARCHAR(64) NULL COMMENT '幂等令牌，重复提交返回首次结果',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_registration` (`schedule_id`, `registration_id`),
  UNIQUE KEY `uk_allround_event` (`allround_group_id`, `event_id`),
  KEY `idx_meet_event_round_audit` (`meet_id`, `event_id`, `round_id`, `audit_status`),
  KEY `idx_athlete_event` (`athlete_id`, `event_id`),
  KEY `idx_meet_unit_group` (`meet_id`, `unit_id`, `group_id`),
  KEY `idx_client_token` (`client_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成绩表';

-- -----------------------------------------------------------------------------
-- 2. result_attempt 田赛试跳试掷表
--    田赛项目每一次试跳/试掷的明细，用于取名次前的平局比较与成绩复核。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `result_attempt`;
CREATE TABLE `result_attempt` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `result_id` BIGINT UNSIGNED NULL COMMENT '所属成绩ID（result.id）',
  `registration_id` BIGINT UNSIGNED NULL COMMENT '报名记录ID（registration.id）',
  `attempt_no` SMALLINT NULL COMMENT '试跳/试掷总序号，从 1 递增',
  `attempt_round` SMALLINT NULL COMMENT '轮次序号（第几轮次试跳）',
  `attempt_raw` VARCHAR(16) NULL COMMENT '单次成绩原文，如 5.68',
  `attempt_value` DECIMAL(10,2) NULL COMMENT '单次成绩数值（米/厘米）',
  `attempt_unit` VARCHAR(8) NULL COMMENT '单次成绩单位：m 米 / cm 厘米',
  `height_value` DECIMAL(10,2) NULL COMMENT '本次横杆高度（高度项，米）',
  `is_success` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '本次是否成功：1 成功 / 0 失败',
  `is_pass` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否免跳：1 免跳（PASS，不消耗试跳次数）/ 0 否',
  `is_foul` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否犯规：1 犯规 / 0 未犯规',
  `foul_type` VARCHAR(32) NULL COMMENT '犯规类型码，取自犯规类型字典',
  `is_best` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为该运动员最好一次：1 是 / 0 否',
  `judge_id` BIGINT UNSIGNED NULL COMMENT '记录裁判ID（referee.id）',
  `device_no` VARCHAR(32) NULL COMMENT '测距/计时设备编号',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_result_attempt` (`result_id`, `attempt_no`),
  KEY `idx_registration_round` (`registration_id`, `attempt_round`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='田赛试跳试掷表';

-- -----------------------------------------------------------------------------
-- 3. wind_reading 风速读数表
--    风速仪读数留档，作为超风速标记与纪录认定的原始依据，一成绩一条。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `wind_reading`;
CREATE TABLE `wind_reading` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `result_id` BIGINT UNSIGNED NULL COMMENT '关联成绩ID（result.id）',
  `schedule_id` BIGINT UNSIGNED NULL COMMENT '日程条目ID（schedule.id）',
  `heat_id` BIGINT UNSIGNED NULL COMMENT '分组ID（heat.id）',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID（event.id）',
  `wind_speed` DECIMAL(5,2) NULL COMMENT '风速读数（m/s），顺风为正、逆风为负',
  `wind_direction` VARCHAR(16) NULL COMMENT '风向：HEAD 逆风 / TAIL 顺风 / CROSS 侧风',
  `is_wind_legal` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否在合法上限内：1 合法 / 0 超风速',
  `legal_limit` DECIMAL(5,2) NULL COMMENT '本次判定采用的合法上限值（m/s）',
  `wind_device_no` VARCHAR(32) NULL COMMENT '风速仪设备编号',
  `read_time` DATETIME NULL COMMENT '读数时间',
  `recorder_id` BIGINT UNSIGNED NULL COMMENT '记录人ID（sys_user.id）',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_result` (`result_id`),
  KEY `idx_schedule_heat` (`schedule_id`, `heat_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风速读数表';

-- -----------------------------------------------------------------------------
-- 4. result_audit 成绩留痕表
--    成绩提交、审核、驳回、修改、撤销的完整留痕链，保证「谁/何时/改了什么」可追溯。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `result_audit`;
CREATE TABLE `result_audit` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `result_id` BIGINT UNSIGNED NULL COMMENT '关联成绩ID（result.id）',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID（event.id）',
  `round_id` BIGINT UNSIGNED NULL COMMENT '轮次ID（event_round.id）',
  `registration_id` BIGINT UNSIGNED NULL COMMENT '报名记录ID（registration.id）',
  `action` VARCHAR(32) NULL COMMENT '留痕动作：SUBMIT 提交 / APPROVE 通过 / REJECT 驳回 / MODIFY 修改 / REVOKE 撤销审核',
  `before_json` JSON NULL COMMENT '变更前快照（含各次试跳）',
  `after_json` JSON NULL COMMENT '变更后快照（含各次试跳）',
  `reason` VARCHAR(500) NULL COMMENT '修改或驳回原因',
  `operator_id` BIGINT UNSIGNED NULL COMMENT '操作人ID（sys_user.id）',
  `operator_name` VARCHAR(50) NULL COMMENT '操作人姓名快照',
  `operate_time` DATETIME NULL COMMENT '操作时间',
  `ip` VARCHAR(64) NULL COMMENT '操作来源 IP',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_result_operate_time` (`result_id`, `operate_time`),
  KEY `idx_meet_action_operate_time` (`meet_id`, `action`, `operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成绩留痕表';

-- -----------------------------------------------------------------------------
-- 5. advance_result 晋级名单表
--    记录「从哪一轮、以什么方式晋级到哪一轮」的名单，是自动晋级与人工递补的统一结果。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `advance_result`;
CREATE TABLE `advance_result` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID（event.id）',
  `from_round_id` BIGINT UNSIGNED NULL COMMENT '来源轮次ID（event_round.id）',
  `to_round_id` BIGINT UNSIGNED NULL COMMENT '目标轮次ID（event_round.id）',
  `registration_id` BIGINT UNSIGNED NULL COMMENT '报名记录ID（registration.id）',
  `athlete_id` BIGINT UNSIGNED NULL COMMENT '运动员ID（athlete.id）',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '参赛单位ID（unit.id）',
  `group_id` BIGINT UNSIGNED NULL COMMENT '组别ID（meet_group.id）',
  `source_heat_no` SMALLINT NULL COMMENT '来源组号',
  `source_rank` SMALLINT NULL COMMENT '来源组内名次',
  `source_result_value` DECIMAL(10,2) NULL COMMENT '来源轮次成绩数值（用于递补排序）',
  `source_result_raw` VARCHAR(16) NULL COMMENT '来源轮次成绩原文',
  `advance_type` VARCHAR(32) NULL COMMENT '晋级方式：GROUP_TOP 每组前 N / BEST_LOSER 最好成绩递补 / MANUAL 人工指定',
  `advance_order` SMALLINT NULL COMMENT '晋级后在新轮次中的顺序号',
  `is_confirmed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '名单是否已确认生效：1 已确认 / 0 未确认',
  `confirm_by` BIGINT UNSIGNED NULL COMMENT '确认人ID（sys_user.id）',
  `confirm_time` DATETIME NULL COMMENT '确认时间',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_to_round_registration` (`event_id`, `to_round_id`, `registration_id`),
  KEY `idx_from_round_advance_type` (`from_round_id`, `advance_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='晋级名单表';

-- -----------------------------------------------------------------------------
-- 6. result_import_log 设备成绩导入记录表
--    电子计时/测距设备导出文件的导入批次记录，便于结果核对与失败回查。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `result_import_log`;
CREATE TABLE `result_import_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `file_id` BIGINT UNSIGNED NULL COMMENT '导入文件ID（sys_file.id）',
  `schedule_id` BIGINT UNSIGNED NULL COMMENT '目标日程条目ID（schedule.id）',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID（event.id）',
  `round_id` BIGINT UNSIGNED NULL COMMENT '轮次ID（event_round.id）',
  `import_mode` VARCHAR(32) NULL COMMENT '导入模式：APPEND 追加 / OVERWRITE 覆盖',
  `device_type` VARCHAR(32) NULL COMMENT '设备类型：TIMER 电子计时 / DISTANCE 激光测距',
  `total_count` INT NULL COMMENT '文件总行数',
  `success_count` INT NULL COMMENT '成功写入条数',
  `fail_count` INT NULL COMMENT '失败条数',
  `error_file_id` BIGINT UNSIGNED NULL COMMENT '错误报告文件ID（sys_file.id）',
  `status` VARCHAR(32) NULL COMMENT '导入状态：PENDING 待处理 / RUNNING 处理中 / SUCCESS 成功 / FAILED 失败 / PARTIAL 部分成功',
  `message` VARCHAR(500) NULL COMMENT '结果说明或错误摘要',
  `operator_id` BIGINT UNSIGNED NULL COMMENT '导入操作人ID（sys_user.id）',
  `finish_time` DATETIME NULL COMMENT '导入完成时间',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_meet_status` (`meet_id`, `status`),
  KEY `idx_schedule_create_time` (`schedule_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备成绩导入记录表';

-- =============================================================================
-- 二、统计与输出层（7 张）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 7. points 积分明细表
--    一条「成绩 → 得分」的计算结果，记录名次分、破纪录加分与接力倍数的合成过程。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `points`;
CREATE TABLE `points` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID（event.id）',
  `round_id` BIGINT UNSIGNED NULL COMMENT '轮次ID（event_round.id）',
  `result_id` BIGINT UNSIGNED NULL COMMENT '来源成绩ID（result.id）',
  `registration_id` BIGINT UNSIGNED NULL COMMENT '报名记录ID（registration.id）',
  `athlete_id` BIGINT UNSIGNED NULL COMMENT '运动员ID（athlete.id）',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '计分单位ID（unit.id）',
  `group_id` BIGINT UNSIGNED NULL COMMENT '组别ID（meet_group.id）',
  `gender` VARCHAR(16) NULL COMMENT '性别：MALE 男子 / FEMALE 女子 / MIXED 混合',
  `rank_no` SMALLINT NULL COMMENT '计分名次',
  `rank_point` DECIMAL(10,2) NULL COMMENT '名次分，查 score_rule_item 得',
  `bonus_point` DECIMAL(10,2) NULL COMMENT '破纪录加分合计',
  `relay_factor` DECIMAL(10,2) NULL COMMENT '接力倍数，非接力为 1',
  `total_point` DECIMAL(10,2) NULL COMMENT '合计分 = (名次分 + 加分) × 倍数',
  `score_rule_id` BIGINT UNSIGNED NULL COMMENT '名次分方案ID（score_rule.id）',
  `score_rule_code` VARCHAR(64) NULL COMMENT '方案编码快照',
  `bonus_rule_ids` VARCHAR(200) NULL COMMENT '命中的加分规则 ID 列表，逗号分隔',
  `calc_time` DATETIME NULL COMMENT '计算时间',
  `is_manual` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否人工调整：1 手工赋值 / 0 系统计算',
  `adjust_reason` VARCHAR(200) NULL COMMENT '人工调整原因',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_round_registration` (`event_id`, `round_id`, `registration_id`),
  KEY `idx_meet_unit_group` (`meet_id`, `unit_id`, `group_id`),
  KEY `idx_meet_event` (`meet_id`, `event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分明细表';

-- -----------------------------------------------------------------------------
-- 8. team_score 团体总分快照表
--    按单位 × 组别 × 性别口径聚合的团体总分快照，支持男团/女团/总团三张榜。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `team_score`;
CREATE TABLE `team_score` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '参赛单位ID（unit.id）',
  `group_id` BIGINT UNSIGNED NULL COMMENT '组别ID（meet_group.id）',
  `score_type` VARCHAR(32) NULL COMMENT '团体口径：MALE 男子团体 / FEMALE 女子团体 / TOTAL 总团体',
  `rank_point_sum` DECIMAL(10,2) NULL COMMENT '名次分合计',
  `bonus_point_sum` DECIMAL(10,2) NULL COMMENT '破纪录加分合计',
  `relay_point_sum` DECIMAL(10,2) NULL COMMENT '接力项目得分合计',
  `total_point` DECIMAL(10,2) NULL COMMENT '团体总分',
  `rank_no` SMALLINT NULL COMMENT '团体名次',
  `rank_display` VARCHAR(16) NULL COMMENT '名次显示文本（含并列）',
  `event_count` INT NULL COMMENT '得分项目数',
  `athlete_count` INT NULL COMMENT '得分人数',
  `calc_time` DATETIME NULL COMMENT '统计时间',
  `calc_batch` VARCHAR(64) NULL COMMENT '计算批次号，便于整批重算覆盖',
  `is_published` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已对外发布：1 已发布 / 0 未发布',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_unit_group_score_type` (`meet_id`, `unit_id`, `group_id`, `score_type`),
  KEY `idx_meet_group_rank` (`meet_id`, `group_id`, `rank_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团体总分快照表';

-- -----------------------------------------------------------------------------
-- 9. medal_standing 奖牌榜快照表
--    按单位 × 组别聚合的金银铜牌快照，排序优先级由届次规则配置。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `medal_standing`;
CREATE TABLE `medal_standing` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '参赛单位ID（unit.id）',
  `group_id` BIGINT UNSIGNED NULL COMMENT '组别ID（meet_group.id）',
  `gold` INT NULL COMMENT '金牌数',
  `silver` INT NULL COMMENT '银牌数',
  `bronze` INT NULL COMMENT '铜牌数',
  `total_medal` INT NULL COMMENT '奖牌总数',
  `sort_score` DECIMAL(10,2) NULL COMMENT '排序分，按配置权重折算',
  `medal_sort` VARCHAR(64) NULL COMMENT '排序规则快照，如 GOLD_SILVER_BRONZE',
  `rank_no` SMALLINT NULL COMMENT '奖牌榜名次',
  `rank_display` VARCHAR(16) NULL COMMENT '名次显示文本（含并列）',
  `calc_time` DATETIME NULL COMMENT '统计时间',
  `calc_batch` VARCHAR(64) NULL COMMENT '计算批次号',
  `is_published` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已对外发布：1 已发布 / 0 未发布',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_unit_group` (`meet_id`, `unit_id`, `group_id`),
  KEY `idx_meet_group_rank` (`meet_id`, `group_id`, `rank_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖牌榜快照表';

-- -----------------------------------------------------------------------------
-- 10. announcement 公告表
--     届次公告与通知，支持置顶、可见范围与大屏展示开关。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `title` VARCHAR(100) NULL COMMENT '公告标题',
  `content_html` TEXT NULL COMMENT '公告正文富文本',
  `notice_type` VARCHAR(32) NULL COMMENT '公告类型：NOTICE 通知 / RESULT 成绩 / SCHEDULE 日程 / URGENT 紧急',
  `scope` VARCHAR(32) NULL COMMENT '可见范围：PUBLIC 公开 / LEADER 领队可见 / REFEREE 裁判可见',
  `is_top` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：1 置顶 / 0 不置顶',
  `publish_time` DATETIME NULL COMMENT '生效发布时间',
  `expire_time` DATETIME NULL COMMENT '失效时间，空为长期有效',
  `status` VARCHAR(32) NULL COMMENT '公告状态：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下线',
  `publisher_id` BIGINT UNSIGNED NULL COMMENT '发布人ID（sys_user.id）',
  `publisher_name` VARCHAR(50) NULL COMMENT '发布人姓名快照',
  `screen_show` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否推送大屏轮播：1 推送 / 0 不推送',
  `screen_duration` SMALLINT NULL COMMENT '大屏停留秒数',
  `sort_no` SMALLINT NULL COMMENT '排序号',
  `read_count` INT NULL COMMENT '阅读次数',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_meet_status_publish_time` (`meet_id`, `status`, `publish_time`),
  KEY `idx_meet_scope_is_top` (`meet_id`, `scope`, `is_top`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告表';

-- -----------------------------------------------------------------------------
-- 11. print_template 打印模板表
--     各类报表的页眉页脚、Logo、签名栏与纸张设置，届次级可覆盖全局默认模板。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `print_template`;
CREATE TABLE `print_template` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `template_code` VARCHAR(64) NULL COMMENT '模板编码，届次内唯一',
  `template_name` VARCHAR(100) NULL COMMENT '模板名称',
  `report_type` VARCHAR(32) NULL COMMENT '报表类型：PROGRAM 秩序册 / START_LIST 检录分道表 / RESULT 成绩公告 / POINTS 积分表 / MEDAL 奖牌榜 / CERT 证书',
  `header_html` TEXT NULL COMMENT '页眉 HTML',
  `footer_html` TEXT NULL COMMENT '页脚 HTML',
  `logo_file_id` BIGINT UNSIGNED NULL COMMENT 'Logo 文件ID（sys_file.id）',
  `signature_html` TEXT NULL COMMENT '签名栏 HTML',
  `page_size` VARCHAR(16) NULL COMMENT '纸张规格：A4 / A3 / LETTER',
  `orientation` VARCHAR(16) NULL COMMENT '纸张方向：PORTRAIT 纵向 / LANDSCAPE 横向',
  `font_size` SMALLINT NULL COMMENT '基准字号（磅）',
  `show_page_no` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否显示页码：1 显示 / 0 不显示',
  `show_print_time` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否显示打印时间：1 显示 / 0 不显示',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为该报表类型默认模板：1 是 / 0 否',
  `extra_json` JSON NULL COMMENT '签章位置、水印、列宽等扩展',
  `status` VARCHAR(32) NULL COMMENT '模板状态：ENABLED 启用 / DISABLED 停用',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_template_code` (`meet_id`, `template_code`),
  KEY `idx_meet_report_type_is_default` (`meet_id`, `report_type`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印模板表';

-- -----------------------------------------------------------------------------
-- 12. certificate 证书表
--     生成的获奖/成绩/奖牌/参赛证书，含编号、收件人、成绩描述、文件与二维码。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `certificate`;
CREATE TABLE `certificate` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `cert_no` VARCHAR(64) NULL COMMENT '证书编号，全局唯一',
  `cert_type` VARCHAR(32) NULL COMMENT '证书类型：RANK 获奖 / RESULT 成绩 / MEDAL 奖牌 / PARTICIPATION 参赛',
  `template_id` BIGINT UNSIGNED NULL COMMENT '打印模板ID（print_template.id）',
  `athlete_id` BIGINT UNSIGNED NULL COMMENT '收件运动员ID（athlete.id）',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '所属单位ID（unit.id）',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID（event.id）',
  `round_id` BIGINT UNSIGNED NULL COMMENT '轮次ID（event_round.id）',
  `result_id` BIGINT UNSIGNED NULL COMMENT '来源成绩ID（result.id）',
  `rank_no` SMALLINT NULL COMMENT '名次',
  `rank_display` VARCHAR(16) NULL COMMENT '名次显示文本',
  `result_text` VARCHAR(100) NULL COMMENT '成绩展示文本，如 11"23',
  `honor_text` VARCHAR(200) NULL COMMENT '荣誉描述，如「男子甲组 100 米第一名」',
  `issue_date` DATE NULL COMMENT '签发日期',
  `issuer` VARCHAR(100) NULL COMMENT '签发单位名称',
  `file_id` BIGINT UNSIGNED NULL COMMENT '证书文件ID（sys_file.id）',
  `qr_content` VARCHAR(255) NULL COMMENT '二维码内容（验真链接或编号）',
  `status` VARCHAR(32) NULL COMMENT '证书状态：GENERATED 已生成 / ISSUED 已签发 / REVOKED 已作废',
  `print_count` INT NULL COMMENT '打印次数',
  `last_print_time` DATETIME NULL COMMENT '最近打印时间',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cert_no` (`cert_no`),
  KEY `idx_meet_cert_type` (`meet_id`, `cert_type`),
  KEY `idx_athlete_event` (`athlete_id`, `event_id`),
  KEY `idx_result` (`result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='证书表';

-- -----------------------------------------------------------------------------
-- 13. screen_config 大屏配置表
--     大屏展示方案的模块组合、刷新频率与主题，一个届次可配置多个大屏。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `screen_config`;
CREATE TABLE `screen_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `screen_name` VARCHAR(100) NULL COMMENT '大屏方案名称',
  `screen_code` VARCHAR(64) NULL COMMENT '方案编码，届次内唯一',
  `modules_json` JSON NULL COMMENT '展示模块顺序与开关',
  `refresh_interval` INT NULL COMMENT '自动刷新间隔（秒）',
  `theme_json` JSON NULL COMMENT '主题色、字体、背景与 Logo 墙',
  `resolution` VARCHAR(32) NULL COMMENT '分辨率：1920x1080 / 3840x2160',
  `rotate_seconds` SMALLINT NULL COMMENT '模块轮播间隔（秒）',
  `play_mode` VARCHAR(32) NULL COMMENT '播放模式：CYCLE 轮播 / FIXED 固定',
  `status` VARCHAR(32) NULL COMMENT '大屏状态：ENABLED 启用 / DISABLED 停用',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认大屏方案：1 是 / 0 否',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人ID（sys_user.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人ID（sys_user.id）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_meet_status` (`meet_id`, `status`),
  UNIQUE KEY `uk_meet_screen_code` (`meet_id`, `screen_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大屏配置表';

-- =============================================================================
-- 脚本结束：成绩层 6 张 + 统计输出层 7 张，共 13 张表
-- =============================================================================
