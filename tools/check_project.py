#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
TrackMeet 后端静态检查脚本（无 JDK 环境下的替代校验）。

检查项：
  1. Java 文件：package 与目录一致、文件名与类型名一致、大括号/圆括号配平；
  2. import com.trackmeet.* 的类必须存在（含通配符包校验）；
  3. Mapper 接口必须带 @Mapper；
  4. 实体 @TableName / @TableField 的列必须存在于对应 CREATE TABLE 中；
  5. 继承 BaseEntity 的实体，其表必须包含 6 个通用字段（含 @TableId 的 id）；
  6. 所有 CREATE TABLE 的列名重复检查、括号与反引号配平。

用法：python tools/check_project.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOTS = [
    ROOT / "backend" / "src" / "main" / "java",
    ROOT / "backend" / "src" / "test" / "java",
]
SQL_DIR = ROOT / "sql"

BASE_ENTITY_FIELDS = ["create_by", "create_time", "update_by", "update_time", "deleted", "version"]

errors = []
warnings = []


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


# ----------------------------------------------------------------------------
# 1. 解析 Java 文件
# ----------------------------------------------------------------------------
java_files = sorted(p for root in JAVA_ROOTS for p in root.rglob("*.java"))
types = {}          # 全限定名 -> 文件路径
packages = set()    # 包名集合
java_text = {}      # 文件路径 -> 文本

for path in java_files:
    text = path.read_text(encoding="utf-8")
    java_text[path] = text

    pkg_match = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
    if not pkg_match:
        errors.append(f"{rel(path)}: 缺少 package 声明")
        continue
    pkg = pkg_match.group(1)
    packages.add(pkg)

    type_match = re.search(
        r"^\s*(?:public\s+)?(?:final\s+|abstract\s+|sealed\s+)*(?:@interface|class|interface|enum|record)\s+(\w+)",
        text, re.M)
    if not type_match:
        errors.append(f"{rel(path)}: 未找到 class/interface/enum/record/@interface 声明")
        continue
    name = type_match.group(1)
    kind = "annotation"

    java_root = next((root for root in JAVA_ROOTS if path.is_relative_to(root)), JAVA_ROOTS[0])
    expected_dir = java_root.joinpath(*pkg.split("."))
    if path.parent != expected_dir:
        errors.append(f"{rel(path)}: 包名 {pkg} 与所在目录不一致（期望 {rel(expected_dir)}）")
    if path.stem != name:
        errors.append(f"{rel(path)}: 文件名与类型名不一致（{path.stem} vs {name}）")

    types[f"{pkg}.{name}"] = path

def strip_java_noise(text: str, keep_strings: bool = False) -> str:
    """单遍扫描剥离字符串/字符字面量与注释。

    正则互相干扰（注释中的引号、字符串中的 /*、双反斜杠、字符字面量 '{'）都会破坏括号配平统计，
    因此改用状态机一次扫描。

    :param keep_strings: 为 True 时保留字符串/字符字面量原文（仅剥离注释），用于路由与注解内容解析
    """
    out = []
    i = 0
    length = len(text)
    while i < length:
        char = text[i]
        nxt = text[i + 1] if i + 1 < length else ""
        if char == "/" and nxt == "*":
            end = text.find("*/", i + 2)
            i = length if end < 0 else end + 2
            out.append(" ")
            continue
        if char == "/" and nxt == "/":
            end = text.find("\n", i)
            i = length if end < 0 else end
            continue
        if char == '"':
            start = i
            if text.startswith('"""', i):
                end = text.find('"""', i + 3)
                i = length if end < 0 else end + 3
            else:
                i += 1
                while i < length:
                    if text[i] == "\\":
                        i += 2
                        continue
                    if text[i] == '"':
                        i += 1
                        break
                    i += 1
            out.append(text[start:i] if keep_strings else '""')
            continue
        if char == "'":
            start = i
            i += 1
            while i < length:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            out.append(text[start:i] if keep_strings else "''")
            continue
        out.append(char)
        i += 1
    return "".join(out)


