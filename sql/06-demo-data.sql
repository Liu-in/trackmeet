-- ============================================================================
-- 田径运动会编排软件 TrackMeet —— 06 演示数据脚本（可选）
-- 内容：1 届示例运动会 + 届次规则 + 名次分方案 + 加分规则 + 4 组别 + 4 单位 + 16 运动员 + 演示账号
-- 前置：必须先执行 01/02/03/04 建表脚本与 05-init-data.sql
-- 说明：所有 id 显式指定，便于反复执行；脚本开头会先清理同 id 的演示数据，可安全重复执行。
-- 演示账号统一初始口令由 DefaultAccountInitializer 在首次启动时写入（默认 Admin@123），
--       生产环境请勿导入本脚本，或导入后立即修改全部账号口令。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 0. 清理既有演示数据（按 id 精确删除，避免影响真实数据）
-- ----------------------------------------------------------------------------
DELETE FROM `athlete`            WHERE `meet_id` = 1;
DELETE FROM `unit`               WHERE `meet_id` = 1;
DELETE FROM `meet_group`         WHERE `meet_id` = 1;
DELETE FROM `score_rule_item`    WHERE `meet_id` = 1;
DELETE FROM `score_rule`         WHERE `meet_id` = 1;
DELETE FROM `bonus_rule`         WHERE `meet_id` = 1;
DELETE FROM `meet_rule_config`   WHERE `meet_id` = 1;
DELETE FROM `meet`               WHERE `id` = 1;

-- ----------------------------------------------------------------------------
-- 1. 示例运动会（届次）
-- ----------------------------------------------------------------------------
INSERT INTO `meet` (`id`, `meet_name`, `edition`, `short_name`, `host`, `organizer`, `co_organizer`,
                    `start_date`, `end_date`, `place`, `reg_start_time`, `reg_end_time`, `status`,
                    `regulation_html`, `is_template`, `config_json`, `remark`, `create_by`)
VALUES (1, '示例市第 12 届中学生田径运动会', '2025年第12届', '第12届市中运会',
        '示例市教育局', '示例市体育局', '示例市田径协会',
        '2025-10-18', '2025-10-20', '示例市体育中心田径场',
        '2025-09-01 08:00:00', '2025-10-08 18:00:00', 'REG_OPEN',
        '<h3>竞赛规程</h3><p>1. 采用中国田径协会审定的最新田径竞赛规则；</p><p>2. 每人限报 2 个单项，另可兼报 1 个接力项目；</p><p>3. 各单项录取前 8 名，按 9、7、6、5、4、3、2、1 计分；</p><p>4. 接力项目双倍计分，破赛会纪录加 9 分。</p>',
        0,
        JSON_OBJECT('athleteMaxEvents', 2, 'maxPerUnit', 3, 'conflictIntervalMin', 30,
                    'bonusInTeamScore', TRUE, 'medalSort', 'GOLD_FIRST', 'timePrecision', 'HUNDREDTH'),
        '演示数据：可直接用于功能验证', 1);

-- ----------------------------------------------------------------------------
-- 2. 届次级规则（三层继承的最上层）
-- ----------------------------------------------------------------------------
INSERT INTO `meet_rule_config` (`meet_id`, `admit_rank`, `score_rule_id`, `athlete_max_events`, `relay_max_per_athlete`,
                                `max_per_unit`, `max_athlete_per_unit`, `conflict_interval_min`, `referee_interval_min`,
                                `relay_double_factor`, `bonus_in_team_score`, `medal_sort`, `time_precision`,
                                `field_attempt_count`, `default_lane_count`, `default_lane_order_json`, `lane_assign_mode`,
                                `wind_json`, `tie_break_json`, `remark`)
VALUES (1, 8, 1, 2, 1,
        3, 60, 30, 15,
        2.00, 1, 'GOLD_FIRST', 'HUNDREDTH',
        3, 8, CAST('[4,5,3,6,2,7,1,8]' AS JSON), 'SEED',
        CAST('{"applicable":true,"maxLegal":2.0,"unit":"m/s","illegalEffect":"RANK_VALID_RECORD_INVALID"}' AS JSON),
        CAST('{"order":["SECOND_BEST","BEST_ATTEMPT_ROUND","DRAW"],"allowParallelRank":true}' AS JSON),
        '届次默认规则：可在项目级与轮次级覆盖');

