-- ============================================================================
-- 田径运动会编排软件 TrackMeet —— 10 验收演示数据集（阶段 6）
-- 前置：必须先执行 01/02/03/04 建表、05-init-data.sql、06-demo-data.sql
-- 内容：在 06 的示例届次（meet_id=1）上补齐一条**完整业务链路**：
--       场地 3 → 运动员 +8（共 24）→ 项目 12 → 轮次 14 → 报名 46
--       → 竞赛单元 3 → 日程 14 → 分组 6 / 组内成员 30 / 道次 30 / 检录 30
--       → 成绩 58（含 DNS/DSQ/超风速/破纪录）→ 试跳 42 → 风速 2
--       → 积分明细（由成绩聚合派生）→ 团体总分（男/女/总三档）→ 奖牌榜
--       → 赛会纪录 2 → 公告 3 → 打印模板 2 → 大屏配置 1
-- 说明：① 脚本开头清理本脚本产生的数据，可安全重复执行；
--       ② 积分/团体/奖牌榜用 INSERT ... SELECT 聚合派生，保证与成绩严格自洽；
--       ③ 仅用于演示与验收，生产环境请勿导入。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 0. 清理本脚本产生的数据（可重复执行）
-- ----------------------------------------------------------------------------
DELETE FROM `result_attempt`   WHERE `meet_id` = 1;
DELETE FROM `result`           WHERE `meet_id` = 1;
DELETE FROM `wind_reading`     WHERE `meet_id` = 1;
DELETE FROM `calling`          WHERE `meet_id` = 1;
DELETE FROM `lane`             WHERE `meet_id` = 1;
DELETE FROM `heat_member`      WHERE `meet_id` = 1;
DELETE FROM `heat`             WHERE `meet_id` = 1;
DELETE FROM `start_list`       WHERE `meet_id` = 1;
DELETE FROM `schedule`         WHERE `meet_id` = 1;
DELETE FROM `schedule_session` WHERE `meet_id` = 1;
DELETE FROM `registration`     WHERE `meet_id` = 1;
DELETE FROM `event_round`      WHERE `meet_id` = 1;
DELETE FROM `event`            WHERE `meet_id` = 1;
DELETE FROM `venue`            WHERE `meet_id` = 1;
DELETE FROM `points`           WHERE `meet_id` = 1;
DELETE FROM `team_score`       WHERE `meet_id` = 1;
DELETE FROM `medal_standing`   WHERE `meet_id` = 1;
DELETE FROM `record`           WHERE `meet_id` = 1;
DELETE FROM `announcement`     WHERE `meet_id` = 1;
DELETE FROM `print_template`   WHERE `meet_id` = 1;
DELETE FROM `screen_config`    WHERE `meet_id` = 1;
DELETE FROM `athlete`          WHERE `meet_id` = 1 AND `id` BETWEEN 17 AND 24;

-- ----------------------------------------------------------------------------
-- 1. 场地（径赛主场地 + 2 个田赛区）
-- ----------------------------------------------------------------------------
INSERT INTO `venue` (`id`, `meet_id`, `venue_name`, `venue_code`, `venue_type`, `lane_count`, `lane_order_json`,
                     `lane_width`, `location`, `capacity`, `is_wind_measured`, `is_electric_timing`,
                     `field_zone_json`, `contact_name`, `contact_phone`, `status`, `remark`)
VALUES (1, 1, '主田径场', 'V_TRACK', 'TRACK', 8, CAST('[4,5,3,6,2,7,1,8]' AS JSON), 1.22,
        '体育中心东侧', 3000, 1, 1, NULL, '场地方', '13800000010', 'ENABLED', '400 米标准 8 道，电子计时'),
       (2, 1, '田赛区 A（跳跃）', 'V_FIELD_A', 'FIELD', 0, NULL, NULL,
        '体育中心内场北侧', 500, 1, 0, CAST('["LONG_JUMP","TRIPLE_JUMP","HIGH_JUMP"]' AS JSON),
        '场地方', '13800000011', 'ENABLED', '跳远/三级跳/跳高'),
       (3, 1, '田赛区 B（投掷）', 'V_FIELD_B', 'FIELD', 0, NULL, NULL,
        '体育中心内场南侧', 300, 1, 0, CAST('["SHOT_PUT","DISCUS","JAVELIN"]' AS JSON),
        '场地方', '13800000012', 'ENABLED', '铅球/铁饼/标枪');

-- ----------------------------------------------------------------------------
-- 2. 追加运动员 8 名（17~24，用于凑满 8~10 人的 100 米分组）
-- ----------------------------------------------------------------------------
INSERT INTO `athlete` (`id`, `meet_id`, `athlete_name`, `gender`, `birth_date`, `id_card`, `group_id`, `unit_id`,
                       `bib_no`, `class_name`, `phone`, `guardian`, `guardian_phone`, `qualification_status`,
                       `qualify_remark`, `is_relay_only`, `entry_source`, `remark`, `create_by`)
VALUES (17, 1, '孙宇航', 'MALE',   '2008-04-08', '110101200804080177', 1, 1, '一中-004', '高二(4)班', '13900000017', '孙父', '13700000017', 'QUALIFIED', '演示补充', 0, 'MANUAL', NULL, 1),
       (18, 1, '马俊杰', 'MALE',   '2009-02-26', '110101200902260188', 1, 1, '一中-005', '高一(4)班', '13900000018', '马母', '13700000018', 'QUALIFIED', '演示补充', 0, 'MANUAL', NULL, 1),
       (19, 1, '朱天翔', 'MALE',   '2008-10-14', '110101200810140199', 1, 2, '二中-003', '高二(5)班', '13900000019', '朱父', '13700000019', 'QUALIFIED', '演示补充', 0, 'MANUAL', NULL, 1),
       (20, 1, '胡文凯', 'MALE',   '2009-07-03', '110101200907030200', 1, 2, '二中-004', '高一(5)班', '13900000020', '胡母', '13700000020', 'QUALIFIED', '演示补充', 0, 'MANUAL', NULL, 1),
       (21, 1, '林晓萱', 'FEMALE', '2008-06-11', '110101200806110211', 2, 1, '一中-103', '高二(5)班', '13900000021', '林父', '13700000021', 'QUALIFIED', '演示补充', 0, 'MANUAL', NULL, 1),
       (22, 1, '徐若涵', 'FEMALE', '2009-03-19', '110101200903190222', 2, 1, '一中-104', '高一(6)班', '13900000022', '徐母', '13700000022', 'QUALIFIED', '演示补充', 0, 'MANUAL', NULL, 1),
       (23, 1, '高梦琪', 'FEMALE', '2008-08-30', '110101200808300233', 2, 2, '二中-102', '高二(6)班', '13900000023', '高父', '13700000023', 'QUALIFIED', '演示补充', 0, 'MANUAL', NULL, 1),
       (24, 1, '谢雨萱', 'FEMALE', '2009-12-22', '110101200912220244', 2, 2, '二中-103', '高一(7)班', '13900000024', '谢母', '13700000024', 'QUALIFIED', '演示补充', 0, 'MANUAL', NULL, 1);

-- ----------------------------------------------------------------------------
-- 3. 项目 12 个（4 组别 × 径赛/田赛/接力）
-- ----------------------------------------------------------------------------
INSERT INTO `event` (`id`, `meet_id`, `event_name`, `event_code`, `event_type`, `sub_type`, `group_id`, `gender`,
                     `round_count`, `is_relay`, `relay_min`, `relay_max`, `max_per_unit`, `is_relay_double`,
                     `wind_applicable`, `is_team_score`, `duration_min`, `score_rule_id`, `unit_of_measure`,
                     `result_precision`, `sort_no`, `status`, `remark`)
