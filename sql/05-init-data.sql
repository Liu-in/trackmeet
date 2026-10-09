-- ============================================================================
-- 田径运动会编排软件 TrackMeet —— 05 初始化数据脚本
-- 内容：菜单树 + 权限点（由菜单派生）+ 内置角色 + 角色授权 + 字典 + 系统参数 + 内置账号
-- 前置：必须先执行 01/02/03/04 建表脚本
-- 说明：
--   1) sys_menu 承载菜单/按钮与权限编码，sys_permission 由菜单派生（MENU/BUTTON），保证菜单树与权限点一一对应；
--   2) 角色授权使用 INSERT IGNORE，避免「目录容器授权」与「角色批量授权」重复插入触发唯一键冲突；
--   3) SUPER_ADMIN 拥有全部权限；其余角色按权限编码前缀/LIKE 规则批量授权（后期可在「角色权限」页面调整）；
--   4) admin 口令为占位符 INIT_PLACEHOLDER，首次启动由 DefaultAccountInitializer 用 BCrypt 初始化（默认 Admin@123）。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 部门（根）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_dept` (`id`, `parent_id`, `dept_name`, `dept_code`, `dept_type`, `dept_path`, `sort_no`, `status`, `remark`)
VALUES (1, 0, '组委会', 'ROOT', 'ORG', '/1/', 1, 'ENABLED', '系统根部门');

-- ----------------------------------------------------------------------------
-- 2. 菜单与按钮（menu_type：D 目录 / M 菜单 / B 按钮）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perm_code`, `sort_no`) VALUES
-- 首页
(1,   0,   '首页看板',      'M', '/dashboard',              'dashboard/Index',            'Dashboard', 'dashboard:view',        1),
-- 运动会管理
(10,  0,   '运动会管理',    'D', '/meet',                   NULL,                         'Trophy',    'meet',                  10),
(11,  10,  '运动会列表',    'M', '/meet/list',              'meet/MeetList',              'List',      'meet:list',             11),
(12,  10,  '组别管理',      'M', '/meet/groups',            'meet/GroupList',             'Grid',      'meet:group:list',       12),
(13,  10,  '单位管理',      'M', '/meet/units',             'meet/UnitList',              'OfficeBuilding', 'meet:unit:list',  13),
(14,  10,  '场地管理',      'M', '/meet/venues',            'meet/VenueList',             'Location',  'meet:venue:list',       14),
(15,  10,  '裁判管理',      'M', '/meet/referees',          'meet/RefereeList',           'UserFilled','meet:referee:list',     15),
(110, 11,  '新增运动会',    'B', NULL, NULL, NULL, 'meet:add',             1),
(111, 11,  '修改运动会',    'B', NULL, NULL, NULL, 'meet:edit',            2),
(112, 11,  '删除运动会',    'B', NULL, NULL, NULL, 'meet:delete',          3),
(113, 11,  '复制运动会',    'B', NULL, NULL, NULL, 'meet:clone',           4),
(114, 11,  '状态流转',      'B', NULL, NULL, NULL, 'meet:status',          5),
(115, 11,  '查看运动会',    'B', NULL, NULL, NULL, 'meet:query',           6),
(120, 12,  '新增组别',      'B', NULL, NULL, NULL, 'meet:group:add',       1),
(121, 12,  '修改组别',      'B', NULL, NULL, NULL, 'meet:group:edit',      2),
(122, 12,  '删除组别',      'B', NULL, NULL, NULL, 'meet:group:delete',    3),
(123, 12,  '查看组别',      'B', NULL, NULL, NULL, 'meet:group:query',     4),
(130, 13,  '新增单位',      'B', NULL, NULL, NULL, 'meet:unit:add',        1),
(131, 13,  '修改单位',      'B', NULL, NULL, NULL, 'meet:unit:edit',       2),
(132, 13,  '删除单位',      'B', NULL, NULL, NULL, 'meet:unit:delete',     3),
(133, 13,  '查看单位',      'B', NULL, NULL, NULL, 'meet:unit:query',      4),
(140, 14,  '新增场地',      'B', NULL, NULL, NULL, 'meet:venue:add',       1),
(141, 14,  '修改场地',      'B', NULL, NULL, NULL, 'meet:venue:edit',      2),
(142, 14,  '删除场地',      'B', NULL, NULL, NULL, 'meet:venue:delete',    3),
(150, 15,  '新增裁判',      'B', NULL, NULL, NULL, 'meet:referee:add',     1),
(151, 15,  '修改裁判',      'B', NULL, NULL, NULL, 'meet:referee:edit',    2),
(152, 15,  '删除裁判',      'B', NULL, NULL, NULL, 'meet:referee:delete',  3),
-- 运动员与报名
(20,  0,   '运动员与报名',  'D', '/registration-center',    NULL,                         'User',      'athlete_reg',           20),
(21,  20,  '运动员管理',    'M', '/athlete/list',           'athlete/AthleteList',        'Avatar',    'athlete:list',          21),
(22,  20,  '报名管理',      'M', '/registration/list',      'registration/RegistrationList', 'Document', 'registration:list', 22),
(23,  20,  '报名审核',      'M', '/registration/audit',     'registration/RegistrationAudit', 'Checked', 'registration:audit', 23),
(24,  20,  '报名校验预警',  'M', '/registration/check',     'registration/RegistrationCheck', 'Warning', 'registration:check', 24),
(210, 21,  '新增运动员',    'B', NULL, NULL, NULL, 'athlete:add',          1),
(211, 21,  '修改运动员',    'B', NULL, NULL, NULL, 'athlete:edit',         2),
(212, 21,  '删除运动员',    'B', NULL, NULL, NULL, 'athlete:delete',       3),
(213, 21,  '导入运动员',    'B', NULL, NULL, NULL, 'athlete:import',       4),
(214, 21,  '导出运动员',    'B', NULL, NULL, NULL, 'athlete:export',       5),
(215, 21,  '生成号码',      'B', NULL, NULL, NULL, 'athlete:bib',          6),
(216, 21,  '查看运动员',    'B', NULL, NULL, NULL, 'athlete:query',        7),
(220, 22,  '新增报名',      'B', NULL, NULL, NULL, 'registration:add',     1),
(221, 22,  '修改报名',      'B', NULL, NULL, NULL, 'registration:edit',    2),
(222, 22,  '取消报名',      'B', NULL, NULL, NULL, 'registration:delete',  3),
(223, 22,  '导入报名',      'B', NULL, NULL, NULL, 'registration:import',  4),
(224, 22,  '导出报名',      'B', NULL, NULL, NULL, 'registration:export',  5),
(230, 23,  '报名审核通过',  'B', NULL, NULL, NULL, 'registration:approve', 1),
(231, 23,  '截止并锁定报名','B', NULL, NULL, NULL, 'registration:lock',    2),
(240, 24,  '执行校验',      'B', NULL, NULL, NULL, 'registration:check:exec', 1),
-- 项目与规则
(30,  0,   '项目与规则',    'D', '/event-center',           NULL,                         'Flag',      'event',                 30),
(31,  30,  '项目管理',      'M', '/event/list',             'event/EventList',            'Menu',      'event:list',            31),
(32,  30,  '轮次管理',      'M', '/event/rounds',           'event/RoundList',            'Sort',      'event:round:list',      32),
(33,  30,  '规则配置',      'M', '/event/rules',            'event/RuleConfig',           'Setting',   'event:rule:list',       33),
(34,  30,  '名次分与加分',  'M', '/event/score-rules',      'event/ScoreRule',            'Medal',     'rule:score:list',       34),
(35,  30,  '全能项目配置',  'M', '/event/allround',         'event/AllroundConfig',       'DataLine',  'rule:allround:list',    35),
(310, 31,  '新增项目',      'B', NULL, NULL, NULL, 'event:add',            1),
(311, 31,  '修改项目',      'B', NULL, NULL, NULL, 'event:edit',           2),
(312, 31,  '删除项目',      'B', NULL, NULL, NULL, 'event:delete',         3),
(313, 31,  '批量生成项目',  'B', NULL, NULL, NULL, 'event:batch',          4),
(314, 31,  '查看项目',      'B', NULL, NULL, NULL, 'event:query',          5),
(320, 32,  '新增轮次',      'B', NULL, NULL, NULL, 'event:round:add',      1),
(321, 32,  '修改轮次',      'B', NULL, NULL, NULL, 'event:round:edit',     2),
(322, 32,  '删除轮次',      'B', NULL, NULL, NULL, 'event:round:delete',   3),
(330, 33,  '修改规则',      'B', NULL, NULL, NULL, 'event:rule:edit',      1),
(331, 33,  '导出规则模板',  'B', NULL, NULL, NULL, 'event:rule:export',    2),
(332, 33,  '导入规则模板',  'B', NULL, NULL, NULL, 'event:rule:import',    3),
(340, 34,  '修改名次分表',  'B', NULL, NULL, NULL, 'rule:score:edit',      1),
(341, 34,  '新增名次分方案','B', NULL, NULL, NULL, 'rule:score:add',       2),
(342, 34,  '删除名次分方案','B', NULL, NULL, NULL, 'rule:score:delete',    3),
(343, 34,  '修改加分规则',  'B', NULL, NULL, NULL, 'rule:bonus:edit',      4),
(350, 35,  '修改全能配置',  'B', NULL, NULL, NULL, 'rule:allround:edit',   1),
(351, 35,  '导入计分表',    'B', NULL, NULL, NULL, 'rule:allround:import', 2),
-- 编排管理
(40,  0,   '编排管理',      'D', '/schedule-center',        NULL,                         'Calendar',  'schedule',              40),
(41,  40,  '竞赛单元',      'M', '/schedule/sessions',      'schedule/SessionList',       'Clock',     'schedule:session:list', 41),
(42,  40,  '日程编排',      'M', '/schedule/plan',          'schedule/SchedulePlan',      'Calendar',  'schedule:list',         42),
(43,  40,  '分组分道',      'M', '/schedule/heats',         'schedule/HeatLane',          'Grid',      'schedule:heat:list',    43),
(44,  40,  '冲突检测',      'M', '/schedule/conflicts',     'schedule/ConflictPanel',     'WarningFilled', 'schedule:conflict:list', 44),
(45,  40,  '秩序册',        'M', '/schedule/order-book',    'schedule/OrderBook',         'Notebook',  'print:orderbook',       45),
(410, 41,  '新增单元',      'B', NULL, NULL, NULL, 'schedule:session:add',    1),
(411, 41,  '修改单元',      'B', NULL, NULL, NULL, 'schedule:session:edit',   2),
(412, 41,  '删除单元',      'B', NULL, NULL, NULL, 'schedule:session:delete', 3),
(420, 42,  '自动编排',      'B', NULL, NULL, NULL, 'schedule:auto',           1),
(421, 42,  '调整日程',      'B', NULL, NULL, NULL, 'schedule:edit',           2),
(422, 42,  '锁定日程',      'B', NULL, NULL, NULL, 'schedule:lock',           3),
(423, 42,  '发布日程',      'B', NULL, NULL, NULL, 'schedule:publish',        4),
(424, 42,  '编排快照',      'B', NULL, NULL, NULL, 'schedule:snapshot',       5),
(425, 42,  '新增日程',      'B', NULL, NULL, NULL, 'schedule:add',            6),
(426, 42,  '删除日程',      'B', NULL, NULL, NULL, 'schedule:delete',         7),
(430, 43,  '生成分组分道',  'B', NULL, NULL, NULL, 'schedule:heat:generate',  1),
(431, 43,  '调整分组',      'B', NULL, NULL, NULL, 'schedule:heat:edit',      2),
(432, 43,  '锁定分组',      'B', NULL, NULL, NULL, 'schedule:heat:lock',      3),
(433, 43,  '道次分配',      'B', NULL, NULL, NULL, 'schedule:lane:assign',    4),
(434, 43,  '调整道次',      'B', NULL, NULL, NULL, 'schedule:lane:edit',      5),
(435, 43,  '田赛顺序编排',  'B', NULL, NULL, NULL, 'schedule:field:generate', 6),
(436, 43,  '调整田赛顺序',  'B', NULL, NULL, NULL, 'schedule:field:edit',     7),
(440, 44,  '执行冲突检测',  'B', NULL, NULL, NULL, 'schedule:conflict:detect',  1),
(441, 44,  '处理冲突',      'B', NULL, NULL, NULL, 'schedule:conflict:resolve', 2),
-- 赛前管理
(50,  0,   '赛前管理',      'D', '/precheck-center',        NULL,                         'Finished',  'precheck',              50),
(51,  50,  '检录管理',      'M', '/precheck/calling',       'precheck/CallingList',       'Bell',      'calling:list',          51),
(52,  50,  '抽签管理',      'M', '/precheck/draw',          'precheck/DrawPanel',         'MagicStick','draw:list',             52),
(53,  50,  '参赛名单',      'M', '/precheck/start-list',    'precheck/StartList',         'Tickets',   'startlist:list',        53),
(54,  50,  '裁判分工',      'M', '/precheck/referee-assign','precheck/RefereeAssign',     'UserFilled','referee:assign:list',   54),
(510, 51,  '检录登记',      'B', NULL, NULL, NULL, 'calling:edit',           1),
(511, 51,  '导出检录表',    'B', NULL, NULL, NULL, 'calling:export',         2),
(520, 52,  '随机抽签',      'B', NULL, NULL, NULL, 'draw:random',            1),
(521, 52,  '种子抽签',      'B', NULL, NULL, NULL, 'draw:seed',              2),
(522, 52,  '道次抽签',      'B', NULL, NULL, NULL, 'draw:lane',              3),
(530, 53,  '发布参赛名单',  'B', NULL, NULL, NULL, 'startlist:publish',      1),
(531, 53,  '导出参赛名单',  'B', NULL, NULL, NULL, 'startlist:export',       2),
(540, 54,  '分配裁判岗位',  'B', NULL, NULL, NULL, 'referee:assign:edit',    1),
(541, 54,  '自动分配岗位',  'B', NULL, NULL, NULL, 'referee:assign:auto',    2),
(542, 54,  '导出裁判任务单','B', NULL, NULL, NULL, 'referee:assign:export',  3),
-- 成绩管理
(60,  0,   '成绩管理',      'D', '/result-center',          NULL,                         'Stopwatch', 'result_mgmt',           60),
(61,  60,  '成绩录入',      'M', '/result/entry',           'result/ResultEntry',         'EditPen',   'result:list',           61),
(62,  60,  '成绩审核',      'M', '/result/audit',           'result/ResultAudit',         'Checked',   'result:audit:list',     62),
(63,  60,  '成绩公告',      'M', '/result/notice',          'result/ResultNotice',        'Bell',      'result:notice:list',    63),
(64,  60,  '晋级管理',      'M', '/result/advance',         'result/AdvanceManage',       'Ranking',   'result:advance:list',   64),
(65,  60,  '纪录管理',      'M', '/result/records',         'result/RecordManage',        'Medal',     'record:list',           65),
(610, 61,  '录入成绩',      'B', NULL, NULL, NULL, 'result:add',             1),
(611, 61,  '修改成绩',      'B', NULL, NULL, NULL, 'result:edit',            2),
(612, 61,  '标记特殊状态',  'B', NULL, NULL, NULL, 'result:status',          3),
(613, 61,  '录入风速',      'B', NULL, NULL, NULL, 'result:wind',            4),
(614, 61,  '提交审核',      'B', NULL, NULL, NULL, 'result:submit',          5),
(615, 61,  '录入试跳试掷',  'B', NULL, NULL, NULL, 'result:attempt:add',     6),
(620, 62,  '审核通过',      'B', NULL, NULL, NULL, 'result:approve',         1),
(621, 62,  '审核驳回',      'B', NULL, NULL, NULL, 'result:reject',          2),
(622, 62,  '撤销审核',      'B', NULL, NULL, NULL, 'result:revoke',          3),
(630, 63,  '发布成绩公告',  'B', NULL, NULL, NULL, 'result:notice',          1),
(631, 63,  '导出成绩公告',  'B', NULL, NULL, NULL, 'result:notice:export',   2),
(640, 64,  '生成晋级名单',  'B', NULL, NULL, NULL, 'result:advance',         1),
(641, 64,  '调整晋级名单',  'B', NULL, NULL, NULL, 'result:advance:edit',    2),
(650, 65,  '新增纪录',      'B', NULL, NULL, NULL, 'record:add',             1),
(651, 65,  '修改纪录',      'B', NULL, NULL, NULL, 'record:edit',            2),
(652, 65,  '删除纪录',      'B', NULL, NULL, NULL, 'record:delete',          3),
(653, 65,  '破纪录认定',    'B', NULL, NULL, NULL, 'result:record',          4),
-- 统计与积分
(70,  0,   '统计与积分',    'D', '/standing-center',        NULL,                         'DataAnalysis', 'standing',          70),
(71,  70,  '积分榜',        'M', '/standing/points',        'standing/PointsBoard',       'Histogram', 'standing:points',       71),
(72,  70,  '奖牌榜',        'M', '/standing/medals',        'standing/MedalBoard',        'Medal',     'standing:medal',        72),
(73,  70,  '成绩册',        'M', '/standing/result-book',   'standing/ResultBook',        'Notebook',  'print:results',         73),
(710, 71,  '重算积分',      'B', NULL, NULL, NULL, 'standing:recalc',        1),
(711, 71,  '保存榜单快照',  'B', NULL, NULL, NULL, 'standing:snapshot',      2),
-- 打印与导出
(80,  0,   '打印与导出',    'D', '/print-center',           NULL,                         'Printer',   'print_dir',             80),
(81,  80,  '打印中心',      'M', '/print/center',           'print/PrintCenter',          'Printer',   'print:center',          81),
(82,  80,  '打印模板配置',  'M', '/print/templates',        'print/TemplateConfig',       'Files',     'print:template:list',   82),
(83,  80,  '证书打印',      'M', '/certificate/list',       'certificate/CertificateList','Postcard',  'certificate:list',      83),
(810, 81,  '打印秩序册',    'B', NULL, NULL, NULL, 'print:orderbook:print',  1),
(811, 81,  '打印检录表',    'B', NULL, NULL, NULL, 'print:calling',          2),
(812, 81,  '打印积分奖牌榜','B', NULL, NULL, NULL, 'print:standings',        3),
(813, 81,  '批量打印导出',  'B', NULL, NULL, NULL, 'print:batch',            4),
(820, 82,  '新增打印模板',  'B', NULL, NULL, NULL, 'print:template:add',     1),
(821, 82,  '修改打印模板',  'B', NULL, NULL, NULL, 'print:template:edit',    2),
(830, 83,  '生成证书',      'B', NULL, NULL, NULL, 'certificate:generate',   1),
(831, 83,  '打印证书',      'B', NULL, NULL, NULL, 'certificate:print',      2),
(832, 83,  '导出证书',      'B', NULL, NULL, NULL, 'certificate:export',     3),
-- 公告与大屏
(90,  0,   '公告与大屏',    'D', '/screen-center',          NULL,                         'Monitor',   'screen_dir',            90),
(91,  90,  '公告管理',      'M', '/notice/list',            'notice/NoticeList',          'ChatDotRound', 'notice:list',        91),
(92,  90,  '大屏配置',      'M', '/screen/config',          'screen/ScreenConfig',        'Monitor',   'screen:config:list',    92),
(910, 91,  '新增公告',      'B', NULL, NULL, NULL, 'notice:add',             1),
(911, 91,  '发布公告',      'B', NULL, NULL, NULL, 'notice:publish',         2),
(920, 92,  '修改大屏配置',  'B', NULL, NULL, NULL, 'screen:config:edit',     1),
(921, 92,  '生成大屏令牌',  'B', NULL, NULL, NULL, 'screen:token',           2),
-- 系统管理
(100, 0,   '系统管理',      'D', '/system-center',          NULL,                         'Setting',   'system',               100),
(101, 100, '用户管理',      'M', '/system/users',           'system/UserList',            'User',      'system:user:list',     101),
(102, 100, '角色权限',      'M', '/system/roles',           'system/RoleList',            'Lock',      'system:role:list',     102),
(103, 100, '菜单管理',      'M', '/system/menus',           'system/MenuList',            'Menu',      'system:menu:list',     103),
(104, 100, '部门管理',      'M', '/system/depts',           'system/DeptList',            'OfficeBuilding', 'system:dept:list',104),
(105, 100, '字典管理',      'M', '/system/dicts',           'system/DictList',            'Collection','system:dict:list',     105),
(106, 100, '操作日志',      'M', '/system/logs/operation',  'system/OperationLog',        'Document',  'system:log:operation', 106),
(107, 100, '登录日志',      'M', '/system/logs/login',      'system/LoginLog',            'Key',       'system:log:login',     107),
(108, 100, '数据备份',      'M', '/system/backup',          'system/BackupManage',        'FolderOpened', 'system:backup:list',109),
(109, 100, '系统设置',      'M', '/system/settings',        'system/Settings',            'Tools',     'system:config:list',   110),
(1010, 101, '新增用户',     'B', NULL, NULL, NULL, 'system:user:add',        1),
(1011, 101, '修改用户',     'B', NULL, NULL, NULL, 'system:user:edit',       2),
(1012, 101, '删除用户',     'B', NULL, NULL, NULL, 'system:user:delete',     3),
(1013, 101, '重置密码',     'B', NULL, NULL, NULL, 'system:user:resetPwd',   4),
(1014, 101, '分配角色',     'B', NULL, NULL, NULL, 'system:user:assignRole', 5),
(1015, 101, '查看用户',     'B', NULL, NULL, NULL, 'system:user:query',      6),
(1020, 102, '新增角色',     'B', NULL, NULL, NULL, 'system:role:add',        1),
(1021, 102, '修改角色',     'B', NULL, NULL, NULL, 'system:role:edit',       2),
(1022, 102, '删除角色',     'B', NULL, NULL, NULL, 'system:role:delete',     3),
(1023, 102, '角色授权',     'B', NULL, NULL, NULL, 'system:role:auth',       4),
(1024, 102, '查看角色',     'B', NULL, NULL, NULL, 'system:role:query',      5),
-- 权限点维护（权限树在「角色权限」页面内以抽屉方式呈现，故不单独建菜单）
(1025, 102, '查看权限',     'B', NULL, NULL, NULL, 'system:perm:list',       6),
(1026, 102, '新增权限',     'B', NULL, NULL, NULL, 'system:perm:add',        7),
(1027, 102, '修改权限',     'B', NULL, NULL, NULL, 'system:perm:edit',       8),
(1028, 102, '删除权限',     'B', NULL, NULL, NULL, 'system:perm:delete',     9),
(1030, 103, '新增菜单',     'B', NULL, NULL, NULL, 'system:menu:add',        1),
(1031, 103, '修改菜单',     'B', NULL, NULL, NULL, 'system:menu:edit',       2),
(1032, 103, '删除菜单',     'B', NULL, NULL, NULL, 'system:menu:delete',     3),
(1033, 103, '查看菜单',     'B', NULL, NULL, NULL, 'system:menu:query',      4),
(1040, 104, '新增部门',     'B', NULL, NULL, NULL, 'system:dept:add',        1),
(1041, 104, '修改部门',     'B', NULL, NULL, NULL, 'system:dept:edit',       2),
(1042, 104, '删除部门',     'B', NULL, NULL, NULL, 'system:dept:delete',     3),
(1050, 105, '新增字典',     'B', NULL, NULL, NULL, 'system:dict:add',        1),
(1051, 105, '修改字典',     'B', NULL, NULL, NULL, 'system:dict:edit',       2),
(1052, 105, '删除字典',     'B', NULL, NULL, NULL, 'system:dict:delete',     3),
(1080, 108, '手动备份',     'B', NULL, NULL, NULL, 'system:backup:add',      1),
(1081, 108, '数据恢复',     'B', NULL, NULL, NULL, 'system:backup:restore',  2),
(1082, 108, '下载备份',     'B', NULL, NULL, NULL, 'system:backup:download', 3),
(1090, 109, '修改系统参数', 'B', NULL, NULL, NULL, 'system:config:edit',     1),
-- 个人中心
(1111, 0,  '个人中心',      'M', '/profile',                'profile/Index',              'UserFilled','profile:view',         110);