-- ----------------------------------------------------------------------------
-- 3. 名次分方案与明细（单项前八名 9-7-6-5-4-3-2-1；接力双倍另见 bonus_rule）
-- ----------------------------------------------------------------------------
INSERT INTO `score_rule` (`id`, `meet_id`, `rule_name`, `rule_code`, `tie_policy`, `score_target`, `rank_count`,
                          `is_default`, `apply_event_type`, `apply_gender`, `effective_round_code`, `is_team_score`, `status`, `remark`)
VALUES (1, 1, '单项前八名 9-7-6-5-4-3-2-1', 'SINGLE_TOP8', 'AVERAGE', 'EVENT', 8,
        1, 'ALL', 'ALL', 'FINAL', 1, 'ENABLED', '届次默认名次分方案（并列取均分）'),
       (2, 1, '接力前八名 9-7-6-5-4-3-2-1（×2）', 'RELAY_TOP8', 'AVERAGE', 'EVENT', 8,
        0, 'RELAY', 'ALL', 'FINAL', 1, 'ENABLED', '接力项目名次分，倍数由 bonus_rule 控制');

INSERT INTO `score_rule_item` (`meet_id`, `rule_id`, `rank_no`, `rank_from`, `rank_to`, `rank_label`, `point_value`, `tie_policy`, `is_team_score`, `sort_no`)
VALUES (1, 1, 1, 1, 1, '第一名', 9.00, 'AVERAGE', 1, 1),
       (1, 1, 2, 2, 2, '第二名', 7.00, 'AVERAGE', 1, 2),
       (1, 1, 3, 3, 3, '第三名', 6.00, 'AVERAGE', 1, 3),
       (1, 1, 4, 4, 4, '第四名', 5.00, 'AVERAGE', 1, 4),
       (1, 1, 5, 5, 5, '第五名', 4.00, 'AVERAGE', 1, 5),
       (1, 1, 6, 6, 6, '第六名', 3.00, 'AVERAGE', 1, 6),
       (1, 1, 7, 7, 7, '第七名', 2.00, 'AVERAGE', 1, 7),
       (1, 1, 8, 8, 8, '第八名', 1.00, 'AVERAGE', 1, 8),
       (1, 2, 1, 1, 1, '第一名', 9.00, 'AVERAGE', 1, 1),
       (1, 2, 2, 2, 2, '第二名', 7.00, 'AVERAGE', 1, 2),
       (1, 2, 3, 3, 3, '第三名', 6.00, 'AVERAGE', 1, 3),
       (1, 2, 4, 4, 4, '第四名', 5.00, 'AVERAGE', 1, 4),
       (1, 2, 5, 5, 5, '第五名', 4.00, 'AVERAGE', 1, 5),
       (1, 2, 6, 6, 6, '第六名', 3.00, 'AVERAGE', 1, 6),
       (1, 2, 7, 7, 7, '第七名', 2.00, 'AVERAGE', 1, 7),
       (1, 2, 8, 8, 8, '第八名', 1.00, 'AVERAGE', 1, 8);

-- ----------------------------------------------------------------------------
-- 4. 加分规则（破纪录加分 + 接力双倍分）
-- ----------------------------------------------------------------------------
INSERT INTO `bonus_rule` (`meet_id`, `rule_name`, `rule_code`, `bonus_type`, `record_type`, `bonus_point`,
                          `is_stackable`, `factor`, `apply_event_type`, `apply_gender`, `effective_round_codes`,
                          `is_in_team_score`, `priority`, `status`, `remark`)
VALUES (1, '破赛会纪录加 9 分', 'BONUS_RECORD_MEET', 'RECORD', 'MEET', 9.00,
        0, NULL, 'ALL', 'ALL', 'FINAL', 1, 10, 'ENABLED', '破赛会纪录加分，仅决赛有效'),
       (1, '破市级及以上纪录加 18 分', 'BONUS_RECORD_CITY', 'RECORD', 'CITY', 18.00,
        0, NULL, 'ALL', 'ALL', 'FINAL', 1, 20, 'ENABLED', '更高层级纪录加分（与赛会纪录不叠加）'),
       (1, '接力项目双倍计分', 'BONUS_RELAY_DOUBLE', 'RELAY', NULL, NULL,
        0, 2.00, 'RELAY', 'ALL', 'FINAL', 1, 30, 'ENABLED', '接力名次分 ×2 计入团体总分');

