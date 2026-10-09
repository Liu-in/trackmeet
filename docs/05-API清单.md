# API 清单

> 风格：RESTful ｜ 前缀：`/api` ｜ 认证：`Authorization: Bearer <JWT>` ｜ 编码：UTF-8 ｜ 时间格式：`yyyy-MM-dd HH:mm:ss`

## 1. 统一约定

### 1.1 统一返回格式
```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "8f3c1e0a9b7d4e21",
  "timestamp": 1735689600000
}
```
- `code = 0` 表示成功；非 0 为业务错误码。
- 分页数据统一结构：`{ "records": [], "total": 120, "page": 1, "size": 20, "pages": 6 }`。

### 1.2 统一错误码
| code | 含义 | HTTP |
| --- | --- | --- |
| 0 | 成功 | 200 |
| 400 | 参数校验失败 | 400 |
| 401 | 未登录或令牌失效 | 401 |
| 403 | 无权限（含数据范围越权） | 403 |
| 404 | 资源不存在 | 404 |
| 409 | 数据冲突（唯一约束、状态不允许、并发修改） | 409 |
| 10001 | 用户名或密码错误 | 200 |
| 10002 | 账号被锁定/停用 | 200 |
| 10003 | 验证码错误 | 200 |
| 20001 | 运动会状态不允许该操作 | 200 |
| 20002 | 报名已截止 | 200 |
| 20003 | 超出报名限制（超项/超人数/组别不符） | 200 |
| 20004 | 重复报名 | 200 |
| 30001 | 规则配置缺失或不完整 | 200 |
| 30002 | 编排冲突未解决 | 200 |
| 40001 | 成绩已审核锁定，禁止直接修改 | 200 |
| 40002 | 成绩风速超限，纪录不予认定 | 200 |
| 40003 | 晋级规则与报名人数不匹配 | 200 |
| 50001 | 导入文件格式错误 | 200 |
| 50002 | 报表模板缺失 | 200 |
| 90000 | 系统内部错误 | 500 |

### 1.3 公共能力
| 能力 | 约定 |
| --- | --- |
| 分页 | `page`（默认 1）、`size`（默认 20，最大 200）、`sort=field,asc` |
| 搜索 | `keyword` 关键字模糊；各接口可有专用过滤参数 |
| 届次上下文 | 除 `/api/auth/*`、`/api/system/*` 外，业务接口必须带 `meetId`（Query 或 Header `X-Meet-Id`） |
| 写操作幂等 | 关键写接口支持 `clientToken`，重复提交返回首次结果 |
| 数据权限 | 服务端按角色自动追加 `meet_id` / `unit_id` / `self` 过滤，前端不可绕过 |
| 导出 | 统一 `GET .../export?format=pdf\|xlsx\|docx\|csv`，返回文件流 |

---

## 2. 认证与系统管理 `/api`

### 2.1 认证 `auth`
| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 登录（`username`、`password`、可选 `captcha`、`captchaKey`）→ `accessToken`、`refreshToken`、`userInfo`、`permissions`、`menus` | 匿名 |
| POST | `/api/auth/refresh` | 刷新令牌 | 需 refreshToken |
| POST | `/api/auth/logout` | 登出（令牌加入黑名单） | 登录 |
| GET | `/api/auth/captcha` | 获取图形验证码 | 匿名 |
| GET | `/api/auth/profile` | 当前用户信息、角色、权限、可访问届次 | 登录 |
| POST | `/api/auth/change-password` | 修改本人密码 | 登录 |