-- ----------------------------------------------------------------------------
-- 3. 权限点（由菜单派生：MENU 对应目录/菜单，BUTTON 对应按钮）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `menu_id`, `module`, `is_builtin`, `status`, `sort_no`)
SELECT m.`perm_code`, m.`menu_name`, 'MENU', m.`id`,
       CASE
         WHEN m.`perm_code` LIKE 'system%' THEN 'SYSTEM'
         WHEN m.`perm_code` LIKE 'meet%' THEN 'MEET'
         WHEN m.`perm_code` LIKE 'athlete%' OR m.`perm_code` LIKE 'registration%' THEN 'ATHLETE'
         WHEN m.`perm_code` LIKE 'event%' OR m.`perm_code` LIKE 'rule%' THEN 'EVENT'
         WHEN m.`perm_code` LIKE 'schedule%' THEN 'SCHEDULE'
         WHEN m.`perm_code` LIKE 'calling%' OR m.`perm_code` LIKE 'draw%'
              OR m.`perm_code` LIKE 'startlist%' OR m.`perm_code` LIKE 'referee%' THEN 'PRECHECK'
         WHEN m.`perm_code` LIKE 'result%' OR m.`perm_code` LIKE 'record%' THEN 'RESULT'
         WHEN m.`perm_code` LIKE 'standing%' THEN 'STANDING'
         WHEN m.`perm_code` LIKE 'print%' OR m.`perm_code` LIKE 'certificate%' THEN 'PRINT'
         WHEN m.`perm_code` LIKE 'notice%' OR m.`perm_code` LIKE 'screen%' THEN 'SCREEN'
         ELSE 'COMMON'
       END,
       1, 'ENABLED', m.`sort_no`
