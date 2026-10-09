-- ----------------------------------------------------------------------------
-- TrackMeet 阶段 5 增量补丁：打印与导出 / 公告与大屏
--   适用：已按 01~08 建库的既有环境；全新环境执行 01~06 即可（05 已含本补丁内容）
--   内容：新增权限编码 screen:board、screen:config:add、notice:edit、notice:delete 及打印中心按钮
--   执行：mysql -h 127.0.0.1 -u root -p trackmeet < sql/09-patch-stage5.sql
-- ----------------------------------------------------------------------------
USE `trackmeet`;

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perm_code`, `sort_no`) VALUES
(93,  90,  '大屏公告',      'M', '/screen/board',           'screen/ScreenBoard',   'DataBoard',  'screen:board',        93),
(912, 91,  '修改公告',      'B', NULL, NULL, NULL, 'notice:edit',         3),
(913, 91,  '删除公告',      'B', NULL, NULL, NULL, 'notice:delete',       4),
(922, 92,  '新增大屏配置',  'B', NULL, NULL, NULL, 'screen:config:add',   3);

-- ----------------------------------------------------------------------------
-- 校验：应新增 4 行菜单/按钮
-- ----------------------------------------------------------------------------
-- SELECT id, menu_name, perm_code FROM sys_menu WHERE id IN (93, 912, 913, 922);