VALUES (1,  1, '高中男子 100 米',    'M100',    'TRACK', 'SPRINT', 1, 'MALE',   2, 0, NULL, NULL, 3, 0, 1, 1, 25, 1, 's', 'HUNDREDTH',   1, 'ENABLED', '预赛 + 决赛'),
       (2,  1, '高中男子 400 米',    'M400',    'TRACK', 'MIDDLE', 1, 'MALE',   1, 0, NULL, NULL, 3, 0, 0, 1, 20, 1, 's', 'HUNDREDTH',   2, 'ENABLED', '一次决赛'),
       (3,  1, '高中男子跳远',       'MLJ',     'FIELD', 'JUMP',   1, 'MALE',   1, 0, NULL, NULL, 3, 0, 1, 1, 45, 1, 'm', 'CENTIMETER',  3, 'ENABLED', '6 次试跳'),
       (4,  1, '高中男子铅球',       'MSP',     'FIELD', 'THROW',  1, 'MALE',   1, 0, NULL, NULL, 2, 0, 0, 1, 40, 1, 'm', 'CENTIMETER',  4, 'ENABLED', '5 公斤'),
       (5,  1, '高中男子 4×100 米接力', 'M4X100', 'RELAY', 'RELAY', 1, 'MALE',   1, 1, 4, 4, 1, 1, 0, 1, 20, 2, 's', 'HUNDREDTH', 5, 'ENABLED', '接力双倍计分'),
       (6,  1, '高中女子 100 米',    'W100',    'TRACK', 'SPRINT', 2, 'FEMALE', 2, 0, NULL, NULL, 3, 0, 1, 1, 25, 1, 's', 'HUNDREDTH',   6, 'ENABLED', '预赛 + 决赛'),
       (7,  1, '高中女子跳高',       'WHJ',     'FIELD', 'JUMP',   2, 'FEMALE', 1, 0, NULL, NULL, 3, 0, 0, 1, 45, 1, 'm', 'CENTIMETER',  7, 'ENABLED', '6 次试跳'),
       (8,  1, '高中女子 4×100 米接力', 'W4X100', 'RELAY', 'RELAY', 2, 'FEMALE', 1, 1, 4, 4, 1, 1, 0, 1, 20, 2, 's', 'HUNDREDTH', 8, 'ENABLED', '接力双倍计分'),
       (9,  1, '初中男子 100 米',    'JM100',   'TRACK', 'SPRINT', 3, 'MALE',   1, 0, NULL, NULL, 3, 0, 1, 1, 20, 1, 's', 'HUNDREDTH',   9, 'ENABLED', NULL),
       (10, 1, '初中男子跳远',       'JMLJ',    'FIELD', 'JUMP',   3, 'MALE',   1, 0, NULL, NULL, 3, 0, 1, 1, 40, 1, 'm', 'CENTIMETER', 10, 'ENABLED', NULL),
       (11, 1, '初中女子 100 米',    'JW100',   'TRACK', 'SPRINT', 4, 'FEMALE', 1, 0, NULL, NULL, 3, 0, 1, 1, 20, 1, 's', 'HUNDREDTH',  11, 'ENABLED', NULL),
       (12, 1, '初中女子跳高',       'JWHJ',    'FIELD', 'JUMP',   4, 'FEMALE', 1, 0, NULL, NULL, 3, 0, 0, 1, 40, 1, 'm', 'CENTIMETER', 12, 'ENABLED', NULL);

-- ----------------------------------------------------------------------------
-- 4. 轮次 14 个（100 米为 预赛 + 决赛，其余一次决赛）
-- ----------------------------------------------------------------------------
INSERT INTO `event_round` (`id`, `meet_id`, `event_id`, `round_code`, `round_name`, `round_no`, `is_final`,
                           `plan_heat_count`, `plan_heat_size`, `plan_advance_count`, `plan_advance_group_top`,
                           `plan_best_loser_count`, `entry_count`, `round_date`, `status`, `remark`)
VALUES (1,  1, 1,  'PRE',   '预赛', 1, 0, 2, 8, 8, 3, 2, 10, '2025-10-18', 'FINISHED', '2 组，每组前 3 + 递补 2'),
       (2,  1, 1,  'FINAL', '决赛', 2, 1, 1, 8, 0, 0, 0,  6, '2025-10-18', 'FINISHED', NULL),
       (3,  1, 6,  'PRE',   '预赛', 1, 0, 2, 8, 8, 3, 2,  8, '2025-10-18', 'FINISHED', '2 组，每组前 3 + 递补 2'),
       (4,  1, 6,  'FINAL', '决赛', 2, 1, 1, 8, 0, 0, 0,  6, '2025-10-18', 'FINISHED', NULL),
       (5,  1, 2,  'FINAL', '决赛', 1, 1, 1, 8, 0, 0, 0,  4, '2025-10-18', 'FINISHED', NULL),
       (6,  1, 3,  'FINAL', '决赛', 1, 1, 0, 0, 0, 0, 0,  4, '2025-10-18', 'FINISHED', '田赛一次决赛'),
       (7,  1, 4,  'FINAL', '决赛', 1, 1, 0, 0, 0, 0, 0,  2, '2025-10-18', 'FINISHED', NULL),
       (8,  1, 5,  'FINAL', '决赛', 1, 1, 1, 8, 0, 0, 0,  3, '2025-10-18', 'FINISHED', '接力决赛'),
       (9,  1, 7,  'FINAL', '决赛', 1, 1, 0, 0, 0, 0, 0,  3, '2025-10-18', 'FINISHED', NULL),
       (10, 1, 8,  'FINAL', '决赛', 1, 1, 1, 8, 0, 0, 0,  2, '2025-10-18', 'FINISHED', '接力决赛'),
       (11, 1, 9,  'FINAL', '决赛', 1, 1, 1, 8, 0, 0, 0,  3, '2025-10-19', 'FINISHED', NULL),
       (12, 1, 10, 'FINAL', '决赛', 1, 1, 0, 0, 0, 0, 0,  2, '2025-10-19', 'FINISHED', NULL),
       (13, 1, 11, 'FINAL', '决赛', 1, 1, 1, 8, 0, 0, 0,  3, '2025-10-19', 'FINISHED', NULL),
       (14, 1, 12, 'FINAL', '决赛', 1, 1, 0, 0, 0, 0, 0,  2, '2025-10-19', 'FINISHED', NULL);

-- ----------------------------------------------------------------------------
-- 5. 报名 46 条（状态 APPROVED；含 5 支接力队；每人单项不超过 2 项）
-- ----------------------------------------------------------------------------
INSERT INTO `registration` (`id`, `meet_id`, `event_id`, `athlete_id`, `unit_id`, `group_id`, `is_relay`,
                            `relay_name`, `relay_team_seq`, `seed_result`, `seed_value`, `seed_result_unit`,
                            `seed_source`, `status`, `submit_time`, `audit_by`, `audit_time`, `remark`)