FROM `sys_menu` m
WHERE m.`menu_type` IN ('D', 'M') AND m.`perm_code` IS NOT NULL AND m.`deleted` = 0;

INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `menu_id`, `module`, `is_builtin`, `status`, `sort_no`)
SELECT m.`perm_code`, m.`menu_name`, 'BUTTON', m.`id`,
       CASE
         WHEN m.`perm_code` LIKE 'system%' THEN 'SYSTEM'
         WHEN m.`perm_code` LIKE 'meet%' THEN 'MEET'
         WHEN m.`perm_code` LIKE 'athlete%' OR m.`perm_code` LIKE 'registration%' THEN 'ATHLETE'
         WHEN m.`perm_code` LIKE 'event%' OR m.`perm_code` LIKE 'rule%' THEN 'EVENT'
         WHEN m.`perm_code` LIKE 'schedule%' THEN 'SCHEDULE'
         WHEN m.`perm_code` LIKE 'calling%' OR m.`perm_code` LIKE 'draw%'
              OR m.`perm_code` LIKE 'startlist%' OR m.`perm_code` LIKE 'referee%' THEN 'PRECHECK'
         WHEN m.`perm_code` LIKE 'result%' OR m.`perm_code` LIKE 'record%' THEN 'RESULT'
         WHEN m.`perm_code` LIKE 'standing%' THEN 'STANDING'
         WHEN m.`perm_code` LIKE 'print%' OR m.`perm_code` LIKE 'certificate%' THEN 'PRINT'
         WHEN m.`perm_code` LIKE 'notice%' OR m.`perm_code` LIKE 'screen%' THEN 'SCREEN'
         ELSE 'COMMON'
       END,
       1, 'ENABLED', m.`sort_no`
