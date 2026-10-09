-- =============================================================================
-- 项目名称：TrackMeet —— 田径运动会编排软件
-- 脚本名称：sql/02-schema-meet-athlete.sql
-- 脚本用途：创建「届次基础层 11 张 + 人员报名层 5 张」共 16 张业务表
--           届次基础层：meet、meet_group、unit、unit_staff、venue、venue_slot、
--                       referee、referee_assignment、record、meet_rule_config、
--                       meet_clone_log
--           人员报名层：athlete、registration、relay_member、
--                       registration_limit、athlete_event_count
-- 目标数据库：MySQL 8.0（字符集 utf8mb4 / 排序规则 utf8mb4_0900_ai_ci）
--            utf8mb4 支持全部 Unicode（含生僻姓名、emoji）；utf8mb4_0900_ai_ci
--            为 MySQL 8 默认排序规则，大小写与重音不敏感，适用于中文人名地名检索。
--            存储引擎：InnoDB，支持事务、行级锁与崩溃恢复。
-- 字段依据：docs/12-02-字段清单-届次基础与赛前层.md（仅取届次基础层 11 张）
--           docs/12-03-字段清单-人员报名与项目规则层.md（仅取人员与报名层 5 张）
--           docs/12-00-字段清单总览.md 第 1/2/3 节（类型规范、通用字段、命名与约束规范）
-- 约定说明：
--   1. 通用字段：id、meet_id、create_by、create_time、update_by、update_time、
--      deleted、version；meet 为届次根实体，自身不含 meet_id 列。
--   2. 不使用物理 FOREIGN KEY 约束，跨表引用由应用层保证（便于备份恢复与批量导入）；
--      无物理外键，因此建表顺序无强依赖，本脚本可一次性从零执行。
--   3. 索引命名：唯一约束 uk_<字段组合>、普通索引 idx_<字段组合>，索引列顺序与字段
--      清单文档「索引与约束」一行完全一致。
--   4. 保留字列名（status、source 等）统一使用反引号包裹。
--   5. 本脚本只建表：不含 INSERT 初始化数据、存储过程与触发器。
-- 执行顺序：01（系统层）→ 02（本脚本：届次基础层 + 人员报名层）
--           → 03（项目与规则层）→ 04（编排层）→ 05（成绩与统计输出层）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 第 1 节 届次基础层（11 张）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. meet 运动会（届次）表
-- 一届运动会的根实体，是所有业务数据的隔离边界与全局开关。
-- 注意：本表为届次本身，不含 meet_id 列。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `meet`;
CREATE TABLE `meet` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_name`          VARCHAR(100)    NOT NULL COMMENT '运动会名称',
  `edition`            VARCHAR(50)     NULL     COMMENT '届次标识（如「2025年第19届」）',
  `short_name`         VARCHAR(50)     NULL     COMMENT '简称（大屏、证书使用）',
  `host`               VARCHAR(100)    NULL     COMMENT '主办单位',
  `organizer`          VARCHAR(100)    NULL     COMMENT '承办单位',
  `co_organizer`       VARCHAR(100)    NULL     COMMENT '协办单位',
  `start_date`         DATE            NULL     COMMENT '比赛开始日期',
  `end_date`           DATE            NULL     COMMENT '比赛结束日期',
  `place`              VARCHAR(100)    NULL     COMMENT '举办地点',
  `reg_start_time`     DATETIME        NULL     COMMENT '报名开始时间',
  `reg_end_time`       DATETIME        NULL     COMMENT '报名截止时间',
  `status`             VARCHAR(32)     NOT NULL COMMENT '届次状态：DRAFT草稿/REG_OPEN报名中/REG_CLOSED报名截止/SCHEDULING编排中/SCHEDULED编排完成/RUNNING比赛进行/FINISHED已结束/ARCHIVED归档',
  `regulation_html`    LONGTEXT        NULL     COMMENT '竞赛规程 HTML',
  `regulation_file_id` BIGINT UNSIGNED NULL     COMMENT '规程附件 sys_file.id',
  `logo_file_id`       BIGINT UNSIGNED NULL     COMMENT '会徽附件 sys_file.id',
  `is_template`        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否可作为复制模板：0否/1是',
  `config_json`        JSON            NULL     COMMENT '届次默认规则扩展 JSON（每人限报项数、兼项间隔、破纪录是否计入团体分、奖牌榜排序、计时精度等）',
  `remark`             VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`          BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_name_edition` (`meet_name`, `edition`),
  KEY `idx_status` (`status`),
  KEY `idx_start_date` (`start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运动会（届次）表';