### 2.2 用户/角色/权限/菜单 `system`
| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/system/users` | 用户分页列表 | `system:user:list` |
| GET | `/api/system/users/{id}` | 用户详情 | `system:user:query` |
| POST | `/api/system/users` | 新增用户 | `system:user:add` |
| PUT | `/api/system/users/{id}` | 修改用户 | `system:user:edit` |
| DELETE | `/api/system/users/{id}` | 删除用户（逻辑） | `system:user:delete` |
| PUT | `/api/system/users/{id}/status` | 启用/停用 | `system:user:edit` |
| PUT | `/api/system/users/{id}/password` | 重置密码 | `system:user:resetPwd` |
| PUT | `/api/system/users/{id}/roles` | 分配角色 | `system:user:assignRole` |
| GET | `/api/system/roles` | 角色列表 | `system:role:list` |
| POST/PUT/DELETE | `/api/system/roles`、`/api/system/roles/{id}` | 角色增改删 | `system:role:*` |
| PUT | `/api/system/roles/{id}/permissions` | 角色授权（菜单+按钮） | `system:role:auth` |
| GET | `/api/system/permissions/tree` | 权限/菜单树 | `system:menu:list` |
| POST/PUT/DELETE | `/api/system/menus`、`/api/system/menus/{id}` | 菜单增改删 | `system:menu:*` |
| GET | `/api/system/depts/tree` | 部门树 | `system:dept:list` |
| POST/PUT/DELETE | `/api/system/depts`、`/api/system/depts/{id}` | 部门增改删 | `system:dept:*` |
| GET | `/api/system/dicts/types` | 字典类型分页 | `system:dict:list` |
| GET | `/api/system/dicts/items?dictType=xxx` | 字典项列表 | 登录 |
| POST/PUT/DELETE | `/api/system/dicts/types`、`/api/system/dicts/items` | 字典增改删 | `system:dict:*` |
| GET | `/api/system/logs/operation` | 操作日志分页（含 before/after） | `system:log:operation` |
| GET | `/api/system/logs/login` | 登录日志分页 | `system:log:login` |
| GET | `/api/system/configs` | 系统参数列表 | `system:config:list` |
| PUT | `/api/system/configs` | 批量修改系统参数 | `system:config:edit` |
| POST | `/api/system/backup` | 手动备份 | `system:backup:add` |
| GET | `/api/system/backup` | 备份记录列表 | `system:backup:list` |
| POST | `/api/system/backup/{id}/restore` | 恢复（恢复前自动快照） | `system:backup:restore` |
| GET | `/api/system/backup/{id}/download` | 下载备份文件 | `system:backup:download` |
| POST | `/api/system/files/upload` | 通用文件上传 | 登录 |
| GET | `/api/system/files/{id}/download` | 文件下载 | 登录 |

---

## 3. 运动会管理 `/api/meets`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/meets` | 运动会分页列表（`status`、`keyword`、年份） | 登录 |
| GET | `/api/meets/{id}` | 详情（含规程、统计概览） | 登录 |
| POST | `/api/meets` | 创建运动会 | `meet:add` |
| PUT | `/api/meets/{id}` | 修改运动会 | `meet:edit` |
| DELETE | `/api/meets/{id}` | 删除（仅草稿态或归档） | `meet:delete` |
| PUT | `/api/meets/{id}/status` | 状态流转（报名中/截止/编排中/进行中/结束/归档） | `meet:status` |
| POST | `/api/meets/{id}/clone` | 复制模板（`copyScope`：groups/units/events/rules/registrations） | `meet:clone` |
| GET | `/api/meets/current` | 当前活跃届次 | 登录 |
| GET | `/api/meets/{id}/dashboard` | 届次看板统计 | 登录 |
| GET | `/api/meets/{id}/groups` | 组别列表 | 登录 |
| POST/PUT/DELETE | `/api/meets/{id}/groups`、`/api/groups/{id}` | 组别增改删 | `meet:group:*` |
| GET | `/api/units` | 单位列表 | 登录 |
| POST/PUT/DELETE | `/api/units`、`/api/units/{id}` | 单位增改删 | `meet:unit:*` |
| GET/POST/PUT/DELETE | `/api/units/{id}/staffs`、`/api/units/staffs/{id}` | 领队教练管理 | `meet:unit:staff` |
| GET | `/api/venues` | 场地列表 | 登录 |
| POST/PUT/DELETE | `/api/venues`、`/api/venues/{id}` | 场地增改删 | `meet:venue:*` |
| GET/POST/PUT/DELETE | `/api/referees`、`/api/referees/{id}` | 裁判管理 | `meet:referee:*` |
| GET | `/api/meets/{id}/rule-config` | 届次规则配置 | `event:rule:query` |
| PUT | `/api/meets/{id}/rule-config` | 保存届次规则配置 | `event:rule:edit` |

---