for path in java_files:
    text = java_text[path]
    java_text[path] = text
    pkg_match = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
    pkg = pkg_match.group(1) if pkg_match else ""
    name_match = re.search(
        r"^\s*(?:public\s+)?(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+)*"
        r"(?:@interface|class|interface|enum|record)\s+(\w+)",
        text,
        re.M,
    )
    name = name_match.group(1) if name_match else path.stem
    java_root = ROOT / "backend" / "src" / "main" / "java"
    if not str(path).startswith(str(java_root)):
        java_root = ROOT / "backend" / "src" / "test" / "java"
    expected_dir = java_root.joinpath(*pkg.split("."))
    if path.parent != expected_dir:
        errors.append(f"{rel(path)}: 包名 {pkg} 与所在目录不一致（期望 {rel(expected_dir)}）")
    if path.stem != name:
        errors.append(f"{rel(path)}: 文件名与类型名不一致（{path.stem} vs {name}）")

    types[f"{pkg}.{name}"] = path

    # 括号配平（单遍状态机剥离注释与字面量，避免注释中的引号/字符串中的 /* 干扰）
    stripped = strip_java_noise(text)
    if stripped.count("{") != stripped.count("}"):
        errors.append(f"{rel(path)}: 大括号不配平 {{={stripped.count('{')} }}={stripped.count('}')}")
    if stripped.count("(") != stripped.count(")"):
        errors.append(f"{rel(path)}: 圆括号不配平 (={stripped.count('(')} )={stripped.count(')')}")

# ----------------------------------------------------------------------------
# 2. import 校验
# ----------------------------------------------------------------------------
import_re = re.compile(r"^\s*import\s+(static\s+)?(com\.trackmeet\.[\w.*]+)\s*;", re.M)
for path, text in java_text.items():
    for match in import_re.finditer(text):
        imported = match.group(2)
        if imported.endswith(".*"):
            pkg = imported[:-2]
            if pkg not in packages:
                errors.append(f"{rel(path)}: 通配符导入的包不存在: {imported}")
            continue
        if imported not in types:
            errors.append(f"{rel(path)}: 导入的类型不存在: {imported}")

# ----------------------------------------------------------------------------
# 3. Mapper 注解校验
# ----------------------------------------------------------------------------
for path, text in java_text.items():
    if "/mapper/" in path.as_posix() and path.name.endswith("Mapper.java"):
        if "@Mapper" not in text:
            errors.append(f"{rel(path)}: Mapper 接口缺少 @Mapper 注解")

# ----------------------------------------------------------------------------
# 4. 解析 SQL 建表语句
# ----------------------------------------------------------------------------
table_columns = {}   # 表名 -> [列名]
table_dupes = {}     # 表名 -> 重复列
sql_files = sorted(SQL_DIR.glob("*.sql"))

for sql_file in sql_files:
    sql = sql_file.read_text(encoding="utf-8")
    if sql.count("`") % 2 != 0:
        errors.append(f"{rel(sql_file)}: 反引号数量为奇数，可能未闭合")

    for match in re.finditer(
            r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`(\w+)`\s*\((.*?)\n\)\s*ENGINE",
            sql, re.S | re.I):
        table = match.group(1)
        body = match.group(2)
        columns = []
        for line in body.splitlines():
            line = line.strip()
            if not line.startswith("`"):
                continue
            col_match = re.match(r"`(\w+)`", line)
            if col_match:
                columns.append(col_match.group(1))
        if table in table_columns:
            warnings.append(f"{rel(sql_file)}: 表 {table} 在多个脚本中重复定义")
        table_columns[table] = columns
        dupes = {c for c in columns if columns.count(c) > 1}
        if dupes:
            table_dupes[table] = sorted(dupes)
            errors.append(f"{rel(sql_file)}: 表 {table} 存在重复列: {sorted(dupes)}")

        # deleted / version 必须有 NOT NULL DEFAULT 0（逻辑删除与乐观锁依赖默认值）
        for line in body.splitlines():
            stripped = line.strip()
            if stripped.startswith("`deleted`") and ("NOT NULL" not in stripped or "DEFAULT 0" not in stripped):
                errors.append(f"{rel(sql_file)}: 表 {table}.deleted 必须为 NOT NULL DEFAULT 0")
            if stripped.startswith("`version`") and ("NOT NULL" not in stripped or "DEFAULT 0" not in stripped):
                errors.append(f"{rel(sql_file)}: 表 {table}.version 必须为 NOT NULL DEFAULT 0")

# ----------------------------------------------------------------------------
# 5. 实体 -> 表 列一致性
# ----------------------------------------------------------------------------
table_name_re = re.compile(r'@TableName\(\s*(?:value\s*=\s*)?"(\w+)"')
field_re = re.compile(r'@TableField\(\s*(?:value\s*=\s*)?"(\w+)"')
table_id_re = re.compile(r'@TableId\(\s*(?:value\s*=\s*)?"(\w+)"')