FROM `sys_menu` m
WHERE m.`menu_type` = 'B' AND m.`perm_code` IS NOT NULL AND m.`deleted` = 0;

-- ----------------------------------------------------------------------------
-- 4. 内置角色（10 个，与《角色与权限矩阵》一致）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `data_scope`, `status`, `is_builtin`, `sort_no`, `remark`) VALUES
(1,  'SUPER_ADMIN',   '超级管理员',   'ALL',  'ENABLED', 1, 1,  '系统所有者：用户/角色/权限/字典/备份/日志'),
(2,  'MEET_ADMIN',    '竞赛主管',     'MEET', 'ENABLED', 1, 2,  '一届运动会负责人：规程、规则、审核、发布'),
(3,  'SCHEDULER',     '编排员',       'MEET', 'ENABLED', 1, 3,  '项目设置、自动编排、分组成道、日程调整'),
(4,  'REGISTRAR',     '报名管理员',   'MEET', 'ENABLED', 1, 4,  '运动员建档、号码、报名与审核'),
(5,  'CHIEF_REFEREE', '裁判长',       'MEET', 'ENABLED', 1, 5,  '成绩审核、判罚确认、破纪录认定'),
(6,  'REFEREE',       '裁判',         'REF',  'ENABLED', 1, 6,  '检录、成绩录入（仅被分配项目/单元）'),
(7,  'ANNOUNCER',     '宣告员',       'MEET', 'ENABLED', 1, 7,  '成绩公告、大屏控制、证书生成'),
(8,  'TEAM_LEADER',   '领队教练',     'UNIT', 'ENABLED', 1, 8,  '本单位报名、名单与成绩查看'),
(9,  'ATHLETE',       '运动员',       'SELF', 'ENABLED', 1, 9,  '查看个人项目、检录时间与成绩'),
(10, 'VIEWER',        '观众',         'REF',  'ENABLED', 1, 10, '公开成绩、公告、奖牌榜只读');