## 4. 运动员与报名 `/api`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/athletes` | 运动员分页（`unitId`、`groupId`、`gender`、`keyword`） | `athlete:list`（领队自动限本单位） |
| GET | `/api/athletes/{id}` | 详情（含报名项、历史成绩） | `athlete:query` |
| POST | `/api/athletes` | 新增 | `athlete:add` |
| PUT | `/api/athletes/{id}` | 修改 | `athlete:edit` |
| DELETE | `/api/athletes/{id}` | 删除（已有报名需确认） | `athlete:delete` |
| GET | `/api/athletes/import-template` | 下载导入模板 | `athlete:import` |
| POST | `/api/athletes/import` | Excel 批量导入（返回成功/失败统计与错误文件） | `athlete:import` |
| GET | `/api/athletes/export` | 导出运动员名册 | `athlete:export` |
| POST | `/api/athletes/generate-bibs` | 按单位批量生成号码 | `athlete:bib` |
| GET | `/api/athletes/{id}/registrations` | 个人报名明细 | `registration:list` |
| GET | `/api/registrations` | 报名分页（`eventId`、`unitId`、`status`） | `registration:list` |
| POST | `/api/registrations` | 新增报名（个人/接力） | `registration:add` |
| POST | `/api/registrations/batch` | 批量报名 | `registration:add` |
| PUT | `/api/registrations/{id}` | 修改报名（成绩种子、队员） | `registration:edit` |
| DELETE | `/api/registrations/{id}` | 取消报名 | `registration:delete` |
| POST | `/api/registrations/import` | Excel 批量导入报名 | `registration:import` |
| GET | `/api/registrations/import-template` | 报名导入模板 | `registration:import` |
| POST | `/api/registrations/check` | 批量限制/冲突校验（不落库，返回明细） | `registration:check` |
| GET | `/api/registrations/check-result` | 校验结果与预警清单 | `registration:check` |
| POST | `/api/registrations/approve` | 批量审核通过 | `registration:approve` |
| POST | `/api/registrations/{id}/reject` | 退回（附原因） | `registration:approve` |
| GET | `/api/registrations/statistics` | 报名统计（按单位/组别/项目） | `registration:list` |
| GET | `/api/registrations/warnings` | 报名不足项目预警 | `registration:list` |
| GET | `/api/registrations/export` | 导出报名表 | `registration:export` |
| POST | `/api/registrations/lock` | 截止并锁定报名 | `registration:lock` |

---

## 5. 项目与规则 `/api`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/events` | 项目分页（`groupId`、`eventType`、`gender`） | `event:list` |
| GET | `/api/events/{id}` | 项目详情（含轮次、规则） | `event:query` |
| POST | `/api/events` | 新增项目 | `event:add` |
| PUT | `/api/events/{id}` | 修改项目 | `event:edit` |
| DELETE | `/api/events/{id}` | 删除项目（有报名需确认） | `event:delete` |
| POST | `/api/events/batch` | 批量生成标准项目（按组别×性别模板） | `event:add` |
| GET/POST/PUT/DELETE | `/api/events/{id}/rounds`、`/api/rounds/{id}` | 轮次管理 | `event:round:*` |
| GET/PUT | `/api/rounds/{id}/rule` | 轮次级规则（晋级/录取） | `event:rule:*` |
| GET/PUT | `/api/events/{id}/rule-config` | 项目级规则配置 | `event:rule:*` |
| POST | `/api/events/{id}/rule-config/reset` | 恢复继承默认 | `event:rule:edit` |
| GET/POST/PUT/DELETE | `/api/score-rules`、`/api/score-rules/{id}` | 名次分方案管理 | `rule:score:*` |
| GET/PUT | `/api/score-rules/{id}/items` | 名次分明细（批量保存） | `rule:score:edit` |
| GET/POST/PUT/DELETE | `/api/bonus-rules`、`/api/bonus-rules/{id}` | 加分规则管理 | `rule:bonus:*` |
| GET | `/api/allround/events/{eventId}` | 全能项目构成 | `event:query` |
| PUT | `/api/allround/events/{eventId}` | 保存全能构成与顺序 | `event:rule:edit` |
| GET | `/api/allround/score-tables` | 计分表列表 | `rule:allround:list` |
| POST | `/api/allround/score-tables/import` | 导入计分表（Excel） | `rule:allround:import` |
| GET | `/api/events/rule-template/export` | 导出规则模板 JSON | `event:rule:export` |
| POST | `/api/events/rule-template/import` | 导入规则模板 JSON | `event:rule:import` |

---

