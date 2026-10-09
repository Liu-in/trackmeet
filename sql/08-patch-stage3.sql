-- ============================================================================
-- 田径运动会编排软件 TrackMeet —— 08 阶段 3 增量补丁（已建库环境升级用）
-- 用途：为「阶段 3 自动编排」补齐权限点：
--   schedule:add / schedule:delete（日程手工增删）、schedule:field:edit（田赛顺序调整）
-- 说明：全新环境无需执行本脚本（05-init-data.sql 已包含）；可重复执行（幂等）。
-- 执行： mysql -h 127.0.0.1 -u root -p trackmeet < sql/08-patch-stage3.sql
-- ============================================================================

SET NAMES utf8mb4;
USE `trackmeet`;

-- 1. 补按钮菜单
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perm_code`, `visible`, `sort_no`)
SELECT 425, 42, '新增日程', 'B', NULL, NULL, NULL, 'schedule:add', 1, 6
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 425);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perm_code`, `visible`, `sort_no`)
SELECT 426, 42, '删除日程', 'B', NULL, NULL, NULL, 'schedule:delete', 1, 7
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 426);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perm_code`, `visible`, `sort_no`)
SELECT 436, 43, '调整田赛顺序', 'B', NULL, NULL, NULL, 'schedule:field:edit', 1, 7
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 436);

-- 2. 派生权限点
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `menu_id`, `module`, `is_builtin`, `status`, `sort_no`)
SELECT m.`perm_code`, m.`menu_name`, 'BUTTON', m.`id`, 'SCHEDULE', 1, 'ENABLED', m.`sort_no`
FROM `sys_menu` m
WHERE m.`id` IN (425, 426, 436)
  AND NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`perm_code` = m.`perm_code`);

-- 3. 授权：超级管理员 + 竞赛主管 + 编排员 + 裁判长（调度相关）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.`perm_code` IN ('schedule:add', 'schedule:delete', 'schedule:field:edit')
WHERE r.`role_code` IN ('SUPER_ADMIN', 'MEET_ADMIN', 'SCHEDULER', 'CHIEF_REFEREE');

-- 4. 校验
SELECT p.`perm_code`, p.`perm_name`, COUNT(rp.`role_id`) AS 已授权角色数
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp ON rp.`permission_id` = p.`id`
WHERE p.`perm_code` IN ('schedule:add', 'schedule:delete', 'schedule:field:edit')
GROUP BY p.`perm_code`, p.`perm_name`;
