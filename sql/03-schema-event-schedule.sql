-- =====================================================================
-- 项目名称：田径运动会管理系统（Sports Meet Management System）
-- 脚本文件：sql/03-schema-event-schedule.sql
-- 脚本用途：创建「项目与规则层 10 张 + 编排层 8 张 + 赛前层 5 张 = 23 张」业务表
-- 字段依据：docs/12-00-字段清单总览.md（通用字段 / 类型规范 / 命名与约束规范）
--           docs/12-03-字段清单-人员报名与项目规则层.md（项目与规则层 10 张）
--           docs/12-04-字段清单-编排层.md（编排层 8 张）
--           docs/12-02-字段清单-届次基础与赛前层.md（赛前层 5 张）
-- ---------------------------------------------------------------------
-- 执行顺序说明：
--   01-schema-system.sql          系统层（sys_user / sys_file / sys_config / sys_operation_log ...）
--   02-schema-meet-athlete.sql    届次基础层 + 人员报名层（meet / meet_group / unit / venue /
--                                 referee / record / athlete / registration ...）
--   03-schema-event-schedule.sql  【本脚本】项目与规则层 + 编排层 + 赛前层（23 张）
--   04-schema-result-standing.sql 成绩层与统计输出层
--   05-init-data.sql              初始化数据（角色/权限/菜单/字典/系统参数 +
--                                 referee_duty / event_schedule_rule / allround_score_table 默认数据）
--   06-demo-data.sql              演示数据（1 届示例运动会 + 组别/单位/运动员 + 名次分与加分规则）
--   依赖说明：本脚本仅建表与索引，不创建物理 FOREIGN KEY（沿用总览第 3 节规范：
--             外键由应用层保证，便于备份恢复与批量导入），亦不执行任何数据写入。
--   环境要求：MySQL 8.0，字符集 utf8mb4 / utf8mb4_0900_ai_ci，存储引擎 InnoDB。
-- ---------------------------------------------------------------------
-- 全局表说明（3 张，无 meet_id 列，跨届次共用）：
--   allround_score_table 全能计分表（成绩区间 → 得分换算，内置表全届次复用）
--   event_schedule_rule  项目时长与顺序默认规则表（项目类型 × 子类型级默认值）
--   referee_duty         裁判岗位字典表（岗位编码跨届次共用的基础字典）
--   其余 20 张表均含 meet_id 实现届次隔离。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =====================================================================
-- 一、项目与规则层（10 张）
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. event 项目表 —— 项目主数据：类型、组别、性别、赛制、接力人数、限报与计分属性
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `event`;
CREATE TABLE `event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `event_name` VARCHAR(100) NOT NULL COMMENT '项目名称',
  `event_code` VARCHAR(64) NOT NULL COMMENT '项目编码，届次内唯一',
  `event_type` VARCHAR(32) NOT NULL COMMENT '项目类型：TRACK径赛/FIELD田赛/ALLROUND全能/RELAY接力',
  `sub_type` VARCHAR(32) NULL COMMENT '项目子类型：SPRINT短跑/MIDDLE中跑/LONG长跑/HURDLE跨栏/STEEPLE障碍/WALK竞走/JUMP跳跃/THROW投掷/RELAY接力',
  `group_id` BIGINT UNSIGNED NULL COMMENT '所属组别ID，关联meet_group.id',
  `gender` VARCHAR(32) NULL COMMENT '适用性别：MALE男/FEMALE女/MIXED混合',
  `round_count` SMALLINT NULL COMMENT '轮次数量',
  `is_relay` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否接力项目：0否/1是',
  `relay_min` SMALLINT NULL COMMENT '接力最少人数（默认4）',
  `relay_max` SMALLINT NULL COMMENT '接力最多人数（默认4）',
  `max_per_unit` INT NULL COMMENT '每单位每项限报人数（默认3）',
  `is_relay_double` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '接力得分是否双倍：0否/1是（关联bonus_rule.factor）',
  `wind_applicable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否记录并判定风速：0否/1是',
  `is_team_score` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否计入团体总分：0否/1是',
  `duration_min` INT NULL COMMENT '项目预计时长（分钟）',
  `score_rule_id` BIGINT UNSIGNED NULL COMMENT '采用名次分方案ID，关联score_rule.id',
  `unit_of_measure` VARCHAR(32) NULL COMMENT '计量单位：s秒/m米/cm厘米/pt分',
  `result_precision` SMALLINT NULL COMMENT '成绩小数位，覆盖sys_config精度配置',
  `sort_no` INT NULL COMMENT '显示排序号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_meet_code` (`meet_id`, `event_code`),
  KEY `idx_event_group_type` (`meet_id`, `group_id`, `event_type`),
  KEY `idx_event_relay` (`meet_id`, `is_relay`),
  KEY `idx_event_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目表';


-- ---------------------------------------------------------------------
-- 2. event_round 轮次表 —— 项目内的比赛轮次定义与编排计划值
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `event_round`;
CREATE TABLE `event_round` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `event_id` BIGINT UNSIGNED NOT NULL COMMENT '所属项目ID，关联event.id',
  `round_code` VARCHAR(32) NOT NULL COMMENT '轮次码：QUALIFY及格赛/PRE预赛/R1次赛/R2复赛/SEMI半决赛/FINAL决赛',
  `round_name` VARCHAR(100) NULL COMMENT '轮次名称（如「预赛」「决赛」）',
  `round_no` SMALLINT NOT NULL COMMENT '轮次顺序号，从1起',
  `is_final` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否决赛轮：0否/1是',
  `plan_heat_count` SMALLINT NULL COMMENT '计划分组数',
  `plan_heat_size` SMALLINT NULL COMMENT '每组计划人数',
  `plan_advance_count` INT NULL COMMENT '计划晋级总人数',
  `plan_advance_group_top` SMALLINT NULL COMMENT '计划每组晋级人数',
  `plan_best_loser_count` SMALLINT NULL COMMENT '计划递补人数（剩余最好成绩）',
  `entry_count` INT NULL COMMENT '本轮实际参与人数（快照）',
  `round_date` DATE NULL COMMENT '计划比赛日期',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED已计划/READY就绪/RUNNING进行中/FINISHED已完成',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_round_event_code` (`event_id`, `round_code`),
  KEY `idx_round_event_no` (`event_id`, `round_no`),
  KEY `idx_round_meet_date` (`meet_id`, `round_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='轮次表';


-- ---------------------------------------------------------------------
-- 3. event_round_rule 轮次级规则表 —— 三层继承最内层：本轮录取名次与晋级/递补规则
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `event_round_rule`;
CREATE TABLE `event_round_rule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `round_id` BIGINT UNSIGNED NOT NULL COMMENT '轮次ID，关联event_round.id，一对一',
  `event_id` BIGINT UNSIGNED NOT NULL COMMENT '所属项目ID，关联event.id，便于按项目查询',
  `admit_rank` SMALLINT NULL COMMENT '本轮录取名次（取前N名）',
  `group_top_n` SMALLINT NULL COMMENT '每组直接晋级前N名（默认2）',
  `best_loser_count` SMALLINT NULL COMMENT '剩余最好成绩递补人数（默认8）',
  `target_count` INT NULL COMMENT '目标晋级总人数',
  `advance_json` JSON NULL COMMENT '晋级规则扩展，如{"groupTop":2,"bestLoser":8,"target":16,"excludeIllegalWind":true}',
  `tie_break_json` JSON NULL COMMENT '本轮平局判定规则（次优成绩→最远成功轮次→并列）',
  `source_round_id` BIGINT UNSIGNED NULL COMMENT '晋级来源轮次ID（上一轮），关联event_round.id',
  `is_inherit` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '未配置项是否继承项目级规则：0否/1是',
  `exclude_illegal_wind` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '超风速成绩是否排除出递补：0否/1是',
  `manual_advance_count` SMALLINT NULL COMMENT '人工指定晋级人数（覆盖自动计算）',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（保护规则并发修改）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roundrule_round` (`round_id`),
  KEY `idx_roundrule_event` (`event_id`),
  KEY `idx_roundrule_source_round` (`source_round_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='轮次级规则表';


-- ---------------------------------------------------------------------
-- 4. event_rule_config 项目级规则表 —— 项目级规则中枢：录取、分组、道次、风速、试跳、升杆、平局、时长
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `event_rule_config`;
CREATE TABLE `event_rule_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `event_id` BIGINT UNSIGNED NOT NULL COMMENT '项目ID，关联event.id，一对一',
  `admit_rank` SMALLINT NULL COMMENT '录取名次（取前N名，默认8）',
  `draw_mode` VARCHAR(32) NULL COMMENT '分组方式：SERPENT蛇形/RANDOM随机/SEED成绩段/UNIT按单位',
  `draw_avoid_same_unit` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '分组时同单位是否避开：0否/1是',
  `draw_group_size` SMALLINT NULL COMMENT '分组计划每组人数',
  `lane_count` SMALLINT NULL COMMENT '本项目使用道数（8道/6道）',
  `lane_order_json` JSON NULL COMMENT '道次顺序，8道[4,5,3,6,2,7,1,8]；6道[3,4,2,5,1,6]',
  `lane_assign_mode` VARCHAR(32) NULL COMMENT '道次分配方式：SEED按种子/DRAW抽签/SERPENT蛇形/ORDER按顺序',
  `conflict_interval_min` SMALLINT NULL COMMENT '兼项最小间隔（分钟，默认30）',
  `wind_json` JSON NULL COMMENT '风速规则，如{"applicable":true,"maxLegal":2.0}',
  `attempt_count` SMALLINT NULL COMMENT '田赛预赛试跳/试掷次数（默认3）',
  `final_attempt_count` SMALLINT NULL COMMENT '田赛决赛试跳/试掷次数（默认3）',
  `qualify_standard` VARCHAR(100) NULL COMMENT '及格赛标准原文',
  `qualify_standard_value` DECIMAL(10,2) NULL COMMENT '及格标准可比较数值',
  `qualify_standard_unit` VARCHAR(32) NULL COMMENT '及格标准单位：s秒/m米/cm厘米',
  `height_plan_json` JSON NULL COMMENT '升杆方案（起跳高度、每轮升幅、免跳）',
  `tie_break_json` JSON NULL COMMENT '平局规则（次优成绩→最远成功轮次→并列）',
  `duration_min` INT NULL COMMENT '项目预计时长（分钟）',
  `is_inherit_meet` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '未配置项是否继承届次默认值：0否/1是',
  `extra_json` JSON NULL COMMENT '其他扩展规则（抽签种子数、重赛规则等）',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evrule_event` (`event_id`),
  KEY `idx_evrule_meet_draw` (`meet_id`, `draw_mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目级规则表';


-- ---------------------------------------------------------------------
-- 5. score_rule 名次分方案表 —— 并列计分方式、适用范围与默认标记
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `score_rule`;
CREATE TABLE `score_rule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `rule_name` VARCHAR(100) NOT NULL COMMENT '方案名称（如「单项前八名 9-7-6-5」）',
  `rule_code` VARCHAR(64) NOT NULL COMMENT '方案编码，届次内唯一',
  `tie_policy` VARCHAR(32) NULL COMMENT '并列计分：AVERAGE均分/FULL满额/ORDER顺延',
  `score_target` VARCHAR(32) NULL COMMENT '计分对象：EVENT单项/TEAM团体',
  `rank_count` SMALLINT NULL COMMENT '计名次数量（明细行数）',
  `point_base` DECIMAL(10,2) NULL COMMENT '计分基准分（用于系数换算）',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否届次默认方案：0否/1是',
  `apply_event_type` VARCHAR(32) NULL COMMENT '适用项目类型：TRACK/FIELD/ALLROUND/RELAY/ALL',
  `apply_gender` VARCHAR(32) NULL COMMENT '适用性别：MALE/FEMALE/MIXED/ALL',
  `group_id` BIGINT UNSIGNED NULL COMMENT '适用组别ID，关联meet_group.id，空为全部组别',
  `effective_round_code` VARCHAR(32) NULL COMMENT '生效轮次码：QUALIFY/PRE/R1/R2/SEMI/FINAL，空默认决赛',
  `is_team_score` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否计入团体总分：0否/1是',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scorerule_meet_code` (`meet_id`, `rule_code`),
  KEY `idx_scorerule_meet_default` (`meet_id`, `is_default`),
  KEY `idx_scorerule_meet_apply` (`meet_id`, `apply_event_type`, `apply_gender`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='名次分方案表';


-- ---------------------------------------------------------------------
-- 6. score_rule_item 名次分明细表 —— 名次（或名次区间）到分值的映射明细，一表多方案
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `score_rule_item`;
CREATE TABLE `score_rule_item` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `rule_id` BIGINT UNSIGNED NOT NULL COMMENT '所属方案ID，关联score_rule.id',
  `rank_no` SMALLINT NOT NULL COMMENT '名次序号（区间行填区间起点）',
  `rank_from` SMALLINT NULL COMMENT '名次区间起（含）',
  `rank_to` SMALLINT NULL COMMENT '名次区间止（含）',
  `rank_label` VARCHAR(50) NULL COMMENT '名次显示名（如「第一名」）',
  `point_value` DECIMAL(10,2) NULL COMMENT '该名次/区间对应分值',
  `bonus_point` DECIMAL(10,2) NULL COMMENT '该名次附加分',
  `tie_policy` VARCHAR(32) NULL COMMENT '该名次并列处理：AVERAGE均分/FULL满额/ORDER顺延，空继承主表',
  `is_team_score` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '该名次分是否计入团体总分：0否/1是',
  `sort_no` INT NULL COMMENT '显示排序号',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scoreitem_rule_rank` (`rule_id`, `rank_no`),
  KEY `idx_scoreitem_rule_range` (`rule_id`, `rank_from`, `rank_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='名次分明细表';


-- ---------------------------------------------------------------------
-- 7. bonus_rule 加分规则表 —— 破纪录加分、接力双倍、破平局加分的可配置规则
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `bonus_rule`;
CREATE TABLE `bonus_rule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `rule_name` VARCHAR(100) NOT NULL COMMENT '加分规则名称',
  `rule_code` VARCHAR(64) NOT NULL COMMENT '规则编码，届次内唯一',
  `bonus_type` VARCHAR(32) NOT NULL COMMENT '加分类型：RECORD破纪录/RELAY接力双倍/BREAK_TIE破平局',
  `record_type` VARCHAR(32) NULL COMMENT '纪录层级：WORLD世界/ASIA亚洲/NATION全国/PROVINCE省/CITY市/MEET赛会/GROUP组别',
  `bonus_point` DECIMAL(10,2) NULL COMMENT '破纪录加分值（默认赛会纪录+9）',
  `is_stackable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '多层级纪录加分是否叠加：0否/1是',
  `factor` DECIMAL(10,2) NULL COMMENT '倍数（接力双倍为2.00）',
  `event_id` BIGINT UNSIGNED NULL COMMENT '适用项目ID，关联event.id，空为全部项目',
  `group_id` BIGINT UNSIGNED NULL COMMENT '适用组别ID，关联meet_group.id，空为全部组别',
  `apply_event_type` VARCHAR(32) NULL COMMENT '适用项目类型：ALL/TRACK/FIELD/ALLROUND/RELAY',
  `apply_gender` VARCHAR(32) NULL COMMENT '适用性别：ALL/MALE/FEMALE',
  `effective_round_codes` VARCHAR(100) NULL COMMENT '生效轮次码，逗号分隔（QUALIFY/PRE/R1/R2/SEMI/FINAL），空为全部',
  `is_in_team_score` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '加分是否计入团体总分：0否/1是',
  `priority` SMALLINT NULL COMMENT '多条命中时的优先顺序',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bonus_meet_code` (`meet_id`, `rule_code`),
  KEY `idx_bonus_meet_type` (`meet_id`, `bonus_type`),
  KEY `idx_bonus_meet_event_type` (`meet_id`, `event_id`, `bonus_type`),
  KEY `idx_bonus_meet_group` (`meet_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加分规则表';


-- ---------------------------------------------------------------------
-- 8. allround_event 全能构成表 —— 全能项目的单项构成、固定顺序与单项间隔
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `allround_event`;
CREATE TABLE `allround_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `event_id` BIGINT UNSIGNED NOT NULL COMMENT '全能项目ID，关联event.id',
  `sub_event_id` BIGINT UNSIGNED NOT NULL COMMENT '单项项目ID，关联event.id',
  `sub_event_name` VARCHAR(100) NULL COMMENT '单项名称冗余，便于打印',
  `sub_event_type` VARCHAR(32) NULL COMMENT '单项类型：TRACK径赛/FIELD田赛',
  `seq_no` SMALLINT NOT NULL COMMENT '全能内固定顺序号',
  `day_no` SMALLINT NULL COMMENT '第几个比赛日',
  `day_period` VARCHAR(32) NULL COMMENT '半天时段：MORNING上午/AFTERNOON下午/EVENING晚上',
  `min_interval` SMALLINT NULL COMMENT '与上一单项最小间隔（分钟）',
  `score_table_id` BIGINT UNSIGNED NULL COMMENT '适用计分表ID，关联allround_score_table.id',
  `gender` VARCHAR(32) NULL COMMENT '适用性别：MALE男/FEMALE女',
  `is_last_event` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否最后一项（如十项1500米）：0否/1是',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_allround_event_seq` (`event_id`, `seq_no`),
  KEY `idx_allround_sub_event` (`sub_event_id`),
  KEY `idx_allround_event_day` (`event_id`, `day_no`, `day_period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全能构成表';


-- ---------------------------------------------------------------------
-- 9. allround_score_table 全能计分表【全局表：无 meet_id，跨届次共用】
--    全能单项「成绩区间 → 得分」换算表，支持手计时/电计时两套
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `allround_score_table`;
CREATE TABLE `allround_score_table` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `table_code` VARCHAR(64) NOT NULL COMMENT '计分表编码（如DECATHLON_MALE_100M）',
  `table_name` VARCHAR(100) NOT NULL COMMENT '计分表名称',
  `event_id` BIGINT UNSIGNED NULL COMMENT '关联全能项目ID，关联event.id，空为通用表',
  `sub_event_id` BIGINT UNSIGNED NULL COMMENT '关联单项项目ID，关联event.id，空为按类型匹配',
  `gender` VARCHAR(32) NULL COMMENT '适用性别：MALE男/FEMALE女',
  `sub_event_type` VARCHAR(32) NULL COMMENT '单项子类型：SPRINT短跑/JUMP跳跃/THROW投掷等',
  `unit` VARCHAR(32) NULL COMMENT '成绩单位：s秒/m米/cm厘米',
  `performance_min` DECIMAL(10,2) NULL COMMENT '成绩区间下限',
  `performance_max` DECIMAL(10,2) NULL COMMENT '成绩区间上限',
  `point_value` DECIMAL(10,2) NULL COMMENT '该区间对应得分',
  `is_hand_time` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否手计时表（手计时换算）：0电计时/1手计时',
  `score_direction` VARCHAR(32) NULL COMMENT '计分方向：ASC越大越好/DESC越小越好',
  `seq_no` INT NULL COMMENT '区间排序号',
  `source_type` VARCHAR(32) NULL COMMENT '来源：BUILTIN内置/IMPORT导入',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_allroundscore_code_gender_time_min` (`table_code`, `gender`, `is_hand_time`, `performance_min`),
  KEY `idx_allroundscore_name_type_min` (`table_name`, `gender`, `sub_event_type`, `performance_min`),
  KEY `idx_allroundscore_event_sub` (`event_id`, `sub_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全能计分表（全局表，跨届次共用）';


-- ---------------------------------------------------------------------
-- 10. event_schedule_rule 项目时长与顺序默认规则表【全局表：无 meet_id，跨届次共用】
--     按「项目类型 × 子类型」内置编排默认值，避免时长与顺序硬编码
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `event_schedule_rule`;
CREATE TABLE `event_schedule_rule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_type` VARCHAR(32) NOT NULL COMMENT '项目类型：TRACK径赛/FIELD田赛/ALLROUND全能/RELAY接力',
  `sub_type` VARCHAR(32) NOT NULL COMMENT '项目子类型（短跑/中长跑/跳跃/投掷等）：SPRINT/MIDDLE/LONG/HURDLE/STEEPLE/WALK/JUMP/THROW/RELAY',
  `gender` VARCHAR(32) NULL COMMENT '适用性别：MALE男/FEMALE女，空为全部',
  `group_id` BIGINT UNSIGNED NULL COMMENT '适用组别ID，关联meet_group.id，空为全部组别',
  `default_duration_min` INT NULL COMMENT '默认项目时长（短跑15、长跑30，分钟）',
  `call_ahead_min` SMALLINT NULL COMMENT '提前检录分钟数',
  `default_order` INT NULL COMMENT '默认排序权重',
  `track_priority` SMALLINT NULL COMMENT '跑道/场地占用优先级',
  `default_lane_count` SMALLINT NULL COMMENT '默认道数（6或8）',
  `period_preference` VARCHAR(32) NULL COMMENT '偏好时段：MORNING上午/AFTERNOON下午/EVENING晚上/ANY不限',
  `max_rounds_per_day` SMALLINT NULL COMMENT '同项目单日最多轮次数',
  `event_interval_min` SMALLINT NULL COMMENT '同项目轮次间最小间隔（分钟）',
  `is_field_order_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需生成田赛出场顺序：0否/1是',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evschedrule_type_sub_gender` (`event_type`, `sub_type`, `gender`),
  KEY `idx_evschedrule_type_sub` (`event_type`, `sub_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目时长与顺序默认规则表（全局表，跨届次共用）';


-- =====================================================================
-- 二、编排层（8 张）
-- =====================================================================


-- ---------------------------------------------------------------------
-- 11. schedule_session 竞赛单元表 —— 某半天在一个场地上的比赛时段，日程条目的时间容器
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `schedule_session`;
CREATE TABLE `schedule_session` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `session_name` VARCHAR(100) NOT NULL COMMENT '单元名称，如「第一天上午」',
  `session_date` DATE NOT NULL COMMENT '单元日期',
  `day_no` SMALLINT NULL COMMENT '赛期第几天，从1开始',
  `period` VARCHAR(32) NOT NULL COMMENT '时段：MORNING上午/AFTERNOON下午/EVENING晚上',
  `start_time` TIME NULL COMMENT '单元开始时刻',
  `end_time` TIME NULL COMMENT '单元结束时刻',
  `venue_id` BIGINT UNSIGNED NULL COMMENT '主场地，关联venue.id',
  `venue_ids_json` JSON NULL COMMENT '同时可用场地ID列表',
  `total_minutes` SMALLINT NULL COMMENT '单元可用总时长（分钟）',
  `used_minutes` SMALLINT NULL COMMENT '已排入时长（分钟），编排后回填',
  `item_count` SMALLINT NULL COMMENT '单元内日程条目数',
  `is_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '单元是否锁定：0未锁定/1已锁定（锁定后不接受自动排入）',
  `locked_by` BIGINT UNSIGNED NULL COMMENT '锁定人，关联sys_user.id',
  `lock_time` DATETIME NULL COMMENT '锁定时间',
  `publish_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT草稿/PUBLISHED已发布',
  `publish_time` DATETIME NULL COMMENT '发布时间',
  `publisher_id` BIGINT UNSIGNED NULL COMMENT '发布人，关联sys_user.id',
  `sort_no` INT NULL COMMENT '单元排序号，决定日程显示顺序',
  `batch_no` VARCHAR(64) NULL COMMENT '生成本单元的编排批次号',
  `remark` VARCHAR(100) NULL COMMENT '备注，如开幕式占用说明',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_meet_date_period` (`meet_id`, `session_date`, `period`),
  KEY `idx_session_meet_date_sort` (`meet_id`, `session_date`, `sort_no`),
  KEY `idx_session_venue_date` (`venue_id`, `session_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='竞赛单元表';


-- ---------------------------------------------------------------------
-- 12. schedule 日程条目表 —— 某项目某轮次在某场地某时间窗内的比赛安排
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `schedule`;
CREATE TABLE `schedule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `session_id` BIGINT UNSIGNED NOT NULL COMMENT '竞赛单元ID，关联schedule_session.id',
  `event_id` BIGINT UNSIGNED NOT NULL COMMENT '项目ID，关联event.id',
  `round_id` BIGINT UNSIGNED NOT NULL COMMENT '轮次ID，关联event_round.id',
  `venue_id` BIGINT UNSIGNED NULL COMMENT '比赛场地ID，关联venue.id',
  `plan_start_time` DATETIME NULL COMMENT '计划开始时间',
  `plan_end_time` DATETIME NULL COMMENT '计划结束时间',
  `actual_start_time` DATETIME NULL COMMENT '实际开始时间，现场回填',
  `actual_end_time` DATETIME NULL COMMENT '实际结束时间，现场回填',
  `duration_min` SMALLINT NULL COMMENT '预计时长（分钟）',
  `actual_duration_min` SMALLINT NULL COMMENT '实际时长（分钟）',
  `heat_count` SMALLINT NULL COMMENT '组数（田赛为轮次组数）',
  `member_count` SMALLINT NULL COMMENT '参赛人数/队数',
  `order_no` INT NULL COMMENT '单元内排序号，决定出场先后',
  `source_type` VARCHAR(32) NULL COMMENT '编排来源：AUTO自动/MANUAL手动/IMPORT导入',
  `batch_no` VARCHAR(64) NULL COMMENT '编排批次号，同一次生成共享',
  `is_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否锁定：0否/1是（重排时跳过锁定项）',
  `locked_by` BIGINT UNSIGNED NULL COMMENT '锁定人，关联sys_user.id',
  `lock_time` DATETIME NULL COMMENT '锁定时间',
  `move_count` SMALLINT NULL COMMENT '被移动次数，拖拽调整累加',
  `adjust_reason` VARCHAR(100) NULL COMMENT '最近一次调整原因',
  `publish_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT草稿/PUBLISHED已发布',
  `publish_time` DATETIME NULL COMMENT '发布时间',
  `publisher_id` BIGINT UNSIGNED NULL COMMENT '发布人，关联sys_user.id',
  `conflict_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否存在冲突：0无/1有（冲突检测后回填）',
  `conflict_count` SMALLINT NULL COMMENT '未处理冲突条数',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_sched_meet_session_order` (`meet_id`, `session_id`, `order_no`),
  KEY `idx_sched_venue_start` (`venue_id`, `plan_start_time`),
  KEY `idx_sched_meet_event_round` (`meet_id`, `event_id`, `round_id`),
  KEY `idx_sched_meet_publish` (`meet_id`, `publish_status`),
  KEY `idx_sched_meet_batch` (`meet_id`, `batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日程条目表';


-- ---------------------------------------------------------------------
-- 13. heat 分组表 —— 一个日程条目下的一个比赛组，径赛为组次、田赛为出场批次
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `heat`;
CREATE TABLE `heat` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `schedule_id` BIGINT UNSIGNED NOT NULL COMMENT '日程条目ID，关联schedule.id',
  `heat_no` SMALLINT NOT NULL COMMENT '组号，从1开始',
  `heat_name` VARCHAR(50) NULL COMMENT '组名，如「预赛第1组」',
  `lane_count` SMALLINT NULL COMMENT '本组可用道数/出场位数',
  `member_count` SMALLINT NULL COMMENT '本组实际人数',
  `seed_count` SMALLINT NULL COMMENT '本组种子选手数',
  `call_time` DATETIME NULL COMMENT '检录时间',
  `call_place` VARCHAR(100) NULL COMMENT '检录地点',
  `start_time` DATETIME NULL COMMENT '本组计划开始时间',
  `end_time` DATETIME NULL COMMENT '本组计划结束时间',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PLANNED' COMMENT '分组状态：PLANNED已计划/CALLING检录中/RUNNING比赛中/FINISHED已完成',
  `is_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否锁定：0否/1是（重排时跳过本组）',
  `locked_by` BIGINT UNSIGNED NULL COMMENT '锁定人，关联sys_user.id',
  `lock_time` DATETIME NULL COMMENT '锁定时间',
  `source_type` VARCHAR(32) NULL COMMENT '编排来源：AUTO自动/MANUAL手动/IMPORT导入',
  `batch_no` VARCHAR(64) NULL COMMENT '编排批次号',
  `draw_mode` VARCHAR(32) NULL COMMENT '分组方式：SEED按种子/DRAW抽签/SERPENT蛇形/ORDER按顺序',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_heat_schedule_no` (`schedule_id`, `heat_no`),
  KEY `idx_heat_meet_sched_start` (`meet_id`, `schedule_id`, `start_time`),
  KEY `idx_heat_meet_status` (`meet_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分组表';


-- ---------------------------------------------------------------------
-- 14. heat_member 组内人员表 —— 参赛身份与出发顺序，检录与成绩录入的衔接点
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `heat_member`;
CREATE TABLE `heat_member` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `heat_id` BIGINT UNSIGNED NOT NULL COMMENT '分组ID，关联heat.id',
  `schedule_id` BIGINT UNSIGNED NOT NULL COMMENT '日程条目ID，关联schedule.id，便于检录与成绩查询',
  `registration_id` BIGINT UNSIGNED NOT NULL COMMENT '报名ID，关联registration.id',
  `athlete_id` BIGINT UNSIGNED NULL COMMENT '运动员ID，关联athlete.id；接力取队长',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '参赛单位ID，关联unit.id',
  `bib_no` VARCHAR(32) NULL COMMENT '号码布号，冗余自athlete.bib_no',
  `group_id` BIGINT UNSIGNED NULL COMMENT '组别ID，关联meet_group.id，用于分组均衡校验',
  `order_no` SMALLINT NULL COMMENT '组内出发/出场顺序号',
  `is_seed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否种子选手：0否/1是（用于种子分散）',
  `is_scratch` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否弃权/弃赛：0否/1是（检录前或检录时）',
  `status` VARCHAR(32) NULL COMMENT '参赛状态：NOT_CALLED未检录/CALLED已检录/PRESENT到检/ABSENT未到/WITHDRAW弃权/DNS未出发（同calling.status）',
  `seed_result` DECIMAL(10,2) NULL COMMENT '报名最好成绩数值，用于排序比较',
  `seed_result_raw` VARCHAR(16) NULL COMMENT '报名成绩原文，用于显示（如11"23）',
  `seed_result_unit` VARCHAR(16) NULL COMMENT '成绩单位：s秒/m米/cm厘米/分',
  `lane_no` SMALLINT NULL COMMENT '道次号（径赛冗余，便于检录表输出）',
  `is_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '本人在组内的位置是否锁定：0否/1是',
  `lock_time` DATETIME NULL COMMENT '锁定时间',
  `batch_no` VARCHAR(64) NULL COMMENT '编排批次号',
  `remark` VARCHAR(100) NULL COMMENT '备注，如递补来源说明',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hm_heat_registration` (`heat_id`, `registration_id`),
  UNIQUE KEY `uk_hm_heat_order` (`heat_id`, `order_no`),
  KEY `idx_hm_meet_sched_reg` (`meet_id`, `schedule_id`, `registration_id`),
  KEY `idx_hm_meet_athlete` (`meet_id`, `athlete_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组内人员表';


-- ---------------------------------------------------------------------
-- 15. lane 道次表 —— 径赛分组内的道次分配结果，含分配方式与交换留痕
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `lane`;
CREATE TABLE `lane` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `heat_id` BIGINT UNSIGNED NOT NULL COMMENT '分组ID，关联heat.id',
  `heat_member_id` BIGINT UNSIGNED NOT NULL COMMENT '组内人员ID，关联heat_member.id',
  `schedule_id` BIGINT UNSIGNED NOT NULL COMMENT '日程条目ID，关联schedule.id，便于按项目查分道表',
  `lane_no` SMALLINT NOT NULL COMMENT '道次号，从1开始',
  `assign_mode` VARCHAR(32) NULL COMMENT '分配方式：SEED按种子/DRAW抽签/SERPENT蛇形/ORDER按顺序',
  `lane_order_source` VARCHAR(32) NULL COMMENT '道次顺序来源：MEET届次/EVENT项目/CUSTOM自定义',
  `lane_order_json` JSON NULL COMMENT '本次实际使用的道次优先顺序快照',
  `seed_rank` SMALLINT NULL COMMENT '成绩排序位次，决定优先选道次序',
  `is_best_lane` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否最优道次（如4道）：0否/1是（用于核对）',
  `is_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否锁定：0否/1是（重排时保持本道次不变）',
  `locked_by` BIGINT UNSIGNED NULL COMMENT '锁定人，关联sys_user.id',
  `lock_time` DATETIME NULL COMMENT '锁定时间',
  `swap_group` VARCHAR(64) NULL COMMENT '交换批次号，同一次道次交换共享',
  `swap_from_lane` SMALLINT NULL COMMENT '交换前道次号',
  `swap_reason` VARCHAR(100) NULL COMMENT '交换原因，如申诉调整',
  `swap_time` DATETIME NULL COMMENT '交换时间',
  `swap_by` BIGINT UNSIGNED NULL COMMENT '交换操作人，关联sys_user.id',
  `batch_no` VARCHAR(64) NULL COMMENT '编排批次号',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lane_heat_no` (`heat_id`, `lane_no`),
  UNIQUE KEY `uk_lane_heat_member` (`heat_member_id`),
  KEY `idx_lane_meet_sched_no` (`meet_id`, `schedule_id`, `lane_no`),
  KEY `idx_lane_meet_batch` (`meet_id`, `batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='道次表';


-- ---------------------------------------------------------------------
-- 16. field_order 田赛出场顺序表 —— 按成绩/抽签/单位/号码排序与免跳、弃权标记
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `field_order`;
CREATE TABLE `field_order` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `heat_id` BIGINT UNSIGNED NOT NULL COMMENT '分组ID，关联heat.id',
  `heat_member_id` BIGINT UNSIGNED NOT NULL COMMENT '组内人员ID，关联heat_member.id',
  `schedule_id` BIGINT UNSIGNED NOT NULL COMMENT '日程条目ID，关联schedule.id',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID，关联event.id，区分跳高/跳远/铅球等',
  `order_no` SMALLINT NOT NULL COMMENT '出场顺序号，从1开始',
  `group_no` SMALLINT NULL COMMENT '田赛分组号（人数多时分批出场）',
  `order_mode` VARCHAR(32) NULL COMMENT '排序方式：SEED按成绩/DRAW抽签/UNIT按单位/ORDER按号码',
  `best_seed` DECIMAL(10,2) NULL COMMENT '报名最好成绩数值，用于排序比较',
  `best_seed_raw` VARCHAR(16) NULL COMMENT '报名最好成绩原文，用于显示',
  `best_seed_unit` VARCHAR(16) NULL COMMENT '成绩单位：m米/cm厘米/分',
  `start_height` DECIMAL(10,2) NULL COMMENT '起跳高度/起始重量（m或kg）',
  `pass_count` SMALLINT NULL COMMENT '免跳次数累计（PASS）',
  `is_scratch` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否弃权/弃赛：0否/1是（出场时跳过）',
  `is_pass` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否本轮免跳：0否/1是',
  `is_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否锁定：0否/1是（重排时保持本顺序）',
  `locked_by` BIGINT UNSIGNED NULL COMMENT '锁定人，关联sys_user.id',
  `lock_time` DATETIME NULL COMMENT '锁定时间',
  `move_count` SMALLINT NULL COMMENT '顺序被调整次数',
  `batch_no` VARCHAR(64) NULL COMMENT '编排批次号',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forder_heat_order` (`heat_id`, `order_no`),
  UNIQUE KEY `uk_forder_heat_member` (`heat_member_id`),
  KEY `idx_forder_meet_sched_group_order` (`meet_id`, `schedule_id`, `group_no`, `order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='田赛出场顺序表';


-- ---------------------------------------------------------------------
-- 17. conflict_log 冲突检测记录表 —— 覆盖兼项、场地、裁判、间隔四类
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `conflict_log`;
CREATE TABLE `conflict_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `conflict_type` VARCHAR(32) NOT NULL COMMENT '冲突类型：ATHLETE兼项/VENUE场地/REFEREE裁判/INTERVAL间隔不足',
  `level` VARCHAR(32) NULL COMMENT '冲突级别：ERROR阻断/WARN提醒',
  `schedule_id` BIGINT UNSIGNED NULL COMMENT '主体日程条目ID，关联schedule.id',
  `related_schedule_id` BIGINT UNSIGNED NULL COMMENT '关联冲突日程条目ID，关联schedule.id',
  `heat_id` BIGINT UNSIGNED NULL COMMENT '主体分组ID，关联heat.id',
  `related_heat_id` BIGINT UNSIGNED NULL COMMENT '关联分组ID，关联heat.id',
  `athlete_id` BIGINT UNSIGNED NULL COMMENT '涉及运动员ID，关联athlete.id',
  `registration_id` BIGINT UNSIGNED NULL COMMENT '涉及报名ID，关联registration.id',
  `referee_id` BIGINT UNSIGNED NULL COMMENT '涉及裁判ID，关联referee.id',
  `venue_id` BIGINT UNSIGNED NULL COMMENT '涉及场地ID，关联venue.id',
  `session_id` BIGINT UNSIGNED NULL COMMENT '涉及竞赛单元ID，关联schedule_session.id',
  `start_time` DATETIME NULL COMMENT '冲突区间开始时间',
  `end_time` DATETIME NULL COMMENT '冲突区间结束时间',
  `related_start_time` DATETIME NULL COMMENT '关联对象区间开始时间',
  `related_end_time` DATETIME NULL COMMENT '关联对象区间结束时间',
  `overlap_minutes` SMALLINT NULL COMMENT '重叠分钟数，0表示无重叠',
  `interval_minutes` SMALLINT NULL COMMENT '实际间隔分钟数，小于阈值即冲突',
  `required_interval_min` SMALLINT NULL COMMENT '规则要求的兼项间隔分钟数',
  `detail_json` JSON NULL COMMENT '冲突详情：涉及人员、时间区间、场地、间隔分钟数',
  `batch_no` VARCHAR(64) NULL COMMENT '触发本次检测的编排批次号',
  `detect_time` DATETIME NULL COMMENT '检测时间',
  `resolved` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已处理：0未处理/1已处理',
  `resolve_remark` VARCHAR(100) NULL COMMENT '处理说明，如「已调整至下一单元」',
  `resolve_by` BIGINT UNSIGNED NULL COMMENT '处理人，关联sys_user.id',
  `resolve_time` DATETIME NULL COMMENT '处理时间',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_conflict_meet_type_resolved` (`meet_id`, `conflict_type`, `resolved`),
  KEY `idx_conflict_schedule` (`schedule_id`),
  KEY `idx_conflict_meet_detect` (`meet_id`, `detect_time`),
  KEY `idx_conflict_meet_athlete_detect` (`meet_id`, `athlete_id`, `detect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冲突检测记录表';


-- ---------------------------------------------------------------------
-- 18. schedule_snapshot 编排快照表 —— 一次编排结果的完整版本留档，用于版本快照与回滚
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `schedule_snapshot`;
CREATE TABLE `schedule_snapshot` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `snapshot_name` VARCHAR(100) NOT NULL COMMENT '快照名称，如「自动编排v3」',
  `batch_no` VARCHAR(64) NULL COMMENT '对应编排批次号',
  `snapshot_type` VARCHAR(32) NULL COMMENT '快照类型：AUTO自动/MANUAL手动/RESTORE回滚备份',
  `snapshot_json` JSON NULL COMMENT '编排结果完整快照（单元/日程/分组/人员/道次）',
  `item_count` INT NULL COMMENT '快照内日程条目数',
  `heat_count` INT NULL COMMENT '快照内分组数',
  `member_count` INT NULL COMMENT '快照内参赛人员数',
  `locked_count` INT NULL COMMENT '快照内锁定项数量',
  `conflict_count` INT NULL COMMENT '快照生成时的冲突条数',
  `scope_type` VARCHAR(32) NULL COMMENT '快照范围：ALL全部/SESSION按单元/EVENT按项目',
  `scope_ids_json` JSON NULL COMMENT '范围ID列表，对应单元或项目',
  `is_current` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前生效版本：0否/1是（回滚后切换）',
  `operator_id` BIGINT UNSIGNED NULL COMMENT '操作人，关联sys_user.id',
  `restore_time` DATETIME NULL COMMENT '被回滚使用的时间',
  `restore_by` BIGINT UNSIGNED NULL COMMENT '执行回滚的用户，关联sys_user.id',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_snapshot_meet_create` (`meet_id`, `create_time`),
  KEY `idx_snapshot_meet_current` (`meet_id`, `is_current`),
  KEY `idx_snapshot_meet_batch` (`meet_id`, `batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='编排快照表';


-- =====================================================================
-- 三、赛前层（5 张）
-- =====================================================================


-- ---------------------------------------------------------------------
-- 19. calling 检录记录表 —— 检录环节的逐人记录，衔接编排名单与成绩录入
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `calling`;
CREATE TABLE `calling` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `schedule_id` BIGINT UNSIGNED NOT NULL COMMENT '日程条目ID，关联schedule.id',
  `heat_id` BIGINT UNSIGNED NOT NULL COMMENT '分组ID，关联heat.id',
  `registration_id` BIGINT UNSIGNED NOT NULL COMMENT '报名记录ID，关联registration.id',
  `athlete_id` BIGINT UNSIGNED NULL COMMENT '运动员ID，关联athlete.id',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '参赛单位ID，关联unit.id',
  `lane_no` SMALLINT NULL COMMENT '道次（径赛）或出场顺序（田赛）',
  `call_time` DATETIME NULL COMMENT '应检录时间',
  `call_place` VARCHAR(100) NULL COMMENT '检录地点',
  `present_time` DATETIME NULL COMMENT '实际到检时间',
  `status` VARCHAR(32) NOT NULL DEFAULT 'NOT_CALLED' COMMENT '检录状态：NOT_CALLED未检录/CALLED已检录/PRESENT到检/ABSENT未到/WITHDRAW弃权/DNS未出发',
  `referee_id` BIGINT UNSIGNED NULL COMMENT '检录裁判，关联referee.id',
  `absent_reason` VARCHAR(100) NULL COMMENT '未到/弃权原因',
  `substitute_json` JSON NULL COMMENT '换人或替补信息',
  `is_bib_checked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '号码布是否核对通过：0否/1是',
  `is_equipment_checked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '服装与器材是否检查通过：0否/1是',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_calling_heat_registration` (`heat_id`, `registration_id`),
  KEY `idx_calling_schedule_status` (`schedule_id`, `status`),
  KEY `idx_calling_meet_status` (`meet_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检录记录表';


-- ---------------------------------------------------------------------
-- 20. draw_record 抽签记录表 —— 保存抽签范围、方式、结果快照与操作留痕
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `draw_record`;
CREATE TABLE `draw_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `draw_type` VARCHAR(32) NOT NULL COMMENT '抽签类型：RANDOM随机/SEED种子/LANE道次',
  `scope_type` VARCHAR(32) NOT NULL COMMENT '范围类型：EVENT项目/ROUND轮次/HEAT分组/GROUP组别',
  `scope_id` BIGINT UNSIGNED NULL COMMENT '范围对象ID（按scope_type解释）',
  `scope_name` VARCHAR(100) NULL COMMENT '范围对象名称（冗余显示）',
  `schedule_id` BIGINT UNSIGNED NULL COMMENT '日程条目ID，关联schedule.id',
  `round_id` BIGINT UNSIGNED NULL COMMENT '轮次ID，关联event_round.id',
  `method` VARCHAR(32) NULL COMMENT '抽签方式：SYSTEM系统自动/MANUAL现场手动',
  `seed_json` JSON NULL COMMENT '抽签依据快照（种子名单、分组规则）',
  `result_json` JSON NULL COMMENT '抽签结果（对象→组号/道次/顺序）',
  `apply_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '应用状态：DRAFT草稿/APPLIED已应用/REVOKED已撤销',
  `is_published` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已公布：0否/1是',
  `witness` VARCHAR(100) NULL COMMENT '见证人/监督人',
  `operator_id` BIGINT UNSIGNED NULL COMMENT '操作人，关联sys_user.id',
  `operator_name` VARCHAR(50) NULL COMMENT '操作人姓名（冗余显示）',
  `draw_time` DATETIME NULL COMMENT '抽签执行时间',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_draw_meet_type` (`meet_id`, `draw_type`),
  KEY `idx_draw_schedule_type` (`schedule_id`, `draw_type`),
  KEY `idx_draw_meet_time` (`meet_id`, `draw_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽签记录表';


-- ---------------------------------------------------------------------
-- 21. start_list 参赛名单发布表 —— 按日程/单位发布名单与出场顺序，管理发布版本与可见范围
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `start_list`;
CREATE TABLE `start_list` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `schedule_id` BIGINT UNSIGNED NOT NULL COMMENT '日程条目ID，关联schedule.id',
  `heat_id` BIGINT UNSIGNED NULL COMMENT '分组ID，关联heat.id（分组名单使用）',
  `event_id` BIGINT UNSIGNED NULL COMMENT '项目ID，关联event.id',
  `round_id` BIGINT UNSIGNED NULL COMMENT '轮次ID，关联event_round.id',
  `unit_id` BIGINT UNSIGNED NULL COMMENT '单位ID，关联unit.id（按单位发布时）',
  `list_type` VARCHAR(32) NOT NULL COMMENT '名单类型：START_LIST参赛名单/ORDER_LIST出场顺序/HEAT_LIST分组名单',
  `publish_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT草稿/PUBLISHED已发布/REVOKED已撤回',
  `publish_version` INT NOT NULL DEFAULT 1 COMMENT '发布版本号（同名单多次发布递增）',
  `publish_time` DATETIME NULL COMMENT '发布时间',
  `publisher_id` BIGINT UNSIGNED NULL COMMENT '发布人，关联sys_user.id',
  `display_scope` VARCHAR(32) NULL COMMENT '可见范围：PUBLIC公开/LEADER领队/REFEREE裁判',
  `content_json` JSON NULL COMMENT '名单内容快照（用于撤回复核与对比）',
  `file_id` BIGINT UNSIGNED NULL COMMENT '名单文件附件，关联sys_file.id',
  `is_printed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已打印：0否/1是',
  `print_count` INT NULL COMMENT '打印次数',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_startlist_meet_schedule` (`meet_id`, `schedule_id`),
  KEY `idx_startlist_meet_publish` (`meet_id`, `publish_status`),
  UNIQUE KEY `uk_startlist_sched_unit_type_ver` (`schedule_id`, `unit_id`, `list_type`, `publish_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='参赛名单发布表';


-- ---------------------------------------------------------------------
-- 22. referee_duty 裁判岗位字典表【全局表：无 meet_id，跨届次共用】
--     定义裁判岗位编码、分组、默认人数与互斥关系
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `referee_duty`;
CREATE TABLE `referee_duty` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `duty_code` VARCHAR(32) NOT NULL COMMENT '岗位编码，跨届次唯一',
  `duty_name` VARCHAR(100) NOT NULL COMMENT '岗位名称（如终点主裁）',
  `duty_group` VARCHAR(32) NULL COMMENT '岗位分组：TRACK径赛/FIELD田赛/RECORD记录/OTHER其他',
  `event_type_scope` VARCHAR(32) NULL COMMENT '适用项目类型：TRACK径赛/FIELD田赛/RELAY接力/ALL全部',
  `default_headcount` SMALLINT NULL COMMENT '默认配置人数',
  `min_headcount` SMALLINT NULL COMMENT '最少可用人数（低于则告警）',
  `require_level` VARCHAR(50) NULL COMMENT '岗位等级要求',
  `is_chief_post` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否裁判长岗位（每场唯一）：0否/1是',
  `is_concurrent_allowed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许同一人本场兼任多个岗位：0否/1是',
  `conflict_group` VARCHAR(32) NULL COMMENT '互斥岗位组（同组岗位不可同时分配）',
  `sort_no` SMALLINT NULL COMMENT '显示排序号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refduty_code` (`duty_code`),
  KEY `idx_refduty_group_sort` (`duty_group`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='裁判岗位字典表（全局表，跨届次共用）';


-- ---------------------------------------------------------------------
-- 23. equipment 器材/物资表 —— 登记器材数量、保管人与检定状态，支撑赛前物资准备
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `equipment`;
CREATE TABLE `equipment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id` BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `equipment_name` VARCHAR(100) NOT NULL COMMENT '器材名称',
  `equipment_code` VARCHAR(64) NOT NULL COMMENT '器材编码，届次内唯一',
  `equipment_type` VARCHAR(32) NULL COMMENT '类型：TIMING计时/MEASURE测量/THROW投掷器械/JUMP跳跃器材/OTHER其他',
  `spec` VARCHAR(100) NULL COMMENT '规格型号（如7.26kg铅球）',
  `unit` VARCHAR(50) NULL COMMENT '计量单位：个/套/台',
  `quantity` INT NULL COMMENT '在册总数量',
  `available_quantity` INT NULL COMMENT '当前可用数量',
  `venue_id` BIGINT UNSIGNED NULL COMMENT '使用或存放场地，关联venue.id',
  `schedule_id` BIGINT UNSIGNED NULL COMMENT '关联日程条目，关联schedule.id（按场次分配）',
  `store_place` VARCHAR(100) NULL COMMENT '存放地点',
  `keeper` VARCHAR(50) NULL COMMENT '保管人姓名',
  `keeper_phone` VARCHAR(50) NULL COMMENT '保管人电话',
  `check_date` DATE NULL COMMENT '最近检定/检验日期',
  `next_check_date` DATE NULL COMMENT '下次检定日期',
  `status` VARCHAR(32) NOT NULL DEFAULT 'IDLE' COMMENT '状态：IDLE闲置/IN_USE使用中/MAINTENANCE维护中/SCRAPPED报废',
  `remark` VARCHAR(100) NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED NULL COMMENT '创建人，关联sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL COMMENT '修改人，关联sys_user.id',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_equip_meet_venue` (`meet_id`, `venue_id`),
  UNIQUE KEY `uk_equip_meet_code` (`meet_id`, `equipment_code`),
  KEY `idx_equip_meet_status` (`meet_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='器材/物资表';


SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 脚本结束：共 23 张表
--   项目与规则层 10 张：event、event_round、event_round_rule、event_rule_config、
--                      score_rule、score_rule_item、bonus_rule、allround_event、
--                      allround_score_table（全局）、event_schedule_rule（全局）
--   编排层 8 张：schedule_session、schedule、heat、heat_member、lane、field_order、
--                conflict_log、schedule_snapshot
--   赛前层 5 张：calling、draw_record、start_list、referee_duty（全局）、equipment
-- =====================================================================