entity_count = 0
for path, text in java_text.items():
    table_match = table_name_re.search(text)
    if not table_match:
        continue
    entity_count += 1
    table = table_match.group(1)
    if table not in table_columns:
        errors.append(f"{rel(path)}: @TableName(\"{table}\") 在建表脚本中找不到对应表")
        continue
    columns = set(table_columns[table])
    declared = field_re.findall(text) + table_id_re.findall(text)
    for column in declared:
        if column not in columns:
            errors.append(f"{rel(path)}: 字段列 {table}.{column} 不存在于建表脚本")

    extends_base = re.search(r"extends\s+BaseEntity", text) is not None
    if extends_base:
        for base_field in BASE_ENTITY_FIELDS:
            if base_field not in columns:
                errors.append(f"{rel(sql_file)}: 表 {table} 缺少 BaseEntity 通用列 {base_field}")
        if "id" not in columns:
            errors.append(f"表 {table} 缺少主键列 id")

# ----------------------------------------------------------------------------
# 6. 初始化数据校验：菜单 ID/权限编码唯一、parent 存在、权限编码覆盖 @PreAuthorize
# ----------------------------------------------------------------------------
init_sql_path = SQL_DIR / "05-init-data.sql"
menu_rows = []          # (id, parent_id, name, type, perm_code)
if init_sql_path.exists():
    init_sql = init_sql_path.read_text(encoding="utf-8")
    row_re = re.compile(
        r"^\((\d+),\s*(\d+),\s*'([^']*)',\s*'([DMB])',\s*(NULL|'[^']*'),\s*(NULL|'[^']*'),\s*(NULL|'[^']*'),\s*(NULL|'[^']*'),\s*(\d+)\)",
        re.M)
    for match in row_re.finditer(init_sql):
        perm_raw = match.group(8)
        perm_code = None if perm_raw == "NULL" else perm_raw.strip("'")
        menu_rows.append((int(match.group(1)), int(match.group(2)), match.group(3), match.group(4), perm_code))

    if not menu_rows:
        errors.append(f"{rel(init_sql_path)}: 未解析到 sys_menu 初始化行")

    ids = [row[0] for row in menu_rows]
    dup_ids = sorted({i for i in ids if ids.count(i) > 1})
    if dup_ids:
        errors.append(f"{rel(init_sql_path)}: sys_menu 存在重复 id: {dup_ids}")

    id_set = set(ids)
    for row in menu_rows:
        if row[1] != 0 and row[1] not in id_set:
            errors.append(f"{rel(init_sql_path)}: 菜单 {row[0]}({row[2]}) 的 parent_id {row[1]} 不存在")

    perms = [row[4] for row in menu_rows if row[4]]
    dup_perms = sorted({p for p in perms if perms.count(p) > 1})
    if dup_perms:
        errors.append(f"{rel(init_sql_path)}: sys_menu 存在重复 perm_code（会导致 sys_permission 唯一键冲突）: {dup_perms}")

    # 其他初始化表的主键/唯一键重复
    def check_unique(pattern, label):
        seen = {}
        for match in re.finditer(pattern, init_sql):
            key = match.group(1)
            seen[key] = seen.get(key, 0) + 1
        dup = sorted(k for k, v in seen.items() if v > 1)
        if dup:
            errors.append(f"{rel(init_sql_path)}: {label} 重复: {dup}")

    check_unique(r"^\((\d+),\s*'[A-Z_]+',\s*'[^']*',\s*'(?:ALL|MEET|UNIT|SELF|REF)'", "sys_role.id")
    check_unique(r"^\('([a-z_.]+)',\s*'[^']*',\s*'[A-Z_]+',\s*[01],\s*[01],", "sys_dict_type.dict_type")
    check_unique(r"^\('([a-z_.]+)',\s*'[^']*',\s*'[A-Za-z_.]+',", "sys_dict_item(dict_type,item_value)")
    check_unique(r"^\('([a-z_.]+)',\s*'", "sys_config.config_key")

    # 所有 @PreAuthorize("@perm.has('xxx')") 的编码必须已在初始化数据中定义
    authorized_codes = set()
    for path, text in java_text.items():
        for match in re.finditer(r"@perm\.has\(\s*'([^']+)'\s*\)", text):
            authorized_codes.add(match.group(1))
        for match in re.finditer(r"@perm\.hasAny\(([^)]*)\)", text):
            for code in re.findall(r"'([^']+)'", match.group(1)):
                authorized_codes.add(code)
    defined = set(perms)
    missing = sorted(authorized_codes - defined)
    if missing:
        errors.append(f"以下权限编码被 @PreAuthorize 使用但未在 05-init-data.sql 中定义: {missing}")
    unused = sorted(defined - authorized_codes)