-- ----------------------------------------------------------------------------
-- 5. 角色授权
-- ----------------------------------------------------------------------------
-- 5.1 超级管理员：全部权限
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN';

-- 5.2 所有角色都拥有目录容器（D），保证导航分组可见
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.`perm_type` = 'MENU'
JOIN `sys_menu` m ON m.`id` = p.`menu_id` AND m.`menu_type` = 'D'
WHERE r.`role_code` <> 'SUPER_ADMIN';

-- 5.3 竞赛主管：除系统用户/角色/菜单/部门/备份/参数之外的权限
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'MEET_ADMIN'
  AND p.`perm_code` NOT LIKE 'system:user%'
  AND p.`perm_code` NOT LIKE 'system:role%'
  AND p.`perm_code` NOT LIKE 'system:perm%'
  AND p.`perm_code` NOT LIKE 'system:menu%'
  AND p.`perm_code` NOT LIKE 'system:dept%'
  AND p.`perm_code` NOT LIKE 'system:backup%'
  AND p.`perm_code` NOT LIKE 'system:config%';

-- 5.4 编排员：项目规则 + 编排 + 报名与成绩只读 + 秩序册
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'SCHEDULER'
  AND (p.`perm_code` LIKE 'event:%'
    OR p.`perm_code` LIKE 'rule:%'
    OR p.`perm_code` LIKE 'schedule:%'
    OR p.`perm_code` LIKE 'meet:group:%'
    OR p.`perm_code` LIKE 'meet:venue:%'
    OR p.`perm_code` LIKE 'draw:%'
    OR p.`perm_code` LIKE 'referee:assign:%'
    OR p.`perm_code` LIKE 'standing:%'
    OR p.`perm_code` LIKE 'result:advance%'
    OR p.`perm_code` LIKE 'print:orderbook'
    OR p.`perm_code` LIKE 'print:center'
    OR p.`perm_code` LIKE 'print:standings'
    OR p.`perm_code` LIKE 'print:batch'
    OR p.`perm_code` IN ('dashboard:view', 'meet:list', 'meet:query', 'meet:group:list', 'meet:group:query',
                         'meet:venue:list', 'meet:referee:list', 'athlete:list', 'athlete:query',
                         'registration:list', 'registration:check', 'registration:check:exec',
                         'event:list', 'event:round:list', 'event:rule:list', 'rule:score:list',
                         'rule:allround:list', 'schedule:list', 'schedule:heat:list', 'schedule:conflict:list',
                         'calling:list', 'startlist:list', 'result:list', 'result:advance:list',
                         'record:list', 'standing:points', 'standing:medal', 'print:results', 'profile:view'));

-- 5.5 报名管理员：运动员 + 报名 + 审核 + 单位 + 名单打印
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'REGISTRAR'
  AND (p.`perm_code` LIKE 'athlete:%'
    OR p.`perm_code` LIKE 'registration:%'
    OR p.`perm_code` LIKE 'meet:unit:%'
    OR p.`perm_code` LIKE 'startlist:%'
    OR p.`perm_code` LIKE 'calling:export'
    OR p.`perm_code` IN ('dashboard:view', 'meet:list', 'meet:query', 'meet:group:list', 'meet:group:query',
                         'meet:unit:list', 'event:list', 'athlete:list', 'registration:list',
                         'registration:audit', 'registration:check', 'startlist:list',
                         'print:center', 'profile:view'));

-- 5.6 裁判长：成绩审核、纪录认定、检录、榜单只读
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'CHIEF_REFEREE'
  AND (p.`perm_code` LIKE 'result:%'
    OR p.`perm_code` LIKE 'record:%'
    OR p.`perm_code` LIKE 'calling:%'
    OR p.`perm_code` LIKE 'standing:%'
    OR p.`perm_code` LIKE 'print:results'
    OR p.`perm_code` LIKE 'print:calling'
    OR p.`perm_code` IN ('dashboard:view', 'meet:list', 'meet:query', 'event:list', 'event:round:list',
                         'event:rule:list', 'rule:score:list', 'rule:allround:list', 'schedule:list',
                         'schedule:conflict:list', 'schedule:conflict:resolve', 'calling:list',
                         'startlist:list', 'result:list', 'result:audit:list', 'record:list',
                         'standing:points', 'standing:medal', 'athlete:list', 'registration:list', 'profile:view'));

-- 5.7 裁判：检录 + 成绩录入（数据范围 REF，仅被分配项目）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'REFEREE'
  AND p.`perm_code` IN ('dashboard:view', 'calling:list', 'calling:edit', 'calling:export', 'result:list',
                        'result:add', 'result:edit', 'result:status', 'result:wind', 'result:submit',
                        'result:attempt:add', 'schedule:list', 'schedule:heat:list', 'meet:query',
                        'event:list', 'athlete:list', 'startlist:list', 'standing:medal',
                        'referee:assign:list', 'print:calling', 'profile:view');

-- 5.8 宣告员：成绩公告、证书、榜单、大屏
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'ANNOUNCER'
  AND (p.`perm_code` LIKE 'certificate:%'
    OR p.`perm_code` LIKE 'notice:%'
    OR p.`perm_code` LIKE 'screen:%'
    OR p.`perm_code` LIKE 'print:%'
    OR p.`perm_code` LIKE 'standing:%'
    OR p.`perm_code` IN ('dashboard:view', 'meet:query', 'result:list', 'result:audit:list',
                         'result:notice:list', 'result:notice', 'result:notice:export', 'record:list',
                         'startlist:list', 'calling:list', 'print:results', 'profile:view'));

-- 5.9 领队教练：本单位报名与查询（数据范围 UNIT）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'TEAM_LEADER'
  AND p.`perm_code` IN ('dashboard:view', 'athlete:list', 'athlete:query', 'athlete:add', 'athlete:edit',
                        'athlete:import', 'registration:list', 'registration:add', 'registration:edit',
                        'registration:import', 'registration:check', 'meet:unit:list', 'meet:group:list',
                        'event:list', 'startlist:list', 'startlist:export', 'standing:medal',
                        'standing:points', 'result:list', 'notice:list', 'print:center', 'profile:view');

-- 5.10 运动员：个人查询（数据范围 SELF）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'ATHLETE'
  AND p.`perm_code` IN ('dashboard:view', 'athlete:query', 'registration:list', 'startlist:list',
                        'result:list', 'standing:medal', 'notice:list', 'profile:view');

-- 5.11 观众：公开只读
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `menu_id`, `perm_type`, `grant_source`, `grant_time`, `is_effective`, `create_time`)
SELECT r.`id`, p.`id`, p.`menu_id`, p.`perm_type`, 'INIT', NOW(), 1, NOW()
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'VIEWER'
  AND p.`perm_code` IN ('dashboard:view', 'standing:medal', 'standing:points', 'result:list',
                        'result:notice:list', 'notice:list', 'print:results', 'screen:config:list');

-- ----------------------------------------------------------------------------
-- 6. 内置账号（口令占位符，首次启动由 DefaultAccountInitializer 初始化为 BCrypt）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `status`, `is_builtin`,
                        `pwd_error_count`, `force_pwd_change`, `token_version`, `sort_no`, `remark`)
VALUES (1, 'admin', 'INIT_PLACEHOLDER', '超级管理员', 'ENABLED', 1, 0, 0, 0, 1,
        '内置账号，初始密码见 trackmeet.default-password（默认 Admin@123），首次登录请立即修改');

INSERT INTO `sys_user_role` (`user_id`, `role_id`, `grant_source`, `grant_time`, `is_primary`, `is_effective`)
VALUES (1, 1, 'INIT', NOW(), 1, 1);