## 6. 编排管理 `/api`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET/POST/PUT/DELETE | `/api/sessions`、`/api/sessions/{id}` | 竞赛单元管理 | `schedule:session:*` |
| GET | `/api/schedule` | 日程查询（`sessionId`、`venueId`、`date`、`groupId`） | `schedule:list` |
| GET | `/api/schedule/{id}` | 日程详情（含分组分道） | `schedule:query` |
| POST | `/api/schedule` | 手动新增日程 | `schedule:add` |
| PUT | `/api/schedule/{id}` | 修改日程 | `schedule:edit` |
| DELETE | `/api/schedule/{id}` | 删除日程 | `schedule:delete` |
| POST | `/api/schedule/auto` | **自动编排**（`scope`、`sessionIds`、`eventIds`、`keepLocked`） | `schedule:auto` |
| POST | `/api/schedule/{id}/move` | 拖拽移动（`sessionId`、`startTime`、`venueId`） | `schedule:edit` |
| PUT | `/api/schedule/{id}/lock` | 锁定/解锁日程 | `schedule:lock` |
| PUT | `/api/schedule/{id}/publish` | 发布/撤回日程 | `schedule:publish` |
| POST | `/api/schedule/regenerate` | 重新生成（`scope=ALL|EVENT|SESSION`） | `schedule:auto` |
| POST | `/api/heats/generate` | **自动分组分道**（`scheduleId` 或 `eventId+roundId`，`drawMode`） | `schedule:heat:generate` |
| GET | `/api/heats` | 分组列表（按日程） | `schedule:list` |
| POST/PUT/DELETE | `/api/heats`、`/api/heats/{id}` | 分组增改删 | `schedule:heat:edit` |
| PUT | `/api/heats/{id}/lock` | 锁定分组 | `schedule:heat:lock` |
| GET | `/api/heats/{id}/members` | 组内人员 | `schedule:list` |
| POST | `/api/heats/{id}/members` | 添加人员到组 | `schedule:heat:edit` |
| DELETE | `/api/heats/{id}/members/{memberId}` | 移出人员 | `schedule:heat:edit` |
| POST | `/api/heats/members/move` | 组间拖拽移动（`memberId`、`targetHeatId`、`targetOrderNo`） | `schedule:heat:edit` |
| POST | `/api/lanes/assign` | **道次分配**（`heatIds`、`mode=SEED|DRAW|ORDER`） | `schedule:lane:assign` |
| PUT | `/api/lanes/{id}` | 手动改道次 | `schedule:lane:edit` |
| POST | `/api/lanes/swap` | 两道次交换 | `schedule:lane:edit` |
| POST | `/api/field-orders/generate` | 田赛顺序生成（`mode=RANDOM|SEED|UNIT|BIB`） | `schedule:field:generate` |
| PUT | `/api/field-orders/reorder` | 田赛顺序调整 | `schedule:field:edit` |
| POST | `/api/schedule/conflicts/detect` | **冲突检测**（`scope`） | `schedule:conflict:detect` |
| GET | `/api/schedule/conflicts` | 冲突清单（`type`、`resolved`） | `schedule:list` |
| PUT | `/api/schedule/conflicts/{id}/resolve` | 标记已处理（附说明） | `schedule:conflict:resolve` |
| GET | `/api/schedule/gantt` | 日程甘特/时间轴数据 | `schedule:list` |
| POST | `/api/schedule/snapshot` | 保存编排快照 | `schedule:snapshot` |
| POST | `/api/schedule/snapshot/{id}/restore` | 回滚快照 | `schedule:snapshot` |

---