# ----------------------------------------------------------------------------
# 7. 数据脚本 INSERT 列名校验
# ----------------------------------------------------------------------------
insert_count = 0
for sql_file in sql_files:
    sql = sql_file.read_text(encoding="utf-8")
    for match in re.finditer(r"INSERT\s+(?:IGNORE\s+)?INTO\s+`(\w+)`\s*\(([^)]*)\)", sql, re.I | re.S):
        table = match.group(1)
        insert_count += 1
        if table not in table_columns:
            errors.append(f"{rel(sql_file)}: INSERT 目标表 {table} 在建表脚本中不存在")
            continue
        columns = set(table_columns[table])
        raw = match.group(2)
        for column in [c.strip().strip("`") for c in raw.split(",") if c.strip()]:
            if column not in columns:
                errors.append(f"{rel(sql_file)}: INSERT {table} 的列 {column} 在该表中不存在")

# ----------------------------------------------------------------------------
# 8. 表清单核对
# ----------------------------------------------------------------------------
expected_tables = 68
if len(table_columns) != expected_tables:
    warnings.append(f"建表脚本共定义 {len(table_columns)} 张表，设计为 {expected_tables} 张")

# ----------------------------------------------------------------------------
# 8.5 引擎调用与属性名静态校验（无编译器时的替代手段）
# ----------------------------------------------------------------------------
JDK_METHOD_WHITELIST = {
    "getClass", "getMessage", "getCause", "getLocalizedMessage", "getSuppressed", "getStackTrace",
    "getBytes", "getTime", "getKey", "getValue", "getOrDefault", "getNumber", "getSource",
    "getInputStream", "getOutputStream", "getWriter", "getParameter", "getHeader", "getRequestURI",
    "getRemoteAddr", "getRequest", "getResponse", "getContentAsString", "getStatus", "getRecords",
    "getTotal", "getCurrent", "getPages", "getSize", "getResult", "getRowIndex", "getReadHolder",
    "getFieldErrors", "getBindingResult", "getDefaultMessage", "getConstraintViolations",
    "getOriginalFilename", "getContentType", "getHeaderName", "getRequestAttributes", "getAttributes",
    "getSession", "getServletContext", "getContextPath", "getQueryString", "getMethod", "getScheme",
    "getServerName", "getServerPort", "getLocale", "getCookies", "getParts", "getParts2", "getValue2",
    # MyBatis-Plus / 框架 / 测试框架常见方法（非业务属性）
    "getById", "getByIds", "getOne", "getBaseMapper", "getEntityClass", "getObj", "getMap",
    "isNull", "isTrue", "isFalse", "isOk", "isNotFound", "isEqualTo", "isInstanceOf", "isNotEmpty",
    "isGreaterThanOrEqualTo", "isLessThanOrEqualTo", "getArgs", "getField", "getFieldErrors",
    "setFont", "getIt", "getAndSet", "getTarget", "getSignature", "getProceedingJoinPoint",
    "setScale", "getScale", "setLenient", "isLenient",
    "getURL", "getRequestURL", "getServletPath", "getParameterMap", "getParameterValues",
    "isNumber", "setBold", "getBold", "isFormula", "setWrapText", "setAlignment",
    # OpenPDF / java.io / 函数式接口常见方法
    "getResource", "isFile", "setPadding", "setBorder", "setLeading", "getAsInt",
    "setBorderWidth", "setBackgroundColor", "setHorizontalAlignment", "setVerticalAlignment",
    "setColspan", "setSpacingBefore", "setSpacingAfter", "setWidthPercentage", "setWidths",
    "setHeaderRows", "setPageEvent", "getDirectContent", "getPageNumber"
}
JDK_METHOD_PREFIX_ALLOW = ("getMessage", "getRootCause")

declared_properties = set()
declared_methods = set()
class_methods = {}   # 全限定名/简单名 -> set(方法名)