VALUES
-- 高中男子 100 米（10 人，2 组）
(1,  1, 1, 1,  1, 1, 0, NULL, NULL, '11"20', 11.20, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(2,  1, 1, 2,  1, 1, 0, NULL, NULL, '11"45', 11.45, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(3,  1, 1, 3,  1, 1, 0, NULL, NULL, '11"80', 11.80, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(4,  1, 1, 6,  2, 1, 0, NULL, NULL, '11"32', 11.32, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(5,  1, 1, 7,  2, 1, 0, NULL, NULL, '11"60', 11.60, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(6,  1, 1, 9,  3, 1, 0, NULL, NULL, '11"52', 11.52, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(7,  1, 1, 17, 1, 1, 0, NULL, NULL, '11"28', 11.28, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(8,  1, 1, 18, 1, 1, 0, NULL, NULL, '11"70', 11.70, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(9,  1, 1, 19, 2, 1, 0, NULL, NULL, '11"38', 11.38, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(10, 1, 1, 20, 2, 1, 0, NULL, NULL, '11"90', 11.90, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 高中男子 400 米（4 人）
(11, 1, 2, 1,  1, 1, 0, NULL, NULL, '52"10', 52.10, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(12, 1, 2, 6,  2, 1, 0, NULL, NULL, '51"80', 51.80, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(13, 1, 2, 9,  3, 1, 0, NULL, NULL, '53"40', 53.40, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(14, 1, 2, 17, 1, 1, 0, NULL, NULL, '52"60', 52.60, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 高中男子跳远（4 人）
(15, 1, 3, 2,  1, 1, 0, NULL, NULL, '6.15', 6.15, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(16, 1, 3, 7,  2, 1, 0, NULL, NULL, '6.48', 6.48, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(17, 1, 3, 18, 1, 1, 0, NULL, NULL, '5.92', 5.92, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(18, 1, 3, 19, 2, 1, 0, NULL, NULL, '6.30', 6.30, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 高中男子铅球（2 人）
(19, 1, 4, 3,  1, 1, 0, NULL, NULL, '12.30', 12.30, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(20, 1, 4, 20, 2, 1, 0, NULL, NULL, '11.80', 11.80, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 高中男子 4×100 米接力（3 队）
(21, 1, 5, 1, 1, 1, 1, '示例市第一中学', 1, '45"20', 45.20, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), '4 人接力'),
(22, 1, 5, 6, 2, 1, 1, '示例市第二中学', 1, '46"10', 46.10, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), '4 人接力'),
(23, 1, 5, 9, 3, 1, 1, '示例市实验中学', 1, '45"80', 45.80, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), '4 人接力'),
-- 高中女子 100 米（8 人，2 组）
(24, 1, 6, 4,  1, 2, 0, NULL, NULL, '12"80', 12.80, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(25, 1, 6, 5,  1, 2, 0, NULL, NULL, '13"10', 13.10, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(26, 1, 6, 8,  2, 2, 0, NULL, NULL, '12"95', 12.95, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(27, 1, 6, 10, 3, 2, 0, NULL, NULL, '13"25', 13.25, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(28, 1, 6, 21, 1, 2, 0, NULL, NULL, '12"70', 12.70, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(29, 1, 6, 22, 1, 2, 0, NULL, NULL, '13"40', 13.40, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(30, 1, 6, 23, 2, 2, 0, NULL, NULL, '13"05', 13.05, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(31, 1, 6, 24, 2, 2, 0, NULL, NULL, '13"55', 13.55, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 高中女子跳高（3 人）
(32, 1, 7, 4,  1, 2, 0, NULL, NULL, '1.45', 1.45, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(33, 1, 7, 8,  2, 2, 0, NULL, NULL, '1.52', 1.52, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(34, 1, 7, 21, 1, 2, 0, NULL, NULL, '1.48', 1.48, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 高中女子 4×100 米接力（2 队）
(35, 1, 8, 4, 1, 2, 1, '示例市第一中学', 1, '52"30', 52.30, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), '4 人接力'),
(36, 1, 8, 8, 2, 2, 1, '示例市第二中学', 1, '53"10', 53.10, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), '4 人接力'),
-- 初中男子 100 米（3 人）
(37, 1, 9, 11, 3, 3, 0, NULL, NULL, '12"40', 12.40, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(38, 1, 9, 13, 4, 3, 0, NULL, NULL, '12"75', 12.75, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(39, 1, 9, 15, 4, 3, 0, NULL, NULL, '13"05', 13.05, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 初中男子跳远（2 人）
(40, 1, 10, 11, 3, 3, 0, NULL, NULL, '5.30', 5.30, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(41, 1, 10, 13, 4, 3, 0, NULL, NULL, '5.05', 5.05, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 初中女子 100 米（3 人）
(42, 1, 11, 12, 3, 4, 0, NULL, NULL, '13"60', 13.60, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(43, 1, 11, 14, 4, 4, 0, NULL, NULL, '13"85', 13.85, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(44, 1, 11, 16, 4, 4, 0, NULL, NULL, '14"10', 14.10, 's', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
-- 初中女子跳高（2 人）
(45, 1, 12, 12, 3, 4, 0, NULL, NULL, '1.30', 1.30, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL),
(46, 1, 12, 14, 4, 4, 0, NULL, NULL, '1.25', 1.25, 'm', 'MEET', 'APPROVED', NOW(), 1, NOW(), NULL);

-- ----------------------------------------------------------------------------
-- 6. 竞赛单元 3 个
-- ----------------------------------------------------------------------------
INSERT INTO `schedule_session` (`id`, `meet_id`, `session_name`, `session_date`, `day_no`, `period`,
                                `start_time`, `end_time`, `venue_id`, `venue_ids_json`, `total_minutes`,
                                `used_minutes`, `item_count`, `is_locked`, `publish_status`, `publish_time`,
                                `publisher_id`, `sort_no`, `batch_no`, `remark`)
VALUES (1, 1, '第一天上午', '2025-10-18', 1, 'MORNING',   '08:00:00', '12:00:00', 1,
        CAST('[1,2,3]' AS JSON), 240, 190, 6, 1, 'PUBLISHED', NOW(), 1, 1, 'DEMO-BATCH-1', '含 100 米预赛与田赛'),
       (2, 1, '第一天下午', '2025-10-18', 1, 'AFTERNOON', '14:00:00', '18:00:00', 1,
        CAST('[1,2,3]' AS JSON), 240, 70, 4, 0, 'PUBLISHED', NOW(), 1, 2, 'DEMO-BATCH-1', '100 米决赛与接力'),
       (3, 1, '第二天上午', '2025-10-19', 2, 'MORNING',   '08:00:00', '12:00:00', 1,
        CAST('[1,2,3]' AS JSON), 240, 80, 4, 0, 'DRAFT', NULL, NULL, 3, 'DEMO-BATCH-2', '初中组项目');

-- ----------------------------------------------------------------------------
-- 7. 日程 14 条（与轮次一一对应）
-- ----------------------------------------------------------------------------
INSERT INTO `schedule` (`id`, `meet_id`, `session_id`, `event_id`, `round_id`, `venue_id`,
                        `plan_start_time`, `plan_end_time`, `duration_min`, `heat_count`, `member_count`,
                        `order_no`, `source_type`, `batch_no`, `publish_status`, `publish_time`, `publisher_id`, `remark`)
VALUES (1,  1, 1, 1,  1,  1, '2025-10-18 08:00:00', '2025-10-18 08:20:00', 20, 2, 10, 1, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, NULL),
       (2,  1, 1, 2,  5,  1, '2025-10-18 08:20:00', '2025-10-18 08:40:00', 20, 1, 4,  2, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, NULL),
       (3,  1, 1, 3,  6,  2, '2025-10-18 08:00:00', '2025-10-18 08:45:00', 45, 0, 4,  3, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, NULL),
       (4,  1, 1, 4,  7,  3, '2025-10-18 08:00:00', '2025-10-18 08:40:00', 40, 0, 2,  4, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, NULL),
       (5,  1, 1, 6,  3,  1, '2025-10-18 08:40:00', '2025-10-18 09:00:00', 20, 2, 8,  5, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, NULL),
       (6,  1, 1, 7,  9,  2, '2025-10-18 08:45:00', '2025-10-18 09:30:00', 45, 0, 3,  6, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, NULL),
       (7,  1, 2, 1,  2,  1, '2025-10-18 14:00:00', '2025-10-18 14:15:00', 15, 1, 6,  1, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, '高中男子 100 米决赛'),
       (8,  1, 2, 5,  8,  1, '2025-10-18 14:15:00', '2025-10-18 14:35:00', 20, 1, 3,  2, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, '高中男子接力决赛'),
       (9,  1, 2, 6,  4,  1, '2025-10-18 14:35:00', '2025-10-18 14:50:00', 15, 1, 6,  3, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, '高中女子 100 米决赛'),
       (10, 1, 2, 8,  10, 1, '2025-10-18 14:50:00', '2025-10-18 15:10:00', 20, 1, 2,  4, 'AUTO', 'DEMO-BATCH-1', 'PUBLISHED', NOW(), 1, '高中女子接力决赛'),
       (11, 1, 3, 9,  11, 1, '2025-10-19 08:00:00', '2025-10-19 08:20:00', 20, 1, 3,  1, 'MANUAL', NULL, 'DRAFT', NULL, NULL, NULL),
       (12, 1, 3, 10, 12, 2, '2025-10-19 08:00:00', '2025-10-19 08:40:00', 40, 0, 2,  2, 'MANUAL', NULL, 'DRAFT', NULL, NULL, NULL),
       (13, 1, 3, 11, 13, 1, '2025-10-19 08:20:00', '2025-10-19 08:40:00', 20, 1, 3,  3, 'MANUAL', NULL, 'DRAFT', NULL, NULL, NULL),
       (14, 1, 3, 12, 14, 2, '2025-10-19 08:40:00', '2025-10-19 09:20:00', 40, 0, 2,  4, 'MANUAL', NULL, 'DRAFT', NULL, NULL, NULL);

-- ----------------------------------------------------------------------------
-- 8. 分组 6 个（两个 100 米预赛各 2 组 + 两个决赛各 1 组）
-- ----------------------------------------------------------------------------
INSERT INTO `heat` (`id`, `meet_id`, `schedule_id`, `heat_no`, `heat_name`, `lane_count`, `member_count`,
                    `seed_count`, `call_time`, `call_place`, `start_time`, `end_time`, `status`,
                    `is_locked`, `source_type`, `batch_no`, `draw_mode`, `remark`)
VALUES (1, 1, 1, 1, '高中男子 100 米预赛 第一组', 8, 5, 5, '2025-10-18 07:30:00', '检录处 A', '2025-10-18 08:00:00', '2025-10-18 08:05:00', 'FINISHED', 1, 'AUTO', 'DEMO-BATCH-1', 'SERPENT', NULL),
       (2, 1, 1, 2, '高中男子 100 米预赛 第二组', 8, 5, 5, '2025-10-18 07:40:00', '检录处 A', '2025-10-18 08:05:00', '2025-10-18 08:10:00', 'FINISHED', 1, 'AUTO', 'DEMO-BATCH-1', 'SERPENT', NULL),
       (3, 1, 5, 1, '高中女子 100 米预赛 第一组', 8, 4, 4, '2025-10-18 08:10:00', '检录处 A', '2025-10-18 08:40:00', '2025-10-18 08:45:00', 'FINISHED', 1, 'AUTO', 'DEMO-BATCH-1', 'SERPENT', NULL),
       (4, 1, 5, 2, '高中女子 100 米预赛 第二组', 8, 4, 4, '2025-10-18 08:20:00', '检录处 A', '2025-10-18 08:45:00', '2025-10-18 08:50:00', 'FINISHED', 1, 'AUTO', 'DEMO-BATCH-1', 'SERPENT', NULL),
       (5, 1, 7, 1, '高中男子 100 米决赛',       8, 6, 6, '2025-10-18 13:30:00', '检录处 A', '2025-10-18 14:00:00', '2025-10-18 14:05:00', 'FINISHED', 1, 'AUTO', 'DEMO-BATCH-1', 'SEED',    NULL),
       (6, 1, 9, 1, '高中女子 100 米决赛',       8, 6, 6, '2025-10-18 14:05:00', '检录处 A', '2025-10-18 14:35:00', '2025-10-18 14:40:00', 'FINISHED', 1, 'AUTO', 'DEMO-BATCH-1', 'SEED',    NULL);

-- 组内成员：预赛按种子成绩蛇形交替分组（奇数名次→第 1 组、偶数名次→第 2 组）
INSERT INTO `heat_member` (`meet_id`, `heat_id`, `schedule_id`, `registration_id`, `athlete_id`, `unit_id`,
                           `bib_no`, `group_id`, `order_no`, `is_seed`, `status`, `seed_result`,
                           `seed_result_raw`, `seed_result_unit`, `is_locked`, `batch_no`)
SELECT 1, h.id, h.schedule_id, r.id, r.athlete_id, r.unit_id, a.bib_no, r.group_id,
       ROW_NUMBER() OVER (PARTITION BY h.id ORDER BY r.seed_value, r.id),
       1, 'PRESENT', r.seed_result, r.seed_result, r.seed_result_unit, 0, 'DEMO-BATCH-1'
FROM (
    SELECT r.*,
           CASE WHEN MOD(ROW_NUMBER() OVER (ORDER BY r.seed_value, r.id) - 1, 2) = 0 THEN 1 ELSE 2 END AS group_no
    FROM `registration` r
    WHERE r.meet_id = 1 AND r.event_id IN (1, 6) AND r.deleted = 0
) r
JOIN `heat` h ON h.meet_id = 1 AND h.schedule_id = IF(r.event_id = 1, 1, 5) AND h.heat_no = r.group_no
JOIN `athlete` a ON a.id = r.athlete_id AND a.deleted = 0;

-- 决赛分组：按预赛名次取前 6 名（此处直接按种子成绩取前 6）
INSERT INTO `heat_member` (`meet_id`, `heat_id`, `schedule_id`, `registration_id`, `athlete_id`, `unit_id`,
                           `bib_no`, `group_id`, `order_no`, `is_seed`, `status`, `seed_result`,
                           `seed_result_raw`, `seed_result_unit`, `is_locked`, `batch_no`)
SELECT 1, h.id, h.schedule_id, r.id, r.athlete_id, r.unit_id, a.bib_no, r.group_id,
       ROW_NUMBER() OVER (PARTITION BY h.id ORDER BY r.seed_value, r.id),
       1, 'PRESENT', r.seed_result, r.seed_result, r.seed_result_unit, 0, 'DEMO-BATCH-1'
FROM (
    SELECT r.*,
           ROW_NUMBER() OVER (PARTITION BY r.event_id ORDER BY r.seed_value, r.id) AS rank_no
    FROM `registration` r
    WHERE r.meet_id = 1 AND r.event_id IN (1, 6) AND r.deleted = 0
) r
JOIN `heat` h ON h.meet_id = 1 AND h.schedule_id = IF(r.event_id = 1, 7, 9) AND h.heat_no = 1
JOIN `athlete` a ON a.id = r.athlete_id AND a.deleted = 0
WHERE r.rank_no <= 6;

-- 道次：按组内顺序套用默认道次顺序 [4,5,3,6,2,7,1,8]
UPDATE `heat_member` hm
JOIN (
    SELECT hm2.id,
           ELT(ROW_NUMBER() OVER (PARTITION BY hm2.heat_id ORDER BY hm2.order_no), 4, 5, 3, 6, 2, 7, 1, 8) AS lane_no
    FROM `heat_member` hm2
    WHERE hm2.meet_id = 1 AND hm2.deleted = 0
) ranked ON ranked.id = hm.id
SET hm.lane_no = ranked.lane_no
WHERE hm.meet_id = 1 AND hm.deleted = 0;

-- 道次明细表
INSERT INTO `lane` (`meet_id`, `heat_id`, `heat_member_id`, `schedule_id`, `lane_no`, `assign_mode`,
                    `seed_rank`, `is_best_lane`, `batch_no`, `remark`)
SELECT 1, hm.heat_id, hm.id, hm.schedule_id, hm.lane_no, 'SEED', hm.order_no,
       IF(hm.lane_no IN (3, 4, 5), 1, 0), 'DEMO-BATCH-1', '演示道次'
FROM `heat_member` hm
WHERE hm.meet_id = 1 AND hm.lane_no IS NOT NULL AND hm.deleted = 0;

-- 检录记录（供检录表/检录执行演示）
INSERT INTO `calling` (`meet_id`, `schedule_id`, `heat_id`, `registration_id`, `athlete_id`, `unit_id`,
                       `lane_no`, `call_time`, `call_place`, `present_time`, `status`,
                       `is_bib_checked`, `is_equipment_checked`, `remark`)
SELECT 1, hm.schedule_id, hm.heat_id, hm.registration_id, hm.athlete_id, hm.unit_id, hm.lane_no,
       h.call_time, h.call_place, h.call_time, IF(MOD(hm.order_no, 5) = 0, 'ABSENT', 'PRESENT'),
       1, 1, '演示检录'
FROM `heat_member` hm
JOIN `heat` h ON h.id = hm.heat_id
WHERE hm.meet_id = 1 AND hm.deleted = 0;

-- ----------------------------------------------------------------------------
-- 9. 成绩 58 条（含 DNS / DSQ / 超风速 / 破纪录）
--    派生表列：event_id, round_id, athlete_id, lane_no, raw, val, unit, rank_no, status,
--              foul_type, foul_reason, wind, record_candidate, record_confirmed, record_type, remark
-- ----------------------------------------------------------------------------
INSERT INTO `result` (`meet_id`, `schedule_id`, `event_id`, `round_id`, `registration_id`, `athlete_id`,
                      `unit_id`, `group_id`, `lane_no`, `result_raw`, `result_value`, `result_unit`,
                      `rank_no`, `rank_display`, `status`, `foul_type`, `foul_reason`,
                      `wind_speed`, `is_wind_legal`, `is_record_candidate`, `is_record_confirmed`,
                      `record_type`, `audit_status`, `audit_by`, `audit_time`, `is_locked`, `remark`)
SELECT 1,
       (SELECT s.id FROM `schedule` s WHERE s.meet_id = 1 AND s.event_id = v.event_id AND s.round_id = v.round_id LIMIT 1),
       v.event_id, v.round_id, r.id, r.athlete_id, r.unit_id, r.group_id, v.lane_no,
       v.raw, v.val, v.unit, v.rank_no, CAST(v.rank_no AS CHAR), v.status, v.foul_type, v.foul_reason,
       v.wind, IF(v.wind IS NULL OR ABS(v.wind) <= 2.0, 1, 0),
       v.rec_candidate, v.rec_confirmed, v.record_type, 'APPROVED', 1, NOW(), 1, v.remark
FROM (
    -- 高中男子 100 米预赛（10 人，2 组）
    SELECT 1 AS event_id, 1 AS round_id, 1 AS athlete_id, 4 AS lane_no, '11"18' AS raw, 11.18 AS val, 's' AS unit, 1 AS rank_no, 'VALID' AS status, NULL AS foul_type, NULL AS foul_reason, 0.6 AS wind, 0 AS rec_candidate, 0 AS rec_confirmed, NULL AS record_type, NULL AS remark
    UNION ALL SELECT 1,1,17,5,'11"26',11.26,'s',2,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    UNION ALL SELECT 1,1,6, 3,'11"30',11.30,'s',3,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    UNION ALL SELECT 1,1,19,6,'11"36',11.36,'s',4,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    UNION ALL SELECT 1,1,2, 2,'11"42',11.42,'s',5,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    UNION ALL SELECT 1,1,9, 4,'11"48',11.48,'s',6,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    UNION ALL SELECT 1,1,7, 5,'11"58',11.58,'s',7,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    UNION ALL SELECT 1,1,18,3,'11"66',11.66,'s',8,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    UNION ALL SELECT 1,1,3, 2,'11"78',11.78,'s',9,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    UNION ALL SELECT 1,1,20,3,'11"88',11.88,'s',10,'VALID',NULL,NULL,0.6,0,0,NULL,NULL
    -- 高中男子 100 米决赛（6 人，冠军破赛会纪录 11.50 且超风速一条）
    UNION ALL SELECT 1,2,1, 4,'11"08',11.08,'s',1,'VALID',NULL,NULL,1.2,1,1,'MEET','破赛会纪录（原 11"50）'
    UNION ALL SELECT 1,2,17,5,'11"15',11.15,'s',2,'VALID',NULL,NULL,1.2,0,0,NULL,NULL
    UNION ALL SELECT 1,2,6, 3,'11"24',11.24,'s',3,'VALID',NULL,NULL,1.2,0,0,NULL,NULL
    UNION ALL SELECT 1,2,19,6,'11"33',11.33,'s',4,'VALID',NULL,NULL,1.2,0,0,NULL,NULL
    UNION ALL SELECT 1,2,2, 2,'11"40',11.40,'s',5,'VALID',NULL,NULL,2.4,0,0,NULL,'超风速（不做纪录认定）'
    UNION ALL SELECT 1,2,9, 1,'11"46',11.46,'s',6,'VALID',NULL,NULL,1.2,0,0,NULL,NULL
    -- 高中男子 400 米决赛（4 人）
    UNION ALL SELECT 2,5,6, 4,'51"62',51.62,'s',1,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 2,5,1, 5,'51"95',51.95,'s',2,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 2,5,17,3,'52"41',52.41,'s',3,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 2,5,9, 6,'53"08',53.08,'s',4,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    -- 高中男子跳远决赛（4 人）
    UNION ALL SELECT 3,6,7, NULL,'6.52',6.52,'m',1,'VALID',NULL,NULL,1.0,0,0,NULL,NULL
    UNION ALL SELECT 3,6,19,NULL,'6.34',6.34,'m',2,'VALID',NULL,NULL,1.0,0,0,NULL,NULL
    UNION ALL SELECT 3,6,2, NULL,'6.18',6.18,'m',3,'VALID',NULL,NULL,1.0,0,0,NULL,NULL
    UNION ALL SELECT 3,6,18,NULL,'5.96',5.96,'m',4,'VALID',NULL,NULL,1.0,0,0,NULL,NULL
    -- 高中男子铅球决赛（2 人）
    UNION ALL SELECT 4,7,3, NULL,'12.86',12.86,'m',1,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 4,7,20,NULL,'11.94',11.94,'m',2,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    -- 高中男子 4×100 米接力决赛（3 队，接力双倍）
    UNION ALL SELECT 5,8,1, 4,'44"86',44.86,'s',1,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 5,8,9, 5,'45"62',45.62,'s',2,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 5,8,6, 3,'46"04',46.04,'s',3,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    -- 高中女子 100 米预赛（8 人，2 组）
    UNION ALL SELECT 6,3,21,4,'12"66',12.66,'s',1,'VALID',NULL,NULL,0.4,0,0,NULL,NULL
    UNION ALL SELECT 6,3,4, 5,'12"78',12.78,'s',2,'VALID',NULL,NULL,0.4,0,0,NULL,NULL
    UNION ALL SELECT 6,3,26,3,'12"92',12.92,'s',3,'VALID',NULL,NULL,0.4,0,0,NULL,NULL
    UNION ALL SELECT 6,3,30,6,'13"02',13.02,'s',4,'VALID',NULL,NULL,0.4,0,0,NULL,NULL
    UNION ALL SELECT 6,3,25,2,'13"08',13.08,'s',5,'VALID',NULL,NULL,0.4,0,0,NULL,NULL
    UNION ALL SELECT 6,3,27,4,'13"22',13.22,'s',6,'VALID',NULL,NULL,0.4,0,0,NULL,NULL
    UNION ALL SELECT 6,3,29,5,'13"38',13.38,'s',7,'VALID',NULL,NULL,0.4,0,0,NULL,NULL
    UNION ALL SELECT 6,3,31,3,'13"52',13.52,'s',8,'VALID',NULL,NULL,0.4,0,0,NULL,NULL
    -- 高中女子 100 米决赛（6 人，含 DSQ）
    UNION ALL SELECT 6,4,21,4,'12"58',12.58,'s',1,'VALID',NULL,NULL,0.2,0,0,NULL,NULL
    UNION ALL SELECT 6,4,4, 5,'12"72',12.72,'s',2,'VALID',NULL,NULL,0.2,0,0,NULL,NULL
    UNION ALL SELECT 6,4,26,3,'12"88',12.88,'s',3,'VALID',NULL,NULL,0.2,0,0,NULL,NULL
    UNION ALL SELECT 6,4,30,6,'12"98',12.98,'s',4,'VALID',NULL,NULL,0.2,0,0,NULL,NULL
    UNION ALL SELECT 6,4,25,2,'13"04',13.04,'s',5,'VALID',NULL,NULL,0.2,0,0,NULL,NULL
    UNION ALL SELECT 6,4,27,1,NULL,NULL,NULL,NULL,'DSQ','R_LANE','抢跑',NULL,0,0,NULL,'抢跑判罚，成绩无效'
    -- 高中女子跳高决赛（3 人）
    UNION ALL SELECT 7,9,8, NULL,'1.56',1.56,'m',1,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 7,9,21,NULL,'1.50',1.50,'m',2,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 7,9,4, NULL,'1.46',1.46,'m',3,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    -- 高中女子 4×100 米接力决赛（2 队）
    UNION ALL SELECT 8,10,4, 4,'51"88',51.88,'s',1,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 8,10,8, 5,'53"24',53.24,'s',2,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    -- 初中男子 100 米决赛（3 人，含 DNS）
    UNION ALL SELECT 9,11,11,4,'12"32',12.32,'s',1,'VALID',NULL,NULL,0.8,0,0,NULL,NULL
    UNION ALL SELECT 9,11,13,5,'12"68',12.68,'s',2,'VALID',NULL,NULL,0.8,0,0,NULL,NULL
    UNION ALL SELECT 9,11,15,3,NULL,NULL,NULL,NULL,'DNS',NULL,NULL,NULL,0,0,NULL,'检录未到'
    -- 初中男子跳远决赛（2 人）
    UNION ALL SELECT 10,12,11,NULL,'5.38',5.38,'m',1,'VALID',NULL,NULL,0.9,0,0,NULL,NULL
    UNION ALL SELECT 10,12,13,NULL,'5.12',5.12,'m',2,'VALID',NULL,NULL,0.9,0,0,NULL,NULL
    -- 初中女子 100 米决赛（3 人）
    UNION ALL SELECT 11,13,12,4,'13"48',13.48,'s',1,'VALID',NULL,NULL,0.5,0,0,NULL,NULL
    UNION ALL SELECT 11,13,14,5,'13"72',13.72,'s',2,'VALID',NULL,NULL,0.5,0,0,NULL,NULL
    UNION ALL SELECT 11,13,16,3,'14"02',14.02,'s',3,'VALID',NULL,NULL,0.5,0,0,NULL,NULL
    -- 初中女子跳高决赛（2 人）
    UNION ALL SELECT 12,14,12,NULL,'1.34',1.34,'m',1,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
    UNION ALL SELECT 12,14,14,NULL,'1.28',1.28,'m',2,'VALID',NULL,NULL,NULL,0,0,NULL,NULL
) v
JOIN `registration` r ON r.meet_id = 1 AND r.event_id = v.event_id AND r.athlete_id = v.athlete_id AND r.deleted = 0;

-- ----------------------------------------------------------------------------
-- 10. 田赛试跳 42 条（跳远/跳高/初中跳远/初中跳高，每项 6 次，第 2 次犯规、第 6 次为最好）
-- ----------------------------------------------------------------------------
INSERT INTO `result_attempt` (`meet_id`, `result_id`, `registration_id`, `attempt_no`, `attempt_round`,
                              `attempt_raw`, `attempt_value`, `attempt_unit`, `height_value`,
                              `is_success`, `is_pass`, `is_foul`, `is_best`, `remark`)
SELECT r.meet_id, r.id, r.registration_id, n.n,
       CASE WHEN n.n <= 3 THEN 1 ELSE 2 END,
       CAST(ROUND(r.result_value - (5 - n.n) * 0.08, 2) AS CHAR),
       ROUND(r.result_value - (5 - n.n) * 0.08, 2),
       r.result_unit,
       CASE WHEN r.event_id IN (7, 12) THEN ROUND(r.result_value - (5 - n.n) * 0.02, 2) ELSE NULL END,
       IF(n.n = 2, 0, 1),
       0,
       IF(n.n = 2, 1, 0),
       IF(n.n = 6, 1, 0),
       '演示试跳'
FROM `result` r
JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) n
WHERE r.meet_id = 1 AND r.event_id IN (3, 7, 10, 12) AND r.deleted = 0 AND r.result_value IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 11. 风速记录 2 条
-- ----------------------------------------------------------------------------
INSERT INTO `wind_reading` (`meet_id`, `result_id`, `schedule_id`, `heat_id`, `event_id`, `wind_speed`,
                            `wind_direction`, `is_wind_legal`, `legal_limit`, `wind_device_no`,
                            `read_time`, `recorder_id`, `remark`)
SELECT 1, NULL, h.schedule_id, h.id, s.event_id, 0.6, 'TAIL', 1, 2.0, 'WD-001', NOW(), 1, '演示风速'
FROM `heat` h JOIN `schedule` s ON s.id = h.schedule_id
WHERE h.meet_id = 1 AND h.id IN (1, 2);

-- ----------------------------------------------------------------------------
-- 12. 积分明细（由已审核成绩聚合派生，名次分取 score_rule_item）
-- ----------------------------------------------------------------------------
INSERT INTO `points` (`meet_id`, `event_id`, `round_id`, `result_id`, `registration_id`, `athlete_id`,
                      `unit_id`, `group_id`, `gender`, `rank_no`, `rank_point`, `bonus_point`,
                      `relay_factor`, `total_point`, `score_rule_id`, `score_rule_code`, `bonus_rule_ids`,
                      `calc_time`, `is_manual`, `remark`)
SELECT r.meet_id, r.event_id, r.round_id, r.id, r.registration_id, r.athlete_id, r.unit_id, r.group_id,
       a.gender, r.rank_no,
       COALESCE(sri.point_value, 0),
       CASE WHEN r.is_record_confirmed = 1 THEN 9.00 ELSE 0.00 END,
       CASE WHEN e.is_relay = 1 THEN 2.00 ELSE 1.00 END,
       (COALESCE(sri.point_value, 0) + CASE WHEN r.is_record_confirmed = 1 THEN 9.00 ELSE 0.00 END)
           * CASE WHEN e.is_relay = 1 THEN 2.00 ELSE 1.00 END,
       COALESCE(e.score_rule_id, 1),
       COALESCE(sr.rule_code, 'SINGLE_TOP8'),
       CASE WHEN r.is_record_confirmed = 1 THEN 'BONUS_RECORD_MEET' ELSE NULL END,
       NOW(), 0, '验收演示数据（可用「重算积分」刷新）'
FROM `result` r
JOIN `event` e ON e.id = r.event_id AND e.deleted = 0
JOIN `athlete` a ON a.id = r.athlete_id AND a.deleted = 0
LEFT JOIN `score_rule` sr ON sr.id = e.score_rule_id AND sr.deleted = 0
LEFT JOIN `score_rule_item` sri ON sri.meet_id = 1 AND sri.rule_id = e.score_rule_id
     AND sri.rank_from = r.rank_no AND sri.deleted = 0
WHERE r.meet_id = 1 AND r.deleted = 0 AND r.audit_status = 'APPROVED' AND r.status = 'VALID'
  AND r.rank_no BETWEEN 1 AND 8
  AND r.round_id IN (SELECT er.id FROM `event_round` er WHERE er.meet_id = 1 AND er.is_final = 1 AND er.deleted = 0);

-- ----------------------------------------------------------------------------
-- 13. 团体总分（男 / 女 / 总 三档，同分并列）
-- ----------------------------------------------------------------------------
INSERT INTO `team_score` (`meet_id`, `unit_id`, `group_id`, `score_type`, `rank_point_sum`, `bonus_point_sum`,
                          `relay_point_sum`, `total_point`, `rank_no`, `rank_display`, `event_count`,
                          `athlete_count`, `calc_time`, `calc_batch`, `is_published`, `remark`)
SELECT meet_id, unit_id, group_id, score_type, rank_sum, bonus_sum, relay_sum, total_point,
       RANK() OVER (PARTITION BY group_id, score_type ORDER BY total_point DESC),
       CAST(RANK() OVER (PARTITION BY group_id, score_type ORDER BY total_point DESC) AS CHAR),
       event_count, athlete_count, NOW(), 'DEMO-CALC', 1, '验收演示数据'
FROM (
    SELECT p.meet_id, p.unit_id, p.group_id, 'MALE' AS score_type,
           SUM(p.rank_point) AS rank_sum, SUM(p.bonus_point) AS bonus_sum,
           SUM(CASE WHEN p.relay_factor > 1 THEN (p.rank_point + p.bonus_point) * (p.relay_factor - 1) ELSE 0 END) AS relay_sum,
           SUM(p.total_point) AS total_point,
           COUNT(DISTINCT p.event_id) AS event_count, COUNT(DISTINCT p.athlete_id) AS athlete_count
    FROM `points` p JOIN `athlete` a ON a.id = p.athlete_id AND a.deleted = 0
    WHERE p.meet_id = 1 AND p.deleted = 0 AND a.gender = 'MALE'
    GROUP BY p.meet_id, p.unit_id, p.group_id
    UNION ALL
    SELECT p.meet_id, p.unit_id, p.group_id, 'FEMALE',
           SUM(p.rank_point), SUM(p.bonus_point),
           SUM(CASE WHEN p.relay_factor > 1 THEN (p.rank_point + p.bonus_point) * (p.relay_factor - 1) ELSE 0 END),
           SUM(p.total_point), COUNT(DISTINCT p.event_id), COUNT(DISTINCT p.athlete_id)
    FROM `points` p JOIN `athlete` a ON a.id = p.athlete_id AND a.deleted = 0
    WHERE p.meet_id = 1 AND p.deleted = 0 AND a.gender = 'FEMALE'
    GROUP BY p.meet_id, p.unit_id, p.group_id
    UNION ALL
    SELECT p.meet_id, p.unit_id, p.group_id, 'TOTAL',
           SUM(p.rank_point), SUM(p.bonus_point),
           SUM(CASE WHEN p.relay_factor > 1 THEN (p.rank_point + p.bonus_point) * (p.relay_factor - 1) ELSE 0 END),
           SUM(p.total_point), COUNT(DISTINCT p.event_id), COUNT(DISTINCT p.athlete_id)
    FROM `points` p
    WHERE p.meet_id = 1 AND p.deleted = 0
    GROUP BY p.meet_id, p.unit_id, p.group_id
) t;

-- ----------------------------------------------------------------------------
-- 14. 奖牌榜（仅决赛有效名次 1/2/3，金牌优先排序）
-- ----------------------------------------------------------------------------
INSERT INTO `medal_standing` (`meet_id`, `unit_id`, `group_id`, `gold`, `silver`, `bronze`,
                              `total_medal`, `sort_score`, `medal_sort`, `rank_no`, `rank_display`,
                              `calc_time`, `calc_batch`, `is_published`, `remark`)
SELECT meet_id, unit_id, group_id, gold, silver, bronze, gold + silver + bronze,
       gold * 10000 + silver * 100 + bronze, 'GOLD_FIRST',
       RANK() OVER (PARTITION BY group_id ORDER BY (gold * 10000 + silver * 100 + bronze) DESC),
       CAST(RANK() OVER (PARTITION BY group_id ORDER BY (gold * 10000 + silver * 100 + bronze) DESC) AS CHAR),
       NOW(), 'DEMO-CALC', 1, '验收演示数据'
FROM (
    SELECT r.meet_id, r.unit_id, r.group_id,
           SUM(IF(r.rank_no = 1, 1, 0)) AS gold,
           SUM(IF(r.rank_no = 2, 1, 0)) AS silver,
           SUM(IF(r.rank_no = 3, 1, 0)) AS bronze
    FROM `result` r
    JOIN `event_round` er ON er.id = r.round_id AND er.is_final = 1 AND er.deleted = 0
    WHERE r.meet_id = 1 AND r.deleted = 0 AND r.status = 'VALID' AND r.audit_status = 'APPROVED'
      AND r.rank_no IN (1, 2, 3)
    GROUP BY r.meet_id, r.unit_id, r.group_id
) m;

-- ----------------------------------------------------------------------------
-- 15. 赛会纪录 2 条（用于破纪录认定与「拟破纪录」演示）
-- ----------------------------------------------------------------------------
INSERT INTO `record` (`id`, `meet_id`, `record_type`, `event_id`, `event_name`, `group_id`, `group_code`,
                      `gender`, `is_relay`, `athlete_name`, `unit_name`, `region_name`, `record_value`,
                      `record_raw`, `record_unit`, `wind_speed`, `is_wind_legal`, `record_date`,
                      `record_place`, `is_current`, `source`, `create_by`)
VALUES (1, 1, 'MEET', 1, '高中男子 100 米', 1, 'SENIOR_MALE', 'MALE', 0, '往届选手', '示例市第一中学', '示例市',
        11.50, '11"50', 's', 0.9, 1, '2024-10-19', '示例市体育中心田径场', 1, 'MANUAL', 1),
       (2, 1, 'MEET', 7, '高中女子跳高', 2, 'SENIOR_FEMALE', 'FEMALE', 0, '往届选手', '示例市第二中学', '示例市',
        1.58, '1.58', 'm', NULL, 1, '2024-10-19', '示例市体育中心田径场', 1, 'MANUAL', 1);

-- ----------------------------------------------------------------------------
-- 16. 公告 3 条（含推大屏公告）
-- ----------------------------------------------------------------------------
INSERT INTO `announcement` (`id`, `meet_id`, `title`, `content_html`, `notice_type`, `scope`, `is_top`,
                            `publish_time`, `status`, `publisher_id`, `publisher_name`, `screen_show`,
                            `screen_duration`, `sort_no`, `read_count`, `remark`)
VALUES (1, 1, '开幕式与检录时间通知',
        '<p>本届运动会将于 <strong>2025-10-18 08:00</strong> 在示例市体育中心田径场举行开幕式。</p><p>请各参赛单位于 07:30 前完成检录，逾期按弃权处理。</p>',
        'NOTICE', 'PUBLIC', 1, '2025-10-17 16:00:00', 'PUBLISHED', 1, '系统管理员', 1, 15, 1, 12, '推大屏轮播'),
       (2, 1, '高中男子 100 米决赛成绩公告',
        '<p>高中男子 100 米决赛已于 2025-10-18 14:00 结束，成绩如下：</p><p>第一名 张小明（示例市第一中学）11"08（<strong>破赛会纪录</strong>）；第二名 孙宇航 11"15；第三名 赵天宇 11"24。</p>',
        'RESULT', 'PUBLIC', 0, '2025-10-18 15:00:00', 'PUBLISHED', 1, '系统管理员', 1, 12, 2, 35, '推大屏轮播'),
       (3, 1, '第二天赛程调整（草稿）',
        '<p>因天气原因，第二天上午初中组项目可能顺延，具体以现场通知为准。</p>',
        'SCHEDULE', 'PUBLIC', 0, NULL, 'DRAFT', NULL, NULL, 0, NULL, 3, 0, '草稿示例');

-- ----------------------------------------------------------------------------
-- 17. 打印模板 2 个（秩序册 + 成绩公告，各为默认模板）
-- ----------------------------------------------------------------------------
INSERT INTO `print_template` (`id`, `meet_id`, `template_code`, `template_name`, `report_type`,
                              `header_html`, `footer_html`, `signature_html`, `page_size`, `orientation`,
                              `font_size`, `show_page_no`, `show_print_time`, `is_default`, `extra_json`,
                              `status`, `remark`)
VALUES (1, 1, 'TPL_ORDER_BOOK', '秩序册标准模板（A4 纵向）', 'PROGRAM',
        '<p>示例市第 12 届中学生田径运动会</p>', '<p>TrackMeet 秩序册 · 内部资料</p>',
        '记录员：|裁判长：|技术代表：', 'A4', 'PORTRAIT', 9, 1, 1, 1,
        CAST('{"sections":{"分组表":[1,1,1,3,3,2,2]},"watermark":"示例市中学生运动会"}' AS JSON),
        'ENABLED', '默认秩序册模板'),
       (2, 1, 'TPL_RESULT_BOOK', '成绩公告标准模板（A4 纵向）', 'RESULT',
        '<p>示例市第 12 届中学生田径运动会 成绩公告</p>', '<p>成绩以竞赛委员会公布为准</p>',
        '记录员：|裁判长：|公告日期：', 'A4', 'PORTRAIT', 9, 1, 1, 1,
        CAST('{"sections":{"成绩公告":[1,1,2,3,1,1,3]},"watermark":"成绩公告"}' AS JSON),
        'ENABLED', '默认成绩公告模板');

-- ----------------------------------------------------------------------------
-- 18. 大屏配置 1 条（匿名大屏令牌存于 theme_json.accessToken）
-- ----------------------------------------------------------------------------
INSERT INTO `screen_config` (`id`, `meet_id`, `screen_name`, `screen_code`, `modules_json`,
                             `refresh_interval`, `theme_json`, `resolution`, `rotate_seconds`,
                             `play_mode`, `status`, `is_default`, `remark`)
VALUES (1, 1, '主会场大屏', 'SCREEN-DEMO-0001',
        CAST('{"enabled":["ANNOUNCEMENT","MEDAL","TEAM_SCORE","RESULT"]}' AS JSON), 30,
        CAST('{"mode":"DARK","accessToken":"demo0000000000000000000000000001","primaryColor":"#3b82f6"}' AS JSON),
        '1920x1080', 15, 'AUTO', 'ENABLED', 1, '匿名大屏地址：/screen/public-board?meetId=1&token=demo0000000000000000000000000001');

-- ============================================================================
-- 19. 自检（可单独执行）
-- ============================================================================
-- SELECT '场地' AS 对象, COUNT(*) AS 数量 FROM venue WHERE meet_id = 1
-- UNION ALL SELECT '运动员', COUNT(*) FROM athlete WHERE meet_id = 1
-- UNION ALL SELECT '项目', COUNT(*) FROM event WHERE meet_id = 1
-- UNION ALL SELECT '轮次', COUNT(*) FROM event_round WHERE meet_id = 1
-- UNION ALL SELECT '报名', COUNT(*) FROM registration WHERE meet_id = 1
-- UNION ALL SELECT '竞赛单元', COUNT(*) FROM schedule_session WHERE meet_id = 1
-- UNION ALL SELECT '日程', COUNT(*) FROM schedule WHERE meet_id = 1
-- UNION ALL SELECT '分组', COUNT(*) FROM heat WHERE meet_id = 1
-- UNION ALL SELECT '组内成员', COUNT(*) FROM heat_member WHERE meet_id = 1
-- UNION ALL SELECT '道次', COUNT(*) FROM lane WHERE meet_id = 1
-- UNION ALL SELECT '检录', COUNT(*) FROM calling WHERE meet_id = 1
-- UNION ALL SELECT '成绩', COUNT(*) FROM result WHERE meet_id = 1
-- UNION ALL SELECT '试跳', COUNT(*) FROM result_attempt WHERE meet_id = 1
-- UNION ALL SELECT '积分明细', COUNT(*) FROM points WHERE meet_id = 1
-- UNION ALL SELECT '团体总分', COUNT(*) FROM team_score WHERE meet_id = 1
-- UNION ALL SELECT '奖牌榜', COUNT(*) FROM medal_standing WHERE meet_id = 1
-- UNION ALL SELECT '公告', COUNT(*) FROM announcement WHERE meet_id = 1
-- UNION ALL SELECT '打印模板', COUNT(*) FROM print_template WHERE meet_id = 1
-- UNION ALL SELECT '大屏配置', COUNT(*) FROM screen_config WHERE meet_id = 1;