## 7. 赛前管理 `/api`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/calling` | 检录列表（`scheduleId`、`heatId`、`status`） | `calling:list` |
| POST | `/api/calling/batch` | 批量检录登记（到检/未到/弃权） | `calling:edit` |
| PUT | `/api/calling/{id}` | 修改检录状态 | `calling:edit` |
| GET | `/api/calling/sheet` | 检录表数据（打印用） | `calling:list` |
| POST | `/api/draw/random` | 随机抽签（`scopeType`、`scopeId`） | `draw:exec` |
| POST | `/api/draw/seed` | 种子抽签 | `draw:exec` |
| POST | `/api/draw/lane` | 道次抽签 | `draw:exec` |
| GET | `/api/draw/records` | 抽签记录 | `draw:list` |
| GET | `/api/start-list` | 参赛名单（领队限本单位） | `startlist:list` |
| GET | `/api/start-list/export` | 导出参赛名单 | `startlist:export` |
| PUT | `/api/start-list/publish` | 发布参赛名单 | `startlist:publish` |
| GET | `/api/referee-assignments` | 裁判分工查询（按单元/项目/裁判） | `referee:assign:list` |
| POST | `/api/referee-assignments/batch` | 批量分配岗位 | `referee:assign:edit` |
| POST | `/api/referee-assignments/auto` | 按规则自动分配岗位 | `referee:assign:auto` |
| DELETE | `/api/referee-assignments/{id}` | 取消分配 | `referee:assign:edit` |
| GET | `/api/referee-assignments/export` | 导出裁判分工表/任务单 | `referee:assign:list` |

---

## 8. 成绩管理 `/api`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/results` | 成绩分页（`eventId`、`roundId`、`heatId`、`auditStatus`、`unitId`） | `result:list` |
| GET | `/api/results/entry-sheet` | 成绩录入表结构（含分组、道次、出场顺序） | `result:list` |
| POST | `/api/results` | 录入/保存成绩（径赛、田赛、全能；支持批量数组） | `result:add` |
| PUT | `/api/results/{id}` | 修改成绩（未审核直接改；已审核需走撤销） | `result:edit` |
| POST | `/api/results/batch` | 批量录入（一组一次提交） | `result:add` |
| POST | `/api/results/attempt` | 田赛单次试跳/试掷录入 | `result:attempt:add` |
| PUT | `/api/results/attempt/{id}` | 修改单次成绩 | `result:attempt:edit` |
| POST | `/api/results/status` | 批量标记 DNS/DNF/DSQ/NM（DSQ 附犯规类型） | `result:status` |
| POST | `/api/results/wind` | 风速录入（组级或单人） | `result:wind` |
| POST | `/api/results/submit` | 提交审核（按组/按项目轮次） | `result:submit` |
| POST | `/api/results/approve` | **成绩审核通过**（批量） | `result:approve` |
| POST | `/api/results/reject` | 审核驳回（附原因） | `result:approve` |
| POST | `/api/results/{id}/revoke-approval` | 撤销审核（记录原因） | `result:revoke` |
| GET | `/api/results/{id}/audits` | 成绩修改留痕 | `result:audit:list` |
| GET | `/api/results/rank` | 名次预览/生成（`eventId`、`roundId`） | `result:rank` |
| POST | `/api/results/rank/generate` | 生成名次并锁定 | `result:rank` |
| POST | `/api/results/advance/generate` | **自动晋级**（生成下一轮名单） | `result:advance` |
| GET | `/api/results/advance` | 晋级名单查询 | `result:list` |
| PUT | `/api/results/advance/{id}` | 手动调整晋级名单 | `result:advance:edit` |
| PUT | `/api/results/advance/confirm` | 确认晋级名单 | `result:advance` |
| POST | `/api/results/record/confirm` | 破纪录认定（层级、是否通过） | `result:record` |
| GET | `/api/records` | 纪录查询（按项目/组别/层级） | `record:list` |
| POST/PUT/DELETE | `/api/records`、`/api/records/{id}` | 纪录维护 | `record:*` |
| POST | `/api/results/import` | 设备成绩文件导入 | `result:import` |
| GET | `/api/results/export` | 导出成绩 | `result:export` |

---

## 9. 统计与积分 `/api`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/standings/team` | **团体总分**（`groupId`、`scoreType=MALE|FEMALE|TOTAL`） | `standing:team` |
| GET | `/api/standings/medals` | **奖牌榜**（`groupId`） | `standing:medal` |
| GET | `/api/standings/points` | 积分明细查询（按单位/项目/运动员） | `standing:points` |
| POST | `/api/standings/recalculate` | 重算积分与榜单（`scope`、`eventId`） | `standing:recalc` |
| POST | `/api/standings/snapshot` | 保存榜单快照 | `standing:recalc` |
| GET | `/api/standings/athlete-medals` | 运动员奖牌明细 | `standing:medal` |
| GET | `/api/statistics/overview` | 综合统计概览 | `statistics:view` |
| GET | `/api/statistics/event-distribution` | 项目成绩分布（图表） | `statistics:view` |
| GET | `/api/statistics/unit-trend` | 单位奖牌/积分趋势 | `statistics:view` |
| GET | `/api/statistics/registration-heat` | 报名热度分析 | `statistics:view` |
| POST | `/api/certificates/generate` | 批量生成证书（获奖/成绩/参赛） | `certificate:generate` |
| GET | `/api/certificates` | 证书列表 | `certificate:list` |
| GET | `/api/certificates/{id}/download` | 下载证书 PDF | `certificate:list` |
| POST | `/api/certificates/print` | 批量打印（记录打印次数） | `certificate:print` |