def edit_distance(first: str, second: str) -> int:
    """Levenshtein 距离（用于发现属性名拼写错误）。"""
    if first == second:
        return 0
    if not first:
        return len(second)
    if not second:
        return len(first)
    previous = list(range(len(second) + 1))
    for i, char_a in enumerate(first, start=1):
        current = [i]
        for j, char_b in enumerate(second, start=1):
            cost = 0 if char_a == char_b else 1
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost))
        previous = current
    return previous[-1]


field_re = re.compile(r"^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.?\s]+\s+(\w+)\s*(?:=|;)", re.M)
method_re = re.compile(r"^\s*(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.?\s]+\s+(\w+)\s*\(", re.M)

for path, text in java_text.items():
    type_match = re.search(r"^\s*(?:public\s+)?(?:final\s+|abstract\s+)*(?:@interface|class|interface|enum|record)\s+(\w+)", text, re.M)
    simple_name = type_match.group(1) if type_match else path.stem
    props = set()
    for match in field_re.finditer(text):
        name = match.group(1)
        if name not in {"class", "return", "new"}:
            props.add(name)
    methods = set(method_re.findall(text))
    declared_properties |= props
    declared_methods |= methods
    class_methods.setdefault(simple_name, set()).update(methods)

# 引擎工具类（静态方法）精确校验
ENGINE_UTILS = {
    "SerpentineGrouper": class_methods.get("SerpentineGrouper", set()),
    "LaneAllocator": class_methods.get("LaneAllocator", set()),
    "FieldOrderGenerator": class_methods.get("FieldOrderGenerator", set()),
    "ConflictDetector": class_methods.get("ConflictDetector", set()),
    "DurationEstimator": class_methods.get("DurationEstimator", set()),
    "SchedulePacker": class_methods.get("SchedulePacker", set()),
    "RankResolver": class_methods.get("RankResolver", set()),
    "AdvanceCalculator": class_methods.get("AdvanceCalculator", set()),
    "WindValidator": class_methods.get("WindValidator", set()),
    "AllroundScoreLookup": class_methods.get("AllroundScoreLookup", set()),
    "ScoreCalculator": class_methods.get("ScoreCalculator", set()),
    "StandingCalculator": class_methods.get("StandingCalculator", set()),
}
property_warnings = []
engine_calls_checked = 0
# 仅用「小写开头的实例字段」做近似匹配，排除常量（全大写）造成的误报
instance_property_pool = {name for name in declared_properties if name[:1].islower()}

for path, text in java_text.items():
    clean = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    clean = re.sub(r"//[^\n]*", "", clean)
    # 引擎静态方法调用：类名.方法(
    for util_name, methods in ENGINE_UTILS.items():
        for match in re.finditer(re.escape(util_name) + r"\.(\w+)\s*\(", clean):
            method = match.group(1)
            engine_calls_checked += 1
            if method not in methods:
                errors.append(f"{rel(path)}: 调用 {util_name}.{method}() 但该类未声明该方法")
    # getter/setter 属性名（仅报告「与已声明属性高度近似」的调用，避免命中 JDK/框架方法）
    for match in re.finditer(r"\.(get|set|is)([A-Z]\w*)\s*\(", clean):
        method_name = match.group(1) + match.group(2)
        if method_name in JDK_METHOD_WHITELIST or method_name.startswith(JDK_METHOD_PREFIX_ALLOW):
            continue
        if method_name in declared_methods:
            continue
        prop = match.group(2)[0].lower() + match.group(2)[1:]
        if prop in declared_properties:
            continue
        base = prop
        for suffix in ("ById", "ByName", "ByCode", "ByType", "ByKey", "List", "Map", "Set",
                       "Count", "Page", "Vo", "Dto", "Json"):
            if base.endswith(suffix) and len(base) > len(suffix):
                base = base[: -len(suffix)]
                break
        if not base:
            continue
        nearby = [candidate for candidate in instance_property_pool
                  if abs(len(candidate) - len(base)) <= 2 and edit_distance(candidate.lower(), base.lower()) <= 2]
        if nearby:
            property_warnings.append(
                f"{rel(path)}: 调用 {method_name}()，属性 {base} 未声明；相近的已声明属性: {sorted(nearby)[:5]}")