-- ----------------------------------------------------------------------------
-- 5. 组别
-- ----------------------------------------------------------------------------
INSERT INTO `meet_group` (`id`, `meet_id`, `group_name`, `group_code`, `gender`, `birth_date_start`, `birth_date_end`,
                          `grade_range`, `is_team_score`, `score_group_code`, `record_group_code`, `sort_no`, `status`, `remark`)
VALUES (1, 1, '高中男子组', 'SENIOR_MALE',   'MALE',   '2007-09-01', '2010-08-31', '高一至高三', 1, 'SENIOR_MALE',   'SENIOR_MALE',   1, 'ENABLED', '演示组别'),
       (2, 1, '高中女子组', 'SENIOR_FEMALE', 'FEMALE', '2007-09-01', '2010-08-31', '高一至高三', 1, 'SENIOR_FEMALE', 'SENIOR_FEMALE', 2, 'ENABLED', '演示组别'),
       (3, 1, '初中男子组', 'JUNIOR_MALE',   'MALE',   '2010-09-01', '2013-08-31', '初一至初三', 1, 'JUNIOR_MALE',   'JUNIOR_MALE',   3, 'ENABLED', '演示组别'),
       (4, 1, '初中女子组', 'JUNIOR_FEMALE', 'FEMALE', '2010-09-01', '2013-08-31', '初一至初三', 1, 'JUNIOR_FEMALE', 'JUNIOR_FEMALE', 4, 'ENABLED', '演示组别');

-- ----------------------------------------------------------------------------
-- 6. 参赛单位
-- ----------------------------------------------------------------------------
INSERT INTO `unit` (`id`, `meet_id`, `unit_name`, `unit_code`, `short_name`, `unit_type`, `number_prefix`,
                    `leader`, `coach`, `phone`, `address`, `group_scope_json`, `total_quota`, `status`, `remark`)
VALUES (1, 1, '示例市第一中学', 'UNIT_001', '一中', 'SCHOOL', '一中', '张老师', '李教练', '13800000001', '示例市示例路 1 号',
        CAST('["SENIOR_MALE","SENIOR_FEMALE","JUNIOR_MALE","JUNIOR_FEMALE"]' AS JSON), 60, 'ENABLED', '演示单位'),
       (2, 1, '示例市第二中学', 'UNIT_002', '二中', 'SCHOOL', '二中', '王老师', '赵教练', '13800000002', '示例市示例路 2 号',
        CAST('["SENIOR_MALE","SENIOR_FEMALE"]' AS JSON), 40, 'ENABLED', '演示单位'),
       (3, 1, '示例市实验中学', 'UNIT_003', '实验', 'SCHOOL', '实验', '刘老师', '陈教练', '13800000003', '示例市示例路 3 号',
        CAST('["SENIOR_MALE","SENIOR_FEMALE","JUNIOR_MALE","JUNIOR_FEMALE"]' AS JSON), 50, 'ENABLED', '演示单位'),
       (4, 1, '示例市青少年体育俱乐部', 'UNIT_004', '青俱', 'CLUB', '青俱', '孙老师', '周教练', '13800000004', '示例市示例路 4 号',
        CAST('["JUNIOR_MALE","JUNIOR_FEMALE"]' AS JSON), 30, 'ENABLED', '演示单位');

-- ----------------------------------------------------------------------------
-- 7. 运动员（16 名，覆盖 4 组别 × 4 单位）
-- ----------------------------------------------------------------------------
INSERT INTO `athlete` (`id`, `meet_id`, `athlete_name`, `gender`, `birth_date`, `id_card`, `group_id`, `unit_id`,
                       `bib_no`, `class_name`, `phone`, `guardian`, `guardian_phone`, `qualification_status`,
                       `qualify_remark`, `is_relay_only`, `entry_source`, `remark`, `create_by`)