---

## 10. 打印与导出 `/api/print`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/print/order-book` | **秩序册**（`sessionId`、`groupId`、`format`、`templateId`） | `print:orderbook` |
| GET | `/api/print/order-book/schedule` | 总日程表 | `print:orderbook` |
| GET | `/api/print/heats` | 分组表 | `print:orderbook` |
| GET | `/api/print/lanes` | 分道表 | `print:orderbook` |
| GET | `/api/print/calling-sheets` | 检录表 | `print:calling` |
| GET | `/api/print/referee-sheets` | 裁判安排表 | `print:referee` |
| GET | `/api/print/results` | **成绩公告/成绩册**（`eventId`、`unitId`、`format`） | `print:results` |
| GET | `/api/print/standings/team` | 团体总分表 | `print:standings` |
| GET | `/api/print/standings/medals` | 奖牌榜 | `print:standings` |
| GET | `/api/print/bibs` | 号码布 | `print:bib` |
| GET | `/api/print/entry-cards` | 参赛证 | `print:entrycard` |
| GET | `/api/print/registration-form` | 报名表 | `print:registration` |
| GET | `/api/print/batch` | 批量打包（多报表 → ZIP） | `print:batch` |
| GET | `/api/print/templates` | 打印模板列表 | `print:template:list` |
| POST/PUT/DELETE | `/api/print/templates`、`/api/print/templates/{id}` | 模板增改删 | `print:template:*` |
| PUT | `/api/print/templates/{id}/default` | 设为默认模板 | `print:template:edit` |
| GET | `/api/print/logs` | 打印记录 | `print:log:list` |

---

## 11. 大屏与公告 `/api`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/screen/data` | 大屏聚合数据（模块可配） | 大屏 Token / 公开 |
| GET | `/api/screen/live-results` | 实时成绩 | 公开 |
| GET | `/api/screen/advance-list` | 晋级名单 | 公开 |
| GET | `/api/screen/medals` | 奖牌榜 | 公开 |
| GET | `/api/screen/team-scores` | 团体总分 | 公开 |
| GET | `/api/screen/config` | 大屏配置 | `screen:config:list` |
| PUT | `/api/screen/config` | 保存大屏配置 | `screen:config:edit` |
| POST | `/api/screen/token` | 生成大屏访问 Token | `screen:config:edit` |
| GET | `/api/announcements` | 公告列表（按可见范围过滤） | 登录/公开 |
| POST/PUT/DELETE | `/api/announcements`、`/api/announcements/{id}` | 公告增改删 | `notice:*` |
| PUT | `/api/announcements/{id}/publish` | 发布/撤回/置顶 | `notice:publish` |
| GET | `/api/announcements/next-session` | 下一单元倒计时与项目提醒 | 公开 |

---

## 12. 接口统计

| 模块 | 接口数（约） |
| --- | --- |
| 认证与系统管理 | 40 |
| 运动会管理 | 24 |
| 运动员与报名 | 26 |
| 项目与规则 | 24 |
| 编排管理 | 38 |
| 赛前管理 | 16 |
| 成绩管理 | 27 |
| 统计与积分 | 14 |
| 打印与导出 | 17 |
| 大屏与公告 | 11 |
| **合计** | **约 237** |

> 需求文档中明确指定的 18 个接口（`/api/auth/login`、`/api/meets`、`/api/athletes`、`/api/athletes/import`、`/api/registrations`、`/api/events`、`/api/schedule/auto`、`/api/heats/generate`、`/api/lanes/assign`、`/api/results`、`/api/results/approve`、`/api/standings/team`、`/api/standings/medals`、`/api/print/order-book`、`/api/print/results` 等）全部包含，路径与语义完全一致。