# ----------------------------------------------------------------------------
# 8.6 Mapper 扫描覆盖校验（存在显式 @MapperScan 时，@Mapper 自动注册会关闭）
# ----------------------------------------------------------------------------
scan_packages = set()
for path, text in java_text.items():
    for match in re.finditer(r"@MapperScan\s*\(\s*(?:basePackages\s*=\s*)?(?:\{([^}]*)\}|\"([^\"]*)\")", text):
        raw = match.group(1) if match.group(1) is not None else ""
        for pkg in re.findall(r"\"([^\"]+)\"", raw):
            scan_packages.add(pkg)
        if match.group(2):
            scan_packages.add(match.group(2))

mapper_dirs = sorted(
    d for d in (JAVA_ROOTS[0] / "com" / "trackmeet" / "modules").glob("*/mapper") if d.is_dir()
)
uncovered_packages = []
for directory in mapper_dirs:
    pkg = f"com.trackmeet.modules.{directory.parent.name}.mapper"
    if not any(pkg == scanned or pkg.startswith(scanned + ".") for scanned in scan_packages):
        if any(directory.glob("*.java")):
            uncovered_packages.append(pkg)
if uncovered_packages:
    errors.append(f"以下 Mapper 包未被任何 @MapperScan 覆盖（启动时注入会失败）: {uncovered_packages}")