VALUES (1,  1, '张小明', 'MALE',   '2008-03-12', '110101200803120011', 1, 1, '一中-001', '高二(1)班', '13900000001', '张父', '13700000001', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (2,  1, '李强',   'MALE',   '2008-07-25', '110101200807250022', 1, 1, '一中-002', '高二(2)班', '13900000002', '李父', '13700000002', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (3,  1, '王浩',   'MALE',   '2009-01-09', '110101200901090033', 1, 1, '一中-003', '高一(1)班', '13900000003', '王母', '13700000003', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (4,  1, '陈雨欣', 'FEMALE', '2008-05-18', '110101200805180044', 2, 1, '一中-101', '高二(3)班', '13900000004', '陈父', '13700000004', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (5,  1, '刘思远', 'FEMALE', '2009-11-02', '110101200911020055', 2, 1, '一中-102', '高一(2)班', '13900000005', '刘母', '13700000005', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (6,  1, '赵天宇', 'MALE',   '2008-02-14', '110101200802140066', 1, 2, '二中-001', '高二(1)班', '13900000006', '赵父', '13700000006', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (7,  1, '周子豪', 'MALE',   '2009-06-30', '110101200906300077', 1, 2, '二中-002', '高一(3)班', '13900000007', '周母', '13700000007', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (8,  1, '吴佳怡', 'FEMALE', '2008-09-21', '110101200809210088', 2, 2, '二中-101', '高二(4)班', '13900000008', '吴父', '13700000008', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (9,  1, '郑一鸣', 'MALE',   '2008-12-05', '110101200812050099', 1, 3, '实验-001', '高二(2)班', '13900000009', '郑父', '13700000009', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (10, 1, '冯雪',   'FEMALE', '2009-04-17', '110101200904170100', 2, 3, '实验-101', '高一(1)班', '13900000010', '冯母', '13700000010', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (11, 1, '蒋文博', 'MALE',   '2011-03-08', '110101201103080111', 3, 3, '实验-201', '初二(1)班', '13900000011', '蒋父', '13700000011', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (12, 1, '韩梅梅', 'FEMALE', '2011-08-23', '110101201108230122', 4, 3, '实验-301', '初二(2)班', '13900000012', '韩母', '13700000012', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (13, 1, '许铭',   'MALE',   '2012-01-11', '110101201201110133', 3, 4, '青俱-001', '初一(1)班', '13900000013', '许父', '13700000013', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (14, 1, '何雨桐', 'FEMALE', '2012-05-27', '110101201205270144', 4, 4, '青俱-101', '初一(2)班', '13900000014', '何母', '13700000014', 'QUALIFIED', '资料齐全', 0, 'MANUAL', NULL, 1),
       (15, 1, '邓超',   'MALE',   '2011-10-19', '110101201110190155', 3, 4, '青俱-002', '初二(3)班', '13900000015', '邓父', '13700000015', 'PENDING',   '待补充体检证明', 0, 'MANUAL', NULL, 1),
       (16, 1, '罗静',   'FEMALE', '2012-09-03', '110101201209030166', 4, 4, '青俱-102', '初一(3)班', '13900000016', '罗母', '13700000016', 'PENDING',   '待补充学籍证明', 0, 'MANUAL', NULL, 1);

-- ----------------------------------------------------------------------------
-- 8. 演示账号（口令占位符，首次启动统一初始化；仅用于演示环境）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `unit_id`, `status`, `is_builtin`,
                        `pwd_error_count`, `force_pwd_change`, `token_version`, `sort_no`, `remark`)
VALUES (2, 'scheduler', 'INIT_PLACEHOLDER', '编排员演示号', '13800000002', NULL, 'ENABLED', 0, 0, 0, 0, 2, '演示账号（编排员）'),
       (3, 'referee',   'INIT_PLACEHOLDER', '裁判演示号',   '13800000003', NULL, 'ENABLED', 0, 0, 0, 0, 3, '演示账号（裁判）'),
       (4, 'leader1',   'INIT_PLACEHOLDER', '一中领队演示号', '13800000004', 1,    'ENABLED', 0, 0, 0, 0, 4, '演示账号（领队，绑定单位 1）');

INSERT INTO `sys_user_role` (`user_id`, `role_id`, `grant_source`, `grant_time`, `is_primary`, `is_effective`)
VALUES (2, 3, 'INIT', NOW(), 1, 1),
       (3, 6, 'INIT', NOW(), 1, 1),
       (4, 8, 'INIT', NOW(), 1, 1);

-- ----------------------------------------------------------------------------
-- 9. 自检
-- ----------------------------------------------------------------------------
-- SELECT m.meet_name, m.status,
--        (SELECT COUNT(*) FROM meet_group g WHERE g.meet_id = m.id) AS 组别数,
--        (SELECT COUNT(*) FROM unit u WHERE u.meet_id = m.id) AS 单位数,
--        (SELECT COUNT(*) FROM athlete a WHERE a.meet_id = m.id) AS 运动员数,
--        (SELECT COUNT(*) FROM score_rule r WHERE r.meet_id = m.id) AS 名次分方案数,
--        (SELECT COUNT(*) FROM bonus_rule b WHERE b.meet_id = m.id) AS 加分规则数
-- FROM meet m WHERE m.id = 1;