-- -----------------------------------------------------------------------------
-- 2. meet_group 组别表
-- 划分参赛对象（性别、年龄、年级），并决定是否计入团体分与使用哪套组别纪录。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `meet_group`;
CREATE TABLE `meet_group` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`           BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `group_name`        VARCHAR(100)    NOT NULL COMMENT '组别名称（如高中男子组）',
  `group_code`        VARCHAR(64)     NOT NULL COMMENT '组别编码（届次内唯一）',
  `gender`            VARCHAR(32)     NOT NULL COMMENT '性别限制：MALE男/FEMALE女/MIXED混合',
  `age_min`           SMALLINT        NULL     COMMENT '最小年龄（含）',
  `age_max`           SMALLINT        NULL     COMMENT '最大年龄（含）',
  `birth_date_start`  DATE            NULL     COMMENT '出生日期区间起（资格判定基准）',
  `birth_date_end`    DATE            NULL     COMMENT '出生日期区间止',
  `grade_range`       VARCHAR(100)    NULL     COMMENT '年级范围（如高一至高三）',
  `is_team_score`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否计入团体总分：0否/1是',
  `score_group_code`  VARCHAR(64)     NULL     COMMENT '团体计分归并码（跨组合并计分）',
  `record_group_code` VARCHAR(64)     NULL     COMMENT '组别纪录归并码（对应 record 表）',
  `sort_no`           SMALLINT        NULL     COMMENT '显示排序号',
  `status`            VARCHAR(32)     NOT NULL COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark`            VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`         BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_id_group_code` (`meet_id`, `group_code`),
  KEY `idx_meet_id_sort_no` (`meet_id`, `sort_no`),
  KEY `idx_meet_id_gender` (`meet_id`, `gender`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组别表';

-- -----------------------------------------------------------------------------
-- 3. unit 参赛单位表
-- 参赛主体的组织单元，承载号码布前缀、领队教练归属与单位级报名上限。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `unit`;
CREATE TABLE `unit` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`          BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `unit_name`        VARCHAR(100)    NOT NULL COMMENT '单位名称',
  `unit_code`        VARCHAR(64)     NOT NULL COMMENT '单位编码（届次内唯一）',
  `short_name`       VARCHAR(50)     NULL     COMMENT '单位简称（榜单、大屏）',
  `unit_type`        VARCHAR(32)     NOT NULL COMMENT '类型：SCHOOL学校/CLUB俱乐部/OTHER其他',
  `number_prefix`    VARCHAR(50)     NULL     COMMENT '号码布前缀（如「一中」）',
  `leader`           VARCHAR(50)     NULL     COMMENT '领队姓名（主联系人）',
  `coach`            VARCHAR(50)     NULL     COMMENT '主教练姓名',
  `phone`            VARCHAR(50)     NULL     COMMENT '联系电话',
  `address`          VARCHAR(100)    NULL     COMMENT '通信地址',
  `logo_file_id`     BIGINT UNSIGNED NULL     COMMENT '单位标志附件 sys_file.id',
  `group_scope_json` JSON            NULL     COMMENT '可参加的组别范围 JSON（组别编码数组）',
  `total_quota`      INT             NULL     COMMENT '单位报名人数上限（0 表示不限）',
  `status`           VARCHAR(32)     NOT NULL COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark`           VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`        BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_id_unit_code` (`meet_id`, `unit_code`),
  KEY `idx_meet_id_status` (`meet_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='参赛单位表';

-- -----------------------------------------------------------------------------
-- 4. unit_staff 单位领队/教练表
-- 记录各单位领队、教练、队医等随队人员，用于数据权限关联与秩序册编制。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `unit_staff`;
CREATE TABLE `unit_staff` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`     BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `unit_id`     BIGINT UNSIGNED NOT NULL COMMENT '所属单位 unit.id',
  `staff_name`  VARCHAR(50)     NOT NULL COMMENT '姓名',
  `staff_type`  VARCHAR(32)     NOT NULL COMMENT '类型：LEADER领队/COACH教练/DOCTOR队医',
  `gender`      VARCHAR(32)     NULL     COMMENT '性别：MALE男/FEMALE女',
  `phone`       VARCHAR(50)     NULL     COMMENT '联系电话',
  `id_card`     VARCHAR(32)     NULL     COMMENT '身份证号（兼容护照号）',
  `duty_title`  VARCHAR(50)     NULL     COMMENT '职务/职称',
  `is_contact`  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否单位唯一联系人：0否/1是',
  `sort_no`     SMALLINT        NULL     COMMENT '显示排序号',
  `status`      VARCHAR(32)     NOT NULL COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark`      VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`   BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_unit_id_staff_type` (`unit_id`, `staff_type`),
  UNIQUE KEY `uk_meet_id_unit_id_id_card` (`meet_id`, `unit_id`, `id_card`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单位领队/教练表';

-- -----------------------------------------------------------------------------
-- 5. venue 场地表
-- 比赛场地及其道数与道次顺序配置，是径赛道次分配算法的输入。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `venue`;
CREATE TABLE `venue` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`            BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `venue_name`         VARCHAR(100)    NOT NULL COMMENT '场地名称',
  `venue_code`         VARCHAR(64)     NOT NULL COMMENT '场地编码（届次内唯一）',
  `venue_type`         VARCHAR(32)     NOT NULL COMMENT '类型：TRACK径赛/FIELD田赛/INDOOR馆内',
  `lane_count`         SMALLINT        NULL     COMMENT '道数（6 道或 8 道）',
  `lane_order_json`    JSON            NULL     COMMENT '道次优先顺序 JSON，默认 8 道 [4,5,3,6,2,7,1,8]',
  `lane_width`         DECIMAL(10,2)   NULL     COMMENT '单道宽度（米）',
  `location`           VARCHAR(100)    NULL     COMMENT '场地位置描述',
  `capacity`           INT             NULL     COMMENT '可容纳观众人数',
  `is_wind_measured`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否配置风速测量设备：0否/1是',
  `is_electric_timing` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否具备电子计时：0否/1是',
  `field_zone_json`    JSON            NULL     COMMENT '田赛区配置 JSON（跳跃区、投掷区编号与朝向）',
  `available_json`     JSON            NULL     COMMENT '可用情况扩展 JSON（可承接项目类型、限制说明）',
  `contact_name`       VARCHAR(50)     NULL     COMMENT '场地联系人',
  `contact_phone`      VARCHAR(50)     NULL     COMMENT '联系人电话',
  `status`             VARCHAR(32)     NOT NULL COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark`             VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`          BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_id_venue_code` (`meet_id`, `venue_code`),
  KEY `idx_meet_id_venue_type` (`meet_id`, `venue_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地表';

-- -----------------------------------------------------------------------------
-- 6. venue_slot 场地可用时段表
-- 把场地按日期与时刻切成可排期时段，供自动编排与场地冲突检测使用。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `venue_slot`;
CREATE TABLE `venue_slot` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`            BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `venue_id`           BIGINT UNSIGNED NOT NULL COMMENT '场地 venue.id',
  `slot_date`          DATE            NOT NULL COMMENT '时段日期',
  `day_no`             SMALLINT        NULL     COMMENT '比赛日序号（第 1、2 个比赛日）',
  `start_time`         TIME            NOT NULL COMMENT '时段开始时刻',
  `end_time`           TIME            NULL     COMMENT '时段结束时刻',
  `session_id`         BIGINT UNSIGNED NULL     COMMENT '关联竞赛单元 schedule_session.id',
  `slot_type`          VARCHAR(32)     NOT NULL COMMENT '时段类型：COMPETITION比赛/WARMUP热身/MAINTENANCE维护',
  `lane_scope_json`    JSON            NULL     COMMENT '本时段可用道次范围 JSON，如 [1,2,3,4,5,6,7,8]',
  `capacity`           INT             NULL     COMMENT '本时段可同时容纳的项目数',
  `is_available`       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否可用：0不可用/1可用',
  `unavailable_reason` VARCHAR(100)    NULL     COMMENT '不可用原因',
  `sort_no`            SMALLINT        NULL     COMMENT '显示排序号',
  `remark`             VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`          BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_venue_id_slot_date` (`venue_id`, `slot_date`),
  KEY `idx_meet_id_slot_date_start_time` (`meet_id`, `slot_date`, `start_time`),
  UNIQUE KEY `uk_venue_id_slot_date_start_time_slot_type` (`venue_id`, `slot_date`, `start_time`, `slot_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地可用时段表';

-- -----------------------------------------------------------------------------
-- 7. referee 裁判/工作人员表
-- 裁判与工作人员档案，含岗位类型、等级与每日可承担岗位数，是分工与冲突检测的主体。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `referee`;
CREATE TABLE `referee` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`           BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `referee_name`      VARCHAR(50)     NOT NULL COMMENT '姓名',
  `gender`            VARCHAR(32)     NULL     COMMENT '性别：MALE男/FEMALE女',
  `unit_id`           BIGINT UNSIGNED NULL     COMMENT '所属单位 unit.id（外聘可空）',
  `unit_name`         VARCHAR(100)    NULL     COMMENT '所属单位名称（外部人员直接填写）',
  `duty_type`         VARCHAR(32)     NOT NULL COMMENT '主岗位类型：TRACK径赛/FIELD田赛/RECORD记录/OTHER其他',
  `referee_level`     VARCHAR(50)     NULL     COMMENT '裁判等级（国家级、一级、二级）',
  `phone`             VARCHAR(50)     NULL     COMMENT '联系电话',
  `id_card`           VARCHAR(32)     NULL     COMMENT '身份证号（兼容护照号）',
  `cert_no`           VARCHAR(64)     NULL     COMMENT '裁判证书编号',
  `is_chief`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否具备裁判长资格：0否/1是',
  `is_external`       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否外聘人员：0否/1是',
  `max_duty_per_day`  SMALLINT        NULL     COMMENT '单日最多承担岗位数（分工约束）',
  `available_json`    JSON            NULL     COMMENT '可工作时间与偏好岗位 JSON',
  `status`            VARCHAR(32)     NOT NULL COMMENT '状态：ENABLED启用/DISABLED停用',
  `remark`            VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`         BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_meet_id_duty_type` (`meet_id`, `duty_type`),
  KEY `idx_meet_id_unit_id` (`meet_id`, `unit_id`),
  UNIQUE KEY `uk_meet_id_id_card` (`meet_id`, `id_card`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='裁判/工作人员表';

-- -----------------------------------------------------------------------------
-- 8. referee_assignment 裁判分工表
-- 按日程/单元给裁判分派具体岗位与起止时段，为裁判时间冲突检测提供数据。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `referee_assignment`;
CREATE TABLE `referee_assignment` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`       BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `referee_id`    BIGINT UNSIGNED NOT NULL COMMENT '裁判 referee.id',
  `schedule_id`   BIGINT UNSIGNED NOT NULL COMMENT '日程条目 schedule.id',
  `session_id`    BIGINT UNSIGNED NULL     COMMENT '竞赛单元 schedule_session.id',
  `event_id`      BIGINT UNSIGNED NULL     COMMENT '关联项目 event.id',
  `round_id`      BIGINT UNSIGNED NULL     COMMENT '关联轮次 event_round.id',
  `venue_id`      BIGINT UNSIGNED NULL     COMMENT '执行场地 venue.id',
  `duty_code`     VARCHAR(32)     NOT NULL COMMENT '岗位编码（对应 referee_duty.duty_code）',
  `post_no`       SMALLINT        NULL     COMMENT '岗位序号（第几道、第几组、第几号位）',
  `duty_role`     VARCHAR(32)     NULL     COMMENT '岗位角色：MAIN主岗/ASSIST副岗',
  `start_time`    DATETIME        NULL     COMMENT '分工开始时间（含提前到场）',
  `end_time`      DATETIME        NULL     COMMENT '分工结束时间',
  `assign_mode`   VARCHAR(32)     NULL     COMMENT '分配方式：AUTO自动/MANUAL手动',
  `is_chief`      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否本场主裁：0否/1是',
  `conflict_flag` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否检测到时间冲突：0否/1是',
  `status`        VARCHAR(32)     NOT NULL COMMENT '状态：PLANNED待执行/CONFIRMED已确认/FINISHED已完成/CANCELLED已取消',
  `remark`        VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`     BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_id_referee_id_duty_code` (`schedule_id`, `referee_id`, `duty_code`),
  KEY `idx_referee_id_session_id` (`referee_id`, `session_id`),
  KEY `idx_meet_id_start_time_end_time` (`meet_id`, `start_time`, `end_time`),
  KEY `idx_schedule_id_status` (`schedule_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='裁判分工表';

-- -----------------------------------------------------------------------------
-- 9. record 纪录表
-- 多层级纪录库（世界到组别），支持风速与是否现行标记，供成绩实时对标。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `record`;
CREATE TABLE `record` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`        BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `record_type`    VARCHAR(32)     NOT NULL COMMENT '纪录层级：WORLD世界/ASIA亚洲/NATION全国/PROVINCE省/CITY市/MEET赛会/GROUP组别',
  `event_id`       BIGINT UNSIGNED NULL     COMMENT '关联项目 event.id',
  `event_name`     VARCHAR(100)    NULL     COMMENT '项目名称（冗余显示）',
  `group_id`       BIGINT UNSIGNED NULL     COMMENT '关联组别 meet_group.id（组别纪录使用）',
  `group_code`     VARCHAR(64)     NULL     COMMENT '组别归并码（对应 meet_group.record_group_code）',
  `gender`         VARCHAR(32)     NULL     COMMENT '性别：MALE男/FEMALE女/MIXED混合',
  `is_relay`       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否接力项目纪录：0否/1是',
  `athlete_name`   VARCHAR(50)     NULL     COMMENT '纪录保持者姓名',
  `unit_name`      VARCHAR(100)    NULL     COMMENT '保持者单位/代表队',
  `region_name`    VARCHAR(100)    NULL     COMMENT '国家或地区（世界、亚洲、全国纪录）',
  `record_value`   DECIMAL(10,2)   NULL     COMMENT '纪录数值（秒、米，用于排序比较）',
  `record_raw`     VARCHAR(16)     NULL     COMMENT '纪录原文（显示，如 1:02.45）',
  `record_unit`    VARCHAR(32)     NULL     COMMENT '单位：s秒/m米/cm厘米/pt分',
  `wind_speed`     DECIMAL(10,2)   NULL     COMMENT '创造时风速（米/秒，正值顺风）',
  `is_wind_legal`  TINYINT(1)      NULL     COMMENT '风速是否合规：0不合规/1合规（未测风速为空）',
  `record_date`    DATE            NULL     COMMENT '创造日期',
  `record_place`   VARCHAR(100)    NULL     COMMENT '创造地点',
  `is_current`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否现行纪录：1现行/0历史纪录',
  `source`         VARCHAR(100)    NULL     COMMENT '数据来源（协会公布、组委会认定）',
  `create_by`      BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_meet_id_event_id_record_type` (`meet_id`, `event_id`, `record_type`),
  KEY `idx_meet_id_event_id_group_code_gender_is_current` (`meet_id`, `event_id`, `group_code`, `gender`, `is_current`),
  KEY `idx_meet_id_is_current` (`meet_id`, `is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='纪录表';

-- -----------------------------------------------------------------------------
-- 10. meet_rule_config 届次级规则表
-- 届次默认规则的结构化落点，作为「届次 → 项目 → 轮次」三层继承的最上层。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `meet_rule_config`;
CREATE TABLE `meet_rule_config` (
  `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`                 BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `admit_rank`              SMALLINT        NULL     COMMENT '默认录取名次（如 8 取前 8 名）',
  `score_rule_id`           BIGINT UNSIGNED NULL     COMMENT '默认名次分方案 score_rule.id',
  `athlete_max_events`      SMALLINT        NULL     COMMENT '每人限报项数（0 表示不限）',
  `relay_max_per_athlete`   SMALLINT        NULL     COMMENT '每人限报接力项数',
  `max_per_unit`            SMALLINT        NULL     COMMENT '每单位每项限报人数',
  `max_athlete_per_unit`    INT             NULL     COMMENT '每单位报名总人数上限',
  `conflict_interval_min`   SMALLINT        NULL     COMMENT '兼项最小间隔分钟数（冲突检测阈值）',
  `referee_interval_min`    SMALLINT        NULL     COMMENT '裁判连场最小间隔分钟数',
  `relay_double_factor`     DECIMAL(10,2)   NULL     COMMENT '接力得分双倍系数（默认 2.00）',
  `bonus_in_team_score`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '破纪录加分是否计入团体分：1计入/0不计入',
  `medal_sort`              VARCHAR(64)     NULL     COMMENT '奖牌榜排序：GOLD_FIRST金牌优先/TOTAL_FIRST奖牌总数优先/POINT_FIRST积分优先',
  `time_precision`          VARCHAR(32)     NULL     COMMENT '计时精度：HUNDREDTH百分秒/TENTH十分秒/SECOND秒',
  `field_attempt_count`     SMALLINT        NULL     COMMENT '田赛默认试跳/试掷次数（如 3 次）',
  `default_lane_count`      SMALLINT        NULL     COMMENT '默认道数（6 或 8）',
  `default_lane_order_json` JSON            NULL     COMMENT '默认道次顺序 JSON，8 道为 [4,5,3,6,2,7,1,8]',
  `lane_assign_mode`        VARCHAR(32)     NULL     COMMENT '默认道次分配方式：SEED按种子/DRAW抽签/SERPENT蛇形',
  `wind_json`               JSON            NULL     COMMENT '风速上限规则 JSON（各项目风速上限与超风速处理）',
  `tie_break_json`          JSON            NULL     COMMENT '默认平局规则 JSON（比较次优成绩、名次并列、抽签等顺序）',
  `extra_json`              JSON            NULL     COMMENT '届次规则扩展项 JSON（未结构化的默认值）',
  `remark`                  VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`               BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`                 INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_id` (`meet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='届次级规则表';

-- -----------------------------------------------------------------------------
-- 11. meet_clone_log 复制运动会留痕表
-- 记录「按模板复制新一届」的范围、结果与耗时，便于追溯与失败重试。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `meet_clone_log`;
CREATE TABLE `meet_clone_log` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`           BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `source_meet_id`    BIGINT UNSIGNED NULL     COMMENT '源届次 meet.id',
  `source_meet_name`  VARCHAR(100)    NULL     COMMENT '源届次名称（冗余显示）',
  `target_meet_id`    BIGINT UNSIGNED NULL     COMMENT '目标届次 meet.id',
  `target_meet_name`  VARCHAR(100)    NULL     COMMENT '目标届次名称（冗余显示）',
  `copy_mode`         VARCHAR(32)     NULL     COMMENT '复制方式：STRUCTURE仅结构/FULL全量含人员',
  `copy_scope_json`   JSON            NULL     COMMENT '复制范围 JSON（组别、单位、项目、规则、裁判、岗位字典）',
  `is_clear_target`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '复制前是否清空目标届次数据：0否/1是',
  `total_count`       INT             NULL     COMMENT '计划复制对象总数',
  `success_count`     INT             NULL     COMMENT '成功条数',
  `fail_count`        INT             NULL     COMMENT '失败条数',
  `status`            VARCHAR(32)     NULL     COMMENT '状态：RUNNING进行中/SUCCESS成功/PARTIAL部分成功/FAILED失败',
  `message`           VARCHAR(100)    NULL     COMMENT '结果说明或失败原因',
  `operator_id`       BIGINT UNSIGNED NULL     COMMENT '操作人 sys_user.id',
  `operator_name`     VARCHAR(50)     NULL     COMMENT '操作人姓名（冗余显示）',
  `start_time`        DATETIME        NULL     COMMENT '复制开始时间',
  `end_time`          DATETIME        NULL     COMMENT '复制结束时间',
  `cost_ms`           INT             NULL     COMMENT '耗时毫秒数',
  `create_by`         BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_target_meet_id` (`target_meet_id`),
  KEY `idx_source_meet_id` (`source_meet_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='复制运动会留痕表';

-- =============================================================================
-- 第 2 节 人员报名层（5 张）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 12. athlete 运动员表
-- 参赛的「人」：届次内的运动员档案，关联组别与参赛单位。
-- 逻辑删除时 bib_no 改写为「原值_d{id}」以保留唯一性。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `athlete`;
CREATE TABLE `athlete` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`             BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `athlete_name`        VARCHAR(50)     NOT NULL COMMENT '运动员姓名',
  `gender`              VARCHAR(32)     NOT NULL COMMENT '性别码：MALE男/FEMALE女',
  `birth_date`          DATE            NULL     COMMENT '出生日期，用于组别年龄校验',
  `id_card`             VARCHAR(32)     NOT NULL COMMENT '身份证/护照号，届次内唯一',
  `group_id`            BIGINT UNSIGNED NULL     COMMENT '所属组别 meet_group.id',
  `unit_id`             BIGINT UNSIGNED NULL     COMMENT '所属参赛单位 unit.id',
  `bib_no`              VARCHAR(32)     NULL     COMMENT '号码布编号，届次内唯一',
  `class_name`          VARCHAR(64)     NULL     COMMENT '班级/年级名称',
  `phone`               VARCHAR(32)     NULL     COMMENT '联系电话',
  `guardian`            VARCHAR(50)     NULL     COMMENT '监护人姓名（青少年赛事）',
  `guardian_phone`      VARCHAR(32)     NULL     COMMENT '监护人联系电话',
  `qualification_status` VARCHAR(32)    NOT NULL COMMENT '资格状态：PENDING待审/QUALIFIED合格/DISQUALIFIED不合格',
  `qualify_remark`      VARCHAR(100)    NULL     COMMENT '资格审核说明',
  `is_relay_only`       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否仅参加接力项目：0否/1是',
  `photo_file_id`       BIGINT UNSIGNED NULL     COMMENT '照片附件 sys_file.id',
  `entry_source`        VARCHAR(32)     NULL     COMMENT '数据来源：MANUAL手工录入/IMPORT导入',
  `remark`              VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`           BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_id_id_card` (`meet_id`, `id_card`),
  UNIQUE KEY `uk_meet_id_bib_no` (`meet_id`, `bib_no`),
  KEY `idx_meet_id_unit_id_group_id` (`meet_id`, `unit_id`, `group_id`),
  KEY `idx_qualification_status` (`qualification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运动员表';

-- -----------------------------------------------------------------------------
-- 13. registration 报名表
-- 运动员 × 项目的报名关系：个人项目 1 行 1 人，接力项目 1 行 N 人（队员存 relay_member）。
-- 个人项目唯一键 uk_meet_id_event_id_athlete_id；接力项目唯一键
-- uk_meet_id_event_id_unit_id_relay_team_seq（两类各自成立：另一类的键列取值为 NULL，
-- MySQL 唯一索引允许多个 NULL 值）。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `registration`;
CREATE TABLE `registration` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`             BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `event_id`            BIGINT UNSIGNED NOT NULL COMMENT '报名项目 event.id',
  `athlete_id`          BIGINT UNSIGNED NULL     COMMENT '个人项目运动员 ID，接力为空',
  `unit_id`             BIGINT UNSIGNED NOT NULL COMMENT '报名单位 ID',
  `group_id`            BIGINT UNSIGNED NULL     COMMENT '报名组别 ID',
  `round_scope`         VARCHAR(32)     NULL     COMMENT '报名范围：WHOLE全程/FINAL_ONLY只报决赛',
  `is_relay`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否接力项目报名：0否/1是',
  `relay_name`          VARCHAR(100)    NULL     COMMENT '接力队名（如「XX 单位 A 队」）',
  `relay_team_seq`      SMALLINT        NULL     COMMENT '同单位同项目接力队序号',
  `seed_result`         VARCHAR(16)     NULL     COMMENT '种子成绩原文，如 11"23、1:02.45',
  `seed_value`          DECIMAL(10,2)   NULL     COMMENT '种子成绩可比较数值（秒/米）',
  `seed_result_unit`    VARCHAR(32)     NULL     COMMENT '种子成绩单位：s秒/m米/cm厘米/pt分',
  `seed_source`         VARCHAR(32)     NULL     COMMENT '种子来源：PB个人最好/SB赛季最好/LAST_MEET上届成绩/CUSTOM手工录入',
  `seed_source_meet_id` BIGINT UNSIGNED NULL     COMMENT '上届成绩来源届次 meet.id',
  `seed_time`           DATETIME        NULL     COMMENT '种子成绩产生时间',
  `status`              VARCHAR(32)     NOT NULL COMMENT '状态：PENDING待审/APPROVED通过/REJECTED退回/CANCELLED取消',
  `reject_reason`       VARCHAR(200)    NULL     COMMENT '退回原因（≤200）',
  `check_result_json`   JSON            NULL     COMMENT '限报校验快照 JSON：超项/超人数/重复/组别不符',
  `submit_time`         DATETIME        NULL     COMMENT '报名提交时间',
  `audit_by`            BIGINT UNSIGNED NULL     COMMENT '审核人 sys_user.id',
  `audit_time`          DATETIME        NULL     COMMENT '审核时间',
  `client_token`        VARCHAR(64)     NULL     COMMENT '幂等令牌，防重复提交',
  `remark`              VARCHAR(200)    NULL     COMMENT '备注（≤200）',
  `create_by`           BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_id_event_id_athlete_id` (`meet_id`, `event_id`, `athlete_id`),
  UNIQUE KEY `uk_meet_id_event_id_unit_id_relay_team_seq` (`meet_id`, `event_id`, `unit_id`, `relay_team_seq`),
  KEY `idx_meet_id_unit_id_status` (`meet_id`, `unit_id`, `status`),
  KEY `idx_status` (`status`),
  KEY `idx_event_id_status` (`event_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报名表';

-- -----------------------------------------------------------------------------
-- 14. relay_member 接力队员表
-- 接力报名下的队员与棒次明细。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `relay_member`;
CREATE TABLE `relay_member` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`         BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `registration_id` BIGINT UNSIGNED NOT NULL COMMENT '接力报名记录 registration.id',
  `athlete_id`      BIGINT UNSIGNED NOT NULL COMMENT '队员运动员 ID',
  `athlete_name`    VARCHAR(50)     NULL     COMMENT '队员姓名冗余，便于打印',
  `leg_no`          SMALLINT        NOT NULL COMMENT '棒次号，1 起',
  `unit_id`         BIGINT UNSIGNED NULL     COMMENT '队员所属单位 ID',
  `group_id`        BIGINT UNSIGNED NULL     COMMENT '队员所属组别 ID',
  `bib_no`          VARCHAR(32)     NULL     COMMENT '队员号码布（冗余快照）',
  `is_reserve`      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否替补队员：0否/1是',
  `status`          VARCHAR(32)     NULL     COMMENT '状态：NORMAL正常/CHANGED已换人/CANCELLED已取消',
  `change_reason`   VARCHAR(100)    NULL     COMMENT '换人/调整原因',
  `remark`          VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`       BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_id_leg_no` (`registration_id`, `leg_no`),
  KEY `idx_athlete_id` (`athlete_id`),
  KEY `idx_registration_id_is_reserve` (`registration_id`, `is_reserve`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接力队员表';

-- -----------------------------------------------------------------------------
-- 15. registration_limit 报名限制计数表
-- 按「单位 × 项目」维护限报快照与实时计数，供报名校验与列表展示。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `registration_limit`;
CREATE TABLE `registration_limit` (
  `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`                    BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `unit_id`                    BIGINT UNSIGNED NOT NULL COMMENT '参赛单位 ID',
  `event_id`                   BIGINT UNSIGNED NOT NULL COMMENT '项目 ID',
  `group_id`                   BIGINT UNSIGNED NULL     COMMENT '组别 ID',
  `registered_count`           INT             NULL     COMMENT '本单位该项目已报人数',
  `max_count`                  INT             NULL     COMMENT '每单位每项限报人数（生效值）',
  `relay_registered_count`     INT             NULL     COMMENT '已报接力队数',
  `relay_max_count`            INT             NULL     COMMENT '接力限报队数',
  `remain_count`               INT             NULL     COMMENT '剩余可报人数',
  `is_full`                    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已达上限：0否/1是',
  `over_limit`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否超限（含已批准超额）：0否/1是',
  `athlete_event_count_json`   JSON            NULL     COMMENT '单位内各运动员项数明细快照 JSON',
  `limit_source`               VARCHAR(32)     NULL     COMMENT '限值来源：MEET届次/EVENT项目/DICT字典',
  `limit_check_time`           DATETIME        NULL     COMMENT '最近一次校验时间',
  `remark`                     VARCHAR(100)    NULL     COMMENT '备注',
  `create_by`                  BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                  BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`                    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`                    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_id_unit_id_event_id` (`meet_id`, `unit_id`, `event_id`),
  KEY `idx_meet_id_event_id_is_full` (`meet_id`, `event_id`, `is_full`),
  KEY `idx_unit_id_group_id` (`unit_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报名限制计数表';

-- -----------------------------------------------------------------------------
-- 16. athlete_event_count 每人项数计数表
-- 按运动员维护已报项数与超项标记，承载「每人限报项数」校验。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `athlete_event_count`;
CREATE TABLE `athlete_event_count` (
  `id`                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `meet_id`                BIGINT UNSIGNED NOT NULL COMMENT '届次ID',
  `athlete_id`             BIGINT UNSIGNED NOT NULL COMMENT '运动员 ID',
  `unit_id`                BIGINT UNSIGNED NULL     COMMENT '所属单位 ID',
  `group_id`               BIGINT UNSIGNED NULL     COMMENT '所属组别 ID',
  `event_count`            INT             NULL     COMMENT '已报项数合计',
  `individual_event_count` INT             NULL     COMMENT '个人项目项数',
  `relay_event_count`      INT             NULL     COMMENT '接力项目项数',
  `allround_event_count`   INT             NULL     COMMENT '全能项目项数',
  `max_events`             INT             NULL     COMMENT '每人限报项数上限（生效值）',
  `remain_count`           INT             NULL     COMMENT '剩余可报项数',
  `is_relay_counted`       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '接力是否计入限报项数：0不计入/1计入',
  `over_limit`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否超项：0否/1是',
  `event_ids_json`         JSON            NULL     COMMENT '已报项目 ID 明细快照 JSON',
  `count_source`           VARCHAR(32)     NULL     COMMENT '上限来源：MEET届次/EVENT项目/DICT字典',
  `last_register_time`     DATETIME        NULL     COMMENT '最近一次报名时间',
  `check_time`             DATETIME        NULL     COMMENT '计数刷新时间',
  `create_by`              BIGINT UNSIGNED NULL     COMMENT '创建人 sys_user.id',
  `create_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              BIGINT UNSIGNED NULL     COMMENT '修改人 sys_user.id',
  `update_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`                TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  `version`                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_athlete_id` (`athlete_id`),
  KEY `idx_meet_id_over_limit` (`meet_id`, `over_limit`),
  KEY `idx_unit_id_group_id` (`unit_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每人项数计数表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 脚本结束：共 16 张表（届次基础层 11 张 + 人员报名层 5 张）
-- =============================================================================
