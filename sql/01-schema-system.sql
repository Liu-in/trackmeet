-- ============================================================================
-- 田径运动会编排软件 TrackMeet —— 01 系统层建表脚本
-- 内容：系统层 16 张全局表（RBAC、菜单、字典、日志、参数、备份、文件、导入、打印留痕）
-- 说明：系统层为全局表，不含 meet_id 租户隔离列；届次相关语义用业务列弱引用表达
-- 执行顺序：01-schema-system.sql → 02-schema-meet-athlete.sql → 03-schema-event-schedule.sql
--          → 04-schema-result-standing.sql → 05-init-data.sql → 06-demo-data.sql
-- 引擎/字符集：InnoDB / utf8mb4 / utf8mb4_0900_ai_ci（MySQL 8.0）
-- 约定：不建物理外键（应用层保证）；业务唯一键不并入 deleted，删除时改写唯一键
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 建库并切换（Docker 首次启动会用 MYSQL_DATABASE 指定库，手动执行时本段保证脚本自包含）
CREATE DATABASE IF NOT EXISTS `trackmeet`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `trackmeet`;

-- ----------------------------------------------------------------------------
-- 1. sys_user 用户表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`          VARCHAR(64)     NOT NULL                COMMENT '登录名，全局唯一',
  `password`          VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 密文，禁止存明文',
  `real_name`         VARCHAR(50)     NULL                    COMMENT '真实姓名',
  `phone`             VARCHAR(20)     NULL                    COMMENT '手机号',
  `email`             VARCHAR(100)    NULL                    COMMENT '邮箱',
  `avatar_file_id`    BIGINT UNSIGNED NULL                    COMMENT '头像文件 sys_file.id',
  `dept_id`           BIGINT UNSIGNED NULL                    COMMENT '所属部门 sys_dept.id',
  `unit_id`           BIGINT UNSIGNED NULL                    COMMENT '关联参赛单位 unit.id',
  `status`            VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `is_builtin`        TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1内置账号，禁止删除',
  `pwd_update_time`   DATETIME        NULL                    COMMENT '密码最后修改时间',
  `pwd_error_count`   INT             NOT NULL DEFAULT 0      COMMENT '连续输错次数，达阈值锁定',
  `lock_until`        DATETIME        NULL                    COMMENT '锁定到期时间，空为未锁',
  `force_pwd_change`  TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1首次登录强制改密',
  `token_version`     INT             NOT NULL DEFAULT 0      COMMENT '令牌版本，自增即失效旧令牌',
  `last_login_time`   DATETIME        NULL                    COMMENT '最后登录时间',
  `last_login_ip`     VARCHAR(64)     NULL                    COMMENT '最后登录IP',
  `sort_no`           INT             NOT NULL DEFAULT 0      COMMENT '列表显示顺序',
  `remark`            VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`         BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`           TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除/1已删除',
  `version`           INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_unit_id` (`unit_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ----------------------------------------------------------------------------
-- 2. sys_role 角色表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code`       VARCHAR(64)     NOT NULL                COMMENT '角色编码，唯一，如 SCHEDULER',
  `role_name`       VARCHAR(100)    NOT NULL                COMMENT '角色名称',
  `data_scope`      VARCHAR(32)     NOT NULL DEFAULT 'ALL'  COMMENT '数据范围：ALL全部/MEET本届次/UNIT本单位/SELF本人/REF自定义',
  `data_scope_json` JSON            NULL                    COMMENT 'REF 时的部门/单位/届次 ID 集合',
  `status`          VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用/DISABLED停用',
  `is_builtin`      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1内置角色，禁止删除',
  `sort_no`         INT             NOT NULL DEFAULT 0      COMMENT '列表显示顺序',
  `remark`          VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`       BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`         INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_data_scope_status` (`data_scope`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- ----------------------------------------------------------------------------
-- 3. sys_permission 权限表（菜单权限 / 按钮权限 / 接口权限）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `perm_code`   VARCHAR(64)     NOT NULL                COMMENT '权限编码，唯一，如 schedule:auto',
  `perm_name`   VARCHAR(100)    NOT NULL                COMMENT '权限名称',
  `perm_type`   VARCHAR(32)     NOT NULL DEFAULT 'BUTTON' COMMENT '权限类型：MENU菜单/BUTTON按钮/API接口',
  `menu_id`     BIGINT UNSIGNED NULL                    COMMENT '所属菜单 sys_menu.id，API 可空',
  `module`      VARCHAR(64)     NULL                    COMMENT '所属模块，用于日志归类',
  `api_path`    VARCHAR(200)    NULL                    COMMENT '接口路径（Ant 风格），API 用',
  `http_method` VARCHAR(16)     NULL                    COMMENT 'GET/POST/PUT/DELETE，API 用',
  `api_group`   VARCHAR(64)     NULL                    COMMENT '接口分组，如 REGISTRATION',
  `is_builtin`  TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1内置权限，禁止删除',
  `status`      VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `sort_no`     INT             NOT NULL DEFAULT 0      COMMENT '列表显示顺序',
  `remark`      VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`   BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`     INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`),
  KEY `idx_perm_type_status` (`perm_type`, `status`),
  KEY `idx_menu_id` (`menu_id`),
  KEY `idx_api_path_method` (`api_path`, `http_method`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

-- ----------------------------------------------------------------------------
-- 4. sys_menu 菜单表（目录 D / 菜单 M / 按钮 B）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父节点，0 为根',
  `menu_name`   VARCHAR(100)    NOT NULL                COMMENT '菜单/按钮名称',
  `menu_type`   VARCHAR(32)     NOT NULL DEFAULT 'M'    COMMENT '类型：D目录/M菜单/B按钮',
  `path`        VARCHAR(200)    NULL                    COMMENT '路由路径',
  `component`   VARCHAR(200)    NULL                    COMMENT '前端组件路径',
  `icon`        VARCHAR(64)     NULL                    COMMENT '图标标识',
  `perm_code`   VARCHAR(64)     NULL                    COMMENT '权限编码，与 sys_permission 一致',
  `visible`     TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '1导航可见/0隐藏',
  `keep_alive`  TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1页面缓存',
  `is_external` TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1外链/0内部路由',
  `status`      VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `sort_no`     INT             NOT NULL DEFAULT 0      COMMENT '同级排序号',
  `remark`      VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`   BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`     INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_parent_sort` (`parent_id`, `sort_no`),
  KEY `idx_menu_type` (`menu_type`),
  KEY `idx_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表';

-- ----------------------------------------------------------------------------
-- 5. sys_role_permission 角色权限关联表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id`              BIGINT UNSIGNED NOT NULL                COMMENT '角色 sys_role.id',
  `permission_id`        BIGINT UNSIGNED NOT NULL                COMMENT '权限 sys_permission.id',
  `menu_id`              BIGINT UNSIGNED NULL                    COMMENT '冗余菜单 sys_menu.id',
  `perm_type`            VARCHAR(32)     NULL                    COMMENT '授权类型：MENU/BUTTON/API',
  `grant_source`         VARCHAR(32)     NOT NULL DEFAULT 'MANUAL' COMMENT '授权来源：INIT初始化/MANUAL手工/COPY复制角色',
  `grant_by`             BIGINT UNSIGNED NULL                    COMMENT '授权人 sys_user.id',
  `grant_time`           DATETIME        NULL                    COMMENT '授权时间',
  `is_effective`         TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '1生效/0暂停授权',
  `effective_start_time` DATETIME        NULL                    COMMENT '生效开始时间，空为立即',
  `effective_end_time`   DATETIME        NULL                    COMMENT '生效结束时间，空为长期',
  `remark`               VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`            BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`              TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`              INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_permission_id` (`permission_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

-- ----------------------------------------------------------------------------
-- 6. sys_user_role 用户角色关联表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`              BIGINT UNSIGNED NOT NULL                COMMENT '用户 sys_user.id',
  `role_id`              BIGINT UNSIGNED NOT NULL                COMMENT '角色 sys_role.id',
  `grant_source`         VARCHAR(32)     NOT NULL DEFAULT 'MANUAL' COMMENT '授权来源：INIT/MANUAL/IMPORT',
  `grant_by`             BIGINT UNSIGNED NULL                    COMMENT '授权人 sys_user.id',
  `grant_time`           DATETIME        NULL                    COMMENT '授权时间',
  `is_primary`           TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1主角色，用于默认数据范围',
  `is_effective`         TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '1生效/0停用',
  `effective_start_time` DATETIME        NULL                    COMMENT '生效开始时间',
  `effective_end_time`   DATETIME        NULL                    COMMENT '生效结束时间',
  `remark`               VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`            BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`              TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`              INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

-- ----------------------------------------------------------------------------
-- 7. sys_dept 部门表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父部门，0 为根',
  `dept_name`   VARCHAR(100)    NOT NULL                COMMENT '部门名称',
  `dept_code`   VARCHAR(64)     NOT NULL                COMMENT '部门编码，唯一',
  `dept_type`   VARCHAR(32)     NOT NULL DEFAULT 'DEPT' COMMENT '类型：ORG机构/DEPT部门/TEAM业务小组',
  `dept_path`   VARCHAR(500)    NULL                    COMMENT '祖先路径，如 /1/4/9/',
  `leader`      VARCHAR(50)     NULL                    COMMENT '负责人姓名',
  `phone`       VARCHAR(20)     NULL                    COMMENT '联系电话',
  `email`       VARCHAR(100)    NULL                    COMMENT '联系邮箱',
  `unit_id`     BIGINT UNSIGNED NULL                    COMMENT '绑定参赛单位 unit.id，空为纯管理组织',
  `sort_no`     INT             NOT NULL DEFAULT 0      COMMENT '同级排序号',
  `status`      VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `remark`      VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`   BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`     INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_dept_path` (`dept_path`),
  KEY `idx_unit_id` (`unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';

-- ----------------------------------------------------------------------------
-- 8. sys_dict_type 字典类型表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_type`   VARCHAR(64)     NOT NULL                COMMENT '字典类型编码，唯一，如 event_type',
  `dict_name`   VARCHAR(100)    NOT NULL                COMMENT '字典类型名称',
  `dict_group`  VARCHAR(64)     NULL                    COMMENT '字典分组，如 TRACK_FIELD',
  `is_system`   TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1系统内置，禁止删除',
  `is_global`   TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '1全局字典，届次间共用',
  `status`      VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `sort_no`     INT             NOT NULL DEFAULT 0      COMMENT '列表显示顺序',
  `extra_json`  JSON            NULL                    COMMENT '扩展属性（颜色、默认值等）',
  `remark`      VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`   BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`     INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`),
  KEY `idx_dict_group_status` (`dict_group`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

-- ----------------------------------------------------------------------------
-- 9. sys_dict_item 字典项表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_type`    VARCHAR(64)     NOT NULL                COMMENT '所属字典类型编码',
  `item_label`   VARCHAR(100)    NOT NULL                COMMENT '显示名，如 径赛',
  `item_value`   VARCHAR(64)     NOT NULL                COMMENT '存储码，如 TRACK',
  `parent_value` VARCHAR(64)     NULL                    COMMENT '上级字典项码，支持级联',
  `css_class`    VARCHAR(64)     NULL                    COMMENT '状态标签样式类',
  `is_default`   TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1为默认选中项',
  `sort_no`      INT             NOT NULL DEFAULT 0      COMMENT '同类型内排序号',
  `extra_json`   JSON            NULL                    COMMENT '扩展属性（图标、附加码值）',
  `status`       VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `remark`       VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`    BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`      INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_item_value` (`dict_type`, `item_value`),
  KEY `idx_dict_type_sort` (`dict_type`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典项表';

-- ----------------------------------------------------------------------------
-- 10. sys_operation_log 操作日志表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT UNSIGNED NULL                    COMMENT '操作人 sys_user.id，匿名写 0',
  `username`       VARCHAR(64)     NULL                    COMMENT '操作人登录名快照',
  `module`         VARCHAR(64)     NULL                    COMMENT '业务模块，如 RESULT',
  `action`         VARCHAR(64)     NULL                    COMMENT '动作码，如 AUDIT',
  `action_name`    VARCHAR(100)    NULL                    COMMENT '动作中文名，如 成绩审核',
  `target_type`    VARCHAR(64)     NULL                    COMMENT '目标类型，如 result',
  `target_id`      BIGINT UNSIGNED NULL                    COMMENT '目标主键',
  `before_json`    JSON            NULL                    COMMENT '修改前值快照，仅存变更字段',
  `after_json`     JSON            NULL                    COMMENT '修改后值快照，仅存变更字段',
  `params_summary` VARCHAR(500)    NULL                    COMMENT '请求参数摘要，敏感字段脱敏',
  `request_id`     VARCHAR(64)     NULL                    COMMENT '请求链路 ID',
  `ip`             VARCHAR(64)     NULL                    COMMENT '客户端 IP',
  `ip_region`      VARCHAR(100)    NULL                    COMMENT 'IP 归属地',
  `ua`             VARCHAR(500)    NULL                    COMMENT 'User-Agent 原文',
  `os`             VARCHAR(64)     NULL                    COMMENT '操作系统',
  `browser`        VARCHAR(64)     NULL                    COMMENT '浏览器',
  `cost_ms`        INT             NULL                    COMMENT '处理耗时（毫秒）',
  `result`         VARCHAR(32)     NULL                    COMMENT '结果：SUCCESS成功/FAIL失败',
  `error_msg`      TEXT            NULL                    COMMENT '失败异常摘要',
  `operate_time`   DATETIME        NULL                    COMMENT '操作发生时间，分区与清理依据',
  `is_archived`    TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1已归档，可被清理任务删除',
  `create_by`      BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`        TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`        INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_operate_time` (`operate_time`),
  KEY `idx_user_operate_time` (`user_id`, `operate_time`),
  KEY `idx_module_action` (`module`, `action`),
  KEY `idx_target` (`target_type`, `target_id`),
  KEY `idx_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';

-- ----------------------------------------------------------------------------
-- 11. sys_login_log 登录日志表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT UNSIGNED NULL                    COMMENT '用户 sys_user.id，失败可为空',
  `username`    VARCHAR(64)     NULL                    COMMENT '登录名快照',
  `login_type`  VARCHAR(32)     NOT NULL DEFAULT 'LOGIN' COMMENT '类型：LOGIN登录/LOGOUT登出/REFRESH刷新令牌',
  `success`     TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '1成功/0失败',
  `fail_reason` VARCHAR(255)    NULL                    COMMENT '失败原因，如密码错误、账号锁定',
  `ip`          VARCHAR(64)     NULL                    COMMENT '客户端 IP',
  `ip_region`   VARCHAR(100)    NULL                    COMMENT 'IP 归属地',
  `ua`          VARCHAR(500)    NULL                    COMMENT 'User-Agent 原文',
  `os`          VARCHAR(64)     NULL                    COMMENT '操作系统',
  `browser`     VARCHAR(64)     NULL                    COMMENT '浏览器',
  `device_type` VARCHAR(32)     NULL                    COMMENT '设备类型：PC/MOBILE/PAD',
  `session_id`  VARCHAR(128)    NULL                    COMMENT '会话标识，用于强制下线',
  `login_time`  DATETIME        NULL                    COMMENT '登录/登出发生时间',
  `create_by`   BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`     INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_login_time` (`login_time`),
  KEY `idx_username_login_time` (`username`, `login_time`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';

-- ----------------------------------------------------------------------------
-- 12. sys_config 系统参数表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_key`   VARCHAR(100)    NOT NULL                COMMENT '参数键，唯一，如 pwd.min.length',
  `config_value` TEXT            NULL                    COMMENT '参数值，JSON 型存序列化文本',
  `config_name`  VARCHAR(100)    NOT NULL                COMMENT '参数名称',
  `config_group` VARCHAR(64)     NULL                    COMMENT '分组，如 SECURITY、FILE、PRINT',
  `value_type`   VARCHAR(32)     NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING/NUMBER/BOOLEAN/JSON',
  `is_system`    TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1系统内置，禁止删除',
  `is_encrypted` TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1值加密存储',
  `status`       VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `sort_no`      INT             NOT NULL DEFAULT 0      COMMENT '列表显示顺序',
  `remark`       VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`    BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`      INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_config_group_status` (`config_group`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统参数表';

-- ----------------------------------------------------------------------------
-- 13. sys_backup_record 备份记录表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_backup_record`;
CREATE TABLE `sys_backup_record` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `backup_name`    VARCHAR(100)    NOT NULL                COMMENT '备份名称',
  `file_path`      VARCHAR(500)    NULL                    COMMENT '备份文件存放路径',
  `file_size`      BIGINT UNSIGNED NULL                    COMMENT '文件大小（字节）',
  `backup_type`    VARCHAR(32)     NOT NULL DEFAULT 'MANUAL' COMMENT '类型：MANUAL手工/AUTO自动',
  `backup_scope`   VARCHAR(32)     NOT NULL DEFAULT 'FULL' COMMENT '范围：FULL全库/SCHEMA仅结构/DATA仅数据',
  `status`         VARCHAR(32)     NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING进行中/SUCCESS成功/FAILED失败',
  `start_time`     DATETIME        NULL                    COMMENT '备份开始时间',
  `finish_time`    DATETIME        NULL                    COMMENT '备份结束时间',
  `cost_ms`        INT             NULL                    COMMENT '耗时（毫秒）',
  `md5`            VARCHAR(64)     NULL                    COMMENT '文件校验值',
  `restore_status` VARCHAR(32)     NOT NULL DEFAULT 'NONE' COMMENT '恢复状态：NONE未恢复/SUCCESS成功/FAILED失败',
  `restore_time`   DATETIME        NULL                    COMMENT '最近恢复时间',
  `restore_by`     BIGINT UNSIGNED NULL                    COMMENT '恢复操作人 sys_user.id',
  `error_msg`      TEXT            NULL                    COMMENT '失败原因',
  `remark`         VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`      BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`        TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`        INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_backup_type_status` (`backup_type`, `status`),
  KEY `idx_restore_time` (`restore_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备份记录表';

-- ----------------------------------------------------------------------------
-- 14. sys_file 文件表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `biz_type`      VARCHAR(32)     NULL                    COMMENT '业务类型，如 REGULATION、CERT、IMPORT',
  `biz_id`        BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '业务主键，未绑定为 0',
  `file_name`     VARCHAR(255)    NOT NULL                COMMENT '存储文件名（含相对路径）',
  `original_name` VARCHAR(255)    NULL                    COMMENT '上传时原始文件名',
  `file_path`     VARCHAR(500)    NULL                    COMMENT '物理存储路径',
  `file_url`      VARCHAR(500)    NULL                    COMMENT '访问地址',
  `file_size`     BIGINT UNSIGNED NULL                    COMMENT '文件大小（字节）',
  `file_ext`      VARCHAR(16)     NULL                    COMMENT '扩展名，如 xlsx',
  `mime_type`     VARCHAR(100)    NULL                    COMMENT 'MIME 类型',
  `md5`           VARCHAR(64)     NULL                    COMMENT '内容摘要，用于秒传去重',
  `storage_type`  VARCHAR(32)     NOT NULL DEFAULT 'LOCAL' COMMENT '存储类型：LOCAL本地/OSS/MINIO',
  `bucket_name`   VARCHAR(100)    NULL                    COMMENT '存储桶名称',
  `is_temp`       TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '1临时文件，可被清理任务删除',
  `ref_count`     INT             NOT NULL DEFAULT 0      COMMENT '被引用次数，为 0 可回收',
  `status`        VARCHAR(32)     NOT NULL DEFAULT 'NORMAL' COMMENT '状态：NORMAL正常/DELETED已清理',
  `remark`        VARCHAR(255)    NULL                    COMMENT '备注',
  `create_by`     BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`       TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`       INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_md5_file_name` (`md5`, `file_name`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_is_temp_create_time` (`is_temp`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件表';

-- ----------------------------------------------------------------------------
-- 15. sys_import_task 导入导出任务表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_import_task`;
CREATE TABLE `sys_import_task` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_name`     VARCHAR(100)    NULL                    COMMENT '任务名称，如 运动员导入',
  `biz_type`      VARCHAR(32)     NOT NULL                COMMENT '业务类型，如 ATHLETE、RESULT',
  `task_direction` VARCHAR(32)    NOT NULL DEFAULT 'IMPORT' COMMENT '方向：IMPORT导入/EXPORT导出',
  `file_id`       BIGINT UNSIGNED NULL                    COMMENT '源文件 sys_file.id',
  `import_mode`   VARCHAR(32)     NOT NULL DEFAULT 'INSERT' COMMENT '模式：INSERT仅新增/UPSERT覆盖/VALIDATE仅校验',
  `total_count`   INT             NOT NULL DEFAULT 0      COMMENT '总记录数',
  `success_count` INT             NOT NULL DEFAULT 0      COMMENT '成功条数',
  `fail_count`    INT             NOT NULL DEFAULT 0      COMMENT '失败条数',
  `progress`      INT             NOT NULL DEFAULT 0      COMMENT '进度百分比 0-100',
  `error_file_id` BIGINT UNSIGNED NULL                    COMMENT '错误明细文件 sys_file.id',
  `status`        VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/PARTIAL/FAILED',
  `operator_id`   BIGINT UNSIGNED NULL                    COMMENT '发起人 sys_user.id',
  `start_time`    DATETIME        NULL                    COMMENT '开始执行时间',
  `finish_time`   DATETIME        NULL                    COMMENT '结束时间',
  `cost_ms`       INT             NULL                    COMMENT '耗时（毫秒）',
  `message`       TEXT            NULL                    COMMENT '结果说明或失败摘要',
  `create_by`     BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`       TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`       INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_biz_type_status` (`biz_type`, `status`),
  KEY `idx_operator_create_time` (`operator_id`, `create_time`),
  KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导入导出任务表';

-- ----------------------------------------------------------------------------
-- 16. sys_print_log 打印留痕表
-- 说明：meet_ref_id 为届次弱引用（系统表不参与 meet_id 租户隔离）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_print_log`;
CREATE TABLE `sys_print_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `report_type`   VARCHAR(32)     NOT NULL                COMMENT '报表类型，如 START_LIST、RESULT_BOOK',
  `report_name`   VARCHAR(100)    NULL                    COMMENT '报表名称',
  `params_json`   JSON            NULL                    COMMENT '打印查询参数快照，用于重打',
  `format`        VARCHAR(32)     NULL                    COMMENT '格式：PDF/EXCEL/WORD',
  `meet_ref_id`   BIGINT UNSIGNED NULL                    COMMENT '届次弱引用，仅用于筛选留痕',
  `file_id`       BIGINT UNSIGNED NULL                    COMMENT '生成文件 sys_file.id',
  `template_id`   BIGINT UNSIGNED NULL                    COMMENT '使用的打印模板 print_template.id',
  `operator_id`   BIGINT UNSIGNED NULL                    COMMENT '操作人 sys_user.id',
  `operator_name` VARCHAR(50)     NULL                    COMMENT '操作人姓名快照',
  `print_count`   INT             NOT NULL DEFAULT 1      COMMENT '本次打印份数',
  `print_device`  VARCHAR(100)    NULL                    COMMENT '打印设备或终端标识',
  `ip`            VARCHAR(64)     NULL                    COMMENT '客户端 IP',
  `status`        VARCHAR(32)     NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS成功/FAILED失败',
  `error_msg`     VARCHAR(255)    NULL                    COMMENT '失败原因',
  `print_time`    DATETIME        NULL                    COMMENT '打印发生时间',
  `create_by`     BIGINT UNSIGNED NULL                    COMMENT '创建人',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED NULL                    COMMENT '修改人',
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted`       TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `version`       INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_meet_ref_report_type` (`meet_ref_id`, `report_type`),
  KEY `idx_operator_print_time` (`operator_id`, `print_time`),
  KEY `idx_print_time` (`print_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印留痕表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 系统层 16 张表创建完毕
-- 后续：执行 02/03/04 建表脚本，再执行 05-init-data.sql 灌入角色/权限/菜单/字典
-- ============================================================================