-- ----------------------------------------------------------------------------
-- 7. 字典（项目类型、子类型、性别、报名与成绩状态、犯规类型、裁判岗位、证书类型、风速规则等）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_dict_type` (`dict_type`, `dict_name`, `dict_group`, `is_system`, `is_global`, `sort_no`, `remark`) VALUES
('event_type',          '项目类型',       'TRACK_FIELD',  1, 1, 1,  '径赛/田赛/全能/接力'),
('track_sub_type',      '径赛子类型',     'TRACK_FIELD',  1, 1, 2,  '短跑/中跑/长跑/跨栏/障碍/竞走/接力'),
('field_sub_type',      '田赛子类型',     'TRACK_FIELD',  1, 1, 3,  '跳跃/投掷'),
('gender',              '性别',           'COMMON',       1, 1, 4,  '男/女/混合'),
('meet_status',         '运动会状态',     'MEET',         1, 1, 5,  '草稿→报名中→…→归档'),
('registration_status', '报名状态',       'REGISTRATION', 1, 1, 6,  '待审/通过/退回/取消'),
('audit_status',        '审核状态',       'COMMON',       1, 1, 7,  '草稿/待审/已审/驳回'),
('result_status',       '成绩状态',       'RESULT',       1, 1, 8,  '有效/DNS/DNF/DSQ/NM/免跳/弃权'),
('foul_type',           '犯规类型',       'RESULT',       1, 1, 9,  '抢跑/串道/阻挡/越线/超时等'),
('record_type',         '纪录层级',       'RESULT',       1, 1, 10, '世界/亚洲/全国/省/市/赛会/组别'),
('referee_duty',        '裁判岗位',       'PRECHECK',     1, 1, 11, '起点/终点/计时/检查/田赛/记录/宣告'),
('cert_type',           '证书类型',       'PRINT',        1, 1, 12, '获奖/成绩/奖牌/参赛'),
('wind_rule',           '风速规则',       'EVENT',        1, 1, 13, '合法上限与适用项目'),
('round_code',          '轮次类型',       'EVENT',        1, 1, 14, '及格赛/预赛/次赛/复赛/半决赛/决赛'),
('lane_assign_mode',    '道次分配方式',   'SCHEDULE',     1, 1, 15, '按成绩/抽签/蛇形/顺序'),
('draw_mode',           '分组方式',       'SCHEDULE',     1, 1, 16, '蛇形/随机/按成绩段/按单位'),
('backup_type',         '备份类型',       'SYSTEM',       1, 1, 17, '手动/自动'),
('notice_type',         '公告类型',       'SCREEN',       1, 1, 18, '通知/成绩/日程/紧急'),
('data_scope',          '数据范围',       'SYSTEM',       1, 1, 19, '全部/本届次/本单位/本人/自定义'),
('qualification_status','参赛资格状态',   'ATHLETE',      1, 1, 20, '待审/合格/不合格/禁赛');

INSERT INTO `sys_dict_item` (`dict_type`, `item_label`, `item_value`, `css_class`, `is_default`, `sort_no`) VALUES
-- 项目类型
('event_type', '径赛', 'TRACK',    'primary', 1, 1),
('event_type', '田赛', 'FIELD',    'success', 0, 2),
('event_type', '全能', 'ALLROUND', 'warning', 0, 3),
('event_type', '接力', 'RELAY',    'danger',  0, 4),
-- 径赛子类型
('track_sub_type', '短跑',   'SPRINT',  NULL, 1, 1),
('track_sub_type', '中跑',   'MIDDLE',  NULL, 0, 2),
('track_sub_type', '长跑',   'LONG',    NULL, 0, 3),
('track_sub_type', '跨栏',   'HURDLE',  NULL, 0, 4),
('track_sub_type', '障碍赛', 'STEEPLE', NULL, 0, 5),
('track_sub_type', '竞走',   'WALK',    NULL, 0, 6),
('track_sub_type', '接力',   'RELAY',   NULL, 0, 7),
-- 田赛子类型
('field_sub_type', '跳跃', 'JUMP',  NULL, 1, 1),
('field_sub_type', '投掷', 'THROW', NULL, 0, 2),
-- 性别
('gender', '男',   'MALE',   'primary', 1, 1),
('gender', '女',   'FEMALE', 'danger',  0, 2),
('gender', '混合', 'MIXED',  'info',    0, 3),
-- 运动会状态
('meet_status', '草稿',     'DRAFT',      'info',    1, 1),
('meet_status', '报名中',   'REG_OPEN',   'success', 0, 2),
('meet_status', '报名截止', 'REG_CLOSED', 'warning', 0, 3),
('meet_status', '编排中',   'SCHEDULING', 'warning', 0, 4),
('meet_status', '编排完成', 'SCHEDULED',  'primary', 0, 5),
('meet_status', '比赛进行', 'RUNNING',    'danger',  0, 6),
('meet_status', '已结束',   'FINISHED',   'info',    0, 7),
('meet_status', '已归档',   'ARCHIVED',   'info',    0, 8),
-- 报名状态
('registration_status', '待审核', 'PENDING',   'warning', 1, 1),
('registration_status', '已通过', 'APPROVED',  'success', 0, 2),
('registration_status', '已退回', 'REJECTED',  'danger',  0, 3),
('registration_status', '已取消', 'CANCELLED', 'info',    0, 4),
-- 审核状态
('audit_status', '草稿',   'DRAFT',     'info',    1, 1),
('audit_status', '待审核', 'SUBMITTED', 'warning', 0, 2),
('audit_status', '已通过', 'APPROVED',  'success', 0, 3),
('audit_status', '已驳回', 'REJECTED',  'danger',  0, 4),
-- 成绩状态
('result_status', '有效',     'VALID',   'success', 1, 1),
('result_status', '未出发',   'DNS',     'info',    0, 2),
('result_status', '未完成',   'DNF',     'warning', 0, 3),
('result_status', '取消资格', 'DSQ',     'danger',  0, 4),
('result_status', '无有效成绩','NM',     'info',    0, 5),
('result_status', '免跳',     'PASS',    'info',    0, 6),
('result_status', '弃权',     'SCRATCH', 'info',    0, 7),
-- 犯规类型
('foul_type', '抢跑',     'FALSE_START',  'danger',  0, 1),
('foul_type', '串道',     'LANE_VIOLATION', 'danger', 0, 2),
('foul_type', '阻挡',     'OBSTRUCTION',  'danger',  0, 3),
('foul_type', '踩线',     'OVERSTEP',     'danger',  0, 4),
('foul_type', '超时',     'TIME_LIMIT',   'warning', 0, 5),
('foul_type', '器材违规', 'EQUIPMENT',    'warning', 0, 6),
('foul_type', '其他',     'OTHER',        'info',    0, 7),
-- 纪录层级
('record_type', '世界纪录', 'WORLD',    'danger',  0, 1),
('record_type', '亚洲纪录', 'ASIA',     'danger',  0, 2),
('record_type', '全国纪录', 'NATION',   'warning', 0, 3),
('record_type', '省级纪录', 'PROVINCE', 'warning', 0, 4),
('record_type', '市级纪录', 'CITY',     'primary', 0, 5),
('record_type', '赛会纪录', 'MEET',     'success', 1, 6),
('record_type', '组别纪录', 'GROUP',    'info',    0, 7),
-- 裁判岗位
('referee_duty', '起点裁判',   'START',      NULL, 0, 1),
('referee_duty', '终点裁判',   'FINISH',     NULL, 0, 2),
('referee_duty', '计时裁判',   'TIMER',      NULL, 0, 3),
('referee_duty', '检查裁判',   'INSPECT',    NULL, 0, 4),
('referee_duty', '田赛裁判',   'FIELD',      NULL, 0, 5),
('referee_duty', '记录员',     'RECORDER',   NULL, 0, 6),
('referee_duty', '宣告员',     'ANNOUNCER',  NULL, 0, 7),
('referee_duty', '检录裁判',   'CALLING',    NULL, 0, 8),
('referee_duty', '发令员',     'STARTER',    NULL, 0, 9),
-- 证书类型
('cert_type', '获奖证书', 'RANK',          'success', 1, 1),
('cert_type', '成绩证书', 'RESULT',        'primary', 0, 2),
('cert_type', '奖牌记录', 'MEDAL',         'warning', 0, 3),
('cert_type', '参赛证书', 'PARTICIPATION', 'info',    0, 4),
-- 风速规则
('wind_rule', '电子风速仪', 'ELECTRONIC', NULL, 1, 1),
('wind_rule', '机械风速仪', 'MECHANICAL', NULL, 0, 2),
('wind_rule', '不计风速',   'NONE',       NULL, 0, 3),
-- 轮次类型
('round_code', '及格赛', 'QUALIFY', 'info',    0, 1),
('round_code', '预赛',   'PRE',     'primary', 1, 2),
('round_code', '次赛',   'R1',      'primary', 0, 3),
('round_code', '复赛',   'R2',      'primary', 0, 4),
('round_code', '半决赛', 'SEMI',    'warning', 0, 5),
('round_code', '决赛',   'FINAL',   'danger',  0, 6),
-- 道次分配方式
('lane_assign_mode', '按成绩', 'SEED',    NULL, 1, 1),
('lane_assign_mode', '抽签',   'DRAW',    NULL, 0, 2),
('lane_assign_mode', '蛇形',   'SERPENT', NULL, 0, 3),
('lane_assign_mode', '顺序',   'ORDER',   NULL, 0, 4),
-- 分组方式
('draw_mode', '蛇形',       'SERPENT', NULL, 1, 1),
('draw_mode', '随机',       'RANDOM',  NULL, 0, 2),
('draw_mode', '按成绩段',   'SEED',    NULL, 0, 3),
('draw_mode', '按单位顺序', 'UNIT',    NULL, 0, 4),
-- 备份类型
('backup_type', '手动', 'MANUAL', NULL, 1, 1),
('backup_type', '自动', 'AUTO',   NULL, 0, 2),
-- 公告类型
('notice_type', '通知', 'NOTICE',   'info',    1, 1),
('notice_type', '成绩', 'RESULT',   'success', 0, 2),
('notice_type', '日程', 'SCHEDULE', 'primary', 0, 3),
('notice_type', '紧急', 'URGENT',   'danger',  0, 4),
-- 数据范围
('data_scope', '全部',     'ALL',  NULL, 0, 1),
('data_scope', '本届次',   'MEET', NULL, 0, 2),
('data_scope', '本单位',   'UNIT', NULL, 0, 3),
('data_scope', '本人',     'SELF', NULL, 0, 4),
('data_scope', '自定义',   'REF',  NULL, 0, 5),
-- 参赛资格状态
('qualification_status', '待审',   'PENDING',       'warning', 1, 1),
('qualification_status', '合格',   'QUALIFIED',     'success', 0, 2),
('qualification_status', '不合格', 'DISQUALIFIED',  'danger',  0, 3),
('qualification_status', '禁赛',   'BANNED',        'danger',  0, 4);

-- ----------------------------------------------------------------------------
-- 8. 全局参考数据（跨届次共用：裁判岗位、项目时长与顺序默认、全能计分表结构示例）
-- ----------------------------------------------------------------------------
-- 8.1 裁判岗位字典（referee_duty：全局表，无 meet_id）
INSERT INTO `referee_duty` (`duty_code`, `duty_name`, `duty_group`, `event_type_scope`, `default_headcount`,
                            `min_headcount`, `is_chief_post`, `is_concurrent_allowed`, `conflict_group`, `sort_no`, `status`, `remark`)
VALUES ('CHIEF',    '裁判长',   'OTHER',  'ALL',   1, 1, 1, 0, 'CHIEF_GROUP',   1, 'ENABLED', '总裁判长，每场唯一'),
       ('STARTER',  '发令员',   'TRACK',  'TRACK', 1, 1, 0, 0, 'START_GROUP',   2, 'ENABLED', '发令与召回'),
       ('START',    '起点裁判', 'TRACK',  'TRACK', 2, 1, 0, 0, 'START_GROUP',   3, 'ENABLED', '起跑犯规判定'),
       ('FINISH',   '终点裁判', 'TRACK',  'TRACK', 4, 2, 0, 0, 'FINISH_GROUP',  4, 'ENABLED', '名次判定'),
       ('TIMER',    '计时裁判', 'TRACK',  'TRACK', 8, 4, 0, 1, 'TIMER_GROUP',   5, 'ENABLED', '手计时/电子计时'),
       ('INSPECT',  '检查裁判', 'TRACK',  'TRACK', 4, 2, 0, 1, NULL,            6, 'ENABLED', '串道与阻挡检查'),
       ('CALLING',  '检录裁判', 'TRACK',  'ALL',   2, 2, 0, 0, 'CALLING_GROUP', 7, 'ENABLED', '检录与到检登记'),
       ('FIELD',    '田赛裁判', 'FIELD',  'FIELD', 6, 4, 0, 1, NULL,            8, 'ENABLED', '试跳试掷判定与丈量'),
       ('RECORDER', '记录员',   'RECORD', 'ALL',   2, 1, 0, 0, 'RECORD_GROUP',  9, 'ENABLED', '成绩记录与核对'),
       ('ANNOUNCER','宣告员',   'OTHER',  'ALL',   1, 1, 0, 0, NULL,           10, 'ENABLED', '现场宣告');

-- 8.2 项目时长与顺序默认规则（event_schedule_rule：全局表，无 meet_id）
--     供阶段 5「自动编排」估算时长与排序，可在届次/项目级覆盖
INSERT INTO `event_schedule_rule` (`event_type`, `sub_type`, `gender`, `default_duration_min`, `call_ahead_min`,
                                   `default_order`, `track_priority`, `default_lane_count`, `period_preference`,
                                   `max_rounds_per_day`, `event_interval_min`, `is_field_order_required`, `status`, `remark`)
VALUES ('TRACK',    'SPRINT',  NULL, 15,  20, 10, 1, 8,    'ANY',       3, 30, 0, 'ENABLED', '短跑：分组分道，轮次间隔 30 分钟'),
       ('TRACK',    'MIDDLE',  NULL, 25,  20, 20, 1, NULL, 'ANY',       2, 60, 0, 'ENABLED', '中跑：一般不分组道'),
       ('TRACK',    'LONG',    NULL, 35,  20, 30, 1, NULL, 'MORNING',   1, 0,  0, 'ENABLED', '长跑：建议上午、单轮次'),
       ('TRACK',    'HURDLE',  NULL, 20,  20, 15, 1, 8,    'ANY',       2, 45, 0, 'ENABLED', '跨栏：需道次与栏架准备'),
       ('TRACK',    'STEEPLE', NULL, 35,  20, 35, 1, NULL, 'MORNING',   1, 0,  0, 'ENABLED', '障碍赛：需布置障碍'),
       ('TRACK',    'WALK',    NULL, 40,  15, 40, 0, NULL, 'MORNING',   1, 0,  0, 'ENABLED', '竞走：不占跑道道次'),
       ('RELAY',    'RELAY',   NULL, 20,  20, 25, 1, 8,    'AFTERNOON', 2, 30, 0, 'ENABLED', '接力：需交接区与棒次'),
       ('FIELD',    'JUMP',    NULL, 40,  15, 50, 0, NULL, 'ANY',       2, 30, 1, 'ENABLED', '跳跃：需田赛出场顺序与升杆'),
       ('FIELD',    'THROW',   NULL, 40,  15, 60, 0, NULL, 'ANY',       2, 30, 1, 'ENABLED', '投掷：需出场顺序与安全区'),
       ('ALLROUND', 'ALL',     NULL, 180, 20, 70, 0, NULL, 'ANY',       1, 0,  1, 'ENABLED', '全能：按单项固定顺序连续进行');

-- 8.3 全能计分表结构示例（allround_score_table：全局表，无 meet_id）
--     说明：此处仅给出「成绩区间 → 得分」的少量示例行，用于验证查表逻辑与导入链路；
--           正式比赛请通过「全能计分表导入」接口导入权威计分表后停用/删除示例行。
INSERT INTO `allround_score_table` (`table_code`, `table_name`, `gender`, `sub_event_type`, `unit`,
                                    `performance_min`, `performance_max`, `point_value`, `is_hand_time`,
                                    `score_direction`, `seq_no`, `source_type`, `status`, `remark`)
VALUES ('DECATHLON_M_100M', '男子十项全能-100米(电计时)', 'MALE', 'SPRINT', 's', 10.00, 10.09, 1200.00, 0, 'DESC', 1,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('DECATHLON_M_100M', '男子十项全能-100米(电计时)', 'MALE', 'SPRINT', 's', 10.10, 10.19, 1150.00, 0, 'DESC', 2,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('DECATHLON_M_100M', '男子十项全能-100米(电计时)', 'MALE', 'SPRINT', 's', 11.00, 11.09, 800.00,  0, 'DESC', 3,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('DECATHLON_M_100M', '男子十项全能-100米(电计时)', 'MALE', 'SPRINT', 's', 12.00, 12.09, 500.00,  0, 'DESC', 4,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('DECATHLON_M_100M', '男子十项全能-100米(手计时)', 'MALE', 'SPRINT', 's', 10.20, 10.29, 1200.00, 1, 'DESC', 5,  'BUILTIN', 'ENABLED', '手计时示例行'),
       ('DECATHLON_M_LJ',   '男子十项全能-跳远',          'MALE', 'JUMP',   'm', 6.50,  6.59,  700.00,  0, 'ASC',  1,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('DECATHLON_M_LJ',   '男子十项全能-跳远',          'MALE', 'JUMP',   'm', 7.00,  7.09,  850.00,  0, 'ASC',  2,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('DECATHLON_M_LJ',   '男子十项全能-跳远',          'MALE', 'JUMP',   'm', 7.50,  7.59,  950.00,  0, 'ASC',  3,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('HEPTATHLON_W_100H','女子七项全能-100米栏(电计时)','FEMALE','HURDLE','s', 13.00, 13.09, 1150.00, 0, 'DESC', 1,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('HEPTATHLON_W_100H','女子七项全能-100米栏(电计时)','FEMALE','HURDLE','s', 14.00, 14.09, 950.00,  0, 'DESC', 2,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('HEPTATHLON_W_HJ',  '女子七项全能-跳高',          'FEMALE','JUMP',   'm', 1.60,  1.64,  850.00,  0, 'ASC',  1,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换'),
       ('HEPTATHLON_W_HJ',  '女子七项全能-跳高',          'FEMALE','JUMP',   'm', 1.70,  1.74,  950.00,  0, 'ASC',  2,  'BUILTIN', 'ENABLED', '示例行，请导入权威计分表替换');

-- ----------------------------------------------------------------------------
-- 9. 系统参数
-- ----------------------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_name`, `config_group`, `value_type`, `is_system`, `sort_no`, `remark`) VALUES
('security.captcha.enabled',   'false',      '登录验证码开关',       'SECURITY', 'BOOLEAN', 1, 1, 'true 时登录必须携带验证码'),
('security.pwd.min.length',    '6',          '密码最小长度',         'SECURITY', 'NUMBER',  1, 2, '新增/重置密码校验'),
('security.pwd.max.error',     '5',          '连续输错锁定阈值',     'SECURITY', 'NUMBER',  1, 3, '达到阈值锁定账号'),
('security.pwd.lock.minutes',  '10',         '锁定时长（分钟）',     'SECURITY', 'NUMBER',  1, 4, '锁定到期自动解锁'),
('system.time.precision',      '2',          '径赛成绩时间精度（位）','SYSTEM',  'NUMBER',  1, 5, '默认 1/100 秒'),
('system.upload.max.size.mb',  '20',         '单文件上传上限（MB）', 'FILE',     'NUMBER',  1, 6, '导入与附件'),
('screen.refresh.seconds',     '15',         '大屏刷新间隔（秒）',   'SCREEN',   'NUMBER',  1, 7, '大屏轮询频率'),
('print.default.page.size',    'A4',         '默认纸张',             'PRINT',    'STRING',  1, 8, 'A4/A3'),
('print.header.text',          '田径运动会编排系统', '默认页眉文字', 'PRINT',    'STRING',  1, 9, '可在打印模板中覆盖'),
('system.backup.retain.days',  '30',         '备份保留天数',         'SYSTEM',   'NUMBER',  1, 10, '超期自动清理');