# ----------------------------------------------------------------------------
# 8.7 路由重复校验（同一 方法+路径 被两个方法映射时 Spring 启动会报 Ambiguous mapping）
# ----------------------------------------------------------------------------
route_map = {}
for path, text in java_text.items():
    if not path.stem.endswith("Controller"):
        continue
    clean = strip_java_noise(text, keep_strings=True)
    base_match = re.search(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"', clean)
    base_path = base_match.group(1) if base_match else ""
    for match in re.finditer(
            r'@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?', clean):
        verb = match.group(1).upper()
        sub_path = match.group(2) or ""
        full_path = (base_path + sub_path).replace("//", "/")
        route_map.setdefault((verb, full_path), []).append(rel(path))

duplicate_routes = []
for route, files in route_map.items():
    unique_files = sorted(set(files))
    if len(files) > 1 and len(unique_files) >= 1:
        duplicate_routes.append(f"{route[0]} {route[1]} 被 {len(files)} 个方法映射：{unique_files}")
for item in duplicate_routes:
    errors.append(f"路由重复（Spring 启动会报 Ambiguous mapping）：{item}")

# ----------------------------------------------------------------------------
# 9. 前端静态检查：@/ 别名导入可解析、api 具名导出存在、.vue 结构完整
# ----------------------------------------------------------------------------
FRONTEND_SRC = ROOT / "frontend" / "src"
front_files = []
if FRONTEND_SRC.exists():
    front_files = [p for p in FRONTEND_SRC.rglob("*") if p.suffix in {".ts", ".vue"}]

    # 收集各模块导出名
    module_exports = {}
    for path in front_files:
        text = path.read_text(encoding="utf-8")
        names = set()
        names |= set(re.findall(r"export\s+(?:async\s+)?function\s+(\w+)", text))
        names |= set(re.findall(r"export\s+const\s+(\w+)", text))
        names |= set(re.findall(r"export\s+let\s+(\w+)", text))
        names |= set(re.findall(r"export\s+(?:interface|type|enum|class)\s+(\w+)", text))
        names |= set(re.findall(r"export\s+default\s+function\s+(\w+)", text))
        module_exports[path] = names

    def resolve_alias(spec: str):
        """把 @/xxx 解析为 frontend/src 下的真实文件"""
        rel = spec[2:]
        base = FRONTEND_SRC / rel
        for candidate in (base.with_suffix(".ts"), base.with_suffix(".vue"), base / "index.ts", base):
            if candidate.exists() and candidate.is_file():
                return candidate
        return None

    named_import_re = re.compile(r"import\s+(?:type\s+)?(?:(\w+)\s*,\s*)?\{([^}]*)\}\s+from\s+['\"]([^'\"]+)['\"]")
    default_import_re = re.compile(r"import\s+(?:type\s+)?(\w+)\s+from\s+['\"]([^'\"]+)['\"]")
    side_effect_import_re = re.compile(r"import\s+['\"]([^'\"]+)['\"]")

    for path in front_files:
        text = path.read_text(encoding="utf-8")
        for match in named_import_re.finditer(text):
            spec = match.group(3)
            if not spec.startswith("@/"):
                continue
            target = resolve_alias(spec)
            if target is None:
                errors.append(f"{rel(path)}: 导入路径无法解析: {spec}")
                continue
            for raw in match.group(2).split(","):
                name = raw.strip()
                if not name:
                    continue
                name = re.sub(r"^type\s+", "", name).strip()
                if " as " in name:
                    name = name.split(" as ")[0].strip()
                if name and name not in module_exports.get(target, set()):
                    errors.append(f"{rel(path)}: {spec} 未导出 {name}")
        for match in default_import_re.finditer(text):
            spec = match.group(2)
            if spec.startswith("@/"):
                if resolve_alias(spec) is None:
                    errors.append(f"{rel(path)}: 导入路径无法解析: {spec}")
        for match in side_effect_import_re.finditer(text):
            spec = match.group(1)
            if spec.startswith("@/") and resolve_alias(spec) is None:
                errors.append(f"{rel(path)}: 导入路径无法解析: {spec}")

        if path.suffix == ".vue":
            if "<template>" not in text:
                errors.append(f"{rel(path)}: 缺少 <template> 块")
            if "<script setup" not in text:
                errors.append(f"{rel(path)}: 缺少 <script setup> 块")

    if not front_files:
        warnings.append("未发现前端源码（frontend/src 为空）")

    # 菜单 component → vue 文件存在性（阶段 2 应交付的页面缺失即报错）
    PHASE2_COMPONENTS = {
        "dashboard/Index",
        "athlete/AthleteList",
        "registration/RegistrationList",
        "registration/RegistrationAudit",
        "registration/RegistrationCheck",
        "event/EventList",
        "event/RoundList",
        "event/RuleConfig",
        "event/ScoreRule",
        "profile/Index"
    }
    PHASE3_COMPONENTS = {
        "schedule/SessionList",
        "schedule/SchedulePlan",
        "schedule/HeatLane",
        "schedule/OrderBook"
    }
    PHASE4_COMPONENTS = {
        "result/ResultEntry",
        "result/ResultAudit",
        "result/ResultNotice",
        "result/AdvanceManage",
        "standing/PointsBoard",
        "standing/MedalBoard"
    }
    PHASE2_COMPONENTS = PHASE2_COMPONENTS | PHASE3_COMPONENTS | PHASE4_COMPONENTS
    menu_components = set()
    if init_sql_path.exists():
        for match in re.finditer(r"'([A-Za-z]+/[A-Za-z]+)'", init_sql_path.read_text(encoding="utf-8")):
            menu_components.add(match.group(1))
    missing_phase2 = sorted(
        comp for comp in PHASE2_COMPONENTS
        if not (FRONTEND_SRC / "views" / f"{comp}.vue").exists()
    )
    if missing_phase2:
        errors.append(f"以下阶段 2 页面的 vue 文件缺失（菜单会回退占位页）: {missing_phase2}")
    pending = sorted(comp for comp in menu_components if comp not in PHASE2_COMPONENTS)
    if pending:
        print(f"待后续阶段实现的菜单页面: {len(pending)} 个（渲染占位页）")

# ----------------------------------------------------------------------------
# 输出
# ----------------------------------------------------------------------------
print(f"Java 文件数        : {len(java_files)}")
print(f"实体（@TableName） : {entity_count}")
print(f"SQL 脚本数         : {len(sql_files)}")
print(f"建表数量           : {len(table_columns)}")
print(f"类型总数           : {len(types)}")
print(f"菜单/按钮行        : {len(menu_rows)}")
print(f"已定义权限编码     : {len(perms)}")
print(f"被引用的权限编码   : {len(authorized_codes)}")
print(f"前端文件数         : {len(front_files)}")
print(f"引擎调用检查       : {engine_calls_checked} 处")
print(f"属性名可疑调用     : {len(property_warnings)} 处（详见下方 WARN）")
print("-" * 72)

if property_warnings:
    print("属性名可疑调用（可能为拼写错误，需人工确认）：")
    for item in property_warnings[:60]:
        print("  [WARN] " + item)
    if len(property_warnings) > 60:
        print(f"  ... 其余 {len(property_warnings) - 60} 处省略")
    print("-" * 72)

if warnings:
    print("警告：")
    for item in warnings:
        print("  [WARN] " + item)
    print("-" * 72)

if errors:
    print(f"发现 {len(errors)} 个问题：")
    for item in errors:
        print("  [ERR ] " + item)
    sys.exit(1)

print("检查通过：包路径/类型名/导入/Mapper 注解/实体列与 DDL 一致。")
