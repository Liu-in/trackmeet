-- ============================================================================
-- 田径运动会编排软件 TrackMeet —— 07 阶段 2 增量补丁（已建库环境升级用）
-- 用途：为「阶段 2 报名与项目管理」补齐权限点 event:query 并授予相关角色。
-- 说明：
--   1) 全新环境无需执行本脚本（01~06 已包含该权限点）；
--   2) 本脚本可重复执行（幂等：INSERT ... WHERE NOT EXISTS + INSERT IGNORE）；
--   3) 若数据库名不是 trackmeet，请先手工 USE 到目标库再执行（或修改下方 USE 语句）。
-- 执行： mysql -h 127.0.0.1 -u root -p trackmeet < sql/07-patch-stage2.sql
-- ============================================================================

SET NAMES utf8mb4;
USE `trackmeet`;

-- 1. 新增按钮菜单：项目管理 → 查看项目（event:query）
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perm_code`, `visible`, `sort_no`)
SELECT 314, 31, '查看项目', 'B', NULL, NULL, NULL, 'event:query', 1, 5
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 314);

-- 2. 由菜单派生权限点（与 05-init-data.sql 的派生规则一致）
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `menu_id`, `module`, `is_builtin`, `status`, `sort_no`)
SELECT 'event:query', '查看项目', 'BUTTON', 314, 'EVENT', 1, 'ENABLED', 5
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code` = 'event:query');

-- 3. 授予角色：超级管理员 / 竞赛主管 / 编排员 / 裁判长
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.`perm_code` = 'event:query'
WHERE r.`role_code` IN ('SUPER_ADMIN', 'MEET_ADMIN', 'SCHEDULER', 'CHIEF_REFEREE', 'REFEREE');

-- 4. 报名表字段宽度与接口约定对齐（退回原因/备注 ≤200，避免长文本被 MySQL 截断报错）
ALTER TABLE `registration`
  MODIFY COLUMN `reject_reason` VARCHAR(200) NULL COMMENT '退回原因（≤200）',
  MODIFY COLUMN `remark`        VARCHAR(200) NULL COMMENT '备注（≤200）';

-- 5. 校验
SELECT p.`perm_code`, p.`perm_name`, COUNT(rp.`role_id`) AS 已授权角色数
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp ON rp.`permission_id` = p.`id`
WHERE p.`perm_code` = 'event:query'
GROUP BY p.`perm_code`, p.`perm_name`;

SELECT COLUMN_NAME AS 列名, COLUMN_TYPE AS 类型
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'registration'
  AND COLUMN_NAME IN ('reject_reason', 'remark');