-- ----------------------------------------------------------------------------
-- 10. 执行结果自检（可单独执行）
-- ----------------------------------------------------------------------------
-- SELECT '菜单' AS 对象, COUNT(*) AS 数量 FROM sys_menu
-- UNION ALL SELECT '权限', COUNT(*) FROM sys_permission
-- UNION ALL SELECT '角色', COUNT(*) FROM sys_role
-- UNION ALL SELECT '角色授权', COUNT(*) FROM sys_role_permission
-- UNION ALL SELECT '字典类型', COUNT(*) FROM sys_dict_type
-- UNION ALL SELECT '字典项', COUNT(*) FROM sys_dict_item
-- UNION ALL SELECT '裁判岗位', COUNT(*) FROM referee_duty
-- UNION ALL SELECT '项目时长规则', COUNT(*) FROM event_schedule_rule
-- UNION ALL SELECT '全能计分表行', COUNT(*) FROM allround_score_table
-- UNION ALL SELECT '系统参数', COUNT(*) FROM sys_config;

-- ----------------------------------------------------------------------------
-- 11. 阶段 5 追加菜单与按钮（打印与导出 / 公告与大屏）
--     新增权限编码：screen:board、screen:config:add、notice:edit、notice:delete、print:results
--     （print:results、print:batch 等其余编码已在前文菜单段定义）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perm_code`, `sort_no`) VALUES
(93,  90,  '大屏公告',      'M', '/screen/board',           'screen/ScreenBoard',   'DataBoard',  'screen:board',        93),
(912, 91,  '修改公告',      'B', NULL, NULL, NULL, 'notice:edit',         3),
(913, 91,  '删除公告',      'B', NULL, NULL, NULL, 'notice:delete',       4),
(922, 92,  '新增大屏配置',  'B', NULL, NULL, NULL, 'screen:config:add',   3);
